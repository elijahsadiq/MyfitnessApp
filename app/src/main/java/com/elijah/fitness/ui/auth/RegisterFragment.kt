package com.elijah.fitness.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.databinding.FragmentRegisterBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar

/**
 * Register screen. Same state-machine shape as [LoginFragment] with an
 * extra full-name, weight, and terms-checkbox row. Successful
 * registration bounces to Main via [AuthActivity.goToMain].
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

        binding.fullNameInput.doAfterTextChanged { viewModel.onFullNameChanged() }
        binding.emailInput.doAfterTextChanged { viewModel.onEmailChanged() }
        binding.passwordInput.doAfterTextChanged { viewModel.onPasswordChanged() }
        binding.weightInput.doAfterTextChanged { viewModel.onWeightChanged() }
        binding.termsCheckbox.setOnCheckedChangeListener { _, _ -> viewModel.onTermsToggled() }

        binding.registerButton.setOnClickListener { submit() }
        binding.weightInput.setOnEditorActionListener { _, _, _ ->
            submit(); true
        }

        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.goToLogin.setOnClickListener {
            findNavController().navigate(R.id.action_register_to_login)
        }

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

    private fun submit() {
        viewModel.submit(
            fullName = binding.fullNameInput.text?.toString().orEmpty(),
            email = binding.emailInput.text?.toString().orEmpty(),
            password = binding.passwordInput.text?.toString().orEmpty(),
            weightRaw = binding.weightInput.text?.toString().orEmpty(),
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
            binding.weightInput, binding.termsCheckbox,
            binding.backButton, binding.goToLogin,
        ).forEach { it.isEnabled = !state.submitting }

        binding.fullNameFieldLayout.error = errorText(state.fullNameError)
        binding.emailFieldLayout.error = errorText(state.emailError)
        binding.passwordFieldLayout.error = errorText(state.passwordError)
        binding.weightFieldLayout.error = errorText(state.weightError)

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
        Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_LONG)
            .setAction(R.string.auth_retry) { submit() }
            .show()
    }
}
