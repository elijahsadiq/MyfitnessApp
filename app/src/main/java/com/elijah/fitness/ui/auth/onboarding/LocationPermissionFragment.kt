package com.elijah.fitness.ui.auth.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.R
import com.elijah.fitness.databinding.FragmentLocationPermissionBinding
import com.elijah.fitness.tracking.LocationPermissions
import com.elijah.fitness.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar

/**
 * Priming screen for the location permission.
 *
 * Shown once as part of onboarding. Tapping "Allow location" fires the real
 * runtime request through [LocationPermissions]; whatever the user chooses,
 * registration continues — manual logging works without GPS, so denial
 * must never block the flow.
 *
 * The outcome is only informational here; the actual gate for live tracking
 * lives at the point where the user starts a run or ride (phase 8a step 5).
 */
class LocationPermissionFragment : BaseFragment<FragmentLocationPermissionBinding>() {

    private val permissionLauncher =
        LocationPermissions.registerLocationLauncher(this) { outcome ->
            handleOutcome(outcome)
        }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentLocationPermissionBinding =
        FragmentLocationPermissionBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Edge-to-edge: keep the content below the status bar (the host
        // already pads for the navigation bar).
        applySystemBarInsets(binding.root, applyBottom = false)

        binding.bulletList.findViewById<View>(R.id.bullet1)
            .findViewById<TextView>(R.id.bulletText)
            .setText(R.string.auth_location_bullet_1)
        binding.bulletList.findViewById<View>(R.id.bullet2)
            .findViewById<TextView>(R.id.bulletText)
            .setText(R.string.auth_location_bullet_2)
        binding.bulletList.findViewById<View>(R.id.bullet3)
            .findViewById<TextView>(R.id.bulletText)
            .setText(R.string.auth_location_bullet_3)

        binding.allowLocationButton.setOnClickListener { onAllowTapped() }
        binding.notNowButton.setOnClickListener { continueToRegister() }
    }

    private fun onAllowTapped() {
        // Already granted? Skip the dialog and move on.
        if (LocationPermissions.isFineGranted(requireContext()) ||
            LocationPermissions.isCoarseGranted(requireContext())
        ) {
            continueToRegister()
            return
        }
        permissionLauncher.launch(LocationPermissions.LOCATION_PERMISSIONS)
    }

    private fun handleOutcome(outcome: LocationPermissions.Outcome) {
        when (outcome) {
            LocationPermissions.Outcome.FineGranted,
            LocationPermissions.Outcome.CoarseOnly -> continueToRegister()
            LocationPermissions.Outcome.Denied -> {
                // After a refusal, shouldShowRationale returns `false` when
                // the system flipped the "don't ask again" bit. Offer the
                // settings deep-link in that case; otherwise just continue.
                val canAskAgain = LocationPermissions.shouldShowRationale(this)
                if (canAskAgain) {
                    snackbar(R.string.auth_location_denied_snackbar, Snackbar.LENGTH_SHORT).show()
                } else {
                    snackbar(R.string.auth_location_permanent_denied_snackbar)
                        .setAction(R.string.auth_location_open_settings) { openAppSettings() }
                        .show()
                }
                // Either way, don't block onboarding — manual logging works.
                binding.allowLocationButton.postDelayed(::continueToRegister, 1500L)
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", requireContext().packageName, null),
        )
        startActivity(intent)
    }

    private fun continueToRegister() {
        if (isAdded && findNavController().currentDestination?.id == R.id.locationPermissionFragment) {
            findNavController().navigate(R.id.action_location_to_register)
        }
    }
}
