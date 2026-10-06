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
     * The returned [WindowInsetsCompat] is consumed in full so sibling
     * views don't double-apply the padding.
     *
     * @param applyBottom when true (default), nav-bar + IME inset is also
     *   added as bottom padding. Pass `false` when the layout already has
     *   room for the bottom-nav rail (e.g. a `MaterialCardView` the user
     *   won't scroll past).
     */
    protected fun applySystemBarInsets(
        target: View = binding.root,
        applyBottom: Boolean = true,
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(target) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = bars.top,
                bottom = if (applyBottom) bars.bottom else view.paddingBottom,
            )
            WindowInsetsCompat.CONSUMED
        }
    }
}
