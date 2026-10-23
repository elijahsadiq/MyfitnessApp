package com.elijah.fitness.ui.log

import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.data.model.StrengthWorkout
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
class LogStrengthWorkoutViewModelTest {

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
    fun `save with valid inputs and at least one set persists the workout`() = runTest {
        val repo = InMemoryRepo()
        val vm = LogStrengthWorkoutViewModel(repo, clock, zone)
        vm.setDate(LocalDate.of(2026, 9, 11))
        vm.setTime(LocalTime.of(10, 0))
        vm.addSet(StrengthSet("Bench Press", 5, 60.0))
        vm.save(hours = 0, minutes = 45, seconds = 0, notes = "upper day")
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first() as LogStrengthWorkoutViewModel.Event.Saved
        val list = repo.observeWorkouts().first { it.isNotEmpty() }
        val w = list.first() as StrengthWorkout
        assertEquals(event.clientUuid, w.id)
        assertEquals(1, w.sets.size)
        assertEquals("upper day", w.notes)
    }

    @Test
    fun `save stores calories only from a known profile weight`() = runTest {
        val known = InMemoryRepo()
        val vmKnown = LogStrengthWorkoutViewModel(known, clock, zone, weightForSavingKg = { 80.0 })
        vmKnown.addSet(StrengthSet("Squat", 5, 100.0))
        vmKnown.setTime(LocalTime.of(10, 0))
        vmKnown.save(hours = 1, minutes = 0, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()
        vmKnown.events.first()
        assertEquals(280, known.observeWorkouts().first { it.isNotEmpty() }.first().caloriesKcal) // 3.5 × 80 × 1

        val unknown = InMemoryRepo()
        val vmUnknown = LogStrengthWorkoutViewModel(unknown, clock, zone, weightForSavingKg = { null })
        vmUnknown.addSet(StrengthSet("Squat", 5, 100.0))
        vmUnknown.setTime(LocalTime.of(10, 0))
        vmUnknown.save(hours = 1, minutes = 0, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()
        vmUnknown.events.first()
        assertEquals(null, unknown.observeWorkouts().first { it.isNotEmpty() }.first().caloriesKcal)
    }

    @Test
    fun `minutes above 59 are rejected like on the run form`() = runTest {
        val vm = LogStrengthWorkoutViewModel(InMemoryRepo(), clock, zone)
        vm.setDate(LocalDate.of(2026, 9, 11))
        vm.setTime(LocalTime.of(10, 0))
        vm.addSet(StrengthSet("Squat", 5, 100.0))
        vm.save(hours = 0, minutes = 75, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first() as LogStrengthWorkoutViewModel.Event.ValidationFailed
        assertTrue(event.errors.any {
            it.field == LogStrengthWorkoutViewModel.Field.DURATION &&
                it.messageRes == R.string.log_error_duration_out_of_range
        })
    }

    @Test
    fun `a double tap on Save stores the workout once`() = runTest {
        val repo = InMemoryRepo()
        val vm = LogStrengthWorkoutViewModel(repo, clock, zone)
        vm.setDate(LocalDate.of(2026, 9, 11))
        vm.setTime(LocalTime.of(10, 0))
        vm.addSet(StrengthSet("Squat", 5, 100.0))
        vm.save(hours = 0, minutes = 30, seconds = 0, notes = "")
        vm.save(hours = 0, minutes = 30, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.observeWorkouts().first().size)
    }

    @Test
    fun `a saved workout is remembered for the log sheet`() = runTest {
        val remembered = mutableListOf<ActivityType>()
        val vm = LogStrengthWorkoutViewModel(
            InMemoryRepo(), clock, zone,
            rememberActivity = { remembered += it },
        )
        vm.setDate(LocalDate.of(2026, 9, 11))
        vm.setTime(LocalTime.of(10, 0))
        vm.addSet(StrengthSet("Squat", 5, 100.0))
        vm.save(hours = 0, minutes = 30, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.events.first() is LogStrengthWorkoutViewModel.Event.Saved)
        assertEquals(listOf(ActivityType.WEIGHTLIFTING), remembered)
    }

    @Test
    fun `save with no sets is rejected`() = runTest {
        val repo = InMemoryRepo()
        val vm = LogStrengthWorkoutViewModel(repo, clock, zone)
        vm.setDate(LocalDate.of(2026, 9, 11))
        vm.setTime(LocalTime.of(10, 0))
        vm.save(hours = 0, minutes = 45, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first() as LogStrengthWorkoutViewModel.Event.ValidationFailed
        assertTrue(event.errors.any {
            it.field == LogStrengthWorkoutViewModel.Field.SETS &&
                it.messageRes == R.string.log_error_sets_required
        })
    }

    @Test
    fun `addSet and removeSetAt update the sets flow`() = runTest {
        val vm = LogStrengthWorkoutViewModel(InMemoryRepo(), clock, zone)
        assertEquals(emptyList<StrengthSet>(), vm.sets.value)
        vm.addSet(StrengthSet("Row", 8, 50.0))
        vm.addSet(StrengthSet("Row", 8, 55.0))
        assertEquals(2, vm.sets.value.size)
        vm.removeSetAt(0)
        assertEquals(1, vm.sets.value.size)
        assertEquals(55.0, vm.sets.value.first().weightKg, 0.0)
    }

    @Test
    fun `future date is rejected even with sets present`() = runTest {
        val repo = InMemoryRepo()
        val vm = LogStrengthWorkoutViewModel(repo, clock, zone)
        vm.setDate(LocalDate.of(2026, 9, 12))
        vm.setTime(LocalTime.of(10, 0))
        vm.addSet(StrengthSet("Bench", 5, 60.0))
        vm.save(hours = 0, minutes = 30, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first() as LogStrengthWorkoutViewModel.Event.ValidationFailed
        assertTrue(event.errors.any {
            it.field == LogStrengthWorkoutViewModel.Field.DATE
        })
    }

    @Test
    fun `a repository crash becomes a save error with Retry, not a crash`() = runTest {
        val repo = InMemoryRepo().apply { throwOnAdd = true }
        val remembered = mutableListOf<ActivityType>()
        val vm = LogStrengthWorkoutViewModel(
            repo, clock, zone,
            rememberActivity = { remembered += it },
        )
        vm.setDate(LocalDate.of(2026, 9, 11))
        vm.setTime(LocalTime.of(10, 0))
        vm.addSet(StrengthSet("Squat", 5, 100.0))
        vm.save(hours = 0, minutes = 30, seconds = 0, notes = "")
        dispatcher.scheduler.advanceUntilIdle()

        val failed = vm.events.first() as LogStrengthWorkoutViewModel.Event.SaveFailed
        assertEquals(R.string.log_error_save_generic, failed.messageRes)
        assertEquals(false, vm.isSaving.value)
        assertEquals(emptyList<ActivityType>(), remembered)
        // The sets stay, so Retry saves the same workout.
        assertEquals(1, vm.sets.value.size)
    }

    private class InMemoryRepo : WorkoutRepository {
        private val state = MutableStateFlow<List<Workout>>(emptyList())

        /** Simulates a Room / disk failure inside the repository. */
        var throwOnAdd = false

        override fun observeWorkouts(): Flow<List<Workout>> = state
        override suspend fun refresh(): com.elijah.fitness.data.remote.ApiResult<Unit> =
            com.elijah.fitness.data.remote.ApiResult.Success(Unit)

        override suspend fun addWorkout(workout: Workout, routePoints: List<com.elijah.fitness.tracking.TrackPoint>): com.elijah.fitness.data.remote.ApiResult<Unit> {
            if (throwOnAdd) error("disk full")
            state.value = state.value + workout
            return com.elijah.fitness.data.remote.ApiResult.Success(Unit)
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
