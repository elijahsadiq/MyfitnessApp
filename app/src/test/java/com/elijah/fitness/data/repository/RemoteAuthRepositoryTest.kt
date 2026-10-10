package com.elijah.fitness.data.repository

import com.elijah.fitness.data.local.OnboardingStore
import com.elijah.fitness.data.remote.TokenStore
import com.elijah.fitness.data.remote.api.AuthApi
import com.elijah.fitness.data.remote.dto.ApiEnvelope
import com.elijah.fitness.data.remote.dto.ApiError
import com.elijah.fitness.data.remote.dto.AuthResponseDto
import com.elijah.fitness.data.remote.dto.LoginRequestDto
import com.elijah.fitness.data.remote.dto.RegisterRequestDto
import com.elijah.fitness.data.remote.dto.UserDto
import com.elijah.fitness.data.remote.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class RemoteAuthRepositoryTest {

    // ---------- happy path ----------

    @Test
    fun `login on 200 persists the token and marks onboarding completed`() = runTest {
        val tokenStore = FakeTokenStore()
        val onboardingStore = FakeOnboardingStore()
        val api = FakeAuthApi(
            loginResponse = Response.success(
                ApiEnvelope(
                    success = true,
                    data = AuthResponseDto(
                        token = "abc-token",
                        user = alexDto,
                    )
                )
            )
        )
        val repo = RemoteAuthRepository(api, tokenStore, onboardingStore, UserSettings())

        val result = repo.login("alex@example.com", "Password123")

        assertTrue(result is ApiResult.Success)
        assertEquals("abc-token", tokenStore.token.first())
        assertTrue(onboardingStore.hasCompletedOnboarding.first())
    }

    @Test
    fun `register on 201 persists the token and returns a UserProfile`() = runTest {
        val tokenStore = FakeTokenStore()
        val onboardingStore = FakeOnboardingStore()
        val api = FakeAuthApi(
            registerResponse = Response.success(
                201,
                ApiEnvelope(
                    success = true,
                    data = AuthResponseDto(token = "new-token", user = alexDto)
                )
            )
        )
        val repo = RemoteAuthRepository(api, tokenStore, onboardingStore, UserSettings())

        val result = repo.register(
            email = "alex@example.com",
            password = "Password123",
            fullName = "Alex",
            weightKg = 70.0,
            heightCm = 175.0,
        )

        assertTrue(result is ApiResult.Success)
        assertEquals("new-token", tokenStore.token.first())
    }

    // ---------- error paths ----------

    @Test
    fun `login on 401 does not persist a token`() = runTest {
        val tokenStore = FakeTokenStore()
        val api = FakeAuthApi(
            loginResponse = Response.error(
                401,
                """{"success":false,"error":{"message":"Invalid email or password"}}"""
                    .toResponseBody("application/json".toMediaType())
            )
        )
        val repo = RemoteAuthRepository(api, tokenStore, FakeOnboardingStore(), UserSettings())

        val result = repo.login("alex@example.com", "wrong")

        assertTrue(result is ApiResult.Error)
        assertEquals(401, (result as ApiResult.Error).status)
        assertNull(tokenStore.token.first())
    }

    @Test
    fun `register surfaces the field errors returned by the server`() = runTest {
        val tokenStore = FakeTokenStore()
        val api = FakeAuthApi(
            registerResponse = Response.error(
                422,
                """{"success":false,"error":{"message":"Validation failed","fields":{"email":"Must be valid"}}}"""
                    .toResponseBody("application/json".toMediaType())
            )
        )
        val repo = RemoteAuthRepository(api, tokenStore, FakeOnboardingStore(), UserSettings())

        val result = repo.register("bad", "shortpw", "Alex")

        val error = result as ApiResult.Error
        assertEquals(422, error.status)
        assertEquals("Must be valid", error.fieldErrors["email"])
        assertNull(tokenStore.token.first())
    }

    // ---------- logout ----------

    @Test
    fun `logout clears the token even when the network call succeeds`() = runTest {
        val tokenStore = FakeTokenStore().apply { save("t") }
        val api = FakeAuthApi(logoutResponse = Response.success(Unit))
        val repo = RemoteAuthRepository(api, tokenStore, FakeOnboardingStore(), UserSettings())

        val result = repo.logout()

        assertTrue(result is ApiResult.Success)
        assertNull(tokenStore.token.first())
    }

    @Test
    fun `logout clears the token even when the network call fails`() = runTest {
        val tokenStore = FakeTokenStore().apply { save("t") }
        val api = FakeAuthApi(
            logoutResponse = Response.error(500, "".toResponseBody("application/json".toMediaType()))
        )
        val repo = RemoteAuthRepository(api, tokenStore, FakeOnboardingStore(), UserSettings())

        val result = repo.logout()

        // Even on server-side failure we report Success locally so the UI
        // routes the user back to auth; the on-device token is gone.
        assertTrue(result is ApiResult.Success)
        assertNull(tokenStore.token.first())
    }

    // ---------- isLoggedIn ----------

    @Test
    fun `isLoggedIn tracks the TokenStore`() = runTest {
        val tokenStore = FakeTokenStore()
        val repo = RemoteAuthRepository(FakeAuthApi(), tokenStore, FakeOnboardingStore(), UserSettings())

        assertFalse(repo.isLoggedIn.first())
        tokenStore.save("t")
        assertTrue(repo.isLoggedIn.first())
        tokenStore.clear()
        assertFalse(repo.isLoggedIn.first())
    }

    // ---------- fakes ----------

    private val alexDto = UserDto(
        id = 1,
        email = "alex@example.com",
        fullName = "Alex Runner",
        weightKg = 70.0,
        heightCm = 178.0,
        createdAt = "2026-01-01T00:00:00Z",
    )

    private class FakeTokenStore : TokenStore {
        private val state = MutableStateFlow<String?>(null)
        override val token: kotlinx.coroutines.flow.Flow<String?> = state
        override suspend fun save(token: String) { state.value = token }
        override suspend fun clear() { state.value = null }
    }

    private class FakeOnboardingStore : OnboardingStore {
        private val state = MutableStateFlow(false)
        override val hasCompletedOnboarding: kotlinx.coroutines.flow.Flow<Boolean> = state
        override suspend fun markCompleted() { state.value = true }
    }

    private class FakeAuthApi(
        private val registerResponse: Response<ApiEnvelope<AuthResponseDto>> =
            Response.success(ApiEnvelope(success = true, data = null, error = ApiError("unset"))),
        private val loginResponse: Response<ApiEnvelope<AuthResponseDto>> =
            Response.success(ApiEnvelope(success = true, data = null, error = ApiError("unset"))),
        private val logoutResponse: Response<Unit> = Response.success(Unit),
    ) : AuthApi {
        override suspend fun register(body: RegisterRequestDto) = registerResponse
        override suspend fun login(body: LoginRequestDto) = loginResponse
        override suspend fun logout() = logoutResponse
        override suspend fun health(): Response<Unit> = Response.success(Unit)
    }
}
