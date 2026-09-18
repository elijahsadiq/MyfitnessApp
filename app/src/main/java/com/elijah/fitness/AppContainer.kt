package com.elijah.fitness

import android.content.Context
import com.elijah.fitness.data.remote.NetworkModule
import com.elijah.fitness.data.remote.TokenStore
import com.elijah.fitness.data.remote.api.AuthApi
import com.elijah.fitness.data.remote.api.UserApi
import com.elijah.fitness.data.remote.api.WorkoutApi
import com.elijah.fitness.data.repository.FakeWorkoutRepository
import com.elijah.fitness.data.repository.UserSettings
import com.elijah.fitness.data.repository.WorkoutRepository

/**
 * Manual dependency container. Instantiated once in [FitnessApp] and read
 * by ViewModel factories that need shared collaborators.
 *
 * Phase 1 wires an in-memory [FakeWorkoutRepository]; phase 2 adds the
 * remote API surface (Auth / User / Workout) and a [TokenStore] backed by
 * DataStore. Step 8 swaps `workoutRepository` to the remote implementation
 * — the interface stays the same, so nothing above the repository moves.
 */
class AppContainer(applicationContext: Context) {

    // Phase-1 pieces — still active until Step 8 wires the remote repo.
    val workoutRepository: WorkoutRepository = FakeWorkoutRepository()
    val userSettings: UserSettings = UserSettings()

    // Phase-2 networking layer.
    val tokenStore: TokenStore = TokenStore(applicationContext)
    private val retrofit = NetworkModule.buildRetrofit(tokenStore)
    val authApi: AuthApi = NetworkModule.buildAuthApi(retrofit)
    val userApi: UserApi = NetworkModule.buildUserApi(retrofit)
    val workoutApi: WorkoutApi = NetworkModule.buildWorkoutApi(retrofit)
}
