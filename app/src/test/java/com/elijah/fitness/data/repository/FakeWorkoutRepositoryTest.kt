package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.RunningWorkout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FakeWorkoutRepositoryTest {

    @Test
    fun `seed data has 8 to 10 workouts`() = runTest {
        val repo = FakeWorkoutRepository()
        val count = repo.observeWorkouts().first().size
        assertTrue("expected 8..10 seeds, got $count", count in 8..10)
    }

    @Test
    fun `seed data covers all three activity types`() = runTest {
        val repo = FakeWorkoutRepository()
        val types = repo.observeWorkouts().first().map { it.type }.toSet()
        assertEquals(
            setOf(ActivityType.RUNNING, ActivityType.CYCLING, ActivityType.WEIGHTLIFTING),
            types
        )
    }

    @Test
    fun `seed data is sorted newest first`() = runTest {
        val repo = FakeWorkoutRepository()
        val list = repo.observeWorkouts().first()
        val monotone = list.zipWithNext { a, b -> !a.startTime.isBefore(b.startTime) }
        assertTrue("expected non-increasing startTime, got $list", monotone.all { it })
    }

    @Test
    fun `addWorkout puts the newest workout at the front`() = runTest {
        val repo = FakeWorkoutRepository()
        val newest = RunningWorkout(
            id = "just-added",
            startTime = Instant.now().plus(Duration.ofDays(1)),
            duration = Duration.ofMinutes(30),
            distanceMeters = 5_000.0
        )
        repo.addWorkout(newest)
        val list = repo.observeWorkouts().first()
        assertEquals("just-added", list.first().id)
    }

    @Test
    fun `getWorkout returns matching workout`() = runTest {
        val repo = FakeWorkoutRepository()
        val added = RunningWorkout(
            id = "lookup-me",
            startTime = Instant.now(),
            duration = Duration.ofMinutes(20),
            distanceMeters = 4_000.0
        )
        repo.addWorkout(added)
        assertEquals(added, repo.getWorkout("lookup-me"))
    }

    @Test
    fun `getWorkout returns null when id is unknown`() = runTest {
        val repo = FakeWorkoutRepository()
        assertNull(repo.getWorkout("does-not-exist"))
    }
}
