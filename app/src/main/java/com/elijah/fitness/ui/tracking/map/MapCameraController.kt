package com.elijah.fitness.ui.tracking.map

import android.view.MotionEvent
import com.elijah.fitness.tracking.BoundingBoxDegrees
import com.elijah.fitness.tracking.RouteGeometry
import com.elijah.fitness.tracking.TrackPoint
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Camera / follow-mode controller for a [MapView] (phase 8c step 3).
 *
 * On the **finished-workout screen** the only call is
 * [fitRouteToView] once the fragment has routed its data in; the
 * controller stays in no-follow mode.
 *
 * On the **tracking screen** the controller follows the current
 * position until the user drags the map, then turns following off.
 * The re-centre button on the overlay calls [followCurrent] with
 * `true` to opt back in and immediately pans to the last-known
 * fix.
 *
 * ### Why we need our own controller
 *
 * osmdroid's `MapView` doesn't expose "follow the current location"
 * as a built-in — it only has `controller.animateTo(point)`. We
 * either call that on every state emission (and never let the user
 * pan freely) or we respect the user's gesture. This class takes
 * the second path: a `MapListener` watches for scroll + zoom
 * events, and a touch listener distinguishes a user gesture from a
 * programmatic pan. Only user gestures turn follow off.
 */
class MapCameraController(
    private val mapView: MapView,
) {

    private var following: Boolean = true
    private var lastPanWasProgrammatic: Boolean = false

    init {
        // Detect user-initiated scrolls / zooms. A programmatic pan
        // from `followCurrentPosition` also fires `onScroll`, so we
        // mark those with [lastPanWasProgrammatic] and ignore them.
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                if (!lastPanWasProgrammatic) following = false
                lastPanWasProgrammatic = false
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean = false
        })

        // Any ACTION_DOWN is unambiguously a user touch — safer than
        // relying on the MapListener alone, which also fires for
        // flings continuing after the finger lifts.
        mapView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                following = false
            }
            false // let the map process the event
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
     * happens. The internal flag stops a programmatic pan from
     * incorrectly turning follow off.
     */
    fun followCurrentPosition(point: GeoPoint) {
        if (!following) return
        lastPanWasProgrammatic = true
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
            lastPanWasProgrammatic = true
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
