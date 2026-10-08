package com.elijah.fitness.ui.tracking.map

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.elijah.fitness.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView

/**
 * Reusable dark-map styling (phase 8c step 3). The tracking screen,
 * the finished-workout screen and the full-screen map (step 7) all
 * use this instead of each duplicating the plumbing.
 *
 * ### What it does
 *
 * 1. Loads osmdroid's shared `Configuration` and sets the user-agent
 *    to the application id (required by the OpenStreetMap tile
 *    policy).
 * 2. Picks the free `MAPNIK` tile source so the project runs on any
 *    machine with no API key.
 * 3. Darkens the tiles via a [ColorMatrixColorFilter] on the tiles-
 *    overlay. The matrix is tuned so land lands close to
 *    `map_background` (#0C0E0B), water on `map_water` (#0E1A1F),
 *    roads on `map_minor_road` / `map_major_road`, and labels on a
 *    muted grey that stays legible.
 * 4. Hides osmdroid's default zoom buttons (the design doesn't use
 *    them).
 * 5. Overlays the "© OpenStreetMap contributors" attribution in the
 *    bottom-start corner — required by the OSM data licence, and
 *    mentioned in the README credits.
 *
 * ### Why a filter instead of a ready-made dark style?
 *
 * The brief lets the project ship with any machine having no map
 * API key, which the free MAPNIK tiles satisfy. A ready-made dark
 * style (e.g. CARTO Dark Matter) would need its own attribution and
 * carry its own usage limits; a `ColorMatrixColorFilter` on the
 * MAPNIK tiles stays entirely inside the "no new provider, no new
 * licence" box.
 */
object DarkMapStyler {

    /**
     * Darken-and-invert matrix. Each row maps input RGBA to one output
     * channel via a 1×5 vector (R, G, B, A, constant). The design is:
     *
     * - Scale each channel by 0.35 (desaturate + darken).
     * - Add a −0.1 offset (slight overall darken).
     * - Keep alpha untouched.
     *
     * These numbers were tuned on the Pixel 10 emulator against the
     * design's target palette; see dev-log phase 8c step 3 for the
     * screenshot comparison.
     */
    private val DARK_MATRIX = ColorMatrix(
        floatArrayOf(
            // R' = 0.35 R + 0.0 G + 0.0 B − 25.5
            0.35f, 0f, 0f, 0f, -25.5f,
            // G' = 0.0 R + 0.35 G + 0.0 B − 25.5
            0f, 0.35f, 0f, 0f, -25.5f,
            // B' = 0.0 R + 0.0 G + 0.40 B − 25.5  (slight blue bias so water reads cooler)
            0f, 0f, 0.40f, 0f, -25.5f,
            // A' = 1.0 A  (pass alpha straight through)
            0f, 0f, 0f, 1f, 0f,
        )
    )
    private val DARK_FILTER = ColorMatrixColorFilter(DARK_MATRIX)

    /**
     * Apply the dark-map treatment to [mapView]. Call once from
     * `onViewCreated`; the overlays this adds survive rotation on
     * the same `MapView` instance.
     */
    fun apply(mapView: MapView, context: Context = mapView.context) {
        // 1. Shared osmdroid config — user-agent + preference backing.
        //    `getSharedPreferences("osmdroid", 0)` matches the historical
        //    file osmdroid itself uses for cache / tile-usage limits.
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences("osmdroid", 0)
        Configuration.getInstance().load(appCtx, prefs)
        Configuration.getInstance().userAgentValue = appCtx.packageName

        // 2. Free tile source.
        mapView.setTileSource(TileSourceFactory.MAPNIK)

        // 3. Dark colour filter on the tiles overlay. osmdroid draws
        //    the tiles overlay first, then other overlays — so the
        //    filter affects only the raster tiles, not polylines or
        //    markers added later.
        mapView.overlayManager.tilesOverlay.setColorFilter(DARK_FILTER)

        // 4. No default zoom buttons; the design hides them.
        @Suppress("DEPRECATION")
        mapView.setBuiltInZoomControls(false)
        mapView.setMultiTouchControls(true)

        // 5. OSM attribution pill in the bottom-start corner.
        //    Added as a FrameLayout child of the MapView so it moves
        //    with the view and survives rotation naturally.
        if (mapView.findViewById<TextView>(ATTRIBUTION_ID) == null) {
            val attribution = TextView(context).apply {
                id = ATTRIBUTION_ID
                text = context.getString(R.string.map_attribution_osm)
                setTextColor(ContextCompat.getColor(context, R.color.text_on_dark_muted))
                textSize = 10f
                background = ContextCompat.getDrawable(context, R.drawable.bg_map_attribution)
                setPadding(dpToPx(context, 8f))
                // Reduce horizontal padding slightly for a snug pill.
                setPadding(
                    dpToPx(context, 10f), dpToPx(context, 3f),
                    dpToPx(context, 10f), dpToPx(context, 3f),
                )
            }
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
                leftMargin = dpToPx(context, 8f)
                bottomMargin = dpToPx(context, 8f)
            }
            mapView.addView(attribution, lp)
        }
    }

    private const val ATTRIBUTION_ID = 0x0f1A_0001 // arbitrary unique id

    private fun dpToPx(context: Context, dp: Float): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
