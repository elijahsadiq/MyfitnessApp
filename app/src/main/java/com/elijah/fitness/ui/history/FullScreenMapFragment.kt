package com.elijah.fitness.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.data.model.WorkoutDetail
import com.elijah.fitness.databinding.FragmentFullScreenMapBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.tracking.map.DarkMapStyler
import com.elijah.fitness.ui.tracking.map.MapCameraController
import com.elijah.fitness.ui.tracking.map.RoutePolylineRenderer

/**
 * Full-screen interactive map of a saved route (phase 8c step 7).
 * Reuses the Step-3 map component — same dark tiles, same glow, same
 * attribution — with pan + zoom enabled and a close button.
 *
 * Shares the saved workout's `clientUuid` with the detail screen; the
 * same `WorkoutDetailViewModel` loads the route points.
 */
class FullScreenMapFragment : BaseFragment<FragmentFullScreenMapBinding>() {

    private val clientUuid: String by lazy {
        arguments?.getString(ARG_CLIENT_UUID)
            ?: error("FullScreenMapFragment missing clientUuid")
    }

    private val viewModel: WorkoutDetailViewModel by viewModels {
        WorkoutDetailViewModel.factory(
            (requireContext().applicationContext as FitnessApp).container,
            clientUuid,
        )
    }

    private var routeRenderer: RoutePolylineRenderer? = null
    private var mapCamera: MapCameraController? = null

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentFullScreenMapBinding =
        FragmentFullScreenMapBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Edge-to-edge: push the close button below the status bar.
        val closeTop = binding.fullMapCloseButton.marginTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.fullMapCloseButton) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateMargin(top = closeTop + bars.top)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(binding.fullMapCloseButton)

        DarkMapStyler.apply(binding.mapView, requireContext())
        binding.mapView.controller.setZoom(14.0)
        mapCamera = MapCameraController(binding.mapView)
        routeRenderer = RoutePolylineRenderer(binding.mapView)

        binding.fullMapCloseButton.setOnClickListener { findNavController().popBackStack() }

        collectOnStart(viewModel.state) { state ->
            if (state is WorkoutDetailViewModel.State.Loaded) renderRoute(state.detail)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        binding.mapView.onDetach()
        routeRenderer = null
        mapCamera = null
        super.onDestroyView()
    }

    private fun renderRoute(detail: WorkoutDetail) {
        if (detail.routePoints.isEmpty()) return
        routeRenderer?.render(detail.routePoints)
        mapCamera?.fitRouteToView(detail.routePoints, padFraction = 0.15)
    }

    private val View.marginTop: Int
        get() = (layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0

    private fun View.updateMargin(top: Int) {
        (layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.topMargin = top
            layoutParams = it
        }
    }

    companion object {
        const val ARG_CLIENT_UUID = "clientUuid"
    }
}
