package com.elijah.fitness

import android.content.Context
import com.elijah.fitness.data.local.DataStoreLastLoggedActivityStore
import com.elijah.fitness.data.local.DataStoreOnboardingStore
import com.elijah.fitness.data.local.LastLoggedActivityStore
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
import com.elijah.fitness.data.repository.GoalDeleteCoordinator
import com.elijah.fitness.data.repository.WorkoutDeleteCoordinator
import com.elijah.fitness.insights.InsightEngine
import com.elijah.fitness.tracking.LocationTracker
import com.elijah.fitness.tracking.LocationTrackingService
import com.elijah.fitness.tracking.TrackingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    /**
     * Application-level [CoroutineScope]. Jobs launched here outlive any
     * individual fragment or ViewModel — used by the Phase-8b delayed-
     * delete coordinator so leaving the Goals screen within the 5-second
     * Undo window doesn't cancel the pending DELETE.
     *
     * `SupervisorJob` so one failed child job doesn't nuke the scope.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Phase-2 preferences.
    val tokenStore: TokenStore = DataStoreTokenStore(applicationContext)
    val onboardingStore: OnboardingStore = DataStoreOnboardingStore(applicationContext)
    val lastLoggedActivityStore: LastLoggedActivityStore =
        DataStoreLastLoggedActivityStore(applicationContext)

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
     * Phase-8b delayed-delete coordinator. The Goals screen calls
     * `scheduleDelete(goal)` to hide a goal and start a 5-second timer;
     * `undo(id)` cancels the timer. If the timer runs out, the real
     * server-side `DELETE` fires. Running on [appScope] so navigating
     * away from the Goals screen during the Undo window doesn't lose
     * the pending delete.
     */
    val goalDeleteCoordinator: GoalDeleteCoordinator = GoalDeleteCoordinator(
        repository = goalRepository,
        appScope = appScope,
    )

    /**
     * Twin of [goalDeleteCoordinator] for the History screen's swipe-to-
     * delete gesture. Same 5-second delay + Undo semantics, same app-level
     * scope so the DELETE survives navigating away from History.
     */
    val workoutDeleteCoordinator: WorkoutDeleteCoordinator = WorkoutDeleteCoordinator(
        repository = workoutRepository,
        appScope = appScope,
    )

    /**
     * Phase-8 personalised-insights engine. Pure Kotlin — the Home VM
     * calls `rank(workouts, goalsWithProgress)` on each flow emission.
     */
    val insightEngine: InsightEngine = InsightEngine()

    /**
     * Forget everything about the signed-in user on this device: stop a
     * GPS session, drop pending deletes, clear the cached workouts and
     * goals, reset body metrics and remove the token. Used when the
     * server already rejected the session (401) or the account was
     * deleted — nothing is sent to the server.
     */
    suspend fun signOutLocally(context: Context) {
        context.stopService(LocationTrackingService.serviceIntent(context))
        trackingSession.reset()
        workoutDeleteCoordinator.cancelAll()
        goalDeleteCoordinator.cancelAll()
        workoutRepository.clearCache()
        goalRepository.clearCache()
        userSettings.reset()
        tokenStore.clear()
    }

    /**
     * "Log out" from Profile: send any swipe-deletes still waiting on
     * their Undo window, tell the server, then sign out locally.
     */
    suspend fun signOut(context: Context) {
        workoutDeleteCoordinator.flushPending()
        goalDeleteCoordinator.flushPending()
        authRepository.logout()
        signOutLocally(context)
    }

    /**
     * Fire-and-forget `GET /health` at app start. The free cloud server
     * sleeps when idle and needs about a minute to wake; starting that
     * while the splash / login screen is up means it's usually awake by
     * the time the user taps Log in. Failures are ignored on purpose —
     * the real requests report their own errors.
     */
    fun wakeUpServer() {
        appScope.launch {
            runCatching { authApi.health() }
        }
    }
}
