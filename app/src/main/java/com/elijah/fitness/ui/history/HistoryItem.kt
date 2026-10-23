package com.elijah.fitness.ui.history

import androidx.annotation.StringRes
import com.elijah.fitness.data.model.DistanceUnit
import com.elijah.fitness.data.model.Workout

/**
 * One row in the history RecyclerView. Sealed so `WorkoutAdapter` can
 * dispatch view types by class — `SectionHeader` for the pill row with
 * a per-week summary, `Row` for a workout (adapter still picks the
 * distance/strength sub-type by the workout's own class).
 */
sealed interface HistoryItem {
    /**
     * [distanceUnit]: the unit the total reads in — metres ("1,500 m")
     * when every distance workout in the section is shown in metres (only
     * swims), like the rows under it; km otherwise ([DistanceUnit.forTotal]).
     */
    data class SectionHeader(
        @StringRes val labelRes: Int,
        val count: Int,
        val distanceMeters: Double,
        val distanceUnit: DistanceUnit = DistanceUnit.KILOMETRES,
    ) : HistoryItem

    /** [unsynced]: saved or edited offline, not on the server yet. */
    data class Row(val workout: Workout, val unsynced: Boolean = false) : HistoryItem
}
