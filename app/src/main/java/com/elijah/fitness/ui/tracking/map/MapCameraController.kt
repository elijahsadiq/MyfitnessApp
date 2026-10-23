package com.elijah.fitness.ui.tracking.map

import android.view.MotionEvent
import android.view.ViewConfiguration
import com.elijah.fitness.tracking.BoundingBoxDegrees
import com.elijah.fitness.tracking.RouteGeometry
import com.elijah.fitness.tracking.TrackPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.hypot

/**
 * Camera / follow-mode controller for a [MapView] (phase 8c step 3).
 *
 * On the **finished-workout screen** the only call is
 * [fitRouteToView] once the fragment has routed its data in; the
 * controller stays in no-follow mode.
 *
 * On the **tracking screen** the controller follows the current
 * position until the user drags or pinches the map, then turns
 * following off. The re-centre button on the overlay calls
 * [followCurrent] with `true` to opt back in and immediately pans to
 * the last-known fix.
 *
 * ### Why we need our own controller
 *
 * osmdroid's `MapView` doesn't expose "follow the current location"
 * as a built-in — it only has `controller.animateTo(point)`. We
 * either call that on every state emission (and never let the user
 * pan freely) or we respect the user's gesture. This class takes
 * the second path.
 *
 * ### Only a real gesture stops following
 * Following turns off when a finger moves further than the touch slop,
 * or a second finger lands (pinch). It does **not** listen to the map's
 * scroll events: osmdroid fires one for every frame of an `animateTo`
 * and for every `setCenter` / centre offset change, so a scroll-based
 * check switched following off by itself right after the screen opened
 * (the map then stayed on the phone's old last-known position while the
 * route was drawn off screen). A plain tap doesn't count either.
 */
class MapCameraController(
    private val mapView: MapView,
) {

    private var following: Boolean = true

    private val touchSlopPx: Int = ViewConfiguration.get(mapView.context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f

    init {
        // Returns false so the map still handles every touch itself.
        mapView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                }
                // A second finger: pinch-zoom or a two-finger drag.
                MotionEvent.ACTION_POINTER_DOWN -> following = false
                MotionEvent.ACTION_MOVE ->
                    if (hypot(event.x - downX, event.y - downY) > touchSlopPx) following = false
            }
            false
        }
    }

    /** True when the camera is auto-panning to each new current-position fix. */
    val isFollowing: Boolean get() = following

    /** Switch follow-mode on or off. Turning it on panned to the last fix. */
    fun followCurrent(enabled: Boolean) {
        following = enabled
    }

    /**
     * Smoothly re-centre on [point]. When follow mode is on this is
     * called on every tracking emission; when it's off, nothing
     * happens. Programmatic moves never turn following off (see the
     * class docs).
     */
    fun followCurrentPosition(point: GeoPoint) {
        if (!following) return
        mapView.controller.animateTo(point)
    }

    /**
     * Fit [points] to the viewport with [padFraction] breathing room.
     * Posted to the view so it runs *after* the first layout pass;
     * calling `zoomToBoundingBox` before layout leaves the map at the
     * wrong zoom.
     */
    fun fitRouteToView(
        points: List<TrackPoint>,
        padFraction: Double = 0.1,
        animated: Boolean = false,
    ) {
        val bbox = RouteGeometry.boundingBox(points, padFraction) ?: return
        val paddingPx = (FIT_SCREEN_PADDING_DP * mapView.resources.displayMetrics.density).toInt()
        mapView.post {
            mapView.zoomToBoundingBox(bbox.toOsm(), animated, paddingPx)
        }
    }

    private fun BoundingBoxDegrees.toOsm(): BoundingBox =
        BoundingBox(north, east, south, west)

    private companion object {
        /**
         * Breathing room around a fitted route, in dp — keeps the ends of
         * the route clear of the pills and buttons drawn over the map.
         */
        const val FIT_SCREEN_PADDING_DP = 56
    }
}
