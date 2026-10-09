package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.remote.ApiResult
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Read/write access to the user's goals. Same shape as
 * [WorkoutRepository]: a hot [observeGoals] flow the ViewModels collect,
 * plus explicit suspend mutators that return [ApiResult] so the UI can
 * render loading / error / retry states without observing exceptions.
 *
 * The server assigns numeric ids; on the domain side [Goal.id] carries
 * the same value as a `String` (mirrors the workout repository's
 * clientUuid convention).
 *
 * Phase-8b additions:
 *   - [makeMainGoal] moves the "main" flag to one goal (transactional
 *     on the server, so sibling goals' `is_main` is cleared atomically).
 *   - [extendGoal] adds days to an active goal (rejected server-side
 *     for ended goals and for extensions that push past 365 days).
 */
interface GoalRepository {

    /** Emits the current goals list whenever it changes. Newest first. */
    fun observeGoals(): Flow<List<Goal>>

    /** Fetch the latest list from the source of truth. */
    suspend fun refresh(): ApiResult<Unit>

    /**
     * Create a goal. The raw fields are what the "New goal" screen
     * collects; the server assigns the id and returns the full [Goal]
     * which is pushed into [observeGoals] on success. The client supplies
     * its local [startDate] — the server validates it's within ±1 day of
     * the server's today.
     */
    suspend fun createGoal(
        title: String,
        activityType: ActivityType?,
        goalType: GoalType,
        targetValue: Double,
        startDate: LocalDate,
        durationDays: Int,
    ): ApiResult<Unit>

    /**
     * Update the editable fields of an existing goal. [startDate] is
     * immutable — changing it would invalidate already-shown progress
     * against the original window — but [durationDays] can grow or
     * shrink; the server recomputes `last_day` from the new span.
     */
    suspend fun updateGoal(
        id: String,
        title: String,
        activityType: ActivityType?,
        goalType: GoalType,
        targetValue: Double,
        durationDays: Int,
    ): ApiResult<Unit>

    suspend fun deleteGoal(id: String): ApiResult<Unit>

    /**
     * Persist the "we've already celebrated this completion" flag on
     * the server + patch the local flow so subsequent snapshots have
     * `completedNotified = true`. Idempotent — calling twice is fine.
     */
    suspend fun markCompletedNotified(id: String): ApiResult<Unit>

    /** Make [id] the user's main goal, clearing is_main on every other goal. */
    suspend fun makeMainGoal(id: String): ApiResult<Unit>

    /** Add [days] to an active goal, up to the 365-day server-side cap. */
    suspend fun extendGoal(id: String, days: Int): ApiResult<Unit>

    /** Forget every cached goal (sign-out). */
    fun clearCache() {}
}
