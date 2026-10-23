package com.elijah.fitness.ui.components

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.updateLayoutParams
import com.elijah.fitness.R
import com.elijah.fitness.databinding.ViewStepperBinding

/**
 * "−  value  +" stepper (phase 8d, LogSwim.dc.html; reused by the New
 * goal sheet). The view only draws and reports taps: the screen's
 * ViewModel owns the value, its step and its limits, and sends back the
 * text to show ([valueText]) and which buttons still work
 * ([setStepEnabled]).
 *
 * - **Buttons:** a 44dp circle inside a 48dp touch target each; "−" is
 *   background_warm with a 1dp border, "+" is lime. A tap is one step,
 *   a long press repeats and speeds up ([HoldToRepeat]). The symbols
 *   are [StepperSymbolView]s, so each glyph sits exactly in the middle
 *   of its circle.
 * - **Limits:** a button that can't step any further is disabled and
 *   faded (minus at the minimum, plus at the maximum).
 * - **TalkBack:** the buttons read as buttons with their own labels
 *   (`stepperDecrementDescription` / `stepperIncrementDescription`,
 *   e.g. "One lap fewer"); the value is a polite live region, so each
 *   change is read out.
 * - **Sizes:** `stepperValueTextSize`, `stepperValueMinWidth`,
 *   `stepperSymbolTextSize` and `stepperValueGap` (28sp / 48dp / 22sp /
 *   8dp by default, from `Widget.Fitness.Stepper`; the New goal sheet
 *   uses `Widget.Fitness.Stepper.Goal`).
 */
class StepperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** Receives -1 (minus) or +1 (plus), once per step. */
    fun interface OnStepListener {
        fun onStep(direction: Int)
    }

    private val binding = ViewStepperBinding.inflate(LayoutInflater.from(context), this)
    private var listener: OnStepListener? = null

    private val minusRepeat = HoldToRepeat(binding.stepperMinus) { listener?.onStep(-1) }
    private val plusRepeat = HoldToRepeat(binding.stepperPlus) { listener?.onStep(+1) }

    /** The text in the middle ("60", "50 km", "30 days"). */
    var valueText: CharSequence
        get() = binding.stepperValue.text
        set(value) {
            // Same text again: leave it, so TalkBack doesn't re-read it.
            if (binding.stepperValue.text.toString() != value.toString()) {
                binding.stepperValue.text = value
            }
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        val a = context.obtainStyledAttributes(
            attrs,
            R.styleable.StepperView,
            defStyleAttr,
            R.style.Widget_Fitness_Stepper,
        )
        try {
            binding.stepperValue.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                a.getDimension(
                    R.styleable.StepperView_stepperValueTextSize,
                    resources.getDimension(R.dimen.text_size_stepper_value),
                ),
            )
            binding.stepperValue.minWidth = a.getDimensionPixelSize(
                R.styleable.StepperView_stepperValueMinWidth,
                resources.getDimensionPixelSize(R.dimen.stepper_value_min_width),
            )
            val symbolSize = a.getDimension(
                R.styleable.StepperView_stepperSymbolTextSize,
                resources.getDimension(R.dimen.text_size_stepper_symbol),
            )
            binding.stepperMinus.setTextSize(TypedValue.COMPLEX_UNIT_PX, symbolSize)
            binding.stepperPlus.setTextSize(TypedValue.COMPLEX_UNIT_PX, symbolSize)
            val valueGap = a.getDimensionPixelSize(
                R.styleable.StepperView_stepperValueGap,
                resources.getDimensionPixelSize(R.dimen.stepper_value_gap),
            )
            binding.stepperValue.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = valueGap
                marginEnd = valueGap
            }
            binding.stepperMinus.contentDescription =
                a.getText(R.styleable.StepperView_stepperDecrementDescription)
            binding.stepperPlus.contentDescription =
                a.getText(R.styleable.StepperView_stepperIncrementDescription)
        } finally {
            a.recycle()
        }

        ViewCompat.setAccessibilityDelegate(binding.stepperMinus, ButtonRole)
        ViewCompat.setAccessibilityDelegate(binding.stepperPlus, ButtonRole)
    }

    fun setOnStepListener(listener: OnStepListener?) {
        this.listener = listener
    }

    /**
     * Enables or disables each button. A disabled button fades to
     * [CarouselNavButtons.DISABLED_ALPHA] and stops any repeat in progress.
     */
    fun setStepEnabled(canDecrement: Boolean, canIncrement: Boolean) {
        setButtonEnabled(binding.stepperMinus, minusRepeat, canDecrement)
        setButtonEnabled(binding.stepperPlus, plusRepeat, canIncrement)
    }

    private fun setButtonEnabled(button: View, repeat: HoldToRepeat, enabled: Boolean) {
        if (!enabled) repeat.stop()
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else CarouselNavButtons.DISABLED_ALPHA
    }

    /** The "−" and "+" text views read as buttons. */
    private object ButtonRole : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfoCompat,
        ) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = Button::class.java.name
        }
    }
}
