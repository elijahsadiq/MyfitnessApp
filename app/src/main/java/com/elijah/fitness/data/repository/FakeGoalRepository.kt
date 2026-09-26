package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.DurationGoal
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalPeriod
import com.elijah.fitness.data.model.GoalPeriodResolver
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.model.StrengthVolumeGoal
import com.elijah.fitness.data.model.WorkoutCountGoal
import com.elijah.fitness.data.remote.ApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

/**
 * In-memory implementation for unit tests + offline development. Backed
 * by a [MutableStateFlow] so every observer sees the same list and is
 * notified atomically. Mirrors [FakeWorkoutRepository].
 *
 * The fake generates monotonically increasing ids so `createGoal` +
 * `deleteGoal` round-trips have stable identity — matches the server's
 * `AUTO_INCREMENT` semantics for tests that assert on `Goal.id`.
 */
class FakeGoalRepository(
    private val clock: Clock = Clock.systemUTC(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : GoalRepository {

    private val _goals: MutableStateFlow<List<Goal>> = MutableStateFlow(emptyList())
    private val nextId = AtomicLong(1)

    override fun observeGoals(): Flow<List<Goal>> = _goals.asStateFlow()

    /** In-memory backend has nothing to refresh — always succeeds instantly. */
    override suspend fun refresh(): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun createGoal(
        title: String,
        activityType: ActivityType?,
        goalType: GoalType,
        period: GoalPeriod,
        targetValue: Double,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): ApiResult<Unit> {
        val id = nextId.getAndIncrement().toString()
        val start = periodStart.atStartOfDay(zone).toInstant()
        val end = periodEnd.atStartOfDay(zone).toInstant()
        val now = clock.instant()
        val goal: Goal = when (goalType) {
            GoalType.DISTANCE -> DistanceGoal(id, title, activityType, targetValue,
                period, start, end, now)
            GoalType.COUNT -> WorkoutCountGoal(id, title, activityType, targetValue,
                period, start, end, now)
            GoalType.DURATION -> DurationGoal(id, title, activityType, targetValue,
                period, start, end, now)
            GoalType.VOLUME -> StrengthVolumeGoal(id, title, activityType, targetValue,
                period, start, end, now)
        }
        _goals.update { current ->
            (listOf(goal) + current).sortedByDescending { it.createdAt }
        }
        return ApiResult.Success(Unit)
    }

    override suspend fun updateGoal(
        id: String,
        title: String,
        activityType: ActivityType?,
        targetValue: Double,
    ): ApiResult<Unit> {
        val existing = _goals.value.firstOrNull { it.id == id }
            ?: return ApiResult.Error(message = "Goal not found", status = 404)

        // Rebuild with new fields — Goal fields are `val`, so we
        // construct a new instance of the same subclass.
        val replaced: Goal = when (existing) {
            is DistanceGoal -> DistanceGoal(id, title, activityType, targetValue,
                existing.period, existing.periodStart, existing.periodEnd, existing.createdAt)
            is WorkoutCountGoal -> WorkoutCountGoal(id, title, activityType, targetValue,
                existing.period, existing.periodStart, existing.periodEnd, existing.createdAt)
            is DurationGoal -> DurationGoal(id, title, activityType, targetValue,
                existing.period, existing.periodStart, existing.periodEnd, existing.createdAt)
            is StrengthVolumeGoal -> StrengthVolumeGoal(id, title, activityType, targetValue,
                existing.period, existing.periodStart, existing.periodEnd, existing.createdAt)
            else -> error("Unknown Goal subclass in fake repo: ${existing::class.simpleName}")
        }
        _goals.update { current -> current.map { if (it.id == id) replaced else it } }
        return ApiResult.Success(Unit)
    }

    override suspend fun deleteGoal(id: String): ApiResult<Unit> {
        val existed = _goals.value.any { it.id == id }
        if (!existed) return ApiResult.Error(message = "Goal not found", status = 404)
        _goals.update { current -> current.filterNot { it.id == id } }
        return ApiResult.Success(Unit)
    }

    override suspend fun markCompletedNotified(id: String): ApiResult<Unit> {
        val existing = _goals.value.firstOrNull { it.id == id }
            ?: return ApiResult.Error(message = "Goal not found", status = 404)
        val replaced: Goal = when (existing) {
            is DistanceGoal -> DistanceGoal(
                existing.id, existing.title, existing.activityType, existing.targetValue,
                existing.period, existing.periodStart, existing.periodEnd, existing.createdAt,
                completedNotified = true)
            is WorkoutCountGoal -> WorkoutCountGoal(
                existing.id, existing.title, existing.activityType, existing.targetValue,
                existing.period, existing.periodStart, existing.periodEnd, existing.createdAt,
                completedNotified = true)
            is DurationGoal -> DurationGoal(
                existing.id, existing.title, existing.activityType, existing.targetValue,
                existing.period, existing.periodStart, existing.periodEnd, existing.createdAt,
                completedNotified = true)
            is StrengthVolumeGoal -> StrengthVolumeGoal(
                existing.id, existing.title, existing.activityType, existing.targetValue,
                existing.period, existing.periodStart, existing.periodEnd, existing.createdAt,
                completedNotified = true)
            else -> error("Unknown Goal subclass in fake repo: ${existing::class.simpleName}")
        }
        _goals.update { current -> current.map { if (it.id == id) replaced else it } }
        return ApiResult.Success(Unit)
    }

    /**
     * Convenience for tests that want to seed a couple of goals aligned
     * with the current week / month without going through [createGoal].
     */
    fun seed(goals: List<Goal>) {
        _goals.value = goals.sortedByDescending { it.createdAt }
    }

    companion object {
        /**
         * Small helper for tests: build a "this week" `DistanceGoal` in the
         * device's local zone, using [GoalPeriodResolver] so boundaries
         * match production behaviour.
         */
        fun thisWeekDistanceGoal(
            id: String, title: String, activityType: ActivityType?,
            targetKm: Double, clock: Clock, zone: ZoneId,
        ): DistanceGoal {
            val bounds = GoalPeriodResolver.currentWeek(zone, clock)
            return DistanceGoal(
                id = id, title = title, activityType = activityType,
                targetValue = targetKm, period = GoalPeriod.THIS_WEEK,
                periodStart = bounds.start, periodEnd = bounds.endExclusive,
                createdAt = clock.instant().minus(1.milliseconds.inWholeMilliseconds,
                    java.time.temporal.ChronoUnit.MILLIS)
            )
        }
    }
}
