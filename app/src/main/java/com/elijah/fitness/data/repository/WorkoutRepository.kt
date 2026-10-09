package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.model.WorkoutDetail
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.tracking.TrackPoint
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to the user's workouts.
 *
 * The interface deliberately hides where data lives. Phase 1 used
 * `FakeWorkoutRepository` (in-memory); phase 2 swaps in
 * `RemoteWorkoutRepository` talking to the PHP API. Phase 4 will slot in
 * a Room cache and a WorkManager sync behind the same signature — the
 * ViewModels above never move.
 *
 * Phase-2 additions:
 *  - [refresh] triggers a network fetch and returns an [ApiResult] so the
 *    ViewModel can render loading / error states without observing raw
 *    exceptions.
 *  - [addWorkout] now returns [ApiResult] so log fragments can keep the
 *    user's typed input on screen and offer a Retry when the POST fails.
 */
interface WorkoutRepository {

    /** Emits the current workout list whenever it changes. Newest first. */
    fun observeWorkouts(): Flow<List<Workout>>

    /**
     * Fetch the latest list from the source of truth. On success, the
     * result is pushed into [observeWorkouts]; the return value carries
     * failure information for the UI.
     */
    suspend fun refresh(): ApiResult<Unit>

    /**
     * Persist a new workout. On success, [observeWorkouts] emits an
     * updated list including it. On failure, the returned [ApiResult]
     * carries the reason so the log form can offer a Retry.
     *
     * @param routePoints optional GPS track for distance workouts; empty
     *   for manual logs and strength workouts.
     */
    suspend fun addWorkout(
        workout: Workout,
        routePoints: List<TrackPoint> = emptyList(),
    ): ApiResult<Unit>

    /** Look up a workout by its device-generated id; null if none matches. */
    suspend fun getWorkout(id: String): Workout?

    /**
     * Fetch the full detail (workout + route points) for one workout. Route
     * points come from the server because the list endpoint omits them.
     * Returns [ApiResult.Error] with a lookup message if the clientUuid
     * isn't known locally (e.g. deep link to a deleted workout).
     */
    suspend fun getWorkoutDetail(clientUuid: String): ApiResult<WorkoutDetail>

    /**
     * Delete a workout by its device-generated clientUuid. On success,
     * [observeWorkouts] re-emits without the row. Lookup failures return
     * `ApiResult.Error(status = 404)` so the UI can treat a stale swipe
     * as a soft no-op.
     */
    suspend fun deleteWorkout(clientUuid: String): ApiResult<Unit>

    /**
     * Partial update — only the user-visible notes string. Returns the
     * refreshed [Workout] on success so the caller can rebuild its detail
     * view without a second round trip. [observeWorkouts] also re-emits
     * so other screens pick up the change.
     *
     * `notes` may be `null` or a blank string to clear a note.
     */
    suspend fun updateNotes(clientUuid: String, notes: String?): ApiResult<Workout>

    /**
     * Forget every cached workout. Called on sign-out so the next account
     * to sign in on this phone never sees the previous user's history.
     */
    fun clearCache() {}
}
