package com.elijah.fitness.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class UserSettingsTest {

    @Test
    fun `default body weight is 70 kg`() {
        val settings = UserSettings()
        assertEquals(70.0, settings.bodyWeightKg.value, 0.0)
    }

    @Test
    fun `body weight can be updated`() {
        val settings = UserSettings()
        settings.updateBodyWeightKg(82.5)
        assertEquals(82.5, settings.bodyWeightKg.value, 0.0)
    }

    // --------- Phase 8d: weight fallback flag ---------

    @Test
    fun `weight is a fallback until a real weight arrives`() {
        val settings = UserSettings()
        assertEquals(true, settings.weightIsFallback.value)
        settings.hydrateFrom(weightKg = 72.0, heightCm = 180.0)
        assertEquals(false, settings.weightIsFallback.value)
        assertEquals(72.0, settings.bodyWeightKg.value, 0.0)
    }

    @Test
    fun `a missing or broken weight falls back to 70 kg and says so`() {
        val settings = UserSettings()
        settings.hydrateFrom(weightKg = 72.0, heightCm = null)
        settings.hydrateFrom(weightKg = null, heightCm = null)
        assertEquals(true, settings.weightIsFallback.value)
        assertEquals(70.0, settings.bodyWeightKg.value, 0.0)
        settings.hydrateFrom(weightKg = 0.0, heightCm = null)
        assertEquals(true, settings.weightIsFallback.value)
        assertEquals(70.0, settings.bodyWeightKg.value, 0.0)
    }

    @Test
    fun `no weight to save with until a profile has loaded`() {
        // Offline cold start: the user may well have a real weight, so no
        // 70 kg guess is stored.
        val settings = UserSettings()
        assertEquals(null, settings.weightForSavingKgOrNull())
        settings.hydrateFrom(weightKg = 88.0, heightCm = null)
        assertEquals(88.0, settings.weightForSavingKgOrNull()!!, 0.0)
        settings.reset()
        assertEquals(null, settings.weightForSavingKgOrNull())
        settings.updateBodyWeightKg(64.0)
        assertEquals(64.0, settings.weightForSavingKgOrNull()!!, 0.0)
    }

    @Test
    fun `a loaded profile with no weight saves with the 70 kg fallback`() {
        val settings = UserSettings()
        settings.hydrateFrom(weightKg = null, heightCm = 175.0)
        assertEquals(70.0, settings.weightForSavingKgOrNull()!!, 0.0)
        assertEquals(true, settings.weightIsFallback.value)
        // A broken weight is treated the same as none.
        settings.hydrateFrom(weightKg = Double.NaN, heightCm = null)
        assertEquals(70.0, settings.weightForSavingKgOrNull()!!, 0.0)
    }

    @Test
    fun `an unusable weight update doesn't count as a loaded profile`() {
        val settings = UserSettings()
        settings.updateBodyWeightKg(0.0)
        assertEquals(null, settings.weightForSavingKgOrNull())
        assertEquals(true, settings.weightIsFallback.value)
    }

    @Test
    fun `reset makes the weight a fallback again`() {
        val settings = UserSettings()
        settings.updateBodyWeightKg(80.0)
        assertEquals(false, settings.weightIsFallback.value)
        settings.reset()
        assertEquals(true, settings.weightIsFallback.value)
        assertEquals(70.0, settings.bodyWeightKg.value, 0.0)
        assertEquals(null, settings.heightCm.value)
    }

    // --------- Phase 8d: weight kept across restarts ---------

    @Test
    fun `a loaded profile publishes its weight, and reset clears it`() {
        val settings = UserSettings()
        assertEquals(null, settings.loadedProfileWeight.value)
        settings.hydrateFrom(weightKg = 82.0, heightCm = null)
        assertEquals(UserSettings.ProfileWeight(82.0), settings.loadedProfileWeight.value)
        settings.hydrateFrom(weightKg = null, heightCm = null)
        assertEquals(UserSettings.ProfileWeight(null), settings.loadedProfileWeight.value)
        settings.reset()
        assertEquals(null, settings.loadedProfileWeight.value)
    }

    @Test
    fun `a restored weight is used for saving before the profile loads`() {
        // Offline cold start: the weight saved by the last session.
        val settings = UserSettings()
        settings.restoreSaved(UserSettings.ProfileWeight(82.0), settings.resetCount)
        assertEquals(82.0, settings.weightForSavingKgOrNull()!!, 0.0)
        assertEquals(82.0, settings.bodyWeightKg.value, 0.0)
        assertEquals(false, settings.weightIsFallback.value)
    }

    @Test
    fun `a restored profile without a weight saves with 70 kg and stays a fallback`() {
        val settings = UserSettings()
        settings.restoreSaved(UserSettings.ProfileWeight(null), settings.resetCount)
        assertEquals(70.0, settings.weightForSavingKgOrNull()!!, 0.0)
        assertEquals(true, settings.weightIsFallback.value)
    }

    @Test
    fun `a restored weight never replaces a profile that already loaded`() {
        val settings = UserSettings()
        val resets = settings.resetCount
        settings.hydrateFrom(weightKg = 64.0, heightCm = null)
        settings.restoreSaved(UserSettings.ProfileWeight(82.0), resets)
        assertEquals(64.0, settings.weightForSavingKgOrNull()!!, 0.0)
    }

    @Test
    fun `a weight read before a sign-out is ignored`() {
        val settings = UserSettings()
        val resets = settings.resetCount
        settings.reset()
        settings.restoreSaved(UserSettings.ProfileWeight(82.0), resets)
        assertEquals(null, settings.weightForSavingKgOrNull())
        assertEquals(null, settings.loadedProfileWeight.value)
    }
}
