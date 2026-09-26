package com.elijah.fitness.ui.goals

import androidx.annotation.StringRes
import com.elijah.fitness.data.model.GoalWithProgress

/**
 * One row of the goals RecyclerView. A sealed hierarchy so the adapter's
 * `when` is exhaustive and the DiffUtil callback can compare by variant.
 *
 * The featured card is a full-width variant of a "row" so it participates
 * in the same list scroll — no need for a header adapter concat.
 */
sealed interface GoalListItem {

    /** Section title (with optional count pill) between groups of rows. */
    data class SectionHeader(
        @StringRes val labelRes: Int,
        val countPill: String? = null,
    ) : GoalListItem

    /** The single most-urgent incomplete goal, rendered as the dark hero card. */
    data class Featured(val goal: GoalWithProgress) : GoalListItem

    /** An active (incomplete) non-featured goal. */
    data class Active(val goal: GoalWithProgress) : GoalListItem

    /** A completed goal (progressFraction == 1.0). */
    data class Completed(val goal: GoalWithProgress) : GoalListItem
}
