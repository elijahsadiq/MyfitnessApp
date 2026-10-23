package com.elijah.fitness.ui.celebration

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.DialogFragment
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.GoalCelebration
import com.elijah.fitness.data.model.GoalCompletionStats.AchievedLabel
import com.elijah.fitness.databinding.DialogGoalCompleteBinding
import com.elijah.fitness.util.DurationFormatter
import com.elijah.fitness.util.Formatters
import kotlin.math.roundToLong

/**
 * The goal-complete pop-up (GoalComplete.dc.html, phase 8d Step 7):
 * a full-screen transparent window whose background is the 62 % scrim,
 * confetti above the scrim, and a white card with the trophy, the goal,
 * three stat tiles and the "Set a new goal" / "Share" / "Done" actions.
 *
 * `MainActivity` shows one at a time from
 * [com.elijah.fitness.notifications.GoalCelebrationManager.pending].
 * Everything it shows travels in its arguments, so a rotation restores
 * the same pop-up (without replaying the confetti or the haptic) and
 * never shows it twice.
 *
 * Kotlin note: a `DialogFragment` is a Fragment that owns a Dialog
 * window, so the system re-creates it after a rotation by itself.
 */
class GoalCompleteDialogFragment : DialogFragment() {

    private var _binding: DialogGoalCompleteBinding? = null
    private val binding get() = _binding!!

    private val goalId: String?
        get() = arguments?.getString(ARG_GOAL_ID)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AppCompatDialog(requireContext(), R.style.Theme_Fitness_GoalCompleteDialog).apply {
            setCanceledOnTouchOutside(false)
            // Window title: what TalkBack announces when the pop-up opens.
            setTitle(R.string.goal_complete_title)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogGoalCompleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val celebration = celebrationFrom(arguments)
        if (celebration == null) {
            dismissAllowingStateLoss()
            return
        }
        bind(celebration)
        applyInsets()

        binding.goalCompleteNewGoal.setOnClickListener {
            val host = activity as? GoalCelebrationHost
            dismissAllowingStateLoss()
            host?.openNewGoalFromCelebration()
        }
        binding.goalCompleteShare.setOnClickListener { share(celebration) }
        binding.goalCompleteDone.setOnClickListener { dismissAllowingStateLoss() }

        ViewCompat.setAccessibilityHeading(binding.goalCompleteTitle, true)
        // Also set in code: the XML app:tint needs AppCompat inflation.
        ImageViewCompat.setImageTintList(
            binding.goalCompleteTrophy,
            ColorStateList.valueOf(ContextCompat.getColor(view.context, R.color.text_on_lime)),
        )

        if (savedInstanceState == null) {
            binding.goalCompleteConfetti.play()
            binding.root.post { _binding?.root?.performHapticFeedback(hapticConstant()) }
        }
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // Edge to edge: the scrim and the confetti also cover the system bars.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // A rotation also dismisses the dialog; the restored copy is the
        // same pop-up, so it stays at the head of the queue.
        val host = activity
        if (host?.isChangingConfigurations == true) return
        val id = goalId
        val app = context?.applicationContext as? FitnessApp
        if (id != null) app?.container?.goalCelebrationManager?.consume(id)
        (host as? GoalCelebrationHost)?.onCelebrationClosed()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bind(c: GoalCelebration) {
        val body = if (c.daysEarly > 0) {
            resources.getQuantityString(R.plurals.goal_complete_body_early, c.daysEarly, c.title, c.daysEarly)
        } else {
            getString(R.string.goal_complete_body_last_day, c.title)
        }
        binding.goalCompleteBody.text = body

        binding.goalCompleteAchievedValue.text = achievedValueText(c)
        binding.goalCompleteAchievedLabel.text = achievedLabelText(c)

        val days = c.daysTaken.coerceAtLeast(1)
        binding.goalCompleteDaysValue.text = Formatters.formatGoalAmount(days.toDouble())
        binding.goalCompleteDaysLabel.text =
            resources.getQuantityString(R.plurals.goal_complete_days_taken, days)

        binding.goalCompleteKcalValue.text = Formatters.formatKcalValue(c.kcal)
    }

    private fun achievedValueText(c: GoalCelebration): String = when (c.label) {
        AchievedLabel.TIME_ACTIVE ->
            DurationFormatter.format(requireContext(), c.achievedValue.safeRound())
        else -> Formatters.formatGoalAmount(c.achievedValue)
    }

    private fun achievedLabelText(c: GoalCelebration): String {
        val count = c.achievedValue.safeRound().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return when (c.label) {
            AchievedLabel.KM_RUN -> getString(R.string.goal_complete_km_run)
            AchievedLabel.KM_RIDDEN -> getString(R.string.goal_complete_km_ridden)
            AchievedLabel.KM_SWUM -> getString(R.string.goal_complete_km_swum)
            AchievedLabel.KM_ROWED -> getString(R.string.goal_complete_km_rowed)
            AchievedLabel.RUNS -> resources.getQuantityString(R.plurals.goal_complete_runs, count)
            AchievedLabel.RIDES -> resources.getQuantityString(R.plurals.goal_complete_rides, count)
            AchievedLabel.SWIMS -> resources.getQuantityString(R.plurals.goal_complete_swims, count)
            AchievedLabel.ROWS -> resources.getQuantityString(R.plurals.goal_complete_rows, count)
            AchievedLabel.WORKOUTS -> resources.getQuantityString(R.plurals.goal_complete_workouts, count)
            AchievedLabel.SESSIONS -> resources.getQuantityString(R.plurals.goal_complete_sessions, count)
            AchievedLabel.TIME_ACTIVE -> getString(R.string.goal_complete_time_active)
            AchievedLabel.KG_LIFTED -> getString(R.string.goal_complete_kg_lifted)
        }
    }

    private fun share(c: GoalCelebration) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.goal_complete_share_text, c.title))
        try {
            startActivity(Intent.createChooser(send, getString(R.string.goal_complete_share_chooser)))
        } catch (_: ActivityNotFoundException) {
            // Nothing can share text on this device; the pop-up stays open.
        }
    }

    /** Keep the card clear of the status / navigation bars on short screens. */
    private fun applyInsets() {
        val scroll = binding.goalCompleteScroll
        val baseTop = scroll.paddingTop
        val baseBottom = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(top = baseTop + bars.top, bottom = baseBottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(scroll)
    }

    private fun hapticConstant(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }

    companion object {
        const val TAG = "goal_complete_dialog"

        private const val ARG_GOAL_ID = "goal_id"
        private const val ARG_TITLE = "title"
        private const val ARG_LABEL = "label"
        private const val ARG_VALUE = "achieved_value"
        private const val ARG_DAYS_TAKEN = "days_taken"
        private const val ARG_DAYS_EARLY = "days_early"
        private const val ARG_KCAL = "kcal"

        fun newInstance(celebration: GoalCelebration): GoalCompleteDialogFragment =
            GoalCompleteDialogFragment().apply {
                arguments = bundleOf(
                    ARG_GOAL_ID to celebration.goalId,
                    ARG_TITLE to celebration.title,
                    ARG_LABEL to celebration.label.name,
                    ARG_VALUE to celebration.achievedValue,
                    ARG_DAYS_TAKEN to celebration.daysTaken,
                    ARG_DAYS_EARLY to celebration.daysEarly,
                    ARG_KCAL to celebration.kcal,
                )
            }

        private fun celebrationFrom(args: Bundle?): GoalCelebration? {
            if (args == null) return null
            val id = args.getString(ARG_GOAL_ID) ?: return null
            val label = args.getString(ARG_LABEL)
                ?.let { name -> AchievedLabel.entries.firstOrNull { it.name == name } }
                ?: return null
            return GoalCelebration(
                goalId = id,
                title = args.getString(ARG_TITLE).orEmpty(),
                label = label,
                achievedValue = args.getDouble(ARG_VALUE, 0.0),
                daysTaken = args.getInt(ARG_DAYS_TAKEN, 1),
                daysEarly = args.getInt(ARG_DAYS_EARLY, 0),
                kcal = args.getDouble(ARG_KCAL, 0.0),
            )
        }

        private fun Double.safeRound(): Long =
            if (isFinite()) coerceAtLeast(0.0).roundToLong() else 0L
    }
}

/** Implemented by the activity that hosts the goal-complete pop-up. */
interface GoalCelebrationHost {
    /** "Set a new goal": open the New goal sheet from wherever the user is. */
    fun openNewGoalFromCelebration()

    /** The pop-up closed for good (not a rotation); show the next one, if any. */
    fun onCelebrationClosed()
}
