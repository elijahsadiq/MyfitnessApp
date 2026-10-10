package com.elijah.fitness.notifications

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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class GoalAchievementNotifierTest {

    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-07T12:00:00Z") // Wednesday
    private val goals = FakeGoalRepository(Clock.fixed(now, zone))
    private val workouts = MutableStateFlow<List<Workout>>(emptyList())
    private val alertsOn = MutableStateFlow(true)
    private val posted = mutableListOf<String>()

    private fun TestScope.notifier() = GoalAchievementNotifier(
        calculator = GoalProgressCalculator(goals, ListWorkoutRepo(workouts), Clock.fixed(now, zone), zone),
        goalRepository = goals,
        alertsEnabled = alertsOn,
        scope = backgroundScope,
        post = { id, _ -> posted += id },
        titleOf = { it.goal.title },
    )

    @Test
    fun `reaching a goal posts one notification and saves the flag`() = runTest {
        goals.seed(listOf(runGoal(id = "1", startDate = "2026-10-05")))
        notifier().start()
        runCurrent()
        assertTrue(posted.isEmpty())

        workouts.value = listOf(run(km = 10.0))
        runCurrent()

        assertEquals(listOf("1"), posted)
        assertTrue(goals.observeGoals().first().single().completedNotified)
    }

    @Test
    fun `a goal is announced only once`() = runTest {
        goals.seed(listOf(runGoal(id = "1", startDate = "2026-10-05")))
        notifier().start()
        workouts.value = listOf(run(km = 10.0))
        runCurrent()

        workouts.value = listOf(run(km = 10.0), run(km = 3.0, at = "2026-10-07T08:00:00Z"))
        runCurrent()

        assertEquals(listOf("1"), posted)
    }

    @Test
    fun `nothing is posted with goal alerts switched off`() = runTest {
        alertsOn.value = false
        goals.seed(listOf(runGoal(id = "1", startDate = "2026-10-05")))
        notifier().start()

        workouts.value = listOf(run(km = 10.0))
        runCurrent()

        assertTrue(posted.isEmpty())
        // Left for the Goals tab's in-app celebration.
        assertTrue(!goals.observeGoals().first().single().completedNotified)
    }

    @Test
    fun `an old goal that ended long ago is not announced`() = runTest {
        goals.seed(listOf(runGoal(id = "1", startDate = "2026-09-01")))
        workouts.value = listOf(run(km = 10.0, at = "2026-09-02T07:00:00Z"))
        notifier().start()
        runCurrent()

        assertTrue(posted.isEmpty())
    }

    private fun runGoal(id: String, startDate: String) = DistanceGoal(
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
