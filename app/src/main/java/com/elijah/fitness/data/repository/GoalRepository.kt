package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalPeriod
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
 */
interface GoalRepository {

    /** Emits the current goals list whenever it changes. Newest first. */
    fun observeGoals(): Flow<List<Goal>>

    /** Fetch the latest list from the source of truth. */
    suspend fun refresh(): ApiResult<Unit>

    /**
     * Create a goal. The raw fields are what the "New goal" bottom sheet
     * (Step 5) collects; the server assigns the id and returns a full
     * [Goal] which is pushed into [observeGoals] on success.
     */
    suspend fun createGoal(
        title: String,
        activityType: ActivityType?,
        goalType: GoalType,
        period: GoalPeriod,
        targetValue: Double,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): ApiResult<Unit>

    /**
     * Update the editable fields of an existing goal. Period + dates
     * are immutable — changing them would invalidate the already-shown
     * progress against the original window.
     */
    suspend fun updateGoal(
        id: String,
        title: String,
        activityType: ActivityType?,
        targetValue: Double,
    ): ApiResult<Unit>

    suspend fun deleteGoal(id: String): ApiResult<Unit>

    /**
     * Persist the "we've already celebrated this completion" flag on
     * the server + patch the local flow so subsequent snapshots have
     * `completedNotified = true`. Idempotent — calling twice is fine.
     */
    suspend fun markCompletedNotified(id: String): ApiResult<Unit>
}
