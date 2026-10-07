package com.elijah.fitness.ui.goals

import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalProgressCalculator
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.model.WorkoutCountGoal
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.GoalDeleteCoordinator
import com.elijah.fitness.data.repository.GoalRepository
import com.elijah.fitness.data.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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

/**
 * Phase 8b: the top of the list is always a Header row carrying the
 * active/completed counts, followed (optionally) by the MainGoalCard,
 * then grouped sections. Sections with no items are omitted entirely.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    private val startDate = LocalDate.parse("2026-09-14") // Monday
    private val durationDays = 7
    private val now = Instant.parse("2026-09-16T09:00:00Z") // Wed
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `empty goals yields an empty list (fragment renders empty state)`() = runTest {
        val vm = viewModelWith(goals = emptyList(), workouts = emptyList())
        assertTrue(collectItems(vm).isEmpty())
    }

    @Test
    fun `single incomplete goal becomes Header + MainGoalCard, no section`() = runTest {
        val g = distanceGoal("g1", target = 10.0, createdAt = startDate.atStartOfDay(ZoneOffset.UTC).toInstant())
        val vm = viewModelWith(goals = listOf(g), workouts = emptyList())
        val items = collectItems(vm)
        assertEquals(2, items.size)
        assertTrue(items[0] is GoalListItem.Header)
        val header = items[0] as GoalListItem.Header
        assertEquals(1, header.activeCount)
        assertEquals(0, header.completedCount)
        assertTrue(items[1] is GoalListItem.MainGoalCard)
    }

    @Test
    fun `multiple incompletes produce Header + MainGoalCard + Active section`() = runTest {
        val base = startDate.atStartOfDay(ZoneOffset.UTC).toInstant()
        val a = distanceGoal("a", target = 10.0, createdAt = base)
        val b = distanceGoal("b", target = 20.0, createdAt = base.plusSeconds(60))
        val c = distanceGoal("c", target = 30.0, createdAt = base.plusSeconds(120))
        val vm = viewModelWith(goals = listOf(a, b, c), workouts = emptyList())
        val items = collectItems(vm)
        // Header + MainGoalCard + Section header + 2 Active rows
        assertEquals(5, items.size)
        assertTrue(items[0] is GoalListItem.Header)
        assertTrue(items[1] is GoalListItem.MainGoalCard)
        assertTrue(items[2] is GoalListItem.SectionHeader)
        assertEquals(R.string.goals_active_title, (items[2] as GoalListItem.SectionHeader).labelRes)
        assertEquals("2 in progress", (items[2] as GoalListItem.SectionHeader).countPill)
        assertTrue(items[3] is GoalListItem.Active)
        assertTrue(items[4] is GoalListItem.Active)
    }

    @Test
    fun `MainGoalCard prefers the main goal when it is active`() = runTest {
        val base = startDate.atStartOfDay(ZoneOffset.UTC).toInstant()
        val urgent = DistanceGoal(
            id = "urgent", title = "u", activityType = ActivityType.RUNNING,
            targetValue = 20.0, startDate = startDate, durationDays = 3, createdAt = base,
        )
        val main = DistanceGoal(
            id = "main", title = "m", activityType = ActivityType.RUNNING,
            targetValue = 20.0, startDate = startDate, durationDays = 30, createdAt = base,
            isMain = true,
        )
        val vm = viewModelWith(goals = listOf(urgent, main), workouts = emptyList())
        val items = collectItems(vm)
        val card = items.filterIsInstance<GoalListItem.MainGoalCard>().single()
        assertEquals("main", card.goal.goal.id)
    }

    @Test
    fun `MainGoalCard falls back to fewest-daysLeft when the main goal is ended`() = runTest {
        val base = startDate.atStartOfDay(ZoneOffset.UTC).toInstant()
        val pastMain = DistanceGoal(
            id = "past", title = "p", activityType = ActivityType.RUNNING,
            targetValue = 20.0, startDate = LocalDate.parse("2026-08-01"),
            durationDays = 7, createdAt = Instant.parse("2026-08-01T00:00:00Z"),
            isMain = true,
        )
        val active = DistanceGoal(
            id = "active", title = "a", activityType = ActivityType.RUNNING,
            targetValue = 20.0, startDate = startDate, durationDays = 30, createdAt = base,
        )
        val vm = viewModelWith(goals = listOf(pastMain, active), workouts = emptyList())
        val items = collectItems(vm)
        val card = items.filterIsInstance<GoalListItem.MainGoalCard>().single()
        assertEquals("active", card.goal.goal.id)
    }

    @Test
    fun `MainGoalCard picks the goal with fewest daysLeft`() = runTest {
        val base = startDate.atStartOfDay(ZoneOffset.UTC).toInstant()
        val soon = DistanceGoal(
            id = "soon", title = "soon", activityType = ActivityType.RUNNING,
            targetValue = 20.0, startDate = startDate, durationDays = 3, createdAt = base,
        )
        val later = DistanceGoal(
            id = "later", title = "later", activityType = ActivityType.RUNNING,
            targetValue = 20.0, startDate = startDate, durationDays = 30, createdAt = base,
        )
        val vm = viewModelWith(goals = listOf(later, soon), workouts = emptyList())
        val items = collectItems(vm)
        val card = items.filterIsInstance<GoalListItem.MainGoalCard>().single()
        assertEquals("soon", card.goal.goal.id)
    }

    @Test
    fun `completed goals go under a Completed section, no MainGoalCard when none are active`() = runTest {
        val g = WorkoutCountGoal(
            id = "done", title = "done", activityType = null,
            targetValue = 1.0, startDate = startDate, durationDays = 7, createdAt = startDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
        )
        val w = com.elijah.fitness.data.model.RunningWorkout(
            id = "w1", startTime = Instant.parse("2026-09-15T10:00:00Z"),
            duration = Duration.ofMinutes(30), distanceMeters = 5_000.0,
        )
        val vm = viewModelWith(goals = listOf(g), workouts = listOf(w))
        val items = collectItems(vm)
        // Header + Section header + 1 Completed row
        assertEquals(3, items.size)
        assertTrue(items[0] is GoalListItem.Header)
        assertEquals(0, (items[0] as GoalListItem.Header).activeCount)
        assertEquals(1, (items[0] as GoalListItem.Header).completedCount)
        assertTrue(items[1] is GoalListItem.SectionHeader)
        assertEquals(R.string.goals_completed_title, (items[1] as GoalListItem.SectionHeader).labelRes)
        assertEquals("1", (items[1] as GoalListItem.SectionHeader).countPill)
        assertTrue(items[2] is GoalListItem.Completed)
    }

    @Test
    fun `expired goals land in Ended, not Active, and are never MainGoalCard`() = runTest {
        val lastWeekStart = LocalDate.parse("2026-09-07")
        val base = startDate.atStartOfDay(ZoneOffset.UTC).toInstant()
        val lastWeekBase = lastWeekStart.atStartOfDay(ZoneOffset.UTC).toInstant()

        val active = distanceGoal("active", target = 10.0, createdAt = base)
        val completedCurrent = WorkoutCountGoal(
            id = "comp-current", title = "comp-current", activityType = null,
            targetValue = 1.0, startDate = startDate, durationDays = 7, createdAt = base,
        )
        val endedUnmet = WorkoutCountGoal(
            id = "ended-unmet", title = "ended-unmet", activityType = null,
            targetValue = 5.0, startDate = lastWeekStart, durationDays = 7, createdAt = lastWeekBase,
        )
        val endedComplete = WorkoutCountGoal(
            id = "ended-comp", title = "ended-comp", activityType = null,
            targetValue = 1.0, startDate = lastWeekStart, durationDays = 7, createdAt = lastWeekBase,
        )
        val workoutInCompleted = com.elijah.fitness.data.model.RunningWorkout(
            id = "w1", startTime = Instant.parse("2026-09-15T10:00:00Z"),
            duration = Duration.ofMinutes(20), distanceMeters = 1_000.0,
        )
        val workoutInEnded = com.elijah.fitness.data.model.RunningWorkout(
            id = "w2", startTime = Instant.parse("2026-09-10T10:00:00Z"),
            duration = Duration.ofMinutes(20), distanceMeters = 1_000.0,
        )

        val vm = viewModelWith(
            goals = listOf(active, completedCurrent, endedUnmet, endedComplete),
            workouts = listOf(workoutInCompleted, workoutInEnded),
        )
        val items = collectItems(vm)

        val card = items.filterIsInstance<GoalListItem.MainGoalCard>().singleOrNull()
        assertTrue("expected one MainGoalCard, got $card", card != null)
        assertEquals("active", card!!.goal.goal.id)

        val headers = items.filterIsInstance<GoalListItem.SectionHeader>().map { it.labelRes }
        assertEquals(listOf(R.string.goals_completed_title, R.string.goals_ended_title), headers)

        val completed = items.filterIsInstance<GoalListItem.Completed>().map { it.goal.goal.id }
        assertTrue("ended-comp belongs in Completed, got $completed", "ended-comp" in completed)
        assertTrue("comp-current belongs in Completed, got $completed", "comp-current" in completed)

        val ended = items.filterIsInstance<GoalListItem.Ended>().map { it.goal.goal.id }
        assertEquals(listOf("ended-unmet"), ended)
    }

    // ---------- completion Snackbar ----------

    @Test
    fun `no celebration when the seed pass finds a goal already notified`() = runTest {
        val g = WorkoutCountGoal(
            id = "done", title = "done", activityType = null,
            targetValue = 1.0, startDate = startDate, durationDays = 7,
            createdAt = startDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
            completedNotified = true,
        )
        val w = com.elijah.fitness.data.model.RunningWorkout(
            id = "w1", startTime = Instant.parse("2026-09-15T10:00:00Z"),
            duration = Duration.ofMinutes(30), distanceMeters = 5_000.0,
        )
        val goalsFlow = MutableStateFlow<List<Goal>>(listOf(g))
        val workoutsFlow = MutableStateFlow<List<Workout>>(listOf(w))
        val repo = FakeGoalRepo(goalsFlow)
        val vm = buildWith(goalsFlow, workoutsFlow, repo)
        val events = collectEvents(vm)

        assertTrue(events.none { it is GoalsViewModel.ErrorEvent.GoalCompleted })
        assertEquals(0, repo.markNotifiedCallCount)
    }

    @Test
    fun `celebrate on seed pass when the goal is complete but not yet notified`() = runTest {
        val g = WorkoutCountGoal(
            id = "pending", title = "pending", activityType = null,
            targetValue = 1.0, startDate = startDate, durationDays = 7,
            createdAt = startDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
            completedNotified = false,
        )
        val w = com.elijah.fitness.data.model.RunningWorkout(
            id = "w1", startTime = Instant.parse("2026-09-15T10:00:00Z"),
            duration = Duration.ofMinutes(30), distanceMeters = 5_000.0,
        )
        val goalsFlow = MutableStateFlow<List<Goal>>(listOf(g))
        val workoutsFlow = MutableStateFlow<List<Workout>>(listOf(w))
        val repo = FakeGoalRepo(goalsFlow)
        val vm = buildWith(goalsFlow, workoutsFlow, repo)
        val events = collectEvents(vm)

        val completions = events.filterIsInstance<GoalsViewModel.ErrorEvent.GoalCompleted>()
        assertEquals(1, completions.size)
        assertEquals("pending", completions[0].title)
        assertEquals(1, repo.markNotifiedCallCount)
    }

    @Test
    fun `celebrate on transition from incomplete to complete during the session`() = runTest {
        val g = WorkoutCountGoal(
            id = "grow", title = "grow", activityType = null,
            targetValue = 1.0, startDate = startDate, durationDays = 7,
            createdAt = startDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
            completedNotified = false,
        )
        val goalsFlow = MutableStateFlow<List<Goal>>(listOf(g))
        val workoutsFlow = MutableStateFlow<List<Workout>>(emptyList())
        val repo = FakeGoalRepo(goalsFlow)
        val vm = buildWith(goalsFlow, workoutsFlow, repo)

        val events = mutableListOf<GoalsViewModel.ErrorEvent>()
        val job = kotlinx.coroutines.CoroutineScope(dispatcher).launch {
            vm.errorEvents.collect { events.add(it) }
        }
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(events.filterIsInstance<GoalsViewModel.ErrorEvent.GoalCompleted>().isEmpty())

        workoutsFlow.value = listOf(
            com.elijah.fitness.data.model.RunningWorkout(
                id = "w1", startTime = Instant.parse("2026-09-15T10:00:00Z"),
                duration = Duration.ofMinutes(30), distanceMeters = 5_000.0,
            )
        )
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        val completions = events.filterIsInstance<GoalsViewModel.ErrorEvent.GoalCompleted>()
        assertEquals(1, completions.size)
        assertEquals("grow", completions[0].title)
        assertEquals(1, repo.markNotifiedCallCount)
    }

    // ---------- helpers ----------

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

    private suspend fun collectEvents(vm: GoalsViewModel): MutableList<GoalsViewModel.ErrorEvent> {
        val bag = mutableListOf<GoalsViewModel.ErrorEvent>()
        val job = kotlinx.coroutines.CoroutineScope(dispatcher).launch {
            vm.errorEvents.collect { bag.add(it) }
        }
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()
        return bag
    }

    private fun distanceGoal(id: String, target: Double, createdAt: Instant): Goal =
        DistanceGoal(
            id = id, title = "goal $id", activityType = ActivityType.RUNNING,
            targetValue = target, startDate = startDate, durationDays = durationDays,
            createdAt = createdAt,
        )

    private fun viewModelWith(goals: List<Goal>, workouts: List<Workout>): GoalsViewModel {
        val goalsFlow = MutableStateFlow(goals)
        val workoutsFlow = MutableStateFlow(workouts)
        val goalRepo = FakeGoalRepo(goalsFlow)
        val workoutRepo = FakeWorkoutRepo(workoutsFlow)
        val calc = GoalProgressCalculator(goalRepo, workoutRepo, clock, ZoneOffset.UTC)
        return GoalsViewModel(goalRepo, calc, newDeleteCoordinator(goalRepo))
    }

    private fun buildWith(
        goals: MutableStateFlow<List<Goal>>,
        workouts: MutableStateFlow<List<Workout>>,
        repo: FakeGoalRepo = FakeGoalRepo(goals),
    ): GoalsViewModel {
        val workoutRepo = FakeWorkoutRepo(workouts)
        val calc = GoalProgressCalculator(repo, workoutRepo, clock, ZoneOffset.UTC)
        return GoalsViewModel(repo, calc, newDeleteCoordinator(repo))
    }

    private fun newDeleteCoordinator(repo: GoalRepository): GoalDeleteCoordinator =
        GoalDeleteCoordinator(repo, CoroutineScope(SupervisorJob() + dispatcher), delayMs = 50L)

    private class FakeGoalRepo(
        private val flow: MutableStateFlow<List<Goal>>,
    ) : GoalRepository {
        var markNotifiedCallCount = 0
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
        override suspend fun deleteGoal(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun markCompletedNotified(id: String): ApiResult<Unit> {
            markNotifiedCallCount++
            return ApiResult.Success(Unit)
        }
        override suspend fun makeMainGoal(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun extendGoal(id: String, days: Int): ApiResult<Unit> = ApiResult.Success(Unit)
    }

    private class FakeWorkoutRepo(
        private val flow: MutableStateFlow<List<Workout>>,
    ) : WorkoutRepository {
        override fun observeWorkouts(): Flow<List<Workout>> = flow.asStateFlow()
        override suspend fun refresh(): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun addWorkout(workout: Workout, routePoints: List<com.elijah.fitness.tracking.TrackPoint>): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun getWorkout(id: String): Workout? = flow.value.firstOrNull { it.id == id }
        override suspend fun getWorkoutDetail(clientUuid: String): ApiResult<com.elijah.fitness.data.model.WorkoutDetail> =
            ApiResult.Error("not supported in test fake", 404, emptyMap())
    }
}
