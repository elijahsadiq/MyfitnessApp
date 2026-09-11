package com.elijah.fitness.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.model.CyclingWorkout
import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.model.RunningWorkout
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.databinding.ItemDistanceWorkoutBinding
import com.elijah.fitness.databinding.ItemStrengthWorkoutBinding
import com.elijah.fitness.ui.base.BaseViewHolder
import com.elijah.fitness.util.Formatters

/**
 * RecyclerView adapter for the workout history list.
 *
 * Two view types are dispatched polymorphically from the item's class:
 * a distance row for `DistanceWorkout` subclasses (running, cycling) and
 * a strength row for `StrengthWorkout`. `DiffUtil` uses `id` for identity
 * and the models' content-equality (see Step 3) for change detection.
 */
class WorkoutAdapter :
    ListAdapter<Workout, RecyclerView.ViewHolder>(WorkoutDiff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DistanceWorkout -> VIEW_TYPE_DISTANCE
        is StrengthWorkout -> VIEW_TYPE_STRENGTH
        else -> error("Unknown workout type at position $position")
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
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
        when (holder) {
            is DistanceWorkoutViewHolder -> holder.bind(getItem(position) as DistanceWorkout)
            is StrengthWorkoutViewHolder -> holder.bind(getItem(position) as StrengthWorkout)
        }
    }

    companion object {
        private const val VIEW_TYPE_DISTANCE = 1
        private const val VIEW_TYPE_STRENGTH = 2
    }
}

/** Identity by workout id; content equality via `==` (see Step 3). */
private object WorkoutDiff : DiffUtil.ItemCallback<Workout>() {
    override fun areItemsTheSame(oldItem: Workout, newItem: Workout): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Workout, newItem: Workout): Boolean =
        oldItem == newItem
}

class DistanceWorkoutViewHolder(
    private val binding: ItemDistanceWorkoutBinding
) : BaseViewHolder<DistanceWorkout>(binding) {

    override fun bind(item: DistanceWorkout) {
        val ctx = binding.root.context
        val (iconRes, accentRes, typeLabel) = when (item) {
            is RunningWorkout -> Triple(
                R.drawable.ic_run_24,
                R.color.accent_running,
                ctx.getString(R.string.activity_type_running)
            )
            is CyclingWorkout -> Triple(
                R.drawable.ic_bike_24,
                R.color.accent_cycling,
                ctx.getString(R.string.activity_type_cycling)
            )
            else -> error("Unhandled DistanceWorkout subtype: ${item::class.simpleName}")
        }

        binding.activityIcon.setImageResource(iconRes)
        binding.activityIcon.imageTintList =
            ContextCompat.getColorStateList(ctx, accentRes)

        binding.typeAndDate.text = ctx.getString(
            R.string.history_row_type_and_date,
            typeLabel,
            Formatters.formatDate(item.startTime)
        )
        binding.distanceAndDuration.text = ctx.getString(
            R.string.history_row_distance_and_duration,
            Formatters.formatDistanceKm(item.distanceMeters),
            Formatters.formatDuration(item.duration)
        )
        binding.paceOrSpeed.text = when (item) {
            is RunningWorkout -> Formatters.formatPace(item.paceSecondsPerKm())
            is CyclingWorkout -> Formatters.formatSpeed(item.averageSpeedKmh())
            else -> ""
        }
    }
}

class StrengthWorkoutViewHolder(
    private val binding: ItemStrengthWorkoutBinding
) : BaseViewHolder<StrengthWorkout>(binding) {

    override fun bind(item: StrengthWorkout) {
        val ctx = binding.root.context
        val typeLabel = ctx.getString(R.string.activity_type_weightlifting)

        binding.strengthTitle.text = ctx.getString(
            R.string.history_row_type_and_date,
            typeLabel,
            Formatters.formatDate(item.startTime)
        )

        val distinctExercises = item.sets.map { it.exercise }.distinct().size
        binding.strengthSummary.text = ctx.resources.getQuantityString(
            R.plurals.history_row_strength_summary,
            item.sets.size,
            distinctExercises,
            item.sets.size
        )
        binding.totalVolume.text = ctx.getString(
            R.string.history_row_total_volume,
            item.totalVolumeKg()
        )
    }
}

/** Helper for the future icon-tint work in Step 8. */
@Suppress("unused")
internal fun ActivityType.accentColorRes(): Int = when (this) {
    ActivityType.RUNNING -> R.color.accent_running
    ActivityType.CYCLING -> R.color.accent_cycling
    ActivityType.WEIGHTLIFTING -> R.color.accent_weightlifting
}
