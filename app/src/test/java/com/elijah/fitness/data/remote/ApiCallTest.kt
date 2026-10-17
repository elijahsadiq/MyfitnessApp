package com.elijah.fitness.data.remote

import com.elijah.fitness.data.remote.dto.ApiEnvelope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Regression tests for the delete-account bug: `DELETE /me` answers
 * `{ success: true, data: null }`. Through [apiCall] that looked like a
 * failure ("Server error") even though the account was deleted, so the
 * endpoint must use [apiCallEmpty].
 */
class ApiCallTest {

    private val json = "application/json".toMediaType()

    @Test
    fun `apiCall treats a success without data as an error`() = runTest {
        val result = apiCall<Unit?> {
            Response.success(ApiEnvelope(success = true, data = null))
        }
        assertTrue(result is ApiResult.Error)
    }

    @Test
    fun `apiCallEmpty treats any 2xx as success`() = runTest {
        val result = apiCallEmpty { Response.success(Unit) }
        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `apiCallEmpty keeps the server's field errors`() = runTest {
        val body = """{"success":false,"error":{"message":"Validation failed",""" +
            """"fields":{"confirm":"Type DELETE to confirm"}}}"""
        val result = apiCallEmpty { Response.error(422, body.toResponseBody(json)) }

        assertTrue(result is ApiResult.Error)
        result as ApiResult.Error
        assertEquals(422, result.status)
        assertEquals("Type DELETE to confirm", result.fieldErrors["confirm"])
    }

    @Test
    fun `apiCallEmpty reports 404 status so a vanished account can be treated as deleted`() = runTest {
        val body = """{"success":false,"error":{"message":"User not found"}}"""
        val result = apiCallEmpty { Response.error(404, body.toResponseBody(json)) }

        assertTrue(result is ApiResult.Error)
        assertEquals(404, (result as ApiResult.Error).status)
    }

    @Test
    fun `apiCallEmpty maps IOException to NetworkError`() = runTest {
        val result = apiCallEmpty { throw IOException("offline") }
        assertEquals(ApiResult.NetworkError, result)
    }
}
