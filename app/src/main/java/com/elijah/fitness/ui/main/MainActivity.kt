package com.elijah.fitness.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isTopLevel = destination.id in TOP_LEVEL_DESTINATIONS
            val isImmersive = destination.id == R.id.liveTrackingFragment
            binding.bottomNavigation.isVisible = isTopLevel && !isImmersive
            // Toolbar only for the log flow + standalone Profile; the live
            // tracking screen draws its own close affordance.
            binding.appBarLayout.isVisible =
                destination.id in CHROME_DESTINATIONS && !isImmersive
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

        /** Destinations that show the shared toolbar + Up arrow. */
        val CHROME_DESTINATIONS = setOf(
            R.id.logDistanceWorkoutFragment,
            R.id.logStrengthWorkoutFragment,
            R.id.profileFragment,
            R.id.workoutDetailFragment,
        )
    }
}
