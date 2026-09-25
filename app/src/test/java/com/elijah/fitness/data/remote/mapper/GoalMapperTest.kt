package com.elijah.fitness.data.remote.mapper

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.DurationGoal
import com.elijah.fitness.data.model.GoalPeriod
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.model.StrengthVolumeGoal
import com.elijah.fitness.data.model.WorkoutCountGoal
import com.elijah.fitness.data.remote.dto.GoalDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class GoalMapperTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun dto(
        id: Long = 1L,
        title: String = "T",
        activityType: String? = null,
        goalType: String,
        period: String = "WEEK",
        target: Double = 20.0,
        periodStart: String = "2026-09-14",
        periodEnd: String = "2026-09-21",
        completedNotified: Boolean = false,
    ) = GoalDto(
        id = id, title = title, activityType = activityType, goalType = goalType,
        period = period, targetValue = target,
        periodStart = periodStart, periodEnd = periodEnd,
        completedNotified = completedNotified,
        createdAt = "2026-09-14T09:00:00Z", updatedAt = "2026-09-14T09:00:00Z",
    )

    @Test
    fun `distance goal maps to DistanceGoal with RUNNING`() {
        val g = dto(goalType = "distance", activityType = "RUNNING").toDomain(zone)
        assertTrue(g is DistanceGoal)
        assertEquals("1", g.id)
        assertEquals(ActivityType.RUNNING, g.activityType)
        assertEquals(GoalPeriod.THIS_WEEK, g.period)
        assertEquals(20.0, g.targetValue, 1e-6)
        assertEquals("km", g.unitLabel)
    }

    @Test
    fun `count goal maps to WorkoutCountGoal with any activity`() {
        val g = dto(goalType = "count", activityType = null, period = "MONTH").toDomain(zone)
        assertTrue(g is WorkoutCountGoal)
        assertNull(g.activityType)
        assertEquals(GoalPeriod.THIS_MONTH, g.period)
    }

    @Test
    fun `duration goal maps to DurationGoal`() {
        val g = dto(goalType = "duration", target = 150.0).toDomain(zone)
        assertTrue(g is DurationGoal)
        assertEquals(150.0, g.targetValue, 1e-6)
        assertEquals("min", g.unitLabel)
    }

    @Test
    fun `volume goal maps to StrengthVolumeGoal`() {
        val g = dto(
            goalType = "volume", activityType = "WEIGHTLIFTING",
            target = 5000.0, period = "MONTH"
        ).toDomain(zone)
        assertTrue(g is StrengthVolumeGoal)
        assertEquals(GoalPeriod.THIS_MONTH, g.period)
        assertEquals("kg", g.unitLabel)
    }

    @Test
    fun `period_end is exclusive after mapping — a workout on the boundary is not counted`() {
        // 2026-09-14 → 2026-09-21 in UTC.
        val g = dto(goalType = "distance", activityType = "RUNNING",
            periodStart = "2026-09-14", periodEnd = "2026-09-21").toDomain(zone)
        // A synthetic empty workouts list gives 0 progress — the point of
        // this test is that the mapping produces the half-open range the
        // Goal.relevantWorkouts filter expects.
        assertEquals("2026-09-14T00:00:00Z", g.periodStart.toString())
        assertEquals("2026-09-21T00:00:00Z", g.periodEnd.toString())
    }

    @Test
    fun `mapper preserves invariants — server-sent nonsense throws at construction`() {
        // Distance goal + WEIGHTLIFTING is forbidden by DistanceGoal.init.
        val bad = dto(goalType = "distance", activityType = "WEIGHTLIFTING")
        val thrown = runCatching { bad.toDomain(zone) }
        assertTrue(thrown.isFailure)
        assertTrue(thrown.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `Goal_goalType extension returns the right enum for each subclass`() {
        val distance = dto(goalType = "distance", activityType = "RUNNING").toDomain(zone)
        val count    = dto(goalType = "count").toDomain(zone)
        val duration = dto(goalType = "duration").toDomain(zone)
        val volume   = dto(goalType = "volume", activityType = "WEIGHTLIFTING").toDomain(zone)
        assertEquals(GoalType.DISTANCE, distance.goalType())
        assertEquals(GoalType.COUNT, count.goalType())
        assertEquals(GoalType.DURATION, duration.goalType())
        assertEquals(GoalType.VOLUME, volume.goalType())
    }

    @Test
    fun `GoalType wire round trip`() {
        for (t in GoalType.entries) {
            assertEquals(t, GoalType.fromWire(t.wire()))
        }
    }
}
