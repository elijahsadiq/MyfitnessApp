package com.elijah.fitness.ui.log

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.elijah.fitness.R
import com.elijah.fitness.data.model.StrengthSet
import com.elijah.fitness.databinding.ItemStrengthSetBinding
import java.text.DecimalFormat

/**
 * Simple adapter for the sets-being-added list. Kept intentionally basic
 * (no `DiffUtil`) because sets have no stable id, duplicate sets are
 * legitimate, and the list is short (a session is usually well under 20
 * sets).
 */
class StrengthSetAdapter(
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<StrengthSetAdapter.SetViewHolder>() {

    private val items: MutableList<StrengthSet> = mutableListOf()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newItems: List<StrengthSet>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SetViewHolder {
        val binding = ItemStrengthSetBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SetViewHolder(binding, onDelete)
    }

    override fun onBindViewHolder(holder: SetViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    class SetViewHolder(
        private val binding: ItemStrengthSetBinding,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(set: StrengthSet, position: Int) {
            val ctx = binding.root.context
            binding.setNumber.text = (position + 1).toString()
            binding.setExercise.text = set.exercise
            binding.setSummary.text = ctx.resources.getQuantityString(
                R.plurals.log_strength_set_summary,
                set.reps,
                set.reps,
                WEIGHT_FORMAT.format(set.weightKg),
            )
            binding.deleteButton.contentDescription = ctx.getString(
                R.string.log_strength_delete_set_content_description_numbered, position + 1,
            )
            binding.deleteButton.setOnClickListener {
                val current = bindingAdapterPosition
                if (current != RecyclerView.NO_POSITION) onDelete(current)
            }
        }

        private companion object {
            /** "60", "62.5" — no trailing ".0" on whole kilograms. */
            val WEIGHT_FORMAT = DecimalFormat("0.#")
        }
    }
}
