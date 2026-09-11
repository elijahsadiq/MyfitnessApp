package com.elijah.fitness.ui.dashboard

import com.elijah.fitness.data.model.ActivityType

/**
 * Aggregated numbers for the current calendar week. Raw values only — the
 * fragment does the formatting so this stays trivially testable.
 */
data class WeeklySummary(
    val workoutCount: Int,
    val activeMinutes: Long,
    val totalDistanceMeters: Double,
    val estimatedCalories: Double,
    val perActivity: Map<ActivityType, ActivityBreakdown>
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
            perActivity = emptyMap()
        )
    }
}
