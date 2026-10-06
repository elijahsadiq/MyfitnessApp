package com.elijah.fitness.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Shared base class for every fragment in the app (except the bottom sheet,
 * which has to extend `BottomSheetDialogFragment`).
 *
 * ### What this class removes from every subclass
 * - **ViewBinding boilerplate**: subclasses only need to implement
 *   [inflateBinding]. The base class handles inflate-in-`onCreateView` and
 *   null-out-in-`onDestroyView` so binding leaks are impossible.
 * - **Error UI**: [showError] wraps `Snackbar` construction.
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

    /** Show a short error message anchored on the fragment's root view. */
    protected fun showError(message: CharSequence) {
        _binding?.let { vb ->
            Snackbar.make(vb.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

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
     * Consume status-bar + navigation-bar insets as padding on [target].
     * Call from `onViewCreated` on any top-level fragment that draws its
     * own header (Home / History / Progress / Goals) — the shared toolbar
     * is hidden for those, so the fragment root sits beneath the status
     * bar unless we pay rent on the insets ourselves.
     *
     * ### Preserves the view's original padding
     * The target view's XML padding is captured once per call and used as
     * the baseline; the inset values are **added** on top. This is the
     * standard pattern — the previous "set padding to bars.top" shape
     * silently wiped out any `android:paddingTop` the layout declared
     * AND accumulated extra space when inset dispatch happened to fire
     * through a different hierarchy path (e.g. after returning from an
     * edit dialog that briefly showed the IME). Preserving the baseline
     * makes the result idempotent: multiple dispatches land on the same
     * final padding.
     *
     * @param applyBottom when true (default), nav-bar inset is added to
     *   the baseline bottom padding. Pass `false` when a sibling view
     *   (e.g. the BottomNavigationView drawn on top) already handles
     *   the bottom inset.
     */
    protected fun applySystemBarInsets(
        target: View = binding.root,
        applyBottom: Boolean = true,
    ) {
        val baseLeft = target.paddingLeft
        val baseTop = target.paddingTop
        val baseRight = target.paddingRight
        val baseBottom = target.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(target) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                baseLeft,
                baseTop + bars.top,
                baseRight,
                if (applyBottom) baseBottom + bars.bottom else baseBottom,
            )
            WindowInsetsCompat.CONSUMED
        }
        // Force a dispatch so the padding applies immediately — otherwise
        // the first frame draws without insets and the content pops into
        // place on the next layout pass.
        ViewCompat.requestApplyInsets(target)
    }

    /**
     * Add just the system navigation-bar inset as additional bottom
     * padding on [target]. Use this on the scrolling view of a top-level
     * fragment so its content can scroll fully clear of both the app's
     * `BottomNavigationView` (handled by a baseline `paddingBottom =
     * bottom_nav_height_plus_breathing_room` in the layout) **and** the
     * device's system nav bar on three-button-nav devices.
     *
     * Preserves [target]'s XML padding and adds the inset on top, same
     * as [applySystemBarInsets].
     */
    protected fun applyBottomNavInset(target: View) {
        val baseLeft = target.paddingLeft
        val baseTop = target.paddingTop
        val baseRight = target.paddingRight
        val baseBottom = target.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(target) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(baseLeft, baseTop, baseRight, baseBottom + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(target)
    }
}
