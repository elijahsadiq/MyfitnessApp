package com.elijah.fitness.util

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import com.elijah.fitness.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Tiny `Typeface` cache for custom views that draw text with `Paint`.
 *
 * `Paint.setTypeface` can't resolve a font resource by itself the way
 * `TextView` does — the view has to load the `Typeface` through
 * [ResourcesCompat.getFont] first. That call parses the font file every
 * time, which is unnecessary when `BarChartView` / `WeeklyMomentumView`
 * and the Home metrics all want the same two weights.
 *
 * This object caches by resource id (process-scoped) so each custom view
 * pays the parse cost once, then shares the result.
 */
object Fonts {

    private val cache: MutableMap<Int, Typeface> = ConcurrentHashMap()

    fun regular(context: Context): Typeface =
        get(context, R.font.plus_jakarta_sans_regular)

    fun medium(context: Context): Typeface =
        get(context, R.font.plus_jakarta_sans_medium)

    fun semibold(context: Context): Typeface =
        get(context, R.font.plus_jakarta_sans_semibold)

    fun bold(context: Context): Typeface =
        get(context, R.font.plus_jakarta_sans_bold)

    fun extrabold(context: Context): Typeface =
        get(context, R.font.plus_jakarta_sans_extrabold)

    private fun get(context: Context, @FontRes fontRes: Int): Typeface =
        cache.getOrPut(fontRes) {
            // Non-null in release: all five TTFs ship in the APK.
            ResourcesCompat.getFont(context.applicationContext, fontRes)
                ?: Typeface.DEFAULT
        }
}
