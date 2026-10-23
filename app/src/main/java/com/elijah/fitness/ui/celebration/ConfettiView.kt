package com.elijah.fitness.ui.celebration

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.elijah.fitness.R
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lime confetti behind the goal-complete card (phase 8d, Step 7). A small
 * custom View instead of a library: about 80 pieces (7 × 14dp and
 * 6 × 12dp rounded rectangles, 3–4dp dots) burst from the top third,
 * then fall with gravity, spin and a slight sway.
 *
 * - One [ValueAnimator] (2.5 s) drives everything; [onDraw] only reads
 *   the preallocated arrays, so nothing is allocated while drawing.
 * - With animations switched off (`ValueAnimator.areAnimatorsEnabled()`
 *   is false) it draws the design's static scatter instead.
 * - Purely decorative: not focusable, hidden from TalkBack.
 */
class ConfettiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private enum class Mode { NONE, BURST, STATIC }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val palette = intArrayOf(
        ContextCompat.getColor(context, R.color.lime_accent),
        ContextCompat.getColor(context, R.color.confetti_lime_mid),
        ContextCompat.getColor(context, R.color.confetti_lime_soft),
        ContextCompat.getColor(context, R.color.surface_white),
    )

    // Burst pieces, preallocated (positions in px, speeds in px/s).
    private val startX = FloatArray(PIECES)
    private val startY = FloatArray(PIECES)
    private val speedX = FloatArray(PIECES)
    private val speedY = FloatArray(PIECES)
    private val angle0 = FloatArray(PIECES)
    private val spin = FloatArray(PIECES)
    private val swayAmp = FloatArray(PIECES)
    private val swayFreq = FloatArray(PIECES)
    private val swayPhase = FloatArray(PIECES)
    private val pieceW = FloatArray(PIECES)
    private val pieceH = FloatArray(PIECES)
    /** 0 = rounded rectangle, 1 = dot (radius = [pieceW] / 2). */
    private val pieceKind = IntArray(PIECES)
    private val pieceColor = IntArray(PIECES)

    private var gravity = 0f
    private var elapsedS = 0f
    private var mode = Mode.NONE
    private var burstPending = false
    private var animator: ValueAnimator? = null

    private val updateListener = ValueAnimator.AnimatorUpdateListener { anim ->
        elapsedS = anim.animatedFraction * DURATION_S
        postInvalidateOnAnimation()
    }

    init {
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /**
     * Fire the burst once, or show the static scatter when the system
     * has animations switched off. Safe before layout: it waits for a size.
     */
    fun play() {
        if (!animationsEnabled()) {
            showStatic()
            return
        }
        if (width == 0 || height == 0) {
            burstPending = true
            return
        }
        startBurst()
    }

    /** Draw the design's still confetti (no motion). */
    fun showStatic() {
        cancelAnimator()
        burstPending = false
        mode = Mode.STATIC
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (burstPending && w > 0 && h > 0) {
            burstPending = false
            startBurst()
        }
    }

    override fun onDetachedFromWindow() {
        cancelAnimator()
        burstPending = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (mode) {
            Mode.NONE -> Unit
            Mode.BURST -> drawBurst(canvas)
            Mode.STATIC -> drawStatic(canvas)
        }
    }

    private fun startBurst() {
        cancelAnimator()
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val random = Random(System.nanoTime())
        gravity = h * GRAVITY_PER_HEIGHT
        for (i in 0 until PIECES) {
            startX[i] = random.nextFloat() * w
            startY[i] = (random.nextFloat() * TOP_THIRD - START_ABOVE) * h
            speedX[i] = (random.nextFloat() * 2f - 1f) * w * MAX_SPEED_X_PER_WIDTH
            speedY[i] = -(MIN_LIFT + random.nextFloat() * (MAX_LIFT - MIN_LIFT)) * h
            angle0[i] = random.nextFloat() * 360f
            spin[i] = (random.nextFloat() * 2f - 1f) * MAX_SPIN_DEG_S
            swayAmp[i] = (2f + random.nextFloat() * 6f) * density
            swayFreq[i] = (1.5f + random.nextFloat() * 2f) * TWO_PI
            swayPhase[i] = random.nextFloat() * TWO_PI
            pieceColor[i] = palette[random.nextInt(palette.size)]
            when (random.nextInt(3)) {
                0 -> setShape(i, kind = 0, w = 7f, h = 14f)
                1 -> setShape(i, kind = 0, w = 6f, h = 12f)
                else -> {
                    val r = 3f + random.nextInt(3) * 0.5f // 3, 3.5 or 4dp
                    setShape(i, kind = 1, w = r * 2f, h = r * 2f)
                }
            }
        }
        elapsedS = 0f
        mode = Mode.BURST
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener(updateListener)
            start()
        }
        invalidate()
    }

    private fun setShape(i: Int, kind: Int, w: Float, h: Float) {
        pieceKind[i] = kind
        pieceW[i] = w * density
        pieceH[i] = h * density
    }

    private fun drawBurst(canvas: Canvas) {
        val t = elapsedS
        if (t >= DURATION_S) return
        // Fade out over the last part of the burst.
        val alpha = if (t <= FADE_START_S) {
            255
        } else {
            (255f * (1f - (t - FADE_START_S) / (DURATION_S - FADE_START_S))).toInt().coerceIn(0, 255)
        }
        val h = height.toFloat()
        val w = width.toFloat()
        for (i in 0 until PIECES) {
            val x = startX[i] + speedX[i] * t + swayAmp[i] * sin(swayFreq[i] * t + swayPhase[i])
            val y = startY[i] + speedY[i] * t + 0.5f * gravity * t * t
            val margin = pieceH[i]
            if (y < -margin || y > h + margin || x < -margin || x > w + margin) continue
            drawPiece(canvas, x, y, angle0[i] + spin[i] * t, pieceKind[i], pieceW[i], pieceH[i], pieceColor[i], alpha)
        }
    }

    private fun drawStatic(canvas: Canvas) {
        val sx = width / DESIGN_WIDTH
        val sy = height / DESIGN_HEIGHT
        var row = 0
        while (row + STATIC_STRIDE <= STATIC.size) {
            val kind = STATIC[row].toInt()
            val cx = STATIC[row + 1] * sx
            val cy = STATIC[row + 2] * sy
            val w = STATIC[row + 3] * density
            val h = STATIC[row + 4] * density
            val rotation = STATIC[row + 5]
            val color = palette[STATIC[row + 6].toInt().coerceIn(0, palette.size - 1)]
            drawPiece(canvas, cx, cy, rotation, kind, w, h, color, 255)
            row += STATIC_STRIDE
        }
    }

    private fun drawPiece(
        canvas: Canvas,
        x: Float,
        y: Float,
        rotation: Float,
        kind: Int,
        w: Float,
        h: Float,
        color: Int,
        alpha: Int,
    ) {
        paint.color = color
        paint.alpha = alpha
        if (kind == 1) {
            canvas.drawCircle(x, y, w / 2f, paint)
            return
        }
        val save = canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        rect.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        val corner = CORNER_DP * density
        canvas.drawRoundRect(rect, corner, corner, paint)
        canvas.restoreToCount(save)
    }

    private fun cancelAnimator() {
        animator?.let {
            it.removeAllUpdateListeners()
            it.cancel()
        }
        animator = null
    }

    private fun animationsEnabled(): Boolean = try {
        ValueAnimator.areAnimatorsEnabled()
    } catch (_: Throwable) {
        true
    }

    private companion object {
        const val PIECES = 80
        const val DURATION_MS = 2_500L
        const val DURATION_S = 2.5f
        const val FADE_START_S = 1.8f
        const val CORNER_DP = 2f
        const val TWO_PI = 6.2831855f

        /** Start inside the top third (a little above the edge too). */
        const val TOP_THIRD = 0.36f
        const val START_ABOVE = 0.03f
        /** Upward kick, as a share of the view height per second. */
        const val MIN_LIFT = 0.15f
        const val MAX_LIFT = 0.65f
        const val MAX_SPEED_X_PER_WIDTH = 0.45f
        const val GRAVITY_PER_HEIGHT = 0.8f
        const val MAX_SPIN_DEG_S = 360f

        /** The design frame the static scatter was drawn in. */
        const val DESIGN_WIDTH = 390f
        const val DESIGN_HEIGHT = 844f

        /**
         * GoalComplete.dc.html's confetti: kind (0 rect, 1 dot), centre
         * x, centre y (design px), width, height (dp; a dot's diameter
         * twice), rotation (degrees), colour (0 lime, 1 mid, 2 soft,
         * 3 white).
         */
        const val STATIC_STRIDE = 7
        val STATIC = floatArrayOf(
            0f, 37.5f, 69f, 7f, 14f, 24f, 0f,
            0f, 81f, 126f, 6f, 12f, -32f, 3f,
            1f, 120f, 54f, 8f, 8f, 0f, 2f,
            0f, 161.5f, 103f, 7f, 14f, 58f, 1f,
            0f, 209f, 46f, 6f, 12f, 12f, 3f,
            1f, 246f, 118f, 7f, 7f, 0f, 0f,
            0f, 285.5f, 77f, 7f, 14f, -44f, 2f,
            0f, 333f, 134f, 6f, 12f, 30f, 0f,
            1f, 360f, 58f, 8f, 8f, 0f, 3f,
            0f, 21f, 170f, 6f, 12f, -18f, 2f,
            1f, 56f, 210f, 6f, 6f, 0f, 3f,
            0f, 107.5f, 179f, 7f, 14f, 70f, 0f,
            0f, 279.5f, 183f, 7f, 14f, -62f, 3f,
            1f, 318f, 200f, 8f, 8f, 0f, 1f,
            0f, 359f, 174f, 6f, 12f, 40f, 2f,
            0f, 11.5f, 307f, 7f, 14f, 16f, 0f,
            1f, 374f, 318f, 7f, 7f, 0f, 3f,
            0f, 9f, 436f, 6f, 12f, -36f, 3f,
            0f, 375.5f, 459f, 7f, 14f, 52f, 1f,
            1f, 12f, 560f, 8f, 8f, 0f, 2f,
            0f, 371f, 592f, 6f, 12f, -24f, 0f,
            0f, 33.5f, 707f, 7f, 14f, 64f, 0f,
            1f, 78f, 756f, 7f, 7f, 0f, 3f,
            0f, 135f, 730f, 6f, 12f, -50f, 2f,
            0f, 193.5f, 785f, 7f, 14f, 20f, 1f,
            1f, 242f, 732f, 8f, 8f, 0f, 0f,
            0f, 293f, 774f, 6f, 12f, -14f, 3f,
            0f, 347.5f, 723f, 7f, 14f, 46f, 2f,
            1f, 362f, 800f, 6f, 6f, 0f, 0f,
            1f, 24f, 812f, 6f, 6f, 0f, 1f,
        )
    }
}
