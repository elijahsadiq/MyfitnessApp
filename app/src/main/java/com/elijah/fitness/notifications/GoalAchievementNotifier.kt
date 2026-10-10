package com.elijah.fitness.notifications

import com.elijah.fitness.data.model.GoalProgressCalculator
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.data.repository.GoalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Posts "Goal achieved" the moment a goal reaches its target, whichever
 * screen the user is on. Progress is worked out on the phone, so this
 * also fires for a workout logged offline.
 *
 * It watches the same goal-progress stream the Goals tab uses. A goal
 * is announced once: the server's `completedNotified` flag stops a
 * repeat after a restart, and [announced] covers the time before that
 * flag is saved (or while offline).
 *
 * With goal alerts switched off it does nothing, and the Goals tab's
 * in-app Snackbar celebrates instead, as before.
 *
 * @param post shows the notification; injected so tests can count calls.
 */
class GoalAchievementNotifier(
    private val calculator: GoalProgressCalculator,
    private val goalRepository: GoalRepository,
    private val alertsEnabled: Flow<Boolean>,
    private val scope: CoroutineScope,
    private val post: (goalId: String, goalTitle: String) -> Unit,
    private val titleOf: (GoalWithProgress) -> String,
) {

    private val announced = mutableSetOf<String>()
    private var job: Job? = null

    /** Start watching. Calling it again while running does nothing. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            calculator.observe().collect { onProgress(it) }
        }
    }

    /** Stop and forget (sign-out). */
    fun stop() {
        job?.cancel()
        job = null
        announced.clear()
    }

    internal suspend fun onProgress(snapshots: List<GoalWithProgress>) {
        val reached = snapshots.filter {
            // daysLeft >= 0: reached within its window, not a goal that
            // ended long ago and was never celebrated.
            it.isCompleted && it.daysLeft >= 0 &&
                !it.goal.completedNotified && it.goal.id !in announced
        }
        if (reached.isEmpty() || !alertsEnabled.first()) return
        reached.forEach { goal ->
            announced += goal.goal.id
            post(goal.goal.id, titleOf(goal))
            // Offline, this fails; [announced] still stops a repeat now,
            // and the Goals tab retries the flag on its next celebration.
            goalRepository.markCompletedNotified(goal.goal.id)
        }
    }
}
