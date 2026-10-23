package com.elijah.fitness.util

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.SwimmingWorkout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

class CalorieEstimatorTest {

    private val kg = 72.0

    // ---- goal estimates: the brief's worked examples ----

    @Test
    fun `swim time goal of 150 min at 72 kg is 1260 kcal`() {
        // 7.0 × 72 × 2.5 h = 1260
        assertEquals(1260, CalorieEstimator.goalEstimateKcal(ActivityType.SWIMMING, GoalType.DURATION, 150.0, kg))
    }

    @Test
    fun `run 50 km at 72 kg is 3530 kcal`() {
        // 9.8 × 72 × (50 / 10) h = 3528 → nearest 10
        assertEquals(3530, CalorieEstimator.goalEstimateKcal(ActivityType.RUNNING, GoalType.DISTANCE, 50.0, kg))
    }

    @Test
    fun `ride 100 km at 72 kg is 2700 kcal`() {
        // 7.5 × 72 × (100 / 20) h = 2700
        assertEquals(2700, CalorieEstimator.goalEstimateKcal(ActivityType.CYCLING, GoalType.DISTANCE, 100.0, kg))
    }

    @Test
    fun `12 run sessions at 72 kg is 4230 kcal`() {
        // 9.8 × 72 × (12 × 0.5) h = 4233.6 → nearest 10
        assertEquals(4230, CalorieEstimator.goalEstimateKcal(ActivityType.RUNNING, GoalType.COUNT, 12.0, kg))
    }

    @Test
    fun `20000 kg lifted at 72 kg is 1440 kcal`() {
        // 5.0 × 72 × (20000 / 5000) h = 1440
        assertEquals(1440, CalorieEstimator.goalEstimateKcal(ActivityType.WEIGHTLIFTING, GoalType.VOLUME, 20_000.0, kg))
    }

    @Test
    fun `row and any-activity rows of the table`() {
        // Row 30 km: 7.0 × 72 × (30 / 8) h = 1890
        assertEquals(1890, CalorieEstimator.goalEstimateKcal(ActivityType.ROWING, GoalType.DISTANCE, 30.0, kg))
        // Any, 12 sessions: 6.0 × 72 × (12 × 0.75) h = 3888 → 3890
        assertEquals(3890, CalorieEstimator.goalEstimateKcal(null, GoalType.COUNT, 12.0, kg))
        // Swim 10 km: 7.0 × 72 × (10 / 2.5) h = 2016 → 2020
        assertEquals(2020, CalorieEstimator.goalEstimateKcal(ActivityType.SWIMMING, GoalType.DISTANCE, 10.0, kg))
    }

    @Test
    fun `goal estimate is always a multiple of 10`() {
        listOf(1.0, 7.0, 13.0, 55.0, 333.0).forEach { target ->
            val kcal = CalorieEstimator.goalEstimateKcal(ActivityType.RUNNING, GoalType.DURATION, target, 71.3)
            assertEquals(0, kcal % 10)
        }
    }

    @Test
    fun `impossible or empty inputs give 0, never a crash`() {
        // Distance has no typical speed for "any" or a lift.
        assertEquals(0, CalorieEstimator.goalEstimateKcal(null, GoalType.DISTANCE, 10.0, kg))
        assertEquals(0, CalorieEstimator.goalEstimateKcal(ActivityType.WEIGHTLIFTING, GoalType.DISTANCE, 10.0, kg))
        assertEquals(0, CalorieEstimator.goalEstimateKcal(ActivityType.RUNNING, GoalType.DISTANCE, 0.0, kg))
        assertEquals(0, CalorieEstimator.goalEstimateKcal(ActivityType.RUNNING, GoalType.DISTANCE, -5.0, kg))
        assertEquals(0, CalorieEstimator.goalEstimateKcal(ActivityType.RUNNING, GoalType.DISTANCE, 10.0, 0.0))
        assertEquals(0, CalorieEstimator.goalEstimateKcal(ActivityType.RUNNING, GoalType.DISTANCE, Double.NaN, kg))
        assertEquals(0, CalorieEstimator.goalEstimateKcal(ActivityType.RUNNING, GoalType.DISTANCE, 10.0, Double.NaN))
    }

    // ---- logged workouts ----

    @Test
    fun `workout kcal is caloriesBurned rounded`() {
        val swim = SwimmingWorkout(
            id = "s", startTime = Instant.EPOCH, duration = Duration.ofSeconds(1_935),
            distanceMeters = 1_500.0,
        )
        // 7.0 × 72 × (1935 / 3600) h = 270.9
        assertEquals(swim.caloriesBurned(kg).roundToInt(), CalorieEstimator.workoutKcal(swim, kg))
        assertEquals(271, CalorieEstimator.workoutKcal(swim, kg))
    }

    @Test
    fun `logged lifts keep the 3_5 MET, not the goal table's 5_0`() {
        val lift = StrengthWorkout(
            id = "l", startTime = Instant.EPOCH, duration = Duration.ofHours(1),
            sets = listOf(StrengthSet("Squat", 5, 100.0)),
        )
        assertEquals(252, CalorieEstimator.workoutKcal(lift, kg)) // 3.5 × 72 × 1
    }

    @Test
    fun `workout kcal is 0 for a zero-length workout and capped at the server limit`() {
        val empty = RunningWorkout("r", Instant.EPOCH, Duration.ZERO, distanceMeters = 0.0)
        assertEquals(0, CalorieEstimator.workoutKcal(empty, kg))
        val huge = RunningWorkout("r", Instant.EPOCH, Duration.ofHours(24), distanceMeters = 300_000.0)
        assertEquals(CalorieEstimator.MAX_WORKOUT_KCAL, CalorieEstimator.workoutKcal(huge, 500.0))
        assertEquals(0, CalorieEstimator.workoutKcal(huge, Double.NaN))
    }

    @Test
    fun `stored kcal uses the real profile weight`() {
        val lift = StrengthWorkout(
            id = "l", startTime = Instant.EPOCH, duration = Duration.ofHours(1),
            sets = listOf(StrengthSet("Squat", 5, 100.0)),
        )
        assertEquals(315, CalorieEstimator.storedWorkoutKcal(lift, 90.0)) // 3.5 × 90 × 1
    }

    @Test
    fun `nothing is stored while the profile weight is unknown or broken`() {
        val run = RunningWorkout("r", Instant.EPOCH, Duration.ofMinutes(30), distanceMeters = 5_000.0)
        assertEquals(null, CalorieEstimator.storedWorkoutKcal(run, null))
        assertEquals(null, CalorieEstimator.storedWorkoutKcal(run, 0.0))
        assertEquals(null, CalorieEstimator.storedWorkoutKcal(run, Double.NaN))
    }
}
