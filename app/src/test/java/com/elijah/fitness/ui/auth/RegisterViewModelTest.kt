package com.elijah.fitness.ui.auth

import com.elijah.fitness.R
import com.elijah.fitness.data.model.UserProfile
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.AuthRepository
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
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `client validation surfaces every failing field at once`() = runTest {
        val repo = FakeAuthRepository()
        val vm = RegisterViewModel(repo)

        vm.submit(
            fullName = "",
            email = "bad",
            password = "short",
            weightRaw = "500",
            termsAccepted = false,
        )

        val state = vm.uiState.value
        assertFalse(state.submitting)
        assertEquals(FieldError.Res(R.string.auth_error_name_required), state.fullNameError)
        assertEquals(FieldError.Res(R.string.auth_error_email_invalid), state.emailError)
        assertEquals(FieldError.Res(R.string.auth_error_password_too_short), state.passwordError)
        assertEquals(FieldError.Res(R.string.auth_error_weight_out_of_range), state.weightError)
        assertEquals(FieldError.Res(R.string.auth_error_terms_required), state.termsError)
        assertEquals(0, repo.registerCallCount)
    }

    @Test
    fun `successful submit clears state and emits RegistrationComplete`() = runTest {
        val repo = FakeAuthRepository()
        repo.registerResult = ApiResult.Success(sampleUser)
        val vm = RegisterViewModel(repo)

        vm.submit(
            fullName = "Alex Runner",
            email = "alex@example.com",
            password = "hunter2hunter",
            weightRaw = "70.5",
            termsAccepted = true,
        )
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.submitting)
        assertNull(state.fullNameError)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertEquals(RegisterViewModel.Event.RegistrationComplete, vm.events.first())
        assertEquals(1, repo.registerCallCount)
        assertEquals(70.5, repo.lastWeight!!, 0.0)
    }

    @Test
    fun `empty weight submits as null`() = runTest {
        val repo = FakeAuthRepository()
        repo.registerResult = ApiResult.Success(sampleUser)
        val vm = RegisterViewModel(repo)

        vm.submit(
            fullName = "Alex",
            email = "alex@example.com",
            password = "hunter2hunter",
            weightRaw = "",
            termsAccepted = true,
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(repo.lastWeight)
    }

    @Test
    fun `422 field errors map onto the matching fields`() = runTest {
        val repo = FakeAuthRepository()
        repo.registerResult = ApiResult.Error(
            message = "Validation failed",
            status = 422,
            fieldErrors = mapOf("email" to "Already registered"),
        )
        val vm = RegisterViewModel(repo)

        vm.submit("Alex", "alex@example.com", "hunter2hunter", "70", termsAccepted = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FieldError.Text("Already registered"), vm.uiState.value.emailError)
    }

    @Test
    fun `network error emits ShowNetworkError`() = runTest {
        val repo = FakeAuthRepository()
        repo.registerResult = ApiResult.NetworkError
        val vm = RegisterViewModel(repo)

        vm.submit("Alex", "alex@example.com", "hunter2hunter", "70", termsAccepted = true)
        dispatcher.scheduler.advanceUntilIdle()

        val event = vm.events.first() as RegisterViewModel.Event.ShowNetworkError
        assertEquals(R.string.auth_error_network, event.messageRes)
    }

    private val sampleUser = UserProfile(
        id = 1,
        email = "alex@example.com",
        fullName = "Alex",
        weightKg = 70.0,
        heightCm = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private class FakeAuthRepository : AuthRepository {
        private val tokenState = MutableStateFlow(false)
        override val isLoggedIn: Flow<Boolean> = tokenState

        var registerCallCount = 0
        var lastWeight: Double? = null
        var registerResult: ApiResult<UserProfile> = ApiResult.NetworkError

        override suspend fun register(
            email: String, password: String, fullName: String,
            weightKg: Double?, heightCm: Double?,
        ): ApiResult<UserProfile> {
            registerCallCount++
            lastWeight = weightKg
            return registerResult
        }

        override suspend fun login(email: String, password: String): ApiResult<UserProfile> =
            ApiResult.NetworkError

        override suspend fun logout(): ApiResult<Unit> = ApiResult.Success(Unit)
    }
}
