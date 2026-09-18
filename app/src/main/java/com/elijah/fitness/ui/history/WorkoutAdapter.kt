package com.elijah.fitness.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elijah.fitness.R
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.databinding.ItemDistanceWorkoutBinding
import com.elijah.fitness.databinding.ItemHistorySectionHeaderBinding
import com.elijah.fitness.databinding.ItemStrengthWorkoutBinding
import com.elijah.fitness.ui.base.BaseViewHolder
import com.elijah.fitness.util.Formatters

/**
 * RecyclerView adapter for the workout history list.
 *
 * Three view types are dispatched polymorphically from the item's class:
 *  - `HistoryItem.SectionHeader` -> a header pill row with per-week summary
 *  - `HistoryItem.Row(DistanceWorkout)` -> distance row
 *  - `HistoryItem.Row(StrengthWorkout)` -> strength row
 */
class WorkoutAdapter :
    ListAdapter<HistoryItem, RecyclerView.ViewHolder>(HistoryItemDiff) {

    override fun getItemViewType(position: Int): Int = when (val item = getItem(position)) {
        is HistoryItem.SectionHeader -> VIEW_TYPE_HEADER
        is HistoryItem.Row -> when (item.workout) {
            is DistanceWorkout -> VIEW_TYPE_DISTANCE
            is StrengthWorkout -> VIEW_TYPE_STRENGTH
            else -> error("Unknown workout type at position $position")
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> SectionHeaderViewHolder(
                ItemHistorySectionHeaderBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_DISTANCE -> DistanceWorkoutViewHolder(
                ItemDistanceWorkoutBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_STRENGTH -> StrengthWorkoutViewHolder(
                ItemStrengthWorkoutBinding.inflate(inflater, parent, false)
            )
            else -> error("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HistoryItem.SectionHeader ->
                (holder as SectionHeaderViewHolder).bind(item)
            is HistoryItem.Row -> when (holder) {
                is DistanceWorkoutViewHolder -> holder.bind(item.workout as DistanceWorkout)
                is StrengthWorkoutViewHolder -> holder.bind(item.workout as StrengthWorkout)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_DISTANCE = 1
        private const val VIEW_TYPE_STRENGTH = 2
    }
}

/** Content equality across all HistoryItem variants. Rows compare by id. */
private object HistoryItemDiff : DiffUtil.ItemCallback<HistoryItem>() {
    override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean =
        when {
            oldItem is HistoryItem.Row && newItem is HistoryItem.Row ->
                oldItem.workout.id == newItem.workout.id
            oldItem is HistoryItem.SectionHeader && newItem is HistoryItem.SectionHeader ->
                oldItem.labelRes == newItem.labelRes
            else -> false
        }

    override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean =
        oldItem == newItem
}

class SectionHeaderViewHolder(
    private val binding: ItemHistorySectionHeaderBinding,
) : BaseViewHolder<HistoryItem.SectionHeader>(binding) {

    override fun bind(item: HistoryItem.SectionHeader) {
        val ctx = binding.root.context
        binding.sectionTitle.setText(item.labelRes)
        binding.sectionSummary.text = if (item.distanceMeters > 0.0) {
            ctx.getString(
                R.string.history_section_summary_with_distance,
                item.count,
                Formatters.formatDistanceKm(item.distanceMeters),
            )
        } else {
            ctx.getString(R.string.history_section_summary_count_only, item.count)
        }
    }
}

class DistanceWorkoutViewHolder(
    private val binding: ItemDistanceWorkoutBinding,
) : BaseViewHolder<DistanceWorkout>(binding) {

    override fun bind(item: DistanceWorkout) {
        val ctx = binding.root.context
        val (iconRes, typeLabel) = when (item) {
            is RunningWorkout -> R.drawable.ic_run_24 to
                ctx.getString(R.string.activity_type_running)
            is CyclingWorkout -> R.drawable.ic_bike_24 to
                ctx.getString(R.string.activity_type_cycling)
            else -> error("Unhandled DistanceWorkout subtype: ${item::class.simpleName}")
        }

        binding.activityIcon.setImageResource(iconRes)
        binding.activityIcon.imageTintList =
            ContextCompat.getColorStateList(ctx, R.color.text_primary)

        binding.typeAndDate.text = ctx.getString(
            R.string.history_row_type_and_date,
            typeLabel,
            Formatters.formatDate(item.startTime),
        )
        binding.distanceAndDuration.text = ctx.getString(
            R.string.history_row_distance_and_duration,
            Formatters.formatDistanceKm(item.distanceMeters),
            Formatters.formatDuration(item.duration),
        )
        binding.paceOrSpeed.text = when (item) {
            is RunningWorkout -> Formatters.formatPace(item.paceSecondsPerKm())
            is CyclingWorkout -> Formatters.formatSpeed(item.averageSpeedKmh())
            else -> ""
        }
    }
}

class StrengthWorkoutViewHolder(
    private val binding: ItemStrengthWorkoutBinding,
) : BaseViewHolder<StrengthWorkout>(binding) {

    override fun bind(item: StrengthWorkout) {
        val ctx = binding.root.context
        val typeLabel = ctx.getString(R.string.activity_type_weightlifting)

        binding.strengthTitle.text = ctx.getString(
            R.string.history_row_type_and_date,
            typeLabel,
            Formatters.formatDate(item.startTime),
        )

        val distinctExercises = item.sets.map { it.exercise }.distinct().size
        binding.strengthSummary.text = ctx.resources.getQuantityString(
            R.plurals.history_row_strength_summary,
            item.sets.size,
            distinctExercises,
            item.sets.size,
        )
        binding.totalVolume.text = ctx.getString(
            R.string.history_row_total_volume,
            item.totalVolumeKg(),
        )
    }
}
