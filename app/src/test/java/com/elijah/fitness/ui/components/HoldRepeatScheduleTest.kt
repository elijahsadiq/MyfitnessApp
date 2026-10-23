package com.elijah.fitness.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stepper long-press timing (phase 8d): repeats start after a hold and speed up. */
class HoldRepeatScheduleTest {

    @Test
    fun `a hold must last past a normal tap before repeating`() {
        assertTrue(HoldRepeatSchedule.HOLD_THRESHOLD_MS >= 300L)
    }

    @Test
    fun `repeats speed up twice`() {
        assertEquals(160L, HoldRepeatSchedule.intervalAfter(0))
        assertEquals(160L, HoldRepeatSchedule.intervalAfter(5))
        assertEquals(80L, HoldRepeatSchedule.intervalAfter(6))
        assertEquals(80L, HoldRepeatSchedule.intervalAfter(13))
        assertEquals(35L, HoldRepeatSchedule.intervalAfter(14))
        assertEquals(35L, HoldRepeatSchedule.intervalAfter(500))
    }

    @Test
    fun `the interval never grows during a hold`() {
        val intervals = (0..50).map(HoldRepeatSchedule::intervalAfter)
        assertEquals(intervals.sortedDescending(), intervals)
    }
}
