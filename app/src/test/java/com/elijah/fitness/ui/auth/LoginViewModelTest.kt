package com.elijah.fitness.ui.auth

import com.elijah.fitness.R
import com.elijah.fitness.data.model.UserProfile
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `client validation blocks submission and populates field errors`() = runTest {
        val repo = FakeAuthRepository()
        val vm = LoginViewModel(repo)

        vm.submit(email = "not-an-email", password = "short")

        val state = vm.uiState.value
        assertFalse(state.submitting)
        assertEquals(FieldError.Res(R.string.auth_error_email_invalid), state.emailError)
        assertEquals(FieldError.Res(R.string.auth_error_password_too_short), state.passwordError)
        assertEquals(0, repo.loginCallCount)
    }

    @Test
    fun `successful submit flips submitting on then off and emits LoggedIn`() = runTest {
        val repo = FakeAuthRepository()
        repo.loginResult = ApiResult.Success(sampleUser)
        val vm = LoginViewModel(repo)

        vm.submit("alex@example.com", "correctpassword")
        // While the coroutine is queued but not yet dispatched, submitting is true.
        assertTrue(vm.uiState.value.submitting)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.submitting)
        assertEquals(LoginViewModel.Event.LoggedIn, vm.events.first())
    }

    @Test
    fun `401 with no field map attaches the message to the password row`() = runTest {
        val repo = FakeAuthRepository()
        repo.loginResult = ApiResult.Error(
            message = "Invalid email or password",
            status = 401,
        )
        val vm = LoginViewModel(repo)

        vm.submit("alex@example.com", "wrongpassword")
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.submitting)
        assertNull(state.emailError)
        assertEquals(FieldError.Text("Invalid email or password"), state.passwordError)
    }

    @Test
    fun `422 field errors map onto the matching fields`() = runTest {
        val repo = FakeAuthRepository()
        repo.loginResult = ApiResult.Error(
            message = "Validation failed",
            status = 422,
            fieldErrors = mapOf("email" to "Must be valid"),
        )
        val vm = LoginViewModel(repo)

        vm.submit("alex@example.com", "correctpassword")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FieldError.Text("Must be valid"), vm.uiState.value.emailError)
        assertNull(vm.uiState.value.passwordError)
    }

    @Test
    fun `network error emits ShowNetworkError with the network string`() = runTest {
        val repo = FakeAuthRepository()
        repo.loginResult = ApiResult.NetworkError
        val vm = LoginViewModel(repo)

        vm.submit("alex@example.com", "correctpassword")
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first() as LoginViewModel.Event.ShowNetworkError
        assertEquals(R.string.auth_error_network, event.messageRes)
        assertFalse(vm.uiState.value.submitting)
    }

    @Test
    fun `double-submit while in flight is ignored`() = runTest {
        val gate = CompletableDeferred<ApiResult<UserProfile>>()
        val repo = FakeAuthRepository()
        repo.loginResultProvider = { gate.await() }
        val vm = LoginViewModel(repo)

        vm.submit("alex@example.com", "correctpassword")
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.submitting)
        vm.submit("alex@example.com", "correctpassword")
        gate.complete(ApiResult.Success(sampleUser))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.loginCallCount)
    }

    @Test
    fun `onEmailChanged clears an existing email error only`() = runTest {
        val repo = FakeAuthRepository()
        val vm = LoginViewModel(repo)
        vm.submit("bad", "short")
        assertEquals(FieldError.Res(R.string.auth_error_email_invalid), vm.uiState.value.emailError)
        vm.onEmailChanged()
        assertNull(vm.uiState.value.emailError)
        // Password error untouched.
        assertEquals(FieldError.Res(R.string.auth_error_password_too_short), vm.uiState.value.passwordError)
    }

    private val sampleUser = UserProfile(
        id = 1,
        email = "alex@example.com",
        fullName = "Alex",
        weightKg = 70.0,
        heightCm = 178.0,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private class FakeAuthRepository : AuthRepository {
        private val tokenState = MutableStateFlow(false)
        override val isLoggedIn: Flow<Boolean> = tokenState

        var loginCallCount = 0
        var loginResult: ApiResult<UserProfile> = ApiResult.NetworkError
        var loginResultProvider: (suspend () -> ApiResult<UserProfile>)? = null

        override suspend fun register(
            email: String, password: String, fullName: String,
            weightKg: Double?, heightCm: Double?,
        ): ApiResult<UserProfile> = ApiResult.NetworkError

        override suspend fun login(email: String, password: String): ApiResult<UserProfile> {
            loginCallCount++
            return loginResultProvider?.invoke() ?: loginResult
        }

        override suspend fun logout(): ApiResult<Unit> = ApiResult.Success(Unit)
    }
}
