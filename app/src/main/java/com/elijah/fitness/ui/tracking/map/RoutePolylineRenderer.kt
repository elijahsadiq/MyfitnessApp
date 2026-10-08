package com.elijah.fitness.ui.tracking.map

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.ContextCompat
import com.elijah.fitness.R
import com.elijah.fitness.tracking.RouteGeometry
import com.elijah.fitness.tracking.TrackPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

/**
 * Draws the glowing lime route on a [MapView] (phase 8c step 3).
 *
 * Three polyline layers are stacked, all `#D4F26A` with round caps and
 * joins so the stroke doesn't butt into a square end on paused gaps:
 *
 * | Layer       | Width | Opacity |
 * |-------------|-------|---------|
 * | Outer glow  | 18 dp | 14 %    |
 * | Inner glow  |  9 dp | 35 %    |
 * | Line        |  4 dp | 100 %   |
 *
 * ### Paused gaps
 *
 * [render] takes the raw point list, splits it with
 * [RouteGeometry.splitByPause] so a paused section leaves a visible
 * gap in each layer rather than a straight line jumping across the
 * map, and emits one `Polyline` per segment per layer. Three layers
 * × N segments means the overlay list grows linearly with route
 * fragmentation; a typical run has one or two segments.
 */
class RoutePolylineRenderer(
    private val mapView: MapView,
    private val context: Context = mapView.context,
) {

    private val lineColor: Int =
        ContextCompat.getColor(context, R.color.lime_accent)

    private val outerPaint: Paint = basePaint(widthDp = 18f, alpha = (0.14f * 255).toInt())
    private val innerPaint: Paint = basePaint(widthDp = 9f, alpha = (0.35f * 255).toInt())
    private val linePaint: Paint = basePaint(widthDp = 4f, alpha = 255)

    private val overlays: MutableList<Polyline> = mutableListOf()

    /**
     * Replace every previously-rendered polyline with new ones for
     * [points]. Call again on each state emission; the renderer owns
     * its own overlay instances so repeated calls don't leak.
     */
    fun render(points: List<TrackPoint>) {
        clear()
        if (points.size < 2) return

        val segments = RouteGeometry.splitByPause(points)
        // Three layers per segment: outer glow first (deepest), then
        // inner glow, then the crisp line last so it renders on top.
        segments.forEach { seg -> addLayer(seg, outerPaint) }
        segments.forEach { seg -> addLayer(seg, innerPaint) }
        segments.forEach { seg -> addLayer(seg, linePaint) }

        mapView.invalidate()
    }

    /** Remove every overlay this renderer has added so far. */
    fun clear() {
        if (overlays.isEmpty()) return
        mapView.overlays.removeAll(overlays)
        overlays.clear()
        mapView.invalidate()
    }

    private fun addLayer(segment: List<TrackPoint>, paint: Paint) {
        if (segment.size < 2) return
        val polyline = Polyline(mapView).apply {
            outlinePaint.set(paint)
            setPoints(segment.map { GeoPoint(it.latitude, it.longitude) })
            isGeodesic = false
        }
        mapView.overlays.add(polyline)
        overlays.add(polyline)
    }

    private fun basePaint(widthDp: Float, alpha: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(widthDp)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(alpha, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor))
    }

    private fun dpToPx(dp: Float): Float =
        dp * context.resources.displayMetrics.density
}
