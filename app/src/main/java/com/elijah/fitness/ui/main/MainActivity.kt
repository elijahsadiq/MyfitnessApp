package com.elijah.fitness.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.elijah.fitness.databinding.ActivityMainBinding

/**
 * Single-activity host. Owns the toolbar, the [NavHostFragment] and the
 * bottom navigation. Every user-facing screen in phase 1 is a fragment
 * inside this activity.
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

        // Every bottom-nav destination is a top-level destination — no Up
        // arrow. Log destinations (added in Step 6/7) will use their own
        // AppBarConfiguration so the Up arrow shows there.
        val topLevel = AppBarConfiguration(
            setOf(
                com.elijah.fitness.R.id.dashboardFragment,
                com.elijah.fitness.R.id.workoutHistoryFragment,
                com.elijah.fitness.R.id.goalsFragment,
                com.elijah.fitness.R.id.profileFragment
            )
        )
        binding.toolbar.setupWithNavController(navController, topLevel)
        binding.bottomNavigation.setupWithNavController(navController)
    }
}
