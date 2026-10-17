package com.elijah.fitness.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.elijah.fitness.data.model.ActivityType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lastActivityDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "log_prefs")

/**
 * Remembers the most recent activity the user logged. The "Log a
 * workout" sheet preselects the matching tile on open (phase 8c
 * step 4). Interface-first so unit tests can substitute an in-memory
 * fake.
 */
interface LastLoggedActivityStore {
    /** Emits the stored activity, or `null` if nothing has been logged yet. */
    val lastActivity: Flow<ActivityType?>

    suspend fun setLastActivity(type: ActivityType)

    /** Sign-out: forget the preselection so it can't leak to the next account. */
    suspend fun clear()
}

class DataStoreLastLoggedActivityStore(
    context: Context,
) : LastLoggedActivityStore {

    private val store = context.applicationContext.lastActivityDataStore

    override val lastActivity: Flow<ActivityType?> = store.data.map { prefs ->
        prefs[KEY_LAST]?.let { stored ->
            runCatching { ActivityType.valueOf(stored) }.getOrNull()
        }
    }

    override suspend fun setLastActivity(type: ActivityType) {
        store.edit { it[KEY_LAST] = type.name }
    }

    override suspend fun clear() {
        store.edit { it.remove(KEY_LAST) }
    }

    private companion object {
        val KEY_LAST = stringPreferencesKey("last_logged_activity")
    }
}
