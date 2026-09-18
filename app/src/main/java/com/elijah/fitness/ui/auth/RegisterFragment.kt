package com.elijah.fitness.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.R
import com.elijah.fitness.databinding.FragmentRegisterBinding
import com.elijah.fitness.ui.base.BaseFragment

/**
 * Placeholder register screen. Real form (fullName, email, password,
 * body weight, terms, validation) lands in Step 7. Until then, the
 * "Back to login" link keeps the nav graph exercisable.
 */
class RegisterFragment : BaseFragment<FragmentRegisterBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentRegisterBinding =
        FragmentRegisterBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.goToLogin.setOnClickListener {
            findNavController().navigate(R.id.action_register_to_login)
        }
    }
}
