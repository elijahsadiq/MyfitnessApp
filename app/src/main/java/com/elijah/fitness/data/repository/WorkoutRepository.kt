package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.Workout
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to the user's workouts.
 *
 * The interface deliberately hides where data lives. Phase 1 uses
 * `FakeWorkoutRepository` (in-memory); phase 2 swaps in a Retrofit-backed
 * `RemoteWorkoutRepository` talking to the PHP API; phase 4 adds a Room
 * cache and a WorkManager sync layer behind the same signature.
 */
interface WorkoutRepository {

    /** Emits the current workout list whenever it changes. Newest first. */
    fun observeWorkouts(): Flow<List<Workout>>

    /** Persist a new workout. Emits an updated list to [observeWorkouts]. */
    suspend fun addWorkout(workout: Workout)

    /** Look up a workout by its device-generated id; null if none matches. */
    suspend fun getWorkout(id: String): Workout?
}
