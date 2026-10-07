package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.remote.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class GoalDeleteCoordinatorTest {

    private val dispatcher = StandardTestDispatcher()
    private val goal: Goal = DistanceGoal(
        id = "g1", title = "x", activityType = ActivityType.RUNNING,
        targetValue = 10.0, startDate = LocalDate.parse("2026-09-14"),
        durationDays = 7, createdAt = Instant.parse("2026-09-14T00:00:00Z"),
    )

    @Test
    fun `scheduleDelete hides the id immediately`() = runTest(dispatcher) {
        val repo = CountingRepo()
        val coordinator = GoalDeleteCoordinator(repo, CoroutineScope(SupervisorJob() + dispatcher), delayMs = 1_000L)

        coordinator.scheduleDelete(goal)
        assertTrue(coordinator.isPending("g1"))
        assertEquals(setOf("g1"), coordinator.hiddenIds.value)
        assertEquals(0, repo.deleteCalls) // NOT fired yet
    }

    @Test
    fun `delay elapses then DELETE fires once and the id is unhidden`() = runTest(dispatcher) {
        val repo = CountingRepo()
        val coordinator = GoalDeleteCoordinator(repo, CoroutineScope(SupervisorJob() + dispatcher), delayMs = 500L)
        coordinator.scheduleDelete(goal)

        dispatcher.scheduler.advanceTimeBy(600L)
        assertEquals(1, repo.deleteCalls)
        assertFalse(coordinator.isPending("g1"))
        assertEquals(emptySet<String>(), coordinator.hiddenIds.value)
    }

    @Test
    fun `undo inside the window cancels the DELETE`() = runTest(dispatcher) {
        val repo = CountingRepo()
        val coordinator = GoalDeleteCoordinator(repo, CoroutineScope(SupervisorJob() + dispatcher), delayMs = 500L)
        coordinator.scheduleDelete(goal)

        dispatcher.scheduler.advanceTimeBy(100L) // still inside the 500 ms window
        coordinator.undo("g1")
        dispatcher.scheduler.advanceTimeBy(1_000L) // let any remaining timers run

        assertEquals(0, repo.deleteCalls)
        assertFalse(coordinator.isPending("g1"))
        assertEquals(emptySet<String>(), coordinator.hiddenIds.value)
    }

    @Test
    fun `scheduling the same id twice does not restart the timer`() = runTest(dispatcher) {
        val repo = CountingRepo()
        val coordinator = GoalDeleteCoordinator(repo, CoroutineScope(SupervisorJob() + dispatcher), delayMs = 500L)

        coordinator.scheduleDelete(goal)
        dispatcher.scheduler.advanceTimeBy(300L)
        coordinator.scheduleDelete(goal) // second call — must be a no-op
        dispatcher.scheduler.advanceTimeBy(300L) // 600 ms total since first schedule

        assertEquals(1, repo.deleteCalls)
    }

    private class CountingRepo : GoalRepository {
        private val flow = MutableStateFlow<List<Goal>>(emptyList())
        var deleteCalls = 0
            private set

        override fun observeGoals(): Flow<List<Goal>> = flow.asStateFlow()
        override suspend fun refresh(): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun createGoal(
            title: String, activityType: ActivityType?, goalType: GoalType,
            targetValue: Double, startDate: LocalDate, durationDays: Int,
        ): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun updateGoal(
            id: String, title: String, activityType: ActivityType?,
            goalType: GoalType, targetValue: Double, durationDays: Int,
        ): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun deleteGoal(id: String): ApiResult<Unit> {
            deleteCalls++
            return ApiResult.Success(Unit)
        }
        override suspend fun markCompletedNotified(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun makeMainGoal(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun extendGoal(id: String, days: Int): ApiResult<Unit> = ApiResult.Success(Unit)
    }
}
