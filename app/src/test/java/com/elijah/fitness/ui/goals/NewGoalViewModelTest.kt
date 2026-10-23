package com.elijah.fitness.ui.goals

import androidx.lifecycle.SavedStateHandle
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.DurationGoal
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.GoalRepository
import com.elijah.fitness.testutil.StringsXmlText
import com.elijah.fitness.ui.auth.FieldError
import com.elijah.fitness.ui.goals.NewGoalViewModel.ActivityOption
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * The New goal sheet's ViewModel (phase 8d, NewGoal.dc.html): defaults,
 * the keep-or-reset rule when the activity changes, Double targets end
 * to end, the live summary and saving. The wording itself is covered in
 * [NewGoalPlanTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewGoalViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** The design's "today": Monday 19 October 2026. */
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    // --------- Defaults ---------

    @Test
    fun `opens on Running, Distance, 50 km, 30 days`() {
        val vm = vm()
        val s = state(vm)
        assertTrue(s.loaded)
        assertFalse(s.isEditing)
        assertEquals(ActivityOption.RUN, s.activity)
        assertEquals(GoalType.DISTANCE, s.metric)
        assertEquals(50.0, s.target, 0.0)
        assertEquals(30, s.days)
        assertEquals("New goal", s.title)
        assertEquals("Create goal", s.submitLabel)
    }

    @Test
    fun `the default summary matches the design`() {
        val s = state(vm(weightKg = 72.0))
        assertEquals("Run 50 km in 30 days", s.sentence)
        assertEquals("Per week", s.perLabel)
        assertEquals("11.7 km", s.perValue)
        assertEquals(LocalDate.of(2026, 11, 17), s.endDate)
        assertEquals("17 Nov", s.endsText)
        assertEquals(3_530, s.kcal)
        assertEquals("3,530 kcal", s.kcalText)
        assertEquals("Distance", s.targetLabel)
        assertEquals("About 1.7\u00A0km a\u00A0day", s.targetHint)
        assertEquals("50 km", s.targetValueText)
        assertEquals("30 days", s.daysValueText)
        assertEquals("Starts today", s.daysHint)
        assertEquals(listOf(GoalType.DISTANCE, GoalType.DURATION, GoalType.COUNT), s.metrics)
        assertEquals(0, s.metricIndex)
    }

    // --------- Activity / metric rules ---------

    @Test
    fun `switching activity keeps an offered metric but resets the target`() {
        val vm = vm()
        vm.increaseTarget() // 55 km
        vm.setActivity(ActivityOption.RIDE)
        val s = state(vm)
        assertEquals(GoalType.DISTANCE, s.metric)
        assertEquals(100.0, s.target, 0.0) // the ride default, not 55
        assertEquals(1, s.activityIndex)
    }

    @Test
    fun `switching to Workout drops Distance for Time at its default`() {
        val vm = vm()
        vm.setActivity(ActivityOption.LIFT)
        val s = state(vm)
        assertEquals(GoalType.DURATION, s.metric)
        assertEquals(150.0, s.target, 0.0)
        assertEquals(listOf(GoalType.DURATION, GoalType.COUNT, GoalType.VOLUME), s.metrics)
        assertEquals("2 h 30 min", s.targetValueText)
        assertEquals("Work out for 2 h 30 min in 30 days", s.sentence)
    }

    @Test
    fun `Any keeps Sessions and offers only Time and Sessions`() {
        val vm = vm()
        vm.setMetric(GoalType.COUNT)
        vm.setActivity(ActivityOption.ANY)
        val s = state(vm)
        assertEquals(GoalType.COUNT, s.metric)
        assertEquals(12.0, s.target, 0.0)
        assertEquals(listOf(GoalType.DURATION, GoalType.COUNT), s.metrics)
        assertEquals("Complete 12 workouts in 30 days", s.sentence)
    }

    @Test
    fun `a metric the activity doesn't offer is ignored`() {
        val vm = vm()
        vm.setActivity(ActivityOption.ANY)
        vm.setMetric(GoalType.VOLUME)
        assertEquals(GoalType.DURATION, state(vm).metric)
    }

    @Test
    fun `changing metric resets the target to that metric's default`() {
        val vm = vm()
        vm.setMetric(GoalType.VOLUME) // not offered for runs: ignored
        vm.setMetric(GoalType.COUNT)
        assertEquals(12.0, state(vm).target, 0.0)
        assertEquals("12 runs", state(vm).targetValueText)
        vm.setMetric(GoalType.DURATION)
        assertEquals(150.0, state(vm).target, 0.0)
    }

    // --------- Steppers ---------

    @Test
    fun `swim distance moves in half kilometres, as a Double`() {
        val vm = vm()
        vm.setActivity(ActivityOption.SWIM)
        assertEquals(10.0, state(vm).target, 0.0)
        vm.increaseTarget()
        val s = state(vm)
        assertEquals(10.5, s.target, 0.0)
        assertEquals("10.5 km", s.targetValueText)
        assertEquals("Swim 10.5 km in 30 days", s.sentence)
    }

    @Test
    fun `minus disables at one step and plus at the server limit`() {
        val vm = vm()
        vm.setActivity(ActivityOption.SWIM)
        repeat(30) { vm.decreaseTarget() }
        val low = state(vm)
        assertEquals(0.5, low.target, 0.0)
        assertFalse(low.canDecreaseTarget)
        assertTrue(low.canIncreaseTarget)

        vm.setActivity(ActivityOption.LIFT)
        vm.setMetric(GoalType.VOLUME)
        repeat(500) { vm.increaseTarget() }
        val high = state(vm)
        assertEquals(500_000.0, high.target, 0.0)
        assertFalse(high.canIncreaseTarget)
        assertTrue(high.canDecreaseTarget)
    }

    @Test
    fun `days step between 1 and 365 and switch the pace to per day`() {
        val vm = vm()
        repeat(40) { vm.decreaseDays() }
        val one = state(vm)
        assertEquals(1, one.days)
        assertFalse(one.canDecreaseDays)
        assertEquals("1 day", one.daysValueText)
        assertEquals("Per day", one.perLabel)
        assertEquals("50 km", one.perValue)
        assertEquals("19 Oct", one.endsText)
        assertEquals("Run 50 km in 1 day", one.sentence)

        repeat(400) { vm.increaseDays() }
        val max = state(vm)
        assertEquals(365, max.days)
        assertFalse(max.canIncreaseDays)
    }

    @Test
    fun `sessions hint is weekly from seven days and daily below`() {
        val vm = vm()
        vm.setMetric(GoalType.COUNT)
        assertEquals("About 2.8 a\u00A0week", state(vm).targetHint)
        assertEquals("2.8 runs", state(vm).perValue)
        repeat(25) { vm.decreaseDays() } // 5 days
        assertEquals("About 2.4 a\u00A0day", state(vm).targetHint)
        assertEquals("Per day", state(vm).perLabel)
    }

    // --------- Calories and weight ---------

    @Test
    fun `burn estimate follows the profile weight`() {
        val weight = MutableStateFlow(72.0)
        val vm = NewGoalViewModel(
            FakeGoalRepo(), StringsXmlText,
            bodyWeightKg = weight, clock = clock, zone = ZoneOffset.UTC, locale = Locale.UK,
        )
        vm.setActivity(ActivityOption.SWIM)
        vm.setMetric(GoalType.DURATION)
        assertEquals(1_260, state(vm).kcal)
        assertEquals("1,260 kcal", state(vm).kcalText)

        weight.value = 80.0
        assertEquals(1_400, state(vm).kcal)

        vm.setActivity(ActivityOption.RIDE)
        vm.setMetric(GoalType.DISTANCE)
        weight.value = 72.0
        assertEquals(2_700, state(vm).kcal)
    }

    @Test
    fun `the weight hint shows only while the weight is the fallback`() {
        val fallback = MutableStateFlow(true)
        val vm = NewGoalViewModel(
            FakeGoalRepo(), StringsXmlText,
            weightIsFallback = fallback, clock = clock, zone = ZoneOffset.UTC, locale = Locale.UK,
        )
        assertTrue(state(vm).showWeightHint)
        fallback.value = false
        assertFalse(state(vm).showWeightHint)
    }

    // --------- Prefill ---------

    @Test
    fun `a prefill seeds activity, metric, a Double target and days`() {
        val vm = vm(prefill = NewGoalViewModel.Prefill(ActivityOption.SWIM, GoalType.DISTANCE, 10.5, 14))
        val s = state(vm)
        assertEquals(ActivityOption.SWIM, s.activity)
        assertEquals(10.5, s.target, 0.0)
        assertEquals(14, s.days)
        assertEquals("Swim 10.5 km in 14 days", s.sentence)
    }

    @Test
    fun `a prefill with a metric the activity lacks falls back to its first`() {
        val vm = vm(prefill = NewGoalViewModel.Prefill(ActivityOption.ANY, GoalType.DISTANCE, 40.0, 500))
        val s = state(vm)
        assertEquals(GoalType.DURATION, s.metric)
        assertEquals(150.0, s.target, 0.0)
        assertEquals(365, s.days)
    }

    // --------- Save: create ---------

    @Test
    fun `create sends a Double target, today and the sentence as the title`() = runTest(dispatcher) {
        val repo = FakeGoalRepo()
        val vm = vm(repo)
        vm.setActivity(ActivityOption.SWIM)
        vm.increaseTarget() // 10.5 km
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.createCount)
        assertEquals(ActivityType.SWIMMING, repo.lastActivity)
        assertEquals(GoalType.DISTANCE, repo.lastType)
        assertEquals(10.5, repo.lastTarget!!, 0.0)
        assertEquals(30, repo.lastDuration)
        assertEquals(LocalDate.of(2026, 10, 19), repo.lastStart)
        assertEquals("Swim 10.5 km in 30 days", repo.lastTitle)
        assertEquals(NewGoalViewModel.Event.Saved, vm.events.first())
        assertFalse(state(vm).submitting)
    }

    @Test
    fun `create for any activity sends a null activity`() = runTest(dispatcher) {
        val repo = FakeGoalRepo()
        val vm = vm(repo)
        vm.setActivity(ActivityOption.ANY)
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.createCount)
        assertEquals(null, repo.lastActivity)
        assertEquals(GoalType.DURATION, repo.lastType)
        assertEquals("Be active for 2 h 30 min in 30 days", repo.lastTitle)
    }

    @Test
    fun `a second tap while saving doesn't create twice`() = runTest(dispatcher) {
        val repo = FakeGoalRepo()
        val vm = vm(repo)
        vm.submit()
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.createCount)
    }

    // --------- Save: edit ---------

    @Test
    fun `edit loads the stored goal, keeps its start and calls updateGoal`() = runTest(dispatcher) {
        val existing: Goal = DistanceGoal(
            id = "g1", title = "Swim 6.5 km", activityType = ActivityType.SWIMMING,
            targetValue = 6.5, startDate = LocalDate.of(2026, 10, 12),
            durationDays = 14, createdAt = Instant.parse("2026-10-12T08:00:00Z"),
        )
        val repo = FakeGoalRepo(seed = listOf(existing))
        val vm = vm(repo, editingGoalId = "g1")
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.uiState.value
        assertTrue(s.loaded)
        assertTrue(s.isEditing)
        assertEquals("Edit goal", s.title)
        assertEquals("Save changes", s.submitLabel)
        assertEquals(ActivityOption.SWIM, s.activity)
        assertEquals(GoalType.DISTANCE, s.metric)
        assertEquals(6.5, s.target, 0.0)
        assertEquals(14, s.days)
        assertEquals("Started 12 Oct", s.daysHint)
        assertEquals(LocalDate.of(2026, 10, 25), s.endDate) // from the goal's own start

        vm.increaseDays()
        vm.increaseTarget() // 7 km
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.updateCount)
        assertEquals(0, repo.createCount)
        assertEquals("g1", repo.lastUpdatedId)
        assertEquals(7.0, repo.lastTarget!!, 0.0)
        assertEquals(15, repo.lastDuration)
        assertEquals("Swim 7 km in 15 days", repo.lastTitle)
    }

    @Test
    fun `edit never offers the defaults for a goal it can't find`() = runTest(dispatcher) {
        val repo = FakeGoalRepo()
        val vm = vm(repo, editingGoalId = "g1")
        assertFalse(vm.uiState.value.loaded)
        dispatcher.scheduler.advanceUntilIdle()

        // Not in the list, nor after asking the server: the sheet closes.
        assertEquals(1, repo.refreshCount)
        assertEquals(NewGoalViewModel.Event.GoalUnavailable, vm.events.first())
        assertFalse(vm.uiState.value.loaded)
        // "Save changes" can't write the create defaults over the goal.
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, repo.updateCount)
        assertEquals(0, repo.createCount)
    }

    @Test
    fun `edit waits for a list that is still loading`() = runTest(dispatcher) {
        // Straight after a restore the list is empty until the cache is read.
        val repo = FakeGoalRepo()
        val vm = vm(repo, editingGoalId = "g1")
        dispatcher.scheduler.advanceTimeBy(NewGoalViewModel.EDIT_LOAD_WAIT_MS / 2)
        assertFalse(vm.uiState.value.loaded)
        repo.publish(listOf(swimGoal()))
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.uiState.value
        assertTrue(s.loaded)
        assertEquals(ActivityOption.SWIM, s.activity)
        assertEquals(6.5, s.target, 0.0)
        assertEquals(0, repo.refreshCount)
    }

    @Test
    fun `edit asks the server once when the goal isn't listed`() = runTest(dispatcher) {
        val repo = FakeGoalRepo()
        repo.onRefresh = { repo.publish(listOf(swimGoal())) }
        val vm = vm(repo, editingGoalId = "g1")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.refreshCount)
        assertTrue(vm.uiState.value.loaded)
        assertEquals(ActivityOption.SWIM, vm.uiState.value.activity)
    }

    @Test
    fun `the button reads Saving while a save is in flight`() = runTest(dispatcher) {
        val repo = FakeGoalRepo()
        val vm = vm(repo)
        assertEquals("Create goal", state(vm).submitLabel)
        repo.holdSaves = true
        vm.submit()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.submitting)
        assertEquals("Saving…", vm.uiState.value.submitLabel)
        repo.release()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.submitting)
        assertEquals("Create goal", vm.uiState.value.submitLabel)
    }

    @Test
    fun `fewer days never change the target, as in the design`() {
        val vm = vm()
        vm.setActivity(ActivityOption.ROW)
        vm.setMetric(GoalType.DURATION)
        repeat(120) { vm.increaseTarget() } // 2 h 30 min + 30 h = 32 h 30 min
        assertEquals(1_950.0, state(vm).target, 0.0)
        repeat(29) { vm.decreaseDays() } // 1 day
        val s = state(vm)
        assertEquals(1, s.days)
        assertEquals(1_950.0, s.target, 0.0)
        assertEquals("32 h 30 min", s.targetValueText)
        assertTrue(s.canIncreaseTarget) // only the server's 10,000 min stops "+"
    }

    @Test
    fun `edit keeps a stored time target a day can't hold`() = runTest(dispatcher) {
        // Made on the old screen, which allowed 10,000 min for any length.
        val legacy: Goal = DurationGoal(
            id = "g2", title = "Be active for 2,000 min", activityType = null,
            targetValue = 2_000.0, startDate = LocalDate.of(2026, 10, 19),
            durationDays = 1, createdAt = Instant.parse("2026-10-19T08:00:00Z"),
        )
        val repo = FakeGoalRepo(seed = listOf(legacy))
        val vm = vm(repo, editingGoalId = "g2")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2_000.0, vm.uiState.value.target, 0.0)

        vm.submit() // "Save changes" without touching anything
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.updateCount)
        assertEquals(2_000.0, repo.lastTarget!!, 0.0)
        assertEquals(1, repo.lastDuration)
    }

    @Test
    fun `edit never shortens a running goal to end before today`() = runTest(dispatcher) {
        // Started 12 Oct for 14 days; today is 19 Oct, so at least 8 days.
        val repo = FakeGoalRepo(seed = listOf(swimGoal()))
        val vm = vm(repo, editingGoalId = "g1")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.canDecreaseDays)

        repeat(20) { vm.decreaseDays() }
        val s = state(vm)
        assertEquals(8, s.days)
        assertEquals(LocalDate.of(2026, 10, 19), s.endDate) // ends today
        assertFalse(s.canDecreaseDays)
        assertTrue(s.canIncreaseDays)
    }

    @Test
    fun `an edited goal that has already ended can't be shortened further`() = runTest(dispatcher) {
        val ended: Goal = DistanceGoal(
            id = "g3", title = "Swim 5 km", activityType = ActivityType.SWIMMING,
            targetValue = 5.0, startDate = LocalDate.of(2026, 10, 1),
            durationDays = 7, createdAt = Instant.parse("2026-10-01T08:00:00Z"),
        )
        val vm = vm(FakeGoalRepo(seed = listOf(ended)), editingGoalId = "g3")
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.canDecreaseDays)
        vm.decreaseDays()
        assertEquals(7, state(vm).days)
        vm.increaseDays()
        assertEquals(8, state(vm).days)
    }

    // --------- Process death ---------

    @Test
    fun `the picks survive process death in a create flow`() {
        val saved = SavedStateHandle()
        val first = vm(prefill = NewGoalViewModel.Prefill(ActivityOption.RUN, GoalType.DISTANCE, 20.0, 14), savedState = saved)
        first.setActivity(ActivityOption.SWIM)
        first.increaseTarget() // 10.5 km, still 14 days
        assertEquals("Swim 10.5 km in 14 days", state(first).sentence)

        // A new ViewModel from the same arguments and the kept state.
        val restored = vm(
            prefill = NewGoalViewModel.Prefill(ActivityOption.RUN, GoalType.DISTANCE, 20.0, 14),
            savedState = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }),
        )
        val s = state(restored)
        assertEquals(ActivityOption.SWIM, s.activity)
        assertEquals(GoalType.DISTANCE, s.metric)
        assertEquals(10.5, s.target, 0.0)
        assertEquals(14, s.days)
    }

    @Test
    fun `the picks survive process death in an edit, over the stored goal`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = vm(FakeGoalRepo(seed = listOf(swimGoal())), editingGoalId = "g1", savedState = saved)
        dispatcher.scheduler.advanceUntilIdle()
        first.increaseTarget() // 7 km
        first.increaseDays() // 15 days

        val repo = FakeGoalRepo(seed = listOf(swimGoal()))
        val restored = vm(
            repo,
            editingGoalId = "g1",
            savedState = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val s = restored.uiState.value
        assertTrue(s.loaded)
        assertEquals(7.0, s.target, 0.0)
        assertEquals(15, s.days)
        assertEquals("Started 12 Oct", s.daysHint) // the start still comes from the goal

        restored.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(7.0, repo.lastTarget!!, 0.0)
        assertEquals(15, repo.lastDuration)
    }

    @Test
    fun `broken saved picks are ignored, never a crash`() {
        val saved = SavedStateHandle(
            mapOf(
                "new_goal_activity" to "SKIING",
                "new_goal_metric" to "DISTANCE",
                "new_goal_target" to 10.0,
                "new_goal_days" to 30,
            ),
        )
        val s = state(vm(savedState = saved))
        assertEquals(ActivityOption.RUN, s.activity)
        assertEquals(50.0, s.target, 0.0)

        val noDistance = SavedStateHandle(
            mapOf(
                "new_goal_activity" to "ANY",
                "new_goal_metric" to "DISTANCE",
                "new_goal_target" to 10.0,
                "new_goal_days" to 30,
            ),
        )
        assertEquals(ActivityOption.RUN, state(vm(savedState = noDistance)).activity)
    }

    // --------- Save: errors ---------

    @Test
    fun `a rejected field shows the server's message`() = runTest(dispatcher) {
        val repo = FakeGoalRepo()
        repo.createResult = ApiResult.Error(
            message = "Validation failed", status = 422,
            fieldErrors = mapOf("targetValue" to "Must be at most 1000"),
        )
        val vm = vm(repo)
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            NewGoalViewModel.Event.ShowError(FieldError.Text("Must be at most 1000")),
            vm.events.first(),
        )
        assertFalse(state(vm).submitting)
    }

    @Test
    fun `no connection and server errors show generic messages`() = runTest(dispatcher) {
        val repo = FakeGoalRepo()
        repo.createResult = ApiResult.NetworkError
        val vm = vm(repo)
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            NewGoalViewModel.Event.ShowError(FieldError.Res(R.string.auth_error_network)),
            vm.events.first(),
        )

        repo.createResult = ApiResult.Error(message = "Boom", status = 500)
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            NewGoalViewModel.Event.ShowError(FieldError.Res(R.string.auth_error_generic)),
            vm.events.first(),
        )
    }

    @Test
    fun `swim and row options map to the API both ways`() {
        assertEquals(ActivityType.SWIMMING, ActivityOption.SWIM.toApi())
        assertEquals(ActivityType.ROWING, ActivityOption.ROW.toApi())
        ActivityOption.entries.forEach {
            assertEquals(it, ActivityOption.fromApi(it.toApi()))
        }
    }

    // ---------- helpers ----------

    private fun vm(
        repo: FakeGoalRepo = FakeGoalRepo(),
        editingGoalId: String? = null,
        prefill: NewGoalViewModel.Prefill? = null,
        weightKg: Double = 70.0,
        savedState: SavedStateHandle = SavedStateHandle(),
    ): NewGoalViewModel = NewGoalViewModel(
        repository = repo,
        text = StringsXmlText,
        editingGoalId = editingGoalId,
        prefill = prefill,
        bodyWeightKg = MutableStateFlow(weightKg),
        clock = clock,
        zone = ZoneOffset.UTC,
        locale = Locale.UK,
        savedState = savedState,
    )

    /** The state once the summary has caught up with the last change. */
    private fun state(vm: NewGoalViewModel): NewGoalUiState {
        dispatcher.scheduler.advanceUntilIdle()
        return vm.uiState.value
    }

    private fun swimGoal(): Goal = DistanceGoal(
        id = "g1", title = "Swim 6.5 km", activityType = ActivityType.SWIMMING,
        targetValue = 6.5, startDate = LocalDate.of(2026, 10, 12),
        durationDays = 14, createdAt = Instant.parse("2026-10-12T08:00:00Z"),
    )

    private class FakeGoalRepo(seed: List<Goal> = emptyList()) : GoalRepository {
        private val goalsFlow = MutableStateFlow(seed)

        var refreshCount = 0
        var onRefresh: () -> Unit = {}

        /** While true, create / update wait for [release] (a slow server). */
        var holdSaves = false
        private val gate = CompletableDeferred<Unit>()

        fun publish(goals: List<Goal>) { goalsFlow.value = goals }
        fun release() { gate.complete(Unit) }

        var createCount = 0
        var updateCount = 0
        var lastActivity: ActivityType? = null
        var lastType: GoalType? = null
        var lastTarget: Double? = null
        var lastDuration: Int? = null
        var lastStart: LocalDate? = null
        var lastTitle: String? = null
        var lastUpdatedId: String? = null

        var createResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var updateResult: ApiResult<Unit> = ApiResult.Success(Unit)

        override fun observeGoals(): Flow<List<Goal>> = goalsFlow.asStateFlow()
        override suspend fun refresh(): ApiResult<Unit> {
            refreshCount++
            onRefresh()
            return ApiResult.Success(Unit)
        }
        override suspend fun createGoal(
            title: String, activityType: ActivityType?, goalType: GoalType,
            targetValue: Double, startDate: LocalDate, durationDays: Int,
        ): ApiResult<Unit> {
            if (holdSaves) gate.await()
            createCount++
            lastTitle = title
            lastActivity = activityType
            lastType = goalType
            lastTarget = targetValue
            lastStart = startDate
            lastDuration = durationDays
            return createResult
        }
        override suspend fun updateGoal(
            id: String, title: String, activityType: ActivityType?,
            goalType: GoalType, targetValue: Double, durationDays: Int,
        ): ApiResult<Unit> {
            updateCount++
            lastUpdatedId = id
            lastTitle = title
            lastActivity = activityType
            lastType = goalType
            lastTarget = targetValue
            lastDuration = durationDays
            return updateResult
        }
        override suspend fun deleteGoal(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun markCompletedNotified(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun makeMainGoal(id: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun extendGoal(id: String, days: Int): ApiResult<Unit> = ApiResult.Success(Unit)
    }
}
