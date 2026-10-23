package com.elijah.fitness.data.remote.mapper

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.RowingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.SwimmingWorkout
import com.elijah.fitness.data.remote.dto.WorkoutDto
import com.elijah.fitness.tracking.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WorkoutMapperTest {

    private fun dto(
        type: String,
        distance: Double? = 1_500.0,
        laps: Int? = null,
        pool: Int? = null,
        kcal: Int? = null,
        start: String = "2026-10-18T07:00:00Z",
    ) = WorkoutDto(
        id = 7, clientUuid = "uuid-$type", activityType = type, startTime = start,
        durationSeconds = 1_935, distanceMeters = distance, poolLengthM = pool, laps = laps,
        caloriesKcal = kcal, createdAt = start, updatedAt = start,
    )

    @Test
    fun `swim round trip keeps laps, pool length and calories`() {
        val workout = dto("SWIMMING", laps = 60, pool = 25, kcal = 270).toDomain()
        assertTrue(workout is SwimmingWorkout)
        val swim = workout as SwimmingWorkout
        assertEquals(60, swim.laps)
        assertEquals(25, swim.poolLengthM)
        assertEquals(270, swim.caloriesKcal)
        assertEquals(1_500.0, swim.distanceMeters, 0.0)

        val request = swim.toRequestDto()
        assertEquals("SWIMMING", request.activityType)
        assertEquals(60, request.laps)
        assertEquals(25, request.poolLengthM)
        assertEquals(270, request.caloriesKcal)
        assertEquals(1_500.0, request.distanceMeters!!, 0.0)
    }

    @Test
    fun `row round trip keeps calories and its route`() {
        val workout = dto("ROWING", distance = 6_000.0, kcal = 345).toDomain()
        assertTrue(workout is RowingWorkout)
        val point = TrackPoint(0, 51.5, -0.1, 0L, 5.0, Instant.parse("2026-10-18T07:00:00Z"))
        val request = workout!!.toRequestDto(listOf(point))
        assertEquals("ROWING", request.activityType)
        assertEquals(345, request.caloriesKcal)
        assertEquals(1, request.routePoints.size)
        assertNull(request.laps)
        assertNull(request.poolLengthM)
    }

    @Test
    fun `swims never send a route and other types never send laps`() {
        val swim = SwimmingWorkout("s", Instant.EPOCH, Duration.ofMinutes(30), 1_000.0, laps = 40, poolLengthM = 25)
        val point = TrackPoint(0, 51.5, -0.1, 0L, 5.0, Instant.EPOCH)
        assertTrue(swim.toRequestDto(listOf(point)).routePoints.isEmpty())

        val run = RunningWorkout("r", Instant.EPOCH, Duration.ofMinutes(30), 5_000.0)
        val req = run.toRequestDto()
        assertNull(req.laps)
        assertNull(req.poolLengthM)
        assertNull(req.caloriesKcal)
    }

    @Test
    fun `pool and laps the server would reject are left out, the swim still uploads`() {
        fun swim(laps: Int?, pool: Int?) = SwimmingWorkout(
            "s", Instant.EPOCH, Duration.ofMinutes(30), 1_500.0, laps = laps, poolLengthM = pool,
        )
        // Only one of the pair: the server wants both together.
        listOf(swim(60, null), swim(null, 25), swim(0, 25), swim(401, 25), swim(60, 9), swim(60, 101))
            .forEach { bad ->
                val req = bad.toRequestDto()
                assertNull(req.laps)
                assertNull(req.poolLengthM)
                assertEquals("SWIMMING", req.activityType)
                assertEquals(1_500.0, req.distanceMeters!!, 0.0)
            }
        val ok = swim(400, 100).toRequestDto()
        assertEquals(400, ok.laps)
        assertEquals(100, ok.poolLengthM)
    }

    @Test
    fun `calories outside the server's range are left out`() {
        fun run(kcal: Int?) = RunningWorkout("r", Instant.EPOCH, Duration.ofMinutes(30), 5_000.0, caloriesKcal = kcal)
        assertNull(run(-1).toRequestDto().caloriesKcal)
        assertNull(run(20_001).toRequestDto().caloriesKcal)
        assertEquals(0, run(0).toRequestDto().caloriesKcal)
        assertEquals(20_000, run(20_000).toRequestDto().caloriesKcal)
    }

    @Test
    fun `an unknown activity type is skipped, not a crash`() {
        assertNull(dto("HIKING").toDomain())
        assertNull(dto("running").toDomain()) // values are upper case on the wire
        val list = listOf(dto("RUNNING"), dto("HIKING"), dto("ROWING")).mapNotNull { it.toDomain() }
        assertEquals(listOf(ActivityType.RUNNING, ActivityType.ROWING), list.map { it.type })
    }

    @Test
    fun `an unreadable start time is skipped too`() {
        assertNull(dto("RUNNING", start = "yesterday").toDomain())
    }

    @Test
    fun `older replies without the new fields still parse`() {
        val old = dto("RUNNING", distance = 5_000.0).toDomain()!!
        assertNull(old.caloriesKcal)
    }

    @Test
    fun `every activity type maps to its own class`() {
        ActivityType.entries.forEach { type ->
            val w = dto(type.name).toDomain()
            assertEquals(type, w?.type)
        }
    }
}
