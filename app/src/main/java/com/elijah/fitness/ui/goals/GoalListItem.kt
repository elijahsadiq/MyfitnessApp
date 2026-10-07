package com.elijah.fitness.ui.goals

import androidx.annotation.StringRes
import com.elijah.fitness.data.model.GoalWithProgress

/**
 * One row of the goals RecyclerView. A sealed hierarchy so the adapter's
 * `when` is exhaustive and the DiffUtil callback can compare by variant.
 *
 * Phase-8b (Main.dc.html) sections:
 *   Header → optional MainGoalCard → section header + active rows →
 *   section header + completed rows → section header + ended rows.
 *
 * Sections with no items are omitted entirely.
 */
sealed interface GoalListItem {

    /**
     * Title row at the top of the screen: eyebrow ("3 active · 1
     * completed"), "Goals" headline, and the lime "+ New goal" pill.
     */
    data class Header(
        val activeCount: Int,
        val completedCount: Int,
    ) : GoalListItem

    /** Section title (with optional count pill) between groups of rows. */
    data class SectionHeader(
        @StringRes val labelRes: Int,
        val countPill: String? = null,
    ) : GoalListItem

    /**
     * The user's main goal — if there is one — rendered as the dark card
     * directly below the header. The screen shows at most one of these.
     */
    data class MainGoalCard(val goal: GoalWithProgress) : GoalListItem

    /** An active (incomplete) non-main goal. */
    data class Active(val goal: GoalWithProgress) : GoalListItem

    /** A completed goal (progressFraction == 1.0). */
    data class Completed(val goal: GoalWithProgress) : GoalListItem

    /**
     * A goal whose period has passed with the target unmet. Rendered in
     * its own section below Completed; the final value is shown as
     * `N of M units` so the user still sees how close they got.
     */
    data class Ended(val goal: GoalWithProgress) : GoalListItem

    /**
     * Empty-state hero card (phase 8b step 8). Rendered once below the
     * header when the user has no goals at all. Taps the dark card's
     * "Create a goal" pill to open the New Goal screen blank.
     */
    data object EmptyHeroCard : GoalListItem

    /**
     * Quick Start row — pre-filled New Goal suggestion. Only rendered
     * in the empty-state composition.
     */
    data class QuickStart(val suggestion: GoalSuggestion) : GoalListItem
}
