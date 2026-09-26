package com.elijah.fitness.ui.goals

import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalPeriod
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.GoalRepository
import com.elijah.fitness.ui.auth.FieldError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class NewGoalViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(
        Instant.parse("2026-09-16T09:00:00Z"), ZoneOffset.UTC
    )

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    // ---------- available goal types ----------

    @Test
    fun `weightlifting hides distance chip`() {
        val vm = NewGoalViewModel(FakeGoalRepo(), clock = clock, zone = ZoneOffset.UTC)
        vm.setActivity(ActivityType.WEIGHTLIFTING)
        val types = vm.uiState.value.availableGoalTypes
        assertFalse(GoalType.DISTANCE in types)
        assertTrue(GoalType.VOLUME in types)
    }

    @Test
    fun `running or cycling hide volume chip`() {
        val vm = NewGoalViewModel(FakeGoalRepo(), clock = clock, zone = ZoneOffset.UTC)
        vm.setActivity(ActivityType.RUNNING)
        val running = vm.uiState.value.availableGoalTypes
        assertTrue(GoalType.DISTANCE in running)
        assertFalse(GoalType.VOLUME in running)

        vm.setActivity(ActivityType.CYCLING)
        assertFalse(GoalType.VOLUME in vm.uiState.value.availableGoalTypes)
    }

    @Test
    fun `any activity shows all four chips`() {
        val vm = NewGoalViewModel(FakeGoalRepo(), clock = clock, zone = ZoneOffset.UTC)
        vm.setActivity(null)
        assertEquals(GoalType.entries.toSet(), vm.uiState.value.availableGoalTypes.toSet())
    }

    // ---------- auto title ----------

    @Test
    fun `auto-title is generated from the choices while user hasn't edited`() {
        val vm = NewGoalViewModel(FakeGoalRepo(), clock = clock, zone = ZoneOffset.UTC)
        vm.setActivity(ActivityType.RUNNING)
        vm.setGoalType(GoalType.DISTANCE)
        vm.setPeriod(GoalPeriod.THIS_MONTH)
        vm.setTarget("50")
        assertEquals("Run 50 km this month", vm.uiState.value.title)
    }

    @Test
    fun `auto-title stops updating once the user typed their own`() {
        val vm = NewGoalViewModel(FakeGoalRepo(), clock = clock, zone = ZoneOffset.UTC)
        vm.setActivity(ActivityType.RUNNING)
        vm.setGoalType(GoalType.DISTANCE)
        vm.setTarget("20")
        vm.setTitle("My custom title")
        vm.setTarget("30") // would rewrite auto-title, but must not
        assertEquals("My custom title", vm.uiState.value.title)
    }

    // ---------- validation ----------

    @Test
    fun `submit before choosing activity surfaces the activity error`() = runTest {
        val vm = NewGoalViewModel(FakeGoalRepo(), clock = clock, zone = ZoneOffset.UTC)
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.activityError)
    }

    @Test
    fun `target above the type-specific max is rejected`() {
        val vm = NewGoalViewModel(FakeGoalRepo(), clock = clock, zone = ZoneOffset.UTC)
        vm.setActivity(ActivityType.RUNNING)
        vm.setGoalType(GoalType.DISTANCE)
        vm.setTarget("2000") // over 1000 km
        vm.setTitle("x")
        assertFalse(vm.uiState.value.isValid)
    }

    // ---------- create flow ----------

    @Test
    fun `create success emits Saved and hits the repository once`() = runTest {
        val repo = FakeGoalRepo()
        val vm = NewGoalViewModel(repo, clock = clock, zone = ZoneOffset.UTC)
        vm.setActivity(ActivityType.RUNNING)
        vm.setGoalType(GoalType.DISTANCE)
        vm.setTarget("20")
        // Auto title = "Run 20 km this week" — form is valid.
        assertTrue(vm.uiState.value.isValid)

        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.createCount)
        assertEquals(NewGoalViewModel.Event.Saved, vm.events.first())
    }

    // ---------- edit flow ----------

    @Test
    fun `edit mode pre-fills fields from the existing goal`() = runTest {
        val existing: Goal = DistanceGoal(
            id = "g1", title = "Run 25 km this week",
            activityType = ActivityType.RUNNING, targetValue = 25.0,
            period = GoalPeriod.THIS_WEEK,
            periodStart = Instant.parse("2026-09-14T00:00:00Z"),
            periodEnd = Instant.parse("2026-09-21T00:00:00Z"),
            createdAt = Instant.parse("2026-09-14T00:00:00Z"),
        )
        val repo = FakeGoalRepo(seed = listOf(existing))
        val vm = NewGoalViewModel(repo, editingGoalId = "g1",
            clock = clock, zone = ZoneOffset.UTC)
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals("g1", s.editingGoalId)
        assertEquals(ActivityType.RUNNING, s.activityType)
        assertEquals(GoalType.DISTANCE, s.goalType)
        assertEquals("25", s.targetRaw)
        assertEquals("Run 25 km this week", s.title)
    }

    @Test
    fun `edit mode submit calls updateGoal, not createGoal`() = runTest {
        val existing: Goal = DistanceGoal(
            id = "g1", title = "Run 25 km", activityType = ActivityType.RUNNING,
            targetValue = 25.0, period = GoalPeriod.THIS_WEEK,
            periodStart = Instant.parse("2026-09-14T00:00:00Z"),
            periodEnd = Instant.parse("2026-09-21T00:00:00Z"),
            createdAt = Instant.parse("2026-09-14T00:00:00Z"),
        )
        val repo = FakeGoalRepo(seed = listOf(existing))
        val vm = NewGoalViewModel(repo, editingGoalId = "g1",
            clock = clock, zone = ZoneOffset.UTC)
        dispatcher.scheduler.advanceUntilIdle()

        vm.setTarget("30")
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.updateCount)
        assertEquals(0, repo.createCount)
        assertEquals(30.0, repo.lastUpdatedTarget!!, 1e-6)
    }

    // ---------- 422 field errors ----------

    @Test
    fun `server field errors land on matching fields`() = runTest {
        val repo = FakeGoalRepo()
        repo.createResult = ApiResult.Error(
            message = "Validation failed", status = 422,
            fieldErrors = mapOf("title" to "Too silly"),
        )
        val vm = NewGoalViewModel(repo, clock = clock, zone = ZoneOffset.UTC)
        vm.setActivity(ActivityType.RUNNING)
        vm.setGoalType(GoalType.DISTANCE)
        vm.setTarget("20")
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(FieldError.Text("Too silly"), vm.uiState.value.titleError)
    }

    // ---------- helpers ----------

    private class FakeGoalRepo(
        seed: List<Goal> = emptyList(),
    ) : GoalRepository {
        private val goalsFlow = MutableStateFlow(seed)

        var createCount = 0
        var updateCount = 0
        var lastUpdatedTarget: Double? = null

        var createResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var updateResult: ApiResult<Unit> = ApiResult.Success(Unit)

        override fun observeGoals(): Flow<List<Goal>> = goalsFlow.asStateFlow()
        override suspend fun refresh(): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun createGoal(
            title: String, activityType: ActivityType?, goalType: GoalType,
            period: GoalPeriod, targetValue: Double,
            periodStart: LocalDate, periodEnd: LocalDate,
        ): ApiResult<Unit> {
            createCount++
            return createResult
        }
        override suspend fun updateGoal(
            id: String, title: String, activityType: ActivityType?, targetValue: Double,
        ): ApiResult<Unit> {
            updateCount++
            lastUpdatedTarget = targetValue
            return updateResult
        }
        override suspend fun deleteGoal(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
    }
}
