package com.elijah.fitness.ui.auth

import androidx.annotation.StringRes
import com.elijah.fitness.R
import com.elijah.fitness.util.BodyMetrics
import com.elijah.fitness.util.UnitSystem

/**
 * Pure-Kotlin validators for the auth forms. No Android imports beyond
 * `@StringRes` and the generated `R`, so every branch is JVM-testable.
 *
 * Each validator returns a [ValidationResult]. `Ok` for a valid value,
 * `Invalid(messageRes)` for a specific reason.
 */
object AuthValidators {

    /** Minimum password length. Kept as a constant so tests + UI hint stay aligned. */
    const val PASSWORD_MIN_LENGTH: Int = 8

    // Human plausibility bounds in metric. Imperial ranges are derived
    // from these — the app stores everything in kg / cm regardless.
    const val MIN_WEIGHT_KG: Double = 20.0
    const val MAX_WEIGHT_KG: Double = 300.0
    const val MIN_HEIGHT_CM: Double = 90.0
    const val MAX_HEIGHT_CM: Double = 250.0

    sealed interface ValidationResult {
        data object Ok : ValidationResult
        data class Invalid(@StringRes val messageRes: Int) : ValidationResult
    }

    fun validateEmail(email: String?): ValidationResult {
        val trimmed = email?.trim().orEmpty()
        if (trimmed.isEmpty()) return ValidationResult.Invalid(R.string.auth_error_email_required)
        // Deliberately not using android.util.Patterns so this stays JVM-testable.
        val regex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return if (regex.matches(trimmed)) ValidationResult.Ok
        else ValidationResult.Invalid(R.string.auth_error_email_invalid)
    }

    fun validatePassword(password: String?): ValidationResult = when {
        password.isNullOrEmpty() ->
            ValidationResult.Invalid(R.string.auth_error_password_required)
        password.length < PASSWORD_MIN_LENGTH ->
            ValidationResult.Invalid(R.string.auth_error_password_too_short)
        else -> ValidationResult.Ok
    }

    fun validateFullName(name: String?): ValidationResult {
        val trimmed = name?.trim().orEmpty()
        return when {
            trimmed.isEmpty() -> ValidationResult.Invalid(R.string.auth_error_name_required)
            trimmed.length > 120 -> ValidationResult.Invalid(R.string.auth_error_name_too_long)
            else -> ValidationResult.Ok
        }
    }

    /**
     * Body weight is now required (BMI depends on it). [unitSystem]
     * determines both the acceptable numeric range and which error
     * string is returned. Storage on the server is always in kg, but
     * the input string is in the user's chosen unit.
     */
    fun validateWeight(raw: String?, unitSystem: UnitSystem): ValidationResult {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ValidationResult.Invalid(R.string.auth_error_weight_required)
        val value = trimmed.toDoubleOrNull()
            ?: return ValidationResult.Invalid(R.string.auth_error_weight_invalid)
        val (min, max, errorRes) = when (unitSystem) {
            UnitSystem.METRIC -> Triple(MIN_WEIGHT_KG, MAX_WEIGHT_KG,
                R.string.auth_error_weight_out_of_range_metric)
            UnitSystem.IMPERIAL -> Triple(
                BodyMetrics.kilogramsToPounds(MIN_WEIGHT_KG),
                BodyMetrics.kilogramsToPounds(MAX_WEIGHT_KG),
                R.string.auth_error_weight_out_of_range_imperial,
            )
        }
        return if (value in min..max) ValidationResult.Ok
        else ValidationResult.Invalid(errorRes)
    }

    /**
     * Height is required (BMI depends on it). Same unit-aware pattern
     * as [validateWeight] — [unitSystem] picks the range + error string.
     */
    fun validateHeight(raw: String?, unitSystem: UnitSystem): ValidationResult {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ValidationResult.Invalid(R.string.auth_error_height_required)
        val value = trimmed.toDoubleOrNull()
            ?: return ValidationResult.Invalid(R.string.auth_error_height_invalid)
        val (min, max, errorRes) = when (unitSystem) {
            UnitSystem.METRIC -> Triple(MIN_HEIGHT_CM, MAX_HEIGHT_CM,
                R.string.auth_error_height_out_of_range_metric)
            UnitSystem.IMPERIAL -> Triple(
                BodyMetrics.centimetersToInches(MIN_HEIGHT_CM),
                BodyMetrics.centimetersToInches(MAX_HEIGHT_CM),
                R.string.auth_error_height_out_of_range_imperial,
            )
        }
        return if (value in min..max) ValidationResult.Ok
        else ValidationResult.Invalid(errorRes)
    }

    fun validateTermsAccepted(accepted: Boolean): ValidationResult =
        if (accepted) ValidationResult.Ok
        else ValidationResult.Invalid(R.string.auth_error_terms_required)
}
