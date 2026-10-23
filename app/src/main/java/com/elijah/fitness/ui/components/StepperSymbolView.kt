package com.elijah.fitness.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.withTranslation
import kotlin.math.roundToInt

/**
 * The "−" or "+" of a [StepperView], drawn at the exact centre of its
 * 44dp circle.
 *
 * A TextView centres its *line* (the font's ascent to descent), not the
 * glyph. A minus or plus sits on the maths axis, a little below the
 * middle of that line, so in Plus Jakarta Sans the symbol landed about
 * 3.5dp under the circle's centre. This view measures the glyph's real
 * ink bounds and moves only the text by the difference when drawing:
 * the circle (the background) stays where it is, the touch target and
 * layout don't change, and the result is right for any font size or
 * the user's font scale.
 *
 * The glyph is measured again only on the first draw after its text,
 * size or layout changed, into one reused [Rect]; other frames just
 * apply the stored offset.
 */
class StepperSymbolView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val glyphBounds = Rect()

    /** The symbol as a String, cached so drawing doesn't build one each frame. */
    private var glyph: String = ""

    /** Pixels to move the text down (negative: up) so the glyph is centred. */
    private var glyphShiftY = 0

    /**
     * Set when the text, its size or the view's size changed. Only a
     * plain Boolean: TextView's constructor already calls
     * [onTextChanged], before this class's fields are set up.
     */
    private var glyphDirty = true

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        glyphDirty = true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        glyphDirty = true
    }

    override fun onDraw(canvas: Canvas) {
        if (glyphDirty) updateGlyphShift()
        if (glyphShiftY == 0) {
            super.onDraw(canvas)
            return
        }
        canvas.withTranslation(y = glyphShiftY.toFloat()) { super.onDraw(this) }
    }

    /**
     * Where the glyph's ink is centred now ([getBaseline] already counts
     * the padding and the vertical gravity) against the middle of the
     * view, rounded to whole pixels so the symbol stays crisp.
     */
    private fun updateGlyphShift() {
        glyphDirty = false
        glyphShiftY = 0
        glyph = text?.toString().orEmpty()
        if (glyph.isEmpty() || layout == null || height <= 0) return
        paint.getTextBounds(glyph, 0, glyph.length, glyphBounds)
        if (glyphBounds.isEmpty) return
        val baselineY = baseline
        if (baselineY < 0) return
        val glyphCentreY = baselineY + (glyphBounds.top + glyphBounds.bottom) / 2f
        glyphShiftY = (height / 2f - glyphCentreY).roundToInt()
    }
}
