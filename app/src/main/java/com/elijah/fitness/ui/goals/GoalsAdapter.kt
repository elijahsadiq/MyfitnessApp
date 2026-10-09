package com.elijah.fitness.ui.goals

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        val gwp = item.goal
        val goal = gwp.goal
        val type = goal.goalType()

        // Activity pill: icon + "Main goal · <Activity>" (or "Any activity").
        binding.goalMainPillActivityIcon.setImageResource(
            GoalFormatters.iconRes(goal.activityType, type)
        )
        binding.goalMainPillActivityLabel.text = formatMainPill(ctx, goal.activityType, goal.isMain)

        // Days-left pill (same wording as the active row's small pill).
        binding.goalMainPillDaysLeftLabel.text =
            GoalFormatters.formatTimeRemaining(ctx, gwp.daysLeft)

        binding.goalMainTitle.text = goal.displayTitle()

        // Big current value + smaller "/ target unit", one auto-sizing line.
        binding.goalMainValue.text = MetricText.of(
            GoalFormatters.formatValue(gwp.currentValue, type),
            "/ " + GoalFormatters.formatValue(goal.targetValue, type) + " " +
                GoalFormatters.unitLabel(ctx, type),
            ContextCompat.getColor(ctx, R.color.goal_main_muted),
            unitScale = 0.42f,
            unitTypeface = Fonts.medium(ctx),
        )

        // Progress ring + percent + "DONE" caption underneath.
        binding.goalMainProgressRing.progress = gwp.progressFraction.toFloat()
        binding.goalMainProgressRingPercent.text = ctx.getString(
            R.string.home_progress_ring_label,
            (gwp.progressFraction * 100).toInt(),
        )

        binding.goalMainCard.setOnClickListener { onGoalTap(gwp) }
        binding.goalMainCard.contentDescription = ctx.getString(
            R.string.goals_main_card_content_description,
            goal.displayTitle(),
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
        val activityLabel = activity?.let { GoalFormatters.activityLabel(ctx, it) }
            ?: ctx.getString(R.string.new_goal_activity_any)
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
        val gwp = item.goal
        val goal = gwp.goal
        val type = goal.goalType()

        binding.activeIcon.setImageResource(GoalFormatters.iconRes(goal.activityType, type))
        binding.activeTitle.text = goal.displayTitle()
        binding.activeValue.text = ctx.getString(
            R.string.goal_value_over_target_decimal,
            GoalFormatters.formatValue(gwp.currentValue, type),
            GoalFormatters.formatValue(goal.targetValue, type),
            GoalFormatters.unitLabel(ctx, type),
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
        val gwp = item.goal
        val goal = gwp.goal
        val type = goal.goalType()

        binding.completedIcon.setImageResource(GoalFormatters.iconRes(goal.activityType, type))
        binding.completedTitle.text = goal.displayTitle()
        // Phase-8b: we don't persist the exact completion date yet, so the
        // row shows the goal's `lastDay` as the "Finished …" date. The
        // actual value is the user's current measured total (which, for
        // completed goals, is >= target).
        binding.completedCaption.text = ctx.getString(
            R.string.goal_completed_caption,
            GoalFormatters.formatDate(goal.lastDay),
            GoalFormatters.formatValue(gwp.currentValue, type),
            GoalFormatters.unitLabel(ctx, type),
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
        binding.goalQuickStartIcon.setImageResource(
            GoalFormatters.iconRes(s.activity.toApi(), s.metric)
        )
        binding.goalQuickStartTitle.setText(s.titleRes)
        binding.goalQuickStartSubtitle.text = ctx.getString(s.subtitleRes, s.subtitleArg)
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
        val gwp = item.goal
        val goal = gwp.goal
        val type = goal.goalType()

        binding.endedIcon.setImageResource(GoalFormatters.iconRes(goal.activityType, type))
        binding.endedTitle.text = goal.displayTitle()
        binding.endedCaption.text = ctx.getString(
            R.string.goal_ended_date_caption,
            GoalFormatters.formatDate(goal.lastDay),
            GoalFormatters.formatValue(gwp.currentValue, type),
            GoalFormatters.formatValue(goal.targetValue, type),
            GoalFormatters.unitLabel(ctx, type),
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

    private val DATE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    /** Whole units ("10,000 kg", "45 min") except distance ("12.5 km"). */
    fun formatValue(value: Double, type: GoalType): String = when (type) {
        GoalType.COUNT, GoalType.DURATION, GoalType.VOLUME ->
            NumberFormat.getIntegerInstance(Locale.getDefault()).format(value.toLong())
        GoalType.DISTANCE ->
            String.format(Locale.getDefault(), "%.1f", value)
    }

    fun unitLabel(ctx: Context, type: GoalType): String = when (type) {
        GoalType.DISTANCE -> ctx.getString(R.string.goal_unit_km)
        GoalType.COUNT -> ctx.getString(R.string.goal_unit_workouts)
        GoalType.DURATION -> ctx.getString(R.string.goal_unit_min)
        GoalType.VOLUME -> ctx.getString(R.string.goal_unit_kg)
    }

    fun activityLabel(ctx: Context, activity: ActivityType): String = when (activity) {
        ActivityType.RUNNING -> ctx.getString(R.string.activity_type_running)
        ActivityType.CYCLING -> ctx.getString(R.string.activity_type_cycling)
        ActivityType.WEIGHTLIFTING -> ctx.getString(R.string.activity_type_weightlifting)
    }

    fun iconRes(activityType: ActivityType?, goalType: GoalType): Int {
        return when (activityType) {
            ActivityType.RUNNING -> R.drawable.ic_run_24
            ActivityType.CYCLING -> R.drawable.ic_bike_24
            ActivityType.WEIGHTLIFTING -> R.drawable.ic_dumbbell_24
            null -> when (goalType) {
                GoalType.DISTANCE -> R.drawable.ic_run_24
                GoalType.VOLUME -> R.drawable.ic_dumbbell_24
                GoalType.DURATION -> R.drawable.ic_history_24
                GoalType.COUNT -> R.drawable.ic_flag_24
            }
        }
    }

    /** "Fri 2 Oct" — short-weekday + day + short-month, locale-aware. */
    fun formatDate(date: java.time.LocalDate): String = date.format(DATE_FORMAT)

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

