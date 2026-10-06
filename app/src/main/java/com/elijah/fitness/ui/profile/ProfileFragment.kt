package com.elijah.fitness.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.load
import com.elijah.fitness.BuildConfig
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.remote.dto.UserDto
import com.elijah.fitness.databinding.DialogDeleteAccountBinding
import com.elijah.fitness.databinding.DialogEditProfileFieldBinding
import com.elijah.fitness.databinding.FragmentProfileBinding
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.launcher.LauncherActivity
import com.elijah.fitness.util.BmiCategory
import com.elijah.fitness.util.BodyMetrics
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Profile screen — phase 8 step 4 rebuild. Binds every field to GET /me
 * (amendment B: no more baked-in `profile_default_name` / `_email`
 * strings), supports editing name / weight / height, avatar
 * upload+remove, BMI display, and password-gated account deletion.
 */
class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

    private val viewModel: ProfileViewModel by viewModels {
        ProfileViewModel.factory(
            (requireContext().applicationContext as FitnessApp).container
        )
    }

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let(::onPhotoPicked)
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentProfileBinding =
        FragmentProfileBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindTileLabels()

        binding.avatarContainer.setOnClickListener { launchPhotoPicker() }
        binding.removePhotoButton.setOnClickListener { viewModel.deleteAvatar() }
        binding.nameRow.setOnClickListener { showEditNameDialog() }
        binding.weightRow.setOnClickListener { showEditWeightDialog() }
        binding.heightRow.setOnClickListener { showEditHeightDialog() }
        binding.logoutButton.setOnClickListener { logOut() }
        binding.deleteAccountButton.setOnClickListener { showDeleteAccountDialog() }

        collectOnStart(viewModel.user) { it?.let(::renderUser) }
        collectOnStart(viewModel.totals) { renderTotals(it) }
        collectOnStart(viewModel.isAvatarUploading) { uploading ->
            binding.avatarProgress.isVisible = uploading
        }
        collectOnStart(viewModel.events) { handleEvent(it) }
    }

    private fun bindTileLabels() {
        binding.tileWorkouts.tileLabel.setText(R.string.profile_tile_workouts_label)
        binding.tileDistance.tileLabel.setText(R.string.profile_tile_distance_label)
        binding.tileMemberSince.tileLabel.setText(R.string.profile_tile_member_since_label)
    }

    private fun renderUser(user: UserDto) {
        binding.profileName.text = user.fullName
        binding.profileEmail.text = user.email
        binding.nameValue.text = user.fullName
        binding.weightValue.text = user.weightKg?.let {
            getString(R.string.profile_weight_value, it)
        } ?: "—"
        binding.heightValue.text = user.heightCm?.let {
            getString(R.string.profile_height_value, it)
        } ?: "—"

        renderAvatar(user.avatarPath)
        renderBmi(user)
        renderMemberSince(user.createdAt)
    }

    private fun renderAvatar(relativePath: String?) {
        if (relativePath.isNullOrBlank()) {
            binding.avatarImage.setImageDrawable(null)
            binding.avatarPlaceholder.isVisible = true
            binding.removePhotoButton.isVisible = false
            return
        }
        binding.avatarPlaceholder.isVisible = false
        binding.removePhotoButton.isVisible = true
        val base = BuildConfig.BASE_URL.trimEnd('/')
        val url = "$base/${relativePath.trimStart('/')}"
        binding.avatarImage.load(url) {
            crossfade(true)
            // No disk cache key hassles — the random filename on the
            // server guarantees a fresh URL after every upload.
        }
    }

    private fun renderBmi(user: UserDto) {
        val bmi = BodyMetrics.bmi(weightKg = user.weightKg, heightCm = user.heightCm)
        if (bmi == null) {
            binding.bmiValue.setText(R.string.profile_bmi_no_data)
            binding.bmiCategory.setText(R.string.profile_bmi_missing)
            return
        }
        binding.bmiValue.text = String.format(Locale.getDefault(), "%.1f", bmi)
        val labelRes = when (BodyMetrics.bmiCategory(bmi)) {
            BmiCategory.UNDERWEIGHT -> R.string.profile_bmi_category_underweight
            BmiCategory.NORMAL -> R.string.profile_bmi_category_normal
            BmiCategory.OVERWEIGHT -> R.string.profile_bmi_category_overweight
            BmiCategory.OBESE -> R.string.profile_bmi_category_obese
            null -> R.string.profile_bmi_missing
        }
        binding.bmiCategory.setText(labelRes)
    }

    private fun renderMemberSince(createdAt: String) {
        val instant = runCatching { Instant.parse(createdAt) }.getOrNull() ?: return
        val text = memberSinceFormatter.format(instant.atZone(ZoneId.systemDefault()))
        binding.tileMemberSince.tileValue.text = text
    }

    private fun renderTotals(totals: ProfileViewModel.Totals) {
        binding.tileWorkouts.tileValue.text = totals.workouts.toString()
        binding.tileDistance.tileValue.text = getString(
            R.string.profile_tile_distance_value, totals.distanceMeters / 1000.0,
        )
    }

    private fun handleEvent(event: ProfileViewModel.Event) {
        when (event) {
            ProfileViewModel.Event.Saved ->
                snack(R.string.profile_update_saved_snackbar)
            is ProfileViewModel.Event.SaveFailed ->
                snack(R.string.profile_update_failed_snackbar)
            ProfileViewModel.Event.AvatarUpdated ->
                snack(R.string.profile_avatar_updated)
            ProfileViewModel.Event.AvatarRemoved ->
                snack(R.string.profile_avatar_removed)
            ProfileViewModel.Event.AvatarFailed ->
                snack(R.string.profile_avatar_failed)
            ProfileViewModel.Event.Network ->
                snack(R.string.profile_network_snackbar)
            is ProfileViewModel.Event.DeleteFailed -> {
                Snackbar.make(
                    binding.root,
                    getString(R.string.profile_delete_account_failed, event.message),
                    Snackbar.LENGTH_LONG,
                ).show()
            }
            ProfileViewModel.Event.AccountDeleted -> goToAuth()
        }
    }

    // -----------------------------------------------------------------
    // Edit dialogs
    // -----------------------------------------------------------------

    private fun showEditNameDialog() {
        val current = viewModel.user.value?.fullName.orEmpty()
        showEditDialog(
            titleRes = R.string.profile_edit_name_title,
            initial = current,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS,
            validate = { candidate ->
                val trimmed = candidate.trim()
                when {
                    trimmed.length < 2 -> getString(R.string.profile_name_error_short)
                    trimmed.length > 60 -> getString(R.string.profile_name_error_long)
                    else -> null
                }
            },
            onConfirm = { candidate ->
                viewModel.updateProfile(
                    fullName = candidate.trim(),
                    weightKg = null,
                    heightCm = null,
                )
            }
        )
    }

    private fun showEditWeightDialog() {
        val current = viewModel.user.value?.weightKg?.toString() ?: ""
        showEditDialog(
            titleRes = R.string.profile_edit_weight_title,
            initial = current,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            validate = { candidate ->
                val v = candidate.toDoubleOrNull()
                if (v == null || v < 20.0 || v > 300.0) {
                    getString(R.string.profile_weight_error_range)
                } else null
            },
            onConfirm = { candidate ->
                val existing = viewModel.user.value ?: return@showEditDialog
                viewModel.updateProfile(
                    fullName = existing.fullName,
                    weightKg = candidate.toDouble(),
                    heightCm = null,
                )
            }
        )
    }

    private fun showEditHeightDialog() {
        val current = viewModel.user.value?.heightCm?.toString() ?: ""
        showEditDialog(
            titleRes = R.string.profile_edit_height_title,
            initial = current,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            validate = { candidate ->
                val v = candidate.toDoubleOrNull()
                if (v == null || v < 80.0 || v > 260.0) {
                    getString(R.string.profile_height_error_range)
                } else null
            },
            onConfirm = { candidate ->
                val existing = viewModel.user.value ?: return@showEditDialog
                viewModel.updateProfile(
                    fullName = existing.fullName,
                    weightKg = null,
                    heightCm = candidate.toDouble(),
                )
            }
        )
    }

    private fun showEditDialog(
        titleRes: Int,
        initial: String,
        inputType: Int,
        validate: (String) -> String?,
        onConfirm: (String) -> Unit,
    ) {
        val binding = DialogEditProfileFieldBinding.inflate(layoutInflater)
        binding.fieldInput.inputType = inputType
        binding.fieldInput.setText(initial)
        binding.fieldInput.setSelection(initial.length)
        binding.fieldInput.doAfterTextChanged { editable ->
            binding.fieldLayout.error = validate(editable?.toString().orEmpty())
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setView(binding.root)
            .setPositiveButton(R.string.profile_edit_save, null)
            .setNegativeButton(R.string.profile_edit_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = binding.fieldInput.text?.toString().orEmpty()
                val error = validate(value)
                if (error != null) {
                    binding.fieldLayout.error = error
                    return@setOnClickListener
                }
                onConfirm(value)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // -----------------------------------------------------------------
    // Delete-account dialog
    // -----------------------------------------------------------------

    private fun showDeleteAccountDialog() {
        val binding = DialogDeleteAccountBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_delete_account_title)
            .setView(binding.root)
            .setPositiveButton(R.string.profile_delete_account_confirm, null)
            .setNegativeButton(R.string.profile_edit_cancel, null)
            .create()
        dialog.setOnShowListener {
            val positive = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            positive.isEnabled = false
            binding.passwordInput.doAfterTextChanged { editable ->
                positive.isEnabled = (editable?.length ?: 0) > 0
            }
            positive.setOnClickListener {
                positive.isEnabled = false
                viewModel.deleteAccount(
                    binding.passwordInput.text?.toString().orEmpty()
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // -----------------------------------------------------------------
    // Avatar picker
    // -----------------------------------------------------------------

    private fun launchPhotoPicker() {
        photoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun onPhotoPicked(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val resolver = requireContext().contentResolver
            val bytes = try {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: Exception) {
                null
            }
            val mime = resolver.getType(uri) ?: "image/jpeg"
            if (bytes != null && bytes.isNotEmpty()) {
                viewModel.uploadAvatar(bytes, mime)
            } else {
                snack(R.string.profile_avatar_failed)
            }
        }
    }

    // -----------------------------------------------------------------
    // Log out + nav helpers
    // -----------------------------------------------------------------

    private fun logOut() {
        binding.logoutButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val container = (requireContext().applicationContext as FitnessApp).container
            container.authRepository.logout()
            goToAuth()
        }
    }

    private fun goToAuth() {
        startActivity(
            Intent(requireContext(), LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        requireActivity().finish()
    }

    private fun snack(messageRes: Int) {
        Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_SHORT).show()
    }

    companion object {
        private val memberSinceFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM yyyy")
    }
}
