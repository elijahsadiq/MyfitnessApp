package com.elijah.fitness.ui.log

import com.elijah.fitness.testutil.StringsXmlText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** Log a swim maths and text (phase 8d, LogSwim.dc.html's script). */
class LogSwimCalculatorTest {

    private val uk = Locale.UK
    private val nbsp = ' '

    // ---------- distance ----------

    @Test
    fun `distance is laps times pool length`() {
        assertEquals(1_500, LogSwimCalculator.distanceMeters(laps = 60, poolLengthM = 25))
        assertEquals(3_000, LogSwimCalculator.distanceMeters(laps = 60, poolLengthM = 50))
        assertEquals(25, LogSwimCalculator.distanceMeters(laps = 1, poolLengthM = 25))
    }

    @Test
    fun `negative inputs never give a negative distance`() {
        assertEquals(0, LogSwimCalculator.distanceMeters(laps = -3, poolLengthM = 25))
    }

    // ---------- pace per 100 m ----------

    @Test
    fun `the design's swim paces 2 09 per 100 m`() {
        // 60 x 25 m in 32:10 -> 1930 s / 15 = 128.67 -> 129 s.
        val summary = LogSwimCalculator.summary(
            laps = 60, poolLengthM = 25, durationSeconds = 32 * 60 + 10L, weightKg = 72.0,
        )
        assertEquals(1_500, summary.distanceMeters)
        assertEquals(129L, summary.pacePer100mSeconds)
        assertEquals("2:09", LogSwimCalculator.paceText(summary.pacePer100mSeconds, uk))
    }

    @Test
    fun `a 50 m pool paces over the longer distance`() {
        // 60 x 50 m in 1:02:30 -> 3750 s / 30 = 125 s.
        val summary = LogSwimCalculator.summary(
            laps = 60, poolLengthM = 50, durationSeconds = 3_750L, weightKg = 70.0,
        )
        assertEquals(3_000, summary.distanceMeters)
        assertEquals(125L, summary.pacePer100mSeconds)
        assertEquals("2:05", LogSwimCalculator.paceText(125L, uk))
    }

    @Test
    fun `no duration yet shows 0 00 and no calories`() {
        val summary = LogSwimCalculator.summary(
            laps = 60, poolLengthM = 25, durationSeconds = 0L, weightKg = 72.0,
        )
        assertEquals(1_500, summary.distanceMeters)
        assertEquals(0L, summary.pacePer100mSeconds)
        assertEquals(0, summary.kcal)
        assertEquals("0:00", LogSwimCalculator.paceText(0L, uk))
    }

    @Test
    fun `pace over an hour per 100 m still reads minutes and seconds`() {
        // 1 lap of 25 m in 32:10 -> 1930 / 0.25 = 7720 s = 128:40.
        assertEquals("128:40", LogSwimCalculator.paceText(7_720L, uk))
    }

    // ---------- calories ----------

    @Test
    fun `calories use the swimming MET and the real duration`() {
        // 7.0 x 72 kg x (1930 / 3600) h = 270.2 -> 270 (the design's number).
        val summary = LogSwimCalculator.summary(
            laps = 60, poolLengthM = 25, durationSeconds = 1_930L, weightKg = 72.0,
        )
        assertEquals(270, summary.kcal)
    }

    @Test
    fun `an unusable weight gives no calories instead of crashing`() {
        val summary = LogSwimCalculator.summary(
            laps = 60, poolLengthM = 25, durationSeconds = 1_930L, weightKg = Double.NaN,
        )
        assertEquals(0, summary.kcal)
    }

    // ---------- laps stepper ----------

    @Test
    fun `laps step by one inside 1 to 400`() {
        assertEquals(61, LogSwimCalculator.stepLaps(60, +1))
        assertEquals(59, LogSwimCalculator.stepLaps(60, -1))
        assertEquals(1, LogSwimCalculator.stepLaps(1, -1))
        assertEquals(400, LogSwimCalculator.stepLaps(400, +1))
    }

    @Test
    fun `defaults match the design`() {
        assertEquals(60, LogSwimCalculator.DEFAULT_LAPS)
        assertEquals(25, LogSwimCalculator.DEFAULT_POOL_LENGTH_M)
        assertEquals(listOf(25, 50), LogSwimCalculator.POOL_LENGTHS_M)
        assertEquals(1..400, LogSwimCalculator.LAPS_RANGE)
    }

    // ---------- text ----------

    @Test
    fun `laps hint reads laps times pool length equals distance`() {
        val hint = LogSwimCalculator.lapsHint(StringsXmlText, laps = 60, poolLengthM = 25, locale = uk)
        assertEquals("60${nbsp}×${nbsp}25${nbsp}m = 1,500${nbsp}m", hint)
    }

    @Test
    fun `laps hint follows the pool length`() {
        val hint = LogSwimCalculator.lapsHint(StringsXmlText, laps = 30, poolLengthM = 50, locale = uk)
        assertEquals("30${nbsp}×${nbsp}50${nbsp}m = 1,500${nbsp}m", hint)
    }

    @Test
    fun `date card says today or yesterday before the date`() {
        val today = LocalDate.of(2026, 10, 19)
        assertEquals(
            "Today, Mon 19 Oct",
            LogSwimCalculator.dateText(StringsXmlText, today, today, uk),
        )
        assertEquals(
            "Yesterday, Sun 18 Oct",
            LogSwimCalculator.dateText(StringsXmlText, today.minusDays(1), today, uk),
        )
        assertEquals(
            "Sat 17 Oct",
            LogSwimCalculator.dateText(StringsXmlText, today.minusDays(2), today, uk),
        )
    }
}
