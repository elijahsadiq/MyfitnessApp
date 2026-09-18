package com.elijah.fitness.data.remote.api

import com.elijah.fitness.data.remote.dto.ApiEnvelope
import com.elijah.fitness.data.remote.dto.WorkoutDto
import com.elijah.fitness.data.remote.dto.WorkoutRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface WorkoutApi {

    @GET("workouts")
    suspend fun list(
        @Query("type") type: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): Response<ApiEnvelope<List<WorkoutDto>>>

    @POST("workouts")
    suspend fun create(@Body body: WorkoutRequestDto): Response<ApiEnvelope<WorkoutDto>>

    @GET("workouts/{id}")
    suspend fun get(@Path("id") id: Long): Response<ApiEnvelope<WorkoutDto>>

    @DELETE("workouts/{id}")
    suspend fun delete(@Path("id") id: Long): Response<Unit>
}
