package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalPeriod
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.remote.api.GoalApi
import com.elijah.fitness.data.remote.apiCall
import com.elijah.fitness.data.remote.apiCallEmpty
import com.elijah.fitness.data.remote.mapper.buildCreateGoalRequest
import com.elijah.fitness.data.remote.mapper.buildUpdateGoalRequest
import com.elijah.fitness.data.remote.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Retrofit-backed [GoalRepository]. The remote is the source of truth;
 * a local [MutableStateFlow] holds the last-known list so
 * [observeGoals] gives ViewModels a hot stream.
 *
 * Mirrors [RemoteWorkoutRepository]: no offline cache; a cold start with
 * no network leaves the store empty until [refresh] succeeds. Room +
 * WorkManager (phase 4) will fit behind this interface without moving
 * any UI code.
 */
class RemoteGoalRepository(
    private val goalApi: GoalApi,
) : GoalRepository {

    private val _goals: MutableStateFlow<List<Goal>> = MutableStateFlow(emptyList())

    override fun observeGoals(): Flow<List<Goal>> = _goals.asStateFlow()

    override suspend fun refresh(): ApiResult<Unit> =
        when (val result = apiCall { goalApi.list() }) {
            is ApiResult.Success -> {
                _goals.value = result.data
                    .map { it.toDomain() }
                    .sortedByDescending { it.createdAt }
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }

    override suspend fun createGoal(
        title: String,
        activityType: ActivityType?,
        goalType: GoalType,
        period: GoalPeriod,
        targetValue: Double,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): ApiResult<Unit> {
        val body = buildCreateGoalRequest(
            title, activityType, goalType, period, targetValue, periodStart, periodEnd,
        )
        return when (val result = apiCall { goalApi.create(body) }) {
            is ApiResult.Success -> {
                val added = result.data.toDomain()
                _goals.value = (listOf(added) + _goals.value.filterNot { it.id == added.id })
                    .sortedByDescending { it.createdAt }
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    override suspend fun updateGoal(
        id: String,
        title: String,
        activityType: ActivityType?,
        targetValue: Double,
    ): ApiResult<Unit> {
        val body = buildUpdateGoalRequest(title, activityType, targetValue)
        return when (val result = apiCall { goalApi.update(id.toLong(), body) }) {
            is ApiResult.Success -> {
                val updated = result.data.toDomain()
                _goals.value = _goals.value
                    .map { if (it.id == updated.id) updated else it }
                    .sortedByDescending { it.createdAt }
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    override suspend fun deleteGoal(id: String): ApiResult<Unit> =
        when (val result = apiCallEmpty { goalApi.delete(id.toLong()) }) {
            is ApiResult.Success -> {
                _goals.value = _goals.value.filterNot { it.id == id }
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }

    override suspend fun markCompletedNotified(id: String): ApiResult<Unit> =
        when (val result = apiCall { goalApi.markNotified(id.toLong()) }) {
            is ApiResult.Success -> {
                val updated = result.data.toDomain()
                _goals.value = _goals.value.map {
                    if (it.id == updated.id) updated else it
                }
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
}
