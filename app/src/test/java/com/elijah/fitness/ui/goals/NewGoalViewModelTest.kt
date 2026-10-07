package com.elijah.fitness.ui.goals

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.Goal
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Phase-8b Step 6 `NewGoalViewModel` tests.
 *
 * The screen now exposes an activity (RUN/RIDE/LIFT/ANY) × metric
 * (DISTANCE/DURATION/COUNT/VOLUME) matrix, with a stepper-driven target
 * + days instead of free-form text. The ViewModel owns the clamping and
 * coercion rules; these tests pin them down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewGoalViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(
        Instant.parse("2026-09-16T09:00:00Z"), ZoneOffset.UTC
    )

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    // --------- Allowed-matrix / activity coercion ---------

    @Test
    fun `switching to LIFT drops DISTANCE and resets target to VOLUME default`() {
        val vm = vm()
        // Default state: RUN + DISTANCE + 50.
        assertEquals(NewGoalViewModel.ActivityOption.RUN, vm.uiState.value.activity)
        assertEquals(GoalType.DISTANCE, vm.uiState.value.metric)
        assertEquals(50, vm.uiState.value.target)

        vm.setActivity(NewGoalViewModel.ActivityOption.LIFT)
        // LIFT doesn't allow DISTANCE → metric falls back to first allowed
        // (DURATION) with its default target (150).
        assertEquals(GoalType.DURATION, vm.uiState.value.metric)
        assertEquals(150, vm.uiState.value.target)
        assertEquals(
            listOf(GoalType.DURATION, GoalType.COUNT, GoalType.VOLUME),
            vm.uiState.value.allowedMetrics,
        )
    }

    @Test
    fun `switching to RIDE preserves DISTANCE and keeps the current target`() {
        val vm = vm()
        vm.setTarget(80) // custom target
        vm.setActivity(NewGoalViewModel.ActivityOption.RIDE)
        assertEquals(GoalType.DISTANCE, vm.uiState.value.metric)
        assertEquals(80, vm.uiState.value.target)
    }

    @Test
    fun `ANY allows only DURATION and COUNT`() {
        val vm = vm()
        vm.setActivity(NewGoalViewModel.ActivityOption.ANY)
        assertEquals(listOf(GoalType.DURATION, GoalType.COUNT), vm.uiState.value.allowedMetrics)
    }

    // --------- Target clamping / stepping ---------

    @Test
    fun `incrementTarget steps up by the metric step and caps at the metric max`() {
        val vm = vm()
        vm.setActivity(NewGoalViewModel.ActivityOption.LIFT)
        vm.setMetric(GoalType.VOLUME) // step 1000, max 500000
        vm.setTarget(499_500)
        vm.incrementTarget()
        assertEquals(500_000, vm.uiState.value.target)
        vm.incrementTarget()
        assertEquals(500_000, vm.uiState.value.target) // no wrap
    }

    @Test
    fun `decrementTarget floors at the metric min`() {
        val vm = vm()
        vm.setMetric(GoalType.DISTANCE) // step 5, min 1
        vm.setTarget(4)
        vm.decrementTarget()
        assertEquals(1, vm.uiState.value.target) // 4 - 5 clamps to min
        vm.decrementTarget()
        assertEquals(1, vm.uiState.value.target)
    }

    @Test
    fun `setTarget clamps type-in values into the metric range`() {
        val vm = vm() // RUN + DISTANCE
        vm.setTarget(99_999) // way above DISTANCE max 1000
        assertEquals(1_000, vm.uiState.value.target)
        vm.setTarget(-5)
        assertEquals(1, vm.uiState.value.target)
    }

    // --------- Days clamping / stepping ---------

    @Test
    fun `days step and clamp between 1 and 365`() {
        val vm = vm()
        vm.setDays(400)
        assertEquals(365, vm.uiState.value.days)
        vm.incrementDays()
        assertEquals(365, vm.uiState.value.days)

        vm.setDays(1)
        vm.decrementDays()
        assertEquals(1, vm.uiState.value.days)
    }

    // --------- Submit (create) ---------

    @Test
    fun `create submit uses the current state and sends startDate = today`() = runTest {
        val repo = FakeGoalRepo()
        val vm = NewGoalViewModel(repo, clock = clock, zone = ZoneOffset.UTC)
        vm.setActivity(NewGoalViewModel.ActivityOption.RIDE)
        vm.setMetric(GoalType.DISTANCE)
        vm.setTarget(100)
        vm.setDays(21)

        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.createCount)
        assertEquals(ActivityType.CYCLING, repo.lastCreatedActivity)
        assertEquals(GoalType.DISTANCE, repo.lastCreatedType)
        assertEquals(100.0, repo.lastCreatedTarget!!, 1e-6)
        assertEquals(21, repo.lastCreatedDuration)
        assertEquals(LocalDate.parse("2026-09-16"), repo.lastCreatedStart)
        assertEquals("Ride 100 km in 21 days", repo.lastCreatedTitle)
        assertEquals(NewGoalViewModel.Event.Saved, vm.events.first())
    }

    @Test
    fun `create submit on COUNT with target=1 produces singular session word`() = runTest {
        val repo = FakeGoalRepo()
        val vm = NewGoalViewModel(repo, clock = clock, zone = ZoneOffset.UTC)
        vm.setActivity(NewGoalViewModel.ActivityOption.RUN)
        vm.setMetric(GoalType.COUNT)
        vm.setTarget(1)
        vm.setDays(1)
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("1 run in 1 day", repo.lastCreatedTitle)
    }

    @Test
    fun `volume summary uses "Lift" + comma-grouped target`() = runTest {
        val repo = FakeGoalRepo()
        val vm = NewGoalViewModel(repo, clock = clock, zone = ZoneOffset.UTC)
        vm.setActivity(NewGoalViewModel.ActivityOption.LIFT)
        vm.setMetric(GoalType.VOLUME)
        vm.setTarget(10_000)
        vm.setDays(30)
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Lift 10,000 kg in 30 days", repo.lastCreatedTitle)
    }

    // --------- Pre-fill (create mode with suggested seed) ---------

    @Test
    fun `pre-fill UiState hydrates activity, metric, target and days`() {
        val vm = NewGoalViewModel(
            repository = FakeGoalRepo(),
            editingGoalId = null,
            initial = NewGoalViewModel.UiState(
                activity = NewGoalViewModel.ActivityOption.RIDE,
                metric = GoalType.DISTANCE,
                target = 100,
                days = 30,
            ),
            clock = clock,
            zone = ZoneOffset.UTC,
        )
        val s = vm.uiState.value
        assertEquals(null, s.editingGoalId)
        assertEquals(NewGoalViewModel.ActivityOption.RIDE, s.activity)
        assertEquals(GoalType.DISTANCE, s.metric)
        assertEquals(100, s.target)
        assertEquals(30, s.days)
    }

    // --------- Edit mode ---------

    @Test
    fun `edit mode pre-fills the ViewModel from the stored goal`() = runTest {
        val existing: Goal = DistanceGoal(
            id = "g1", title = "Run 25 km", activityType = ActivityType.RUNNING,
            targetValue = 25.0, startDate = LocalDate.parse("2026-09-14"),
            durationDays = 7, createdAt = Instant.parse("2026-09-14T00:00:00Z"),
        )
        val repo = FakeGoalRepo(seed = listOf(existing))
        val vm = NewGoalViewModel(repo, editingGoalId = "g1",
            clock = clock, zone = ZoneOffset.UTC)
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals("g1", s.editingGoalId)
        assertEquals(NewGoalViewModel.ActivityOption.RUN, s.activity)
        assertEquals(GoalType.DISTANCE, s.metric)
        assertEquals(25, s.target)
        assertEquals(7, s.days)
        assertEquals(LocalDate.parse("2026-09-14"), s.startDate)
    }

    @Test
    fun `edit submit keeps the original startDate and calls updateGoal`() = runTest {
        val existing: Goal = DistanceGoal(
            id = "g1", title = "Run 25 km", activityType = ActivityType.RUNNING,
            targetValue = 25.0, startDate = LocalDate.parse("2026-09-01"),
            durationDays = 7, createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        )
        val repo = FakeGoalRepo(seed = listOf(existing))
        val vm = NewGoalViewModel(repo, editingGoalId = "g1",
            clock = clock, zone = ZoneOffset.UTC)
        dispatcher.scheduler.advanceUntilIdle()

        vm.setDays(21) // user bumps days
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.updateCount)
        assertEquals(0, repo.createCount)
        assertEquals(21, repo.lastUpdatedDuration)
        // startDate on the goal must not have moved — repo doesn't take it,
        // but editing a goal never calls createGoal (which would set a new
        // startDate), so the original September 1 is preserved implicitly.
        assertEquals(LocalDate.parse("2026-09-01"), vm.uiState.value.startDate)
    }

    // --------- Server errors ---------

    @Test
    fun `server targetValue error lands on targetError`() = runTest {
        val repo = FakeGoalRepo()
        repo.createResult = ApiResult.Error(
            message = "Validation failed", status = 422,
            fieldErrors = mapOf("targetValue" to "Too high"),
        )
        val vm = NewGoalViewModel(repo, clock = clock, zone = ZoneOffset.UTC)
        vm.setActivity(NewGoalViewModel.ActivityOption.RUN)
        vm.setMetric(GoalType.DISTANCE)
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(FieldError.Text("Too high"), vm.uiState.value.targetError)
    }

    @Test
    fun `generic server error becomes a ShowError event, no field errors set`() = runTest {
        val repo = FakeGoalRepo()
        repo.createResult = ApiResult.Error(
            message = "Boom", status = 500, fieldErrors = emptyMap(),
        )
        val vm = NewGoalViewModel(repo, clock = clock, zone = ZoneOffset.UTC)
        vm.setActivity(NewGoalViewModel.ActivityOption.RUN)
        vm.setMetric(GoalType.DISTANCE)
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.events.first() is NewGoalViewModel.Event.ShowError)
        assertNull(vm.uiState.value.targetError)
        assertNotEquals(true, vm.uiState.value.submitting)
    }

    // ---------- helpers ----------

    private fun vm(): NewGoalViewModel =
        NewGoalViewModel(FakeGoalRepo(), clock = clock, zone = ZoneOffset.UTC)

    private class FakeGoalRepo(
        seed: List<Goal> = emptyList(),
    ) : GoalRepository {
        private val goalsFlow = MutableStateFlow(seed)

        var createCount = 0
        var updateCount = 0
        var lastCreatedActivity: ActivityType? = null
        var lastCreatedType: GoalType? = null
        var lastCreatedTarget: Double? = null
        var lastCreatedDuration: Int? = null
        var lastCreatedStart: LocalDate? = null
        var lastCreatedTitle: String? = null
        var lastUpdatedDuration: Int? = null

        var createResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var updateResult: ApiResult<Unit> = ApiResult.Success(Unit)

        override fun observeGoals(): Flow<List<Goal>> = goalsFlow.asStateFlow()
        override suspend fun refresh(): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun createGoal(
            title: String, activityType: ActivityType?, goalType: GoalType,
            targetValue: Double, startDate: LocalDate, durationDays: Int,
        ): ApiResult<Unit> {
            createCount++
            lastCreatedActivity = activityType
            lastCreatedType = goalType
            lastCreatedTarget = targetValue
            lastCreatedDuration = durationDays
            lastCreatedStart = startDate
            lastCreatedTitle = title
            return createResult
        }
        override suspend fun updateGoal(
            id: String, title: String, activityType: ActivityType?,
            goalType: GoalType, targetValue: Double, durationDays: Int,
        ): ApiResult<Unit> {
            updateCount++
            lastUpdatedDuration = durationDays
            return updateResult
        }
        override suspend fun deleteGoal(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun markCompletedNotified(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun makeMainGoal(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun extendGoal(id: String, days: Int): ApiResult<Unit> = ApiResult.Success(Unit)
    }
}
