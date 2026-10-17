package com.elijah.fitness.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import com.elijah.fitness.R
import com.elijah.fitness.databinding.ActivityAuthBinding
import com.elijah.fitness.ui.base.SnackKind
import com.elijah.fitness.ui.base.withKind
import com.elijah.fitness.ui.main.MainActivity
import com.google.android.material.snackbar.Snackbar

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

        // Push bottom-anchored content above the gesture / 3-button nav
        // bar — and above the keyboard while it's open, so the form
        // shrinks and the focused field scrolls into view instead of
        // hiding behind the keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(binding.authNavHost) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = maxOf(bars.bottom, ime.bottom),
            )
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

        // Onboarding + location priming are dark screens: paint the host
        // (which shows under the gesture / nav bar) to match, and flip the
        // system-bar icons to light. Log in / Register are light.
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val dark = destination.id in DARK_DESTINATIONS
            binding.authNavHost.setBackgroundResource(
                if (dark) R.color.surface_dark else R.color.background_warm
            )
            insetsController.isAppearanceLightStatusBars = !dark
            insetsController.isAppearanceLightNavigationBars = !dark
        }

        // Arrived here straight after deleting the account: confirm it.
        // Only on first creation, so it doesn't repeat after rotation.
        if (savedInstanceState == null &&
            intent.getBooleanExtra(EXTRA_ACCOUNT_DELETED, false)
        ) {
            binding.root.post {
                Snackbar.make(binding.root, R.string.auth_account_deleted, Snackbar.LENGTH_LONG)
                    .withKind(SnackKind.SUCCESS)
                    .show()
            }
        }
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

        /** Set when Profile hands over after a successful account delete. */
        const val EXTRA_ACCOUNT_DELETED = "account_deleted"

        private val DARK_DESTINATIONS = setOf(
            R.id.onboardingHostFragment,
            R.id.locationPermissionFragment,
        )
    }
}
