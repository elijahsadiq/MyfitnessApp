package com.elijah.fitness

import com.elijah.fitness.data.repository.FakeWorkoutRepository
import com.elijah.fitness.data.repository.UserSettings
import com.elijah.fitness.data.repository.WorkoutRepository

/**
 * Manual dependency container. Instantiated once in [FitnessApp] and read
 * by ViewModel factories that need shared collaborators.
 *
 * Phase 1 wires an in-memory [FakeWorkoutRepository]; phase 2 replaces it
 * with a Retrofit-backed implementation behind the same
 * [WorkoutRepository] interface.
 */
class AppContainer {
    val workoutRepository: WorkoutRepository = FakeWorkoutRepository()
    val userSettings: UserSettings = UserSettings()
}
