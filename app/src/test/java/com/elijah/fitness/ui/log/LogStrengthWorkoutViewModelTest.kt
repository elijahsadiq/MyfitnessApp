package com.elijah.fitness.ui.log

import com.elijah.fitness.R
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

        val event = vm.events.first()
        assertEquals(LogStrengthWorkoutViewModel.Event.Saved, event)
        val list = repo.observeWorkouts().first { it.isNotEmpty() }
        val w = list.first() as StrengthWorkout
        assertEquals(1, w.sets.size)
        assertEquals("upper day", w.notes)
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

    private class InMemoryRepo : WorkoutRepository {
        private val state = MutableStateFlow<List<Workout>>(emptyList())
        override fun observeWorkouts(): Flow<List<Workout>> = state
        override suspend fun addWorkout(workout: Workout) {
            state.value = state.value + workout
        }

        override suspend fun getWorkout(id: String): Workout? =
            state.value.firstOrNull { it.id == id }
    }
}
