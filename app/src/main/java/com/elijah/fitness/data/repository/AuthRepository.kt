package com.elijah.fitness.data.repository

import com.elijah.fitness.data.model.UserProfile
import com.elijah.fitness.data.remote.ApiResult
import kotlinx.coroutines.flow.Flow

/**
 * Auth surface for the UI layer. Repositories hide *where* the token
 * lives and *how* it's issued, so a future migration (biometrics,
 * refresh tokens, SSO) doesn't ripple into every ViewModel.
 */
interface AuthRepository {

    /** Emits `true` while a valid token is stored, `false` otherwise. */
    val isLoggedIn: Flow<Boolean>

    /** Creates the account only. The user signs in afterwards with [login]. */
    suspend fun register(
        email: String,
        password: String,
        fullName: String,
        weightKg: Double? = null,
        heightCm: Double? = null,
    ): ApiResult<UserProfile>

    suspend fun login(email: String, password: String): ApiResult<UserProfile>

    /**
     * Best-effort logout: always clears the local token even if the
     * network call fails, so the user isn't stuck "logged in" locally
     * when they can't reach the server.
     */
    suspend fun logout(): ApiResult<Unit>
}
