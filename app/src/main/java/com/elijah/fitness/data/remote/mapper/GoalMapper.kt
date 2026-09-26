package com.elijah.fitness.data.remote.mapper

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.DurationGoal
import com.elijah.fitness.data.model.Goal
import com.elijah.fitness.data.model.GoalPeriod
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.model.StrengthVolumeGoal
import com.elijah.fitness.data.model.WorkoutCountGoal
import com.elijah.fitness.data.remote.dto.CreateGoalRequestDto
import com.elijah.fitness.data.remote.dto.GoalDto
import com.elijah.fitness.data.remote.dto.UpdateGoalRequestDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Server DTO → domain [Goal]. The concrete subclass is picked from
 * [GoalDto.goalType]; `periodStart` / `periodEnd` DATE strings are
 * interpreted as midnight in [zone] so a mid-day workout at the same
 * date falls inside the window.
 *
 * The server assigns numeric ids; the Kotlin model uses `String` (same
 * shape as [com.elijah.fitness.data.model.Workout.id]), so `.toString()`
 * bridges them. The mapper never trusts the server on invariants — it
 * lets the subclass `init { require(...) }` throw if the server sent a
 * nonsense combination like distance-on-weightlifting.
 */
fun GoalDto.toDomain(zone: ZoneId = ZoneId.systemDefault()): Goal {
    val period = GoalPeriod.valueOf(period.uppercase().let { if (it == "WEEK") "THIS_WEEK" else "THIS_MONTH" })
    val start = LocalDate.parse(periodStart).atStartOfDay(zone).toInstant()
    val end   = LocalDate.parse(periodEnd).atStartOfDay(zone).toInstant()
    val activity = activityType?.let { ActivityType.valueOf(it) }
    val created = Instant.parse(createdAt)

    return when (GoalType.fromWire(goalType)) {
        GoalType.DISTANCE -> DistanceGoal(
            id = id.toString(), title = title, activityType = activity,
            targetValue = targetValue, period = period,
            periodStart = start, periodEnd = end, createdAt = created,
            completedNotified = completedNotified,
        )
        GoalType.COUNT -> WorkoutCountGoal(
            id = id.toString(), title = title, activityType = activity,
            targetValue = targetValue, period = period,
            periodStart = start, periodEnd = end, createdAt = created,
            completedNotified = completedNotified,
        )
        GoalType.DURATION -> DurationGoal(
            id = id.toString(), title = title, activityType = activity,
            targetValue = targetValue, period = period,
            periodStart = start, periodEnd = end, createdAt = created,
            completedNotified = completedNotified,
        )
        GoalType.VOLUME -> StrengthVolumeGoal(
            id = id.toString(), title = title, activityType = activity,
            targetValue = targetValue, period = period,
            periodStart = start, periodEnd = end, createdAt = created,
            completedNotified = completedNotified,
        )
    }
}

/**
 * Build the create-request payload from the raw fields the user picked
 * in the "New goal" bottom sheet (step 5). We can't derive it from an
 * already-constructed [Goal] because the server assigns the id.
 */
fun buildCreateGoalRequest(
    title: String,
    activityType: ActivityType?,
    goalType: GoalType,
    period: GoalPeriod,
    targetValue: Double,
    periodStart: LocalDate,
    periodEnd: LocalDate,
): CreateGoalRequestDto = CreateGoalRequestDto(
    title = title,
    activityType = activityType?.name,
    goalType = goalType.wire(),
    period = when (period) {
        GoalPeriod.THIS_WEEK -> "WEEK"
        GoalPeriod.THIS_MONTH -> "MONTH"
    },
    targetValue = targetValue,
    periodStart = periodStart.toString(),
    periodEnd = periodEnd.toString(),
)

fun buildUpdateGoalRequest(
    title: String,
    activityType: ActivityType?,
    targetValue: Double,
): UpdateGoalRequestDto = UpdateGoalRequestDto(
    title = title,
    activityType = activityType?.name,
    targetValue = targetValue,
)

/** Concrete [Goal] subclass → wire enum value. Handy for the calculator + UI. */
fun Goal.goalType(): GoalType = when (this) {
    is DistanceGoal -> GoalType.DISTANCE
    is WorkoutCountGoal -> GoalType.COUNT
    is DurationGoal -> GoalType.DURATION
    is StrengthVolumeGoal -> GoalType.VOLUME
    else -> error("Unknown Goal subclass: ${this::class.simpleName}")
}
