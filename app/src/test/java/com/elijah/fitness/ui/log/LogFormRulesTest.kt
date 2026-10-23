package com.elijah.fitness.ui.log

import com.elijah.fitness.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** The checks every log form shares (phase 8d). */
class LogFormRulesTest {

    @Test
    fun `duration seconds adds up hours minutes and seconds`() {
        assertEquals(1_930L, LogFormRules.durationSeconds(0, 32, 10))
        assertEquals(3_750L, LogFormRules.durationSeconds(1, 2, 30))
    }

    @Test
    fun `a duration above zero with valid fields has no error`() {
        assertNull(LogFormRules.durationError(0, 32, 10))
        assertNull(LogFormRules.durationError(0, 0, 1))
        assertNull(LogFormRules.durationError(12, 59, 59))
    }

    @Test
    fun `a zero duration is an error`() {
        assertEquals(R.string.log_error_duration_zero, LogFormRules.durationError(0, 0, 0))
    }

    @Test
    fun `minutes or seconds over 59 are out of range, not clamped`() {
        assertEquals(R.string.log_error_duration_out_of_range, LogFormRules.durationError(0, 75, 0))
        assertEquals(R.string.log_error_duration_out_of_range, LogFormRules.durationError(0, 10, 60))
        assertEquals(R.string.log_error_duration_out_of_range, LogFormRules.durationError(-1, 10, 0))
    }

    @Test
    fun `exactly 24 h is the longest duration allowed`() {
        assertNull(LogFormRules.durationError(24, 0, 0))
        assertEquals(LogFormRules.MAX_DURATION_SECONDS, LogFormRules.durationSeconds(24, 0, 0))
    }

    @Test
    fun `more than 24 h is too long, the server would refuse it`() {
        assertEquals(R.string.log_error_duration_too_long, LogFormRules.durationError(24, 0, 1))
        // "32" typed into the hours cell instead of the minutes cell.
        assertEquals(R.string.log_error_duration_too_long, LogFormRules.durationError(32, 0, 0))
        assertEquals(R.string.log_error_duration_too_long, LogFormRules.durationError(99, 59, 59))
    }

    @Test
    fun `a start in the future is an error, now is fine`() {
        val now = Instant.parse("2026-10-20T08:00:00Z")
        assertNull(LogFormRules.startError(now, now))
        assertNull(LogFormRules.startError(now.minusSeconds(60), now))
        assertEquals(
            R.string.log_error_date_in_future,
            LogFormRules.startError(now.plusSeconds(60), now),
        )
    }

    @Test
    fun `start instant combines the date and time in the zone`() {
        assertEquals(
            Instant.parse("2026-10-19T07:10:00Z"),
            LogFormRules.startInstant(LocalDate.of(2026, 10, 19), LocalTime.of(7, 10), ZoneOffset.UTC),
        )
    }
}
