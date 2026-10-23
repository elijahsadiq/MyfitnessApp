package com.elijah.fitness.ui.goals

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.elijah.fitness.R
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.ui.components.ActivityCardItem
import com.elijah.fitness.ui.goals.NewGoalViewModel.ActivityOption
import com.elijah.fitness.util.ActivityLabels
import com.elijah.fitness.util.CalorieEstimator
import com.elijah.fitness.util.DurationFormatter
import com.elijah.fitness.util.Formatters
import com.elijah.fitness.util.TextResolver
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * The rules and the wording of the New goal sheet (phase 8d,
 * NewGoal.dc.html), as plain Kotlin so every number is unit-tested.
 * It follows the design's script line by line:
 *
 * - **Metrics per activity:** runs, rides, swims and rows measure by
 *   Distance, Time or Sessions; a Workout by Time, Sessions or Weight;
 *   Any activity by Time or Sessions.
 * - **Steps and defaults** ([spec]): distance 5 km / 50 km (run),
 *   10 / 100 (ride), 0.5 / 10 (swim), 5 / 30 (row); time 15 min /
 *   2 h 30 min; sessions 1 / 12; weight 1,000 kg / 20,000 kg. The
 *   lowest target is one step, the highest the server's limit (the
 *   design's script has no upper limit; the server needs one). The
 *   number of days never changes the target, as in the script.
 * - **Days:** 1 to 365; the goal ends on `start + days − 1` (the last
 *   day counts, as everywhere since phase 8b). Editing a goal that
 *   began earlier can't end it before today ([lowestDays]).
 * - **Summary:** the goal sentence (also the stored title), the pace
 *   "Per week" (or "Per day" for goals under a week), the end date and
 *   the estimated burn ([CalorieEstimator.goalEstimateKcal]).
 *
 * Targets are Doubles throughout: a swim distance moves in 0.5 km steps.
 */
object NewGoalPlan {

    const val DAYS_MIN: Int = 1
    const val DAYS_MAX: Int = 365
    const val DAYS_DEFAULT: Int = 30

    /** Goals at least this many days long show a weekly pace. */
    private const val DAYS_PER_WEEK: Int = 7

    /** Highest targets the server accepts (GoalController::TARGET_MAX). */
    private const val MAX_DISTANCE_KM = 1_000.0
    private const val MAX_DURATION_MIN = 10_000.0
    private const val MAX_SESSIONS = 100.0
    private const val MAX_VOLUME_KG = 500_000.0

    /** Floating-point slack when comparing a target with its limits. */
    private const val EPSILON = 1e-9

    /** The stepper rules of one metric: step, lowest, highest and starting value. */
    data class TargetSpec(
        val step: Double,
        val min: Double,
        val max: Double,
        val default: Double,
    )

    // ------------------------------------------------------------------
    // Rules
    // ------------------------------------------------------------------

    /** What [activity] can be measured by, in the "Measure by" order. */
    fun metricsFor(activity: ActivityOption): List<GoalType> = when (activity) {
        ActivityOption.RUN, ActivityOption.RIDE, ActivityOption.SWIM, ActivityOption.ROW ->
            listOf(GoalType.DISTANCE, GoalType.DURATION, GoalType.COUNT)
        ActivityOption.LIFT ->
            listOf(GoalType.DURATION, GoalType.COUNT, GoalType.VOLUME)
        ActivityOption.ANY ->
            listOf(GoalType.DURATION, GoalType.COUNT)
    }

    /**
     * The metric after switching to [activity]: [current] when the new
     * activity still offers it, otherwise its first metric.
     */
    fun metricAfterActivityChange(activity: ActivityOption, current: GoalType): GoalType {
        val offered = metricsFor(activity)
        return if (current in offered) current else offered.first()
    }

    /** Step, limits (one step up to the server's limit) and default of [metric] for [activity]. */
    fun spec(activity: ActivityOption, metric: GoalType): TargetSpec = when (metric) {
        GoalType.DISTANCE -> {
            val (step, default) = when (activity) {
                ActivityOption.RUN -> 5.0 to 50.0
                ActivityOption.RIDE -> 10.0 to 100.0
                ActivityOption.SWIM -> 0.5 to 10.0
                ActivityOption.ROW -> 5.0 to 30.0
                // Workouts and "any" have no distance goal; a run's values
                // keep the stepper sane if one ever arrives.
                ActivityOption.LIFT, ActivityOption.ANY -> 5.0 to 50.0
            }
            TargetSpec(step = step, min = step, max = MAX_DISTANCE_KM, default = default)
        }
        GoalType.DURATION -> TargetSpec(step = 15.0, min = 15.0, max = MAX_DURATION_MIN, default = 150.0)
        GoalType.COUNT -> TargetSpec(step = 1.0, min = 1.0, max = MAX_SESSIONS, default = 12.0)
        GoalType.VOLUME -> TargetSpec(step = 1_000.0, min = 1_000.0, max = MAX_VOLUME_KG, default = 20_000.0)
    }

    /**
     * One step down ([direction] < 0) or up from [value]: never below one
     * step, never above the server's limit, kept to two decimals (the
     * server stores `DECIMAL(10,2)`).
     */
    fun stepTarget(value: Double, direction: Int, spec: TargetSpec): Double {
        val start = if (value.isFinite()) value else spec.default
        val next = if (direction < 0) start - spec.step else start + spec.step
        return roundToHundredths(next.coerceIn(spec.min, spec.max))
    }

    fun canDecreaseTarget(value: Double, spec: TargetSpec): Boolean = value > spec.min + EPSILON

    fun canIncreaseTarget(value: Double, spec: TargetSpec): Boolean = value < spec.max - EPSILON

    /** A usable target for a seeded or loaded goal: finite, above 0, at most the limit. */
    fun sanitizeTarget(value: Double, spec: TargetSpec): Double =
        if (!value.isFinite() || value <= 0.0) spec.default else roundToHundredths(value.coerceAtMost(spec.max))

    /**
     * One step of the Days stepper, kept in [lowest]..365. A goal already
     * below [lowest] (an edited goal that has ended) can only grow: "−"
     * never moves it, "+" adds one day.
     */
    fun stepDays(days: Int, direction: Int, lowest: Int = DAYS_MIN): Int {
        val floorDays = lowest.coerceIn(DAYS_MIN, DAYS_MAX)
        return if (direction < 0) {
            if (days <= floorDays) days.coerceIn(DAYS_MIN, DAYS_MAX) else (days - 1).coerceIn(floorDays, DAYS_MAX)
        } else {
            (days + 1).coerceIn(DAYS_MIN, DAYS_MAX)
        }
    }

    /**
     * The fewest days the Days stepper offers. A new goal starts today,
     * so 1. A goal being edited keeps its own [startDate]: it must still
     * reach [today], or saving would end it on the spot (started 15 Oct,
     * today 20 Oct: at least 6 days, ending today).
     */
    fun lowestDays(startDate: LocalDate?, today: LocalDate): Int {
        if (startDate == null || !startDate.isBefore(today)) return DAYS_MIN
        val elapsed = ChronoUnit.DAYS.between(startDate, today) + 1
        return elapsed.coerceIn(DAYS_MIN.toLong(), DAYS_MAX.toLong()).toInt()
    }

    /** The last day of the goal, inclusive: a 30-day goal started on 19 Oct ends on 17 Nov. */
    fun endDate(start: LocalDate, days: Int): LocalDate =
        start.plusDays((days.coerceIn(DAYS_MIN, DAYS_MAX) - 1).toLong())

    /** True when the summary shows "Per week" (7 days or more), false for "Per day". */
    fun isWeekly(days: Int): Boolean = days >= DAYS_PER_WEEK

    /** The target per week (7+ day goals) or per day (shorter goals). */
    fun perPeriod(target: Double, days: Int): Double {
        val d = days.coerceAtLeast(DAYS_MIN).toDouble()
        return if (isWeekly(days)) target * DAYS_PER_WEEK / d else target / d
    }

    /** Estimated calories for the goal at [weightKg], to the nearest 10 kcal. */
    fun estimatedKcal(
        activity: ActivityOption,
        metric: GoalType,
        target: Double,
        weightKg: Double,
    ): Int = CalorieEstimator.goalEstimateKcal(activity.toApi(), metric, target, weightKg)

    // ------------------------------------------------------------------
    // Wording
    // ------------------------------------------------------------------

    /** "Run 50 km in 30 days": the summary sentence and the stored title. */
    fun sentence(
        text: TextResolver,
        activity: ActivityOption,
        metric: GoalType,
        target: Double,
        days: Int,
        locale: Locale = Locale.getDefault(),
    ): String = autoTitle(text, activity, metric, target, days, locale)

    /** The target row's label ("Distance", "Total time", …), shown in capitals. */
    @StringRes
    fun targetLabelRes(metric: GoalType): Int = when (metric) {
        GoalType.DISTANCE -> R.string.new_goal_target_label_distance
        GoalType.DURATION -> R.string.new_goal_target_label_time
        GoalType.COUNT -> R.string.new_goal_target_label_sessions
        GoalType.VOLUME -> R.string.new_goal_target_label_weight
    }

    /** A "Measure by" segment's label. */
    @StringRes
    fun metricLabelRes(metric: GoalType): Int = when (metric) {
        GoalType.DISTANCE -> R.string.new_goal_metric_distance
        GoalType.DURATION -> R.string.new_goal_metric_time
        GoalType.COUNT -> R.string.new_goal_metric_sessions
        GoalType.VOLUME -> R.string.new_goal_metric_weight
    }

    /** The stepper value: "50 km", "2 h 30 min", "12 runs", "20,000 kg". */
    fun targetValueText(
        text: TextResolver,
        activity: ActivityOption,
        metric: GoalType,
        target: Double,
        locale: Locale = Locale.getDefault(),
    ): String {
        val amount = Formatters.formatGoalAmount(target, locale)
        return when (metric) {
            GoalType.DISTANCE -> text.string(R.string.new_goal_value_km, amount)
            GoalType.DURATION -> DurationFormatter.format(text, minutes(target))
            GoalType.COUNT -> text.string(
                R.string.new_goal_value_count,
                amount,
                sessionsWord(text, activity, pluralQuantity(target)),
            )
            GoalType.VOLUME -> text.string(R.string.new_goal_value_kg, amount)
        }
    }

    /**
     * The hint under the target label: "About 1.7 km a day",
     * "About 5 min a day", "About 2.8 a week" (sessions in goals of a
     * week or more; "a day" below that), "About 667 kg a day".
     *
     * The amount and the closing "a day" / "a week" are each held
     * together with non-breaking spaces (U+00A0), so the narrow column
     * wraps as "About 1 h 25 min" / "a day", never leaving "day" alone or
     * splitting "1 h 25 min".
     */
    fun targetHint(
        text: TextResolver,
        metric: GoalType,
        target: Double,
        days: Int,
        locale: Locale = Locale.getDefault(),
    ): String {
        val perDay = target / days.coerceAtLeast(DAYS_MIN)
        return when (metric) {
            GoalType.DISTANCE -> aDay(text, km(text, Formatters.formatTenths(perDay, locale)))
            GoalType.DURATION -> aDay(text, DurationFormatter.format(text, minutes(max(1.0, perDay))))
            GoalType.COUNT ->
                if (isWeekly(days)) {
                    glueLastWord(text.string(R.string.new_goal_hint_per_week, Formatters.formatTenths(perPeriod(target, days), locale)))
                } else {
                    aDay(text, Formatters.formatTenths(perDay, locale))
                }
            GoalType.VOLUME -> aDay(text, kg(text, wholeAmount(perDay, locale)))
        }
    }

    /** The first tile's label: "Per week", or "Per day" for goals under a week. */
    @StringRes
    fun perLabelRes(days: Int): Int =
        if (isWeekly(days)) R.string.new_goal_pace_per_week else R.string.new_goal_pace_per_day

    /** The first tile's value: "11.7 km", "35 min", "2.8 runs", "4,667 kg". */
    fun perValueText(
        text: TextResolver,
        activity: ActivityOption,
        metric: GoalType,
        target: Double,
        days: Int,
        locale: Locale = Locale.getDefault(),
    ): String {
        val per = perPeriod(target, days)
        return when (metric) {
            GoalType.DISTANCE -> km(text, Formatters.formatTenths(per, locale))
            GoalType.DURATION -> DurationFormatter.format(text, minutes(max(1.0, per)))
            GoalType.COUNT -> {
                // One word per the shown number: "1 run", but "0.9 runs".
                val shown = Formatters.roundToTenths(per)
                text.string(
                    R.string.new_goal_value_count,
                    Formatters.formatTenths(per, locale),
                    sessionsWord(text, activity, pluralQuantity(shown)),
                )
            }
            GoalType.VOLUME -> kg(text, wholeAmount(per, locale))
        }
    }

    /** The Days stepper value: "30 days", "1 day". */
    fun daysValueText(text: TextResolver, days: Int): String =
        text.plural(R.plurals.new_goal_days_value, days, days)

    /** "Starts today", or "Started 12 Oct" when editing a goal that began earlier. */
    fun daysHint(
        text: TextResolver,
        startDate: LocalDate?,
        today: LocalDate,
        locale: Locale = Locale.getDefault(),
    ): String =
        if (startDate != null && startDate.isBefore(today)) {
            text.string(R.string.new_goal_days_hint_started, shortDate(startDate, locale))
        } else {
            text.string(R.string.new_goal_days_hint_today)
        }

    /** "17 Nov". */
    fun shortDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("d MMM", locale))

    /** "1,260 kcal". */
    fun kcalText(text: TextResolver, kcal: Int, locale: Locale = Locale.getDefault()): String =
        text.string(R.string.new_goal_kcal_value, Formatters.formatKcalValue(kcal, locale))

    /** The activity card for [option]: icon, name and what it can measure. */
    fun cardFor(option: ActivityOption): ActivityCardItem {
        val type = option.toApi()
        return ActivityCardItem(
            activity = type,
            iconRes = type?.let(ActivityLabels::cardIconRes) ?: R.drawable.ic_pulse_24,
            nameRes = type?.let(ActivityLabels::nameRes) ?: R.string.new_goal_card_any,
            descriptionRes = cardDescriptionRes(option),
            spokenNameRes = if (type == null) R.string.new_goal_activity_any else null,
        )
    }

    @StringRes
    private fun cardDescriptionRes(option: ActivityOption): Int = when (option) {
        ActivityOption.RUN -> R.string.new_goal_card_running
        ActivityOption.RIDE -> R.string.new_goal_card_cycling
        ActivityOption.SWIM -> R.string.new_goal_card_swimming
        ActivityOption.ROW -> R.string.new_goal_card_rowing
        ActivityOption.LIFT -> R.string.new_goal_card_workout
        ActivityOption.ANY -> R.string.new_goal_card_any_description
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** A minutes target as whole minutes, rounded half up (the design's `Math.round`). */
    internal fun minutes(value: Double): Long = if (value.isFinite()) value.roundToLong() else 0L

    /**
     * The quantity that picks a plural form: whole numbers as they are,
     * anything with a fraction as "many" (2), so 1 reads "1 run" and
     * 1.5 reads "1.5 runs".
     */
    internal fun pluralQuantity(value: Double): Int {
        if (!value.isFinite()) return 0
        val whole = value.roundToLong()
        return if (abs(value - whole) < EPSILON) {
            whole.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        } else {
            FRACTION_QUANTITY
        }
    }

    /** Any quantity that reads as "other" in English. */
    private const val FRACTION_QUANTITY = 2

    private fun sessionsWord(text: TextResolver, activity: ActivityOption, quantity: Int): String =
        text.plural(sessionsWordRes(activity), quantity)

    @PluralsRes
    private fun sessionsWordRes(activity: ActivityOption): Int =
        ActivityLabels.sessionsUnitRes(activity.toApi())

    private fun km(text: TextResolver, amount: String): String =
        text.string(R.string.new_goal_value_km, amount)

    private fun kg(text: TextResolver, amount: String): String =
        text.string(R.string.new_goal_value_kg, amount)

    /** "About 1.7 km a day", with the amount and "a day" each kept on one line. */
    private fun aDay(text: TextResolver, amount: String): String =
        glueLastWord(text.string(R.string.new_goal_hint_per_day, amount.replace(' ', NBSP)))

    /** [phrase] with its last space non-breaking: "a day" stays together. */
    private fun glueLastWord(phrase: String): String {
        val lastSpace = phrase.lastIndexOf(' ')
        return if (lastSpace <= 0) phrase else phrase.substring(0, lastSpace) + NBSP + phrase.substring(lastSpace + 1)
    }

    private const val NBSP = '\u00A0'

    /** Rounded half up to a whole number, with a thousands separator: "4,667". */
    private fun wholeAmount(value: Double, locale: Locale): String =
        Formatters.formatGoalAmount(if (value.isFinite()) value.roundToLong().toDouble() else 0.0, locale)

    private fun roundToHundredths(value: Double): Double =
        (value * 100.0).roundToLong() / 100.0
}

// ----------------------------------------------------------------------
// Goal titles
// ----------------------------------------------------------------------

/**
 * The goal sentence, which is also the stored title: "Run 50 km in
 * 30 days", "Swim 10.5 km in 30 days", "Swim for 2 h 30 min in 30 days",
 * "Complete 12 runs in 30 days", "Lift 20,000 kg in 30 days". The words
 * come from strings.xml / plurals.xml through [text]; amounts use the
 * locale's separators and keep a swim's half kilometre; time reads
 * "2 h 30 min", never "150 min".
 */
fun autoTitle(
    text: TextResolver,
    activity: ActivityOption,
    metric: GoalType,
    target: Double,
    days: Int,
    locale: Locale = Locale.getDefault(),
): String {
    val amount = Formatters.formatGoalAmount(target, locale)
    val daysText = text.plural(R.plurals.goal_title_days, days, days)
    return when (metric) {
        GoalType.DISTANCE -> {
            val res = distanceTitleRes(activity)
                ?: return text.string(R.string.goal_title_generic, amount, daysText)
            text.string(res, amount, daysText)
        }
        GoalType.DURATION -> text.string(
            durationTitleRes(activity),
            DurationFormatter.format(text, NewGoalPlan.minutes(target)),
            daysText,
        )
        GoalType.VOLUME -> text.string(R.string.goal_title_volume, amount, daysText)
        GoalType.COUNT -> text.plural(
            sessionsTitleRes(activity),
            NewGoalPlan.pluralQuantity(target),
            amount,
            daysText,
        )
    }
}

/** `null` for Workout / Any, which have no distance goal. */
private fun distanceTitleRes(activity: ActivityOption): Int? = when (activity) {
    ActivityOption.RUN -> R.string.goal_title_distance_run
    ActivityOption.RIDE -> R.string.goal_title_distance_ride
    ActivityOption.SWIM -> R.string.goal_title_distance_swim
    ActivityOption.ROW -> R.string.goal_title_distance_row
    ActivityOption.LIFT,
    ActivityOption.ANY -> null
}

private fun durationTitleRes(activity: ActivityOption): Int = when (activity) {
    ActivityOption.RUN -> R.string.goal_title_time_run
    ActivityOption.RIDE -> R.string.goal_title_time_ride
    ActivityOption.SWIM -> R.string.goal_title_time_swim
    ActivityOption.ROW -> R.string.goal_title_time_row
    ActivityOption.LIFT -> R.string.goal_title_time_lift
    ActivityOption.ANY -> R.string.goal_title_time_any
}

private fun sessionsTitleRes(activity: ActivityOption): Int = when (activity) {
    ActivityOption.RUN -> R.plurals.goal_title_sessions_run
    ActivityOption.RIDE -> R.plurals.goal_title_sessions_ride
    ActivityOption.SWIM -> R.plurals.goal_title_sessions_swim
    ActivityOption.ROW -> R.plurals.goal_title_sessions_row
    ActivityOption.LIFT -> R.plurals.goal_title_sessions_lift
    ActivityOption.ANY -> R.plurals.goal_title_sessions_any
}
