package com.elijah.fitness.ui.log

import com.elijah.fitness.R
import com.elijah.fitness.data.local.LastLoggedActivityStore
import com.elijah.fitness.data.model.ActivityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Phase 8d Step 3: `ChooseActivityViewModel` (Log a workout sheet state). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChooseActivityViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeStore(initial: ActivityType?) : LastLoggedActivityStore {
        val stored = MutableStateFlow(initial)
        override val lastActivity: Flow<ActivityType?> = stored
        override suspend fun setLastActivity(type: ActivityType) { stored.value = type }
        override suspend fun clear() { stored.value = null }
    }

    private class BrokenStore : LastLoggedActivityStore {
        override val lastActivity: Flow<ActivityType?> = flow { throw IOException("disk") }
        override suspend fun setLastActivity(type: ActivityType) = throw IOException("disk")
        override suspend fun clear() = Unit
    }

    @Test
    fun `state stays null until the remembered activity is read`() = runTest(dispatcher) {
        val vm = ChooseActivityViewModel(FakeStore(ActivityType.SWIMMING))
        assertNull(vm.state.value)
        advanceUntilIdle()
        assertEquals(ActivityType.SWIMMING, vm.state.value?.activity)
        assertEquals(LogMode.LIVE, vm.state.value?.mode)
    }

    @Test
    fun `a new user starts on Running`() = runTest(dispatcher) {
        val vm = ChooseActivityViewModel(FakeStore(null))
        advanceUntilIdle()
        assertEquals(0, vm.state.value?.selectedIndex)
        assertEquals(R.string.log_sheet_cta_run_live, vm.state.value?.ctaLabelRes)
    }

    @Test
    fun `unreadable preferences fall back to Running instead of crashing`() = runTest(dispatcher) {
        val vm = ChooseActivityViewModel(BrokenStore())
        advanceUntilIdle()
        assertEquals(ActivityType.RUNNING, vm.state.value?.activity)
    }

    @Test
    fun `select and setMode update the button label`() = runTest(dispatcher) {
        val vm = ChooseActivityViewModel(FakeStore(null))
        advanceUntilIdle()
        vm.select(3)
        assertEquals(R.string.log_sheet_cta_row_live, vm.state.value?.ctaLabelRes)
        vm.setMode(LogMode.MANUAL)
        assertEquals(R.string.log_sheet_cta_row_manual, vm.state.value?.ctaLabelRes)
        // The mode is kept while swiping, as in the design.
        vm.select(1)
        assertEquals(R.string.log_sheet_cta_ride_manual, vm.state.value?.ctaLabelRes)
        vm.select(2)
        assertEquals(R.string.log_sheet_cta_swim, vm.state.value?.ctaLabelRes)
    }

    @Test
    fun `select clamps an out of range index`() = runTest(dispatcher) {
        val vm = ChooseActivityViewModel(FakeStore(null))
        advanceUntilIdle()
        vm.select(42)
        assertEquals(4, vm.state.value?.selectedIndex)
        vm.select(-1)
        assertEquals(0, vm.state.value?.selectedIndex)
    }

    @Test
    fun `a selection made before the state is ready is ignored`() = runTest(dispatcher) {
        val vm = ChooseActivityViewModel(FakeStore(ActivityType.ROWING))
        vm.select(0)
        advanceUntilIdle()
        assertEquals(ActivityType.ROWING, vm.state.value?.activity)
    }

    @Test
    fun `choosing a card never changes the remembered activity`() = runTest(dispatcher) {
        // Only a saved workout updates it (AppContainer.rememberLoggedActivity),
        // so opening a form and backing out leaves the sheet where it was.
        val store = FakeStore(ActivityType.CYCLING)
        val vm = ChooseActivityViewModel(store)
        advanceUntilIdle()
        vm.select(2)
        vm.setMode(LogMode.MANUAL)
        advanceUntilIdle()
        assertEquals(ActivityType.CYCLING, store.stored.value)
    }
}
