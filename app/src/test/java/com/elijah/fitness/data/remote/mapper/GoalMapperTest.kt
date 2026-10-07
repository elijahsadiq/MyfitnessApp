package com.elijah.fitness.data.remote.mapper

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.DistanceGoal
import com.elijah.fitness.data.model.DurationGoal
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.model.StrengthVolumeGoal
import com.elijah.fitness.data.model.WorkoutCountGoal
import com.elijah.fitness.data.remote.dto.GoalDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalMapperTest {

    private fun dto(
        id: Long = 1L,
        title: String = "T",
        activityType: String? = null,
        goalType: String,
        target: Double = 20.0,
        startDate: String = "2026-09-14",
        lastDay: String = "2026-09-20",
        durationDays: Int = 7,
        isMain: Boolean = false,
        completedNotified: Boolean = false,
    ) = GoalDto(
        id = id, title = title, activityType = activityType, goalType = goalType,
        targetValue = target,
        startDate = startDate, lastDay = lastDay, durationDays = durationDays,
        isMain = isMain, completedNotified = completedNotified,
        createdAt = "2026-09-14T09:00:00Z", updatedAt = "2026-09-14T09:00:00Z",
    )

    @Test
    fun `distance goal maps to DistanceGoal with RUNNING`() {
        val g = dto(goalType = "distance", activityType = "RUNNING").toDomain()
        assertTrue(g is DistanceGoal)
        assertEquals("1", g.id)
        assertEquals(ActivityType.RUNNING, g.activityType)
        assertEquals(20.0, g.targetValue, 1e-6)
        assertEquals(7, g.durationDays)
        assertEquals(LocalDate.parse("2026-09-14"), g.startDate)
        assertEquals(LocalDate.parse("2026-09-20"), g.lastDay)
        assertEquals("km", g.unitLabel)
        assertFalse(g.isMain)
    }

    @Test
    fun `count goal maps to WorkoutCountGoal with any activity`() {
        val g = dto(goalType = "count", activityType = null, durationDays = 30,
            lastDay = "2026-10-13").toDomain()
        assertTrue(g is WorkoutCountGoal)
        assertNull(g.activityType)
        assertEquals(30, g.durationDays)
    }

    @Test
    fun `duration goal maps to DurationGoal`() {
        val g = dto(goalType = "duration", target = 150.0).toDomain()
        assertTrue(g is DurationGoal)
        assertEquals(150.0, g.targetValue, 1e-6)
        assertEquals("min", g.unitLabel)
    }

    @Test
    fun `volume goal maps to StrengthVolumeGoal`() {
        val g = dto(
            goalType = "volume", activityType = "WEIGHTLIFTING",
            target = 5000.0, durationDays = 30, lastDay = "2026-10-13",
        ).toDomain()
        assertTrue(g is StrengthVolumeGoal)
        assertEquals("kg", g.unitLabel)
    }

    @Test
    fun `isMain flag passes through from DTO`() {
        val g = dto(goalType = "distance", activityType = "RUNNING", isMain = true).toDomain()
        assertTrue(g.isMain)
    }

    @Test
    fun `mapper preserves invariants — server-sent nonsense throws at construction`() {
        // Distance + WEIGHTLIFTING is forbidden by DistanceGoal.init.
        val bad = dto(goalType = "distance", activityType = "WEIGHTLIFTING")
        val thrown = runCatching { bad.toDomain() }
        assertTrue(thrown.isFailure)
        assertTrue(thrown.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `Goal_goalType extension returns the right enum for each subclass`() {
        val distance = dto(goalType = "distance", activityType = "RUNNING").toDomain()
        val count    = dto(goalType = "count").toDomain()
        val duration = dto(goalType = "duration").toDomain()
        val volume   = dto(goalType = "volume", activityType = "WEIGHTLIFTING").toDomain()
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
