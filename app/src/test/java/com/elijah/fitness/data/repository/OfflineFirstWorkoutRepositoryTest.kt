package com.elijah.fitness.data.repository

import com.elijah.fitness.data.local.db.FakeWorkoutDao
import com.elijah.fitness.data.local.db.SyncState
import com.elijah.fitness.data.model.RowingWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.SwimmingWorkout
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.remote.api.WorkoutApi
import com.elijah.fitness.data.remote.dto.ApiEnvelope
import com.elijah.fitness.data.remote.dto.RoutePointDto
import com.elijah.fitness.data.remote.dto.StrengthSetDto
import com.elijah.fitness.data.remote.dto.WorkoutDto
import com.elijah.fitness.data.remote.dto.WorkoutRequestDto
import com.elijah.fitness.data.sync.SyncScheduler
import com.elijah.fitness.tracking.TrackPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Duration
import java.time.Instant

class OfflineFirstWorkoutRepositoryTest {

    private val dao = FakeWorkoutDao()
    private val server = FakeServer()
    private val scheduler = CountingScheduler()

    private fun TestScope.repo() = OfflineFirstWorkoutRepository(
        dao = dao,
        api = server,
        syncScheduler = scheduler,
        scope = backgroundScope,
        isOnline = { server.online },
    )

    // --------- Phase 8d ---------

    @Test
    fun `a swim saved offline uploads laps, pool length and calories`() = runTest {
        server.online = false
        val repo = repo()
        val swim = SwimmingWorkout(
            id = "sw1", startTime = Instant.parse("2026-10-18T07:00:00Z"),
            duration = Duration.ofSeconds(1_935), distanceMeters = 1_500.0,
            poolLengthM = 25, laps = 60, caloriesKcal = 270,
        )
        repo.addWorkout(swim)
        assertEquals(swim, repo.observeWorkouts().first().single())

        server.online = true
        repo.syncPending()

        val uploaded = server.rows.getValue("sw1")
        assertEquals("SWIMMING", uploaded.activityType)
        assertEquals(60, uploaded.laps)
        assertEquals(25, uploaded.poolLengthM)
        assertEquals(270, uploaded.caloriesKcal)
        assertEquals(SyncState.SYNCED, dao.row("sw1")?.syncState)
    }

    @Test
    fun `a refresh from a server without the new fields keeps the stored calories, laps and pool`() = runTest {
        val repo = repo()
        val swim = SwimmingWorkout(
            id = "sw2", startTime = Instant.parse("2026-10-18T07:00:00Z"),
            duration = Duration.ofSeconds(1_930), distanceMeters = 1_500.0,
            poolLengthM = 25, laps = 60, caloriesKcal = 270,
        )
        repo.addWorkout(swim)
        assertEquals(SyncState.SYNCED, dao.row("sw2")?.syncState)
        // A backend from before migration 08 doesn't keep the new columns.
        server.rows["sw2"] = server.rows.getValue("sw2").copy(caloriesKcal = null, laps = null, poolLengthM = null)

        assertTrue(repo.refresh() is ApiResult.Success)

        val shown = repo.observeWorkouts().first().single() as SwimmingWorkout
        assertEquals(270, shown.caloriesKcal)
        assertEquals(60, shown.laps)
        assertEquals(25, shown.poolLengthM)
    }

    @Test
    fun `a refresh still takes the server's values when it has them`() = runTest {
        val repo = repo()
        repo.addWorkout(run("r9"))
        server.rows["r9"] = server.rows.getValue("r9").copy(caloriesKcal = 512)

        assertTrue(repo.refresh() is ApiResult.Success)

        assertEquals(512, repo.observeWorkouts().first().single().caloriesKcal)
    }

    @Test
    fun `a server workout of an unknown type is skipped, the rest still show`() = runTest {
        server.seed(
            WorkoutDto(
                id = 0, clientUuid = "hike", activityType = "HIKING",
                startTime = "2026-10-18T07:00:00Z", durationSeconds = 3_600, distanceMeters = 8_000.0,
                createdAt = "2026-10-18T07:00:00Z", updatedAt = "2026-10-18T07:00:00Z",
            )
        )
        server.seed(
            WorkoutDto(
                id = 0, clientUuid = "row", activityType = "ROWING",
                startTime = "2026-10-18T08:00:00Z", durationSeconds = 1_860, distanceMeters = 6_000.0,
                caloriesKcal = 345,
                createdAt = "2026-10-18T08:00:00Z", updatedAt = "2026-10-18T08:00:00Z",
            )
        )
        val repo = repo()

        assertTrue(repo.refresh() is ApiResult.Success)

        val shown = repo.observeWorkouts().first()
        assertEquals(listOf("row"), shown.map { it.id })
        assertTrue(shown.single() is RowingWorkout)
        assertEquals(345, shown.single().caloriesKcal)
        assertNull(repo.getWorkout("hike"))
    }

    @Test
    fun `offline save shows at once as unsynced and queues a sync`() = runTest {
        server.online = false
        val repo = repo()

        val result = repo.addWorkout(run("r1"))

        assertTrue(result is ApiResult.Success)
        assertEquals(listOf("r1"), repo.observeWorkouts().first().map { it.id })
        assertEquals(setOf("r1"), repo.observeUnsyncedIds().first())
        assertEquals(1, scheduler.scheduled)
        assertTrue(server.rows.isEmpty())
    }

    @Test
    fun `sync after reconnecting uploads the workout once and marks it synced`() = runTest {
        server.online = false
        val repo = repo()
        repo.addWorkout(run("r1"), route(points = 3))

        server.online = true
        val result = repo.syncPending()

        assertTrue(result is ApiResult.Success)
        assertEquals(1, server.rows.size)
        assertEquals(3, server.rows.getValue("r1").routePoints.size)
        assertEquals(SyncState.SYNCED, dao.row("r1")?.syncState)
        assertTrue(repo.observeUnsyncedIds().first().isEmpty())
    }

    @Test
    fun `online save uploads straight away without queueing`() = runTest {
        val repo = repo()

        repo.addWorkout(lift("s1"))

        assertEquals(SyncState.SYNCED, dao.row("s1")?.syncState)
        assertEquals(1, server.rows.size)
        assertEquals(0, scheduler.scheduled)
    }

    @Test
    fun `a lost reply then a retry still leaves one copy on the server`() = runTest {
        server.loseNextReply = true
        val repo = repo()
        repo.addWorkout(run("r1"))
        assertEquals(SyncState.PENDING_CREATE, dao.row("r1")?.syncState)

        repo.syncPending()

        assertEquals(1, server.rows.size)
        assertEquals(2, server.createCalls)
        assertEquals(SyncState.SYNCED, dao.row("r1")?.syncState)
    }

    @Test
    fun `deleting a workout that never left the phone sends nothing`() = runTest {
        server.online = false
        val repo = repo()
        repo.addWorkout(run("r1"))

        repo.deleteWorkout("r1")
        server.online = true
        repo.syncPending()

        assertNull(dao.row("r1"))
        assertEquals(0, server.deleteCalls)
        assertTrue(server.rows.isEmpty())
    }

    @Test
    fun `offline delete hides the workout and the DELETE runs on sync`() = runTest {
        val repo = repo()
        repo.addWorkout(run("r1"))
        server.online = false

        repo.deleteWorkout("r1")

        assertTrue(repo.observeWorkouts().first().isEmpty())
        assertEquals(1, server.rows.size)

        server.online = true
        repo.syncPending()

        assertTrue(server.rows.isEmpty())
        assertNull(dao.row("r1"))
    }

    @Test
    fun `notes edited offline are patched on sync`() = runTest {
        val repo = repo()
        repo.addWorkout(run("r1"))
        server.online = false

        val edited = repo.updateNotes("r1", "Felt strong")

        assertEquals("Felt strong", (edited as ApiResult.Success).data.notes)
        assertEquals(SyncState.PENDING_UPDATE, dao.row("r1")?.syncState)

        server.online = true
        repo.syncPending()

        assertEquals("Felt strong", server.rows.getValue("r1").notes)
        assertEquals(SyncState.SYNCED, dao.row("r1")?.syncState)
    }

    @Test
    fun `an edit made while the upload is in flight is not lost`() = runTest {
        server.online = false
        val repo = repo()
        repo.addWorkout(run("r1"))
        server.online = true
        server.duringCreate = { dao.editNotes("r1", "Edited mid-upload") }

        repo.syncPending()

        assertEquals("Edited mid-upload", server.rows.getValue("r1").notes)
        assertEquals(SyncState.SYNCED, dao.row("r1")?.syncState)
        assertEquals(1, server.rows.size)
    }

    @Test
    fun `refresh keeps local edits and drops workouts deleted elsewhere`() = runTest {
        val repo = repo()
        repo.addWorkout(run("kept", at = "2026-10-01T08:00:00Z"))
        repo.addWorkout(run("gone", at = "2026-10-02T08:00:00Z"))
        server.online = false
        repo.updateNotes("kept", "Local note")
        server.online = true
        // Another device deleted "gone"; the pending note edit uploads first.
        server.rows.remove("gone")

        val result = repo.refresh()

        assertTrue(result is ApiResult.Success)
        val list = repo.observeWorkouts().first()
        assertEquals(listOf("kept"), list.map { it.id })
        assertEquals("Local note", list.single().notes)
    }

    @Test
    fun `offline refresh keeps the cached list on screen`() = runTest {
        val repo = repo()
        repo.addWorkout(run("r1"))
        server.online = false

        val result = repo.refresh()

        assertTrue(result is ApiResult.NetworkError)
        assertEquals(listOf("r1"), repo.observeWorkouts().first().map { it.id })
    }

    @Test
    fun `a tracked workout opens with its route while offline`() = runTest {
        server.online = false
        val repo = repo()
        repo.addWorkout(run("r1"), route(points = 4))

        val detail = repo.getWorkoutDetail("r1")

        assertEquals(4, (detail as ApiResult.Success).data.routePoints.size)
    }

    @Test
    fun `a synced route is fetched once and then read from the phone`() = runTest {
        val repo = repo()
        server.seed(serverDto("r1", routePoints = 5))
        repo.refresh()

        repo.getWorkoutDetail("r1")
        server.online = false
        val second = repo.getWorkoutDetail("r1")

        assertEquals(1, server.getCalls)
        assertEquals(5, (second as ApiResult.Success).data.routePoints.size)
        assertEquals(5, dao.routeSize("r1"))
    }

    @Test
    fun `a row the server rejects stays pending and doesn't block the rest`() = runTest {
        server.online = false
        val repo = repo()
        repo.addWorkout(run("bad", at = "2026-10-01T08:00:00Z"))
        repo.addWorkout(run("good", at = "2026-10-02T08:00:00Z"))
        server.online = true
        server.rejectUuid = "bad"

        val result = repo.syncPending()

        assertTrue(result is ApiResult.Success)
        assertEquals(SyncState.PENDING_CREATE, dao.row("bad")?.syncState)
        assertEquals(SyncState.SYNCED, dao.row("good")?.syncState)
    }

    @Test
    fun `clearCache empties the phone copy and cancels the queued sync`() = runTest {
        server.online = false
        val repo = repo()
        repo.addWorkout(run("r1"))

        repo.clearCache()

        assertTrue(repo.observeWorkouts().first().isEmpty())
        assertEquals(1, scheduler.cancelled)
        assertFalse(server.rows.containsKey("r1"))
    }

    // -----------------------------------------------------------------

    private fun run(id: String, at: String = "2026-10-05T07:30:00Z") = RunningWorkout(
        id = id,
        startTime = Instant.parse(at),
        duration = Duration.ofMinutes(30),
        distanceMeters = 5_000.0,
    )

    private fun lift(id: String) = StrengthWorkout(
        id = id,
        startTime = Instant.parse("2026-10-05T18:00:00Z"),
        duration = Duration.ofMinutes(45),
        sets = listOf(StrengthSet("Squat", 5, 100.0), StrengthSet("Squat", 5, 100.0)),
    )

    private fun route(points: Int) = (0 until points).map {
        TrackPoint(
            seq = it,
            latitude = 51.5 + it * 0.001,
            longitude = -0.16,
            elapsedMs = it * 2_000L,
            accuracyM = 5.0,
            recordedAt = Instant.parse("2026-10-05T07:30:00Z").plusSeconds(it * 2L),
        )
    }

    private fun serverDto(uuid: String, routePoints: Int = 0) = WorkoutDto(
        id = 0L,
        clientUuid = uuid,
        activityType = "RUNNING",
        startTime = "2026-10-04T07:00:00Z",
        durationSeconds = 1_800,
        distanceMeters = 5_000.0,
        routePoints = route(routePoints).map {
            RoutePointDto(it.seq, it.latitude, it.longitude, it.elapsedMs, it.accuracyM, it.recordedAt.toString())
        },
        createdAt = "2026-10-04T07:30:00Z",
        updatedAt = "2026-10-04T07:30:00Z",
    )

    private class CountingScheduler : SyncScheduler {
        var scheduled = 0
        var cancelled = 0
        override fun schedule() { scheduled++ }
        override fun cancel() { cancelled++ }
    }

    /**
     * A tiny in-memory PHP API: `POST /workouts` is idempotent on the
     * client uuid like the real one, and [online] = false makes every
     * call fail the way a dropped connection does.
     */
    private class FakeServer : WorkoutApi {
        val rows = linkedMapOf<String, WorkoutDto>()
        var online = true
        var loseNextReply = false
        var rejectUuid: String? = null
        var duringCreate: (suspend () -> Unit)? = null
        var createCalls = 0
        var deleteCalls = 0
        var getCalls = 0
        private var nextId = 1L

        fun seed(dto: WorkoutDto) {
            rows[dto.clientUuid] = dto.copy(id = nextId++)
        }

        private fun checkOnline() {
            if (!online) throw IOException("offline")
        }

        override suspend fun list(type: String?, from: String?, to: String?): Response<ApiEnvelope<List<WorkoutDto>>> {
            checkOnline()
            return Response.success(ApiEnvelope(success = true, data = rows.values.map { it.copy(routePoints = emptyList()) }))
        }

        override suspend fun create(body: WorkoutRequestDto): Response<ApiEnvelope<WorkoutDto>> {
            checkOnline()
            createCalls++
            if (body.clientUuid == rejectUuid) {
                return Response.error(422, """{"success":false,"error":{"message":"Validation failed"}}"""
                    .toResponseBody("application/json".toMediaType()))
            }
            duringCreate?.let { hook -> duringCreate = null; hook() }
            val stored = rows.getOrPut(body.clientUuid) { body.toDto(nextId++) }
            if (loseNextReply) {
                loseNextReply = false
                throw IOException("reply lost")
            }
            return Response.success(ApiEnvelope(success = true, data = stored))
        }

        override suspend fun get(id: Long): Response<ApiEnvelope<WorkoutDto>> {
            checkOnline()
            getCalls++
            val row = rows.values.first { it.id == id }
            return Response.success(ApiEnvelope(success = true, data = row))
        }

        override suspend fun delete(id: Long): Response<Unit> {
            checkOnline()
            deleteCalls++
            rows.entries.removeAll { it.value.id == id }
            return Response.success(Unit)
        }

        override suspend fun patch(id: Long, body: Map<String, Any?>): Response<ApiEnvelope<WorkoutDto>> {
            checkOnline()
            val entry = rows.entries.firstOrNull { it.value.id == id }
                ?: return Response.error(404, "{}".toResponseBody("application/json".toMediaType()))
            val updated = entry.value.copy(notes = body["notes"] as String?)
            rows[entry.key] = updated
            return Response.success(ApiEnvelope(success = true, data = updated))
        }

        private fun WorkoutRequestDto.toDto(id: Long) = WorkoutDto(
            id = id,
            clientUuid = clientUuid,
            activityType = activityType,
            startTime = startTime,
            durationSeconds = durationSeconds,
            distanceMeters = distanceMeters,
            poolLengthM = poolLengthM,
            laps = laps,
            caloriesKcal = caloriesKcal,
            notes = notes,
            sets = sets.mapIndexed { i, s -> StrengthSetDto(i + 1, s.exerciseName, s.reps, s.weightKg) },
            routePoints = routePoints.map {
                RoutePointDto(it.seq, it.latitude, it.longitude, it.elapsedMs, it.accuracyM, it.recordedAt)
            },
            createdAt = startTime,
            updatedAt = startTime,
        )
    }
}
