package com.elijah.fitness.ui.goals

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.util.ActivityLabels
import com.elijah.fitness.util.DurationFormatter
import com.elijah.fitness.util.Formatters
import com.elijah.fitness.util.TextResolver
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * How a goal's numbers read on the Goals list, the main goal card and
 * the Manage sheet (phase 8d step 6). One place, so the three can't
 * drift apart, and pure Kotlin (words through [TextResolver]), so every
 * case is unit-tested.
 *
 * - **Time** always goes through [DurationFormatter]: "1 h 10 min /
 *   2 h 30 min", never "70 / 150 min".
 * - **Wrapping:** each amount ("2 h 30 min", "5,000 kg"), date and the
 *   unit after a number are held together with non-breaking spaces, so
 *   a narrow screen wraps between the parts of a line, never leaving a
 *   lone "kg", "min" or "Oct".
 * - **Distance** stays in km for every activity, swims included, with
 *   at most one decimal: "6.5 / 10 km", "0 / 10.5 km".
 * - **Progress** numbers round to the nearest shown step ("13.2 km"),
 *   except that an unmet target never looks met: 9.96 of 10 km reads
 *   "9.9 / 10 km", not "10 / 10 km". Targets read exactly as the goal
 *   sentence does.
 * - **Sessions** name the activity in the right plural: "3 / 12 swims",
 *   "1 / 1 row", "5 / 12 workouts" for any activity.
 */
object GoalDisplay {

    /** Ignores floating-point noise when rounding down (6.6 stored as 6.5999…). */
    private const val FLOOR_EPSILON = 1e-6

    /** Scale factors for [shownProgress]: one decimal, or whole numbers. */
    private const val TENTHS = 10.0
    private const val WHOLE = 1.0

    /** A non-breaking space: keeps "Mon 19 Oct" or "Any activity" on one line. */
    private const val NBSP = '\u00A0'

    /** The smallest rate a per-day tile shows as a number. */
    private const val SMALLEST_TENTH = 0.1

    // ------------------------------------------------------------------
    // Progress text
    // ------------------------------------------------------------------

    /** "6.5 / 10 km", "3 / 12 swims", "1 h 10 min / 2 h 30 min", "5,000 / 20,000 kg". */
    fun progressText(
        text: TextResolver,
        type: GoalType,
        activity: ActivityType?,
        current: Double,
        target: Double,
        locale: Locale = Locale.getDefault(),
    ): String {
        val unit = unitWord(text, type, activity, target)
            ?: return text.string(
                R.string.goal_value_over_target_duration,
                duration(text, currentMinutes(current)),
                duration(text, targetMinutes(target)),
            )
        return text.string(
            R.string.goal_value_over_target_decimal,
            currentNumber(type, current, target, locale),
            targetNumber(type, target, locale),
            unit,
        )
    }

    /** The target with its unit: "10 km", "12 swims", "2 h 30 min", "20,000 kg". */
    fun targetText(
        text: TextResolver,
        type: GoalType,
        activity: ActivityType?,
        target: Double,
        locale: Locale = Locale.getDefault(),
    ): String {
        val unit = unitWord(text, type, activity, target)
            ?: return duration(text, targetMinutes(target))
        return text.string(R.string.goal_amount_with_unit, targetNumber(type, target, locale), unit)
    }

    /** Progress so far with its unit: "6.5 km", "3 swims", "1 h 10 min", "5,000 kg". */
    fun currentText(
        text: TextResolver,
        type: GoalType,
        activity: ActivityType?,
        current: Double,
        locale: Locale = Locale.getDefault(),
    ): String {
        val unit = unitWord(text, type, activity, current)
            ?: return duration(text, currentMinutes(current))
        return text.string(R.string.goal_amount_with_unit, currentNumber(type, current, locale = locale), unit)
    }

    /** The Manage sheet's "of 10 km" / "of 2 h 30 min" next to the big number. */
    fun ofTargetText(
        text: TextResolver,
        type: GoalType,
        activity: ActivityType?,
        target: Double,
        locale: Locale = Locale.getDefault(),
    ): String = text.string(
        R.string.manage_goal_progress_of,
        targetText(text, type, activity, target, locale),
    )

    /** The main goal card's small "/ 10 km" / "/ 2 h 30 min" after the big number. */
    fun overTargetText(
        text: TextResolver,
        type: GoalType,
        activity: ActivityType?,
        target: Double,
        locale: Locale = Locale.getDefault(),
    ): String = text.string(
        R.string.goal_main_card_over_target,
        targetText(text, type, activity, target, locale),
    )

    /** "Finished Fri 2 Oct · 46 km" (or "· 2 h 30 min") on a completed row. */
    fun completedCaption(
        text: TextResolver,
        type: GoalType,
        activity: ActivityType?,
        lastDay: LocalDate,
        current: Double,
        locale: Locale = Locale.getDefault(),
    ): String = text.string(
        R.string.goal_completed_caption,
        unbreakable(rowDate(lastDay, locale)),
        currentText(text, type, activity, current, locale),
    )

    /** "Ended Mon 28 Sep · 16 of 20 km" (or "· 1 h 10 min of 2 h 30 min"). */
    fun endedCaption(
        text: TextResolver,
        type: GoalType,
        activity: ActivityType?,
        lastDay: LocalDate,
        current: Double,
        target: Double,
        locale: Locale = Locale.getDefault(),
    ): String {
        val date = unbreakable(rowDate(lastDay, locale))
        val unit = unitWord(text, type, activity, target)
            ?: return text.string(
                R.string.goal_ended_date_caption_duration,
                date,
                duration(text, currentMinutes(current)),
                duration(text, targetMinutes(target)),
            )
        return text.string(
            R.string.goal_ended_date_caption,
            date,
            currentNumber(type, current, target, locale),
            targetNumber(type, target, locale),
            unit,
        )
    }

    /**
     * A Manage stat tile ("Avg per day", "Needed per day"): "4.8 km",
     * "35 min", "1 h 5 min", "2,000 kg", "0.4" (sessions). A dash when
     * there is nothing to show (no progress yet, or the goal is done).
     * A rate too small to show at one decimal reads "<0.1" (or
     * "<0.1 km"), never a misleading "0": 12 runs over 365 days.
     */
    fun perDayText(
        text: TextResolver,
        type: GoalType,
        value: Double,
        locale: Locale = Locale.getDefault(),
    ): String {
        if (!value.isFinite() || value <= 0.0) return text.string(R.string.manage_goal_stat_value_blank)
        return when (type) {
            GoalType.DISTANCE ->
                text.string(R.string.manage_goal_stat_value_distance, tenthsOrBelow(text, value, locale))
            // At least a minute, so a tiny pace never reads "0 min".
            GoalType.DURATION ->
                duration(text, value.roundToLong().coerceAtLeast(1L))
            GoalType.VOLUME ->
                text.string(
                    R.string.manage_goal_stat_value_volume,
                    Formatters.formatGoalAmount(value.roundToLong().toDouble(), locale),
                )
            // "0.4" a day, rather than rounding to a misleading "0".
            GoalType.COUNT ->
                text.string(R.string.manage_goal_stat_value_count, tenthsOrBelow(text, value, locale))
        }
    }

    /** [value] (> 0) to one decimal, or "<0.1" when that would read 0. */
    private fun tenthsOrBelow(text: TextResolver, value: Double, locale: Locale): String =
        if (Formatters.roundToTenths(value) > 0.0) {
            Formatters.formatTenths(value, locale)
        } else {
            text.string(R.string.manage_goal_stat_value_below, Formatters.formatTenths(SMALLEST_TENTH, locale))
        }

    // ------------------------------------------------------------------
    // Numbers and units
    // ------------------------------------------------------------------

    /**
     * Progress so far as a number: distance to one decimal ("6.5",
     * "13.2"), everything else whole ("12", "5,000"), rounded to the
     * nearest. With [target], a goal that isn't reached yet never shows
     * the target itself: 9.96 of 10 km is "9.9", 19,999.6 of 20,000 kg is
     * "19,999".
     */
    fun currentNumber(
        type: GoalType,
        value: Double,
        target: Double? = null,
        locale: Locale = Locale.getDefault(),
    ): String = when (type) {
        GoalType.DISTANCE -> Formatters.formatGoalAmount(shownProgress(value, TENTHS, target), locale)
        GoalType.DURATION -> currentMinutes(value).toString()
        GoalType.COUNT, GoalType.VOLUME ->
            Formatters.formatGoalAmount(shownProgress(value, WHOLE, target), locale)
    }

    /**
     * The target as a number, as the goal sentence writes it: "10",
     * "10.5" (a swim's half kilometre), "20,000". Time targets are whole
     * minutes (see [targetText] for "2 h 30 min").
     */
    fun targetNumber(type: GoalType, value: Double, locale: Locale = Locale.getDefault()): String =
        when (type) {
            GoalType.DISTANCE -> Formatters.formatGoalAmount(value, locale)
            GoalType.DURATION -> targetMinutes(value).toString()
            GoalType.COUNT, GoalType.VOLUME ->
                Formatters.formatGoalAmount(if (value.isFinite()) value.roundToLong().toDouble() else 0.0, locale)
        }

    /**
     * The word after a goal number: "km", "kg", or the activity's
     * sessions in the plural that matches [quantity] ("1 swim",
     * "12 swims", "12 workouts" for any activity). `null` for a time
     * goal: [DurationFormatter] writes its own "h" and "min".
     */
    fun unitWord(text: TextResolver, type: GoalType, activity: ActivityType?, quantity: Double): String? =
        when (type) {
            GoalType.DISTANCE -> text.string(R.string.goal_unit_km)
            GoalType.VOLUME -> text.string(R.string.goal_unit_kg)
            GoalType.COUNT -> text.plural(
                ActivityLabels.sessionsUnitRes(activity),
                NewGoalPlan.pluralQuantity(if (quantity.isFinite()) floor(quantity + FLOOR_EPSILON) else 0.0),
            )
            GoalType.DURATION -> null
        }

    /**
     * [value] rounded to 1 / [scale] (tenths or wholes), or rounded down
     * instead when rounding would reach a [target] that isn't met yet.
     * "Met" is the goal's own test ([Goal.meetsTarget]), so the numbers
     * read the target exactly when the goal is Completed. Never negative;
     * NaN reads 0.
     */
    private fun shownProgress(value: Double, scale: Double, target: Double?): Double {
        val safe = if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0
        val rounded = (safe * scale).roundToLong() / scale
        val looksMetEarly = target != null && target.isFinite() &&
            !Goal.meetsTarget(safe, target) && rounded >= target
        return if (looksMetEarly) floor(safe * scale + FLOOR_EPSILON) / scale else rounded
    }

    /** Whole minutes of progress, rounded down (never negative). */
    internal fun currentMinutes(value: Double): Long =
        if (value.isFinite()) floor(value.coerceAtLeast(0.0) + FLOOR_EPSILON).toLong() else 0L

    /** A time target in whole minutes, rounded like the goal sentence. */
    internal fun targetMinutes(value: Double): Long = NewGoalPlan.minutes(value).coerceAtLeast(0L)

    // ------------------------------------------------------------------
    // Activity, icon, subtitle
    // ------------------------------------------------------------------

    /**
     * The icon a goal shows, the same 2dp-stroke glyph as its card in
     * the New goal carousel: runner, bike, swimmer, rower, dumbbell, or
     * the pulse line for a goal that counts any activity.
     */
    @DrawableRes
    fun iconRes(activity: ActivityType?): Int =
        activity?.let(ActivityLabels::cardIconRes) ?: R.drawable.ic_pulse_24

    /** "Running", "Swimming", … or "Any activity" (what the carousel's "Any" card reads aloud). */
    @StringRes
    fun activityNameRes(activity: ActivityType?): Int =
        activity?.let(ActivityLabels::nameRes) ?: R.string.new_goal_activity_any

    /**
     * The Manage sheet subtitle: "Swimming · Distance · started Mon 19 Oct",
     * "Any activity · Time · started …". Each part is glued together
     * with non-breaking spaces and the "·" stays with the part before
     * it, so a narrow screen wraps between parts ("Swimming · Distance ·"
     * / "started Mon 19 Oct"), never leaving a lone "Oct".
     */
    fun manageSubtitle(
        text: TextResolver,
        activity: ActivityType?,
        type: GoalType,
        startDate: LocalDate,
        locale: Locale = Locale.getDefault(),
    ): String = text.string(
        R.string.manage_goal_subtitle,
        unbreakable(text.string(activityNameRes(activity))),
        unbreakable(text.string(NewGoalPlan.metricLabelRes(type))),
        unbreakable(rowDate(startDate, locale)),
    )

    /**
     * A goal title for display, its last two words held together
     * ("Complete 4 workouts in 7 days" wraps as "… in" / "7 days", never
     * leaving "days" alone). Only the screen text changes; the stored
     * title is untouched.
     */
    fun titleForDisplay(title: String): String {
        val trimmed = title.trimEnd()
        val lastSpace = trimmed.lastIndexOf(' ')
        if (lastSpace <= 0) return title
        return trimmed.substring(0, lastSpace) + NBSP + trimmed.substring(lastSpace + 1)
    }

    /** "Fri 2 Oct": short weekday, day, short month. */
    fun rowDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

    private fun unbreakable(part: String): String = part.replace(' ', NBSP)

    /**
     * "2 h 30 min" ([DurationFormatter]) held together, so a narrow row
     * never splits it ("2 h 30" / "min").
     */
    private fun duration(text: TextResolver, minutes: Long): String =
        unbreakable(DurationFormatter.format(text, minutes))
}
