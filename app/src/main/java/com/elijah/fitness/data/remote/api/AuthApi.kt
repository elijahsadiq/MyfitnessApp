package com.elijah.fitness.data.remote.api

import com.elijah.fitness.data.remote.dto.ApiEnvelope
import com.elijah.fitness.data.remote.dto.AuthResponseDto
import com.elijah.fitness.data.remote.dto.LoginRequestDto
import com.elijah.fitness.data.remote.dto.RegisterRequestDto
import com.elijah.fitness.data.remote.dto.RegisterResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): Response<ApiEnvelope<RegisterResponseDto>>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): Response<ApiEnvelope<AuthResponseDto>>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    /** No auth needed. Used to wake a sleeping server early (see AppContainer). */
    @GET("health")
    suspend fun health(): Response<Unit>
}
