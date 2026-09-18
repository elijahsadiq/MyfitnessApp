package com.elijah.fitness.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elijah.fitness.AppContainer
import com.elijah.fitness.R
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.repository.AuthRepository
import com.elijah.fitness.ui.auth.AuthValidators.ValidationResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Owns the login-screen state machine.
 *
 * ### State
 * [UiState] exposes the loading spinner + per-field errors. Client-side
 * validators produce `FieldError.Res` (string resource); the server's
 * `fields` map produces `FieldError.Text` (raw message). The fragment
 * resolves either and hangs it off the matching `TextInputLayout`.
 *
 * ### Events
 * One-shot outcomes are pushed through a [Channel]:
 * - `LoggedIn` — token stored; the fragment should navigate to Main.
 * - `ShowNetworkError` — offline / DNS / timeout; the fragment shows a
 *   Snackbar with a Retry action (per spec, not a field error).
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val submitting: Boolean = false,
        val emailError: FieldError? = null,
        val passwordError: FieldError? = null,
    )

    sealed interface Event {
        data object LoggedIn : Event
        data class ShowNetworkError(@StringRes val messageRes: Int) : Event
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    /** Clear the emailError as the user starts fixing it. */
    fun onEmailChanged() {
        if (_uiState.value.emailError != null) {
            _uiState.value = _uiState.value.copy(emailError = null)
        }
    }

    /** Clear the passwordError as the user starts fixing it. */
    fun onPasswordChanged() {
        if (_uiState.value.passwordError != null) {
            _uiState.value = _uiState.value.copy(passwordError = null)
        }
    }

    fun submit(email: String, password: String) {
        if (_uiState.value.submitting) return

        val emailResult = AuthValidators.validateEmail(email)
        val passwordResult = AuthValidators.validatePassword(password)
        val clientEmailError = (emailResult as? ValidationResult.Invalid)?.let { FieldError.Res(it.messageRes) }
        val clientPasswordError = (passwordResult as? ValidationResult.Invalid)?.let { FieldError.Res(it.messageRes) }
        if (clientEmailError != null || clientPasswordError != null) {
            _uiState.value = UiState(
                submitting = false,
                emailError = clientEmailError,
                passwordError = clientPasswordError,
            )
            return
        }

        _uiState.value = UiState(submitting = true)
        viewModelScope.launch {
            when (val result = authRepository.login(email.trim(), password)) {
                is ApiResult.Success -> {
                    _uiState.value = UiState()
                    _events.send(Event.LoggedIn)
                }
                is ApiResult.NetworkError -> {
                    _uiState.value = UiState()
                    _events.send(Event.ShowNetworkError(R.string.auth_error_network))
                }
                is ApiResult.Error -> handleServerError(result)
            }
        }
    }

    private suspend fun handleServerError(result: ApiResult.Error) {
        // 401 has no field map — treat as "wrong credentials" and hang the
        // message off the password row (the API deliberately returns one
        // opaque message so the response can't be used to enumerate emails).
        if (result.status == 401 && result.fieldErrors.isEmpty()) {
            _uiState.value = UiState(
                submitting = false,
                emailError = null,
                passwordError = FieldError.Text(result.message),
            )
            return
        }

        val emailErr = result.fieldErrors["email"]?.let(FieldError::Text)
        val passwordErr = result.fieldErrors["password"]?.let(FieldError::Text)

        if (emailErr != null || passwordErr != null) {
            _uiState.value = UiState(
                submitting = false,
                emailError = emailErr,
                passwordError = passwordErr,
            )
        } else {
            // Server error with no field mapping — Snackbar instead of a
            // silent submit that pretends nothing happened.
            _uiState.value = UiState()
            _events.send(Event.ShowNetworkError(R.string.auth_error_generic))
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel(container.authRepository) }
        }
    }
}
