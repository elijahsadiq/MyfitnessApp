package com.elijah.fitness.ui.history

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.RunningWorkout
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
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default filter is ALL and returns every workout`() = runTest {
        val repo = InMemoryTestRepo(fakeWorkouts())
        val vm = HistoryViewModel(repo)
        val list = vm.workouts.first { it.size == 3 }
        assertEquals(3, list.size)
    }

    @Test
    fun `switching filter to RUNNING keeps only running workouts`() = runTest {
        val repo = InMemoryTestRepo(fakeWorkouts())
        val vm = HistoryViewModel(repo)
        vm.setFilter(HistoryViewModel.Filter.RUNNING)
        val list = vm.workouts.first { list -> list.size == 1 && list[0].type == ActivityType.RUNNING }
        assertTrue(list.all { it.type == ActivityType.RUNNING })
    }

    @Test
    fun `switching filter to WEIGHTLIFTING removes running and cycling`() = runTest {
        val repo = InMemoryTestRepo(fakeWorkouts())
        val vm = HistoryViewModel(repo)
        vm.setFilter(HistoryViewModel.Filter.WEIGHTLIFTING)
        val list = vm.workouts.first { list -> list.size == 1 && list[0].type == ActivityType.WEIGHTLIFTING }
        assertEquals(1, list.size)
    }

    private fun fakeWorkouts(): List<Workout> = listOf(
        RunningWorkout(
            id = "r1",
            startTime = Instant.parse("2026-09-10T10:00:00Z"),
            duration = Duration.ofMinutes(30),
            distanceMeters = 5_000.0
        ),
        CyclingWorkout(
            id = "c1",
            startTime = Instant.parse("2026-09-09T10:00:00Z"),
            duration = Duration.ofMinutes(60),
            distanceMeters = 20_000.0
        ),
        StrengthWorkout(
            id = "s1",
            startTime = Instant.parse("2026-09-08T10:00:00Z"),
            duration = Duration.ofMinutes(45),
            sets = listOf(StrengthSet("Bench", 10, 60.0))
        )
    )

    private class InMemoryTestRepo(seed: List<Workout>) : WorkoutRepository {
        private val state = MutableStateFlow(seed)
        override fun observeWorkouts(): Flow<List<Workout>> = state
        override suspend fun addWorkout(workout: Workout) {
            state.value = state.value + workout
        }

        override suspend fun getWorkout(id: String): Workout? =
            state.value.firstOrNull { it.id == id }
    }
}
