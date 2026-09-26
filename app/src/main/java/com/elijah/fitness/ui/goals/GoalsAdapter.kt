package com.elijah.fitness.ui.goals

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.GoalPeriod
import com.elijah.fitness.data.model.GoalWithProgress
import com.elijah.fitness.data.remote.mapper.goalType
import com.elijah.fitness.databinding.ItemGoalActiveBinding
import com.elijah.fitness.databinding.ItemGoalCompletedBinding
import com.elijah.fitness.databinding.ItemGoalFeaturedBinding
import com.elijah.fitness.databinding.ItemGoalsSectionHeaderBinding
import com.elijah.fitness.ui.base.BaseViewHolder
import com.elijah.fitness.data.model.GoalType
import java.util.Locale

/**
 * RecyclerView adapter for the Goals screen. Four view types are
 * dispatched polymorphically off the sealed [GoalListItem]:
 *   - [GoalListItem.Featured] → dark hero card with a progress ring
 *   - [GoalListItem.SectionHeader] → section title + optional count pill
 *   - [GoalListItem.Active] → row with icon, title, thin progress bar
 *   - [GoalListItem.Completed] → row with lime check badge
 *
 * Every view holder extends the existing [BaseViewHolder] — reusing the
 * hierarchy that the History adapter established (Task 5b, inheritance
 * example #3).
 */
class GoalsAdapter(
    private val onGoalTap: (GoalWithProgress) -> Unit,
) : ListAdapter<GoalListItem, RecyclerView.ViewHolder>(GoalListItemDiff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is GoalListItem.Featured -> VIEW_TYPE_FEATURED
        is GoalListItem.SectionHeader -> VIEW_TYPE_HEADER
        is GoalListItem.Active -> VIEW_TYPE_ACTIVE
        is GoalListItem.Completed -> VIEW_TYPE_COMPLETED
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_FEATURED -> FeaturedGoalViewHolder(
                ItemGoalFeaturedBinding.inflate(inflater, parent, false), onGoalTap,
            )
            VIEW_TYPE_HEADER -> GoalsSectionHeaderViewHolder(
                ItemGoalsSectionHeaderBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_ACTIVE -> ActiveGoalViewHolder(
                ItemGoalActiveBinding.inflate(inflater, parent, false), onGoalTap,
            )
            VIEW_TYPE_COMPLETED -> CompletedGoalViewHolder(
                ItemGoalCompletedBinding.inflate(inflater, parent, false), onGoalTap,
            )
            else -> error("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GoalListItem.Featured -> (holder as FeaturedGoalViewHolder).bind(item)
            is GoalListItem.SectionHeader -> (holder as GoalsSectionHeaderViewHolder).bind(item)
            is GoalListItem.Active -> (holder as ActiveGoalViewHolder).bind(item)
            is GoalListItem.Completed -> (holder as CompletedGoalViewHolder).bind(item)
        }
    }

    companion object {
        private const val VIEW_TYPE_FEATURED = 0
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_ACTIVE = 2
        private const val VIEW_TYPE_COMPLETED = 3
    }
}

/** Diff by id for goal rows; by label for headers. */
private object GoalListItemDiff : DiffUtil.ItemCallback<GoalListItem>() {
    override fun areItemsTheSame(oldItem: GoalListItem, newItem: GoalListItem): Boolean =
        when {
            oldItem is GoalListItem.Featured && newItem is GoalListItem.Featured ->
                oldItem.goal.goal.id == newItem.goal.goal.id
            oldItem is GoalListItem.Active && newItem is GoalListItem.Active ->
                oldItem.goal.goal.id == newItem.goal.goal.id
            oldItem is GoalListItem.Completed && newItem is GoalListItem.Completed ->
                oldItem.goal.goal.id == newItem.goal.goal.id
            oldItem is GoalListItem.SectionHeader && newItem is GoalListItem.SectionHeader ->
                oldItem.labelRes == newItem.labelRes
            else -> false
        }

    override fun areContentsTheSame(oldItem: GoalListItem, newItem: GoalListItem): Boolean =
        oldItem == newItem
}

// ---------------------------------------------------------------------
// View holders
// ---------------------------------------------------------------------

class GoalsSectionHeaderViewHolder(
    private val binding: ItemGoalsSectionHeaderBinding,
) : BaseViewHolder<GoalListItem.SectionHeader>(binding) {
    override fun bind(item: GoalListItem.SectionHeader) {
        binding.goalsSectionTitle.setText(item.labelRes)
        if (item.countPill != null) {
            binding.goalsSectionCountPill.text = item.countPill
            binding.goalsSectionCountPill.visibility = android.view.View.VISIBLE
        } else {
            binding.goalsSectionCountPill.visibility = android.view.View.GONE
        }
    }
}

class FeaturedGoalViewHolder(
    private val binding: ItemGoalFeaturedBinding,
    private val onGoalTap: (GoalWithProgress) -> Unit,
) : BaseViewHolder<GoalListItem.Featured>(binding) {
    override fun bind(item: GoalListItem.Featured) {
        val ctx = binding.root.context
        val gwp = item.goal
        val goal = gwp.goal

        binding.featuredEyebrow.setText(
            if (goal.period == GoalPeriod.THIS_WEEK) R.string.goals_eyebrow_week
            else R.string.goals_eyebrow_month
        )
        binding.featuredTitle.text = goal.title
        binding.featuredValue.text = GoalFormatters.formatValue(gwp.currentValue, goal.goalType())
        binding.featuredValueSuffix.text = ctx.getString(
            R.string.goal_value_over_target_decimal,
            "",
            GoalFormatters.formatValue(goal.targetValue, goal.goalType()),
            GoalFormatters.unitLabel(ctx, goal.goalType()),
        ).trimStart(' ', '/').let { "/ $it" }
        binding.featuredTimeRemaining.text = GoalFormatters.formatTimeRemaining(ctx, gwp.daysRemaining)
        binding.featuredProgressRing.progress = gwp.progressFraction.toFloat()
        binding.featuredProgressRingLabel.text = ctx.getString(
            R.string.home_progress_ring_label,
            (gwp.progressFraction * 100).toInt(),
        )

        binding.featuredCard.setOnClickListener { onGoalTap(gwp) }
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

        binding.activeIcon.setImageResource(GoalFormatters.iconRes(goal.activityType, goal.goalType()))
        binding.activeTitle.text = goal.title
        binding.activeValue.text = ctx.getString(
            R.string.goal_value_over_target_decimal,
            GoalFormatters.formatValue(gwp.currentValue, goal.goalType()),
            GoalFormatters.formatValue(goal.targetValue, goal.goalType()),
            GoalFormatters.unitLabel(ctx, goal.goalType()),
        )
        binding.activeProgressBar.progress = (gwp.progressFraction * 100).toInt()
        binding.activeTimeRemaining.text = GoalFormatters.formatTimeRemaining(ctx, gwp.daysRemaining)

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

        binding.completedTitle.text = goal.title
        binding.completedCaption.text = ctx.getString(
            R.string.goal_value_over_target_decimal,
            GoalFormatters.formatValue(goal.targetValue, goal.goalType()),
            GoalFormatters.formatValue(goal.targetValue, goal.goalType()),
            GoalFormatters.unitLabel(ctx, goal.goalType()),
        )
        binding.completedCard.setOnClickListener { onGoalTap(gwp) }
    }
}

// ---------------------------------------------------------------------
// Formatting helpers (kept alongside the adapter so the renders stay in
// one file — Java-side of the design system already lives in Formatters).
// ---------------------------------------------------------------------

private object GoalFormatters {

    fun formatValue(value: Double, type: GoalType): String = when (type) {
        // Counts and minutes are integers; km + kg one decimal.
        GoalType.COUNT, GoalType.DURATION ->
            value.toLong().toString()
        GoalType.DISTANCE, GoalType.VOLUME ->
            String.format(Locale.getDefault(), "%.1f", value)
    }

    fun unitLabel(ctx: android.content.Context, type: GoalType): String = when (type) {
        GoalType.DISTANCE -> ctx.getString(R.string.goal_unit_km)
        GoalType.COUNT -> ctx.getString(R.string.goal_unit_workouts)
        GoalType.DURATION -> ctx.getString(R.string.goal_unit_min)
        GoalType.VOLUME -> ctx.getString(R.string.goal_unit_kg)
    }

    fun iconRes(activityType: ActivityType?, goalType: GoalType): Int {
        // Prefer activity icon; fall back to goal-type semantics for "any".
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

    fun formatTimeRemaining(ctx: android.content.Context, daysRemaining: Long): String = when {
        daysRemaining < 0 -> ctx.getString(R.string.goal_expired, "${-daysRemaining}d ago")
        daysRemaining == 0L -> ctx.getString(R.string.goal_last_day)
        daysRemaining == 1L -> ctx.getString(R.string.goal_one_day_remaining)
        else -> ctx.getString(R.string.goal_days_remaining, daysRemaining.toInt())
    }
}
