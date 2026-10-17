package com.elijah.fitness.ui.base

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController

/**
 * Navigate only if [actionId] exists on the *current* destination.
 *
 * Guards against the classic double-tap crash: the first tap moves the
 * NavController to a new destination, the second (queued) tap then asks for
 * an action that only existed on the screen we just left and NavController
 * throws `IllegalArgumentException`. Views stay clickable until the next
 * frame, so fast double-taps (or two fingers) hit this reliably.
 *
 * After the first successful call the current destination no longer carries
 * the action, so the duplicate call becomes a no-op instead of a crash.
 */
fun NavController.navigateSafe(@IdRes actionId: Int, args: Bundle? = null) {
    if (currentDestination?.getAction(actionId) != null) {
        navigate(actionId, args)
    }
}
