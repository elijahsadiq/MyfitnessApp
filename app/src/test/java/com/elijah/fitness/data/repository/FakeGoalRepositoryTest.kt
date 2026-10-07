package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.model.StrengthVolumeGoal
import com.elijah.fitness.data.remote.ApiResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FakeGoalRepositoryTest {

    private val today = LocalDate.parse("2026-09-14")

    @Test
    fun `create then observe emits the new goal`() = runTest {
        val repo = FakeGoalRepository()
        assertEquals(0, repo.observeGoals().first().size)

        val result = repo.createGoal(
            title = "Run 20 km",
            activityType = ActivityType.RUNNING,
            goalType = GoalType.DISTANCE,
            targetValue = 20.0,
            startDate = today,
            durationDays = 7,
        )
        assertTrue(result is ApiResult.Success)
        val list = repo.observeGoals().first()
        assertEquals(1, list.size)
        assertTrue(list[0] is DistanceGoal)
    }

    @Test
    fun `update rebuilds the same subclass with the edited fields`() = runTest {
        val repo = FakeGoalRepository()
        repo.createGoal(
            title = "Lift 1000 kg",
            activityType = ActivityType.WEIGHTLIFTING,
            goalType = GoalType.VOLUME,
            targetValue = 1000.0,
            startDate = today, durationDays = 30,
        )
        val id = repo.observeGoals().first().first().id

        val result = repo.updateGoal(
            id = id, title = "Lift 1500 kg",
            activityType = ActivityType.WEIGHTLIFTING, goalType = GoalType.VOLUME,
            targetValue = 1500.0, durationDays = 45,
        )
        assertTrue(result is ApiResult.Success)
        val goal = repo.observeGoals().first().first()
        assertTrue(goal is StrengthVolumeGoal)
        assertEquals("Lift 1500 kg", goal.title)
        assertEquals(1500.0, goal.targetValue, 1e-6)
        assertEquals(45, goal.durationDays)
    }

    @Test
    fun `updating a missing id returns 404 Error`() = runTest {
        val repo = FakeGoalRepository()
        val result = repo.updateGoal(
            id = "does-not-exist", title = "x",
            activityType = null, goalType = GoalType.COUNT, targetValue = 5.0, durationDays = 7,
        )
        assertTrue(result is ApiResult.Error)
        assertEquals(404, (result as ApiResult.Error).status)
    }

    @Test
    fun `delete removes the goal and 404s a repeated delete`() = runTest {
        val repo = FakeGoalRepository()
        repo.createGoal(
            title = "walk", activityType = null, goalType = GoalType.COUNT,
            targetValue = 3.0, startDate = today, durationDays = 7,
        )
        val id = repo.observeGoals().first().first().id

        assertTrue(repo.deleteGoal(id) is ApiResult.Success)
        assertEquals(0, repo.observeGoals().first().size)

        val repeat = repo.deleteGoal(id)
        assertTrue(repeat is ApiResult.Error)
        assertEquals(404, (repeat as ApiResult.Error).status)
    }

    @Test
    fun `makeMainGoal keeps at most one main per user`() = runTest {
        val repo = FakeGoalRepository()
        repo.createGoal("A", ActivityType.RUNNING, GoalType.DISTANCE, 10.0, today, 7)
        repo.createGoal("B", ActivityType.CYCLING, GoalType.DISTANCE, 50.0, today, 7)
        // Fake assigns monotonic ids starting at 1.
        val id1 = "1"; val id2 = "2"

        repo.makeMainGoal(id1)
        val afterFirst = repo.observeGoals().first().associateBy({ it.id }, { it.isMain })
        assertEquals(mapOf(id1 to true, id2 to false), afterFirst)

        repo.makeMainGoal(id2)
        val afterSecond = repo.observeGoals().first().associateBy({ it.id }, { it.isMain })
        assertEquals(mapOf(id1 to false, id2 to true), afterSecond)
    }

    @Test
    fun `extendGoal adds days up to the 365 cap`() = runTest {
        val repo = FakeGoalRepository()
        repo.createGoal("A", ActivityType.RUNNING, GoalType.DISTANCE, 10.0, today, 7)
        val id = repo.observeGoals().first().first().id

        repo.extendGoal(id, 14)
        assertEquals(21, repo.observeGoals().first().first().durationDays)

        // 360-day extension would push past the cap; the fake coerces to 365.
        repo.extendGoal(id, 360)
        val finalDuration = repo.observeGoals().first().first().durationDays
        assertEquals(365, finalDuration)
        assertFalse(finalDuration > 365)
    }
}
