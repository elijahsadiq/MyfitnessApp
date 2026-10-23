package com.elijah.fitness.ui.goals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.elijah.fitness.FitnessApp
import com.elijah.fitness.R
import com.elijah.fitness.data.model.GoalType
import com.elijah.fitness.databinding.BottomSheetNewGoalBinding
import com.elijah.fitness.ui.auth.FieldError
import com.elijah.fitness.ui.base.SnackKind
import com.elijah.fitness.ui.base.withKind
import com.elijah.fitness.ui.components.ActivityCardSpec
import com.elijah.fitness.ui.components.SheetScrollGuard
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * New goal / Edit goal sheet (phase 8d, NewGoal.dc.html): a one-hand,
 * nearly full-height bottom sheet that replaced the full-screen New
 * goal screen. Opened from the Goals FAB, the empty state's "Create a
 * goal", the Quick Start rows and "Try again" (pre-filled), and Manage
 * → Edit (`goal_id`).
 *
 * Swipeable activity cards, "Measure by", two steppers (target, days)
 * and a live summary — sentence, Per week / Per day, end date and the
 * estimated burn — above them. Everything shown is worked out in
 * [NewGoalViewModel]; this class only forwards input and draws state.
 *
 * Stays a `BottomSheetDialogFragment` (not a `BaseFragment`): Android's
 * dialog sheet has to extend Material's class. It is a `<dialog>`
 * destination in the nav graph, so Back and swipe-down both close it.
 *
 * ### Size
 * Fully expanded, never half-open (`skipCollapsed`), as tall as the
 * screen less 40dp under the status bar, so the dimmed page peeks above
 * it ([fitSheetToScreen]). There is no close button on purpose: the
 * sheet is used one-handed, and dragging it down cancels. A drag
 * anywhere closes it only when let go low or flung, like the header
 * ([SheetScrollGuard]); a short nudge springs back.
 *
 * ### Saving
 * While a save is in flight the button reads "Saving…" and the sheet
 * can't be dragged, swiped or backed out of: closing it would cancel
 * the request without saying whether the goal was saved, and creating
 * it again could make a duplicate.
 *
 * ### Result
 * A successful save posts [FRAGMENT_RESULT_KEY] / [RESULT_SAVED_KEY];
 * `GoalsFragment` turns it into the "Goal saved" snackbar. An edited
 * goal that can't be found posts [RESULT_UNAVAILABLE_KEY] instead.
 */
class NewGoalBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetNewGoalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewGoalViewModel by viewModels {
        NewGoalViewModel.factory(
            container = (requireContext().applicationContext as FitnessApp).container,
            editingGoalId = arguments?.getString(ARG_GOAL_ID),
            prefill = prefillFrom(arguments),
        )
    }

    /** The carousel is built once, from the first loaded state. */
    private var carouselReady = false

    /** The "Measure by" segments on screen, so they're only rebuilt when they change. */
    private var shownMetrics: List<GoalType> = emptyList()

    /** Keeps the sheet's height in step with the window (see [fitSheetToScreen]). */
    private var sheetSizer: View.OnLayoutChangeListener? = null
    private var sizedParent: View? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_Fitness_BottomSheetDialog_NewGoal

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetNewGoalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        carouselReady = false
        shownMetrics = emptyList()

        // Fully open, and a drag down closes it (no half-way stop).
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            isDraggable = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        // A short drag on the body springs back instead of closing.
        SheetScrollGuard.install(binding.newGoalScroll, (dialog as? BottomSheetDialog)?.behavior)

        ViewCompat.setAccessibilityHeading(binding.newGoalTitle, true)
        // TalkBack reads the summary card as one block, named "Goal
        // summary" before its live text (the design's aria-label).
        ViewCompat.setScreenReaderFocusable(binding.newGoalSummary, true)
        ViewCompat.setAccessibilityDelegate(
            binding.newGoalSummary,
            LabelledBlock(getString(R.string.new_goal_summary_label)),
        )
        binding.newGoalDots.count = NewGoalViewModel.ActivityOption.entries.size

        binding.newGoalMeasureBy.contentDescription = getString(R.string.new_goal_measure_by)
        binding.newGoalMeasureBy.setOnSegmentSelectedListener { index ->
            viewModel.uiState.value.metrics.getOrNull(index)?.let(viewModel::setMetric)
        }
        binding.newGoalTargetStepper.setOnStepListener { direction -> viewModel.stepTarget(direction) }
        binding.newGoalDaysStepper.setOnStepListener { direction -> viewModel.stepDays(direction) }
        binding.newGoalSubmit.setOnClickListener { viewModel.submit() }

        // Blank for the frame or two an edited goal takes to load, so the
        // sheet never shows Running before jumping to, say, Swimming.
        binding.newGoalContent.isInvisible = true

        collectOnStart(viewModel.uiState) { render(it) }
        collectOnStart(viewModel.events) { event ->
            when (event) {
                NewGoalViewModel.Event.Saved -> onSaved()
                is NewGoalViewModel.Event.ShowError -> showError(event.error)
                NewGoalViewModel.Event.GoalUnavailable -> onGoalUnavailable()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        fitSheetToScreen()
        // TalkBack announces the sheet by name when it opens.
        dialog?.setTitle(viewModel.uiState.value.title)
    }

    override fun onDestroyView() {
        removeSheetSizer()
        super.onDestroyView()
        _binding = null
    }

    // ---------- rendering ----------

    private fun render(state: NewGoalUiState) {
        val b = _binding ?: return
        if (!state.loaded) {
            b.newGoalContent.isInvisible = true
            return
        }

        if (!carouselReady) {
            carouselReady = true
            b.newGoalCarousel.setUp(
                spec = ActivityCardSpec.GOAL_SHEET,
                items = NewGoalViewModel.ActivityOption.entries.map(NewGoalPlan::cardFor),
                selected = state.activityIndex,
            ) { position, _ ->
                NewGoalViewModel.ActivityOption.entries.getOrNull(position)?.let(viewModel::setActivity)
            }
            b.newGoalDots.setSelectedIndex(state.activityIndex, animate = false)
            b.newGoalContent.isInvisible = false
        } else {
            // The carousel drives the selection; the dots only follow it.
            b.newGoalDots.setSelectedIndex(state.activityIndex)
        }

        setTextIfChanged(b.newGoalTitle, state.title)
        setTextIfChanged(b.newGoalSubmit, state.submitLabel)
        b.newGoalSubmit.isEnabled = !state.submitting
        lockWhileSaving(state.submitting)

        // Summary (a polite live region: only touch what changed). The
        // sentence keeps its last two words together on screen, like goal
        // titles on the Goals list ("… in" / "30 days", never a lone "days").
        setTextIfChanged(b.newGoalSentence, GoalDisplay.titleForDisplay(state.sentence))
        setTextIfChanged(b.newGoalPerLabel, state.perLabel)
        setTextIfChanged(b.newGoalPerValue, state.perValue)
        setTextIfChanged(b.newGoalEndsValue, state.endsText)
        setTextIfChanged(b.newGoalKcalValue, state.kcalText)
        b.newGoalWeightHint.isVisible = state.showWeightHint

        // Measure by: rebuild the segments only when the activity offers other ones.
        if (state.metrics != shownMetrics) {
            shownMetrics = state.metrics
            b.newGoalMeasureBy.setOptions(
                labels = state.metrics.map { getString(NewGoalPlan.metricLabelRes(it)) },
                selected = state.metricIndex.coerceAtLeast(0),
            )
        } else {
            b.newGoalMeasureBy.setSelectedIndex(state.metricIndex)
        }

        // Adjust card.
        setTextIfChanged(b.newGoalTargetLabel, state.targetLabel)
        setTextIfChanged(b.newGoalTargetHint, state.targetHint)
        b.newGoalTargetStepper.valueText = state.targetValueText
        b.newGoalTargetStepper.setStepEnabled(state.canDecreaseTarget, state.canIncreaseTarget)
        setTextIfChanged(b.newGoalDaysHint, state.daysHint)
        b.newGoalDaysStepper.valueText = state.daysValueText
        b.newGoalDaysStepper.setStepEnabled(state.canDecreaseDays, state.canIncreaseDays)
    }

    /** Same text again would make TalkBack re-read the live summary. */
    private fun setTextIfChanged(view: TextView, value: CharSequence) {
        if (view.text.toString() != value.toString()) view.text = value
    }

    /**
     * No drag, swipe, Back or outside tap closes the sheet while a save is
     * in flight (closing would cancel the request); all work again after.
     */
    private fun lockWhileSaving(saving: Boolean) {
        if (isCancelable == !saving) return
        isCancelable = !saving
        // setCancelable also sets the sheet's hideable flag; keep it
        // hideable afterwards so a drag down can close it again.
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            isDraggable = !saving
            if (!saving) isHideable = true
        }
    }

    // ---------- results ----------

    private fun onSaved() {
        setFragmentResult(FRAGMENT_RESULT_KEY, bundleOf(RESULT_SAVED_KEY to true))
        close()
    }

    /** The goal to edit couldn't be found: close, and Goals says so. */
    private fun onGoalUnavailable() {
        setFragmentResult(FRAGMENT_RESULT_KEY, bundleOf(RESULT_UNAVAILABLE_KEY to true))
        close()
    }

    private fun close() {
        isCancelable = true
        val popped = runCatching {
            findNavController().popBackStack(R.id.newGoalFragment, true)
        }.getOrDefault(false)
        if (!popped) dismissAllowingStateLoss()
    }

    private fun showError(error: FieldError) {
        val b = _binding ?: return
        val message = when (error) {
            is FieldError.Res -> getString(error.messageRes)
            is FieldError.Text -> error.message
        }
        Snackbar.make(b.root, message, Snackbar.LENGTH_LONG)
            .withKind(SnackKind.ERROR)
            .setAnchorView(b.newGoalSubmit)
            .show()
    }

    // ---------- sheet size ----------

    /**
     * Makes the sheet as tall as the window less the status bar and
     * 40dp, so that strip of the dimmed page stays visible above it (the
     * design's 40px). Re-applied whenever the window's size changes.
     * The content inside fills the sheet: the cards take the free space.
     */
    private fun fitSheetToScreen() {
        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        val parent = sheet.parent as? View ?: return
        if (sizedParent !== parent) {
            removeSheetSizer()
            val listener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                applySheetHeight(v, sheet)
            }
            parent.addOnLayoutChangeListener(listener)
            sheetSizer = listener
            sizedParent = parent
        }
        applySheetHeight(parent, sheet)
    }

    private fun applySheetHeight(parent: View, sheet: View) {
        val parentHeight = parent.height
        if (parentHeight <= 0) return // the first layout calls back
        val topInset = ViewCompat.getRootWindowInsets(parent)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            ?.top ?: 0
        val gap = resources.getDimensionPixelSize(R.dimen.new_goal_sheet_top_gap)
        // Never less than half the window (split screen, tiny windows).
        val height = max(parentHeight / 2, parentHeight - topInset - gap)
        if (sheet.layoutParams?.height != height) {
            sheet.updateLayoutParams { this.height = height }
        }
    }

    private fun removeSheetSizer() {
        val listener = sheetSizer
        if (listener != null) sizedParent?.removeOnLayoutChangeListener(listener)
        sheetSizer = null
        sizedParent = null
    }

    /**
     * Makes TalkBack read a screen-reader-focusable block as "[label],
     * then the text inside it", worked out afresh each time it's read so
     * the live text stays current. (A fixed content description would
     * replace that text.)
     */
    private class LabelledBlock(private val label: CharSequence) : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            val parts = mutableListOf(label)
            collectText(host, parts)
            info.contentDescription = parts.joinToString(separator = ", ")
        }

        private fun collectText(view: View, into: MutableList<CharSequence>) {
            if (view.visibility != View.VISIBLE) return
            if (view is TextView) {
                view.text?.takeIf { it.isNotBlank() }?.let(into::add)
            } else if (view is ViewGroup) {
                for (i in 0 until view.childCount) collectText(view.getChildAt(i), into)
            }
        }
    }

    /** Lifecycle-safe flow collector — bottom sheets don't extend `BaseFragment`. */
    private fun <T> collectOnStart(flow: Flow<T>, block: suspend (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collect(block)
            }
        }
    }

    companion object {
        const val FRAGMENT_RESULT_KEY = "new_goal_result"
        const val RESULT_SAVED_KEY = "saved"

        /** Set instead of [RESULT_SAVED_KEY] when the goal to edit couldn't be found. */
        const val RESULT_UNAVAILABLE_KEY = "unavailable"

        internal const val ARG_GOAL_ID = "goal_id"
        internal const val ARG_PREFILL_ACTIVITY = "prefill_activity"
        internal const val ARG_PREFILL_METRIC = "prefill_metric"
        internal const val ARG_PREFILL_TARGET = "prefill_target"
        internal const val ARG_PREFILL_DAYS = "prefill_days"

        /**
         * The sheet's arguments. Edit: pass [goalId]. Create: pass the four
         * pre-fill fields together (or none). Edit always wins.
         */
        fun argsFor(
            goalId: String? = null,
            prefillActivity: NewGoalViewModel.ActivityOption? = null,
            prefillMetric: GoalType? = null,
            prefillTarget: Double? = null,
            prefillDays: Int? = null,
        ): Bundle = Bundle().apply {
            goalId?.let { putString(ARG_GOAL_ID, it) }
            prefillActivity?.let { putString(ARG_PREFILL_ACTIVITY, it.name) }
            prefillMetric?.let { putString(ARG_PREFILL_METRIC, it.name) }
            prefillTarget?.let { putDouble(ARG_PREFILL_TARGET, it) }
            prefillDays?.let { putInt(ARG_PREFILL_DAYS, it) }
        }

        /**
         * The pre-fill in [args], or `null` when there is none or any part
         * is unusable (an unknown name means "no prefill", never a crash).
         */
        internal fun prefillFrom(args: Bundle?): NewGoalViewModel.Prefill? {
            val a = args ?: return null
            val activity = a.getString(ARG_PREFILL_ACTIVITY)?.let {
                runCatching { NewGoalViewModel.ActivityOption.valueOf(it) }.getOrNull()
            } ?: return null
            val metric = a.getString(ARG_PREFILL_METRIC)?.let {
                runCatching { GoalType.valueOf(it) }.getOrNull()
            } ?: return null
            val target = a.getDouble(ARG_PREFILL_TARGET, -1.0).takeIf { it.isFinite() && it > 0.0 }
                ?: return null
            val days = a.getInt(ARG_PREFILL_DAYS, -1).takeIf { it > 0 } ?: return null
            return NewGoalViewModel.Prefill(activity, metric, target, days)
        }
    }
}
