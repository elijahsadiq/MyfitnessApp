package com.elijah.fitness.ui.history

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.repository.WorkoutDeleteCoordinator
import com.elijah.fitness.data.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

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
        val vm = HistoryViewModel(repo, newDeleteCoordinator(repo))
        val list = vm.workouts.first { it.size == 3 }
        assertEquals(3, list.size)
    }

    @Test
    fun `switching filter to RUNNING keeps only running workouts`() = runTest {
        val repo = InMemoryTestRepo(fakeWorkouts())
        val vm = HistoryViewModel(repo, newDeleteCoordinator(repo))
        vm.setFilter(HistoryViewModel.Filter.RUNNING)
        val list = vm.workouts.first { list -> list.size == 1 && list[0].type == ActivityType.RUNNING }
        assertTrue(list.all { it.type == ActivityType.RUNNING })
    }

    @Test
    fun `switching filter to WEIGHTLIFTING removes running and cycling`() = runTest {
        val repo = InMemoryTestRepo(fakeWorkouts())
        val vm = HistoryViewModel(repo, newDeleteCoordinator(repo))
        vm.setFilter(HistoryViewModel.Filter.WEIGHTLIFTING)
        val list = vm.workouts.first { list -> list.size == 1 && list[0].type == ActivityType.WEIGHTLIFTING }
        assertEquals(1, list.size)
    }

    @Test
    fun `momentum trend compares with the same point last week, not the whole week`() = runTest {
        // "Now" is Wednesday 7 Oct, midday.
        val clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC)
        val repo = InMemoryTestRepo(
            listOf(
                // This week so far: 5 km.
                RunningWorkout(
                    id = "this-mon",
                    startTime = Instant.parse("2026-10-05T08:00:00Z"),
                    duration = Duration.ofMinutes(30),
                    distanceMeters = 5_000.0,
                ),
                // Last week before Wed midday: 10 km — counts.
                RunningWorkout(
                    id = "last-mon",
                    startTime = Instant.parse("2026-09-28T08:00:00Z"),
                    duration = Duration.ofMinutes(60),
                    distanceMeters = 10_000.0,
                ),
                // Last Saturday: later in the week than "now" — ignored.
                CyclingWorkout(
                    id = "last-sat",
                    startTime = Instant.parse("2026-10-03T08:00:00Z"),
                    duration = Duration.ofMinutes(90),
                    distanceMeters = 40_000.0,
                ),
            )
        )
        val vm = HistoryViewModel(repo, newDeleteCoordinator(repo), clock, ZoneOffset.UTC)

        val momentum = vm.momentum.first { it.trendPercent != null }

        assertEquals(5_000.0, momentum.totalDistanceMeters, 0.001)
        assertEquals(-50, momentum.trendPercent)
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

    private fun newDeleteCoordinator(repo: WorkoutRepository): WorkoutDeleteCoordinator =
        WorkoutDeleteCoordinator(
            repository = repo,
            appScope = CoroutineScope(SupervisorJob() + dispatcher),
            delayMs = 50L,
        )

    private class InMemoryTestRepo(seed: List<Workout>) : WorkoutRepository {
        private val state = MutableStateFlow(seed)
        override fun observeWorkouts(): Flow<List<Workout>> = state
        override suspend fun refresh(): com.elijah.fitness.data.remote.ApiResult<Unit> =
            com.elijah.fitness.data.remote.ApiResult.Success(Unit)

        override suspend fun addWorkout(workout: Workout, routePoints: List<com.elijah.fitness.tracking.TrackPoint>): com.elijah.fitness.data.remote.ApiResult<Unit> {
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
        ): com.elijah.fitness.data.remote.ApiResult<Unit> {
            val before = state.value.size
            state.value = state.value.filterNot { it.id == clientUuid }
            return if (state.value.size == before) {
                com.elijah.fitness.data.remote.ApiResult.Error("not found", 404, emptyMap())
            } else {
                com.elijah.fitness.data.remote.ApiResult.Success(Unit)
            }
        }

        override suspend fun updateNotes(
            clientUuid: String,
            notes: String?,
        ): com.elijah.fitness.data.remote.ApiResult<com.elijah.fitness.data.model.Workout> =
            com.elijah.fitness.data.remote.ApiResult.Error(
                "not supported in test fake", 404, emptyMap(),
            )
    }
}
