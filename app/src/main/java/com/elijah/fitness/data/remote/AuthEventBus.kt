package com.elijah.fitness.data.remote

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Broadcasts app-wide auth signals. Right now there's only one — a 401
 * response on any authenticated endpoint — but this is the seam for
 * anything the app needs to react to globally (session expired, forced
 * logout after a password change, etc.).
 *
 * `extraBufferCapacity = 1` + `DROP_OLDEST` means multiple simultaneous
 * 401s collapse to a single event so we don't ping-pong to the login
 * screen more than once per burst.
 */
class AuthEventBus {

    private val _events = MutableSharedFlow<AuthEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun postUnauthorized() {
        _events.tryEmit(AuthEvent.Unauthorized)
    }

    sealed interface AuthEvent {
        data object Unauthorized : AuthEvent
    }
}
