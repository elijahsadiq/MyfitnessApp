package com.elijah.fitness

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.elijah.fitness.notifications.AppNotifications

/**
 * Custom [Application] that owns the manual dependency container.
 *
 * The container is created lazily so unit-tested code paths that don't touch
 * Android context aren't forced to construct it. Fragments and activities
 * reach it via `(requireContext().applicationContext as FitnessApp).container`.
 *
 * Deliberately no Hilt or Koin — this keeps the DI story small enough to
 * explain in the assignment report.
 *
 * Also forces light mode at startup: the Kinetic Glow design is a
 * light-only design (dark hero cards are drawn on top of light surfaces,
 * not swapped in a dark theme). Without this call the app would try to
 * pick `values-night` resources when the system is in dark mode, and
 * we intentionally don't ship a dark values folder.
 */
class FitnessApp : Application() {
    val container: AppContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        AppNotifications.createChannels(this)
        container.wakeUpServer()
    }
}
