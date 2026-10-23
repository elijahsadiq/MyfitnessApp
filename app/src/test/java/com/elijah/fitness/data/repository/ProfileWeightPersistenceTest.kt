package com.elijah.fitness.data.repository

import com.elijah.fitness.data.local.ProfileWeightStore
import com.elijah.fitness.data.repository.UserSettings.ProfileWeight
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * The profile weight survives an app restart (phase 8d reviewer fix): a
 * workout saved offline right after a cold start still gets its calories.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileWeightPersistenceTest {

    private class FakeStore(var saved: ProfileWeight? = null) : ProfileWeightStore {
        var failReads = false
        var saves = 0
        override suspend fun read(): ProfileWeight? {
            if (failReads) throw IOException("disk")
            return saved
        }
        override suspend fun save(weight: ProfileWeight) {
            saves++
            saved = weight
        }
        override suspend fun clear() {
            saved = null
        }
    }

    @Test
    fun `start restores the saved weight so offline saves store calories`() = runTest {
        val settings = UserSettings()
        val store = FakeStore(saved = ProfileWeight(82.0))
        ProfileWeightPersistence(settings, store, backgroundScope + UnconfinedTestDispatcher(testScheduler))
            .start()

        assertEquals(82.0, settings.weightForSavingKgOrNull()!!, 0.0)
    }

    @Test
    fun `nothing saved leaves the weight unknown`() = runTest {
        val settings = UserSettings()
        ProfileWeightPersistence(settings, FakeStore(), backgroundScope + UnconfinedTestDispatcher(testScheduler))
            .start()

        assertEquals(null, settings.weightForSavingKgOrNull())
    }

    @Test
    fun `every weight that loads is saved`() = runTest {
        val settings = UserSettings()
        val store = FakeStore()
        ProfileWeightPersistence(settings, store, backgroundScope + UnconfinedTestDispatcher(testScheduler))
            .start()

        settings.hydrateFrom(weightKg = 75.0, heightCm = 180.0)
        assertEquals(ProfileWeight(75.0), store.saved)
        settings.updateBodyWeightKg(77.5)
        assertEquals(ProfileWeight(77.5), store.saved)
        settings.hydrateFrom(weightKg = null, heightCm = null)
        assertEquals(ProfileWeight(null), store.saved)
    }

    @Test
    fun `sign-out clears the saved weight and the reset is not saved`() = runTest {
        val settings = UserSettings()
        val store = FakeStore()
        val persistence = ProfileWeightPersistence(
            settings, store, backgroundScope + UnconfinedTestDispatcher(testScheduler),
        )
        persistence.start()
        settings.hydrateFrom(weightKg = 75.0, heightCm = null)
        val savesBefore = store.saves

        settings.reset()
        persistence.clear()

        assertEquals(null, store.saved)
        assertEquals(savesBefore, store.saves)
    }

    @Test
    fun `an unreadable store is ignored`() = runTest {
        val settings = UserSettings()
        val store = FakeStore(saved = ProfileWeight(82.0)).apply { failReads = true }
        ProfileWeightPersistence(settings, store, backgroundScope + UnconfinedTestDispatcher(testScheduler))
            .start()

        assertEquals(null, settings.weightForSavingKgOrNull())
        // Saving still works once a profile loads.
        settings.hydrateFrom(weightKg = 70.0, heightCm = null)
        assertEquals(ProfileWeight(70.0), store.saved)
    }
}
