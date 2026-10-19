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
import com.elijah.fitness.util.BodyMetrics
import com.elijah.fitness.util.UnitSystem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Owns the register-screen state machine.
 *
 * Mirrors [LoginViewModel]: client-side validation up-front, then a
 * network call. On success, `RegistrationComplete` fires and the
 * fragment sends the user to Log in (registering doesn't sign them in).
 * On per-field 422 the server messages hang off the right fields; on
 * any other 4xx/5xx the Snackbar path is used.
 *
 * Body metrics are entered in the [unitSystem] the user picked; the
 * ViewModel converts to kg / cm before the API call so the wire is
 * always metric.
 */
class RegisterViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val submitting: Boolean = false,
        val unitSystem: UnitSystem = UnitSystem.METRIC,
        val fullNameError: FieldError? = null,
        val emailError: FieldError? = null,
        val passwordError: FieldError? = null,
        val weightError: FieldError? = null,
        val heightError: FieldError? = null,
        val termsError: FieldError? = null,
    )

    sealed interface Event {
        /** Account created. [email] pre-fills the Log in screen. */
        data class RegistrationComplete(val email: String) : Event
        data class ShowNetworkError(@StringRes val messageRes: Int) : Event
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    fun onFullNameChanged() = clearIfSet { it.copy(fullNameError = null) }
    fun onEmailChanged() = clearIfSet { it.copy(emailError = null) }
    fun onPasswordChanged() = clearIfSet { it.copy(passwordError = null) }
    fun onWeightChanged() = clearIfSet { it.copy(weightError = null) }
    fun onHeightChanged() = clearIfSet { it.copy(heightError = null) }
    fun onTermsToggled() = clearIfSet { it.copy(termsError = null) }

    /** User flipped the Metric ↔ Imperial toggle. Clears numeric errors. */
    fun onUnitSystemChanged(unitSystem: UnitSystem) {
        _uiState.value = _uiState.value.copy(
            unitSystem = unitSystem,
            weightError = null,
            heightError = null,
        )
    }

    private fun clearIfSet(transform: (UiState) -> UiState) {
        _uiState.value = transform(_uiState.value)
    }

    fun submit(
        fullName: String,
        email: String,
        password: String,
        weightRaw: String,
        heightRaw: String,
        termsAccepted: Boolean,
    ) {
        if (_uiState.value.submitting) return
        val unitSystem = _uiState.value.unitSystem

        val nameResult = AuthValidators.validateFullName(fullName)
        val emailResult = AuthValidators.validateEmail(email)
        val passwordResult = AuthValidators.validatePassword(password)
        val weightResult = AuthValidators.validateWeight(weightRaw, unitSystem)
        val heightResult = AuthValidators.validateHeight(heightRaw, unitSystem)
        val termsResult = AuthValidators.validateTermsAccepted(termsAccepted)

        val nameErr = nameResult.toFieldError()
        val emailErr = emailResult.toFieldError()
        val passwordErr = passwordResult.toFieldError()
        val weightErr = weightResult.toFieldError()
        val heightErr = heightResult.toFieldError()
        val termsErr = termsResult.toFieldError()

        if (nameErr != null || emailErr != null || passwordErr != null ||
            weightErr != null || heightErr != null || termsErr != null) {
            _uiState.value = _uiState.value.copy(
                submitting = false,
                fullNameError = nameErr,
                emailError = emailErr,
                passwordError = passwordErr,
                weightError = weightErr,
                heightError = heightErr,
                termsError = termsErr,
            )
            return
        }

        // Convert to metric before hitting the API. The server + database
        // always store kg / cm; the unit system is a UI-only concern.
        val rawWeight = weightRaw.trim().toDouble()
        val rawHeight = heightRaw.trim().toDouble()
        val weightKg = when (unitSystem) {
            UnitSystem.METRIC -> rawWeight
            UnitSystem.IMPERIAL -> BodyMetrics.poundsToKilograms(rawWeight)
        }
        val heightCm = when (unitSystem) {
            UnitSystem.METRIC -> rawHeight
            UnitSystem.IMPERIAL -> BodyMetrics.inchesToCentimeters(rawHeight)
        }

        _uiState.value = _uiState.value.copy(submitting = true)
        val trimmedEmail = email.trim()
        viewModelScope.launch {
            val result = authRepository.register(
                email = trimmedEmail,
                password = password,
                fullName = fullName.trim(),
                weightKg = weightKg,
                heightCm = heightCm,
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = UiState(unitSystem = unitSystem)
                    _events.send(Event.RegistrationComplete(trimmedEmail))
                }
                is ApiResult.NetworkError -> {
                    _uiState.value = _uiState.value.copy(submitting = false)
                    _events.send(Event.ShowNetworkError(R.string.auth_error_network))
                }
                is ApiResult.Error -> handleServerError(result)
            }
        }
    }

    private suspend fun handleServerError(result: ApiResult.Error) {
        val f = result.fieldErrors
        val nameErr = f["fullName"]?.let(FieldError::Text)
        val emailErr = f["email"]?.let(FieldError::Text)
        val passwordErr = f["password"]?.let(FieldError::Text)
        val weightErr = f["weightKg"]?.let(FieldError::Text)
        val heightErr = f["heightCm"]?.let(FieldError::Text)

        if (nameErr != null || emailErr != null || passwordErr != null ||
            weightErr != null || heightErr != null) {
            _uiState.value = _uiState.value.copy(
                submitting = false,
                fullNameError = nameErr,
                emailError = emailErr,
                passwordError = passwordErr,
                weightError = weightErr,
                heightError = heightErr,
            )
        } else {
            _uiState.value = _uiState.value.copy(submitting = false)
            _events.send(Event.ShowNetworkError(R.string.auth_error_generic))
        }
    }

    private fun ValidationResult.toFieldError(): FieldError? =
        (this as? ValidationResult.Invalid)?.let { FieldError.Res(it.messageRes) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { RegisterViewModel(container.authRepository) }
        }
    }
}
