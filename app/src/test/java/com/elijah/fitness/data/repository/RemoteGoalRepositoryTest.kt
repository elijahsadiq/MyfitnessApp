package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.remote.api.GoalApi
import com.elijah.fitness.data.remote.dto.ApiEnvelope
import com.elijah.fitness.data.remote.dto.CreateGoalRequestDto
import com.elijah.fitness.data.remote.dto.ExtendGoalRequestDto
import com.elijah.fitness.data.remote.dto.GoalDto
import com.elijah.fitness.data.remote.dto.UpdateGoalRequestDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `createGoal inserts the server response into the flow`() = runTest {
        val api = FakeGoalApi()
        api.createResponse = Response.success(
            201,
            ApiEnvelope(success = true, data = dtoWithId(id = 42L, created = "2026-09-14T10:00:00Z"))
        )
        val repo = RemoteGoalRepository(api)

        val result = repo.createGoal(
            title = "Run 20 km",
            activityType = ActivityType.RUNNING,
            goalType = GoalType.DISTANCE,
            targetValue = 20.0,
            startDate = LocalDate.parse("2026-09-14"),
            durationDays = 7,
        )
        assertTrue(result is ApiResult.Success)
        val list = repo.observeGoals().first()
        assertEquals(1, list.size)
        assertEquals("42", list[0].id)
        assertNotNull(api.lastCreateBody)
        assertEquals("distance", api.lastCreateBody!!.goalType)
        assertEquals("RUNNING", api.lastCreateBody!!.activityType)
        assertEquals("2026-09-14", api.lastCreateBody!!.startDate)
        assertEquals(7, api.lastCreateBody!!.durationDays)
    }

    @Test
    fun `updateGoal replaces the existing row in the flow`() = runTest {
        val api = FakeGoalApi()
        val original = dtoWithId(id = 7L, target = 20.0)
        api.listResponse = Response.success(ApiEnvelope(success = true, data = listOf(original)))
        val repo = RemoteGoalRepository(api)
        repo.refresh()

        val updated = original.copy(targetValue = 25.0, title = "Run 25 km", durationDays = 14,
            lastDay = "2026-09-27")
        api.updateResponse = Response.success(ApiEnvelope(success = true, data = updated))

        val result = repo.updateGoal(
            id = "7", title = "Run 25 km",
            activityType = ActivityType.RUNNING, goalType = GoalType.DISTANCE,
            targetValue = 25.0, durationDays = 14,
        )
        assertTrue(result is ApiResult.Success)
        val list = repo.observeGoals().first()
        assertEquals(1, list.size)
        assertEquals("Run 25 km", list[0].title)
        assertEquals(25.0, list[0].targetValue, 1e-6)
        assertEquals(14, list[0].durationDays)
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
    fun `markCompletedNotified patches the flow with the returned flag`() = runTest {
        val api = FakeGoalApi()
        api.listResponse = Response.success(
            ApiEnvelope(success = true, data = listOf(dtoWithId(id = 5L)))
        )
        val repo = RemoteGoalRepository(api)
        repo.refresh()

        val notifiedDto = dtoWithId(id = 5L).copy(completedNotified = true)
        api.markNotifiedResponse = Response.success(ApiEnvelope(success = true, data = notifiedDto))

        val result = repo.markCompletedNotified("5")
        assertTrue(result is ApiResult.Success)
        assertEquals(1, api.markNotifiedCallCount)
        val goal = repo.observeGoals().first().first()
        assertTrue(goal.completedNotified)
    }

    @Test
    fun `makeMainGoal clears is_main on sibling rows locally`() = runTest {
        val api = FakeGoalApi()
        api.listResponse = Response.success(ApiEnvelope(success = true, data = listOf(
            dtoWithId(id = 1L, isMain = true),
            dtoWithId(id = 2L, isMain = false),
        )))
        val repo = RemoteGoalRepository(api)
        repo.refresh()

        api.makeMainResponse = Response.success(
            ApiEnvelope(success = true, data = dtoWithId(id = 2L, isMain = true))
        )
        val result = repo.makeMainGoal("2")
        assertTrue(result is ApiResult.Success)

        val mains = repo.observeGoals().first().filter { it.isMain }.map { it.id }
        assertEquals(listOf("2"), mains)
        assertFalse(repo.observeGoals().first().first { it.id == "1" }.isMain)
    }

    @Test
    fun `extendGoal updates the goal with the server's new durationDays`() = runTest {
        val api = FakeGoalApi()
        api.listResponse = Response.success(ApiEnvelope(success = true, data = listOf(
            dtoWithId(id = 3L, durationDays = 7, lastDay = "2026-09-20"),
        )))
        val repo = RemoteGoalRepository(api)
        repo.refresh()

        api.extendResponse = Response.success(ApiEnvelope(success = true, data = dtoWithId(
            id = 3L, durationDays = 14, lastDay = "2026-09-27",
        )))
        val result = repo.extendGoal("3", 7)
        assertTrue(result is ApiResult.Success)
        assertEquals(ExtendGoalRequestDto(days = 7), api.lastExtendBody)
        assertEquals(14, repo.observeGoals().first().first().durationDays)
    }

    @Test
    fun `deleteGoal error leaves the flow untouched`() = runTest {
        val api = FakeGoalApi()
        api.listResponse = Response.success(ApiEnvelope(success = true, data = listOf(dtoWithId(id = 1L))))
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
        durationDays: Int = 7,
        startDate: String = "2026-09-14",
        lastDay: String = "2026-09-20",
        isMain: Boolean = false,
    ): GoalDto = GoalDto(
        id = id,
        title = "goal $id",
        activityType = "RUNNING",
        goalType = "distance",
        targetValue = target,
        startDate = startDate,
        lastDay = lastDay,
        durationDays = durationDays,
        isMain = isMain,
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
        var markNotifiedResponse: Response<ApiEnvelope<GoalDto>> = Response.success(
            ApiEnvelope(success = true, data = null)
        )
        var markNotifiedCallCount = 0
        var makeMainResponse: Response<ApiEnvelope<GoalDto>> = Response.success(
            ApiEnvelope(success = true, data = null)
        )
        var extendResponse: Response<ApiEnvelope<GoalDto>> = Response.success(
            ApiEnvelope(success = true, data = null)
        )

        var lastCreateBody: CreateGoalRequestDto? = null
        var lastUpdateBody: UpdateGoalRequestDto? = null
        var lastExtendBody: ExtendGoalRequestDto? = null

        override suspend fun list() = listResponse
        override suspend fun create(body: CreateGoalRequestDto): Response<ApiEnvelope<GoalDto>> {
            lastCreateBody = body; return createResponse
        }
        override suspend fun update(id: Long, body: UpdateGoalRequestDto): Response<ApiEnvelope<GoalDto>> {
            lastUpdateBody = body; return updateResponse
        }
        override suspend fun delete(id: Long): Response<Unit> = deleteResponse
        override suspend fun markNotified(id: Long): Response<ApiEnvelope<GoalDto>> {
            markNotifiedCallCount++; return markNotifiedResponse
        }
        override suspend fun makeMain(id: Long): Response<ApiEnvelope<GoalDto>> = makeMainResponse
        override suspend fun extend(id: Long, body: ExtendGoalRequestDto): Response<ApiEnvelope<GoalDto>> {
            lastExtendBody = body; return extendResponse
        }
    }
}
