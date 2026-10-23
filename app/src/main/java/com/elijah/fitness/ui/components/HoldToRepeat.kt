package com.elijah.fitness.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

/**
 * When a held stepper button repeats, and how fast (phase 8d). Pure
 * Kotlin so the timing is unit-tested.
 *
 * - A tap is one step.
 * - Holding for [HOLD_THRESHOLD_MS] starts repeating: every 160 ms, then
 *   every 80 ms after 6 repeats and every 35 ms after 14, so holding "+"
 *   races through a big range without hundreds of taps.
 */
object HoldRepeatSchedule {

    /** How long the button must be held before it starts repeating. */
    const val HOLD_THRESHOLD_MS: Long = 350L

    private const val INTERVAL_MS: Long = 160L
    private const val INTERVAL_FAST_MS: Long = 80L
    private const val INTERVAL_FASTER_MS: Long = 35L
    private const val FAST_AFTER_REPEATS: Int = 6
    private const val FASTER_AFTER_REPEATS: Int = 14

    /** Delay before the next repeat, once [repeats] steps have repeated. */
    fun intervalAfter(repeats: Int): Long = when {
        repeats < FAST_AFTER_REPEATS -> INTERVAL_MS
        repeats < FASTER_AFTER_REPEATS -> INTERVAL_FAST_MS
        else -> INTERVAL_FASTER_MS
    }
}

/**
 * Makes [view] a hold-to-repeat button (phase 8d, used by [StepperView]).
 *
 * - **Tap:** one [onStep], on release, like any button. Starting a
 *   scroll on the button (the scroll view takes the gesture) or sliding
 *   off it doesn't step.
 * - **Hold:** after [HoldRepeatSchedule.HOLD_THRESHOLD_MS], steps
 *   repeat and speed up ([HoldRepeatSchedule.intervalAfter]) until the
 *   finger lifts. From then on the hold keeps the gesture, so a thumb
 *   drifting down can't scroll the page or drag a bottom sheet closed.
 * - **TalkBack, Switch Access, keyboards:** they click instead of
 *   touching; a click is exactly one step.
 *
 * Repeating stops on release, when the view is disabled (a stepper
 * reaching its limit), when it leaves the window, and on [stop].
 */
class HoldToRepeat(
    private val view: View,
    private val onStep: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var holding = false
    private var repeats = 0

    private val repeat: Runnable = object : Runnable {
        override fun run() {
            if (!view.isEnabled || !view.isAttachedToWindow || !view.isPressed) {
                stop()
                return
            }
            if (!holding) {
                // From the first repeat on, this hold owns the gesture: a
                // thumb that drifts while ramping a value must not hand it
                // to a scroll view or drag a bottom sheet closed. Android
                // clears this when the finger lifts.
                view.parent?.requestDisallowInterceptTouchEvent(true)
            }
            holding = true
            onStep()
            repeats++
            handler.postDelayed(this, HoldRepeatSchedule.intervalAfter(repeats))
        }
    }

    init {
        view.setOnClickListener { onStep() }
        view.setOnTouchListener(::onTouch)
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) = stop()
        })
    }

    /** Stops a repeat in progress (and the pressed look). */
    fun stop() {
        handler.removeCallbacks(repeat)
        holding = false
        repeats = 0
        view.isPressed = false
    }

    // The tap goes through performClick(), so accessibility still hears it.
    @SuppressLint("ClickableViewAccessibility")
    private fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stop()
                v.drawableHotspotChanged(event.x, event.y)
                v.isPressed = true
                handler.postDelayed(repeat, HoldRepeatSchedule.HOLD_THRESHOLD_MS)
            }
            MotionEvent.ACTION_MOVE -> {
                v.drawableHotspotChanged(event.x, event.y)
                val inside = event.x >= 0f && event.y >= 0f &&
                    event.x <= v.width && event.y <= v.height
                if (!inside && v.isPressed) stop()
            }
            MotionEvent.ACTION_UP -> {
                val tap = v.isPressed && !holding
                stop()
                if (tap && v.isEnabled) v.performClick()
            }
            MotionEvent.ACTION_CANCEL -> stop()
        }
        return true
    }
}
