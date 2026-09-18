package com.elijah.fitness.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding_prefs")

/**
 * "Have they seen the welcome / onboarding pages yet?" — a single boolean
 * DataStore flag consulted by [com.elijah.fitness.ui.launcher.LauncherActivity]
 * on cold start.
 *
 * Kept as an interface (production impl: [DataStoreOnboardingStore]) so
 * unit tests can substitute a plain in-memory fake without pulling
 * DataStore or an Android context into the test classpath.
 */
interface OnboardingStore {
    val hasCompletedOnboarding: Flow<Boolean>
    suspend fun markCompleted()
}

class DataStoreOnboardingStore(context: Context) : OnboardingStore {

    private val store = context.applicationContext.onboardingDataStore

    override val hasCompletedOnboarding: Flow<Boolean> =
        store.data.map { it[KEY_COMPLETED] ?: false }

    override suspend fun markCompleted() {
        store.edit { it[KEY_COMPLETED] = true }
    }

    private companion object {
        val KEY_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
