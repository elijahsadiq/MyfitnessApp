package com.elijah.fitness.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import com.elijah.fitness.ui.main.SnackbarHost
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Shared base class for every fragment in the app (except the bottom sheet,
 * which has to extend `BottomSheetDialogFragment`).
 *
 * ### What this class removes from every subclass
 * - **ViewBinding boilerplate**: subclasses only need to implement
 *   [inflateBinding]. The base class handles inflate-in-`onCreateView` and
 *   null-out-in-`onDestroyView` so binding leaks are impossible.
 * - **Error UI**: [showError] / [snackbar] wrap `Snackbar` construction
 *   and lift it above the bottom navigation.
 * - **Edge-to-edge insets**: [applyInsets] and its two shorthands.
 * - **Empty-state toggling**: [showEmptyState] swaps two views without
 *   subclasses having to remember which visibility flags to touch.
 * - **Lifecycle-safe flow collection**: [collectOnStart] wraps the standard
 *   `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) }`
 *   incantation so subclasses don't have to.
 *
 * ### Inheritance example #2 (Task 5b)
 * Every phase-1 fragment (`DashboardFragment`, `WorkoutHistoryFragment`,
 * `GoalsFragment`, `ProfileFragment`, plus the two log fragments in Step 7)
 * extends this class — one file's worth of shared behaviour reused six
 * times.
 */
abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null

    /**
     * The view binding. Safe to read between `onCreateView` and
     * `onDestroyView`; throws otherwise so mistakes are loud.
     */
    protected val binding: VB
        get() = _binding
            ?: error("Binding accessed outside onCreateView..onDestroyView")

    /** The binding, or `null` once the view is gone — for delayed callbacks. */
    protected val bindingOrNull: VB?
        get() = _binding

    /** Subclasses inflate their specific binding here. */
    protected abstract fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): VB

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = inflateBinding(inflater, container)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** Show an error message in a Snackbar (above the bottom nav when shown). */
    protected fun showError(message: CharSequence) {
        if (_binding == null) return
        snackbar(message).show()
    }

    /**
     * Build a Snackbar for this screen. It floats just above [anchor] —
     * by default this screen's FAB ([snackbarAnchorView]) or else the
     * bottom navigation — instead of covering it. Pass a pinned Save
     * button as [anchor] on form screens.
     */
    protected fun snackbar(
        message: CharSequence,
        duration: Int = Snackbar.LENGTH_LONG,
        anchor: View? = null,
    ): Snackbar {
        val snack = Snackbar.make(binding.root, message, duration)
        val anchorView = anchor
            ?: snackbarAnchorView()?.takeIf { it.isShown }
            ?: (activity as? SnackbarHost)?.snackbarAnchor()
        if (anchorView != null) snack.anchorView = anchorView
        return snack
    }

    /** A FAB or bottom bar the screen's Snackbars should sit above. */
    protected open fun snackbarAnchorView(): View? = null

    protected fun snackbar(
        @StringRes messageRes: Int,
        duration: Int = Snackbar.LENGTH_LONG,
        anchor: View? = null,
    ): Snackbar = snackbar(getString(messageRes), duration, anchor)

    /**
     * Toggle between an empty-state view and the primary content view.
     *
     * @param isEmpty when true, [emptyView] is shown and [contentView] is
     *   hidden; when false, the roles are reversed.
     */
    protected fun showEmptyState(
        emptyView: View,
        contentView: View,
        isEmpty: Boolean
    ) {
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        contentView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /**
     * Collect [flow] while the view is at least in the `STARTED` state.
     * Cancels automatically when the view goes to `STOPPED`, so it's safe
     * against the classic Fragment memory-leak footgun.
     */
    protected fun <T> collectOnStart(
        flow: Flow<T>,
        block: suspend (T) -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collect { block(it) }
            }
        }
    }

    /**
     * Pad [target] by the system-bar insets on the chosen edges, on top
     * of the padding the layout already declares.
     *
     * ### Never consumes the insets
     * The listener returns the insets untouched, so child views with
     * their own listener (a pinned Save bar, a list) still receive them.
     * On Android 8–10 a consumed dispatch also stops at the first
     * sibling — which is how the New goal button and list paddings used
     * to silently miss their bottom inset.
     *
     * ### Idempotent
     * The XML padding is captured once and the inset is added to it, so
     * repeated dispatches (keyboard, rotation) land on the same value.
     *
     * @param top status-bar inset (screens that draw their own header).
     * @param bottom navigation-bar inset (screens without the bottom nav).
     * @param sides left/right bars + display cutout (landscape phones).
     * @param ime when the keyboard is open, pad by it instead of the nav
     *   bar so a pinned button or the focused field stays above it.
     */
    protected fun applyInsets(
        target: View,
        top: Boolean = false,
        bottom: Boolean = false,
        sides: Boolean = false,
        ime: Boolean = false,
    ) {
        val baseLeft = target.paddingLeft
        val baseTop = target.paddingTop
        val baseRight = target.paddingRight
        val baseBottom = target.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(target) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val keyboard = if (ime) insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0
            val bottomInset = when {
                bottom && ime -> max(bars.bottom, keyboard)
                bottom -> bars.bottom
                ime -> keyboard
                else -> 0
            }
            view.setPadding(
                baseLeft + if (sides) bars.left else 0,
                baseTop + if (top) bars.top else 0,
                baseRight + if (sides) bars.right else 0,
                baseBottom + bottomInset,
            )
            insets
        }
        // Force a dispatch so the padding applies immediately — otherwise
        // the first frame draws without insets and the content jumps.
        ViewCompat.requestApplyInsets(target)
    }

    /**
     * Push a single floating control (e.g. a Skip button over full-bleed
     * art) below the status bar by adding the inset to its top margin.
     */
    protected fun applyStatusBarMargin(target: View) {
        val lp = target.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val baseTop = lp.topMargin
        ViewCompat.setOnApplyWindowInsetsListener(target) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                if (it.topMargin != baseTop + bars.top) {
                    it.topMargin = baseTop + bars.top
                    view.layoutParams = it
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(target)
    }

    /**
     * Status bar on top (+ the nav bar at the bottom when [applyBottom])
     * for screens that draw their own header. Side insets are included
     * so content clears a side nav bar in landscape.
     */
    protected fun applySystemBarInsets(
        target: View = binding.root,
        applyBottom: Boolean = true,
    ) = applyInsets(target, top = true, bottom = applyBottom, sides = true)

    /**
     * Navigation bar — or the keyboard while it's open — at the bottom
     * of [target]. For pinned bottom bars (Save / Create buttons) and the
     * last scrolling view on screens without the bottom navigation.
     */
    protected fun applyBottomNavInset(target: View) =
        applyInsets(target, bottom = true, ime = true)
}
