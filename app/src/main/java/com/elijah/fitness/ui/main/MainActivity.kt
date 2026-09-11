package com.elijah.fitness.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.elijah.fitness.R
import com.elijah.fitness.databinding.ActivityMainBinding

/**
 * Single-activity host. Owns the toolbar, the [NavHostFragment] and the
 * bottom navigation. Log destinations hide the bottom nav and get an Up
 * arrow (the latter comes for free from `AppBarConfiguration`).
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
    }

    private companion object {
        val LOG_DESTINATIONS = setOf(
            R.id.logDistanceWorkoutFragment,
            R.id.logStrengthWorkoutFragment
        )
    }
}
