package com.elijah.fitness.data.remote

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <token>` to every outbound request except
 * the two auth endpoints that create the token in the first place.
 *
 * `runBlocking` is intentional: OkHttp interceptors run on their own
 * dispatcher (never the main thread), and the DataStore read is
 * cheap — no risk of ANR.
 */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (isAuthEndpoint(request.url.encodedPath)) {
            return chain.proceed(request)
        }

        val token = runBlocking { tokenStore.token.first() }
        val authed = if (token.isNullOrBlank()) {
            request
        } else {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(authed)
    }

    private fun isAuthEndpoint(path: String): Boolean =
        path.endsWith("/auth/register") || path.endsWith("/auth/login")
}
