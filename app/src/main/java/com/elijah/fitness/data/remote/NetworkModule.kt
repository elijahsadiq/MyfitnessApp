package com.elijah.fitness.data.remote

import com.elijah.fitness.BuildConfig
import com.elijah.fitness.data.remote.api.AuthApi
import com.elijah.fitness.data.remote.api.GoalApi
import com.elijah.fitness.data.remote.api.UserApi
import com.elijah.fitness.data.remote.api.WorkoutApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create
import java.util.concurrent.TimeUnit

/**
 * Assembles the shared OkHttp + Retrofit stack. Called once by
 * [com.elijah.fitness.AppContainer]; the resulting API interfaces
 * (`AuthApi`, `UserApi`, `WorkoutApi`) are singletons for the app's
 * lifetime.
 *
 * The HTTP logging interceptor is added **only in debug builds** —
 * production traffic never has bodies dumped to logcat.
 */
object NetworkModule {

    // Kept lenient on unknown / null fields so a backend that grows a new
    // response field doesn't crash the client mid-release.
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun buildRetrofit(tokenStore: TokenStore, authEventBus: AuthEventBus): Retrofit {
        // Generous read timeout: the free Render server sleeps after 15
        // minutes idle and takes about a minute to wake. Render holds the
        // first request until the server is up, so we wait rather than
        // fail. An offline phone still fails fast (no DNS / no route).
        val httpBuilder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // Say we're an API client, so a proxy never answers with an
            // HTML page meant for browsers.
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder().header("Accept", "application/json").build()
                )
            }
            .addInterceptor(AuthInterceptor(tokenStore, authEventBus))

        if (BuildConfig.DEBUG) {
            httpBuilder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
        }

        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(httpBuilder.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    fun buildAuthApi(retrofit: Retrofit): AuthApi = retrofit.create()
    fun buildUserApi(retrofit: Retrofit): UserApi = retrofit.create()
    fun buildWorkoutApi(retrofit: Retrofit): WorkoutApi = retrofit.create()
    fun buildGoalApi(retrofit: Retrofit): GoalApi = retrofit.create()
}
