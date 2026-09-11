package com.elijah.fitness.ui.base

import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/**
 * Shared base for every RecyclerView row that uses ViewBinding.
 *
 * ### Inheritance example #3 (Task 5b)
 * The `WorkoutAdapter` shows two visually distinct row types — a distance
 * row and a strength row — but both are workouts. The polymorphism lives
 * in this base class: the adapter holds a `BaseViewHolder<*>`, calls
 * `bind`, and each subclass narrows to the workout subtype it renders.
 *
 * The generic parameter [T] is the domain model type this row renders.
 * Constraining the [ViewBinding] argument means subclasses get a typed
 * `itemView` (through `binding.root`) without any casts.
 */
abstract class BaseViewHolder<T>(binding: ViewBinding) :
    RecyclerView.ViewHolder(binding.root) {

    abstract fun bind(item: T)
}
