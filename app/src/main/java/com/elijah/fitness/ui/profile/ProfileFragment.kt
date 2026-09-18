package com.elijah.fitness.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.databinding.FragmentProfileBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.launcher.LauncherActivity
import kotlinx.coroutines.launch

/**
 * Profile screen — reached from the Home avatar tap.
 *
 * Shows the current body weight (read-only in phase 2; editing lands in
 * a later phase). The Logout button clears the local token via
 * `AuthRepository.logout()` and restarts the task through
 * [LauncherActivity], which now routes back to `AuthActivity` because
 * the token store is empty.
 */
class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentProfileBinding =
        FragmentProfileBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val container = (requireContext().applicationContext as FitnessApp).container

        // Body weight (still driven by the local UserSettings; a phase-2
        // register/login populates it via the API, but the app treats the
        // in-memory copy as the source of truth for the calorie calculator).
        collectOnStart(container.userSettings.bodyWeightKg) { kg ->
            binding.bodyWeightValue.text = getString(R.string.profile_body_weight_value, kg)
        }

        // The name / email fields are populated from the seeded user
        // profile in phase 2. Phase 5 will wire GET /me on entry.
        binding.profileName.text = getString(R.string.profile_default_name)
        binding.profileEmail.text = getString(R.string.profile_default_email)

        binding.logoutButton.setOnClickListener {
            binding.logoutButton.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                container.authRepository.logout()
                startActivity(
                    Intent(requireContext(), LauncherActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                requireActivity().finish()
            }
        }
    }
}
