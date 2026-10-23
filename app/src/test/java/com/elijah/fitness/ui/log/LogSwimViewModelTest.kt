package com.elijah.fitness.ui.log

import androidx.lifecycle.SavedStateHandle
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.SwimmingWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.model.WorkoutDetail
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.WorkoutRepository
import com.elijah.fitness.tracking.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Log a swim (phase 8d): live summary, validation and the saved swim. */
@OptIn(ExperimentalCoroutinesApi::class)
class LogSwimViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone: ZoneId = ZoneId.of("UTC")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), zone)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repo: WorkoutRepository = InMemoryRepo(),
        weight: Double = 72.0,
        weightForSaving: Double? = 72.0,
        remembered: MutableList<ActivityType> = mutableListOf(),
    ) = LogSwimViewModel(
        repository = repo,
        clock = clock,
        zone = zone,
        bodyWeightKg = MutableStateFlow(weight),
        weightForSavingKg = { weightForSaving },
        rememberActivity = { remembered += it },
    ).apply {
        setDate(LocalDate.of(2026, 10, 19))
        setTime(LocalTime.of(7, 10))
    }

    // ---------- live summary ----------

    @Test
    fun `opens on 60 laps of a 25 m pool`() = runTest {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(60, state.laps)
        assertEquals(25, state.poolLengthM)
        assertEquals(0, state.poolIndex)
        assertEquals(1_500, state.summary.distanceMeters)
        assertEquals(0L, state.summary.pacePer100mSeconds)
    }

    @Test
    fun `the summary follows the duration, pool and laps`() = runTest {
        val vm = viewModel()
        vm.setDuration(0, 32, 10)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(129L, vm.uiState.value.summary.pacePer100mSeconds)
        assertEquals(270, vm.uiState.value.summary.kcal)

        vm.setPoolLength(50)
        vm.stepLaps(-1)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(59, vm.uiState.value.laps)
        assertEquals(1, vm.uiState.value.poolIndex)
        assertEquals(2_950, vm.uiState.value.summary.distanceMeters)
    }

    @Test
    fun `only 25 and 50 m pools can be picked`() = runTest {
        val vm = viewModel()
        vm.setPoolLength(33)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(25, vm.uiState.value.poolLengthM)
    }

    @Test
    fun `the laps stepper stops at 1 and 400`() = runTest {
        val vm = viewModel()
        repeat(100) { vm.stepLaps(-1) }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.laps)
        assertEquals(false, vm.uiState.value.canRemoveLap)
        assertEquals(true, vm.uiState.value.canAddLap)

        repeat(500) { vm.stepLaps(+1) }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(400, vm.uiState.value.laps)
        assertEquals(false, vm.uiState.value.canAddLap)
    }

    // ---------- save ----------

    @Test
    fun `save stores a swim with laps, pool, distance and calories`() = runTest {
        val repo = InMemoryRepo()
        val remembered = mutableListOf<ActivityType>()
        val vm = viewModel(repo = repo, remembered = remembered)
        vm.save(hours = 0, minutes = 32, seconds = 10, notes = "  drills  ")
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first()
        assertTrue(event is LogSwimViewModel.Event.Saved)
        val swim = repo.saved.single() as SwimmingWorkout
        assertEquals((event as LogSwimViewModel.Event.Saved).clientUuid, swim.id)
        assertEquals(1_500.0, swim.distanceMeters, 1e-9)
        assertEquals(60, swim.laps)
        assertEquals(25, swim.poolLengthM)
        assertEquals(1_930L, swim.duration.seconds)
        assertEquals("drills", swim.notes)
        assertEquals(Instant.parse("2026-10-19T07:10:00Z"), swim.startTime)
        // 7.0 x 72 kg x 1930 s: stored at save time.
        assertEquals(270, swim.caloriesKcal)
        assertEquals(listOf(ActivityType.SWIMMING), remembered)
    }

    @Test
    fun `a 50 m pool swim saves 50 m laps`() = runTest {
        val repo = InMemoryRepo()
        val vm = viewModel(repo = repo)
        vm.setPoolLength(50)
        vm.save(hours = 1, minutes = 2, seconds = 30, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val swim = repo.saved.single() as SwimmingWorkout
        assertEquals(3_000.0, swim.distanceMeters, 1e-9)
        assertEquals(50, swim.poolLengthM)
        assertEquals(null, swim.notes)
        assertEquals(125L, swim.pacePer100mSeconds())
    }

    @Test
    fun `no calories are stored while the profile weight is unknown`() = runTest {
        val repo = InMemoryRepo()
        val vm = viewModel(repo = repo, weightForSaving = null)
        vm.save(hours = 0, minutes = 30, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, repo.saved.single().caloriesKcal)
    }

    @Test
    fun `a zero duration is an inline error and nothing is saved`() = runTest {
        val repo = InMemoryRepo()
        val vm = viewModel(repo = repo)
        vm.save(hours = 0, minutes = 0, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first() as LogSwimViewModel.Event.ValidationFailed
        assertEquals(
            listOf(LogSwimViewModel.FieldError(LogSwimViewModel.Field.DURATION, R.string.log_error_duration_zero)),
            event.errors,
        )
        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun `minutes over 59 are out of range`() = runTest {
        val vm = viewModel()
        vm.save(hours = 0, minutes = 75, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first() as LogSwimViewModel.Event.ValidationFailed
        assertEquals(R.string.log_error_duration_out_of_range, event.errors.single().messageRes)
    }

    @Test
    fun `a start in the future is rejected`() = runTest {
        val vm = viewModel()
        vm.setTime(LocalTime.of(13, 0)) // the clock says 12:00
        vm.save(hours = 0, minutes = 30, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first() as LogSwimViewModel.Event.ValidationFailed
        assertEquals(LogSwimViewModel.Field.DATE, event.errors.single().field)
    }

    @Test
    fun `a failed save keeps the form and offers a retry with the same id`() = runTest {
        val repo = InMemoryRepo().apply { nextResult = ApiResult.NetworkError }
        val remembered = mutableListOf<ActivityType>()
        val vm = viewModel(repo = repo, remembered = remembered)
        vm.save(hours = 0, minutes = 30, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val failed = vm.events.first() as LogSwimViewModel.Event.SaveFailed
        assertEquals(R.string.log_error_save_network, failed.messageRes)
        assertTrue(remembered.isEmpty())
        assertEquals(false, vm.isSaving.value)

        repo.nextResult = ApiResult.Success(Unit)
        vm.save(hours = 0, minutes = 30, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()
        val saved = vm.events.first() as LogSwimViewModel.Event.Saved
        assertEquals(repo.attemptedIds.first(), saved.clientUuid)
        assertEquals(repo.attemptedIds.first(), repo.attemptedIds.last())
    }

    @Test
    fun `a repository crash becomes a save error, not a crash`() = runTest {
        val repo = InMemoryRepo().apply { throwOnAdd = true }
        val vm = viewModel(repo = repo)
        vm.save(hours = 0, minutes = 30, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val failed = vm.events.first() as LogSwimViewModel.Event.SaveFailed
        assertEquals(R.string.log_error_save_generic, failed.messageRes)
    }

    // ---------- process death ----------

    @Test
    fun `date, start time, pool and laps come back after a process death`() = runTest {
        val saved = SavedStateHandle()
        val first = LogSwimViewModel(repository = InMemoryRepo(), clock = clock, zone = zone, savedState = saved)
        first.setDate(LocalDate.of(2026, 10, 17))
        first.setTime(LocalTime.of(6, 45))
        first.setPoolLength(50)
        first.stepLaps(-1)
        first.stepLaps(-1)

        // Android rebuilds the ViewModel from what it kept in the handle.
        val restored = LogSwimViewModel(
            repository = InMemoryRepo(),
            clock = clock,
            zone = zone,
            savedState = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val state = restored.uiState.value
        assertEquals(LocalDate.of(2026, 10, 17), state.date)
        assertEquals(LocalTime.of(6, 45), state.time)
        assertEquals(50, state.poolLengthM)
        assertEquals(58, state.laps)
    }

    @Test
    fun `the restored form saves with the same id, so a retry can't duplicate`() = runTest {
        val saved = SavedStateHandle()
        val firstRepo = InMemoryRepo().apply { nextResult = ApiResult.NetworkError }
        val first = LogSwimViewModel(repository = firstRepo, clock = clock, zone = zone, savedState = saved)
        first.setDate(LocalDate.of(2026, 10, 19))
        first.setTime(LocalTime.of(7, 10))
        first.save(hours = 0, minutes = 30, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val secondRepo = InMemoryRepo()
        val restored = LogSwimViewModel(
            repository = secondRepo,
            clock = clock,
            zone = zone,
            savedState = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }),
        )
        restored.save(hours = 0, minutes = 30, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(firstRepo.attemptedIds, secondRepo.attemptedIds)
    }

    @Test
    fun `broken or out of range saved values fall back to the defaults`() = runTest {
        val vm = LogSwimViewModel(
            repository = InMemoryRepo(),
            clock = clock,
            zone = zone,
            savedState = SavedStateHandle(
                mapOf(
                    "log_swim_date_epoch_day" to "not a day",
                    "log_swim_time_second_of_day" to 999_999,
                    "log_swim_pool_length_m" to 33,
                    "log_swim_laps" to 0,
                ),
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(LocalDate.of(2026, 10, 19), state.date)
        assertEquals(LocalTime.of(12, 0), state.time)
        assertEquals(25, state.poolLengthM)
        assertEquals(60, state.laps)
    }

    @Test
    fun `a swim over 24 h is rejected before it reaches the server`() = runTest {
        val repo = InMemoryRepo()
        val vm = viewModel(repo = repo)
        vm.setDate(LocalDate.of(2026, 10, 17))
        vm.save(hours = 32, minutes = 0, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val failed = vm.events.first() as LogSwimViewModel.Event.ValidationFailed
        assertEquals(
            listOf(LogSwimViewModel.FieldError(LogSwimViewModel.Field.DURATION, R.string.log_error_duration_too_long)),
            failed.errors,
        )
        assertTrue(repo.saved.isEmpty())
    }

    private class InMemoryRepo : WorkoutRepository {
        val saved = mutableListOf<Workout>()
        val attemptedIds = mutableListOf<String>()
        var nextResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var throwOnAdd = false
        private val state = MutableStateFlow<List<Workout>>(emptyList())

        override fun observeWorkouts(): Flow<List<Workout>> = state
        override suspend fun refresh(): ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun addWorkout(workout: Workout, routePoints: List<TrackPoint>): ApiResult<Unit> {
            if (throwOnAdd) error("disk full")
            attemptedIds += workout.id
            val result = nextResult
            if (result is ApiResult.Success) {
                saved += workout
                state.value = saved.toList()
            }
            return result
        }

        override suspend fun getWorkout(id: String): Workout? = saved.firstOrNull { it.id == id }
        override suspend fun getWorkoutDetail(clientUuid: String): ApiResult<WorkoutDetail> =
            ApiResult.Error("not supported", 404)
        override suspend fun deleteWorkout(clientUuid: String): ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun updateNotes(clientUuid: String, notes: String?): ApiResult<Workout> =
            ApiResult.Error("not supported", 404)
    }
}
