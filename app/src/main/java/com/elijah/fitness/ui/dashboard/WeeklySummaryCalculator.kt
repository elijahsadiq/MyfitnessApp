package com.elijah.fitness.ui.dashboard

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.util.BodyMetrics
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Pure-Kotlin aggregation of a workout list into a [WeeklySummary]. Kept
 * out of Android so it can be unit-tested with a fixed [Instant] and
 * [ZoneId].
 */
object WeeklySummaryCalculator {

    /**
     * Aggregate workouts falling inside the current calendar week
     * (Monday 00:00 in [zone] up to [now]) plus the user's BMI derived
     * from [bodyWeightKg] and [heightCm]. BMI is `null` when height is
     * missing — the UI shows a "add height" state in that case.
     */
    fun compute(
        workouts: List<Workout>,
        now: Instant,
        zone: ZoneId,
        bodyWeightKg: Double,
        heightCm: Double?,
    ): WeeklySummary {
        val today = LocalDate.ofInstant(now, zone)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val startOfWeek = monday.atStartOfDay(zone).toInstant()

        val inWeek = workouts.filter { workout ->
            !workout.startTime.isBefore(startOfWeek) &&
                !workout.startTime.isAfter(now)
        }

        val activeMinutes = inWeek.sumOf { it.duration.toMinutes() }
        val totalDistanceMeters = inWeek
            .filterIsInstance<DistanceWorkout>()
            .sumOf { it.distanceMeters }
        val estimatedCalories = inWeek.sumOf { it.caloriesBurned(bodyWeightKg) }

        val bmi = BodyMetrics.bmi(weightKg = bodyWeightKg, heightCm = heightCm)
        val bmiCategory = BodyMetrics.bmiCategory(bmi)

        val perActivity = ActivityType.entries.associateWith { type ->
            val ofType = inWeek.filter { it.type == type }
            WeeklySummary.ActivityBreakdown(
                count = ofType.size,
                distanceMeters = ofType.filterIsInstance<DistanceWorkout>()
                    .sumOf { it.distanceMeters },
                totalVolumeKg = ofType.filterIsInstance<StrengthWorkout>()
                    .sumOf { it.totalVolumeKg() },
                activeMinutes = ofType.sumOf { it.duration.toMinutes() }
            )
        }

        return WeeklySummary(
            workoutCount = inWeek.size,
            activeMinutes = activeMinutes,
            totalDistanceMeters = totalDistanceMeters,
            estimatedCalories = estimatedCalories,
            bmi = bmi,
            bmiCategory = bmiCategory,
            perActivity = perActivity
        )
    }
}
