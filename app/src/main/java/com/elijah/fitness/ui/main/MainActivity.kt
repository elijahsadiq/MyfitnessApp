package com.elijah.fitness.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.remote.AuthEventBus
import com.elijah.fitness.databinding.ActivityMainBinding
import com.elijah.fitness.ui.launcher.LauncherActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Single-activity host. Owns the toolbar (only shown on log destinations),
 * the [NavHostFragment], and the bottom navigation.
 *
 * ### Chrome per destination (Step 9)
 * Top-level destinations (Home / History / Progress / Goals) draw their
 * own headers, so the shared toolbar stays hidden on those. Log
 * destinations and the standalone Profile page show the toolbar with
 * the Up arrow, and hide the bottom nav.
 *
 * ### Central 401 handling (Step 5)
 * Subscribes to [AuthEventBus] and, on an `Unauthorized` event, clears
 * the local token and restarts the task through [LauncherActivity].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Phase 8c step 2 — enable edge-to-edge before the superclass so
        // the window is already laid out behind the system bars by the
        // time any view attaches. This replaces the pre-8c reliance on
        // the theme's `android:statusBarColor` + `windowLightStatusBar`,
        // both deprecated in SDK 35.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHost = supportFragmentManager
            .findFragmentById(binding.navHostFragment.id) as NavHostFragment
        val navController = navHost.navController

        val topLevel = AppBarConfiguration(
            setOf(
                R.id.dashboardFragment,
                R.id.workoutHistoryFragment,
                R.id.progressFragment,
                R.id.goalsFragment,
            )
        )
        binding.toolbar.setupWithNavController(navController, topLevel)
        binding.bottomNavigation.setupWithNavController(navController)

        // Push the BottomNavigationView above the 3-button-nav gesture
        // bar on devices that have one. Edge-to-edge means the activity
        // owns the inset — the view was previously below an opaque
        // navigation bar from the theme.
        val bottomNavBasePadding = binding.bottomNavigation.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bottomNavBasePadding + bars.bottom)
            insets
        }

        val insetsController = WindowInsetsControllerCompat(window, binding.root)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isTopLevel = destination.id in TOP_LEVEL_DESTINATIONS
            val isImmersive = destination.id == R.id.liveTrackingFragment
            binding.bottomNavigation.isVisible = isTopLevel && !isImmersive
            // Toolbar only for the log flow; the standalone Profile and
            // the workout-detail screen draw their own headers (phase 8c
            // step 2 removes WorkoutDetail from this set — single header
            // per screen).
            binding.appBarLayout.isVisible =
                destination.id in CHROME_DESTINATIONS && !isImmersive

            // Status-bar icon colour — the handful of destinations that
            // draw a dark surface up under the status bar want *light*
            // icons; every other screen keeps the dark icons that work
            // on the warm canvas. Flipping it per destination (rather
            // than per top-level-only destination) means the switch
            // fires correctly however the user navigates in.
            insetsController.isAppearanceLightStatusBars =
                destination.id !in DARK_STATUS_DESTINATIONS
        }

        observeAuthEvents()
    }

    /** Anchor a short Snackbar on the activity's CoordinatorLayout. */
    fun showSnackbar(message: CharSequence) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
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

    private fun handleUnauthorized() {
        val container = (application as FitnessApp).container
        lifecycleScope.launch { container.tokenStore.clear() }
        startActivity(
            Intent(this, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private companion object {
        val TOP_LEVEL_DESTINATIONS = setOf(
            R.id.dashboardFragment,
            R.id.workoutHistoryFragment,
            R.id.progressFragment,
            R.id.goalsFragment,
        )

        /**
         * Destinations that show the shared toolbar + Up arrow. Phase
         * 8c step 7 drops `workoutDetailFragment` — the rebuilt detail
         * screen draws its own header + back arrow (one header per
         * screen, per the user's Step 0 note).
         */
        val CHROME_DESTINATIONS = setOf(
            R.id.logDistanceWorkoutFragment,
            R.id.logStrengthWorkoutFragment,
            R.id.profileFragment,
        )

        /**
         * Destinations whose top edge is a dark surface drawn behind
         * the status bar. These need light status-bar icons so the
         * icons stay legible on the dark background.
         */
        val DARK_STATUS_DESTINATIONS: Set<Int> = setOf(
            R.id.liveTrackingFragment,
            R.id.fullScreenMapFragment,
        )
    }
}
