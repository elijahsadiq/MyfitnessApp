package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.GoalPeriod
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.model.StrengthVolumeGoal
import com.elijah.fitness.data.remote.ApiResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FakeGoalRepositoryTest {

    @Test
    fun `create then observe emits the new goal`() = runTest {
        val repo = FakeGoalRepository()
        assertEquals(0, repo.observeGoals().first().size)

        val result = repo.createGoal(
            title = "Run 20 km",
            activityType = ActivityType.RUNNING,
            goalType = GoalType.DISTANCE,
            period = GoalPeriod.THIS_WEEK,
            targetValue = 20.0,
            periodStart = LocalDate.parse("2026-09-14"),
            periodEnd = LocalDate.parse("2026-09-21"),
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
            period = GoalPeriod.THIS_MONTH,
            targetValue = 1000.0,
            periodStart = LocalDate.parse("2026-09-01"),
            periodEnd = LocalDate.parse("2026-10-01"),
        )
        val id = repo.observeGoals().first().first().id

        val result = repo.updateGoal(
            id = id, title = "Lift 1500 kg",
            activityType = ActivityType.WEIGHTLIFTING, targetValue = 1500.0,
        )
        assertTrue(result is ApiResult.Success)
        val goal = repo.observeGoals().first().first()
        assertTrue(goal is StrengthVolumeGoal)
        assertEquals("Lift 1500 kg", goal.title)
        assertEquals(1500.0, goal.targetValue, 1e-6)
    }

    @Test
    fun `updating a missing id returns 404 Error`() = runTest {
        val repo = FakeGoalRepository()
        val result = repo.updateGoal(
            id = "does-not-exist", title = "x",
            activityType = null, targetValue = 5.0,
        )
        assertTrue(result is ApiResult.Error)
        assertEquals(404, (result as ApiResult.Error).status)
    }

    @Test
    fun `delete removes the goal and 404s a repeated delete`() = runTest {
        val repo = FakeGoalRepository()
        repo.createGoal(
            title = "walk", activityType = null, goalType = GoalType.COUNT,
            period = GoalPeriod.THIS_WEEK, targetValue = 3.0,
            periodStart = LocalDate.parse("2026-09-14"),
            periodEnd = LocalDate.parse("2026-09-21"),
        )
        val id = repo.observeGoals().first().first().id

        assertTrue(repo.deleteGoal(id) is ApiResult.Success)
        assertEquals(0, repo.observeGoals().first().size)

        val repeat = repo.deleteGoal(id)
        assertTrue(repeat is ApiResult.Error)
        assertEquals(404, (repeat as ApiResult.Error).status)
    }
}
