package com.elijah.fitness.util

import com.elijah.fitness.data.model.DistanceUnit
import com.elijah.fitness.testutil.StringsXmlText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Swim and row measures. The units come from the real strings.xml
 * (measure_metres, measure_pace_per_100m, measure_pace_per_500m).
 */
class SwimRowFormattingTest {

    private val uk = Locale.UK
    private lateinit var savedLocale: Locale

    @Before
    fun pinLocale() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(uk)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(savedLocale)
    }

    @Test
    fun `swim distance is whole metres with a thousands separator`() {
        assertEquals("1,500 m", MeasureText.metres(StringsXmlText, 1_500.0))
        assertEquals("750 m", MeasureText.metres(StringsXmlText, 750.0))
        assertEquals("1,800", Formatters.formatMetresValue(1_800.4, uk))
        // Garbage never crashes.
        assertEquals("0 m", MeasureText.metres(StringsXmlText, Double.NaN))
        assertEquals("0 m", MeasureText.metres(StringsXmlText, -20.0))
    }

    @Test
    fun `a distance reads in the unit it is given`() {
        assertEquals("1,500 m", MeasureText.distance(StringsXmlText, 1_500.0, DistanceUnit.METRES))
        assertEquals("1.50 km", MeasureText.distance(StringsXmlText, 1_500.0, DistanceUnit.KILOMETRES))
    }

    @Test
    fun `swim pace per 100 m`() {
        assertEquals("2:09 /100 m", MeasureText.pacePer100m(StringsXmlText, 129))
        assertEquals("-", MeasureText.pacePer100m(StringsXmlText, 0))
    }

    @Test
    fun `row pace per 500 m`() {
        assertEquals("2:35 /500 m", MeasureText.pacePer500m(StringsXmlText, 155))
        assertEquals("-", MeasureText.pacePer500m(StringsXmlText, -1))
    }

    @Test
    fun `calories are rounded and grouped, never truncated`() {
        assertEquals("1,982", Formatters.formatKcalValue(1_982.0, uk))
        assertEquals("1,000", Formatters.formatKcalValue(999.6, uk))
        assertEquals("271", Formatters.formatKcalValue(270.9, uk))
        assertEquals("345", Formatters.formatKcalValue(345, uk))
        assertEquals("0", Formatters.formatKcalValue(Double.NaN, uk))
        assertEquals("0", Formatters.formatKcalValue(-12.0, uk))
    }
}
