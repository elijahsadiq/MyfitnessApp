package com.elijah.fitness.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.load
import com.elijah.fitness.BuildConfig
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.remote.dto.DeleteAccountRequestDto
import com.elijah.fitness.data.remote.dto.UserDto
import com.elijah.fitness.databinding.DialogDeleteAccountBinding
import com.elijah.fitness.databinding.DialogEditProfileFieldBinding
import com.elijah.fitness.databinding.FragmentProfileBinding
import com.elijah.fitness.notifications.AppNotifications
import com.elijah.fitness.notifications.ReminderSettings
import com.elijah.fitness.ui.auth.AuthActivity
import com.elijah.fitness.ui.base.BaseFragment
import com.elijah.fitness.ui.base.SnackKind
import com.elijah.fitness.ui.launcher.LauncherActivity
import com.elijah.fitness.util.AvatarImage
import com.elijah.fitness.util.BmiCategory
import com.elijah.fitness.util.BodyMetrics
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** What to turn on once the notification permission answer comes back. */
    private var afterNotificationPermission: (() -> Unit)? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = afterNotificationPermission
        afterNotificationPermission = null
        if (granted) action?.invoke() else onNotificationsBlocked()
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentProfileBinding =
        FragmentProfileBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Phase 8c step 2 — edge-to-edge. The shared toolbar handles the
        // top inset itself; this clears the gesture bar at the bottom.
        applyBottomNavInset(binding.root)

        bindTileLabels()

        binding.avatarContainer.setOnClickListener { launchPhotoPicker() }
        binding.removePhotoButton.setOnClickListener { viewModel.deleteAvatar() }
        binding.nameRow.setOnClickListener { showEditNameDialog() }
        binding.weightRow.setOnClickListener { showEditWeightDialog() }
        binding.heightRow.setOnClickListener { showEditHeightDialog() }
        binding.logoutButton.setOnClickListener { confirmLogOut() }
        binding.reminderTimeRow.setOnClickListener { showReminderTimePicker() }
        binding.deleteAccountButton.setOnClickListener { showDeleteAccountDialog() }

        collectOnStart(viewModel.user) { it?.let(::renderUser) }
        collectOnStart(viewModel.totals) { renderTotals(it) }
        collectOnStart(viewModel.isAvatarUploading) { uploading ->
            binding.avatarProgress.isVisible = uploading
        }
        collectOnStart(viewModel.events) { handleEvent(it) }
        collectOnStart(viewModel.notificationSettings) { renderNotificationSettings(it) }
    }

    // ---------- phase 10: notifications ----------

    private fun renderNotificationSettings(settings: ReminderSettings) {
        binding.reminderTimeValue.text = settings.reminderTime.format(reminderTimeFormatter())
        // Set the switches without firing their listeners, then re-attach.
        binding.reminderSwitch.setOnCheckedChangeListener(null)
        binding.goalAlertsSwitch.setOnCheckedChangeListener(null)
        binding.reminderSwitch.isChecked = settings.dailyReminder
        binding.goalAlertsSwitch.isChecked = settings.goalAlerts && AppNotifications.canPost(requireContext())
        binding.reminderSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) withNotificationPermission { viewModel.setDailyReminder(true) }
            else viewModel.setDailyReminder(false)
        }
        binding.goalAlertsSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) withNotificationPermission { viewModel.setGoalAlerts(true) }
            else viewModel.setGoalAlerts(false)
        }
    }

    /**
     * Run [action] once notifications are allowed. Android 13+ asks the
     * user first; older versions only need the app's notifications on.
     */
    private fun withNotificationPermission(action: () -> Unit) {
        val ctx = requireContext()
        if (AppNotifications.canPost(ctx)) {
            action()
            return
        }
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsRuntimePermission) {
            afterNotificationPermission = action
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onNotificationsBlocked()
        }
    }

    /** Permission refused or notifications blocked: undo the switch and point to Settings. */
    private fun onNotificationsBlocked() {
        renderNotificationSettings(viewModel.notificationSettings.value)
        snackbar(R.string.profile_notifications_denied, Snackbar.LENGTH_LONG)
            .setAction(R.string.profile_notifications_settings) {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                )
            }
            .show()
    }

    private fun showReminderTimePicker() {
        val current = viewModel.notificationSettings.value.reminderTime
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(
                if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H
                else TimeFormat.CLOCK_12H
            )
            .setHour(current.hour)
            .setMinute(current.minute)
            .setTitleText(R.string.profile_reminder_time_picker_title)
            .build()
        picker.addOnPositiveButtonClickListener {
            viewModel.setReminderTime(LocalTime.of(picker.hour, picker.minute))
        }
        picker.show(parentFragmentManager, "reminder-time-picker")
    }

    private fun reminderTimeFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(requireContext())) "HH:mm" else "h:mm a",
        Locale.getDefault(),
    )

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
                snack(R.string.profile_update_saved_snackbar, SnackKind.SUCCESS)
            is ProfileViewModel.Event.SaveFailed ->
                snack(R.string.profile_update_failed_snackbar, SnackKind.ERROR)
            ProfileViewModel.Event.AvatarUpdated ->
                snack(R.string.profile_avatar_updated, SnackKind.SUCCESS)
            ProfileViewModel.Event.AvatarRemoved ->
                snack(R.string.profile_avatar_removed, SnackKind.SUCCESS)
            ProfileViewModel.Event.AvatarFailed ->
                snack(R.string.profile_avatar_failed, SnackKind.ERROR)
            ProfileViewModel.Event.Network ->
                snack(R.string.profile_network_snackbar, SnackKind.ERROR)
            is ProfileViewModel.Event.DeleteFailed ->
                snackbar(
                    getString(R.string.profile_delete_account_failed, event.message),
                    kind = SnackKind.ERROR,
                ).show()
            ProfileViewModel.Event.AccountDeleted -> {
                val ctx = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    (ctx as FitnessApp).container.signOutLocally(ctx)
                    goToAuth(accountDeleted = true)
                }
            }
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
        val current = viewModel.user.value?.weightKg?.let(NUMBER_FORMAT::format).orEmpty()
        showEditDialog(
            titleRes = R.string.profile_edit_weight_title,
            initial = current,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            validate = { candidate ->
                val v = parseDecimal(candidate)
                if (v == null || v < 20.0 || v > 300.0) {
                    getString(R.string.profile_weight_error_range)
                } else null
            },
            onConfirm = { candidate ->
                val existing = viewModel.user.value ?: return@showEditDialog
                viewModel.updateProfile(
                    fullName = existing.fullName,
                    weightKg = parseDecimal(candidate),
                    heightCm = null,
                )
            }
        )
    }

    private fun showEditHeightDialog() {
        val current = viewModel.user.value?.heightCm?.let(NUMBER_FORMAT::format).orEmpty()
        showEditDialog(
            titleRes = R.string.profile_edit_height_title,
            initial = current,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            validate = { candidate ->
                val v = parseDecimal(candidate)
                if (v == null || v < 80.0 || v > 260.0) {
                    getString(R.string.profile_height_error_range)
                } else null
            },
            onConfirm = { candidate ->
                val existing = viewModel.user.value ?: return@showEditDialog
                viewModel.updateProfile(
                    fullName = existing.fullName,
                    weightKg = null,
                    heightCm = parseDecimal(candidate),
                )
            }
        )
    }

    /** Accepts "72.5" and "72,5" — keyboards in many locales type a comma. */
    private fun parseDecimal(raw: String): Double? =
        raw.trim().replace(',', '.').toDoubleOrNull()

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

    /**
     * Same card as the goal delete dialog (trash badge, Cancel + red
     * pill). "Delete forever" stays disabled until the user types the
     * confirm word — no password needed.
     */
    private fun showDeleteAccountDialog() {
        val dialogBinding = DialogDeleteAccountBinding.inflate(layoutInflater)
        val confirmWord = DeleteAccountRequestDto.CONFIRM_WORD
        dialogBinding.confirmLayout.hint =
            getString(R.string.profile_delete_account_confirm_hint, confirmWord)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()
        // 28dp white card on a transparent window, like DeleteGoalConfirmDialog.
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogBinding.root.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(ContextCompat.getColor(requireContext(), R.color.surface_white))
            cornerRadius = resources.getDimension(R.dimen.radius_bottom_sheet_top)
        }

        val confirm = dialogBinding.deleteAccountConfirm
        fun typedWordMatches(): Boolean =
            dialogBinding.confirmInput.text?.toString()?.trim()
                .equals(confirmWord, ignoreCase = true)
        fun syncConfirm() {
            val ready = typedWordMatches()
            confirm.isEnabled = ready
            confirm.alpha = if (ready) 1f else DISABLED_ALPHA
        }
        syncConfirm()
        dialogBinding.confirmInput.doAfterTextChanged { syncConfirm() }

        dialogBinding.deleteAccountCancel.setOnClickListener { dialog.dismiss() }
        confirm.setOnClickListener {
            if (!typedWordMatches()) return@setOnClickListener
            confirm.isEnabled = false
            viewModel.deleteAccount()
            dialog.dismiss()
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
        val resolver = requireContext().contentResolver
        binding.avatarProgress.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            // Shrink + convert to JPEG off the main thread: camera photos
            // are bigger than the server's 2 MB limit, and HEIC (Samsung's
            // default) isn't accepted at all.
            val jpeg = withContext(Dispatchers.Default) { AvatarImage.prepareJpeg(resolver, uri) }
            if (jpeg != null && jpeg.isNotEmpty()) {
                viewModel.uploadAvatar(jpeg, "image/jpeg")
            } else {
                binding.avatarProgress.isVisible = false
                snack(R.string.profile_avatar_failed, SnackKind.ERROR)
            }
        }
    }

    // -----------------------------------------------------------------
    // Log out + nav helpers
    // -----------------------------------------------------------------

    /**
     * Phase 10: workouts saved offline only live on this phone until they
     * sync. Logging out clears the phone, so warn first if any are left.
     */
    private fun confirmLogOut() {
        viewLifecycleOwner.lifecycleScope.launch {
            val unsynced = viewModel.unsyncedWorkoutCount()
            if (unsynced == 0) {
                logOut()
                return@launch
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_logout_unsynced_title)
                .setMessage(resources.getQuantityString(R.plurals.profile_logout_unsynced_body, unsynced, unsynced))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.profile_logout_unsynced_confirm) { _, _ -> logOut() }
                .show()
        }
    }

    private fun logOut() {
        binding.logoutButton.isEnabled = false
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            // Clears the token AND every cached workout / goal, and stops
            // a GPS session, so the next account on this phone starts clean.
            (ctx as FitnessApp).container.signOut(ctx)
            goToAuth()
        }
    }

    private fun goToAuth(accountDeleted: Boolean = false) {
        startActivity(
            Intent(requireContext(), LauncherActivity::class.java)
                .putExtra(AuthActivity.EXTRA_ACCOUNT_DELETED, accountDeleted)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        requireActivity().finish()
    }

    private fun snack(messageRes: Int, kind: SnackKind) {
        snackbar(messageRes, Snackbar.LENGTH_SHORT, kind = kind).show()
    }

    companion object {
        private val memberSinceFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

        /** "82" or "82.5" — no trailing ".0". */
        private val NUMBER_FORMAT = java.text.DecimalFormat("0.#")

        /** "Delete forever" until the confirm word is typed. */
        private const val DISABLED_ALPHA = 0.4f
    }
}
