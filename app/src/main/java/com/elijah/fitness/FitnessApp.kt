package com.elijah.fitness

import android.app.Application

/**
 * Custom [Application] that owns the manual dependency container.
 *
 * The container is created lazily so unit-tested code paths that don't touch
 * Android context aren't forced to construct it. Fragments and activities
 * reach it via `(requireContext().applicationContext as FitnessApp).container`.
 *
 * Deliberately no Hilt or Koin — this keeps the DI story small enough to
 * explain in the assignment report.
 */
class FitnessApp : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
