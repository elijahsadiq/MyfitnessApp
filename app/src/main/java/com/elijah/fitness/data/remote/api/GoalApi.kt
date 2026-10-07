package com.elijah.fitness.data.remote.api

import com.elijah.fitness.data.remote.dto.ApiEnvelope
import com.elijah.fitness.data.remote.dto.CreateGoalRequestDto
import com.elijah.fitness.data.remote.dto.ExtendGoalRequestDto
import com.elijah.fitness.data.remote.dto.GoalDto
import com.elijah.fitness.data.remote.dto.UpdateGoalRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface GoalApi {

    @GET("goals")
    suspend fun list(): Response<ApiEnvelope<List<GoalDto>>>

    @POST("goals")
    suspend fun create(@Body body: CreateGoalRequestDto): Response<ApiEnvelope<GoalDto>>

    @PUT("goals/{id}")
    suspend fun update(
        @Path("id") id: Long,
        @Body body: UpdateGoalRequestDto,
    ): Response<ApiEnvelope<GoalDto>>

    @DELETE("goals/{id}")
    suspend fun delete(@Path("id") id: Long): Response<Unit>

    /**
     * Flip the server-side `completed_notified` flag so this goal's
     * celebration is never shown again on this account.
     */
    @POST("goals/{id}/notified")
    suspend fun markNotified(@Path("id") id: Long): Response<ApiEnvelope<GoalDto>>

    /** Make this goal the user's main goal (transactional on the server). */
    @POST("goals/{id}/main")
    suspend fun makeMain(@Path("id") id: Long): Response<ApiEnvelope<GoalDto>>

    /** Add [ExtendGoalRequestDto.days] days to an active goal. */
    @POST("goals/{id}/extend")
    suspend fun extend(
        @Path("id") id: Long,
        @Body body: ExtendGoalRequestDto,
    ): Response<ApiEnvelope<GoalDto>>
}
