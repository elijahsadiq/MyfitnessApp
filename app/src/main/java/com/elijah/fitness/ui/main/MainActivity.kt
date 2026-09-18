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
 * Single-activity host. Owns the toolbar, the [NavHostFragment] and the
 * bottom navigation. Log destinations hide the bottom nav and get an Up
 * arrow (the latter comes for free from `AppBarConfiguration`).
 *
 * Also owns the app-wide "your session expired" reaction: it subscribes
 * to [AuthEventBus] and, on an `Unauthorized` event, clears the local
 * token (belt-and-braces — the interceptor already saw the 401) and
 * restarts the task through [LauncherActivity], which will now route to
 * `AuthActivity` because the token is gone.
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
                R.id.goalsFragment,
                R.id.profileFragment
            )
        )
        binding.toolbar.setupWithNavController(navController, topLevel)
        binding.bottomNavigation.setupWithNavController(navController)

        // Hide the bottom nav on log destinations. Everything else keeps it.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigation.isVisible = destination.id !in LOG_DESTINATIONS
        }

        observeAuthEvents()
    }

    /**
     * Anchor a short Snackbar on the activity's CoordinatorLayout. Fragments
     * call this after popping the back stack so the Snackbar survives their
     * disappearance.
     */
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
        // Extra safety: clear the local token even though the interceptor
        // that surfaced the 401 doesn't know about the store. Fire-and-forget
        // is fine — the launcher will read whichever state is current on the
        // next boot.
        val container = (application as FitnessApp).container
        lifecycleScope.launch { container.tokenStore.clear() }
        startActivity(
            Intent(this, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private companion object {
        val LOG_DESTINATIONS = setOf(
            R.id.logDistanceWorkoutFragment,
            R.id.logStrengthWorkoutFragment
        )
    }
}
