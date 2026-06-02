package nart.simpleanki.feature.settings

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
import nart.simpleanki.core.domain.fsrs.FsrsPreset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun reflectsSettings() = runTest {
        val vm = SettingsViewModel(FakeSettingsRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(FsrsPreset.Optimal, vm.uiState.value.settings.preset)
    }

    @Test
    fun updatesPersistThroughRepository() = runTest {
        val vm = SettingsViewModel(FakeSettingsRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.setPreset(FsrsPreset.Aggressive); runCurrent()
        assertEquals(FsrsPreset.Aggressive, vm.uiState.value.settings.preset)
    }

    @Test
    fun customParameters_persistThroughRepository() = runTest {
        val vm = SettingsViewModel(FakeSettingsRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.setPreset(FsrsPreset.Custom); runCurrent()
        vm.setCustomRetention(0.95); runCurrent()
        vm.setCustomMaxInterval(90); runCurrent()
        vm.setEnableFuzz(false); runCurrent()
        vm.setEnableShortTerm(false); runCurrent()

        val s = vm.uiState.value.settings
        assertEquals(FsrsPreset.Custom, s.preset)
        assertEquals(0.95, s.customRetention, 1e-9)
        assertEquals(90, s.customMaxInterval)
        assertEquals(false, s.enableFuzz)
        assertEquals(false, s.enableShortTerm)
    }

    @Test
    fun resetToDefaults_restoresOptimalAndDefaultCustomValues() = runTest {
        val vm = SettingsViewModel(
            FakeSettingsRepository(
                AppSettings(
                    preset = FsrsPreset.Custom, customRetention = 0.82,
                    customMaxInterval = 30, enableFuzz = false, enableShortTerm = false,
                ),
            ),
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.resetToDefaults(); runCurrent()

        val s = vm.uiState.value.settings
        assertEquals(FsrsPreset.Optimal, s.preset)
        assertEquals(0.90, s.customRetention, 1e-9)
        assertEquals(365, s.customMaxInterval)
        assertEquals(true, s.enableFuzz)
        assertEquals(true, s.enableShortTerm)
    }
}
