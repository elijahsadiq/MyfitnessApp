package com.elijah.fitness

import android.content.Context
import com.elijah.fitness.data.local.DataStoreOnboardingStore
import com.elijah.fitness.data.local.OnboardingStore
import com.elijah.fitness.data.remote.AuthEventBus
import com.elijah.fitness.data.remote.DataStoreTokenStore
import com.elijah.fitness.data.remote.NetworkModule
import com.elijah.fitness.data.remote.TokenStore
import com.elijah.fitness.data.remote.api.AuthApi
import com.elijah.fitness.data.remote.api.UserApi
import com.elijah.fitness.data.remote.api.WorkoutApi
import com.elijah.fitness.data.repository.AuthRepository
import com.elijah.fitness.data.repository.FakeWorkoutRepository
import com.elijah.fitness.data.repository.RemoteAuthRepository
import com.elijah.fitness.data.repository.UserSettings
import com.elijah.fitness.data.repository.WorkoutRepository

/**
 * Manual dependency container. Instantiated once in [FitnessApp] and read
 * by ViewModel factories that need shared collaborators.
 *
 * Phase 1 wires an in-memory [FakeWorkoutRepository]; phase 2 adds the
 * remote API surface (Auth / User / Workout), a [TokenStore] backed by
 * DataStore, an [AuthEventBus] for global 401 handling, an
 * [OnboardingStore] for the first-launch flag, and an [AuthRepository].
 * Step 8 swaps `workoutRepository` to the remote implementation — the
 * interface stays the same, so nothing above the repository moves.
 */
class AppContainer(applicationContext: Context) {

    // Phase-1 pieces — still active until Step 8 wires the remote repo.
    val workoutRepository: WorkoutRepository = FakeWorkoutRepository()
    val userSettings: UserSettings = UserSettings()

    // Phase-2 preferences.
    val tokenStore: TokenStore = DataStoreTokenStore(applicationContext)
    val onboardingStore: OnboardingStore = DataStoreOnboardingStore(applicationContext)

    // App-wide auth signal bus (Step 5): a 401 from any endpoint fires an
    // Unauthorized event, MainActivity observes it and bounces back to auth.
    val authEventBus: AuthEventBus = AuthEventBus()

    // Networking layer.
    private val retrofit = NetworkModule.buildRetrofit(tokenStore, authEventBus)
    val authApi: AuthApi = NetworkModule.buildAuthApi(retrofit)
    val userApi: UserApi = NetworkModule.buildUserApi(retrofit)
    val workoutApi: WorkoutApi = NetworkModule.buildWorkoutApi(retrofit)

    // Repository over auth endpoints.
    val authRepository: AuthRepository = RemoteAuthRepository(
        authApi = authApi,
        tokenStore = tokenStore,
        onboardingStore = onboardingStore,
    )
}
