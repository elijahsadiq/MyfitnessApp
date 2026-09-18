package com.elijah.fitness.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.elijah.fitness.R
import com.elijah.fitness.databinding.ActivityAuthBinding
import com.elijah.fitness.ui.main.MainActivity

/**
 * Hosts the auth-side nav graph (onboarding + login + register). The
 * start destination is picked from an intent extra so the launcher can
 * decide whether the current run is a "first ever" launch (onboarding
 * first) or a returning-user launch (straight to login).
 *
 * On a successful sign-in the child fragments call [goToMain] which
 * clears this activity out of the task stack.
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(binding.authNavHost.id) as NavHostFragment
        val navController = navHost.navController

        val graph = navController.navInflater.inflate(R.navigation.auth_nav_graph)
        graph.setStartDestination(
            if (intent.getBooleanExtra(EXTRA_SHOW_ONBOARDING, false)) {
                R.id.onboardingHostFragment
            } else {
                R.id.loginFragment
            }
        )
        navController.graph = graph
    }

    /** Called by child fragments after register / login succeeds. */
    fun goToMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        const val EXTRA_SHOW_ONBOARDING = "show_onboarding"
    }
}
