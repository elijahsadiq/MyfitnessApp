package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.DurationGoal
import com.elijah.fitness.data.model.Goal
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
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory implementation for unit tests + offline development. Backed
 * by a [MutableStateFlow] so every observer sees the same list and is
 * notified atomically. Mirrors [FakeWorkoutRepository].
 *
 * Monotonic ids keep `createGoal` / `deleteGoal` round-trips stable in
 * tests — matches the server's `AUTO_INCREMENT` semantics.
 */
class FakeGoalRepository(
    private val clock: Clock = Clock.systemUTC(),
) : GoalRepository {

    private val _goals: MutableStateFlow<List<Goal>> = MutableStateFlow(emptyList())
    private val nextId = AtomicLong(1)

    override fun observeGoals(): Flow<List<Goal>> = _goals.asStateFlow()

    override suspend fun refresh(): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun createGoal(
        title: String,
        activityType: ActivityType?,
        goalType: GoalType,
        targetValue: Double,
        startDate: LocalDate,
        durationDays: Int,
    ): ApiResult<Unit> {
        val id = nextId.getAndIncrement().toString()
        val now = clock.instant()
        val goal = buildGoal(
            id = id, title = title, activityType = activityType,
            goalType = goalType, targetValue = targetValue,
            startDate = startDate, durationDays = durationDays,
            createdAt = now, completedNotified = false, isMain = false,
        )
        _goals.update { current ->
            (listOf(goal) + current).sortedByDescending { it.createdAt }
        }
        return ApiResult.Success(Unit)
    }

    override suspend fun updateGoal(
        id: String,
        title: String,
        activityType: ActivityType?,
        goalType: GoalType,
        targetValue: Double,
        durationDays: Int,
    ): ApiResult<Unit> {
        val existing = _goals.value.firstOrNull { it.id == id }
            ?: return ApiResult.Error(message = "Goal not found", status = 404)
        val replaced = buildGoal(
            id = id, title = title, activityType = activityType,
            goalType = goalType, targetValue = targetValue,
            startDate = existing.startDate, durationDays = durationDays,
            createdAt = existing.createdAt,
            completedNotified = existing.completedNotified, isMain = existing.isMain,
        )
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
        val replaced = rebuild(existing, isMain = existing.isMain, completedNotified = true)
        _goals.update { current -> current.map { if (it.id == id) replaced else it } }
        return ApiResult.Success(Unit)
    }

    override suspend fun makeMainGoal(id: String): ApiResult<Unit> {
        val existing = _goals.value.firstOrNull { it.id == id }
            ?: return ApiResult.Error(message = "Goal not found", status = 404)
        _goals.update { current ->
            current.map { goal ->
                when {
                    goal.id == existing.id -> rebuild(goal, isMain = true)
                    goal.isMain -> rebuild(goal, isMain = false)
                    else -> goal
                }
            }
        }
        return ApiResult.Success(Unit)
    }

    override suspend fun extendGoal(id: String, days: Int): ApiResult<Unit> {
        val existing = _goals.value.firstOrNull { it.id == id }
            ?: return ApiResult.Error(message = "Goal not found", status = 404)
        val replaced = buildGoal(
            id = existing.id, title = existing.title, activityType = existing.activityType,
            goalType = goalTypeOf(existing), targetValue = existing.targetValue,
            startDate = existing.startDate,
            durationDays = (existing.durationDays + days).coerceAtMost(365),
            createdAt = existing.createdAt,
            completedNotified = existing.completedNotified, isMain = existing.isMain,
        )
        _goals.update { current -> current.map { if (it.id == id) replaced else it } }
        return ApiResult.Success(Unit)
    }

    /** Seed goals directly — bypasses [createGoal]'s id generator. */
    fun seed(goals: List<Goal>) {
        _goals.value = goals.sortedByDescending { it.createdAt }
    }

    // -----------------------------------------------------------------
    // Builders — one place that knows every subclass's constructor,
    // called from create/update/mark/extend.
    // -----------------------------------------------------------------

    private fun buildGoal(
        id: String, title: String, activityType: ActivityType?,
        goalType: GoalType, targetValue: Double,
        startDate: LocalDate, durationDays: Int,
        createdAt: java.time.Instant,
        completedNotified: Boolean, isMain: Boolean,
    ): Goal = when (goalType) {
        GoalType.DISTANCE -> DistanceGoal(id, title, activityType, targetValue,
            startDate, durationDays, createdAt, completedNotified, isMain)
        GoalType.COUNT -> WorkoutCountGoal(id, title, activityType, targetValue,
            startDate, durationDays, createdAt, completedNotified, isMain)
        GoalType.DURATION -> DurationGoal(id, title, activityType, targetValue,
            startDate, durationDays, createdAt, completedNotified, isMain)
        GoalType.VOLUME -> StrengthVolumeGoal(id, title, activityType, targetValue,
            startDate, durationDays, createdAt, completedNotified, isMain)
    }

    private fun rebuild(
        goal: Goal,
        isMain: Boolean = goal.isMain,
        completedNotified: Boolean = goal.completedNotified,
    ): Goal = buildGoal(
        id = goal.id, title = goal.title, activityType = goal.activityType,
        goalType = goalTypeOf(goal), targetValue = goal.targetValue,
        startDate = goal.startDate, durationDays = goal.durationDays,
        createdAt = goal.createdAt,
        completedNotified = completedNotified, isMain = isMain,
    )

    private fun goalTypeOf(goal: Goal): GoalType = when (goal) {
        is DistanceGoal -> GoalType.DISTANCE
        is WorkoutCountGoal -> GoalType.COUNT
        is DurationGoal -> GoalType.DURATION
        is StrengthVolumeGoal -> GoalType.VOLUME
        else -> error("Unknown Goal subclass: ${goal::class.simpleName}")
    }
}
