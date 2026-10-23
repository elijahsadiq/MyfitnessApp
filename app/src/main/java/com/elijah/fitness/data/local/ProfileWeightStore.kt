package com.elijah.fitness.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.elijah.fitness.data.repository.UserSettings.ProfileWeight
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.profileWeightDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "profile_prefs")

/**
 * The signed-in user's profile weight, kept on the device (phase 8d).
 * Without it the weight only lived in memory, so a workout saved offline
 * right after a cold start had no weight to work its calories out with
 * and was stored without them for good.
 *
 * Interface-first so unit tests use an in-memory fake. Cleared on
 * sign-out, like every other per-account store.
 */
interface ProfileWeightStore {
    /** The saved weight, or `null` when nothing is saved (signed out, fresh install). */
    suspend fun read(): ProfileWeight?

    suspend fun save(weight: ProfileWeight)

    suspend fun clear()
}

class DataStoreProfileWeightStore(context: Context) : ProfileWeightStore {

    private val store = context.applicationContext.profileWeightDataStore

    override suspend fun read(): ProfileWeight? {
        val prefs = store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        // "Loaded" separates "profile with no weight" (use 70 kg) from
        // "nothing saved" (weight unknown).
        if (prefs[KEY_LOADED] != true) return null
        return ProfileWeight(prefs[KEY_WEIGHT_KG])
    }

    override suspend fun save(weight: ProfileWeight) {
        store.edit { prefs ->
            prefs[KEY_LOADED] = true
            val kg = weight.weightKg
            if (kg != null) prefs[KEY_WEIGHT_KG] = kg else prefs.remove(KEY_WEIGHT_KG)
        }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val KEY_LOADED = booleanPreferencesKey("profile_loaded")
        val KEY_WEIGHT_KG = doublePreferencesKey("weight_kg")
    }
}
