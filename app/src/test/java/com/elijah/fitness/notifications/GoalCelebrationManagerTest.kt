package com.elijah.fitness.notifications

import com.elijah.fitness.data.local.CelebrationSeedStore
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.GoalProgressCalculator
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.model.WorkoutDetail
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.FakeGoalRepository
import com.elijah.fitness.data.repository.WorkoutRepository
import com.elijah.fitness.tracking.TrackPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Phase 8d, Step 7: who celebrates a reached goal, and only once. */
class GoalCelebrationManagerTest {

    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-07T12:00:00Z") // Wednesday
    private val clock = Clock.fixed(now, zone)
    private val goals = FakeGoalRepository(clock)
    private val workouts = MutableStateFlow<List<Workout>>(emptyList())
    private val alertsOn = MutableStateFlow(true)
    private val seedStore = FakeSeedStore(seeded = true)
    private val posted = mutableListOf<String>()

    private fun TestScope.manager() = GoalCelebrationManager(
        calculator = GoalProgressCalculator(goals, ListWorkoutRepo(workouts), clock, zone),
        goalRepository = goals,
        workoutRepository = ListWorkoutRepo(workouts),
        seedStore = seedStore,
        alertsEnabled = alertsOn,
        scope = backgroundScope,
        weightKg = { 70.0 },
        titleOf = { it.title },
        postNotification = { id, _ -> posted += id },
        clock = clock,
        zone = zone,
    )

    @Test
    fun `on screen, reaching a goal queues one pop-up and saves the flag`() = runTest {
        goals.seed(listOf(runGoal(id = "1")))
        val m = manager()
        m.onUiStarted()
        m.start()
        runCurrent()
        assertTrue(m.pending.value.isEmpty())

        workouts.value = listOf(run(km = 10.0))
        runCurrent()

        assertEquals(listOf("1"), m.pending.value.map { it.goalId })
        assertEquals(1, m.pending.value.single().daysTaken) // started and reached on 6 Oct
        assertTrue(posted.isEmpty())
        assertTrue(goals.observeGoals().first().single().completedNotified)
    }

    @Test
    fun `in the background it posts the notification instead`() = runTest {
        goals.seed(listOf(runGoal(id = "1")))
        val m = manager()
        m.start()
        workouts.value = listOf(run(km = 10.0))
        runCurrent()

        assertEquals(listOf("1"), posted)
        assertTrue(m.pending.value.isEmpty())
        assertTrue(goals.observeGoals().first().single().completedNotified)
    }

    @Test
    fun `in the background with goal alerts off nothing shows but the flag is saved`() = runTest {
        alertsOn.value = false
        goals.seed(listOf(runGoal(id = "1")))
        val m = manager()
        m.start()
        workouts.value = listOf(run(km = 10.0))
        runCurrent()

        assertTrue(posted.isEmpty())
        assertTrue(m.pending.value.isEmpty())
        assertTrue(goals.observeGoals().first().single().completedNotified)
    }

    @Test
    fun `a goal is celebrated only once`() = runTest {
        goals.seed(listOf(runGoal(id = "1")))
        val m = manager()
        m.onUiStarted()
        m.start()
        workouts.value = listOf(run(km = 10.0))
        runCurrent()
        m.consume("1")

        workouts.value = listOf(run(km = 10.0), run(km = 3.0, at = "2026-10-07T08:00:00Z"))
        runCurrent()

        assertTrue(m.pending.value.isEmpty())
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `first run quietly marks goals that are already complete`() = runTest {
        seedStore.seeded = false
        goals.seed(listOf(runGoal(id = "old")))
        workouts.value = listOf(run(km = 12.0))
        val m = manager()
        m.onUiStarted()
        m.start()
        runCurrent()

        assertTrue(m.pending.value.isEmpty())
        assertTrue(posted.isEmpty())
        assertTrue(seedStore.seeded)
        assertTrue(goals.observeGoals().first().single().completedNotified)
    }

    @Test
    fun `an old goal that ended long ago is not celebrated`() = runTest {
        goals.seed(listOf(runGoal(id = "1", startDate = "2026-09-01")))
        workouts.value = listOf(run(km = 10.0, at = "2026-09-02T07:00:00Z"))
        val m = manager()
        m.onUiStarted()
        m.start()
        runCurrent()

        assertTrue(m.pending.value.isEmpty())
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `stop forgets the queue`() = runTest {
        goals.seed(listOf(runGoal(id = "1")))
        val m = manager()
        m.onUiStarted()
        m.start()
        workouts.value = listOf(run(km = 10.0))
        runCurrent()
        assertFalse(m.pending.value.isEmpty())

        m.stop()

        assertTrue(m.pending.value.isEmpty())
    }

    private fun runGoal(id: String, startDate: String = "2026-10-06") = DistanceGoal(
        id = id,
        title = "Run 10 km",
        activityType = ActivityType.RUNNING,
        targetValue = 10.0,
        startDate = LocalDate.parse(startDate),
        durationDays = 7,
        createdAt = Instant.parse("${startDate}T00:00:00Z"),
    )

    private fun run(km: Double, at: String = "2026-10-06T07:00:00Z") = RunningWorkout(
        id = "$at-$km",
        startTime = Instant.parse(at),
        duration = Duration.ofMinutes(60),
        distanceMeters = km * 1_000.0,
    )

    private class FakeSeedStore(var seeded: Boolean) : CelebrationSeedStore {
        override suspend fun isSeeded(): Boolean = seeded
        override suspend fun markSeeded() {
            seeded = true
        }
    }

    private class ListWorkoutRepo(private val flow: MutableStateFlow<List<Workout>>) : WorkoutRepository {
        override fun observeWorkouts(): Flow<List<Workout>> = flow
        override suspend fun refresh(): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun addWorkout(workout: Workout, routePoints: List<TrackPoint>) = ApiResult.Success(Unit)
        override suspend fun getWorkout(id: String): Workout? = null
        override suspend fun getWorkoutDetail(clientUuid: String): ApiResult<WorkoutDetail> =
            ApiResult.Error("unused", 404)
        override suspend fun deleteWorkout(clientUuid: String) = ApiResult.Success(Unit)
        override suspend fun updateNotes(clientUuid: String, notes: String?): ApiResult<Workout> =
            ApiResult.Error("unused", 404)
    }
}
