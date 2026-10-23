package com.elijah.fitness.ui.components

import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType

/**
 * One card in an [ActivityCarouselView]: icon, name and a one-line
 * description, all string / drawable resources.
 *
 * [activity] is `null` for the New goal sheet's "Any" card (the same
 * "no filter" meaning `null` has on a goal). [spokenNameRes] is what
 * TalkBack says instead of the visible name when the two differ (the
 * "Any" card reads "Any activity").
 */
data class ActivityCardItem(
    val activity: ActivityType?,
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:StringRes val spokenNameRes: Int? = null,
) {
    /** The name TalkBack reads: [spokenNameRes], else the visible [nameRes]. */
    @get:StringRes
    val accessibilityNameRes: Int get() = spokenNameRes ?: nameRes
}

/**
 * Size of the cards in an [ActivityCarouselView], so the Log a workout
 * sheet (big cards) and the New goal sheet (smaller cards) share one
 * carousel. Every value is a dimen resource; the view resolves them,
 * which keeps the numbers in `dimens.xml` with the rest of the design.
 *
 * The description's line height is a ratio of its text size (the
 * designs' CSS `line-height`), so it scales with the user's font size.
 */
data class ActivityCardSpec(
    @param:DimenRes val cardWidth: Int,
    @param:DimenRes val cardHeight: Int,
    @param:DimenRes val cornerRadius: Int,
    @param:DimenRes val padding: Int,
    @param:DimenRes val iconTileSize: Int,
    @param:DimenRes val iconTileRadius: Int,
    @param:DimenRes val iconSize: Int,
    @param:DimenRes val nameTextSize: Int,
    @param:DimenRes val descriptionTextSize: Int,
    val descriptionLineHeight: Float,
    /** Minimum space between the icon tile, the name and the description. */
    @param:DimenRes val contentGap: Int,
    /** Space between two neighbouring cards. */
    @param:DimenRes val cardGap: Int,
) {
    companion object {
        /** Log a workout sheet: 250 x 270dp cards (LogSheet.dc.html). */
        val LOG_SHEET = ActivityCardSpec(
            cardWidth = R.dimen.activity_card_log_width,
            cardHeight = R.dimen.activity_card_log_height,
            cornerRadius = R.dimen.activity_card_log_radius,
            padding = R.dimen.activity_card_log_padding,
            iconTileSize = R.dimen.activity_card_log_icon_tile,
            iconTileRadius = R.dimen.activity_card_log_icon_tile_radius,
            iconSize = R.dimen.activity_card_log_icon,
            nameTextSize = R.dimen.activity_card_log_name_text,
            descriptionTextSize = R.dimen.activity_card_log_description_text,
            descriptionLineHeight = 1.4f,
            contentGap = R.dimen.activity_card_log_content_gap,
            cardGap = R.dimen.activity_card_log_gap,
        )

        /** New goal sheet: 200 x 186dp cards (NewGoal.dc.html). */
        val GOAL_SHEET = ActivityCardSpec(
            cardWidth = R.dimen.activity_card_goal_width,
            cardHeight = R.dimen.activity_card_goal_height,
            cornerRadius = R.dimen.activity_card_goal_radius,
            padding = R.dimen.activity_card_goal_padding,
            iconTileSize = R.dimen.activity_card_goal_icon_tile,
            iconTileRadius = R.dimen.activity_card_goal_icon_tile_radius,
            iconSize = R.dimen.activity_card_goal_icon,
            nameTextSize = R.dimen.activity_card_goal_name_text,
            descriptionTextSize = R.dimen.activity_card_goal_description_text,
            descriptionLineHeight = 1.35f,
            contentGap = R.dimen.activity_card_goal_content_gap,
            cardGap = R.dimen.activity_card_goal_gap,
        )
    }
}
