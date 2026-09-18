package com.elijah.fitness.ui.auth

import androidx.annotation.StringRes
import com.elijah.fitness.R

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

    const val MIN_WEIGHT_KG: Double = 20.0
    const val MAX_WEIGHT_KG: Double = 300.0

    sealed interface ValidationResult {
        data object Ok : ValidationResult
        data class Invalid(@StringRes val messageRes: Int) : ValidationResult
    }

    fun validateEmail(email: String?): ValidationResult {
        val trimmed = email?.trim().orEmpty()
        if (trimmed.isEmpty()) return ValidationResult.Invalid(R.string.auth_error_email_required)
        // Deliberately not using android.util.Patterns so this stays JVM-testable.
        // The pattern covers RFC 5321-style local-part@domain.tld with subdomains.
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
     * Weight is optional at the API level, so an empty input is `Ok`.
     * If typed, it must be a number in the plausible human range.
     */
    fun validateWeightKg(raw: String?): ValidationResult {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ValidationResult.Ok
        val value = trimmed.toDoubleOrNull()
            ?: return ValidationResult.Invalid(R.string.auth_error_weight_invalid)
        return if (value in MIN_WEIGHT_KG..MAX_WEIGHT_KG) ValidationResult.Ok
        else ValidationResult.Invalid(R.string.auth_error_weight_out_of_range)
    }

    fun validateTermsAccepted(accepted: Boolean): ValidationResult =
        if (accepted) ValidationResult.Ok
        else ValidationResult.Invalid(R.string.auth_error_terms_required)
}
