package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.Goal
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
 * Delayed-delete coordinator for goals.
 *
 * ### Flow
 *   1. User taps Delete → fragment calls [scheduleDelete].
 *   2. The goal id joins [hiddenIds] immediately; the Goals screen
 *      filters it out of its visible list on the next emission.
 *   3. A coroutine on the app-level scope waits [DELAY_MS] milliseconds,
 *      then fires the real `DELETE /goals/{id}` through the repository.
 *   4. If the user taps Undo before the delay elapses, [undo] cancels
 *      the pending job and takes the id back out of [hiddenIds] — the
 *      goal reappears without any network trip.
 *
 * ### Why the app-level scope
 * The pending-delete job runs on the scope passed in by [AppContainer]
 * (a `SupervisorJob` + `Dispatchers.Main.immediate`), not the fragment's
 * `viewLifecycleOwner` scope or the ViewModel's. If the user leaves the
 * Goals screen within the 5-second window (navigating to Home, locking
 * the phone, etc.), the DELETE still fires — the only cancellation path
 * is an explicit [undo].
 *
 * ### Why delayed-delete beats delete-and-recreate
 * The alternative is to call DELETE immediately and remember enough of
 * the goal to re-POST on Undo. Delete-and-recreate costs *two* network
 * trips, loses the server-side `id` (a fresh row gets a new one), and
 * creates a transient "goal with id X doesn't exist" window during
 * which another device syncing the same account sees it disappear.
 * Delayed-delete avoids all of that at the cost of a 5-second local
 * timer and a tiny bit of state.
 */
class GoalDeleteCoordinator(
    private val repository: GoalRepository,
    private val appScope: CoroutineScope,
    private val delayMs: Long = DEFAULT_DELAY_MS,
) {

    private val _hiddenIds: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    /** Ids currently hidden from the UI awaiting a Delete-or-Undo decision. */
    val hiddenIds: StateFlow<Set<String>> = _hiddenIds.asStateFlow()

    /** `goalId → the pending-delete coroutine job`. */
    private val pendingJobs: ConcurrentHashMap<String, Job> = ConcurrentHashMap()

    /**
     * Schedule a deferred delete for [goal]. The id is hidden from the
     * UI immediately; the server-side DELETE fires after [delayMs] unless
     * [undo] is called first.
     *
     * No-ops if [goal] is already pending — the second tap of Delete
     * doesn't restart the timer.
     */
    fun scheduleDelete(goal: Goal) {
        val id = goal.id
        if (pendingJobs.containsKey(id)) return

        _hiddenIds.update { it + id }

        val job = appScope.launch {
            try {
                delay(delayMs)
                // Fire the real DELETE. Silent on failure — the pending set
                // is cleared either way so a second Delete tap can retry.
                repository.deleteGoal(id)
            } finally {
                pendingJobs.remove(id)
                _hiddenIds.update { it - id }
            }
        }
        pendingJobs[id] = job
    }

    /**
     * Cancel a pending delete. Removes the id from [hiddenIds] so the
     * goal reappears in the list on the next emission. No-op if nothing
     * is pending for that id.
     */
    fun undo(id: String) {
        pendingJobs.remove(id)?.cancel()
        _hiddenIds.update { it - id }
    }

    /**
     * Snapshot of ids that are currently in the "pending delete" state.
     * Handy for tests that want to peek without subscribing.
     */
    /**
     * Sign-out: run every pending delete now instead of waiting out the
     * Undo window (the token is about to be cleared, after which the
     * DELETE could no longer be authorised).
     */
    suspend fun flushPending() {
        pendingJobs.keys.toList().forEach { id ->
            pendingJobs.remove(id)?.cancel()
            runCatching { repository.deleteGoal(id) }
        }
        _hiddenIds.value = emptySet()
    }

    /** Drop every pending delete without sending it (session already invalid). */
    fun cancelAll() {
        pendingJobs.keys.toList().forEach { id -> pendingJobs.remove(id)?.cancel() }
        _hiddenIds.value = emptySet()
    }

    fun pending(): Set<String> = pendingJobs.keys.toSet()

    /**
     * Return true if [id] is currently pending. Convenience for the
     * repository / ViewModel side.
     */
    fun isPending(id: String): Boolean = pendingJobs.containsKey(id)

    companion object {
        /** 5 seconds — matches the Snackbar's `LENGTH_LONG` duration. */
        const val DEFAULT_DELAY_MS: Long = 5_000L
    }
}
