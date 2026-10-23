package com.elijah.fitness

import android.content.Context
import com.elijah.fitness.data.local.DataStoreLastLoggedActivityStore
import com.elijah.fitness.data.local.DataStoreOnboardingStore
import com.elijah.fitness.data.local.LastLoggedActivityStore
import com.elijah.fitness.data.local.OnboardingStore
import com.elijah.fitness.data.local.DataStoreProfileWeightStore
import com.elijah.fitness.data.local.ProfileWeightStore
import com.elijah.fitness.data.local.db.FitnessDatabase
import com.elijah.fitness.data.model.ActivityType
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
import com.elijah.fitness.data.repository.OfflineFirstWorkoutRepository
import com.elijah.fitness.data.repository.ProfileWeightPersistence
import com.elijah.fitness.data.repository.RemoteAuthRepository
import com.elijah.fitness.data.repository.RemoteGoalRepository
import com.elijah.fitness.data.repository.RemoteWorkoutRepository
import com.elijah.fitness.data.repository.UserSettings
import com.elijah.fitness.data.repository.WorkoutRepository
import com.elijah.fitness.data.repository.GoalDeleteCoordinator
import com.elijah.fitness.data.repository.WorkoutDeleteCoordinator
import com.elijah.fitness.data.sync.NetworkStatus
import com.elijah.fitness.data.sync.SyncScheduler
import com.elijah.fitness.data.sync.WorkManagerSyncScheduler
import com.elijah.fitness.insights.InsightEngine
import com.elijah.fitness.notifications.AppNotifications
import com.elijah.fitness.notifications.DailyReminderScheduler
import com.elijah.fitness.notifications.DataStoreReminderSettingsStore
import com.elijah.fitness.notifications.GoalCelebrationManager
import com.elijah.fitness.data.local.CelebrationSeedStore
import com.elijah.fitness.data.local.DataStoreCelebrationSeedStore
import com.elijah.fitness.notifications.ReminderSettingsStore
import com.elijah.fitness.tracking.LocationTracker
import com.elijah.fitness.tracking.LocationTrackingService
import com.elijah.fitness.tracking.TrackingSession
import com.elijah.fitness.ui.goals.displayTitle
import com.elijah.fitness.util.ResourcesTextResolver
import com.elijah.fitness.util.TextResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    /**
     * Phase 8d: a workout of [type] was just saved, so the Log a workout
     * sheet opens on that activity next time. Called by every save path
     * (manual run / ride / row, GPS, swim, workout) only after the save
     * worked — backing out of a form changes nothing. Runs on [appScope]
     * so the write finishes even though the screen closes straight away;
     * a failed write only loses the preselection.
     */
    fun rememberLoggedActivity(type: ActivityType) {
        appScope.launch {
            try {
                lastLoggedActivityStore.setLastActivity(type)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Only a preselection for next time; nothing to tell the user.
            }
        }
    }

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
     * Phase 8d: the profile weight is also kept on the device, so calories
     * are stored with workouts saved offline right after a cold start
     * (before `/me` can answer). Restored here, at app start.
     */
    private val profileWeightStore: ProfileWeightStore =
        DataStoreProfileWeightStore(applicationContext)
    private val profileWeightPersistence =
        ProfileWeightPersistence(userSettings, profileWeightStore, appScope)

    init {
        profileWeightPersistence.start()
    }

    /**
     * strings.xml / plurals.xml for code with no Context: ViewModels,
     * the insight engine and the goal-title builder (phase 8d).
     */
    val text: TextResolver = ResourcesTextResolver(applicationContext.resources)

    // Phase 10 — offline store and background sync.
    val database: FitnessDatabase = FitnessDatabase.build(applicationContext)
    val syncScheduler: SyncScheduler = WorkManagerSyncScheduler(applicationContext)
    private val networkStatus = NetworkStatus(applicationContext)

    /**
     * Offline-first repository (phase 10): screens read the Room copy,
     * writes land there first, and WorkManager uploads what was saved
     * without a connection. It replaced [RemoteWorkoutRepository] with
     * this one line; every screen still depends only on
     * [WorkoutRepository]. `FakeWorkoutRepository()` still drops in here
     * for UI work without a backend.
     */
    val workoutRepository: WorkoutRepository = OfflineFirstWorkoutRepository(
        dao = database.workoutDao(),
        api = workoutApi,
        syncScheduler = syncScheduler,
        scope = appScope,
        isOnline = networkStatus::isOnline,
    )

    /**
     * API-backed goal repository (phase 7 step 3) with a Room copy of
     * the last list (phase 10), so goal progress still shows offline.
     */
    val goalRepository: GoalRepository = RemoteGoalRepository(goalApi, database.goalCacheDao())

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
    val insightEngine: InsightEngine = InsightEngine(text)

    // -----------------------------------------------------------------
    // Phase 10 — notifications (local, posted by the phone).
    // -----------------------------------------------------------------
    val reminderSettingsStore: ReminderSettingsStore =
        DataStoreReminderSettingsStore(applicationContext)
    val dailyReminderScheduler: DailyReminderScheduler = DailyReminderScheduler(applicationContext)

    /** First-run flag for the goal-complete pop-up (kept on sign-out: it describes the install). */
    private val celebrationSeedStore: CelebrationSeedStore =
        DataStoreCelebrationSeedStore(applicationContext)

    /**
     * Phase 8d, Step 7: the one owner of "a goal was just reached". On
     * screen, MainActivity shows the goal-complete pop-up from its
     * `pending` queue; in the background it posts the "Goal achieved"
     * notification (when goal alerts are on). Started by MainActivity
     * once signed in ([onSignedInStart]).
     */
    val goalCelebrationManager: GoalCelebrationManager = GoalCelebrationManager(
        calculator = goalProgressCalculator,
        goalRepository = goalRepository,
        workoutRepository = workoutRepository,
        seedStore = celebrationSeedStore,
        alertsEnabled = reminderSettingsStore.settings.map { it.goalAlerts },
        scope = appScope,
        weightKg = { userSettings.bodyWeightKg.value },
        titleOf = { it.displayTitle(text) },
        postNotification = { id, title ->
            AppNotifications.showGoalAchieved(applicationContext, id, title)
        },
    )

    /**
     * Forget everything about the signed-in user on this device: stop a
     * GPS session, drop pending deletes, clear the cached workouts and
     * goals, reset body metrics (and the saved profile weight) and remove
     * the token. Used when the
     * server already rejected the session (401) or the account was
     * deleted — nothing is sent to the server.
     */
    suspend fun signOutLocally(context: Context) {
        context.stopService(LocationTrackingService.serviceIntent(context))
        trackingSession.reset()
        goalCelebrationManager.stop()
        dailyReminderScheduler.cancel()
        reminderSettingsStore.reset()
        workoutDeleteCoordinator.cancelAll()
        goalDeleteCoordinator.cancelAll()
        workoutRepository.clearCache()
        goalRepository.clearCache()
        lastLoggedActivityStore.clear()
        AppNotifications.cancelAll(context)
        userSettings.reset()
        profileWeightPersistence.clear()
        tokenStore.clear()
    }

    /**
     * "Log out" from Profile: send any swipe-deletes still waiting on
     * their Undo window, upload workouts saved offline while the token
     * still works, tell the server, then sign out locally.
     */
    suspend fun signOut(context: Context) {
        workoutDeleteCoordinator.flushPending()
        goalDeleteCoordinator.flushPending()
        workoutRepository.syncPending()
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
    /**
     * Called when the signed-in app opens: start goal alerts, load the
     * latest goals for them, and re-book the daily reminder if it's on
     * (re-booking keeps it at the chosen time after a time-zone change).
     */
    fun onSignedInStart() {
        goalCelebrationManager.start()
        appScope.launch {
            goalRepository.refresh()
            val settings = reminderSettingsStore.settings.first()
            if (settings.dailyReminder) dailyReminderScheduler.schedule(settings.reminderTime)
        }
    }

    fun wakeUpServer() {
        appScope.launch {
            runCatching { authApi.health() }
        }
    }
}
