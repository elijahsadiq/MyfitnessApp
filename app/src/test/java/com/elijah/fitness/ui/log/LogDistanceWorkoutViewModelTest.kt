package com.elijah.fitness.ui.log

import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.RowingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.repository.WorkoutRepository
import com.elijah.fitness.util.CalorieEstimator
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

@OptIn(ExperimentalCoroutinesApi::class)
class LogDistanceWorkoutViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone: ZoneId = ZoneId.of("UTC")
    private val fixedNow: Instant = Instant.parse("2026-09-11T12:00:00Z")
    private val clock: Clock = Clock.fixed(fixedNow, zone)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save with valid running inputs adds a RunningWorkout`() = runTest {
        val repo = InMemoryRepo()
        val vm = LogDistanceWorkoutViewModel(ActivityType.RUNNING, repo, clock, zone)
        vm.setDate(LocalDate.of(2026, 9, 11))
        vm.setTime(LocalTime.of(9, 30))
        vm.save(hours = 0, minutes = 30, seconds = 0, distanceKm = 5.0, notes = "morning run")
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first()
        assertTrue(event is LogDistanceWorkoutViewModel.Event.Saved)
        val list = repo.observeWorkouts().first { it.isNotEmpty() }
        assertEquals(1, list.size)
        val w = list.first()
        assertTrue(w is RunningWorkout)
        assertEquals(5_000.0, (w as RunningWorkout).distanceMeters, 1e-6)
        assertEquals("morning run", w.notes)
    }

    @Test
    fun `save with cycling type builds a CyclingWorkout`() = runTest {
        val repo = InMemoryRepo()
        val vm = LogDistanceWorkoutViewModel(ActivityType.CYCLING, repo, clock, zone)
        vm.setDate(LocalDate.of(2026, 9, 11))
        vm.setTime(LocalTime.of(8, 0))
        vm.save(hours = 1, minutes = 0, seconds = 0, distanceKm = 22.5, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        vm.events.first()
        val list = repo.observeWorkouts().first { it.isNotEmpty() }
        assertTrue(list.first() is CyclingWorkout)
    }

    @Test
    fun `save stores calories from the real profile weight`() = runTest {
        val repo = InMemoryRepo()
        val vm = LogDistanceWorkoutViewModel(
            ActivityType.RUNNING, repo, clock, zone, weightForSavingKg = { 90.0 },
        )
        vm.setDate(LocalDate.of(2026, 9, 11))
        vm.setTime(LocalTime.of(9, 30))
        vm.save(hours = 0, minutes = 30, seconds = 0, distanceKm = 5.0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        vm.events.first()
        val w = repo.observeWorkouts().first { it.isNotEmpty() }.first()
        assertEquals(CalorieEstimator.workoutKcal(w, 90.0), w.caloriesKcal)
    }

    @Test
    fun `save stores no calories while the profile weight is unknown`() = runTest {
        // Offline cold start: only the 70 kg fallback is known. Nothing is
        // stored, so screens work the calories out once the weight loads.
        val repo = InMemoryRepo()
        val vm = LogDistanceWorkoutViewModel(
            ActivityType.ROWING, repo, clock, zone, weightForSavingKg = { null },
        )
        vm.setDate(LocalDate.of(2026, 9, 11))
        vm.setTime(LocalTime.of(9, 30))
        vm.save(hours = 0, minutes = 30, seconds = 0, distanceKm = 6.0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        vm.events.first()
        val w = repo.observeWorkouts().first { it.isNotEmpty() }.first()
        assertTrue(w is RowingWorkout)
        assertEquals(null, w.caloriesKcal)
    }

    @Test
    fun `future date is rejected`() = runTest {
        val repo = InMemoryRepo()
        val vm = LogDistanceWorkoutViewModel(ActivityType.RUNNING, repo, clock, zone)
        vm.setDate(LocalDate.of(2026, 9, 12)) // one day after fixedNow
        vm.setTime(LocalTime.of(9, 0))
        vm.save(hours = 0, minutes = 30, seconds = 0, distanceKm = 5.0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first() as LogDistanceWorkoutViewModel.Event.ValidationFailed
        assertTrue(event.errors.any {
            it.field == LogDistanceWorkoutViewModel.Field.DATE &&
                it.messageRes == R.string.log_error_date_in_future
        })
    }

    @Test
    fun `zero duration is rejected`() = runTest {
        val repo = InMemoryRepo()
        val vm = LogDistanceWorkoutViewModel(ActivityType.RUNNING, repo, clock, zone)
        vm.setDate(LocalDate.of(2026, 9, 11))
        vm.setTime(LocalTime.of(9, 0))
        vm.save(hours = 0, minutes = 0, seconds = 0, distanceKm = 5.0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first() as LogDistanceWorkoutViewModel.Event.ValidationFailed
        assertTrue(event.errors.any {
            it.field == LogDistanceWorkoutViewModel.Field.DURATION
        })
    }

    @Test
    fun `a saved row is remembered for the log sheet, a failed one is not`() = runTest {
        val repo = InMemoryRepo()
        val remembered = mutableListOf<ActivityType>()
        val vm = LogDistanceWorkoutViewModel(
            ActivityType.ROWING, repo, clock, zone,
            rememberActivity = { remembered += it },
        )
        vm.setDate(LocalDate.of(2026, 9, 11))
        vm.setTime(LocalTime.of(9, 0))

        repo.nextAddResult = com.elijah.fitness.data.remote.ApiResult.NetworkError
        vm.save(hours = 0, minutes = 25, seconds = 0, distanceKm = 6.0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.events.first() is LogDistanceWorkoutViewModel.Event.SaveFailed)
        assertEquals(emptyList<ActivityType>(), remembered)

        repo.nextAddResult = com.elijah.fitness.data.remote.ApiResult.Success(Unit)
        vm.save(hours = 0, minutes = 25, seconds = 0, distanceKm = 6.0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.events.first() is LogDistanceWorkoutViewModel.Event.Saved)
        assertEquals(listOf(ActivityType.ROWING), remembered)
    }

    @Test
    fun `a duration over 24 h is rejected before it reaches the server`() = runTest {
        val repo = InMemoryRepo()
        val vm = LogDistanceWorkoutViewModel(ActivityType.RUNNING, repo, clock, zone)
        vm.setDate(LocalDate.of(2026, 9, 10))
        vm.setTime(LocalTime.of(6, 0))
        vm.save(hours = 32, minutes = 0, seconds = 0, distanceKm = 5.0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first() as LogDistanceWorkoutViewModel.Event.ValidationFailed
        assertTrue(event.errors.any {
            it.field == LogDistanceWorkoutViewModel.Field.DURATION &&
                it.messageRes == R.string.log_error_duration_too_long
        })
        assertEquals(0, repo.observeWorkouts().first().size)
    }

    @Test
    fun `distance over 300 km is rejected`() = runTest {
        val repo = InMemoryRepo()
        val vm = LogDistanceWorkoutViewModel(ActivityType.CYCLING, repo, clock, zone)
        vm.setDate(LocalDate.of(2026, 9, 11))
        vm.setTime(LocalTime.of(6, 0))
        vm.save(hours = 8, minutes = 0, seconds = 0, distanceKm = 301.0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first() as LogDistanceWorkoutViewModel.Event.ValidationFailed
        assertTrue(event.errors.any {
            it.field == LogDistanceWorkoutViewModel.Field.DISTANCE &&
                it.messageRes == R.string.log_error_distance_too_far
        })
    }

    @Test
    fun `missing distance is rejected`() = runTest {
        val repo = InMemoryRepo()
        val vm = LogDistanceWorkoutViewModel(ActivityType.RUNNING, repo, clock, zone)
        vm.setDate(LocalDate.of(2026, 9, 11))
        vm.setTime(LocalTime.of(9, 0))
        vm.save(hours = 0, minutes = 30, seconds = 0, distanceKm = null, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first() as LogDistanceWorkoutViewModel.Event.ValidationFailed
        assertTrue(event.errors.any {
            it.field == LogDistanceWorkoutViewModel.Field.DISTANCE &&
                it.messageRes == R.string.log_error_distance_positive
        })
    }

    @Test
    fun `a repository crash becomes a save error with Retry, not a crash`() = runTest {
        val repo = InMemoryRepo().apply { throwOnAdd = true }
        val remembered = mutableListOf<ActivityType>()
        val vm = LogDistanceWorkoutViewModel(
            ActivityType.RUNNING, repo, clock, zone,
            rememberActivity = { remembered += it },
        )
        vm.setDate(LocalDate.of(2026, 9, 11))
        vm.setTime(LocalTime.of(9, 0))
        vm.save(hours = 0, minutes = 30, seconds = 0, distanceKm = 5.0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val failed = vm.events.first() as LogDistanceWorkoutViewModel.Event.SaveFailed
        assertEquals(R.string.log_error_save_generic, failed.messageRes)
        // Save works again (Retry) and nothing was remembered or stored.
        assertEquals(false, vm.isSaving.value)
        assertEquals(emptyList<ActivityType>(), remembered)
        assertEquals(0, repo.observeWorkouts().first().size)
    }

    private class InMemoryRepo : WorkoutRepository {
        private val state = MutableStateFlow<List<Workout>>(emptyList())
        var nextAddResult: com.elijah.fitness.data.remote.ApiResult<Unit> =
            com.elijah.fitness.data.remote.ApiResult.Success(Unit)

        /** Simulates a Room / disk failure inside the repository. */
        var throwOnAdd = false

        override fun observeWorkouts(): Flow<List<Workout>> = state
        override suspend fun refresh(): com.elijah.fitness.data.remote.ApiResult<Unit> =
            com.elijah.fitness.data.remote.ApiResult.Success(Unit)

        override suspend fun addWorkout(workout: Workout, routePoints: List<com.elijah.fitness.tracking.TrackPoint>): com.elijah.fitness.data.remote.ApiResult<Unit> {
            if (throwOnAdd) error("disk full")
            val result = nextAddResult
            if (result is com.elijah.fitness.data.remote.ApiResult.Success) {
                state.value = state.value + workout
            }
            return result
        }

        override suspend fun getWorkout(id: String): Workout? =
            state.value.firstOrNull { it.id == id }

        override suspend fun getWorkoutDetail(
            clientUuid: String,
        ): com.elijah.fitness.data.remote.ApiResult<com.elijah.fitness.data.model.WorkoutDetail> =
            com.elijah.fitness.data.remote.ApiResult.Error(
                "not supported in test fake", 404, emptyMap(),
            )
        override suspend fun deleteWorkout(
            clientUuid: String,
        ): com.elijah.fitness.data.remote.ApiResult<Unit> =
            com.elijah.fitness.data.remote.ApiResult.Success(Unit)

        override suspend fun updateNotes(
            clientUuid: String,
            notes: String?,
        ): com.elijah.fitness.data.remote.ApiResult<com.elijah.fitness.data.model.Workout> =
            com.elijah.fitness.data.remote.ApiResult.Error(
                "not supported in test fake", 404, emptyMap(),
            )
    }
}
