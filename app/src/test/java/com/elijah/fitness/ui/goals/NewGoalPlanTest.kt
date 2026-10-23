package com.elijah.fitness.ui.goals

import com.elijah.fitness.R
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
 * The New goal sheet's rules and wording (phase 8d, NewGoal.dc.html),
 * checked against the numbers the design's script produces.
 */
class NewGoalPlanTest {

    private val t = StringsXmlText
    private val uk = Locale.UK

    // ---------- metrics per activity ----------

    @Test
    fun `each activity offers the design's metrics in order`() {
        val distanceTimeSessions = listOf(GoalType.DISTANCE, GoalType.DURATION, GoalType.COUNT)
        listOf(ActivityOption.RUN, ActivityOption.RIDE, ActivityOption.SWIM, ActivityOption.ROW).forEach {
            assertEquals(it.name, distanceTimeSessions, NewGoalPlan.metricsFor(it))
        }
        assertEquals(
            listOf(GoalType.DURATION, GoalType.COUNT, GoalType.VOLUME),
            NewGoalPlan.metricsFor(ActivityOption.LIFT),
        )
        assertEquals(listOf(GoalType.DURATION, GoalType.COUNT), NewGoalPlan.metricsFor(ActivityOption.ANY))
    }

    @Test
    fun `switching activity keeps the metric when offered, else takes the first`() {
        assertEquals(GoalType.DISTANCE, NewGoalPlan.metricAfterActivityChange(ActivityOption.SWIM, GoalType.DISTANCE))
        assertEquals(GoalType.DURATION, NewGoalPlan.metricAfterActivityChange(ActivityOption.LIFT, GoalType.DISTANCE))
        assertEquals(GoalType.COUNT, NewGoalPlan.metricAfterActivityChange(ActivityOption.ANY, GoalType.COUNT))
        assertEquals(GoalType.DURATION, NewGoalPlan.metricAfterActivityChange(ActivityOption.ANY, GoalType.VOLUME))
        assertEquals(GoalType.DISTANCE, NewGoalPlan.metricAfterActivityChange(ActivityOption.ROW, GoalType.VOLUME))
    }

    // ---------- steps, defaults, limits ----------

    @Test
    fun `distance steps and defaults depend on the activity`() {
        assertSpec(5.0, 50.0, NewGoalPlan.spec(ActivityOption.RUN, GoalType.DISTANCE))
        assertSpec(10.0, 100.0, NewGoalPlan.spec(ActivityOption.RIDE, GoalType.DISTANCE))
        assertSpec(0.5, 10.0, NewGoalPlan.spec(ActivityOption.SWIM, GoalType.DISTANCE))
        assertSpec(5.0, 30.0, NewGoalPlan.spec(ActivityOption.ROW, GoalType.DISTANCE))
        ActivityOption.entries.forEach {
            assertEquals(1_000.0, NewGoalPlan.spec(it, GoalType.DISTANCE).max, 0.0)
        }
    }

    @Test
    fun `time, sessions and weight steps, defaults and server limits`() {
        val time = NewGoalPlan.spec(ActivityOption.ANY, GoalType.DURATION)
        assertSpec(15.0, 150.0, time)
        assertEquals(10_000.0, time.max, 0.0)
        val sessions = NewGoalPlan.spec(ActivityOption.RUN, GoalType.COUNT)
        assertSpec(1.0, 12.0, sessions)
        assertEquals(100.0, sessions.max, 0.0)
        val weight = NewGoalPlan.spec(ActivityOption.LIFT, GoalType.VOLUME)
        assertSpec(1_000.0, 20_000.0, weight)
        assertEquals(500_000.0, weight.max, 0.0)
    }

    @Test
    fun `stepping never goes below one step or above the limit`() {
        val swim = NewGoalPlan.spec(ActivityOption.SWIM, GoalType.DISTANCE)
        assertEquals(10.5, NewGoalPlan.stepTarget(10.0, +1, swim), 0.0)
        assertEquals(9.5, NewGoalPlan.stepTarget(10.0, -1, swim), 0.0)
        assertEquals(0.5, NewGoalPlan.stepTarget(0.5, -1, swim), 0.0)
        assertFalse(NewGoalPlan.canDecreaseTarget(0.5, swim))
        assertTrue(NewGoalPlan.canDecreaseTarget(1.0, swim))

        val run = NewGoalPlan.spec(ActivityOption.RUN, GoalType.DISTANCE)
        // The design's max(step, v − step): 7 km − 5 lands on 5, not 2.
        assertEquals(5.0, NewGoalPlan.stepTarget(7.0, -1, run), 0.0)
        assertEquals(1_000.0, NewGoalPlan.stepTarget(998.0, +1, run), 0.0)
        assertFalse(NewGoalPlan.canIncreaseTarget(1_000.0, run))

        val weight = NewGoalPlan.spec(ActivityOption.LIFT, GoalType.VOLUME)
        assertEquals(500_000.0, NewGoalPlan.stepTarget(499_500.0, +1, weight), 0.0)
    }

    @Test
    fun `time targets stop only at the server's limit, as in the design`() {
        // The design's "+" has no cap; the server holds at most 10,000 min.
        val time = NewGoalPlan.spec(ActivityOption.ROW, GoalType.DURATION)
        assertEquals(10_000.0, time.max, 0.0)
        assertEquals(2_000.0, NewGoalPlan.sanitizeTarget(2_000.0, time), 0.0)
        assertTrue(NewGoalPlan.canIncreaseTarget(1_440.0, time))
        assertEquals(1_455.0, NewGoalPlan.stepTarget(1_440.0, +1, time), 0.0)
        assertEquals(10_000.0, NewGoalPlan.sanitizeTarget(20_000.0, time), 0.0)
    }

    @Test
    fun `a long hint wraps between whole phrases`() {
        // Row for 42 h 30 min in 30 days: 85 min a day.
        val hint = NewGoalPlan.targetHint(t, GoalType.DURATION, 2_550.0, 30, uk)
        assertEquals("About 1\u00A0h\u00A025\u00A0min a\u00A0day", hint)
        // The only ordinary spaces: after "About" and before "a day".
        assertEquals(listOf("About", "1\u00A0h\u00A025\u00A0min", "a\u00A0day"), hint.split(' '))
    }

    @Test
    fun `days stay between 1 and 365`() {
        assertEquals(365, NewGoalPlan.stepDays(365, +1))
        assertEquals(1, NewGoalPlan.stepDays(1, -1))
        assertEquals(31, NewGoalPlan.stepDays(30, +1))
    }

    @Test
    fun `an edited goal can't be shortened to end before today`() {
        val today = LocalDate.of(2026, 10, 20)
        // New goals (no start yet) and goals starting today: 1 day.
        assertEquals(1, NewGoalPlan.lowestDays(null, today))
        assertEquals(1, NewGoalPlan.lowestDays(today, today))
        // Started 15 Oct: at least 6 days, so it ends today at the earliest.
        val lowest = NewGoalPlan.lowestDays(LocalDate.of(2026, 10, 15), today)
        assertEquals(6, lowest)
        assertEquals(today, NewGoalPlan.endDate(LocalDate.of(2026, 10, 15), lowest))
        assertEquals(6, NewGoalPlan.stepDays(7, -1, lowest))
        assertEquals(6, NewGoalPlan.stepDays(6, -1, lowest))
        // An ended goal below the lowest never shrinks, and can still grow.
        assertEquals(4, NewGoalPlan.stepDays(4, -1, lowest))
        assertEquals(5, NewGoalPlan.stepDays(4, +1, lowest))
        // A start more than a year back never asks for more than 365.
        assertEquals(365, NewGoalPlan.lowestDays(LocalDate.of(2024, 1, 1), today))
    }

    @Test
    fun `a seeded target is cleaned up, never truncated`() {
        val swim = NewGoalPlan.spec(ActivityOption.SWIM, GoalType.DISTANCE)
        assertEquals(10.5, NewGoalPlan.sanitizeTarget(10.5, swim), 0.0)
        assertEquals(10.0, NewGoalPlan.sanitizeTarget(Double.NaN, swim), 0.0)
        assertEquals(10.0, NewGoalPlan.sanitizeTarget(-3.0, swim), 0.0)
        assertEquals(1_000.0, NewGoalPlan.sanitizeTarget(5_000.0, swim), 0.0)
    }

    // ---------- dates and pace ----------

    @Test
    fun `the goal ends on its last inclusive day`() {
        val start = LocalDate.of(2026, 10, 19)
        assertEquals(LocalDate.of(2026, 11, 17), NewGoalPlan.endDate(start, 30))
        assertEquals(start, NewGoalPlan.endDate(start, 1))
        assertEquals("17 Nov", NewGoalPlan.shortDate(LocalDate.of(2026, 11, 17), uk))
    }

    @Test
    fun `seven days or more show a weekly pace, shorter goals a daily one`() {
        assertTrue(NewGoalPlan.isWeekly(7))
        assertFalse(NewGoalPlan.isWeekly(6))
        assertEquals(R.string.new_goal_pace_per_week, NewGoalPlan.perLabelRes(30))
        assertEquals(R.string.new_goal_pace_per_day, NewGoalPlan.perLabelRes(5))
        assertEquals(50.0 * 7 / 30, NewGoalPlan.perPeriod(50.0, 30), 1e-9)
        assertEquals(10.0, NewGoalPlan.perPeriod(50.0, 5), 1e-9)
    }

    // ---------- wording ----------

    @Test
    fun `sentences use the design's verbs`() {
        assertEquals("Run 50 km in 30 days", sentence(ActivityOption.RUN, GoalType.DISTANCE, 50.0, 30))
        assertEquals("Ride 100 km in 30 days", sentence(ActivityOption.RIDE, GoalType.DISTANCE, 100.0, 30))
        assertEquals("Swim 10.5 km in 30 days", sentence(ActivityOption.SWIM, GoalType.DISTANCE, 10.5, 30))
        assertEquals("Row 30 km in 1 day", sentence(ActivityOption.ROW, GoalType.DISTANCE, 30.0, 1))
        assertEquals("Swim for 2 h 30 min in 30 days", sentence(ActivityOption.SWIM, GoalType.DURATION, 150.0, 30))
        assertEquals("Work out for 45 min in 7 days", sentence(ActivityOption.LIFT, GoalType.DURATION, 45.0, 7))
        assertEquals("Be active for 2 h in 14 days", sentence(ActivityOption.ANY, GoalType.DURATION, 120.0, 14))
        assertEquals("Complete 12 runs in 30 days", sentence(ActivityOption.RUN, GoalType.COUNT, 12.0, 30))
        assertEquals("Complete 1 swim in 1 day", sentence(ActivityOption.SWIM, GoalType.COUNT, 1.0, 1))
        assertEquals("Complete 12 workouts in 30 days", sentence(ActivityOption.ANY, GoalType.COUNT, 12.0, 30))
        assertEquals("Lift 20,000 kg in 30 days", sentence(ActivityOption.LIFT, GoalType.VOLUME, 20_000.0, 30))
    }

    @Test
    fun `stepper values read like the design`() {
        assertEquals("50 km", value(ActivityOption.RUN, GoalType.DISTANCE, 50.0))
        assertEquals("0.5 km", value(ActivityOption.SWIM, GoalType.DISTANCE, 0.5))
        assertEquals("2 h 30 min", value(ActivityOption.ROW, GoalType.DURATION, 150.0))
        assertEquals("15 min", value(ActivityOption.ROW, GoalType.DURATION, 15.0))
        assertEquals("12 rides", value(ActivityOption.RIDE, GoalType.COUNT, 12.0))
        assertEquals("1 row", value(ActivityOption.ROW, GoalType.COUNT, 1.0))
        assertEquals("12 workouts", value(ActivityOption.LIFT, GoalType.COUNT, 12.0))
        assertEquals("20,000 kg", value(ActivityOption.LIFT, GoalType.VOLUME, 20_000.0))
        assertEquals("30 days", NewGoalPlan.daysValueText(t, 30))
        assertEquals("1 day", NewGoalPlan.daysValueText(t, 1))
    }

    @Test
    fun `target hints match the design's examples`() {
        assertEquals("About 1.7\u00A0km a\u00A0day", NewGoalPlan.targetHint(t, GoalType.DISTANCE, 50.0, 30, uk))
        assertEquals("About 5\u00A0min a\u00A0day", NewGoalPlan.targetHint(t, GoalType.DURATION, 150.0, 30, uk))
        assertEquals("About 2.8 a\u00A0week", NewGoalPlan.targetHint(t, GoalType.COUNT, 12.0, 30, uk))
        assertEquals("About 2.4 a\u00A0day", NewGoalPlan.targetHint(t, GoalType.COUNT, 12.0, 5, uk))
        assertEquals("About 667\u00A0kg a\u00A0day", NewGoalPlan.targetHint(t, GoalType.VOLUME, 20_000.0, 30, uk))
        // Never "0 min": at least a minute a day.
        assertEquals("About 1\u00A0min a\u00A0day", NewGoalPlan.targetHint(t, GoalType.DURATION, 15.0, 365, uk))
    }

    @Test
    fun `per week and per day values`() {
        assertEquals("11.7 km", per(ActivityOption.RUN, GoalType.DISTANCE, 50.0, 30))
        assertEquals("10 km", per(ActivityOption.RUN, GoalType.DISTANCE, 50.0, 5))
        assertEquals("35 min", per(ActivityOption.SWIM, GoalType.DURATION, 150.0, 30))
        assertEquals("1 h 15 min", per(ActivityOption.SWIM, GoalType.DURATION, 150.0, 2))
        assertEquals("2.8 runs", per(ActivityOption.RUN, GoalType.COUNT, 12.0, 30))
        assertEquals("1 run", per(ActivityOption.RUN, GoalType.COUNT, 7.0, 49))
        assertEquals("4,667 kg", per(ActivityOption.LIFT, GoalType.VOLUME, 20_000.0, 30))
    }

    @Test
    fun `the burn estimate is rounded to 10 kcal`() {
        assertEquals(1_260, NewGoalPlan.estimatedKcal(ActivityOption.SWIM, GoalType.DURATION, 150.0, 72.0))
        assertEquals(3_530, NewGoalPlan.estimatedKcal(ActivityOption.RUN, GoalType.DISTANCE, 50.0, 72.0))
        assertEquals(2_700, NewGoalPlan.estimatedKcal(ActivityOption.RIDE, GoalType.DISTANCE, 100.0, 72.0))
        assertEquals("1,260 kcal", NewGoalPlan.kcalText(t, 1_260, uk))
    }

    @Test
    fun `days hint says when the goal starts`() {
        val today = LocalDate.of(2026, 10, 19)
        assertEquals("Starts today", NewGoalPlan.daysHint(t, null, today, uk))
        assertEquals("Starts today", NewGoalPlan.daysHint(t, today, today, uk))
        assertEquals("Started 12 Oct", NewGoalPlan.daysHint(t, LocalDate.of(2026, 10, 12), today, uk))
    }

    @Test
    fun `cards name every activity, Any reads Any activity to TalkBack`() {
        val any = NewGoalPlan.cardFor(ActivityOption.ANY)
        assertNull(any.activity)
        assertEquals(R.string.new_goal_card_any, any.nameRes)
        assertEquals(R.string.new_goal_activity_any, any.accessibilityNameRes)
        assertEquals(R.drawable.ic_pulse_24, any.iconRes)
        val swim = NewGoalPlan.cardFor(ActivityOption.SWIM)
        assertEquals(R.string.new_goal_card_swimming, swim.descriptionRes)
        assertEquals(swim.nameRes, swim.accessibilityNameRes)
        assertEquals(R.string.new_goal_card_workout, NewGoalPlan.cardFor(ActivityOption.LIFT).descriptionRes)
    }

    // ---------- helpers ----------

    private fun assertSpec(step: Double, default: Double, spec: NewGoalPlan.TargetSpec) {
        assertEquals(step, spec.step, 0.0)
        assertEquals(step, spec.min, 0.0)
        assertEquals(default, spec.default, 0.0)
    }

    private fun sentence(a: ActivityOption, m: GoalType, target: Double, days: Int) =
        NewGoalPlan.sentence(t, a, m, target, days, uk)

    private fun value(a: ActivityOption, m: GoalType, target: Double) =
        NewGoalPlan.targetValueText(t, a, m, target, uk)

    private fun per(a: ActivityOption, m: GoalType, target: Double, days: Int) =
        NewGoalPlan.perValueText(t, a, m, target, days, uk)
}
