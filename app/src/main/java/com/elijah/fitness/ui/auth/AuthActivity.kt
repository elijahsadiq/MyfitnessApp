package com.elijah.fitness.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import com.elijah.fitness.R
import com.elijah.fitness.databinding.ActivityAuthBinding
import com.elijah.fitness.ui.main.MainActivity

/**
 * Hosts the auth-side nav graph (onboarding + location priming + login
 * + register). The start destination is picked from an intent extra so
 * the launcher can decide whether the current run is a "first ever"
 * launch (onboarding first) or a returning-user launch (straight to
 * login).
 *
 * Runs edge-to-edge: onboarding pages draw a hero image up under the
 * status bar. Individual dark-surface fragments call
 * `WindowInsetsControllerCompat.isAppearanceLightStatusBars = false`
 * while attached so system icons stay legible on the dark background.
 *
 * On a successful sign-in the child fragments call [goToMain] which
 * clears this activity out of the task stack.
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Phase 8c step 2 — single androidx.activity call that replaces
        // the three deprecated `window.*` setters below (Android 15 /
        // SDK 35 marks `statusBarColor` + `navigationBarColor` as
        // deprecated; `enableEdgeToEdge()` is the recommended path).
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Push bottom-anchored content up above the gesture-nav pill so
        // buttons aren't trapped under it. Onboarding pages that need to
        // reach the top edge (hero image) opt out via android:fitsSystemWindows.
        ViewCompat.setOnApplyWindowInsetsListener(binding.authNavHost) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bars.bottom)
            insets
        }

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
