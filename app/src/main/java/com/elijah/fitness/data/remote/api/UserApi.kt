package com.elijah.fitness.data.remote.api

import com.elijah.fitness.data.remote.dto.ApiEnvelope
import com.elijah.fitness.data.remote.dto.DeleteAccountRequestDto
import com.elijah.fitness.data.remote.dto.UpdateProfileRequestDto
import com.elijah.fitness.data.remote.dto.UserDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part

interface UserApi {

    @GET("me")
    suspend fun me(): Response<ApiEnvelope<UserDto>>

    @PUT("me")
    suspend fun updateMe(@Body body: UpdateProfileRequestDto): Response<ApiEnvelope<UserDto>>

    @Multipart
    @POST("me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): Response<ApiEnvelope<UserDto>>

    @DELETE("me/avatar")
    suspend fun deleteAvatar(): Response<ApiEnvelope<UserDto>>

    /**
     * `@HTTP(hasBody = true)` is required to send a JSON body on a DELETE
     * — Retrofit's `@DELETE` annotation forbids `@Body` by default.
     */
    @HTTP(method = "DELETE", path = "me", hasBody = true)
    suspend fun deleteMe(@Body body: DeleteAccountRequestDto): Response<Unit>
}
