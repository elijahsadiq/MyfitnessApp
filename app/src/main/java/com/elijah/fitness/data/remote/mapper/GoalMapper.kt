package com.elijah.fitness.data.remote.mapper

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.DurationGoal
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.model.StrengthVolumeGoal
import com.elijah.fitness.data.model.WorkoutCountGoal
import com.elijah.fitness.data.remote.dto.CreateGoalRequestDto
import com.elijah.fitness.data.remote.dto.GoalDto
import com.elijah.fitness.data.remote.dto.UpdateGoalRequestDto
import java.time.Instant
import java.time.LocalDate

/**
 * Server DTO → domain [Goal] (phase-8b shape). The concrete subclass is
 * picked from [GoalDto.goalType]; `startDate` is parsed as a `LocalDate`
 * directly — the window is day-granularity on both sides.
 *
 * The server assigns numeric ids; the Kotlin model uses `String` (same
 * shape as [com.elijah.fitness.data.model.Workout.id]), so `.toString()`
 * bridges them. The mapper never trusts the server on invariants — it
 * lets the subclass `init { require(...) }` throw if the server sent a
 * nonsense combination like distance-on-weightlifting.
 */
fun GoalDto.toDomain(): Goal {
    val start = LocalDate.parse(startDate)
    val activity = activityType?.let { ActivityType.valueOf(it) }
    val created = Instant.parse(createdAt)

    return when (GoalType.fromWire(goalType)) {
        GoalType.DISTANCE -> DistanceGoal(
            id = id.toString(), title = title, activityType = activity,
            targetValue = targetValue, startDate = start, durationDays = durationDays,
            createdAt = created,
            completedNotified = completedNotified, isMain = isMain,
        )
        GoalType.COUNT -> WorkoutCountGoal(
            id = id.toString(), title = title, activityType = activity,
            targetValue = targetValue, startDate = start, durationDays = durationDays,
            createdAt = created,
            completedNotified = completedNotified, isMain = isMain,
        )
        GoalType.DURATION -> DurationGoal(
            id = id.toString(), title = title, activityType = activity,
            targetValue = targetValue, startDate = start, durationDays = durationDays,
            createdAt = created,
            completedNotified = completedNotified, isMain = isMain,
        )
        GoalType.VOLUME -> StrengthVolumeGoal(
            id = id.toString(), title = title, activityType = activity,
            targetValue = targetValue, startDate = start, durationDays = durationDays,
            createdAt = created,
            completedNotified = completedNotified, isMain = isMain,
        )
    }
}

/**
 * Build the create-request payload from the raw fields the user picked
 * in the "New goal" screen. The client supplies its local `startDate`
 * and the server validates it's within ±1 day of the server's today.
 */
fun buildCreateGoalRequest(
    title: String,
    activityType: ActivityType?,
    goalType: GoalType,
    targetValue: Double,
    startDate: LocalDate,
    durationDays: Int,
): CreateGoalRequestDto = CreateGoalRequestDto(
    title = title,
    activityType = activityType?.name,
    goalType = goalType.wire(),
    targetValue = targetValue,
    startDate = startDate.toString(),
    durationDays = durationDays,
)

fun buildUpdateGoalRequest(
    title: String,
    activityType: ActivityType?,
    goalType: GoalType,
    targetValue: Double,
    durationDays: Int,
): UpdateGoalRequestDto = UpdateGoalRequestDto(
    title = title,
    activityType = activityType?.name,
    goalType = goalType.wire(),
    targetValue = targetValue,
    durationDays = durationDays,
)

/** Concrete [Goal] subclass → wire enum value. Handy for the calculator + UI. */
fun Goal.goalType(): GoalType = when (this) {
    is DistanceGoal -> GoalType.DISTANCE
    is WorkoutCountGoal -> GoalType.COUNT
    is DurationGoal -> GoalType.DURATION
    is StrengthVolumeGoal -> GoalType.VOLUME
    else -> error("Unknown Goal subclass: ${this::class.simpleName}")
}

/**
 * Domain -> [GoalDto], for the on-phone goal cache. It's the inverse of
 * [toDomain]; `updatedAt` isn't kept on the domain side, so it repeats
 * `createdAt` (nothing reads it back).
 */
fun Goal.toCacheDto(): GoalDto = GoalDto(
    id = id.toLong(),
    title = title,
    activityType = activityType?.name,
    goalType = goalType().wire(),
    targetValue = targetValue,
    startDate = startDate.toString(),
    lastDay = lastDay.toString(),
    durationDays = durationDays,
    isMain = isMain,
    completedNotified = completedNotified,
    createdAt = createdAt.toString(),
    updatedAt = createdAt.toString(),
)
