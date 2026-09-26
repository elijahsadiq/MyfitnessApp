package com.elijah.fitness.ui.goals

import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalPeriod
import com.elijah.fitness.data.model.GoalProgressCalculator
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.model.WorkoutCountGoal
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.GoalRepository
import com.elijah.fitness.data.repository.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    private val periodStart = Instant.parse("2026-09-14T00:00:00Z")
    private val periodEnd   = Instant.parse("2026-09-21T00:00:00Z")
    private val now         = Instant.parse("2026-09-16T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `empty goals yields an empty list (fragment renders empty state)`() = runTest {
        val vm = viewModelWith(goals = emptyList(), workouts = emptyList())
        assertTrue(collectItems(vm).isEmpty())
    }

    @Test
    fun `single incomplete goal becomes a Featured with no section header`() = runTest {
        val g = distanceGoal("g1", target = 10.0, createdAt = periodStart)
        val vm = viewModelWith(goals = listOf(g), workouts = emptyList())
        val items = collectItems(vm)
        assertEquals(1, items.size)
        assertTrue(items[0] is GoalListItem.Featured)
    }

    @Test
    fun `multiple incompletes produce Featured plus Active section`() = runTest {
        val a = distanceGoal("a", target = 10.0, createdAt = periodStart)
        val b = distanceGoal("b", target = 20.0, createdAt = periodStart.plusSeconds(60))
        val c = distanceGoal("c", target = 30.0, createdAt = periodStart.plusSeconds(120))
        val vm = viewModelWith(goals = listOf(a, b, c), workouts = emptyList())
        val items = collectItems(vm)
        // Featured + Section header + 2 Active rows
        assertEquals(4, items.size)
        assertTrue(items[0] is GoalListItem.Featured)
        assertTrue(items[1] is GoalListItem.SectionHeader)
        assertEquals(R.string.goals_active_title, (items[1] as GoalListItem.SectionHeader).labelRes)
        assertEquals("2 in progress", (items[1] as GoalListItem.SectionHeader).countPill)
        assertTrue(items[2] is GoalListItem.Active)
        assertTrue(items[3] is GoalListItem.Active)
    }

    @Test
    fun `Featured picks the most urgent (smallest daysRemaining)`() = runTest {
        // Two goals — same target — but different periodEnd. The nearer one wins.
        val soon = DistanceGoal(
            id = "soon", title = "soon", activityType = ActivityType.RUNNING,
            targetValue = 20.0, period = GoalPeriod.THIS_WEEK,
            periodStart = periodStart, periodEnd = Instant.parse("2026-09-18T00:00:00Z"),
            createdAt = periodStart,
        )
        val later = DistanceGoal(
            id = "later", title = "later", activityType = ActivityType.RUNNING,
            targetValue = 20.0, period = GoalPeriod.THIS_WEEK,
            periodStart = periodStart, periodEnd = Instant.parse("2026-09-30T00:00:00Z"),
            createdAt = periodStart,
        )
        val vm = viewModelWith(goals = listOf(later, soon), workouts = emptyList())
        val items = collectItems(vm)
        val featured = items.first() as GoalListItem.Featured
        assertEquals("soon", featured.goal.goal.id)
    }

    @Test
    fun `completed goals go under a Completed section, none featured if all done`() = runTest {
        // Small target + a workout that exceeds it → complete.
        val g = WorkoutCountGoal(
            id = "done", title = "done", activityType = null,
            targetValue = 1.0, period = GoalPeriod.THIS_WEEK,
            periodStart = periodStart, periodEnd = periodEnd, createdAt = periodStart,
        )
        val w = com.elijah.fitness.data.model.RunningWorkout(
            id = "w1", startTime = Instant.parse("2026-09-15T10:00:00Z"),
            duration = Duration.ofMinutes(30), distanceMeters = 5_000.0,
        )
        val vm = viewModelWith(goals = listOf(g), workouts = listOf(w))
        val items = collectItems(vm)
        assertEquals(2, items.size)
        assertTrue(items[0] is GoalListItem.SectionHeader)
        assertEquals(R.string.goals_completed_title, (items[0] as GoalListItem.SectionHeader).labelRes)
        assertTrue(items[1] is GoalListItem.Completed)
    }

    // ---------- helpers ----------

    /**
     * The ViewModel exposes `items` via `stateIn(WhileSubscribed)` so the
     * upstream doesn't run until something is actually subscribed. In a
     * test we start a background collector, spin the scheduler, then take
     * the latest value.
     */
    private suspend fun collectItems(vm: GoalsViewModel): List<GoalListItem> {
        val latest = MutableStateFlow<List<GoalListItem>>(emptyList())
        val job = kotlinx.coroutines.CoroutineScope(dispatcher).launch {
            vm.items.collect { latest.value = it }
        }
        dispatcher.scheduler.advanceUntilIdle()
        val out = latest.value
        job.cancel()
        return out
    }

    private fun distanceGoal(id: String, target: Double, createdAt: Instant): Goal =
        DistanceGoal(
            id = id, title = "goal $id", activityType = ActivityType.RUNNING,
            targetValue = target, period = GoalPeriod.THIS_WEEK,
            periodStart = periodStart, periodEnd = periodEnd, createdAt = createdAt,
        )

    private fun viewModelWith(goals: List<Goal>, workouts: List<Workout>): GoalsViewModel {
        val goalsFlow = MutableStateFlow(goals)
        val workoutsFlow = MutableStateFlow(workouts)
        val goalRepo = FakeGoalRepo(goalsFlow)
        val workoutRepo = FakeWorkoutRepo(workoutsFlow)
        val calc = GoalProgressCalculator(goalRepo, workoutRepo, clock)
        return GoalsViewModel(goalRepo, calc)
    }

    private class FakeGoalRepo(
        private val flow: MutableStateFlow<List<Goal>>,
    ) : GoalRepository {
        override fun observeGoals(): Flow<List<Goal>> = flow.asStateFlow()
        override suspend fun refresh(): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun createGoal(
            title: String, activityType: ActivityType?,
            goalType: com.elijah.fitness.data.model.GoalType,
            period: GoalPeriod, targetValue: Double,
            periodStart: LocalDate, periodEnd: LocalDate,
        ): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun updateGoal(
            id: String, title: String, activityType: ActivityType?, targetValue: Double,
        ): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun deleteGoal(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
    }

    private class FakeWorkoutRepo(
        private val flow: MutableStateFlow<List<Workout>>,
    ) : WorkoutRepository {
        override fun observeWorkouts(): Flow<List<Workout>> = flow.asStateFlow()
        override suspend fun refresh(): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun addWorkout(workout: Workout): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun getWorkout(id: String): Workout? = flow.value.firstOrNull { it.id == id }
    }
}
