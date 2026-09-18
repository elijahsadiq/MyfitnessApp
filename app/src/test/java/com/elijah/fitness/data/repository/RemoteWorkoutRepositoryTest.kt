package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.remote.api.WorkoutApi
import com.elijah.fitness.data.remote.dto.ApiEnvelope
import com.elijah.fitness.data.remote.dto.WorkoutDto
import com.elijah.fitness.data.remote.dto.WorkoutRequestDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.time.Duration
import java.time.Instant

class RemoteWorkoutRepositoryTest {

    @Test
    fun `refresh on success populates observeWorkouts newest-first`() = runTest {
        val api = FakeWorkoutApi()
        api.listResponse = Response.success(
            ApiEnvelope(
                success = true,
                data = listOf(
                    dtoAt("older", "2026-09-10T09:00:00Z"),
                    dtoAt("newer", "2026-09-14T09:00:00Z"),
                    dtoAt("middle", "2026-09-12T09:00:00Z"),
                )
            )
        )
        val repo = RemoteWorkoutRepository(api)

        assertTrue(repo.observeWorkouts().first().isEmpty())
        val result = repo.refresh()
        assertTrue(result is ApiResult.Success)
        val list = repo.observeWorkouts().first()
        assertEquals(listOf("newer", "middle", "older"), list.map { it.id })
    }

    @Test
    fun `refresh network error leaves the store empty and returns NetworkError`() = runTest {
        val api = FakeWorkoutApi()
        api.listResponse = Response.error(
            500,
            "".toResponseBody("application/json".toMediaType())
        )
        val repo = RemoteWorkoutRepository(api)

        val result = repo.refresh()
        assertTrue(result is ApiResult.Error)
        assertTrue(repo.observeWorkouts().first().isEmpty())
    }

    @Test
    fun `addWorkout success inserts into the shared flow`() = runTest {
        val api = FakeWorkoutApi()
        api.createResponse = Response.success(
            201,
            ApiEnvelope(success = true, data = dtoAt("new-1", "2026-09-15T10:00:00Z"))
        )
        val repo = RemoteWorkoutRepository(api)

        val local = RunningWorkout(
            id = "new-1",
            startTime = Instant.parse("2026-09-15T10:00:00Z"),
            duration = Duration.ofMinutes(30),
            distanceMeters = 5_000.0,
        )
        val result = repo.addWorkout(local)
        assertTrue(result is ApiResult.Success)

        val list = repo.observeWorkouts().first()
        assertEquals(1, list.size)
        assertEquals("new-1", list.first().id)
    }

    @Test
    fun `addWorkout replaces an existing entry with the same clientUuid`() = runTest {
        // Simulate a retry: same UUID arrives twice; the second create returns
        // the server's canonical row and we replace ours locally instead of
        // duplicating.
        val api = FakeWorkoutApi()
        api.createResponse = Response.success(
            201,
            ApiEnvelope(success = true, data = dtoAt("dup", "2026-09-15T10:00:00Z"))
        )
        val repo = RemoteWorkoutRepository(api)

        val w = RunningWorkout(
            id = "dup",
            startTime = Instant.parse("2026-09-15T10:00:00Z"),
            duration = Duration.ofMinutes(30),
            distanceMeters = 5_000.0,
        )
        repo.addWorkout(w)
        repo.addWorkout(w)

        assertEquals(1, repo.observeWorkouts().first().size)
    }

    @Test
    fun `addWorkout network error does not mutate the store`() = runTest {
        val api = FakeWorkoutApi()
        api.createResponse = Response.error(500, "".toResponseBody("application/json".toMediaType()))
        val repo = RemoteWorkoutRepository(api)

        val result = repo.addWorkout(
            RunningWorkout(
                id = "never-persisted",
                startTime = Instant.parse("2026-09-15T10:00:00Z"),
                duration = Duration.ofMinutes(30),
                distanceMeters = 5_000.0,
            )
        )
        assertTrue(result is ApiResult.Error)
        assertTrue(repo.observeWorkouts().first().isEmpty())
    }

    // ---------- helpers ----------

    private fun dtoAt(id: String, isoStart: String): WorkoutDto = WorkoutDto(
        id = 1L,
        clientUuid = id,
        activityType = ActivityType.RUNNING.name,
        startTime = isoStart,
        durationSeconds = 30 * 60,
        distanceMeters = 5_000.0,
        notes = null,
        sets = emptyList(),
        createdAt = isoStart,
        updatedAt = isoStart,
    )

    private class FakeWorkoutApi : WorkoutApi {
        var listResponse: Response<ApiEnvelope<List<WorkoutDto>>> = Response.success(
            ApiEnvelope(success = true, data = emptyList())
        )
        var createResponse: Response<ApiEnvelope<WorkoutDto>> = Response.success(
            ApiEnvelope(success = true, data = null)
        )

        override suspend fun list(type: String?, from: String?, to: String?) = listResponse
        override suspend fun create(body: WorkoutRequestDto) = createResponse
        override suspend fun get(id: Long): Response<ApiEnvelope<WorkoutDto>> = createResponse
        override suspend fun delete(id: Long): Response<Unit> = Response.success(Unit)
    }
}
