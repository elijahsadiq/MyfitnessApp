package com.elijah.fitness.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WorkoutModelTest {

    private val id = "test-id"
    private val t0: Instant = Instant.parse("2026-01-01T09:00:00Z")

    @Test
    fun `running pace seconds per km`() {
        // 10 km in 60 minutes => 6:00 min/km => 360 s/km
        val run = RunningWorkout(id, t0, Duration.ofMinutes(60), distanceMeters = 10_000.0)
        assertEquals(360L, run.paceSecondsPerKm())
    }

    @Test
    fun `pace is zero for zero distance`() {
        val run = RunningWorkout(id, t0, Duration.ofMinutes(30), distanceMeters = 0.0)
        assertEquals(0L, run.paceSecondsPerKm())
    }

    @Test
    fun `distance workout average speed`() {
        // 20 km in 60 minutes => 20 km/h
        val ride = CyclingWorkout(id, t0, Duration.ofMinutes(60), distanceMeters = 20_000.0)
        assertEquals(20.0, ride.averageSpeedKmh(), 1e-6)
    }

    @Test
    fun `strength workout total volume`() {
        val sets = listOf(
            StrengthSet("Bench", 10, 60.0),
            StrengthSet("Bench", 8, 65.0),
            StrengthSet("Squat", 5, 100.0)
        )
        // 10*60 + 8*65 + 5*100 = 600 + 520 + 500 = 1620
        val w = StrengthWorkout(id, t0, Duration.ofMinutes(45), sets = sets)
        assertEquals(1620.0, w.totalVolumeKg(), 1e-6)
    }

    @Test
    fun `epley one rep max returns best of the exercise`() {
        val sets = listOf(
            StrengthSet("Bench", 10, 60.0), // 60 * (1 + 10/30) = 80.0
            StrengthSet("Bench", 8, 65.0),  // 65 * (1 + 8/30)  ≈ 82.3333
            StrengthSet("Squat", 5, 100.0)
        )
        val w = StrengthWorkout(id, t0, Duration.ofMinutes(45), sets = sets)
        val estimate = w.estimatedOneRepMaxKg("Bench")
        assertNotNull(estimate)
        assertEquals(82.3333, estimate!!, 0.001)
    }

    @Test
    fun `epley one rep max is case insensitive`() {
        val w = StrengthWorkout(
            id, t0, Duration.ofMinutes(30),
            sets = listOf(StrengthSet("Deadlift", 3, 140.0))
        )
        assertNotNull(w.estimatedOneRepMaxKg("deadlift"))
    }

    @Test
    fun `epley one rep max returns null when exercise absent`() {
        val w = StrengthWorkout(
            id, t0, Duration.ofMinutes(30),
            sets = listOf(StrengthSet("Squat", 5, 100.0))
        )
        assertNull(w.estimatedOneRepMaxKg("Bench"))
    }

    @Test
    fun `calories burned uses MET x weight x hours for cycling`() {
        // 20 km/h cycling for 1 hour, 70 kg: MET band = 8.0 => 8 * 70 * 1 = 560
        val ride = CyclingWorkout(id, t0, Duration.ofMinutes(60), distanceMeters = 20_000.0)
        assertEquals(560.0, ride.caloriesBurned(70.0), 1e-6)
    }

    @Test
    fun `polymorphism over mixed workouts returns type-specific calories`() {
        val workouts: List<Workout> = listOf(
            RunningWorkout("a", t0, Duration.ofMinutes(30), distanceMeters = 5_000.0),
            CyclingWorkout("b", t0, Duration.ofMinutes(60), distanceMeters = 20_000.0),
            StrengthWorkout(
                "c", t0, Duration.ofMinutes(45),
                sets = listOf(StrengthSet("Bench", 10, 60.0))
            )
        )
        val calories = workouts.map { it.caloriesBurned(70.0) }
        // running 5 km / 0.5 h = 10 km/h  => 9.8 MET  => 9.8 * 70 * 0.5 = 343
        assertEquals(343.0, calories[0], 1e-6)
        // cycling 20 km/h                => 8.0 MET  => 8.0 * 70 * 1.0 = 560
        assertEquals(560.0, calories[1], 1e-6)
        // weightlifting                  => 3.5 MET  => 3.5 * 70 * 0.75 = 183.75
        assertEquals(183.75, calories[2], 1e-6)
    }

    @Test
    fun `content equality holds for identical workouts`() {
        val a = RunningWorkout(id, t0, Duration.ofMinutes(30), 5_000.0, notes = "morning")
        val b = RunningWorkout(id, t0, Duration.ofMinutes(30), 5_000.0, notes = "morning")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `content equality fails when any field differs`() {
        val a = RunningWorkout(id, t0, Duration.ofMinutes(30), 5_000.0)
        val bDuration = RunningWorkout(id, t0, Duration.ofMinutes(31), 5_000.0)
        val bDistance = RunningWorkout(id, t0, Duration.ofMinutes(30), 5_050.0)
        assertNotEquals(a, bDuration)
        assertNotEquals(a, bDistance)
    }

    @Test
    fun `running and cycling with same base fields are not equal`() {
        val run: Workout = RunningWorkout(id, t0, Duration.ofMinutes(30), 5_000.0)
        val ride: Workout = CyclingWorkout(id, t0, Duration.ofMinutes(30), 5_000.0)
        assertNotEquals(run, ride)
    }
}
