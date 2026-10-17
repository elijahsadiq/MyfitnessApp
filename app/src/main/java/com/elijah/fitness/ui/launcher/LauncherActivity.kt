package com.elijah.fitness.ui.launcher

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.ui.auth.AuthActivity
import com.elijah.fitness.ui.main.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The manifest LAUNCHER. Its only job is to decide *which* activity the
 * user actually sees — [MainActivity] if a token is stored, otherwise
 * [AuthActivity] (with the onboarding start destination on first launch).
 *
 * Kept as a distinct activity (rather than routing from `FitnessApp` or
 * inside `MainActivity`) so the task stack starts fresh each time and
 * neither of the two "real" activities ever appears momentarily.
 */
class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as FitnessApp).container

        lifecycleScope.launch {
            val token = container.tokenStore.token.first()
            val onboarded = container.onboardingStore.hasCompletedOnboarding.first()

            val next = if (!token.isNullOrBlank()) {
                Intent(this@LauncherActivity, MainActivity::class.java)
            } else {
                Intent(this@LauncherActivity, AuthActivity::class.java).apply {
                    putExtra(AuthActivity.EXTRA_SHOW_ONBOARDING, !onboarded)
                    putExtra(
                        AuthActivity.EXTRA_ACCOUNT_DELETED,
                        intent.getBooleanExtra(AuthActivity.EXTRA_ACCOUNT_DELETED, false),
                    )
                }
            }
            startActivity(next)
            finish()
        }
    }
}
