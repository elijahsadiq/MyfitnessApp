package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.remote.api.WorkoutApi
import com.elijah.fitness.data.remote.apiCall
import com.elijah.fitness.data.remote.mapper.toDomain
import com.elijah.fitness.data.remote.mapper.toRequestDto
import com.elijah.fitness.tracking.TrackPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Retrofit-backed [WorkoutRepository]. The remote is the source of
 * truth; a local [MutableStateFlow] holds the last-known list so
 * `observeWorkouts()` still gives ViewModels a hot stream (Retrofit
 * itself is call-response — no push).
 *
 * This class is the phase-2 payoff of Step 4's interface: every
 * ViewModel keeps consuming a `WorkoutRepository`, and swapping the
 * in-memory implementation for this HTTP-backed one is one line in
 * [com.elijah.fitness.AppContainer]. No UI code moves.
 *
 * There is no offline cache — a cold start with no network leaves
 * `_workouts` empty until `refresh()` succeeds. Room + WorkManager
 * arrive in phase 4.
 */
class RemoteWorkoutRepository(
    private val workoutApi: WorkoutApi,
) : WorkoutRepository {

    private val _workouts: MutableStateFlow<List<Workout>> = MutableStateFlow(emptyList())

    override fun observeWorkouts(): Flow<List<Workout>> = _workouts.asStateFlow()

    override suspend fun refresh(): ApiResult<Unit> =
        when (val result = apiCall { workoutApi.list() }) {
            is ApiResult.Success -> {
                _workouts.value = result.data
                    .map { it.toDomain() }
                    .sortedByDescending { it.startTime }
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }

    override suspend fun addWorkout(
        workout: Workout,
        routePoints: List<TrackPoint>,
    ): ApiResult<Unit> =
        when (val result = apiCall { workoutApi.create(workout.toRequestDto(routePoints)) }) {
            is ApiResult.Success -> {
                val added = result.data.toDomain()
                // POST is upsert on the server side (on client_uuid); apply
                // the same semantic locally — if the id already exists (a
                // retry that succeeded on second try), replace it.
                _workouts.value = (_workouts.value.filterNot { it.id == added.id } + added)
                    .sortedByDescending { it.startTime }
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }

    override suspend fun getWorkout(id: String): Workout? =
        _workouts.value.firstOrNull { it.id == id }
}
