package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.Workout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Delayed-delete coordinator for workouts — mirror of
 * [GoalDeleteCoordinator], same shape and reasoning.
 *
 * The History screen's swipe-to-delete gesture calls [scheduleDelete];
 * the workout is hidden from the list immediately via [hiddenIds], and
 * the actual `DELETE /workouts/{id}` fires after [DEFAULT_DELAY_MS]
 * unless the Snackbar's Undo action triggers [undo] first.
 *
 * Lives on `AppContainer.appScope` so the DELETE survives the user
 * swiping, then navigating away from History before the 5-second window
 * elapses.
 */
class WorkoutDeleteCoordinator(
    private val repository: WorkoutRepository,
    private val appScope: CoroutineScope,
    private val delayMs: Long = DEFAULT_DELAY_MS,
) {

    private val _hiddenIds: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())
    val hiddenIds: StateFlow<Set<String>> = _hiddenIds.asStateFlow()

    private val pendingJobs: ConcurrentHashMap<String, Job> = ConcurrentHashMap()

    fun scheduleDelete(workout: Workout) {
        val id = workout.id
        if (pendingJobs.containsKey(id)) return

        _hiddenIds.update { it + id }

        val job = appScope.launch {
            try {
                delay(delayMs)
                repository.deleteWorkout(id)
            } finally {
                pendingJobs.remove(id)
                _hiddenIds.update { it - id }
            }
        }
        pendingJobs[id] = job
    }

    fun undo(id: String) {
        pendingJobs.remove(id)?.cancel()
        _hiddenIds.update { it - id }
    }

    /**
     * Sign-out: run every pending delete now instead of waiting out the
     * Undo window (the token is about to be cleared, after which the
     * DELETE could no longer be authorised).
     */
    suspend fun flushPending() {
        pendingJobs.keys.toList().forEach { id ->
            pendingJobs.remove(id)?.cancel()
            runCatching { repository.deleteWorkout(id) }
        }
        _hiddenIds.value = emptySet()
    }

    /** Drop every pending delete without sending it (session already invalid). */
    fun cancelAll() {
        pendingJobs.keys.toList().forEach { id -> pendingJobs.remove(id)?.cancel() }
        _hiddenIds.value = emptySet()
    }

    fun pending(): Set<String> = pendingJobs.keys.toSet()
    fun isPending(id: String): Boolean = pendingJobs.containsKey(id)

    companion object {
        const val DEFAULT_DELAY_MS: Long = 5_000L
    }
}
