package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.remote.ApiResult
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
     */
    suspend fun addWorkout(workout: Workout): ApiResult<Unit>

    /** Look up a workout by its device-generated id; null if none matches. */
    suspend fun getWorkout(id: String): Workout?
}
