package com.elijah.fitness.ui.auth.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.R
import com.elijah.fitness.databinding.FragmentLocationPermissionBinding
import com.elijah.fitness.ui.base.BaseFragment

/**
 * Priming-only location permission screen.
 *
 * Explains why the app wants location and what the two buttons do — but
 * **does not actually request the OS permission**. GPS lands in phase 3;
 * for now, both "Allow location" and "Not now" continue to the register
 * screen and leave a TODO for the real permission call.
 */
class LocationPermissionFragment : BaseFragment<FragmentLocationPermissionBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentLocationPermissionBinding =
        FragmentLocationPermissionBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bullet text — the three included item_location_bullet views.
        binding.bulletList.findViewById<View>(R.id.bullet1)
            .findViewById<TextView>(R.id.bulletText)
            .setText(R.string.auth_location_bullet_1)
        binding.bulletList.findViewById<View>(R.id.bullet2)
            .findViewById<TextView>(R.id.bulletText)
            .setText(R.string.auth_location_bullet_2)
        binding.bulletList.findViewById<View>(R.id.bullet3)
            .findViewById<TextView>(R.id.bulletText)
            .setText(R.string.auth_location_bullet_3)

        binding.allowLocationButton.setOnClickListener {
            // TODO(phase 3): call the ActivityResultLauncher for
            //   Manifest.permission.ACCESS_FINE_LOCATION before continuing.
            continueToRegister()
        }
        binding.notNowButton.setOnClickListener { continueToRegister() }
    }

    override fun onResume() {
        super.onResume()
        setLightStatusBarIcons(false)
    }

    override fun onPause() {
        super.onPause()
        setLightStatusBarIcons(true)
    }

    private fun setLightStatusBarIcons(lightIcons: Boolean) {
        val window = requireActivity().window
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = lightIcons
    }

    private fun continueToRegister() {
        findNavController().navigate(R.id.action_location_to_register)
    }
}
