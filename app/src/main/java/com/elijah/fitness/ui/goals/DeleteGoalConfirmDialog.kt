package com.elijah.fitness.ui.goals

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.elijah.fitness.R
import com.elijah.fitness.data.model.ActivityType
import com.elijah.fitness.data.remote.mapper.activityTypeOrNull
import com.elijah.fitness.databinding.DialogDeleteGoalBinding

/**
 * "Delete this goal?" confirmation dialog (phase 8b step 7). Matches
 * `DeleteConfirm.dc.html` — trash icon on a pale red circle, 28dp-radius
 * white card, Cancel + destructive Delete pill buttons.
 *
 * Fires an [ACTION_KEY] of `confirm` back to the parent via
 * `setFragmentResult`; the parent calls `GoalsViewModel.deleteWithUndo`
 * to run the Snackbar + 5-second delayed DELETE.
 */
class DeleteGoalConfirmDialog : DialogFragment() {

    private var _binding: DialogDeleteGoalBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        _binding = DialogDeleteGoalBinding.inflate(inflater, null, false)

        val goalId = requireArguments().getString(ARG_GOAL_ID)
            ?: error("Missing goal id")
        val title = requireArguments().getString(ARG_TITLE).orEmpty()
        val activityName = requireArguments().getString(ARG_ACTIVITY_TYPE)
        // An unknown value reads as "workouts" rather than crashing.
        val activity = activityTypeOrNull(activityName)

        binding.deleteGoalBody.text = getString(
            R.string.goals_delete_confirm_body,
            title,
            activityWord(activity),
        )

        val onCancel = View.OnClickListener { dismiss() }
        binding.deleteGoalCancel.setOnClickListener(onCancel)

        binding.deleteGoalConfirm.setOnClickListener {
            setFragmentResult(FRAGMENT_RESULT_KEY, Bundle().apply {
                putString(ACTION_KEY, ACTION_CONFIRM)
                putString(GOAL_ID_KEY, goalId)
            })
            dismiss()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()

        // 28dp rounded corners on the card and a transparent window so the
        // scrim beneath still shows through.
        dialog.window?.let { window ->
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                cornerRadius = resources.displayMetrics.density * 28
            }
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            binding.root.background = bg
        }
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun activityWord(type: ActivityType?): String = getString(
        when (type) {
            ActivityType.RUNNING -> R.string.goals_delete_activity_word_runs
            ActivityType.CYCLING -> R.string.goals_delete_activity_word_rides
            ActivityType.SWIMMING -> R.string.goals_delete_activity_word_swims
            ActivityType.ROWING -> R.string.goals_delete_activity_word_rows
            ActivityType.WEIGHTLIFTING -> R.string.goals_delete_activity_word_workouts
            null -> R.string.goals_delete_activity_word_sessions
        }
    )

    companion object {
        const val FRAGMENT_RESULT_KEY = "delete_goal_result"
        const val ACTION_KEY = "action"
        const val GOAL_ID_KEY = "goal_id"
        const val ACTION_CONFIRM = "confirm"

        private const val ARG_GOAL_ID = "goal_id"
        private const val ARG_TITLE = "title"
        private const val ARG_ACTIVITY_TYPE = "activity_type"

        fun newInstance(
            goalId: String,
            title: String,
            activityType: ActivityType?,
        ): DeleteGoalConfirmDialog = DeleteGoalConfirmDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_GOAL_ID, goalId)
                putString(ARG_TITLE, title)
                activityType?.let { putString(ARG_ACTIVITY_TYPE, it.name) }
            }
        }
    }
}

