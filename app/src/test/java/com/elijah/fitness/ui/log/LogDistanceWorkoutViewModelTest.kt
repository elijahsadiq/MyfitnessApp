package com.elijah.fitness.ui.log

import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.repository.WorkoutRepository
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
        assertEquals(LogDistanceWorkoutViewModel.Event.Saved, event)
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

    private class InMemoryRepo : WorkoutRepository {
        private val state = MutableStateFlow<List<Workout>>(emptyList())
        var nextAddResult: com.elijah.fitness.data.remote.ApiResult<Unit> =
            com.elijah.fitness.data.remote.ApiResult.Success(Unit)

        override fun observeWorkouts(): Flow<List<Workout>> = state
        override suspend fun refresh(): com.elijah.fitness.data.remote.ApiResult<Unit> =
            com.elijah.fitness.data.remote.ApiResult.Success(Unit)

        override suspend fun addWorkout(workout: Workout, routePoints: List<com.elijah.fitness.tracking.TrackPoint>): com.elijah.fitness.data.remote.ApiResult<Unit> {
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
    }
}
