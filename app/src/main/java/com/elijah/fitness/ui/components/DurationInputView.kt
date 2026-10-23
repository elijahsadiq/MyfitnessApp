package com.elijah.fitness.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.doAfterTextChanged
import com.elijah.fitness.R
import com.elijah.fitness.databinding.ViewDurationInputBinding

/**
 * The h / min / s duration cells of every log form (phase 8d,
 * LogSwim.dc.html): three white cards side by side, each a big number
 * field with its unit right after it ("32 min"). The card of the field
 * being typed in gets a 2dp dark border.
 *
 * Used by Log a swim, the run / ride / row form and the workout form, so
 * they all read the duration the same way. The view only collects the
 * three numbers; the screen's ViewModel validates them (minutes and
 * seconds 0–59, total above zero).
 *
 * - Tapping anywhere on a card (not only on the number) puts the cursor
 *   in its field and opens the keyboard.
 * - An empty field shows a "0" placeholder: grey while the whole
 *   duration is empty, dark like a typed number once any field has a
 *   value, so "0 h 32 min 10 s" reads as one value (as in the design).
 * - TalkBack reads each field as "Hours", "Minutes", "Seconds" (the
 *   cards themselves and the unit labels are skipped).
 * - `android:imeOptions` on this view sets the seconds field's keyboard
 *   action (next by default, `actionDone` on the workout form).
 */
class DurationInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewDurationInputBinding.inflate(LayoutInflater.from(context), this)

    val hoursInput: EditText get() = binding.durationHoursInput
    val minutesInput: EditText get() = binding.durationMinutesInput
    val secondsInput: EditText get() = binding.durationSecondsInput

    /** Typed hours; 0 when empty or not a number. */
    val hours: Int get() = read(binding.durationHoursInput)
    val minutes: Int get() = read(binding.durationMinutesInput)
    val seconds: Int get() = read(binding.durationSecondsInput)

    init {
        orientation = HORIZONTAL
        isBaselineAligned = false

        val a = context.obtainStyledAttributes(attrs, R.styleable.DurationInputView, defStyleAttr, 0)
        try {
            binding.durationSecondsInput.imeOptions = a.getInt(
                R.styleable.DurationInputView_android_imeOptions,
                EditorInfo.IME_ACTION_NEXT,
            )
        } finally {
            a.recycle()
        }

        bindCell(binding.durationHoursCell, binding.durationHoursInput, R.string.log_duration_hours_label)
        bindCell(binding.durationMinutesCell, binding.durationMinutesInput, R.string.log_duration_minutes_label)
        bindCell(binding.durationSecondsCell, binding.durationSecondsInput, R.string.log_duration_seconds_label)

        inputs.forEach { input -> input.doAfterTextChanged { updatePlaceholderColour() } }
        updatePlaceholderColour()
    }

    private val inputs: List<EditText>
        get() = listOf(
            binding.durationHoursInput,
            binding.durationMinutesInput,
            binding.durationSecondsInput,
        )

    /** Calls [action] after any of the three numbers changes. */
    fun doOnDurationChanged(action: () -> Unit) {
        inputs.forEach { input -> input.doAfterTextChanged { action() } }
    }

    private fun bindCell(cell: View, input: EditText, labelRes: Int) {
        cell.setOnClickListener { focusField(input) }
        val label = context.getString(labelRes)
        ViewCompat.setAccessibilityDelegate(input, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                // The visible hint is just "0"; TalkBack says what the field is.
                info.hintText = label
            }
        })
    }

    /** Grey "0"s while nothing is typed; dark ones next to a typed value. */
    private fun updatePlaceholderColour() {
        val anyTyped = inputs.any { !it.text.isNullOrEmpty() }
        val colour = ContextCompat.getColor(
            context,
            if (anyTyped) R.color.text_on_lime else R.color.text_placeholder,
        )
        inputs.forEach { if (it.currentHintTextColor != colour) it.setHintTextColor(colour) }
    }

    private fun focusField(input: EditText) {
        if (!input.isEnabled) return
        input.requestFocus()
        input.setSelection(input.text?.length ?: 0)
        context.getSystemService<InputMethodManager>()
            ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun read(input: EditText): Int =
        input.text?.toString()?.trim()?.toIntOrNull() ?: 0
}
