package com.elijah.fitness.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elijah.fitness.AppContainer
import com.elijah.fitness.data.local.LastLoggedActivityStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Log a workout sheet (phase 8d): which activity card is
 * selected and whether a run / ride / row is tracked live or added by
 * hand. Survives a rotation, so the sheet comes back on the same card.
 *
 * [state] is `null` until the last-logged activity has been read from
 * DataStore, so the sheet never flashes Running before jumping to,
 * say, Swimming. The sheet only reads it: the save paths write it
 * ([AppContainer.rememberLoggedActivity]) once a workout is saved.
 */
class ChooseActivityViewModel(
    private val lastLoggedActivityStore: LastLoggedActivityStore,
) : ViewModel() {

    private val _state = MutableStateFlow<LogSheetUiState?>(null)
    val state: StateFlow<LogSheetUiState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val remembered = try {
                lastLoggedActivityStore.lastActivity.first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null // unreadable preferences: fall back to Running
            }
            _state.update {
                it ?: LogSheetUiState(selectedIndex = LogSheetOptions.initialIndex(remembered))
            }
        }
    }

    /**
     * The user moved the carousel to [index]. Ignored before [state] is
     * ready (the sheet only builds the carousel once it is).
     */
    fun select(index: Int) {
        val safe = index.coerceIn(0, LogSheetOptions.activities.lastIndex)
        _state.update { it?.copy(selectedIndex = safe) }
    }

    /** The user picked "Live with GPS" or "Add manually". */
    fun setMode(mode: LogMode) {
        _state.update { it?.copy(mode = mode) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChooseActivityViewModel(
                    lastLoggedActivityStore = container.lastLoggedActivityStore,
                )
            }
        }
    }
}
