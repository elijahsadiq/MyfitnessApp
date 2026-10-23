package com.elijah.fitness.data.repository

import com.elijah.fitness.data.local.ProfileWeightStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Keeps [UserSettings]' profile weight across app restarts (phase 8d).
 *
 * [start] (once, at app start) reads the weight saved by the last
 * session into [UserSettings.restoreSaved] — unless a fresh profile got
 * there first — and from then on saves every weight that loads
 * ([UserSettings.loadedProfileWeight]). So a workout saved offline
 * right after a cold start still stores its calories at the user's
 * weight. [clear] is part of sign-out.
 *
 * Storage errors are swallowed: the weight is a convenience, and at
 * worst calories are worked out later from the profile.
 */
class ProfileWeightPersistence(
    private val settings: UserSettings,
    private val store: ProfileWeightStore,
    private val scope: CoroutineScope,
) {

    fun start() {
        scope.launch {
            val resetsBeforeRead = settings.resetCount
            val saved = safely { store.read() }
            if (saved != null) settings.restoreSaved(saved, resetsBeforeRead)

            settings.loadedProfileWeight.filterNotNull().collect { weight ->
                safely { store.save(weight) }
            }
        }
    }

    /** Sign-out: forget the weight so it can't carry over to the next account. */
    suspend fun clear() {
        safely { store.clear() }
    }

    private inline fun <T> safely(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
