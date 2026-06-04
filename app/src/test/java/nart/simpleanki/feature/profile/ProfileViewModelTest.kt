package nart.simpleanki.feature.profile

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.auth.AuthRepository
import nart.simpleanki.auth.AuthUser
import nart.simpleanki.auth.FakeAuthRepository
import nart.simpleanki.core.billing.Entitlement
import nart.simpleanki.core.billing.FakeEntitlementRepository
import nart.simpleanki.core.billing.PremiumTier
import nart.simpleanki.core.data.settings.AppSettings
import nart.simpleanki.core.data.settings.FakeSettingsRepository
import nart.simpleanki.core.data.settings.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun auth(user: AuthUser?): AuthRepository = mockk(relaxed = true) {
        every { authState } returns flowOf(user)
    }

    @Test
    fun reflectsAccount_andSettings() = runTest {
        val fakeAuth = FakeAuthRepository(MutableStateFlow(AuthUser("u1", "Grace", "grace@example.com", isAnonymous = false)))
        val vm = ProfileViewModel(FakeSettingsRepository(), fakeAuth, FakeEntitlementRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertEquals("grace@example.com", vm.uiState.value.email)
        assertFalse(vm.uiState.value.isAnonymous)
        assertEquals(ThemeMode.System, vm.uiState.value.themeMode)
    }

    @Test
    fun setTheme_persists_andSignOutDelete_delegateToAuth() = runTest {
        val fakeAuth = FakeAuthRepository()
        val settings = FakeSettingsRepository()
        val vm = ProfileViewModel(settings, fakeAuth, FakeEntitlementRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.setThemeMode(ThemeMode.Dark); runCurrent()
        assertEquals(ThemeMode.Dark, vm.uiState.value.themeMode)

        vm.signOut()
        assertEquals(1, fakeAuth.signOutCalls)

        vm.deleteAccount(); runCurrent()
        assertEquals(1, fakeAuth.deleteCalls)
    }

    @Test fun isPremium_reflectsEntitlement() = runTest {
        val vm = ProfileViewModel(FakeSettingsRepository(), auth(null), FakeEntitlementRepository(Entitlement(PremiumTier.Lifetime)))
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertTrue(vm.uiState.value.isPremium)
    }

    @Test fun isPremium_falseWhenNone() = runTest {
        val vm = ProfileViewModel(FakeSettingsRepository(), auth(null), FakeEntitlementRepository(Entitlement(PremiumTier.None)))
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertFalse(vm.uiState.value.isPremium)
    }

    @Test fun restorePurchases_invokesRepository() = runTest {
        val repo = FakeEntitlementRepository(Entitlement(PremiumTier.None))
        val vm = ProfileViewModel(FakeSettingsRepository(), auth(null), repo)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.restorePurchases(); runCurrent()
        assertEquals(1, repo.restoreCount)
    }

    @Test
    fun reflectsDailyGoal_fromSettings() = runTest {
        val settings = FakeSettingsRepository(
            AppSettings(dailyGoalEnabled = true, newCardsTarget = 10, reviewCardsTarget = 20),
        )
        val vm = ProfileViewModel(settings, auth(null), FakeEntitlementRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertTrue(vm.uiState.value.dailyGoalEnabled)
        assertEquals(30, vm.uiState.value.dailyGoalTotal) // 10 new + 20 review
    }
}
