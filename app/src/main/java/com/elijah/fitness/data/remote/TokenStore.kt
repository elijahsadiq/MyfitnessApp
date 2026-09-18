package com.elijah.fitness.data.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

/**
 * Persistent home for the bearer token. Backed by androidx.datastore-preferences
 * (a coroutine-friendly, transactional replacement for SharedPreferences).
 *
 * Reads via a `Flow<String?>` — collectors get the current value on
 * subscribe and every subsequent change. `AuthInterceptor` reads
 * synchronously through `blockingCurrent()` since OkHttp interceptors
 * don't run on the main thread.
 */
class TokenStore(context: Context) {

    private val store = context.applicationContext.tokenDataStore

    val token: Flow<String?> = store.data.map { it[TOKEN_KEY] }

    suspend fun save(token: String) {
        store.edit { it[TOKEN_KEY] = token }
    }

    suspend fun clear() {
        store.edit { it.remove(TOKEN_KEY) }
    }

    private companion object {
        val TOKEN_KEY = stringPreferencesKey("auth_token")
    }
}
