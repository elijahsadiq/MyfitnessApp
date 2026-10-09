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
import com.elijah.fitness.databinding.FragmentLoginBinding
import com.elijah.fitness.ui.base.BaseFragment

/**
 * Login screen — real form (Step 7 replaces the Step-5 placeholder).
 *
 * Client-side validation runs in [LoginViewModel] via [AuthValidators].
 * Server-side field errors (422) are mapped onto the matching
 * `TextInputLayout`; network failures show a Snackbar with a Retry
 * action. The primary button is disabled + shows an inline spinner
 * while a request is in flight.
 */
class LoginFragment : BaseFragment<FragmentLoginBinding>() {

    private val viewModel: LoginViewModel by viewModels {
        LoginViewModel.factory((requireContext().applicationContext as FitnessApp).container)
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentLoginBinding =
        FragmentLoginBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Phase 8c step 2 — edge-to-edge. Push the headline below the
        // (now transparent) status bar; AuthActivity's host already
        // handles the bottom inset.
        applySystemBarInsets(target = binding.root, applyBottom = false)

        binding.emailInput.doAfterTextChanged { viewModel.onEmailChanged() }
        binding.passwordInput.doAfterTextChanged { viewModel.onPasswordChanged() }

        binding.loginButton.setOnClickListener { submit() }
        binding.passwordInput.setOnEditorActionListener { _, _, _ ->
            submit(); true
        }

        binding.goToRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        collectOnStart(viewModel.uiState) { render(it) }
        collectOnStart(viewModel.events) { event ->
            when (event) {
                LoginViewModel.Event.LoggedIn ->
                    (requireActivity() as? AuthActivity)?.goToMain()
                is LoginViewModel.Event.ShowNetworkError ->
                    showRetrySnackbar(event.messageRes)
            }
        }
    }

    private fun submit() {
        viewModel.submit(
            email = binding.emailInput.text?.toString().orEmpty(),
            password = binding.passwordInput.text?.toString().orEmpty(),
        )
    }

    private fun render(state: LoginViewModel.UiState) {
        binding.loginProgress.isVisible = state.submitting
        binding.loginButton.isEnabled = !state.submitting
        binding.loginButton.text =
            if (state.submitting) "" else getString(R.string.auth_login_submit)
        binding.goToRegister.isEnabled = !state.submitting

        binding.emailFieldLayout.error = errorText(state.emailError)
        binding.passwordFieldLayout.error = errorText(state.passwordError)
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
