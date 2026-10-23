package com.elijah.fitness.ui.log

import com.elijah.fitness.data.model.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogDistanceSummaryCalculatorTest {

    private val weight = 70.0

    @Test
    fun `empty inputs return null (incomplete)`() {
        assertNull(LogDistanceSummaryCalculator.calculate(
            durationSeconds = 0L, distanceKm = null,
            activity = ActivityType.RUNNING, bodyWeightKg = weight,
        ))
        assertNull(LogDistanceSummaryCalculator.calculate(
            durationSeconds = 0L, distanceKm = 5.0,
            activity = ActivityType.RUNNING, bodyWeightKg = weight,
        ))
        assertNull(LogDistanceSummaryCalculator.calculate(
            durationSeconds = 600L, distanceKm = null,
            activity = ActivityType.RUNNING, bodyWeightKg = weight,
        ))
    }

    @Test
    fun `zero distance returns null, not a divide-by-zero crash`() {
        assertNull(LogDistanceSummaryCalculator.calculate(
            durationSeconds = 600L, distanceKm = 0.0,
            activity = ActivityType.RUNNING, bodyWeightKg = weight,
        ))
    }

    @Test
    fun `negative duration treated as empty`() {
        assertNull(LogDistanceSummaryCalculator.calculate(
            durationSeconds = -300L, distanceKm = 5.0,
            activity = ActivityType.RUNNING, bodyWeightKg = weight,
        ))
    }

    @Test
    fun `weightlifting never returns a summary (distance log is for running or cycling)`() {
        assertNull(LogDistanceSummaryCalculator.calculate(
            durationSeconds = 1_800L, distanceKm = 5.0,
            activity = ActivityType.WEIGHTLIFTING, bodyWeightKg = weight,
        ))
    }

    @Test
    fun `5 km in 30 min at 70 kg returns 6 min_km pace and 10 kmh speed`() {
        val summary = LogDistanceSummaryCalculator.calculate(
            durationSeconds = 1_800L, distanceKm = 5.0,
            activity = ActivityType.RUNNING, bodyWeightKg = weight,
        )
        assertNotNull(summary)
        assertEquals(360, summary!!.paceSecondsPerKm) // 6:00 /km
        assertEquals(10.0, summary.speedKmh, 1e-6)
        // Running MET ~9.8 for 10 km/h; 9.8 × 70 × 0.5 h ≈ 343 kcal.
        assertTrue("expected ~340 kcal, got ${summary.calories}",
            summary.calories in 300..400)
    }

    @Test
    fun `cycling same distance same time at 70 kg returns a lower kcal than running`() {
        val run = LogDistanceSummaryCalculator.calculate(
            durationSeconds = 1_800L, distanceKm = 5.0,
            activity = ActivityType.RUNNING, bodyWeightKg = weight,
        )!!
        val ride = LogDistanceSummaryCalculator.calculate(
            durationSeconds = 1_800L, distanceKm = 5.0,
            activity = ActivityType.CYCLING, bodyWeightKg = weight,
        )!!
        // Cycling MET is lower than running MET at the same speed.
        assertTrue(ride.calories < run.calories)
        // Shared pace + speed values should match regardless of activity.
        assertEquals(run.paceSecondsPerKm, ride.paceSecondsPerKm)
        assertEquals(run.speedKmh, ride.speedKmh, 1e-6)
    }

    @Test
    fun `calories scale with body weight (never the 70 kg placeholder)`() {
        val light = LogDistanceSummaryCalculator.calculate(
            durationSeconds = 1_800L, distanceKm = 5.0,
            activity = ActivityType.RUNNING, bodyWeightKg = 50.0,
        )!!
        val heavy = LogDistanceSummaryCalculator.calculate(
            durationSeconds = 1_800L, distanceKm = 5.0,
            activity = ActivityType.RUNNING, bodyWeightKg = 100.0,
        )!!
        // Heavier runner burns more kcal for the same workout.
        assertTrue(heavy.calories > light.calories)
    }

    // --------- Phase 8d: rows ---------

    @Test
    fun `a row shows pace per 500 m and rowing calories`() {
        // 6 km in 31 min → 155 s per 500 m ("2:35").
        val row = LogDistanceSummaryCalculator.calculate(
            durationSeconds = 1_860L, distanceKm = 6.0,
            activity = ActivityType.ROWING, bodyWeightKg = weight,
        )
        assertNotNull(row)
        assertEquals(155, row!!.splitPaceSeconds)
        assertEquals(310, row.paceSecondsPerKm)
        // 7.0 × 70 × (1860 / 3600) h = 253.2
        assertEquals(253, row.calories)
    }

    @Test
    fun `split pace rounds like the saved workout`() {
        // 60 × 25 m in 32:10 → 1930 / 15 = 128.67 → 129 ("2:09 /100 m").
        val swim = LogDistanceSummaryCalculator.calculate(
            durationSeconds = 1_930L, distanceKm = 1.5,
            activity = ActivityType.SWIMMING, bodyWeightKg = weight,
        )!!
        assertEquals(129, swim.splitPaceSeconds)
        // 5 km in 27:43 → 332.6 → 333 s per km.
        val run = LogDistanceSummaryCalculator.calculate(
            durationSeconds = 1_663L, distanceKm = 5.0,
            activity = ActivityType.RUNNING, bodyWeightKg = weight,
        )!!
        assertEquals(333, run.paceSecondsPerKm)
        assertEquals(333, run.splitPaceSeconds)
    }

    @Test
    fun `a run's split pace is its per-km pace`() {
        val run = LogDistanceSummaryCalculator.calculate(
            durationSeconds = 1_800L, distanceKm = 5.0,
            activity = ActivityType.RUNNING, bodyWeightKg = weight,
        )!!
        assertEquals(run.paceSecondsPerKm, run.splitPaceSeconds)
    }
}
