package com.elijah.fitness.data.repository

import com.elijah.fitness.data.local.OnboardingStore
import com.elijah.fitness.data.model.UserProfile
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.data.remote.TokenStore
import com.elijah.fitness.data.remote.api.AuthApi
import com.elijah.fitness.data.remote.apiCall
import com.elijah.fitness.data.remote.apiCallEmpty
import com.elijah.fitness.data.remote.dto.LoginRequestDto
import com.elijah.fitness.data.remote.dto.RegisterRequestDto
import com.elijah.fitness.data.remote.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Retrofit-backed [AuthRepository]. Persists the returned bearer token in
 * [TokenStore] on login and clears it on logout. Register only creates
 * the account: the user then signs in on the Log in screen.
 *
 * Also marks onboarding as completed on successful register / login so
 * a user who reinstalls without clearing app data — or hits register on
 * a fresh install — doesn't see the onboarding pages a second time.
 */
class RemoteAuthRepository(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val onboardingStore: OnboardingStore,
    private val userSettings: UserSettings,
) : AuthRepository {

    override val isLoggedIn: Flow<Boolean> =
        tokenStore.token.map { !it.isNullOrBlank() }

    override suspend fun register(
        email: String,
        password: String,
        fullName: String,
        weightKg: Double?,
        heightCm: Double?,
    ): ApiResult<UserProfile> {
        val request = RegisterRequestDto(email, password, fullName, weightKg, heightCm)
        return when (val result = apiCall { authApi.register(request) }) {
            is ApiResult.Success -> {
                // No token: registering doesn't start a session. Login
                // hydrates the body metrics once the user signs in.
                onboardingStore.markCompleted()
                ApiResult.Success(result.data.user.toDomain())
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    override suspend fun login(email: String, password: String): ApiResult<UserProfile> {
        val request = LoginRequestDto(email, password)
        return when (val result = apiCall { authApi.login(request) }) {
            is ApiResult.Success -> {
                tokenStore.save(result.data.token)
                onboardingStore.markCompleted()
                val profile = result.data.user.toDomain()
                userSettings.hydrateFrom(profile.weightKg, profile.heightCm)
                ApiResult.Success(profile)
            }
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }
    }

    override suspend fun logout(): ApiResult<Unit> {
        val result = apiCallEmpty { authApi.logout() }
        // Local logout succeeds unconditionally — even if the server call
        // fails, the user is "logged out" locally so their token doesn't
        // keep them signed in on the device.
        tokenStore.clear()
        return when (result) {
            is ApiResult.Success -> result
            is ApiResult.Error -> ApiResult.Success(Unit)
            ApiResult.NetworkError -> ApiResult.Success(Unit)
        }
    }
}
