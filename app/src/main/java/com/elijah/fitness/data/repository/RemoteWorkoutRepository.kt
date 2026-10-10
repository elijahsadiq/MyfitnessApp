package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.model.WorkoutDetail
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.remote.api.WorkoutApi
import com.elijah.fitness.data.remote.apiCall
import com.elijah.fitness.data.remote.apiCallEmpty
import com.elijah.fitness.data.remote.mapper.toDomain
import com.elijah.fitness.data.remote.mapper.toRequestDto
import com.elijah.fitness.data.remote.dto.RoutePointDto
import com.elijah.fitness.tracking.TrackPoint
import java.time.Instant
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

    /**
     * `clientUuid → server id` map, populated on every `refresh()` /
     * `addWorkout()`. The detail endpoint needs the numeric server id but
     * the domain model only carries `clientUuid`, so this map keeps the
     * two reconciled without widening the `Workout` type.
     */
    private val serverIds: MutableMap<String, Long> = mutableMapOf()

    override fun observeWorkouts(): Flow<List<Workout>> = _workouts.asStateFlow()

    override suspend fun clearCache() {
        serverIds.clear()
        _workouts.value = emptyList()
    }

    override suspend fun refresh(): ApiResult<Unit> =
        when (val result = apiCall { workoutApi.list() }) {
            is ApiResult.Success -> {
                serverIds.clear()
                result.data.forEach { serverIds[it.clientUuid] = it.id }
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
                serverIds[result.data.clientUuid] = result.data.id
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

    private fun RoutePointDto.toTrackPoint(): TrackPoint = TrackPoint(
        seq = seq,
        latitude = latitude,
        longitude = longitude,
        elapsedMs = elapsedMs,
        accuracyM = accuracyM,
        recordedAt = Instant.parse(recordedAt),
    )

    override suspend fun deleteWorkout(clientUuid: String): ApiResult<Unit> {
        val serverId = serverIds[clientUuid]
            ?: return ApiResult.Error(
                message = "Workout not found locally",
                status = 404,
                fieldErrors = emptyMap(),
            )
        return when (val result = apiCallEmpty { workoutApi.delete(serverId) }) {
            is ApiResult.Success -> {
                serverIds.remove(clientUuid)
                _workouts.value = _workouts.value.filterNot { it.id == clientUuid }
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    override suspend fun updateNotes(clientUuid: String, notes: String?): ApiResult<Workout> {
        val serverId = serverIds[clientUuid]
            ?: return ApiResult.Error(
                message = "Workout not found locally",
                status = 404,
                fieldErrors = emptyMap(),
            )
        val body: Map<String, Any?> = mapOf("notes" to notes?.takeUnless { it.isBlank() })
        return when (val result = apiCall { workoutApi.patch(serverId, body) }) {
            is ApiResult.Success -> {
                val dto = result.data
                serverIds[dto.clientUuid] = dto.id
                val updated = dto.toDomain()
                _workouts.value = _workouts.value
                    .map { if (it.id == updated.id) updated else it }
                    .sortedByDescending { it.startTime }
                ApiResult.Success(updated)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    override suspend fun getWorkoutDetail(clientUuid: String): ApiResult<WorkoutDetail> {
        val serverId = serverIds[clientUuid]
            ?: return ApiResult.Error(
                message = "Workout not found locally",
                status = 404,
                fieldErrors = emptyMap(),
            )
        return when (val result = apiCall { workoutApi.get(serverId) }) {
            is ApiResult.Success -> {
                val dto = result.data
                serverIds[dto.clientUuid] = dto.id
                val workout = dto.toDomain()
                val points = dto.routePoints
                    .sortedBy { it.seq }
                    .map { it.toTrackPoint() }
                ApiResult.Success(WorkoutDetail(workout, points))
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }
}
