package com.elijah.fitness.ui.components

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.elijah.fitness.R
import com.elijah.fitness.databinding.ItemActivityCardBinding
import com.elijah.fitness.ui.base.BaseViewHolder
import com.elijah.fitness.util.Fonts
import kotlin.math.roundToInt

/**
 * Pages of an [ActivityCarouselView]: one [ActivityCardViewHolder] per
 * activity card.
 *
 * The selected card is tracked here. [setSelectedPosition] re-renders
 * only the two cards whose state changed, with [PAYLOAD_SELECTION], so
 * the holder animates its colours instead of being rebound (no flicker,
 * no image / text reset).
 */
class ActivityCardAdapter(
    private val spec: ActivityCardSpec,
    private val onCardClick: (position: Int) -> Unit,
) : RecyclerView.Adapter<ActivityCardViewHolder>() {

    var items: List<ActivityCardItem> = emptyList()
        private set

    var selectedPosition: Int = 0
        private set

    /** Replaces the cards. Called once when a carousel is set up. */
    fun submit(newItems: List<ActivityCardItem>, selected: Int) {
        val oldCount = items.size
        items = newItems.toList()
        selectedPosition = if (items.isEmpty()) 0 else selected.coerceIn(0, items.lastIndex)
        if (oldCount > 0) notifyItemRangeRemoved(0, oldCount)
        if (items.isNotEmpty()) notifyItemRangeInserted(0, items.size)
    }

    /** Moves the selected look to [position]; repaints only the two cards involved. */
    fun setSelectedPosition(position: Int) {
        if (position !in items.indices || position == selectedPosition) return
        val previous = selectedPosition
        selectedPosition = position
        if (previous in items.indices) notifyItemChanged(previous, PAYLOAD_SELECTION)
        notifyItemChanged(position, PAYLOAD_SELECTION)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityCardViewHolder =
        ActivityCardViewHolder.create(parent, spec, onCardClick)

    override fun onBindViewHolder(holder: ActivityCardViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return
        holder.bind(item)
        holder.bindState(
            selected = position == selectedPosition,
            position = position,
            count = items.size,
            animate = false,
        )
    }

    override fun onBindViewHolder(
        holder: ActivityCardViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_SELECTION }) {
            holder.bindState(
                selected = position == selectedPosition,
                position = position,
                count = items.size,
                animate = true,
            )
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onViewRecycled(holder: ActivityCardViewHolder) {
        holder.cancelAnimation()
    }

    companion object {
        /** Only the selected / unselected look changed. */
        const val PAYLOAD_SELECTION = "selection"
    }
}

/**
 * One activity card. Extends [BaseViewHolder] like every other row in
 * the app (inheritance example #3): [bind] fills the content, and
 * [bindState] paints the selected or unselected look.
 *
 * Selected: charcoal card with a matching border, white name, muted
 * description, lime icon tile. Unselected: white card with a divider
 * border, dark name, grey description, pale icon tile. A change of
 * state blends between the two over [STATE_ANIMATION_MS].
 */
class ActivityCardViewHolder private constructor(
    private val binding: ItemActivityCardBinding,
    private val onCardClick: (position: Int) -> Unit,
) : BaseViewHolder<ActivityCardItem>(binding) {

    private val context = binding.root.context
    private val strokeWidth = context.resources.getDimensionPixelSize(R.dimen.stroke_hairline)

    private val cardOff = ContextCompat.getColor(context, R.color.surface_white)
    private val cardOn = ContextCompat.getColor(context, R.color.surface_charcoal)
    private val borderOff = ContextCompat.getColor(context, R.color.divider)
    private val nameOff = ContextCompat.getColor(context, R.color.text_on_lime)
    private val nameOn = ContextCompat.getColor(context, R.color.text_on_dark)
    private val descriptionOff = ContextCompat.getColor(context, R.color.text_secondary)
    private val descriptionOn = ContextCompat.getColor(context, R.color.text_muted_on_dark)
    private val tileOff = ContextCompat.getColor(context, R.color.surface_container_low)
    private val tileOn = ContextCompat.getColor(context, R.color.lime_accent)

    private val cardBackground = GradientDrawable()
    private val tileBackground = GradientDrawable()

    /** What TalkBack calls this card ([ActivityCardItem.accessibilityNameRes]). */
    private var spokenName: String? = null

    /** 0 = unselected look, 1 = selected look. */
    private var selectionFraction = -1f
    private var animator: ValueAnimator? = null

    init {
        binding.activityCard.background = RippleDrawable(
            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface_container_high)),
            cardBackground,
            null,
        )
        binding.activityCardIconTile.background = tileBackground
        // The icon is text_on_lime in both states (it sits on lime or on the pale tile).
        ImageViewCompat.setImageTintList(
            binding.activityCardIcon,
            ColorStateList.valueOf(nameOff),
        )
        binding.activityCardName.typeface = Fonts.extrabold(context)
        binding.activityCardDescription.typeface = Fonts.regular(context)
        binding.activityCard.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onCardClick(position)
        }
    }

    private fun applySpec(spec: ActivityCardSpec) {
        val res = context.resources
        val padding = res.getDimensionPixelSize(spec.padding)
        val gap = res.getDimensionPixelSize(spec.contentGap)
        val tile = res.getDimensionPixelSize(spec.iconTileSize)
        val icon = res.getDimensionPixelSize(spec.iconSize)

        binding.activityCard.updateLayoutParams {
            width = res.getDimensionPixelSize(spec.cardWidth)
            height = res.getDimensionPixelSize(spec.cardHeight)
        }
        binding.activityCard.setPadding(padding, padding, padding, padding)
        cardBackground.cornerRadius = res.getDimension(spec.cornerRadius)

        binding.activityCardIconTile.updateLayoutParams {
            width = tile
            height = tile
        }
        tileBackground.cornerRadius = res.getDimension(spec.iconTileRadius)
        binding.activityCardIcon.updateLayoutParams {
            width = icon
            height = icon
        }

        binding.activityCardName.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            res.getDimension(spec.nameTextSize),
        )
        binding.activityCardName.updateLayoutParams<LinearLayout.LayoutParams> { topMargin = gap }

        val descriptionSize = res.getDimension(spec.descriptionTextSize)
        binding.activityCardDescription.setTextSize(TypedValue.COMPLEX_UNIT_PX, descriptionSize)
        TextViewCompat.setLineHeight(
            binding.activityCardDescription,
            (descriptionSize * spec.descriptionLineHeight).roundToInt(),
        )
        binding.activityCardDescription.updateLayoutParams<LinearLayout.LayoutParams> {
            topMargin = gap
        }
    }

    override fun bind(item: ActivityCardItem) {
        binding.activityCardIcon.setImageResource(item.iconRes)
        binding.activityCardName.setText(item.nameRes)
        binding.activityCardDescription.setText(item.descriptionRes)
        spokenName = context.getString(item.accessibilityNameRes)
    }

    /**
     * Paints the selected / unselected look and the TalkBack label
     * ("Swimming, 3 of 5"). With [animate] the colours blend from the
     * current look; otherwise they jump straight to it.
     */
    fun bindState(selected: Boolean, position: Int, count: Int, animate: Boolean) {
        val name = spokenName ?: binding.activityCardName.text
        binding.activityCard.contentDescription = context.getString(
            R.string.activity_card_content_description,
            name,
            position + 1,
            count,
        )
        ViewCompat.setStateDescription(
            binding.activityCard,
            if (selected) context.getString(R.string.activity_card_state_selected) else null,
        )

        val target = if (selected) 1f else 0f
        animator?.cancel()
        animator = null
        if (!animate || selectionFraction < 0f || selectionFraction == target) {
            applySelectionFraction(target)
            return
        }
        animator = ValueAnimator.ofFloat(selectionFraction, target).apply {
            duration = STATE_ANIMATION_MS
            addUpdateListener { applySelectionFraction(it.animatedValue as Float) }
            start()
        }
    }

    fun cancelAnimation() {
        animator?.cancel()
        animator = null
    }

    private fun applySelectionFraction(fraction: Float) {
        selectionFraction = fraction
        cardBackground.setColor(ColorUtils.blendARGB(cardOff, cardOn, fraction))
        cardBackground.setStroke(strokeWidth, ColorUtils.blendARGB(borderOff, cardOn, fraction))
        tileBackground.setColor(ColorUtils.blendARGB(tileOff, tileOn, fraction))
        binding.activityCardName.setTextColor(ColorUtils.blendARGB(nameOff, nameOn, fraction))
        binding.activityCardDescription.setTextColor(
            ColorUtils.blendARGB(descriptionOff, descriptionOn, fraction),
        )
    }

    companion object {
        /** Matches the 0.35 s background transition in the designs. */
        private const val STATE_ANIMATION_MS = 350L

        fun create(
            parent: ViewGroup,
            spec: ActivityCardSpec,
            onCardClick: (position: Int) -> Unit,
        ): ActivityCardViewHolder {
            val binding = ItemActivityCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
            return ActivityCardViewHolder(binding, onCardClick).also { it.applySpec(spec) }
        }
    }
}
