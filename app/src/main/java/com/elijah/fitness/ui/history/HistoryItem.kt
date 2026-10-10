package com.elijah.fitness.ui.history

import androidx.annotation.StringRes
import com.elijah.fitness.data.model.Workout

/**
 * One row in the history RecyclerView. Sealed so `WorkoutAdapter` can
 * dispatch view types by class — `SectionHeader` for the pill row with
 * a per-week summary, `Row` for a workout (adapter still picks the
 * distance/strength sub-type by the workout's own class).
 */
sealed interface HistoryItem {
    data class SectionHeader(
        @StringRes val labelRes: Int,
        val count: Int,
        val distanceMeters: Double,
    ) : HistoryItem

    /** [unsynced]: saved or edited offline, not on the server yet. */
    data class Row(val workout: Workout, val unsynced: Boolean = false) : HistoryItem
}
