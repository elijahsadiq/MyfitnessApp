package com.elijah.fitness.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.celebrationDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "celebration_prefs")

/**
 * Remembers whether the goal-complete pop-up has done its first-run pass
 * on this install (phase 8d, Step 7). On that first pass every goal that
 * is already complete is quietly marked as celebrated, so an existing
 * user doesn't get a pile of old pop-ups. Kept on sign-out on purpose:
 * it describes the install, not the account.
 */
interface CelebrationSeedStore {
    suspend fun isSeeded(): Boolean
    suspend fun markSeeded()
}

class DataStoreCelebrationSeedStore(context: Context) : CelebrationSeedStore {

    private val store = context.applicationContext.celebrationDataStore

    override suspend fun isSeeded(): Boolean =
        store.data.map { it[KEY_SEEDED] ?: false }.first()

    override suspend fun markSeeded() {
        store.edit { it[KEY_SEEDED] = true }
    }

    private companion object {
        val KEY_SEEDED = booleanPreferencesKey("celebrations_seeded")
    }
}
