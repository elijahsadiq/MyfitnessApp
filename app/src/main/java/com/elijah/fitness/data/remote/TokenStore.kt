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
 * Persistent home for the bearer token. Kept as an interface so unit
 * tests can swap in a fake without a DataStore or an Android context.
 * Production uses [DataStoreTokenStore]; tests use `FakeTokenStore`.
 */
interface TokenStore {
    val token: Flow<String?>
    suspend fun save(token: String)
    suspend fun clear()
}

/**
 * androidx.datastore-preferences implementation of [TokenStore]. Reads
 * via a `Flow<String?>` — collectors get the current value on subscribe
 * and every subsequent change. `AuthInterceptor` reads synchronously via
 * `runBlocking { token.first() }`; OkHttp interceptors don't run on the
 * main thread so that's safe.
 */
class DataStoreTokenStore(context: Context) : TokenStore {

    private val store = context.applicationContext.tokenDataStore

    override val token: Flow<String?> = store.data.map { it[TOKEN_KEY] }

    override suspend fun save(token: String) {
        store.edit { it[TOKEN_KEY] = token }
    }

    override suspend fun clear() {
        store.edit { it.remove(TOKEN_KEY) }
    }

    private companion object {
        val TOKEN_KEY = stringPreferencesKey("auth_token")
    }
}
