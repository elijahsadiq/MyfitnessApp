package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.GoalPeriod
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.remote.api.GoalApi
import com.elijah.fitness.data.remote.dto.ApiEnvelope
import com.elijah.fitness.data.remote.dto.CreateGoalRequestDto
import com.elijah.fitness.data.remote.dto.GoalDto
import com.elijah.fitness.data.remote.dto.UpdateGoalRequestDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.time.LocalDate

class RemoteGoalRepositoryTest {

    @Test
    fun `refresh populates the store newest-first`() = runTest {
        val api = FakeGoalApi()
        api.listResponse = Response.success(
            ApiEnvelope(
                success = true,
                data = listOf(
                    dtoWithId(id = 1L, created = "2026-09-10T09:00:00Z"),
                    dtoWithId(id = 2L, created = "2026-09-14T09:00:00Z"),
                    dtoWithId(id = 3L, created = "2026-09-12T09:00:00Z"),
                )
            )
        )
        val repo = RemoteGoalRepository(api)

        assertTrue(repo.observeGoals().first().isEmpty())
        val result = repo.refresh()
        assertTrue(result is ApiResult.Success)
        val order = repo.observeGoals().first().map { it.id }
        assertEquals(listOf("2", "3", "1"), order)
    }

    @Test
    fun `refresh error leaves the store empty`() = runTest {
        val api = FakeGoalApi()
        api.listResponse = Response.error(
            500,
            "".toResponseBody("application/json".toMediaType())
        )
        val repo = RemoteGoalRepository(api)

        val result = repo.refresh()
        assertTrue(result is ApiResult.Error)
        assertTrue(repo.observeGoals().first().isEmpty())
    }

    @Test
    fun `createGoal inserts the server response into the flow`() = runTest {
        val api = FakeGoalApi()
        api.createResponse = Response.success(
            201,
            ApiEnvelope(
                success = true,
                data = dtoWithId(id = 42L, created = "2026-09-14T10:00:00Z")
            )
        )
        val repo = RemoteGoalRepository(api)

        val result = repo.createGoal(
            title = "Run 20 km",
            activityType = ActivityType.RUNNING,
            goalType = GoalType.DISTANCE,
            period = GoalPeriod.THIS_WEEK,
            targetValue = 20.0,
            periodStart = LocalDate.parse("2026-09-14"),
            periodEnd = LocalDate.parse("2026-09-21"),
        )
        assertTrue(result is ApiResult.Success)
        val list = repo.observeGoals().first()
        assertEquals(1, list.size)
        assertEquals("42", list[0].id)
        // The request DTO the API received uses lowercase goal type + uppercase period.
        assertNotNull(api.lastCreateBody)
        assertEquals("distance", api.lastCreateBody!!.goalType)
        assertEquals("WEEK", api.lastCreateBody!!.period)
        assertEquals("RUNNING", api.lastCreateBody!!.activityType)
    }

    @Test
    fun `updateGoal replaces the existing row in the flow`() = runTest {
        val api = FakeGoalApi()
        val original = dtoWithId(id = 7L, target = 20.0)
        api.listResponse = Response.success(ApiEnvelope(success = true, data = listOf(original)))
        val repo = RemoteGoalRepository(api)
        repo.refresh()

        val updated = original.copy(targetValue = 25.0, title = "Run 25 km")
        api.updateResponse = Response.success(ApiEnvelope(success = true, data = updated))

        val result = repo.updateGoal(
            id = "7", title = "Run 25 km",
            activityType = ActivityType.RUNNING, targetValue = 25.0,
        )
        assertTrue(result is ApiResult.Success)
        val list = repo.observeGoals().first()
        assertEquals(1, list.size)
        assertEquals("Run 25 km", list[0].title)
        assertEquals(25.0, list[0].targetValue, 1e-6)
    }

    @Test
    fun `deleteGoal removes the row from the flow on success`() = runTest {
        val api = FakeGoalApi()
        api.listResponse = Response.success(
            ApiEnvelope(success = true, data = listOf(
                dtoWithId(id = 1L),
                dtoWithId(id = 2L),
            ))
        )
        val repo = RemoteGoalRepository(api)
        repo.refresh()

        val result = repo.deleteGoal("1")
        assertTrue(result is ApiResult.Success)
        val list = repo.observeGoals().first()
        assertEquals(listOf("2"), list.map { it.id })
    }

    @Test
    fun `deleteGoal error leaves the flow untouched`() = runTest {
        val api = FakeGoalApi()
        api.listResponse = Response.success(
            ApiEnvelope(success = true, data = listOf(dtoWithId(id = 1L)))
        )
        val repo = RemoteGoalRepository(api)
        repo.refresh()

        api.deleteResponse = Response.error(404, "".toResponseBody("application/json".toMediaType()))
        val result = repo.deleteGoal("1")
        assertTrue(result is ApiResult.Error)
        assertEquals(listOf("1"), repo.observeGoals().first().map { it.id })
    }

    // ---------- helpers ----------

    private fun dtoWithId(
        id: Long,
        created: String = "2026-09-14T09:00:00Z",
        target: Double = 20.0,
    ): GoalDto = GoalDto(
        id = id,
        title = "goal $id",
        activityType = "RUNNING",
        goalType = "distance",
        period = "WEEK",
        targetValue = target,
        periodStart = "2026-09-14",
        periodEnd = "2026-09-21",
        completedNotified = false,
        createdAt = created,
        updatedAt = created,
    )

    private class FakeGoalApi : GoalApi {
        var listResponse: Response<ApiEnvelope<List<GoalDto>>> = Response.success(
            ApiEnvelope(success = true, data = emptyList())
        )
        var createResponse: Response<ApiEnvelope<GoalDto>> = Response.success(
            ApiEnvelope(success = true, data = null)
        )
        var updateResponse: Response<ApiEnvelope<GoalDto>> = Response.success(
            ApiEnvelope(success = true, data = null)
        )
        var deleteResponse: Response<Unit> = Response.success(Unit)

        var lastCreateBody: CreateGoalRequestDto? = null
        var lastUpdateBody: UpdateGoalRequestDto? = null

        override suspend fun list() = listResponse
        override suspend fun create(body: CreateGoalRequestDto): Response<ApiEnvelope<GoalDto>> {
            lastCreateBody = body; return createResponse
        }
        override suspend fun update(id: Long, body: UpdateGoalRequestDto): Response<ApiEnvelope<GoalDto>> {
            lastUpdateBody = body; return updateResponse
        }
        override suspend fun delete(id: Long): Response<Unit> = deleteResponse
    }
}
