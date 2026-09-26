package com.elijah.fitness.ui.goals

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import com.elijah.fitness.R
import com.elijah.fitness.databinding.BottomSheetGoalActionsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Edit / Delete action sheet shown on a goal tap. Delete asks for
 * confirmation via a small AlertDialog before firing.
 *
 * Communicates back via `FragmentResult` so the parent fragment stays
 * ignorant of this sheet's internals — just listens on
 * [FRAGMENT_RESULT_KEY] and reads the [ACTION_KEY] extra.
 */
class GoalActionsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetGoalActionsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetGoalActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val goalId = requireArguments().getString(ARG_GOAL_ID)
            ?: error("Missing goal id")

        binding.actionEdit.setOnClickListener {
            setFragmentResult(FRAGMENT_RESULT_KEY, Bundle().apply {
                putString(ACTION_KEY, ACTION_EDIT)
                putString(GOAL_ID_KEY, goalId)
            })
            dismiss()
        }

        binding.actionDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.goals_delete_confirm_title)
                .setMessage(R.string.goals_delete_confirm_body)
                .setPositiveButton(R.string.goals_delete_confirm_ok) { _, _ ->
                    setFragmentResult(FRAGMENT_RESULT_KEY, Bundle().apply {
                        putString(ACTION_KEY, ACTION_DELETE)
                        putString(GOAL_ID_KEY, goalId)
                    })
                    dismiss()
                }
                .setNegativeButton(R.string.goals_delete_confirm_cancel, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val FRAGMENT_RESULT_KEY = "goal_action_result"
        const val ACTION_KEY = "action"
        const val GOAL_ID_KEY = "goal_id"
        const val ACTION_EDIT = "edit"
        const val ACTION_DELETE = "delete"

        private const val ARG_GOAL_ID = "goal_id"

        fun newInstance(goalId: String): GoalActionsBottomSheet =
            GoalActionsBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_GOAL_ID, goalId) }
            }
    }
}
