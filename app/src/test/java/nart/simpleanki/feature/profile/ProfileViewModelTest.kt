package nart.simpleanki.feature.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.auth.AuthUser
import nart.simpleanki.auth.FakeAuthRepository
import nart.simpleanki.core.data.settings.FakeSettingsRepository
import nart.simpleanki.core.data.settings.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun reflectsAccount_andSettings() = runTest {
        val auth = FakeAuthRepository(MutableStateFlow(AuthUser("u1", "Grace", "grace@example.com", isAnonymous = false)))
        val vm = ProfileViewModel(FakeSettingsRepository(), auth)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertEquals("grace@example.com", vm.uiState.value.email)
        assertFalse(vm.uiState.value.isAnonymous)
        assertEquals(ThemeMode.System, vm.uiState.value.themeMode)
    }

    @Test
    fun setTheme_persists_andSignOutDelete_delegateToAuth() = runTest {
        val auth = FakeAuthRepository()
        val settings = FakeSettingsRepository()
        val vm = ProfileViewModel(settings, auth)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.setThemeMode(ThemeMode.Dark); runCurrent()
        assertEquals(ThemeMode.Dark, vm.uiState.value.themeMode)

        vm.signOut()
        assertEquals(1, auth.signOutCalls)

        vm.deleteAccount(); runCurrent()
        assertEquals(1, auth.deleteCalls)
    }
}
