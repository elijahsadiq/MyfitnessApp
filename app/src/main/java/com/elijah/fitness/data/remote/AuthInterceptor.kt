package com.elijah.fitness.data.remote

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <token>` to every outbound request except
 * the two auth endpoints that create the token in the first place, and
 * broadcasts an [AuthEventBus.AuthEvent.Unauthorized] event when the
 * server returns 401 on a non-auth endpoint (session expired / token
 * revoked).
 *
 * `runBlocking` is intentional: OkHttp interceptors run on their own
 * dispatcher (never the main thread), and the DataStore read is
 * cheap — no risk of ANR.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val authEventBus: AuthEventBus,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isAuth = isAuthEndpoint(request.url.encodedPath)

        val outbound = if (isAuth) {
            request
        } else {
            val token = runBlocking { tokenStore.token.first() }
            if (token.isNullOrBlank()) {
                request
            } else {
                request.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            }
        }

        val response = chain.proceed(outbound)

        // A 401 on a non-auth call means the token we sent was rejected —
        // fire the app-wide event so MainActivity can bounce the user
        // back to the login screen. The 401 that legitimately comes out
        // of /auth/login for wrong-password stays a plain error to the
        // caller.
        if (!isAuth && response.code == 401) {
            authEventBus.postUnauthorized()
        }
        return response
    }

    private fun isAuthEndpoint(path: String): Boolean =
        path.endsWith("/auth/register") || path.endsWith("/auth/login")
}
