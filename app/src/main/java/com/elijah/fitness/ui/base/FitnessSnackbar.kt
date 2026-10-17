package com.elijah.fitness.ui.base

import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.elijah.fitness.R
import com.google.android.material.snackbar.Snackbar

/**
 * What a Snackbar is telling the user. Picks the round status badge
 * shown before the message; the card itself (dark surface, rounded
 * corners, lime action) comes from the theme's `snackbarStyle`.
 */
enum class SnackKind(@DrawableRes val badgeRes: Int) {
    /** Something worked: saved, updated, deleted. Lime check. */
    SUCCESS(R.drawable.ic_snack_success),

    /** Something failed or needs fixing: offline, couldn't save. Red "!". */
    ERROR(R.drawable.ic_snack_error),

    /** Neutral heads-up: a permission was refused, a setting is off. Lime "i". */
    INFO(R.drawable.ic_snack_info),
}

/**
 * Puts the [kind] badge at the start of this Snackbar's message. Every
 * Snackbar in the app goes through here, either via
 * [BaseFragment.snackbar] or directly for the few built with
 * `Snackbar.make`.
 */
fun Snackbar.withKind(kind: SnackKind): Snackbar {
    val message = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        ?: return this
    val badge = ContextCompat.getDrawable(context, kind.badgeRes) ?: return this
    message.setCompoundDrawablesRelativeWithIntrinsicBounds(badge, null, null, null)
    message.compoundDrawablePadding =
        context.resources.getDimensionPixelSize(R.dimen.space_md)
    return this
}
