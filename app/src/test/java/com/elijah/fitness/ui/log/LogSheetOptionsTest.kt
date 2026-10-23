package com.elijah.fitness.ui.log

import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 8d Step 3: the rules of the Log a workout sheet (LogSheet.dc.html). */
class LogSheetOptionsTest {

    @Test
    fun `cards follow the design order and cover every activity`() {
        assertEquals(
            listOf(
                ActivityType.RUNNING,
                ActivityType.CYCLING,
                ActivityType.SWIMMING,
                ActivityType.ROWING,
                ActivityType.WEIGHTLIFTING,
            ),
            LogSheetOptions.activities,
        )
        assertEquals(ActivityType.entries.toSet(), LogSheetOptions.activities.toSet())
    }

    @Test
    fun `the sheet opens on the last activity logged, else Running`() {
        assertEquals(0, LogSheetOptions.initialIndex(null))
        assertEquals(0, LogSheetOptions.initialIndex(ActivityType.RUNNING))
        assertEquals(2, LogSheetOptions.initialIndex(ActivityType.SWIMMING))
        assertEquals(3, LogSheetOptions.initialIndex(ActivityType.ROWING))
        assertEquals(4, LogSheetOptions.initialIndex(ActivityType.WEIGHTLIFTING))
    }

    @Test
    fun `runs rides and rows offer GPS, swims and workouts do not`() {
        assertTrue(LogSheetOptions.supportsGps(ActivityType.RUNNING))
        assertTrue(LogSheetOptions.supportsGps(ActivityType.CYCLING))
        assertTrue(LogSheetOptions.supportsGps(ActivityType.ROWING))
        assertFalse(LogSheetOptions.supportsGps(ActivityType.SWIMMING))
        assertFalse(LogSheetOptions.supportsGps(ActivityType.WEIGHTLIFTING))
    }

    @Test
    fun `only swims and workouts show a note instead of the GPS choice`() {
        assertEquals(R.string.log_sheet_note_swim, LogSheetOptions.noteRes(ActivityType.SWIMMING))
        assertEquals(
            R.string.log_sheet_note_workout,
            LogSheetOptions.noteRes(ActivityType.WEIGHTLIFTING),
        )
        assertNull(LogSheetOptions.noteRes(ActivityType.RUNNING))
        assertNull(LogSheetOptions.noteRes(ActivityType.CYCLING))
        assertNull(LogSheetOptions.noteRes(ActivityType.ROWING))
    }

    @Test
    fun `the button label follows the activity and the mode`() {
        val expected = mapOf(
            (ActivityType.RUNNING to LogMode.LIVE) to R.string.log_sheet_cta_run_live,
            (ActivityType.RUNNING to LogMode.MANUAL) to R.string.log_sheet_cta_run_manual,
            (ActivityType.CYCLING to LogMode.LIVE) to R.string.log_sheet_cta_ride_live,
            (ActivityType.CYCLING to LogMode.MANUAL) to R.string.log_sheet_cta_ride_manual,
            (ActivityType.ROWING to LogMode.LIVE) to R.string.log_sheet_cta_row_live,
            (ActivityType.ROWING to LogMode.MANUAL) to R.string.log_sheet_cta_row_manual,
            // No GPS choice: the mode doesn't change the label.
            (ActivityType.SWIMMING to LogMode.LIVE) to R.string.log_sheet_cta_swim,
            (ActivityType.SWIMMING to LogMode.MANUAL) to R.string.log_sheet_cta_swim,
            (ActivityType.WEIGHTLIFTING to LogMode.LIVE) to R.string.log_sheet_cta_workout,
            (ActivityType.WEIGHTLIFTING to LogMode.MANUAL) to R.string.log_sheet_cta_workout,
        )
        expected.forEach { (key, label) ->
            assertEquals("$key", label, LogSheetOptions.ctaLabelRes(key.first, key.second))
        }
        // Eight different labels in all, as in the brief.
        assertEquals(8, expected.values.toSet().size)
    }

    @Test
    fun `the button goes to the right screen`() {
        assertEquals(
            LogDestination.LIVE_TRACKING,
            LogSheetOptions.destination(ActivityType.ROWING, LogMode.LIVE),
        )
        assertEquals(
            LogDestination.MANUAL_DISTANCE,
            LogSheetOptions.destination(ActivityType.CYCLING, LogMode.MANUAL),
        )
        assertEquals(
            LogDestination.LIVE_TRACKING,
            LogSheetOptions.destination(ActivityType.RUNNING, LogMode.LIVE),
        )
        assertEquals(
            LogDestination.SWIM,
            LogSheetOptions.destination(ActivityType.SWIMMING, LogMode.LIVE),
        )
        assertEquals(
            LogDestination.STRENGTH,
            LogSheetOptions.destination(ActivityType.WEIGHTLIFTING, LogMode.MANUAL),
        )
    }

    @Test
    fun `every card has its own description`() {
        val descriptions = LogSheetOptions.activities.map(LogSheetOptions::descriptionRes)
        assertEquals(descriptions.size, descriptions.toSet().size)
        assertEquals(R.string.log_sheet_card_swimming, LogSheetOptions.descriptionRes(ActivityType.SWIMMING))
    }

    @Test
    fun `the modes are Live first, then Manual`() {
        assertEquals(listOf(LogMode.LIVE, LogMode.MANUAL), LogSheetOptions.modes)
    }

    @Test
    fun `ui state derives everything from the selection and mode`() {
        val swim = LogSheetUiState(selectedIndex = 2)
        assertEquals(ActivityType.SWIMMING, swim.activity)
        assertFalse(swim.showsModeChoice)
        assertEquals(R.string.log_sheet_note_swim, swim.noteRes)
        assertEquals(R.string.log_sheet_cta_swim, swim.ctaLabelRes)
        assertEquals(LogDestination.SWIM, swim.destination)

        val rideManual = LogSheetUiState(selectedIndex = 1, mode = LogMode.MANUAL)
        assertTrue(rideManual.showsModeChoice)
        assertNull(rideManual.noteRes)
        assertEquals(R.string.log_sheet_cta_ride_manual, rideManual.ctaLabelRes)
        assertEquals(LogDestination.MANUAL_DISTANCE, rideManual.destination)
    }

    @Test
    fun `an out of range index never crashes the ui state`() {
        assertEquals(ActivityType.WEIGHTLIFTING, LogSheetUiState(selectedIndex = 99).activity)
        assertEquals(ActivityType.RUNNING, LogSheetUiState(selectedIndex = -3).activity)
    }
}
