package com.elijah.fitness.notifications

import com.elijah.fitness.data.local.CelebrationSeedStore
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalCelebration
import com.elijah.fitness.data.model.GoalCompletionStats
import com.elijah.fitness.data.model.GoalProgressCalculator
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.GoalRepository
import com.elijah.fitness.data.repository.WorkoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The single owner of "a goal was just reached" (phase 8d, Step 7). It
 * replaced the phase-10 `GoalAchievementNotifier` and the Goals tab's
 * completion Snackbar, so a goal is celebrated exactly once, whichever
 * screen the user is on.
 *
 * It watches the same goal-progress stream the Goals tab uses (progress
 * is worked out on the phone, so this also fires for a workout logged
 * offline). When a goal is newly reached inside its window:
 * - app on screen ([onUiStarted] / [onUiStopped], called by
 *   `MainActivity`): the goal joins [pending], and `MainActivity` shows
 *   the goal-complete pop-up for it, one at a time;
 * - app in the background: the "Goal achieved" system notification,
 *   only when goal alerts are switched on in Profile.
 * Either way the server's `completedNotified` flag is saved, so it never
 * fires again after a restart, and [handled] stops a repeat in this
 * session even while that save is failing offline (it is retried on the
 * next progress change).
 *
 * **First run.** The first time this runs on an install it quietly marks
 * every goal that is already complete as celebrated, so an existing user
 * doesn't get a pile of old pop-ups ([seedIfFirstRun]).
 *
 * Kotlin note: [pending] is a `StateFlow` (a `SharedFlow` that always
 * holds its latest value), so a pop-up queued during a rotation is still
 * there when the new `MainActivity` starts collecting.
 */
class GoalCelebrationManager(
    private val calculator: GoalProgressCalculator,
    private val goalRepository: GoalRepository,
    private val workoutRepository: WorkoutRepository,
    private val seedStore: CelebrationSeedStore,
    private val alertsEnabled: Flow<Boolean>,
    private val scope: CoroutineScope,
    private val weightKg: () -> Double,
    private val titleOf: (Goal) -> String,
    private val postNotification: (goalId: String, goalTitle: String) -> Unit,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /** Goals already celebrated (or silently seeded) in this session. */
    private val handled: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Goals whose "notified" flag is being saved right now. */
    private val marking: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val _pending = MutableStateFlow<List<GoalCelebration>>(emptyList())

    /** Pop-ups waiting to be shown, oldest first. The head is the one on screen. */
    val pending: StateFlow<List<GoalCelebration>> = _pending.asStateFlow()

    private val visibleScreens = AtomicInteger(0)
    private var job: Job? = null

    /** True while a `MainActivity` is started (on screen). */
    val isUiVisible: Boolean get() = visibleScreens.get() > 0

    fun onUiStarted() {
        visibleScreens.incrementAndGet()
    }

    fun onUiStopped() {
        while (true) {
            val current = visibleScreens.get()
            if (current <= 0 || visibleScreens.compareAndSet(current, current - 1)) return
        }
    }

    /** Start watching. Calling it again while running does nothing. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                seedIfFirstRun()
                calculator.observe().collect { snapshots ->
                    try {
                        onProgress(snapshots)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // One bad snapshot must not stop the celebrations.
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A failing goals / workouts stream must never crash the app.
            }
        }
    }

    /** Stop and forget everything about this session (sign-out). */
    fun stop() {
        job?.cancel()
        job = null
        handled.clear()
        marking.clear()
        _pending.value = emptyList()
    }

    /** The pop-up for [goalId] was closed; the next one may show. */
    fun consume(goalId: String) {
        _pending.update { list -> list.filterNot { it.goalId == goalId } }
    }

    /**
     * First run on this install: load the latest goals and workouts, then
     * mark every goal that is already complete as celebrated without
     * showing anything. The DataStore flag is only saved when both loads
     * worked; offline, the same quiet pass runs again next launch.
     */
    internal suspend fun seedIfFirstRun() {
        val seeded = try {
            seedStore.isSeeded()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (seeded) return

        val goalsLoaded = refreshed { goalRepository.refresh() }
        val workoutsLoaded = refreshed { workoutRepository.refresh() }
        val snapshots = try {
            calculator.observe().first()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        snapshots.filter { it.isCompleted }.forEach { gwp ->
            handled += gwp.goal.id
            if (!gwp.goal.completedNotified) markNotified(gwp.goal.id)
        }
        if (goalsLoaded && workoutsLoaded) {
            try {
                seedStore.markSeeded()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Runs again next launch; it only ever stays quiet.
            }
        }
    }

    internal suspend fun onProgress(snapshots: List<GoalWithProgress>) {
        // A flag that failed to save (offline) is retried on later changes.
        snapshots.filter {
            it.isCompleted && !it.goal.completedNotified && it.goal.id in handled
        }.forEach { markNotified(it.goal.id) }

        val reached = snapshots.filter {
            // daysLeft >= 0: reached within its window, not a goal that
            // ended long ago and was never celebrated.
            it.isCompleted && it.daysLeft >= 0 &&
                !it.goal.completedNotified && it.goal.id !in handled
        }
        if (reached.isEmpty()) return
        reached.forEach { handled += it.goal.id }

        if (isUiVisible) {
            val workouts = currentWorkouts()
            val today = clock.instant().atZone(zone).toLocalDate()
            val weight = weightKg().takeIf { it.isFinite() && it > 0.0 } ?: FALLBACK_WEIGHT_KG
            val celebrations = reached.mapNotNull { gwp ->
                try {
                    GoalCompletionStats.celebrationFor(
                        goal = gwp.goal,
                        title = safeTitle(gwp.goal),
                        workouts = workouts,
                        weightKg = weight,
                        today = today,
                        zone = zone,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            _pending.update { queued ->
                queued + celebrations.filter { c -> queued.none { it.goalId == c.goalId } }
            }
        } else if (alertsOn()) {
            reached.forEach { gwp ->
                try {
                    postNotification(gwp.goal.id, safeTitle(gwp.goal))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A notification that can't be posted is simply skipped.
                }
            }
        }
        reached.forEach { markNotified(it.goal.id) }
    }

    /** Save the server flag in the background; one request per goal at a time. */
    private fun markNotified(goalId: String) {
        if (!marking.add(goalId)) return
        scope.launch {
            try {
                goalRepository.markCompletedNotified(goalId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Offline or server error: retried on the next progress change.
            } finally {
                marking.remove(goalId)
            }
        }
    }

    private suspend fun currentWorkouts(): List<Workout> = try {
        workoutRepository.observeWorkouts().first()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun alertsOn(): Boolean = try {
        alertsEnabled.first()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private fun safeTitle(goal: Goal): String = try {
        titleOf(goal)
    } catch (_: Exception) {
        goal.title
    }

    private suspend fun refreshed(load: suspend () -> ApiResult<Unit>): Boolean = try {
        load() is ApiResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private companion object {
        /** Same fallback as the rest of the app when the profile has no weight. */
        const val FALLBACK_WEIGHT_KG = 70.0
    }
}
