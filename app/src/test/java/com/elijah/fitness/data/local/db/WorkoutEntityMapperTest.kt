package com.elijah.fitness.data.local.db

import com.elijah.fitness.data.model.RowingWorkout
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.SwimmingWorkout
import com.elijah.fitness.data.remote.dto.WorkoutDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Phase 8d: the three new Room columns (database version 2). */
class WorkoutEntityMapperTest {

    private val t0: Instant = Instant.parse("2026-10-18T07:00:00Z")

    @Test
    fun `swim round trip through Room keeps laps, pool length and calories`() {
        val swim = SwimmingWorkout(
            id = "s1", startTime = t0, duration = Duration.ofSeconds(1_935), distanceMeters = 1_500.0,
            notes = "Pool", poolLengthM = 25, laps = 60, caloriesKcal = 270,
        )
        val entity = swim.toEntity(SyncState.PENDING_CREATE, routeCached = true)
        assertEquals("SWIMMING", entity.activityType)
        assertEquals(60, entity.laps)
        assertEquals(25, entity.poolLengthM)
        assertEquals(270, entity.caloriesKcal)

        val back = WorkoutWithSets(entity, emptyList()).toDomain()
        assertEquals(swim, back)
    }

    @Test
    fun `row and lift keep their calories and leave the swim columns empty`() {
        val row = RowingWorkout("r1", t0, Duration.ofMinutes(31), 6_000.0, caloriesKcal = 345)
        val rowEntity = row.toEntity(SyncState.SYNCED, routeCached = true)
        assertNull(rowEntity.laps)
        assertNull(rowEntity.poolLengthM)
        assertEquals(345, rowEntity.caloriesKcal)
        assertEquals(row, WorkoutWithSets(rowEntity, emptyList()).toDomain())

        val lift = StrengthWorkout("l1", t0, Duration.ofMinutes(45), listOf(StrengthSet("Squat", 5, 100.0)), caloriesKcal = 190)
        val liftEntity = lift.toEntity(SyncState.SYNCED, routeCached = true)
        assertEquals(lift, WorkoutWithSets(liftEntity, lift.setEntities()).toDomain())
    }

    @Test
    fun `a row from before version 2 reads its new columns as null`() {
        val legacy = WorkoutEntity(
            clientUuid = "old", serverId = 1, activityType = "RUNNING", startTimeMs = t0.toEpochMilli(),
            durationSeconds = 1_800, distanceMeters = 5_000.0, notes = null,
            syncState = SyncState.SYNCED, routeCached = false,
        )
        assertNull(legacy.caloriesKcal)
        assertNull(WorkoutWithSets(legacy, emptyList()).toDomain()!!.caloriesKcal)
    }

    @Test
    fun `an unknown type in the database is skipped`() {
        val odd = WorkoutEntity(
            clientUuid = "x", serverId = 1, activityType = "HIKING", startTimeMs = 0,
            durationSeconds = 60, distanceMeters = 1_000.0, notes = null,
            syncState = SyncState.SYNCED, routeCached = false,
        )
        assertNull(WorkoutWithSets(odd, emptyList()).toDomain())
    }

    @Test
    fun `server copies - swims count as route cached, rows do not`() {
        fun dto(type: String) = WorkoutDto(
            id = 9, clientUuid = "u-$type", activityType = type, startTime = "2026-10-18T07:00:00Z",
            durationSeconds = 600, distanceMeters = 1_000.0, laps = if (type == "SWIMMING") 40 else null,
            poolLengthM = if (type == "SWIMMING") 25 else null, caloriesKcal = 100,
            createdAt = "2026-10-18T07:00:00Z", updatedAt = "2026-10-18T07:00:00Z",
        )
        val swim = dto("SWIMMING").toEntity()!!
        assertTrue(swim.routeCached)
        assertEquals(40, swim.laps)
        assertEquals(25, swim.poolLengthM)
        assertEquals(100, swim.caloriesKcal)
        assertFalse(dto("ROWING").toEntity()!!.routeCached)
        assertTrue(dto("WEIGHTLIFTING").toEntity()!!.routeCached)
        assertFalse(dto("HIKING").toEntity()!!.routeCached)
    }

    @Test
    fun `a server copy with an unreadable start time is skipped`() {
        val bad = WorkoutDto(
            id = 9, clientUuid = "bad", activityType = "RUNNING", startTime = "not a date",
            durationSeconds = 600, createdAt = "x", updatedAt = "x",
        )
        assertNull(bad.toEntity())
    }
}
