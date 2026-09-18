package com.elijah.fitness.ui.auth

import androidx.annotation.StringRes

/**
 * Field-scoped error used by both `LoginViewModel` and
 * `RegisterViewModel`. Client-side validators emit `Res` (a string
 * resource); server-side 4xx responses emit `Text` with the raw message
 * from the API. The fragment merges both when rendering.
 */
sealed interface FieldError {
    data class Res(@StringRes val messageRes: Int) : FieldError
    data class Text(val message: String) : FieldError
}
