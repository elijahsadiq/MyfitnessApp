package com.elijah.fitness.ui.goals

import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.testutil.StringsXmlText
import com.elijah.fitness.ui.goals.NewGoalViewModel.ActivityOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * Phase 8d step 6: what the goal cards, the main goal card and the
 * Manage sheet say ([GoalDisplay]), read from the real strings.xml.
 */
class GoalDisplayTest {

    private val text = StringsXmlText
    private val locale = Locale.US

    /**
     * strings.xml and GoalDisplay keep amounts, units and dates together
     * with U+00A0; the only ordinary spaces are where a line may wrap.
     */
    private val nbsp = '\u00A0'

    /** [s] with every space non-breaking: `nb("2 h 30 min")`. */
    private fun nb(s: String): String = s.replace(' ', nbsp)

    // ---------------- time ----------------

    @Test
    fun `time goals read hours and minutes, never raw minutes`() {
        assertEquals(
            "${nb("1 h 10 min")} / ${nb("2 h 30 min")}",
            progress(GoalType.DURATION, ActivityType.ROWING, current = 70.0, target = 150.0),
        )
        assertEquals(
            "${nb("0 min")} / ${nb("2 h 30 min")}",
            progress(GoalType.DURATION, ActivityType.ROWING, 0.0, 150.0),
        )
        assertEquals("${nb("2 h 43 min")} / ${nb("5 h")}", progress(GoalType.DURATION, null, 163.0, 300.0))
        assertEquals(
            nb("2 h 30 min"),
            GoalDisplay.targetText(text, GoalType.DURATION, ActivityType.SWIMMING, 150.0, locale),
        )
        assertEquals(
            "of ${nb("2 h 30 min")}",
            GoalDisplay.ofTargetText(text, GoalType.DURATION, ActivityType.SWIMMING, 150.0, locale),
        )
        assertEquals(
            "/ ${nb("2 h 30 min")}",
            GoalDisplay.overTargetText(text, GoalType.DURATION, null, 150.0, locale),
        )
    }

    @Test
    fun `no goal text ever says 150 min`() {
        val samples = listOf(
            progress(GoalType.DURATION, null, 150.0, 150.0),
            GoalDisplay.targetText(text, GoalType.DURATION, null, 150.0, locale),
            GoalDisplay.currentText(text, GoalType.DURATION, null, 150.0, locale),
            GoalDisplay.ofTargetText(text, GoalType.DURATION, null, 150.0, locale),
            GoalDisplay.perDayText(text, GoalType.DURATION, 150.0, locale),
            GoalDisplay.endedCaption(text, GoalType.DURATION, null, LocalDate.of(2026, 9, 28), 90.0, 150.0, locale),
        )
        samples.forEach { assertFalse(it, it.replace(nbsp, ' ').contains("150 min")) }
        assertNull(GoalDisplay.unitWord(text, GoalType.DURATION, null, 150.0))
    }

    // ---------------- distance ----------------

    @Test
    fun `swim distance goals stay in km with up to one decimal`() {
        assertEquals("6.5 / ${nb("10 km")}", progress(GoalType.DISTANCE, ActivityType.SWIMMING, 6.5, 10.0))
        assertEquals("1.5 / ${nb("10 km")}", progress(GoalType.DISTANCE, ActivityType.SWIMMING, 1.5, 10.0))
        assertEquals("0 / ${nb("10.5 km")}", progress(GoalType.DISTANCE, ActivityType.SWIMMING, 0.0, 10.5))
        assertEquals("10.5${nbsp}km", GoalDisplay.targetText(text, GoalType.DISTANCE, ActivityType.SWIMMING, 10.5, locale))
        assertEquals("of 10${nbsp}km", GoalDisplay.ofTargetText(text, GoalType.DISTANCE, ActivityType.SWIMMING, 10.0, locale))
    }

    @Test
    fun `progress rounds to one decimal but an unmet target never looks met`() {
        assertEquals("13.2 / ${nb("50 km")}", progress(GoalType.DISTANCE, ActivityType.RUNNING, 13.18, 50.0))
        assertEquals("9.9 / ${nb("10 km")}", progress(GoalType.DISTANCE, ActivityType.RUNNING, 9.96, 10.0))
        assertEquals("10 / ${nb("10 km")}", progress(GoalType.DISTANCE, ActivityType.RUNNING, 10.0, 10.0))
        assertEquals("10 / ${nb("10 km")}", progress(GoalType.DISTANCE, ActivityType.RUNNING, 10.04, 10.0))
        // Over-achieved goals show the real total.
        assertEquals("37 / ${nb("20 km")}", progress(GoalType.DISTANCE, ActivityType.CYCLING, 37.0, 20.0))
    }

    @Test
    fun `a total one rounding error short reads as met, as the goal counts it`() {
        // 0.6 + 0.7 + 0.2 km: the goal is Completed (Goal.meetsTarget), so the row agrees.
        val sum = 0.6 + 0.7 + 0.2
        assertTrue(sum < 1.5)
        assertEquals("1.5 / ${nb("1.5 km")}", progress(GoalType.DISTANCE, ActivityType.SWIMMING, sum, 1.5))
        // A real shortfall still never looks met.
        assertEquals("1.4 / ${nb("1.5 km")}", progress(GoalType.DISTANCE, ActivityType.SWIMMING, 1.499, 1.5))
    }

    @Test
    fun `odd values never crash or go negative`() {
        assertEquals("0 / ${nb("10 km")}", progress(GoalType.DISTANCE, ActivityType.ROWING, Double.NaN, 10.0))
        assertEquals("0 / ${nb("10 km")}", progress(GoalType.DISTANCE, ActivityType.ROWING, -3.0, 10.0))
        assertEquals(
            "${nb("0 min")} / ${nb("2 h 30 min")}",
            progress(GoalType.DURATION, null, Double.NaN, 150.0),
        )
    }

    // ---------------- sessions and weight ----------------

    @Test
    fun `session goals name the activity in the right plural`() {
        assertEquals("3 / ${nb("12 swims")}", progress(GoalType.COUNT, ActivityType.SWIMMING, 3.0, 12.0))
        assertEquals("0 / ${nb("1 row")}", progress(GoalType.COUNT, ActivityType.ROWING, 0.0, 1.0))
        assertEquals("5 / ${nb("12 workouts")}", progress(GoalType.COUNT, null, 5.0, 12.0))
        assertEquals("1${nbsp}swim", GoalDisplay.currentText(text, GoalType.COUNT, ActivityType.SWIMMING, 1.0, locale))
        assertEquals("of 12${nbsp}rows", GoalDisplay.ofTargetText(text, GoalType.COUNT, ActivityType.ROWING, 12.0, locale))
    }

    @Test
    fun `weight goals use thousands separators and never look met early`() {
        assertEquals(
            "5,000 / ${nb("20,000 kg")}",
            progress(GoalType.VOLUME, ActivityType.WEIGHTLIFTING, 5_000.0, 20_000.0),
        )
        assertEquals(
            "19,999 / ${nb("20,000 kg")}",
            progress(GoalType.VOLUME, ActivityType.WEIGHTLIFTING, 19_999.6, 20_000.0),
        )
    }

    // ---------------- captions ----------------

    @Test
    fun `completed and ended captions`() {
        val friday = LocalDate.of(2026, 10, 2)
        val monday = LocalDate.of(2026, 9, 28)
        // A narrow row wraps only after the "·": never a lone "kg", "min"
        // or "Oct" on the second line.
        assertEquals(
            "${nb("Finished Fri 2 Oct ·")} ${nb("46 km")}",
            GoalDisplay.completedCaption(text, GoalType.DISTANCE, ActivityType.CYCLING, friday, 46.0, locale),
        )
        assertEquals(
            "${nb("Finished Fri 2 Oct ·")} ${nb("2 h 30 min")}",
            GoalDisplay.completedCaption(text, GoalType.DURATION, ActivityType.SWIMMING, friday, 150.0, locale),
        )
        assertEquals(
            "${nb("Ended Mon 28 Sep ·")} ${nb("6.5 of 10 km")}",
            GoalDisplay.endedCaption(text, GoalType.DISTANCE, ActivityType.SWIMMING, monday, 6.5, 10.0, locale),
        )
        assertEquals(
            "${nb("Ended Sat 3 Oct ·")} ${nb("0 of 5,000 kg")}",
            GoalDisplay.endedCaption(
                text, GoalType.VOLUME, ActivityType.WEIGHTLIFTING, LocalDate.of(2026, 10, 3), 0.0, 5_000.0, locale,
            ),
        )
        assertEquals(
            "${nb("Ended Mon 28 Sep ·")} ${nb("1 h 10 min of 2 h 30 min")}",
            GoalDisplay.endedCaption(text, GoalType.DURATION, ActivityType.ROWING, monday, 70.0, 150.0, locale),
        )
    }

    // ---------------- Manage stat tiles ----------------

    @Test
    fun `per-day tiles show up to one decimal, time in hours and minutes`() {
        assertEquals("4.8${nbsp}km", GoalDisplay.perDayText(text, GoalType.DISTANCE, 4.83, locale))
        assertEquals("2${nbsp}km", GoalDisplay.perDayText(text, GoalType.DISTANCE, 2.0, locale))
        assertEquals(nb("35 min"), GoalDisplay.perDayText(text, GoalType.DURATION, 35.4, locale))
        assertEquals(nb("1 h 5 min"), GoalDisplay.perDayText(text, GoalType.DURATION, 65.0, locale))
        assertEquals(nb("1 min"), GoalDisplay.perDayText(text, GoalType.DURATION, 0.2, locale))
        assertEquals("0.4", GoalDisplay.perDayText(text, GoalType.COUNT, 0.4, locale))
        assertEquals("2,000${nbsp}kg", GoalDisplay.perDayText(text, GoalType.VOLUME, 2_000.4, locale))
        assertEquals("—", GoalDisplay.perDayText(text, GoalType.DISTANCE, 0.0, locale))
        assertEquals("—", GoalDisplay.perDayText(text, GoalType.COUNT, Double.NaN, locale))
    }

    @Test
    fun `a tiny per-day rate reads below a tenth, never 0`() {
        // 12 runs over 365 days, 10 km of swimming over 365 days.
        assertEquals("<0.1", GoalDisplay.perDayText(text, GoalType.COUNT, 12.0 / 365, locale))
        assertEquals("<0.1${nbsp}km", GoalDisplay.perDayText(text, GoalType.DISTANCE, 10.0 / 365, locale))
        // From 0.05 up it rounds to 0.1 as usual.
        assertEquals("0.1", GoalDisplay.perDayText(text, GoalType.COUNT, 0.05, locale))
        assertEquals("0.1${nbsp}km", GoalDisplay.perDayText(text, GoalType.DISTANCE, 0.06, locale))
    }

    // ---------------- Manage subtitle ----------------

    @Test
    fun `manage subtitle only breaks between its parts`() {
        val swim = GoalDisplay.manageSubtitle(
            text, ActivityType.SWIMMING, GoalType.DISTANCE, LocalDate.of(2026, 10, 19), locale,
        )
        assertEquals("Swimming$nbsp· Distance$nbsp· started${nbsp}Mon${nbsp}19${nbsp}Oct", swim)
        // The only ordinary spaces follow a "·": a wrap can never leave "Oct" alone.
        assertEquals(listOf("Swimming$nbsp·", "Distance$nbsp·", "started${nbsp}Mon${nbsp}19${nbsp}Oct"), swim.split(' '))

        val any = GoalDisplay.manageSubtitle(text, null, GoalType.DURATION, LocalDate.of(2026, 10, 19), locale)
        assertEquals("Any${nbsp}activity$nbsp· Time$nbsp· started${nbsp}Mon${nbsp}19${nbsp}Oct", any)
    }

    @Test
    fun `a wrapped title never leaves its last word alone`() {
        assertEquals(
            "Complete 4 workouts in ${nb("7 days")}",
            GoalDisplay.titleForDisplay("Complete 4 workouts in 7 days"),
        )
        assertEquals("Goal", GoalDisplay.titleForDisplay("Goal"))
        assertEquals("", GoalDisplay.titleForDisplay(""))
    }

    // ---------------- icons and names ----------------

    @Test
    fun `goal icons are the carousel card icons, the pulse for any activity`() {
        ActivityOption.entries.forEach { option ->
            assertEquals(option.name, NewGoalPlan.cardFor(option).iconRes, GoalDisplay.iconRes(option.toApi()))
        }
        assertEquals(R.drawable.ic_swim_24, GoalDisplay.iconRes(ActivityType.SWIMMING))
        assertEquals(R.drawable.ic_row_24, GoalDisplay.iconRes(ActivityType.ROWING))
        assertEquals(R.drawable.ic_pulse_24, GoalDisplay.iconRes(null))
    }

    @Test
    fun `any activity reads as the carousel reads it aloud`() {
        assertEquals("Any activity", text.string(GoalDisplay.activityNameRes(null)))
        assertEquals("Swimming", text.string(GoalDisplay.activityNameRes(ActivityType.SWIMMING)))
        assertEquals("Rowing", text.string(GoalDisplay.activityNameRes(ActivityType.ROWING)))
        assertEquals(
            NewGoalPlan.cardFor(ActivityOption.ANY).accessibilityNameRes,
            GoalDisplay.activityNameRes(null),
        )
    }

    private fun progress(type: GoalType, activity: ActivityType?, current: Double, target: Double): String =
        GoalDisplay.progressText(text, type, activity, current, target, locale)
}
