package com.elijah.fitness.ui.history

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.DistanceUnit
import com.elijah.fitness.data.model.DistanceWorkout
import com.elijah.fitness.data.model.StrengthWorkout
import com.elijah.fitness.data.model.Workout
import com.elijah.fitness.data.repository.UserSettings
import com.elijah.fitness.databinding.ItemDistanceWorkoutBinding
import com.elijah.fitness.databinding.ItemHistorySectionHeaderBinding
import com.elijah.fitness.databinding.ItemStrengthWorkoutBinding
import com.elijah.fitness.ui.base.BaseViewHolder
import com.elijah.fitness.util.ActivityLabels
import com.elijah.fitness.util.DurationFormatter
import com.elijah.fitness.util.Formatters
import com.elijah.fitness.util.MeasureText
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToLong

/**
 * RecyclerView adapter for the workout history list.
 *
 * Three view types are dispatched polymorphically from the item's class:
 *  - `HistoryItem.SectionHeader` -> a header pill row with per-week summary
 *  - `HistoryItem.Row(DistanceWorkout)` -> distance row
 *  - `HistoryItem.Row(StrengthWorkout)` -> strength row
 *
 * Phase 8d step 8: rows are the activities-v3 cards (History.dc.html) —
 * icon tile, "Morning swim", "Today · 32 min · 270 kcal" and the distance
 * (metres for swims) or weight lifted on the right. Home's day list uses
 * the same adapter, so both screens show identical rows.
 */
class WorkoutAdapter(
    private val onRowClick: (com.elijah.fitness.data.model.Workout) -> Unit = {},
) : ListAdapter<HistoryItem, RecyclerView.ViewHolder>(HistoryItemDiff) {

    override fun getItemViewType(position: Int): Int = when (val item = getItem(position)) {
        is HistoryItem.SectionHeader -> VIEW_TYPE_HEADER
        // Every workout is one of the two shapes; anything else (never
        // expected) is drawn as a distance row rather than crashing.
        is HistoryItem.Row -> when (item.workout) {
            is StrengthWorkout -> VIEW_TYPE_STRENGTH
            else -> VIEW_TYPE_DISTANCE
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
            is HistoryItem.Row -> {
                val workout = item.workout
                when {
                    holder is DistanceWorkoutViewHolder && workout is DistanceWorkout -> {
                        holder.bind(workout)
                        holder.showUnsynced(item.unsynced)
                    }
                    holder is StrengthWorkoutViewHolder && workout is StrengthWorkout -> {
                        holder.bind(workout)
                        holder.showUnsynced(item.unsynced)
                    }
                }
                holder.itemView.setOnClickListener { onRowClick(item.workout) }
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
        val count = ctx.resources.getQuantityString(
            R.plurals.history_section_workout_count, item.count, item.count,
        )
        binding.sectionSummary.text = if (item.distanceMeters > 0.0) {
            // Swim-only sections read in metres, like their rows.
            val distance = MeasureText.distance(ctx, item.distanceMeters, item.distanceUnit)
            ctx.getString(R.string.history_section_summary_with_distance, count, distance)
        } else {
            count
        }
    }
}

class DistanceWorkoutViewHolder(
    private val binding: ItemDistanceWorkoutBinding,
) : BaseViewHolder<DistanceWorkout>(binding) {

    override fun bind(item: DistanceWorkout) {
        val ctx = binding.root.context
        binding.activityIcon.setImageResource(ActivityLabels.iconRes(item.type))
        binding.activityIcon.imageTintList =
            ContextCompat.getColorStateList(ctx, R.color.text_on_lime)
        binding.workoutTitle.setText(WorkoutTitles.timeOfDayTitleRes(item))
        binding.workoutSubtitle.text = RowText.subtitle(ctx, item)
        // Each class picks its unit: metres for swims ("1,500 m"), km otherwise ("5.2 km").
        binding.workoutValue.text = when (item.distanceUnit) {
            DistanceUnit.METRES -> MeasureText.metres(ctx, item.distanceMeters)
            DistanceUnit.KILOMETRES -> ctx.getString(
                R.string.history_row_km,
                String.format(Locale.getDefault(), "%.1f", item.distanceMeters / 1000.0),
            )
        }
    }

    fun showUnsynced(unsynced: Boolean) = binding.workoutSubtitle.markUnsynced(unsynced)
}

class StrengthWorkoutViewHolder(
    private val binding: ItemStrengthWorkoutBinding,
) : BaseViewHolder<StrengthWorkout>(binding) {

    override fun bind(item: StrengthWorkout) {
        val ctx = binding.root.context
        binding.strengthTitle.setText(WorkoutTitles.timeOfDayTitleRes(item))
        binding.strengthSummary.text = RowText.subtitle(ctx, item)
        binding.totalVolume.text = ctx.getString(
            R.string.history_row_kg,
            NumberFormat.getIntegerInstance(Locale.getDefault()).format(item.totalVolumeKg().roundToLong()),
        )
    }

    fun showUnsynced(unsynced: Boolean) = binding.strengthSummary.markUnsynced(unsynced)
}

/** The second line of a row: "Today · 32 min · 270 kcal". */
private object RowText {

    fun subtitle(ctx: Context, workout: Workout): String {
        val zone = ZoneId.systemDefault()
        val day = workout.startTime.atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val date = when (day) {
            today -> ctx.getString(R.string.history_row_today)
            today.minusDays(1) -> ctx.getString(R.string.history_row_yesterday)
            else -> Formatters.formatDate(workout.startTime, zone)
        }
        // The stored kcal (saved with the workout) wins; older rows fall
        // back to the current profile weight.
        val weightKg = (ctx.applicationContext as? FitnessApp)
            ?.container?.userSettings?.bodyWeightKg?.value
            ?: UserSettings.DEFAULT_BODY_WEIGHT_KG
        val kcal = workout.energyKcal(weightKg).roundToLong().coerceAtLeast(0L)
        return ctx.getString(
            R.string.history_row_subtitle,
            date,
            DurationFormatter.format(ctx, workout.duration),
            ctx.getString(R.string.history_row_kcal, NumberFormat.getIntegerInstance(Locale.getDefault()).format(kcal)),
        )
    }
}

/**
 * Phase 10: a small cloud-off icon in front of the row's second line for
 * a workout that hasn't reached the server. The icon isn't read out, so
 * the line's content description says it in words.
 */
private fun TextView.markUnsynced(unsynced: Boolean) {
    val icon = if (unsynced) ContextCompat.getDrawable(context, R.drawable.ic_cloud_off_16) else null
    setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
    compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.space_xs)
    TextViewCompat.setCompoundDrawableTintList(
        this, ContextCompat.getColorStateList(context, R.color.text_secondary),
    )
    contentDescription = if (unsynced) {
        context.getString(R.string.history_row_unsynced_description, text, context.getString(R.string.sync_not_synced))
    } else {
        null
    }
}
