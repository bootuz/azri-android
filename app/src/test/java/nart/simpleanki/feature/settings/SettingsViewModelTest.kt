package nart.simpleanki.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.auth.FakeAuthRepository
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
    fun reflectsSettingsAndAccount() = runTest {
        val settings = FakeSettingsRepository()
        val auth = FakeAuthRepository().apply { emit(FakeAuthRepository.GOOGLE_USER) }
        val vm = SettingsViewModel(settings, auth)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertEquals(FsrsPreset.Optimal, vm.uiState.value.settings.preset)
        assertEquals("grace@example.com", vm.uiState.value.email)
        assertEquals(false, vm.uiState.value.isAnonymous)
    }

    @Test
    fun updatesPersistThroughRepository() = runTest {
        val settings = FakeSettingsRepository()
        val vm = SettingsViewModel(settings, FakeAuthRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.setPreset(FsrsPreset.Aggressive); runCurrent()
        vm.setNewCardsPerDay(5); runCurrent()
        vm.setMaxReviewsPerDay(50); runCurrent()

        assertEquals(FsrsPreset.Aggressive, vm.uiState.value.settings.preset)
        assertEquals(5, vm.uiState.value.settings.newCardsPerDay)
        assertEquals(50, vm.uiState.value.settings.maxReviewsPerDay)
    }

    @Test
    fun signOut_delegatesToAuth() = runTest {
        val auth = FakeAuthRepository().apply { emit(FakeAuthRepository.ANON_USER) }
        val vm = SettingsViewModel(FakeSettingsRepository(), auth)
        vm.signOut()
        assertEquals(1, auth.signOutCalls)
    }
}
