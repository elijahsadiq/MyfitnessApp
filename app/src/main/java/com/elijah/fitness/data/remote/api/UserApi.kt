package com.elijah.fitness.data.remote.api

import com.elijah.fitness.data.remote.dto.ApiEnvelope
import com.elijah.fitness.data.remote.dto.UpdateProfileRequestDto
import com.elijah.fitness.data.remote.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface UserApi {

    @GET("me")
    suspend fun me(): Response<ApiEnvelope<UserDto>>

    @PUT("me")
    suspend fun updateMe(@Body body: UpdateProfileRequestDto): Response<ApiEnvelope<UserDto>>
}
