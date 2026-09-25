package com.elijah.fitness.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyMetricsTest {

    @Test
    fun `kg to lb round trip`() {
        val kg = 72.5
        val lb = BodyMetrics.kilogramsToPounds(kg)
        assertEquals(159.83, lb, 0.01)
        assertEquals(kg, BodyMetrics.poundsToKilograms(lb), 1e-9)
    }

    @Test
    fun `cm to inch round trip`() {
        val cm = 178.0
        val inches = BodyMetrics.centimetersToInches(cm)
        assertEquals(70.08, inches, 0.01)
        assertEquals(cm, BodyMetrics.inchesToCentimeters(inches), 1e-9)
    }

    @Test
    fun `bmi is null when either input is missing`() {
        assertNull(BodyMetrics.bmi(weightKg = null, heightCm = 178.0))
        assertNull(BodyMetrics.bmi(weightKg = 72.0, heightCm = null))
        assertNull(BodyMetrics.bmi(weightKg = null, heightCm = null))
    }

    @Test
    fun `bmi is null for zero or negative inputs`() {
        assertNull(BodyMetrics.bmi(weightKg = 0.0, heightCm = 178.0))
        assertNull(BodyMetrics.bmi(weightKg = 72.0, heightCm = 0.0))
        assertNull(BodyMetrics.bmi(weightKg = -5.0, heightCm = 178.0))
    }

    @Test
    fun `bmi computes weight over height meters squared`() {
        // 72 kg at 1.80 m → 72 / (1.8*1.8) = 22.222…
        assertEquals(22.222, BodyMetrics.bmi(72.0, 180.0)!!, 0.001)
    }

    @Test
    fun `bmiCategory maps to the WHO ranges at each cutoff`() {
        assertEquals(BmiCategory.UNDERWEIGHT, BodyMetrics.bmiCategory(17.0))
        assertEquals(BmiCategory.UNDERWEIGHT, BodyMetrics.bmiCategory(18.4999))
        assertEquals(BmiCategory.NORMAL, BodyMetrics.bmiCategory(18.5))
        assertEquals(BmiCategory.NORMAL, BodyMetrics.bmiCategory(24.9999))
        assertEquals(BmiCategory.OVERWEIGHT, BodyMetrics.bmiCategory(25.0))
        assertEquals(BmiCategory.OVERWEIGHT, BodyMetrics.bmiCategory(29.9999))
        assertEquals(BmiCategory.OBESE, BodyMetrics.bmiCategory(30.0))
        assertEquals(BmiCategory.OBESE, BodyMetrics.bmiCategory(45.0))
    }

    @Test
    fun `bmiCategory is null when bmi is null`() {
        assertNull(BodyMetrics.bmiCategory(null))
    }
}
