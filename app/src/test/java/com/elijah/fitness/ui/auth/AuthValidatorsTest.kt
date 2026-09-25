package com.elijah.fitness.ui.auth

import com.elijah.fitness.R
import com.elijah.fitness.ui.auth.AuthValidators.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthValidatorsTest {

    // ---------- email ----------

    @Test fun `email empty is invalid`() {
        val result = AuthValidators.validateEmail("")
        assertEquals(ValidationResult.Invalid(R.string.auth_error_email_required), result)
    }

    @Test fun `email whitespace only is invalid`() {
        val result = AuthValidators.validateEmail("   ")
        assertEquals(ValidationResult.Invalid(R.string.auth_error_email_required), result)
    }

    @Test fun `email without at sign is invalid`() {
        val result = AuthValidators.validateEmail("not-an-email")
        assertEquals(ValidationResult.Invalid(R.string.auth_error_email_invalid), result)
    }

    @Test fun `email without tld is invalid`() {
        val result = AuthValidators.validateEmail("alex@example")
        assertEquals(ValidationResult.Invalid(R.string.auth_error_email_invalid), result)
    }

    @Test fun `well-formed email is valid`() {
        assertEquals(ValidationResult.Ok, AuthValidators.validateEmail("alex@example.com"))
    }

    @Test fun `well-formed email with plus tag is valid`() {
        assertEquals(ValidationResult.Ok, AuthValidators.validateEmail("alex+fitness@sub.example.com"))
    }

    // ---------- password ----------

    @Test fun `password empty is invalid`() {
        assertEquals(
            ValidationResult.Invalid(R.string.auth_error_password_required),
            AuthValidators.validatePassword(""),
        )
    }

    @Test fun `password shorter than 8 chars is invalid`() {
        assertEquals(
            ValidationResult.Invalid(R.string.auth_error_password_too_short),
            AuthValidators.validatePassword("abc12"),
        )
    }

    @Test fun `password exactly 8 chars is valid`() {
        assertEquals(ValidationResult.Ok, AuthValidators.validatePassword("abcdefgh"))
    }

    // ---------- full name ----------

    @Test fun `full name empty is invalid`() {
        assertEquals(
            ValidationResult.Invalid(R.string.auth_error_name_required),
            AuthValidators.validateFullName(""),
        )
    }

    @Test fun `full name 120 chars is valid, 121 is invalid`() {
        val ok = "a".repeat(120)
        val bad = "a".repeat(121)
        assertEquals(ValidationResult.Ok, AuthValidators.validateFullName(ok))
        assertEquals(
            ValidationResult.Invalid(R.string.auth_error_name_too_long),
            AuthValidators.validateFullName(bad),
        )
    }

    // ---------- weight (metric) ----------

    @Test fun `weight empty is now required`() {
        assertEquals(
            ValidationResult.Invalid(R.string.auth_error_weight_required),
            AuthValidators.validateWeight("", com.elijah.fitness.util.UnitSystem.METRIC),
        )
    }

    @Test fun `weight non-numeric is invalid`() {
        assertEquals(
            ValidationResult.Invalid(R.string.auth_error_weight_invalid),
            AuthValidators.validateWeight("light", com.elijah.fitness.util.UnitSystem.METRIC),
        )
    }

    @Test fun `weight below metric range is invalid`() {
        assertEquals(
            ValidationResult.Invalid(R.string.auth_error_weight_out_of_range_metric),
            AuthValidators.validateWeight("10", com.elijah.fitness.util.UnitSystem.METRIC),
        )
    }

    @Test fun `weight above metric range is invalid`() {
        assertEquals(
            ValidationResult.Invalid(R.string.auth_error_weight_out_of_range_metric),
            AuthValidators.validateWeight("500", com.elijah.fitness.util.UnitSystem.METRIC),
        )
    }

    @Test fun `weight in metric range is valid`() {
        assertEquals(
            ValidationResult.Ok,
            AuthValidators.validateWeight("70.5", com.elijah.fitness.util.UnitSystem.METRIC),
        )
    }

    // ---------- weight (imperial) ----------

    @Test fun `weight in imperial range is valid`() {
        // 155 lb ~= 70 kg — inside range.
        assertEquals(
            ValidationResult.Ok,
            AuthValidators.validateWeight("155", com.elijah.fitness.util.UnitSystem.IMPERIAL),
        )
    }

    @Test fun `weight below imperial range uses imperial error string`() {
        assertEquals(
            ValidationResult.Invalid(R.string.auth_error_weight_out_of_range_imperial),
            AuthValidators.validateWeight("10", com.elijah.fitness.util.UnitSystem.IMPERIAL),
        )
    }

    // ---------- height ----------

    @Test fun `height empty is required`() {
        assertEquals(
            ValidationResult.Invalid(R.string.auth_error_height_required),
            AuthValidators.validateHeight("", com.elijah.fitness.util.UnitSystem.METRIC),
        )
    }

    @Test fun `height metric in range is valid`() {
        assertEquals(
            ValidationResult.Ok,
            AuthValidators.validateHeight("178", com.elijah.fitness.util.UnitSystem.METRIC),
        )
    }

    @Test fun `height metric below range is invalid`() {
        assertEquals(
            ValidationResult.Invalid(R.string.auth_error_height_out_of_range_metric),
            AuthValidators.validateHeight("50", com.elijah.fitness.util.UnitSystem.METRIC),
        )
    }

    @Test fun `height imperial in range is valid`() {
        // 70 in ~= 178 cm.
        assertEquals(
            ValidationResult.Ok,
            AuthValidators.validateHeight("70", com.elijah.fitness.util.UnitSystem.IMPERIAL),
        )
    }

    @Test fun `height imperial above range uses imperial error string`() {
        assertEquals(
            ValidationResult.Invalid(R.string.auth_error_height_out_of_range_imperial),
            AuthValidators.validateHeight("200", com.elijah.fitness.util.UnitSystem.IMPERIAL),
        )
    }

    // ---------- terms ----------

    @Test fun `terms unaccepted is invalid`() {
        assertEquals(
            ValidationResult.Invalid(R.string.auth_error_terms_required),
            AuthValidators.validateTermsAccepted(false),
        )
    }

    @Test fun `terms accepted is valid`() {
        assertEquals(ValidationResult.Ok, AuthValidators.validateTermsAccepted(true))
    }
}
