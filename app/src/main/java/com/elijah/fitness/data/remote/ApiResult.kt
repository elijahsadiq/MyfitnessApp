package com.elijah.fitness.data.remote

/**
 * Wraps every network call. The UI layer never sees a raw exception:
 * repositories translate everything into one of these three cases and
 * ViewModels branch on the type.
 *
 * - [Success] — HTTP 2xx and the envelope was `{success: true}`.
 * - [Error]   — HTTP error, or the envelope was `{success: false}`.
 *              `fieldErrors` mirrors the server's per-field validation
 *              messages so the UI can highlight the right TextInputLayout.
 * - [NetworkError] — no HTTP response at all (offline, DNS, timeout).
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>

    data class Error(
        val message: String,
        val status: Int,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : ApiResult<Nothing>

    data object NetworkError : ApiResult<Nothing>
}
