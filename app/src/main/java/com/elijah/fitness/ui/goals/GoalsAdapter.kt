package com.elijah.fitness.ui.goals

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.data.remote.mapper.goalType
import com.elijah.fitness.databinding.ItemGoalActiveBinding
import com.elijah.fitness.databinding.ItemGoalCompletedBinding
import com.elijah.fitness.databinding.ItemGoalEmptyHeroBinding
import com.elijah.fitness.databinding.ItemGoalEndedBinding
import com.elijah.fitness.databinding.ItemGoalMainCardBinding
import com.elijah.fitness.databinding.ItemGoalQuickStartBinding
import com.elijah.fitness.databinding.ItemGoalsHeaderBinding
import com.elijah.fitness.databinding.ItemGoalsSectionHeaderBinding
import com.elijah.fitness.ui.base.BaseViewHolder
import com.elijah.fitness.util.Fonts
import com.elijah.fitness.util.MetricText
import com.elijah.fitness.util.ResourcesTextResolver
import androidx.core.content.ContextCompat

/**
 * RecyclerView adapter for the Goals screen (Phase 8b redesign).
 *
 * Five view types dispatched polymorphically off the sealed
 * [GoalListItem]:
 *   - [GoalListItem.Header] → title + eyebrow + "+ New goal" pill
 *   - [GoalListItem.MainGoalCard] → dark 26dp hero card
 *   - [GoalListItem.SectionHeader] → uppercase label + optional count pill
 *   - [GoalListItem.Active] → icon tile + title/value row + 8dp progress bar
 *   - [GoalListItem.Completed] → icon tile + "Finished …" + lime check
 *   - [GoalListItem.Ended] → dashed border + "Try again" white pill
 *
 * Every view holder extends [BaseViewHolder], reusing the inheritance
 * hierarchy that the History adapter established (hierarchy #3 in the
 * report).
 */
class GoalsAdapter(
    private val onGoalTap: (GoalWithProgress) -> Unit,
    private val onTryAgainTap: (GoalWithProgress) -> Unit,
    private val onEmptyHeroCta: () -> Unit,
    private val onQuickStartTap: (GoalSuggestion) -> Unit,
) : ListAdapter<GoalListItem, RecyclerView.ViewHolder>(GoalListItemDiff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is GoalListItem.Header -> VIEW_TYPE_HEADER
        is GoalListItem.MainGoalCard -> VIEW_TYPE_MAIN_CARD
        is GoalListItem.SectionHeader -> VIEW_TYPE_SECTION_HEADER
        is GoalListItem.Active -> VIEW_TYPE_ACTIVE
        is GoalListItem.Completed -> VIEW_TYPE_COMPLETED
        is GoalListItem.Ended -> VIEW_TYPE_ENDED
        GoalListItem.EmptyHeroCard -> VIEW_TYPE_EMPTY_HERO
        is GoalListItem.QuickStart -> VIEW_TYPE_QUICK_START
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> GoalsHeaderViewHolder(
                ItemGoalsHeaderBinding.inflate(inflater, parent, false),
            )
            VIEW_TYPE_MAIN_CARD -> MainGoalCardViewHolder(
                ItemGoalMainCardBinding.inflate(inflater, parent, false),
                onGoalTap,
            )
            VIEW_TYPE_SECTION_HEADER -> GoalsSectionHeaderViewHolder(
                ItemGoalsSectionHeaderBinding.inflate(inflater, parent, false),
            )
            VIEW_TYPE_ACTIVE -> ActiveGoalViewHolder(
                ItemGoalActiveBinding.inflate(inflater, parent, false), onGoalTap,
            )
            VIEW_TYPE_COMPLETED -> CompletedGoalViewHolder(
                ItemGoalCompletedBinding.inflate(inflater, parent, false), onGoalTap,
            )
            VIEW_TYPE_ENDED -> EndedGoalViewHolder(
                ItemGoalEndedBinding.inflate(inflater, parent, false), onGoalTap, onTryAgainTap,
            )
            VIEW_TYPE_EMPTY_HERO -> EmptyHeroViewHolder(
                ItemGoalEmptyHeroBinding.inflate(inflater, parent, false), onEmptyHeroCta,
            )
            VIEW_TYPE_QUICK_START -> QuickStartViewHolder(
                ItemGoalQuickStartBinding.inflate(inflater, parent, false), onQuickStartTap,
            )
            else -> error("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GoalListItem.Header -> (holder as GoalsHeaderViewHolder).bind(item)
            is GoalListItem.MainGoalCard -> (holder as MainGoalCardViewHolder).bind(item)
            is GoalListItem.SectionHeader -> (holder as GoalsSectionHeaderViewHolder).bind(item)
            is GoalListItem.Active -> (holder as ActiveGoalViewHolder).bind(item)
            is GoalListItem.Completed -> (holder as CompletedGoalViewHolder).bind(item)
            is GoalListItem.Ended -> (holder as EndedGoalViewHolder).bind(item)
            GoalListItem.EmptyHeroCard ->
                (holder as EmptyHeroViewHolder).bind(GoalListItem.EmptyHeroCard)
            is GoalListItem.QuickStart -> (holder as QuickStartViewHolder).bind(item)
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_MAIN_CARD = 1
        private const val VIEW_TYPE_SECTION_HEADER = 2
        private const val VIEW_TYPE_ACTIVE = 3
        private const val VIEW_TYPE_COMPLETED = 4
        private const val VIEW_TYPE_ENDED = 5
        private const val VIEW_TYPE_EMPTY_HERO = 6
        private const val VIEW_TYPE_QUICK_START = 7
    }
}

/** Diff by stable id for goal rows; headers compare by content. */
private object GoalListItemDiff : DiffUtil.ItemCallback<GoalListItem>() {
    override fun areItemsTheSame(oldItem: GoalListItem, newItem: GoalListItem): Boolean =
        when {
            oldItem is GoalListItem.Header && newItem is GoalListItem.Header -> true
            oldItem is GoalListItem.MainGoalCard && newItem is GoalListItem.MainGoalCard ->
                oldItem.goal.goal.id == newItem.goal.goal.id
            oldItem is GoalListItem.Active && newItem is GoalListItem.Active ->
                oldItem.goal.goal.id == newItem.goal.goal.id
            oldItem is GoalListItem.Completed && newItem is GoalListItem.Completed ->
                oldItem.goal.goal.id == newItem.goal.goal.id
            oldItem is GoalListItem.Ended && newItem is GoalListItem.Ended ->
                oldItem.goal.goal.id == newItem.goal.goal.id
            oldItem is GoalListItem.SectionHeader && newItem is GoalListItem.SectionHeader ->
                oldItem.labelRes == newItem.labelRes
            oldItem is GoalListItem.EmptyHeroCard && newItem is GoalListItem.EmptyHeroCard -> true
            oldItem is GoalListItem.QuickStart && newItem is GoalListItem.QuickStart ->
                oldItem.suggestion.id == newItem.suggestion.id
            else -> false
        }

    override fun areContentsTheSame(oldItem: GoalListItem, newItem: GoalListItem): Boolean =
        oldItem == newItem
}

// ---------------------------------------------------------------------
// View holders
// ---------------------------------------------------------------------

class GoalsHeaderViewHolder(
    binding: ItemGoalsHeaderBinding,
) : BaseViewHolder<GoalListItem.Header>(binding) {
    // Static title only — "New goal" is the screen's FAB, and the section
    // headers below already show the counts.
    override fun bind(item: GoalListItem.Header) = Unit
}

class GoalsSectionHeaderViewHolder(
    private val binding: ItemGoalsSectionHeaderBinding,
) : BaseViewHolder<GoalListItem.SectionHeader>(binding) {
    override fun bind(item: GoalListItem.SectionHeader) {
        binding.goalsSectionTitle.setText(item.labelRes)
        if (item.countPill != null) {
            binding.goalsSectionCountPill.text = item.countPill
            binding.goalsSectionCountPill.visibility = View.VISIBLE
        } else {
            binding.goalsSectionCountPill.visibility = View.GONE
        }
    }
}

class MainGoalCardViewHolder(
    private val binding: ItemGoalMainCardBinding,
    private val onGoalTap: (GoalWithProgress) -> Unit,
) : BaseViewHolder<GoalListItem.MainGoalCard>(binding) {
    override fun bind(item: GoalListItem.MainGoalCard) {
        val ctx = binding.root.context
        val text = ResourcesTextResolver(ctx.resources)
        val gwp = item.goal
        val goal = gwp.goal
        val type = goal.goalType()

        // Activity pill: icon + "Main goal · <Activity>" (or "Any activity").
        binding.goalMainPillActivityIcon.setImageResource(GoalDisplay.iconRes(goal.activityType))
        binding.goalMainPillActivityLabel.text = formatMainPill(ctx, goal.activityType, goal.isMain)

        // Days-left pill (same wording as the active row's small pill).
        binding.goalMainPillDaysLeftLabel.text =
            GoalFormatters.formatTimeRemaining(ctx, gwp.daysLeft)

        val title = goal.displayTitle(text)
        binding.goalMainTitle.text = GoalDisplay.titleForDisplay(title)

        // Big current value + smaller "/ target", one auto-sizing line:
        // "6.5 / 10 km", or for time goals "1 h 10 min / 2 h 30 min"
        // with small units.
        val muted = ContextCompat.getColor(ctx, R.color.goal_main_muted)
        val overTarget = GoalDisplay.overTargetText(text, type, goal.activityType, goal.targetValue)
        binding.goalMainValue.text = if (type == GoalType.DURATION) {
            MetricText.duration(
                totalMinutes = GoalDisplay.currentMinutes(gwp.currentValue),
                hoursUnit = ctx.getString(R.string.duration_unit_hours),
                minutesUnit = ctx.getString(R.string.duration_unit_minutes),
                unitColor = muted,
                unitScale = 0.42f,
                unitTypeface = Fonts.medium(ctx),
                trailing = overTarget,
            )
        } else {
            MetricText.of(
                GoalDisplay.currentNumber(type, gwp.currentValue, goal.targetValue),
                overTarget,
                muted,
                unitScale = 0.42f,
                unitTypeface = Fonts.medium(ctx),
            )
        }

        // Progress ring + percent + "DONE" caption underneath.
        binding.goalMainProgressRing.progress = gwp.progressFraction.toFloat()
        binding.goalMainProgressRingPercent.text = ctx.getString(
            R.string.home_progress_ring_label,
            (gwp.progressFraction * 100).toInt(),
        )

        binding.goalMainCard.setOnClickListener { onGoalTap(gwp) }
        binding.goalMainCard.contentDescription = ctx.getString(
            R.string.goals_main_card_content_description,
            title,
            (gwp.progressFraction * 100).toInt(),
        )
    }

    /**
     * "Main goal · Running" only when the user actually chose this goal
     * as their main one. A goal featured just because it ends soonest
     * reads "Running" — otherwise the card says "Main goal" while the
     * Manage sheet still offers "Make main goal".
     */
    private fun formatMainPill(ctx: Context, activity: ActivityType?, isMain: Boolean): String {
        val activityLabel = ctx.getString(GoalDisplay.activityNameRes(activity))
        return if (isMain) ctx.getString(R.string.goals_main_pill_with_activity, activityLabel)
        else activityLabel
    }
}

class ActiveGoalViewHolder(
    private val binding: ItemGoalActiveBinding,
    private val onGoalTap: (GoalWithProgress) -> Unit,
) : BaseViewHolder<GoalListItem.Active>(binding) {
    override fun bind(item: GoalListItem.Active) {
        val ctx = binding.root.context
        val text = ResourcesTextResolver(ctx.resources)
        val gwp = item.goal
        val goal = gwp.goal
        val type = goal.goalType()

        GoalFormatters.bindIcon(binding.activeIcon, goal.activityType)
        binding.activeTitle.text = GoalDisplay.titleForDisplay(goal.displayTitle(text))
        // "6.5 / 10 km", "3 / 12 swims", "1 h 10 min / 2 h 30 min".
        binding.activeValue.text = GoalDisplay.progressText(
            text, type, goal.activityType, gwp.currentValue, goal.targetValue,
        )
        binding.activeProgressBar.progress = (gwp.progressFraction * 100).toInt()
        binding.activeTimeRemaining.text = GoalFormatters.formatTimeRemaining(ctx, gwp.daysLeft)
        binding.activeCard.setOnClickListener { onGoalTap(gwp) }
    }
}

class CompletedGoalViewHolder(
    private val binding: ItemGoalCompletedBinding,
    private val onGoalTap: (GoalWithProgress) -> Unit,
) : BaseViewHolder<GoalListItem.Completed>(binding) {
    override fun bind(item: GoalListItem.Completed) {
        val ctx = binding.root.context
        val text = ResourcesTextResolver(ctx.resources)
        val gwp = item.goal
        val goal = gwp.goal
        val type = goal.goalType()

        GoalFormatters.bindIcon(binding.completedIcon, goal.activityType)
        binding.completedTitle.text = GoalDisplay.titleForDisplay(goal.displayTitle(text))
        // Phase-8b: we don't persist the exact completion date yet, so the
        // row shows the goal's `lastDay` as the "Finished …" date. The
        // amount is the user's current measured total (>= target), e.g.
        // "Finished Fri 2 Oct · 46 km" or "… · 2 h 30 min".
        binding.completedCaption.text = GoalDisplay.completedCaption(
            text, type, goal.activityType, goal.lastDay, gwp.currentValue,
        )
        binding.completedCard.setOnClickListener { onGoalTap(gwp) }
    }
}

class EmptyHeroViewHolder(
    private val binding: ItemGoalEmptyHeroBinding,
    private val onCta: () -> Unit,
) : BaseViewHolder<GoalListItem.EmptyHeroCard>(binding) {
    override fun bind(item: GoalListItem.EmptyHeroCard) {
        binding.goalEmptyHeroCta.setOnClickListener { onCta() }
    }
}

class QuickStartViewHolder(
    private val binding: ItemGoalQuickStartBinding,
    private val onTap: (GoalSuggestion) -> Unit,
) : BaseViewHolder<GoalListItem.QuickStart>(binding) {
    override fun bind(item: GoalListItem.QuickStart) {
        val ctx = binding.root.context
        val s = item.suggestion
        val text = ResourcesTextResolver(ctx.resources)
        GoalFormatters.bindIcon(binding.goalQuickStartIcon, s.activity.toApi())
        binding.goalQuickStartTitle.text = GoalDisplay.titleForDisplay(s.title(text))
        // "About 1.4 km a day" / "About 21 min a day", as the sheet it opens says.
        binding.goalQuickStartSubtitle.text = s.subtitle(text)
        binding.goalQuickStartCard.setOnClickListener { onTap(s) }
    }
}

class EndedGoalViewHolder(
    private val binding: ItemGoalEndedBinding,
    private val onGoalTap: (GoalWithProgress) -> Unit,
    private val onTryAgainTap: (GoalWithProgress) -> Unit,
) : BaseViewHolder<GoalListItem.Ended>(binding) {
    override fun bind(item: GoalListItem.Ended) {
        val ctx = binding.root.context
        val text = ResourcesTextResolver(ctx.resources)
        val gwp = item.goal
        val goal = gwp.goal
        val type = goal.goalType()

        GoalFormatters.bindIcon(binding.endedIcon, goal.activityType)
        binding.endedTitle.text = GoalDisplay.titleForDisplay(goal.displayTitle(text))
        // "Ended Mon 28 Sep · 16 of 20 km" / "… · 1 h 10 min of 2 h 30 min".
        binding.endedCaption.text = GoalDisplay.endedCaption(
            text, type, goal.activityType, goal.lastDay, gwp.currentValue, goal.targetValue,
        )
        binding.endedCard.setOnClickListener { onGoalTap(gwp) }
        binding.endedTryAgain.setOnClickListener { onTryAgainTap(gwp) }
    }
}

// ---------------------------------------------------------------------
// Formatting helpers (kept alongside the adapter so the renders stay in
// one file).
// ---------------------------------------------------------------------

private object GoalFormatters {

    /**
     * The goal's activity icon — the same stroke glyph as its card in
     * the New goal carousel ([GoalDisplay.iconRes]) — read by TalkBack as
     * the activity ("Swimming", "Any activity").
     */
    fun bindIcon(view: ImageView, activity: ActivityType?) {
        view.setImageResource(GoalDisplay.iconRes(activity))
        view.contentDescription = view.context.getString(GoalDisplay.activityNameRes(activity))
    }

    /**
     * `daysLeft` counts today: a 13-day goal created today shows "13 days
     * left" (matching "… in 13 days"), the final day shows "Last day",
     * and anything past the end shows "Ended".
     */
    fun formatTimeRemaining(ctx: Context, daysLeft: Long): String = when {
        daysLeft <= 0L -> ctx.getString(R.string.goal_ended_pill)
        daysLeft == 1L -> ctx.getString(R.string.goal_last_day)
        else -> ctx.resources.getQuantityString(
            R.plurals.goal_days_left, daysLeft.toInt(), daysLeft.toInt(),
        )
    }
}
