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
}
