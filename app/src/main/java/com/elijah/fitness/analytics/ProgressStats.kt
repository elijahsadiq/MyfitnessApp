package com.elijah.fitness.analytics

/**
 * One bar for the activity chart: a label the fragment renders under the
 * bar (`Mon`, `W1`, `Jan`...) and the raw numeric value. The chart view
 * normalises values against the max so the caller doesn't pre-scale.
 */
data class BarPoint(val label: String, val value: Double)

/**
 * Per-activity statistics for the selected period. Null fields mean "no
 * data for this activity in this window" — the UI hides those rows
 * rather than printing a dash.
 */
data class ProgressStats(
    val workoutCount: Int,
    val totalDistanceMeters: Double?,
    val totalDurationMinutes: Long?,
    val averagePaceSecondsPerKm: Long?,
    val bestPaceSecondsPerKm: Long?,
    val longestDistanceMeters: Double?,
    val averageSpeedKmh: Double?,
    val maxSpeedKmh: Double?,
    val totalSets: Int?,
    val totalVolumeKg: Double?,
    val mostFrequentExercise: String?,
    /**
     * Swims and rows (phase 8d): average and best pace per split, in
     * seconds — per 100 m for swims, per 500 m for rows.
     */
    val averageSplitSeconds: Long? = null,
    val bestSplitSeconds: Long? = null,
) {
    companion object {
        val EMPTY = ProgressStats(
            workoutCount = 0,
            totalDistanceMeters = null,
            totalDurationMinutes = null,
            averagePaceSecondsPerKm = null,
            bestPaceSecondsPerKm = null,
            longestDistanceMeters = null,
            averageSpeedKmh = null,
            maxSpeedKmh = null,
            totalSets = null,
            totalVolumeKg = null,
            mostFrequentExercise = null,
        )
    }
}

/**
 * Lifetime personal records, computed from every workout the user has
 * ever logged (not just the selected period). Any field can be `null` when
 * the relevant activity type is missing from the user's history.
 */
data class PersonalRecords(
    val fastest5kSecondsPerKm: Long?,
    val longestRunMeters: Double?,
    val longestRideMeters: Double?,
    val bestOneRepMaxes: Map<String, Double>, // exercise → best 1RM (kg)
    val longestSwimMeters: Double? = null,
    val longestRowMeters: Double? = null,
) {
    companion object {
        val EMPTY = PersonalRecords(
            fastest5kSecondsPerKm = null,
            longestRunMeters = null,
            longestRideMeters = null,
            bestOneRepMaxes = emptyMap(),
        )
    }
}
