package com.elijah.fitness

import android.content.Context
import com.elijah.fitness.data.local.DataStoreOnboardingStore
import com.elijah.fitness.data.local.OnboardingStore
import com.elijah.fitness.data.remote.AuthEventBus
import com.elijah.fitness.data.remote.DataStoreTokenStore
import com.elijah.fitness.data.remote.NetworkModule
import com.elijah.fitness.data.remote.TokenStore
import com.elijah.fitness.data.model.GoalProgressCalculator
import com.elijah.fitness.data.remote.api.AuthApi
import com.elijah.fitness.data.remote.api.GoalApi
import com.elijah.fitness.data.remote.api.UserApi
import com.elijah.fitness.data.remote.api.WorkoutApi
import com.elijah.fitness.data.repository.AuthRepository
import com.elijah.fitness.data.repository.FakeWorkoutRepository
import com.elijah.fitness.data.repository.GoalRepository
import com.elijah.fitness.data.repository.RemoteAuthRepository
import com.elijah.fitness.data.repository.RemoteGoalRepository
import com.elijah.fitness.data.repository.RemoteWorkoutRepository
import com.elijah.fitness.data.repository.UserSettings
import com.elijah.fitness.data.repository.WorkoutRepository
import com.elijah.fitness.insights.InsightEngine
import com.elijah.fitness.tracking.LocationTracker
import com.elijah.fitness.tracking.TrackingSession

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
    val goalApi: GoalApi = NetworkModule.buildGoalApi(retrofit)

    // Domain surface.
    val userSettings: UserSettings = UserSettings()

    /**
     * Real API-backed repository. To develop offline, swap in
     * `FakeWorkoutRepository()` here — every screen still compiles and
     * runs unchanged because they only depend on [WorkoutRepository].
     */
    val workoutRepository: WorkoutRepository = RemoteWorkoutRepository(workoutApi)

    /**
     * Real API-backed goal repository (phase 7 step 3). Same swap
     * property as [workoutRepository] — a `FakeGoalRepository()` line
     * makes the whole goals feature offline-friendly.
     */
    val goalRepository: GoalRepository = RemoteGoalRepository(goalApi)

    /**
     * Client-side progress calculation. Combines the goals stream and
     * the workouts stream so any change to either recomputes progress
     * for every affected goal (see phase-7 scope decision: progress
     * lives on the client, not the server).
     */
    val goalProgressCalculator: GoalProgressCalculator = GoalProgressCalculator(
        goalRepository = goalRepository,
        workoutRepository = workoutRepository,
    )

    val authRepository: AuthRepository = RemoteAuthRepository(
        authApi = authApi,
        tokenStore = tokenStore,
        onboardingStore = onboardingStore,
        userSettings = userSettings,
    )

    // -----------------------------------------------------------------
    // Phase 8a — live tracking singletons.
    //
    // Single `TrackingSession` per process so the live screen, the
    // foreground service, and the save step all see the same state
    // flow. `LocationTracker` is also singleton-scoped — it holds the
    // fused provider's callback registration, which must survive a
    // rotation without being torn down and recreated.
    // -----------------------------------------------------------------
    val trackingSession: TrackingSession = TrackingSession()
    val locationTracker: LocationTracker = LocationTracker(applicationContext)

    /**
     * Phase-8 personalised-insights engine. Pure Kotlin — the Home VM
     * calls `rank(workouts, goalsWithProgress)` on each flow emission.
     */
    val insightEngine: InsightEngine = InsightEngine()
}
