package com.elijah.fitness.data.repository

import com.elijah.fitness.data.local.db.CachedGoalEntity
import com.elijah.fitness.data.local.db.GoalCacheDao
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.remote.api.GoalApi
import com.elijah.fitness.data.remote.apiCall
import com.elijah.fitness.data.remote.apiCallEmpty
import com.elijah.fitness.data.remote.dto.ExtendGoalRequestDto
import com.elijah.fitness.data.remote.dto.GoalDto
import com.elijah.fitness.data.remote.mapper.buildCreateGoalRequest
import com.elijah.fitness.data.remote.mapper.buildUpdateGoalRequest
import com.elijah.fitness.data.remote.mapper.toCacheDto
import com.elijah.fitness.data.remote.mapper.toDomainOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Retrofit-backed [GoalRepository]. The remote is the source of truth;
 * a local [MutableStateFlow] holds the last-known list so [observeGoals]
 * gives ViewModels a hot stream.
 *
 * Phase 10: when a [cache] is given, every list the server confirms is
 * also written to Room, and a cold start reads it back before the first
 * refresh. Goal progress is worked out on the phone from the workouts,
 * so with the cache a workout logged offline still moves its goal.
 * Creating or editing a goal still needs a connection.
 */
class RemoteGoalRepository(
    private val goalApi: GoalApi,
    private val cache: GoalCacheDao? = null,
) : GoalRepository {

    private val _goals: MutableStateFlow<List<Goal>> = MutableStateFlow(emptyList())

    /** True once the Room copy has been read (or there is none). */
    @Volatile
    private var cacheLoaded: Boolean = cache == null

    override fun observeGoals(): Flow<List<Goal>> =
        _goals.asStateFlow().onStart { loadCacheOnce() }

    override suspend fun clearCache() {
        _goals.value = emptyList()
        cache?.clear()
        cacheLoaded = true
    }

    /** Publish [goals] to the screens and keep the Room copy in step. */
    private suspend fun publish(goals: List<Goal>) {
        _goals.value = goals
        cache?.replaceAll(
            goals.map { CachedGoalEntity(it.id.toLong(), cacheJson.encodeToString(GoalDto.serializer(), it.toCacheDto())) }
        )
    }

    private suspend fun loadCacheOnce() {
        if (cacheLoaded) return
        cacheLoaded = true
        val cached = cache?.all().orEmpty().mapNotNull { row ->
            runCatching { cacheJson.decodeFromString(GoalDto.serializer(), row.json) }
                .getOrNull()
                ?.toDomainOrNull()
        }
        // A refresh that already landed is fresher than the cache.
        if (_goals.value.isEmpty() && cached.isNotEmpty()) {
            _goals.value = cached.sortedByDescending { it.createdAt }
        }
    }

    override suspend fun refresh(): ApiResult<Unit> {
        loadCacheOnce()
        return when (val result = apiCall { goalApi.list() }) {
            is ApiResult.Success -> {
                // mapNotNull: a goal this version can't read is skipped.
                publish(result.data
                    .mapNotNull { it.toDomainOrNull() }
                    .sortedByDescending { it.createdAt })
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    override suspend fun createGoal(
        title: String,
        activityType: ActivityType?,
        goalType: GoalType,
        targetValue: Double,
        startDate: LocalDate,
        durationDays: Int,
    ): ApiResult<Unit> {
        val body = buildCreateGoalRequest(
            title, activityType, goalType, targetValue, startDate, durationDays,
        )
        return when (val result = apiCall { goalApi.create(body) }) {
            is ApiResult.Success -> {
                val added = result.data.toDomainOrNull()
                    ?: return refresh().let { ApiResult.Success(Unit) }
                publish((listOf(added) + _goals.value.filterNot { it.id == added.id })
                    .sortedByDescending { it.createdAt })
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
        goalType: GoalType,
        targetValue: Double,
        durationDays: Int,
    ): ApiResult<Unit> {
        val body = buildUpdateGoalRequest(title, activityType, goalType, targetValue, durationDays)
        return when (val result = apiCall { goalApi.update(id.toLong(), body) }) {
            is ApiResult.Success -> {
                val updated = result.data.toDomainOrNull()
                    ?: return refresh().let { ApiResult.Success(Unit) }
                publish(_goals.value
                    .map { if (it.id == updated.id) updated else it }
                    .sortedByDescending { it.createdAt })
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    override suspend fun deleteGoal(id: String): ApiResult<Unit> =
        when (val result = apiCallEmpty { goalApi.delete(id.toLong()) }) {
            is ApiResult.Success -> {
                publish(_goals.value.filterNot { it.id == id })
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }

    override suspend fun markCompletedNotified(id: String): ApiResult<Unit> {
        return when (val result = apiCall { goalApi.markNotified(id.toLong()) }) {
            is ApiResult.Success -> {
                val updated = result.data.toDomainOrNull()
                    ?: return refresh().let { ApiResult.Success(Unit) }
                publish(_goals.value.map {
                    if (it.id == updated.id) updated else it
                })
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    /**
     * Making a goal the main also flips `is_main` on every other goal in
     * the local list to false, so the UI reflects the single-main
     * invariant without waiting for a refresh.
     */
    override suspend fun makeMainGoal(id: String): ApiResult<Unit> {
        return when (val result = apiCall { goalApi.makeMain(id.toLong()) }) {
            is ApiResult.Success -> {
                // Server returned the newly-main DTO; locally, clear every
                // other goal's isMain and replace the matched one.
                val newMain = result.data.toDomainOrNull()
                    ?: return refresh().let { ApiResult.Success(Unit) }
                publish(_goals.value.map { goal ->
                    when {
                        goal.id == newMain.id -> newMain
                        goal.isMain -> clearMainFlag(goal)
                        else -> goal
                    }
                })
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    override suspend fun extendGoal(id: String, days: Int): ApiResult<Unit> {
        val body = ExtendGoalRequestDto(days = days)
        return when (val result = apiCall { goalApi.extend(id.toLong(), body) }) {
            is ApiResult.Success -> {
                val updated = result.data.toDomainOrNull()
                    ?: return refresh().let { ApiResult.Success(Unit) }
                publish(_goals.value.map {
                    if (it.id == updated.id) updated else it
                })
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    /**
     * Rebuild a goal with `isMain = false` — we can't just `.copy()`
     * because `Goal` is abstract. One `when` per subclass; adding a new
     * subclass fails compilation here, which is what we want.
     */
    private fun clearMainFlag(goal: Goal): Goal = when (goal) {
        is com.elijah.fitness.data.model.DistanceGoal -> com.elijah.fitness.data.model.DistanceGoal(
            id = goal.id, title = goal.title, activityType = goal.activityType,
            targetValue = goal.targetValue, startDate = goal.startDate,
            durationDays = goal.durationDays, createdAt = goal.createdAt,
            completedNotified = goal.completedNotified, isMain = false,
        )
        is com.elijah.fitness.data.model.WorkoutCountGoal -> com.elijah.fitness.data.model.WorkoutCountGoal(
            id = goal.id, title = goal.title, activityType = goal.activityType,
            targetValue = goal.targetValue, startDate = goal.startDate,
            durationDays = goal.durationDays, createdAt = goal.createdAt,
            completedNotified = goal.completedNotified, isMain = false,
        )
        is com.elijah.fitness.data.model.DurationGoal -> com.elijah.fitness.data.model.DurationGoal(
            id = goal.id, title = goal.title, activityType = goal.activityType,
            targetValue = goal.targetValue, startDate = goal.startDate,
            durationDays = goal.durationDays, createdAt = goal.createdAt,
            completedNotified = goal.completedNotified, isMain = false,
        )
        is com.elijah.fitness.data.model.StrengthVolumeGoal -> com.elijah.fitness.data.model.StrengthVolumeGoal(
            id = goal.id, title = goal.title, activityType = goal.activityType,
            targetValue = goal.targetValue, startDate = goal.startDate,
            durationDays = goal.durationDays, createdAt = goal.createdAt,
            completedNotified = goal.completedNotified, isMain = false,
        )
        else -> error("Unknown Goal subclass: ${goal::class.simpleName}")
    }

    private companion object {
        val cacheJson = Json { ignoreUnknownKeys = true }
    }
}
