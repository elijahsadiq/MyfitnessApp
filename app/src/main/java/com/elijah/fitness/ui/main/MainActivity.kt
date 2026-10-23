package com.elijah.fitness.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.FloatingWindow
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.remote.AuthEventBus
import com.elijah.fitness.databinding.ActivityMainBinding
import com.elijah.fitness.tracking.TrackingState
import com.elijah.fitness.ui.base.SnackKind
import com.elijah.fitness.ui.base.withKind
import com.elijah.fitness.ui.celebration.GoalCelebrationHost
import com.elijah.fitness.ui.celebration.GoalCompleteDialogFragment
import com.elijah.fitness.ui.goals.NewGoalBottomSheet
import com.elijah.fitness.ui.launcher.LauncherActivity
import com.elijah.fitness.ui.tracking.LiveTrackingFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Single-activity host. Owns the toolbar (secondary screens only), the
 * [NavHostFragment], and the bottom navigation, stacked vertically so
 * every screen gets the same, predictable height.
 *
 * ### Chrome per destination
 * Top-level destinations (Home / History / Progress / Goals) draw their
 * own large-title headers and show the bottom nav. Profile shows the
 * shared toolbar with the Up arrow. Full-screen surfaces (the log forms,
 * workout detail, live tracking, full map) draw their own header and
 * hide both. Bottom-sheet destinations (Log a workout, New goal) leave
 * the chrome of the page underneath as it is.
 *
 * ### Central 401 handling
 * Subscribes to [AuthEventBus] and, on an `Unauthorized` event, signs
 * the user out locally and restarts the task through [LauncherActivity].
 *
 * ### Goal-complete pop-up (phase 8d, Step 7)
 * Tells `GoalCelebrationManager` while it is on screen, and shows its
 * pending pop-ups one at a time ([showNextCelebration]): the next one
 * opens after the current one is closed; a rotation keeps the open one.
 *
 * ### Tracking notification
 * The ongoing GPS notification opens this activity with
 * [EXTRA_OPEN_TRACKING]; it jumps straight to the live tracking screen.
 */
class MainActivity : AppCompatActivity(), SnackbarHost, GoalCelebrationHost {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge before the superclass so the window is
        // already laid out behind the system bars by the time any view
        // attaches.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHost = supportFragmentManager
            .findFragmentById(binding.navHostFragment.id) as NavHostFragment
        navController = navHost.navController

        val topLevel = AppBarConfiguration(TOP_LEVEL_DESTINATIONS)
        binding.toolbar.setupWithNavController(navController, topLevel)
        binding.bottomNavigation.setupWithNavController(navController)

        // The bottom nav sits above the gesture bar / 3-button nav bar.
        val bottomNavBasePadding = binding.bottomNavigation.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bottomNavBasePadding + bars.bottom)
            insets
        }

        val insetsController = WindowInsetsControllerCompat(window, binding.root)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            // A bottom sheet / dialog destination floats over the page
            // below it: keep that page's chrome, so it doesn't re-lay out
            // behind the dimmed scrim (and its FAB / snackbars stay put).
            if (destination is FloatingWindow) return@addOnDestinationChangedListener
            binding.bottomNavigation.isVisible = destination.id in TOP_LEVEL_DESTINATIONS
            binding.appBarLayout.isVisible = destination.id in TOOLBAR_DESTINATIONS
            // Dark surfaces drawn up under the status bar need light icons;
            // everything else keeps dark icons on the warm canvas.
            insetsController.isAppearanceLightStatusBars =
                destination.id !in DARK_STATUS_DESTINATIONS
        }

        observeAuthEvents()
        observeCelebrations()
        (application as FitnessApp).container.onSignedInStart()

        if (savedInstanceState == null) handleTrackingIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        (application as FitnessApp).container.goalCelebrationManager.onUiStarted()
    }

    override fun onStop() {
        (application as FitnessApp).container.goalCelebrationManager.onUiStopped()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTrackingIntent(intent)
    }

    /** Short Snackbar for messages that outlive the screen that raised them. */
    fun showSnackbar(message: CharSequence, kind: SnackKind = SnackKind.INFO) {
        val snack = Snackbar.make(binding.mainRoot, message, Snackbar.LENGTH_SHORT).withKind(kind)
        snackbarAnchor()?.let { snack.anchorView = it }
        snack.show()
    }

    /** Snackbars sit just above the bottom nav when it is showing. */
    override fun snackbarAnchor(): View? =
        binding.bottomNavigation.takeIf { it.isVisible }

    private fun handleTrackingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_TRACKING, false) != true) return
        intent.removeExtra(EXTRA_OPEN_TRACKING)
        val container = (application as FitnessApp).container
        val state = container.trackingSession.state.value
        if (state.status == TrackingState.Status.Idle) return
        if (navController.currentDestination?.id == R.id.liveTrackingFragment) return
        navController.navigate(
            R.id.liveTrackingFragment,
            bundleOf(LiveTrackingFragment.ARG_ACTIVITY_TYPE to state.activityType.name),
        )
    }

    private fun observeAuthEvents() {
        val container = (application as FitnessApp).container
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.authEventBus.events.collect { event ->
                    when (event) {
                        AuthEventBus.AuthEvent.Unauthorized -> handleUnauthorized()
                    }
                }
            }
        }
    }

    /** Show the next queued goal-complete pop-up whenever the queue changes. */
    private fun observeCelebrations() {
        val manager = (application as FitnessApp).container.goalCelebrationManager
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                manager.pending.collect { queue ->
                    if (queue.isNotEmpty()) binding.root.post { showNextCelebration() }
                }
            }
        }
    }

    /**
     * Shows the head of the queue unless a pop-up is already open (one at
     * a time; a rotation restores the open one by itself, so it is never
     * shown twice).
     */
    private fun showNextCelebration() {
        if (isFinishing || isDestroyed) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val fm = supportFragmentManager
        if (fm.isStateSaved) return
        try {
            // Let a just-dismissed pop-up finish leaving first.
            fm.executePendingTransactions()
        } catch (_: IllegalStateException) {
            return
        }
        if (fm.findFragmentByTag(GoalCompleteDialogFragment.TAG) != null) return
        val next = (application as FitnessApp).container
            .goalCelebrationManager.pending.value.firstOrNull() ?: return
        try {
            GoalCompleteDialogFragment.newInstance(next).show(fm, GoalCompleteDialogFragment.TAG)
        } catch (_: IllegalStateException) {
            // State saved in between; the queue is still there next start.
        }
    }

    override fun onCelebrationClosed() {
        if (isFinishing || isDestroyed) return
        binding.root.post { showNextCelebration() }
    }

    /**
     * "Set a new goal" on the pop-up: open the New goal sheet over
     * whatever screen is showing, closing another sheet first.
     */
    override fun openNewGoalFromCelebration() {
        if (isFinishing || isDestroyed) return
        try {
            if (navController.currentDestination?.id == R.id.newGoalFragment) return
            while (navController.currentDestination is FloatingWindow) {
                if (!navController.popBackStack()) break
            }
            navController.navigate(R.id.newGoalFragment, NewGoalBottomSheet.argsFor(goalId = null))
        } catch (_: IllegalArgumentException) {
            // Not reachable from here; nothing to open.
        } catch (_: IllegalStateException) {
            // Navigation not ready (activity finishing); ignore.
        }
    }

    private fun handleUnauthorized() {
        val container = (application as FitnessApp).container
        lifecycleScope.launch {
            container.signOutLocally(this@MainActivity)
            startActivity(
                Intent(this@MainActivity, LauncherActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
        }
    }

    companion object {
        private const val EXTRA_OPEN_TRACKING = "open_tracking"

        /** Opens (or brings back) the app on the live tracking screen. */
        fun openTrackingIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_TRACKING, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        private val TOP_LEVEL_DESTINATIONS = setOf(
            R.id.dashboardFragment,
            R.id.workoutHistoryFragment,
            R.id.progressFragment,
            R.id.goalsFragment,
        )

        /**
         * Destinations that show the shared toolbar + Up arrow. The log
         * forms (run / ride / row, swim, workout) draw their own header
         * (phase 8d), so only Profile is left.
         */
        private val TOOLBAR_DESTINATIONS = setOf(
            R.id.profileFragment,
        )

        /**
         * Destinations whose top edge is a dark surface drawn behind
         * the status bar. These need light status-bar icons so the
         * icons stay legible on the dark background.
         */
        private val DARK_STATUS_DESTINATIONS: Set<Int> = setOf(
            R.id.liveTrackingFragment,
            R.id.fullScreenMapFragment,
        )
    }
}

/** Implemented by activities that want Snackbars lifted above a bottom bar. */
interface SnackbarHost {
    fun snackbarAnchor(): View?
}
