package com.elijah.fitness.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.databinding.FragmentRegisterBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.util.UnitSystem

/**
 * Register screen. Same state-machine shape as [LoginFragment] with a
 * full-name field, a metric/imperial unit toggle, required body-weight
 * and height fields (both feed BMI on the Home screen), and a
 * terms-checkbox row. Successful registration bounces to Main via
 * [AuthActivity.goToMain].
 */
class RegisterFragment : BaseFragment<FragmentRegisterBinding>() {

    private val viewModel: RegisterViewModel by viewModels {
        RegisterViewModel.factory((requireContext().applicationContext as FitnessApp).container)
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentRegisterBinding =
        FragmentRegisterBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Phase 8c step 2 — edge-to-edge.
        applySystemBarInsets(target = binding.root, applyBottom = false)

        // Default toggle position matches the initial ViewModel state.
        binding.unitSystemToggle.check(R.id.unitMetric)

        binding.fullNameInput.doAfterTextChanged { viewModel.onFullNameChanged() }
        binding.emailInput.doAfterTextChanged { viewModel.onEmailChanged() }
        binding.passwordInput.doAfterTextChanged { viewModel.onPasswordChanged() }
        binding.weightInput.doAfterTextChanged { viewModel.onWeightChanged() }
        binding.heightInput.doAfterTextChanged { viewModel.onHeightChanged() }
        binding.termsCheckbox.setOnCheckedChangeListener { _, _ -> viewModel.onTermsToggled() }

        binding.unitSystemToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val next = if (checkedId == R.id.unitImperial) UnitSystem.IMPERIAL else UnitSystem.METRIC
            viewModel.onUnitSystemChanged(next)
        }

        binding.registerButton.setOnClickListener { submit() }
        binding.heightInput.setOnEditorActionListener { _, _, _ ->
            submit(); true
        }

        binding.backButton.setOnClickListener { goToLogin() }
        binding.goToLogin.setOnClickListener { goToLogin() }

        collectOnStart(viewModel.uiState) { render(it) }
        collectOnStart(viewModel.events) { event ->
            when (event) {
                RegisterViewModel.Event.RegistrationComplete ->
                    (requireActivity() as? AuthActivity)?.goToMain()
                is RegisterViewModel.Event.ShowNetworkError ->
                    showRetrySnackbar(event.messageRes)
            }
        }
    }

    /**
     * Back arrow and "Log in" link. From Log in → Register we just go
     * back. Reached from onboarding, Register is the only screen on the
     * stack (popping it would leave a blank window), so Log in replaces
     * it instead — never a Register ↔ Log in loop on the back stack.
     */
    private fun goToLogin() {
        val nav = findNavController()
        if (nav.previousBackStackEntry?.destination?.id == R.id.loginFragment) {
            nav.navigateUp()
        } else {
            nav.navigate(
                R.id.loginFragment, null,
                navOptions { popUpTo(R.id.registerFragment) { inclusive = true } },
            )
        }
    }

    private fun submit() {
        viewModel.submit(
            fullName = binding.fullNameInput.text?.toString().orEmpty(),
            email = binding.emailInput.text?.toString().orEmpty(),
            password = binding.passwordInput.text?.toString().orEmpty(),
            weightRaw = binding.weightInput.text?.toString().orEmpty(),
            heightRaw = binding.heightInput.text?.toString().orEmpty(),
            termsAccepted = binding.termsCheckbox.isChecked,
        )
    }

    private fun render(state: RegisterViewModel.UiState) {
        binding.registerProgress.isVisible = state.submitting
        binding.registerButton.isEnabled = !state.submitting
        binding.registerButton.text =
            if (state.submitting) "" else getString(R.string.auth_register_submit)
        listOf(
            binding.fullNameInput, binding.emailInput, binding.passwordInput,
            binding.weightInput, binding.heightInput, binding.termsCheckbox,
            binding.unitMetric, binding.unitImperial,
            binding.backButton, binding.goToLogin,
        ).forEach { it.isEnabled = !state.submitting }

        binding.fullNameFieldLayout.error = errorText(state.fullNameError)
        binding.emailFieldLayout.error = errorText(state.emailError)
        binding.passwordFieldLayout.error = errorText(state.passwordError)
        binding.weightFieldLayout.error = errorText(state.weightError)
        binding.heightFieldLayout.error = errorText(state.heightError)

        // Suffix labels track the current unit system.
        val weightSuffix = if (state.unitSystem == UnitSystem.IMPERIAL) {
            R.string.auth_unit_suffix_lb
        } else R.string.auth_unit_suffix_kg
        val heightSuffix = if (state.unitSystem == UnitSystem.IMPERIAL) {
            R.string.auth_unit_suffix_in
        } else R.string.auth_unit_suffix_cm
        binding.weightFieldLayout.suffixText = getString(weightSuffix)
        binding.heightFieldLayout.suffixText = getString(heightSuffix)

        val termsMessage = errorText(state.termsError)
        binding.termsError.text = termsMessage
        binding.termsError.isVisible = termsMessage != null
    }

    private fun errorText(fieldError: FieldError?): CharSequence? = when (fieldError) {
        null -> null
        is FieldError.Res -> getString(fieldError.messageRes)
        is FieldError.Text -> fieldError.message
    }

    private fun showRetrySnackbar(messageRes: Int) {
        snackbar(messageRes)
            .setAction(R.string.auth_retry) { submit() }
            .show()
    }
}
