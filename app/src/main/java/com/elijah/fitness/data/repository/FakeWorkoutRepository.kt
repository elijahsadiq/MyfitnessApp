package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * In-memory implementation used by phase 1. Backed by a [MutableStateFlow]
 * so every observer sees the same list and is notified atomically on
 * changes. The list is kept sorted newest first so UI adapters don't have
 * to re-sort on every emission.
 *
 * Data is lost when the process dies. That's intentional for phase 1 —
 * Room persistence arrives in phase 4.
 */
class FakeWorkoutRepository(
    clock: Clock = Clock.systemUTC()
) : WorkoutRepository {

    private val _workouts: MutableStateFlow<List<Workout>> =
        MutableStateFlow(seedWorkouts(clock.instant()).sortedByDescending { it.startTime })

    override fun observeWorkouts(): Flow<List<Workout>> = _workouts.asStateFlow()

    override suspend fun addWorkout(workout: Workout) {
        _workouts.update { current ->
            (current + workout).sortedByDescending { it.startTime }
        }
    }

    override suspend fun getWorkout(id: String): Workout? =
        _workouts.value.firstOrNull { it.id == id }

    private companion object {

        /** Nine sample workouts over the last 14 days, all three types. */
        fun seedWorkouts(now: Instant): List<Workout> = listOf(
            RunningWorkout(
                id = "seed-run-1",
                startTime = now.minus(Duration.ofDays(1)).minus(Duration.ofHours(2)),
                duration = Duration.ofMinutes(28),
                distanceMeters = 5_100.0,
                notes = "Easy morning shakeout"
            ),
            CyclingWorkout(
                id = "seed-ride-1",
                startTime = now.minus(Duration.ofDays(2)).minus(Duration.ofHours(5)),
                duration = Duration.ofMinutes(62),
                distanceMeters = 22_400.0,
                notes = "River loop"
            ),
            StrengthWorkout(
                id = "seed-lift-1",
                startTime = now.minus(Duration.ofDays(3)).minus(Duration.ofHours(4)),
                duration = Duration.ofMinutes(52),
                sets = listOf(
                    StrengthSet("Squat", 5, 100.0),
                    StrengthSet("Squat", 5, 105.0),
                    StrengthSet("Squat", 5, 110.0),
                    StrengthSet("Bench Press", 8, 62.5),
                    StrengthSet("Bench Press", 8, 62.5),
                    StrengthSet("Row", 10, 55.0)
                ),
                notes = "Lower + push"
            ),
            RunningWorkout(
                id = "seed-run-2",
                startTime = now.minus(Duration.ofDays(4)).minus(Duration.ofHours(10)),
                duration = Duration.ofMinutes(46),
                distanceMeters = 8_050.0,
                notes = "Tempo, felt strong"
            ),
            CyclingWorkout(
                id = "seed-ride-2",
                startTime = now.minus(Duration.ofDays(6)).minus(Duration.ofHours(3)),
                duration = Duration.ofMinutes(95),
                distanceMeters = 34_800.0,
                notes = "Coffee ride"
            ),
            StrengthWorkout(
                id = "seed-lift-2",
                startTime = now.minus(Duration.ofDays(7)).minus(Duration.ofHours(6)),
                duration = Duration.ofMinutes(48),
                sets = listOf(
                    StrengthSet("Deadlift", 5, 120.0),
                    StrengthSet("Deadlift", 3, 130.0),
                    StrengthSet("Overhead Press", 6, 42.5),
                    StrengthSet("Pull Up", 8, 0.0),
                    StrengthSet("Pull Up", 8, 0.0)
                ),
                notes = "Pull + press"
            ),
            RunningWorkout(
                id = "seed-run-3",
                startTime = now.minus(Duration.ofDays(9)).minus(Duration.ofHours(2)),
                duration = Duration.ofMinutes(64),
                distanceMeters = 10_500.0,
                notes = "Long slow distance"
            ),
            CyclingWorkout(
                id = "seed-ride-3",
                startTime = now.minus(Duration.ofDays(11)).minus(Duration.ofHours(4)),
                duration = Duration.ofMinutes(40),
                distanceMeters = 13_600.0,
                notes = "Commute"
            ),
            StrengthWorkout(
                id = "seed-lift-3",
                startTime = now.minus(Duration.ofDays(13)).minus(Duration.ofHours(1)),
                duration = Duration.ofMinutes(35),
                sets = listOf(
                    StrengthSet("Bench Press", 5, 65.0),
                    StrengthSet("Bench Press", 5, 65.0),
                    StrengthSet("Bench Press", 5, 65.0)
                ),
                notes = "Bench focus"
            )
        )
    }
}
