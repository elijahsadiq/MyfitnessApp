package com.elijah.fitness.data.remote

import com.elijah.fitness.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/**
 * Runs an API [block] and translates the outcome into [ApiResult].
 *
 * - HTTP 2xx + `success: true` + non-null `data` -> [ApiResult.Success].
 * - HTTP 4xx/5xx, or `success: false` -> [ApiResult.Error] with the
 *   server's field-error map preserved.
 * - IOException -> [ApiResult.NetworkError].
 * - Any other Throwable -> [ApiResult.Error] with status -1.
 */
suspend fun <T> apiCall(
    block: suspend () -> Response<ApiEnvelope<T>>,
): ApiResult<T> = try {
    val response = block()
    val envelope = response.body()

    if (response.isSuccessful && envelope?.success == true && envelope.data != null) {
        ApiResult.Success(envelope.data)
    } else {
        val error = envelope?.error ?: parseErrorBody(response)
        ApiResult.Error(
            message = error?.message ?: "Server error",
            status = response.code(),
            fieldErrors = error?.fields.orEmpty(),
        )
    }
} catch (_: IOException) {
    ApiResult.NetworkError
} catch (t: Throwable) {
    ApiResult.Error(message = t.message ?: "Unexpected error", status = -1)
}

/**
 * Same as [apiCall] but for endpoints that return no payload (logout,
 * delete). A 2xx response is `Success(Unit)`; anything else follows the
 * same error / network-error branches.
 */
suspend fun apiCallEmpty(
    block: suspend () -> Response<Unit>,
): ApiResult<Unit> = try {
    val response = block()
    if (response.isSuccessful) {
        ApiResult.Success(Unit)
    } else {
        val error = parseErrorBody(response)
        ApiResult.Error(
            message = error?.message ?: "Server error",
            status = response.code(),
            fieldErrors = error?.fields.orEmpty(),
        )
    }
} catch (_: IOException) {
    ApiResult.NetworkError
} catch (t: Throwable) {
    ApiResult.Error(message = t.message ?: "Unexpected error", status = -1)
}

private val errorJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

/**
 * Server error responses still carry the [ApiEnvelope] shape, but on 4xx/5xx
 * Retrofit puts them in `errorBody()` — not `body()`. We deserialize the raw
 * text back into the envelope's error slot so field messages survive.
 */
private fun parseErrorBody(response: Response<*>): com.elijah.fitness.data.remote.dto.ApiError? {
    val text = response.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        errorJson.decodeFromString(
            ApiEnvelope.serializer(com.elijah.fitness.data.remote.dto.ApiError.serializer()),
            text
        ).error
    }.getOrNull()
}
