package com.elijah.fitness.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.remote.ApiResult
import com.elijah.fitness.databinding.FragmentLoginBinding
import com.elijah.fitness.ui.base.BaseFragment
import kotlinx.coroutines.launch

/**
 * Placeholder login screen. Step 7 replaces it with the real form (fields,
 * validation, autofill, error mapping). Until then, "Continue as Alex"
 * exercises the whole auth pipeline (Retrofit → API → TokenStore →
 * AuthActivity.goToMain) so we can smoke-test the plumbing end-to-end.
 */
class LoginFragment : BaseFragment<FragmentLoginBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentLoginBinding =
        FragmentLoginBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.continueAsAlex.setOnClickListener { attemptSignIn() }
        binding.goToRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }
    }

    private fun attemptSignIn() {
        val container = (requireContext().applicationContext as FitnessApp).container
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = container.authRepository.login(
                email = "alex@example.com",
                password = "Password123",
            )
            setLoading(false)
            when (result) {
                is ApiResult.Success -> (requireActivity() as? AuthActivity)?.goToMain()
                is ApiResult.NetworkError ->
                    showError(getString(R.string.auth_error_network))
                is ApiResult.Error ->
                    showError(result.message.ifBlank { getString(R.string.auth_error_generic) })
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.loginProgress.isVisible = loading
        binding.continueAsAlex.isEnabled = !loading
        binding.goToRegister.isEnabled = !loading
    }
}
