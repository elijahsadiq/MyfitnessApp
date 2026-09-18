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
import com.elijah.fitness.data.repository.RemoteWorkoutRepository
import com.elijah.fitness.data.repository.UserSettings
import com.elijah.fitness.data.repository.WorkoutRepository

/**
 * Manual dependency container. Instantiated once in [FitnessApp] and read
 * by ViewModel factories that need shared collaborators.
 *
 * ### Repository swap (Step 8)
 * `workoutRepository` is now the [RemoteWorkoutRepository] talking to the
 * PHP API. [FakeWorkoutRepository] stays in the codebase — it's used by
 * unit tests and can be reinstated here for offline development by
 * flipping the type on one line. That swap is exactly the payoff of the
 * [WorkoutRepository] interface introduced in phase 1: no UI code moves.
 */
class AppContainer(applicationContext: Context) {

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

    // Domain surface.
    val userSettings: UserSettings = UserSettings()

    /**
     * Real API-backed repository. To develop offline, swap in
     * `FakeWorkoutRepository()` here — every screen still compiles and
     * runs unchanged because they only depend on [WorkoutRepository].
     */
    val workoutRepository: WorkoutRepository = RemoteWorkoutRepository(workoutApi)

    val authRepository: AuthRepository = RemoteAuthRepository(
        authApi = authApi,
        tokenStore = tokenStore,
        onboardingStore = onboardingStore,
    )
}
