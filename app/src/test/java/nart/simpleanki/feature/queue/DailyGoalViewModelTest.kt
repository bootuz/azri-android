package nart.simpleanki.feature.queue

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.core.data.settings.AppSettings
import nart.simpleanki.core.data.settings.FakeSettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyGoalViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun reflectsSettings_andDerivesTotal() = runTest {
        val vm = DailyGoalViewModel(FakeSettingsRepository(AppSettings(dailyGoalEnabled = true, newCardsTarget = 10, reviewCardsTarget = 20)))
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertTrue(vm.uiState.value.enabled)
        assertEquals(30, vm.uiState.value.total)
    }

    @Test
    fun setters_persist() = runTest {
        val vm = DailyGoalViewModel(FakeSettingsRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.setEnabled(false); runCurrent()
        assertFalse(vm.uiState.value.enabled)

        vm.setEnabled(true); vm.setNewCardsTarget(5); vm.setReviewCardsTarget(15); runCurrent()
        val s = vm.uiState.value
        assertEquals(5, s.newCardsTarget)
        assertEquals(15, s.reviewCardsTarget)
        assertEquals(20, s.total)
    }

    @Test
    fun resetToDefaults_restoresEnabled10And20() = runTest {
        val vm = DailyGoalViewModel(
            FakeSettingsRepository(AppSettings(dailyGoalEnabled = false, newCardsTarget = 3, reviewCardsTarget = 4)),
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.resetToDefaults(); runCurrent()
        val s = vm.uiState.value
        assertTrue(s.enabled)
        assertEquals(10, s.newCardsTarget)
        assertEquals(20, s.reviewCardsTarget)
    }
}
