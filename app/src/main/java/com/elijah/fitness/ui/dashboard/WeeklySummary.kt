package com.elijah.fitness.ui.dashboard

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.util.BmiCategory

/**
 * Aggregated numbers for one calendar week — the current one, or the
 * week of the day picked on Home's calendar. Raw values only — the
 * fragment does the formatting so this stays trivially testable.
 *
 * [bmi] + [bmiCategory] are user-scoped, not workout-scoped, but live
 * here because the Home hero card renders them alongside the weekly
 * stats and the fragment already collects `summary` in one place.
 * Both are `null` when the user hasn't recorded height + weight yet —
 * the UI shows a "missing" state in that case.
 */
data class WeeklySummary(
    val workoutCount: Int,
    val activeMinutes: Long,
    val totalDistanceMeters: Double,
    val estimatedCalories: Double,
    val bmi: Double?,
    val bmiCategory: BmiCategory?,
    val perActivity: Map<ActivityType, ActivityBreakdown>,
    /**
     * Consecutive calendar days ending today on which the user logged at
     * least one workout. 0 if today has no workout *and* yesterday has no
     * workout; 1 if today or yesterday has one; longer runs grow from
     * there. The pill hides below 2 (one-day "streak" isn't exciting).
     */
    val streakDays: Int,
) {

    /** Per-activity slice of the week. `mainStat` interpretation is type-dependent. */
    data class ActivityBreakdown(
        val count: Int,
        val distanceMeters: Double,
        val totalVolumeKg: Double,
        val activeMinutes: Long
    ) {
        companion object {
            val EMPTY = ActivityBreakdown(
                count = 0,
                distanceMeters = 0.0,
                totalVolumeKg = 0.0,
                activeMinutes = 0L
            )
        }
    }

    companion object {
        val EMPTY = WeeklySummary(
            workoutCount = 0,
            activeMinutes = 0L,
            totalDistanceMeters = 0.0,
            estimatedCalories = 0.0,
            bmi = null,
            bmiCategory = null,
            perActivity = emptyMap(),
            streakDays = 0,
        )
    }
}
