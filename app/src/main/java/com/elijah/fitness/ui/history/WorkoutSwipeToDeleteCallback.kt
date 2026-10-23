package com.elijah.fitness.ui.history

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.elijah.fitness.R

/**
 * Left-swipe-to-delete gesture for the workout history list.
 *
 * - Only rows (`HistoryItem.Row`) are swipeable; section headers return
 *   `0` from [getMovementFlags], so the user physically can't drag them.
 * - As the row slides, a destructive-red background is drawn under it
 *   with a trash icon pinned to the trailing edge.
 * - On swipe end, the fragment's `onSwipedLeft(position)` runs — it
 *   routes into `HistoryViewModel.deleteWithUndo` so the Step-7
 *   coordinator handles the 5-second Undo window.
 */
class WorkoutSwipeToDeleteCallback(
    private val adapter: WorkoutAdapter,
    private val onSwipedLeft: (position: Int) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private var background: GradientDrawable? = null
    private var trashIcon: Drawable? = null

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ): Int {
        // Section-header rows (view type 0) and anything else that isn't
        // a workout row shouldn't accept the gesture.
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return 0
        val item = adapter.currentList.getOrNull(position) ?: return 0
        return if (item is HistoryItem.Row) {
            makeMovementFlags(0, ItemTouchHelper.LEFT)
        } else {
            0
        }
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false // no drag-and-drop

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) onSwipedLeft(position)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        val itemView = viewHolder.itemView

        if (background == null) {
            val color = ContextCompat.getColor(itemView.context, R.color.destructive)
            // Rounded to the row card's 20dp corners so the red never
            // pokes out square behind the card.
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(color)
                cornerRadius = itemView.resources.getDimension(R.dimen.radius_row)
            }
            trashIcon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_delete_24)?.apply {
                setTint(Color.WHITE)
            }
        }

        if (dX < 0f) {
            // Draw the full row-sized rounded shape but clip it to the
            // revealed strip (plus one corner radius, so the red also fills
            // behind the sliding card's rounded trailing corners).
            val radius = itemView.resources.getDimension(R.dimen.radius_row)
            background?.let { bg ->
                bg.setBounds(itemView.left, itemView.top, itemView.right, itemView.bottom)
                val save = c.save()
                c.clipRect(
                    (itemView.right + dX - radius).coerceAtLeast(itemView.left.toFloat()),
                    itemView.top.toFloat(),
                    itemView.right.toFloat(),
                    itemView.bottom.toFloat(),
                )
                bg.draw(c)
                c.restoreToCount(save)
            }

            val icon = trashIcon
            if (icon != null) {
                val iconSize = dpToPx(itemView.context, 22f)
                val top = itemView.top + (itemView.height - iconSize) / 2
                val margin = dpToPx(itemView.context, 24f)
                val right = itemView.right - margin
                val left = right - iconSize
                icon.bounds = Rect(left, top, right, top + iconSize)
                icon.draw(c)
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun dpToPx(ctx: android.content.Context, dp: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, ctx.resources.displayMetrics,
        ).toInt()
}
