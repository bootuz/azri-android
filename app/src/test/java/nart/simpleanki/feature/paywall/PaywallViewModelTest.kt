package nart.simpleanki.feature.paywall

import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.core.billing.Entitlement
import nart.simpleanki.core.billing.FakeEntitlementRepository
import nart.simpleanki.core.billing.PremiumTier
import nart.simpleanki.core.billing.PurchaseResult
import android.app.Activity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun loadsPlans_andPreselectsAnnual() = runTest {
        val repo = FakeEntitlementRepository()
        val vm = PaywallViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        val s = vm.uiState.value
        assertFalse(s.loading)
        assertFalse(s.plansUnavailable)
        assertEquals(3, s.plans.size)
        assertEquals(PremiumTier.Annual, s.selected)
        assertEquals(1, repo.refreshCount)
    }

    @Test fun noPlansAfterLoad_marksUnavailable_notLoading() = runTest {
        // Store returns nothing (offline / not installed from Play): finish loading, mark unavailable.
        val repo = FakeEntitlementRepository(plans = emptyList())
        val vm = PaywallViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        val s = vm.uiState.value
        assertFalse("load attempt finished", s.loading)
        assertTrue("empty store → unavailable", s.plansUnavailable)
    }

    @Test fun retry_refetchesPlans() = runTest {
        val repo = FakeEntitlementRepository(plans = emptyList())
        val vm = PaywallViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(1, repo.refreshCount)
        vm.retry(); runCurrent()
        assertEquals(2, repo.refreshCount)
    }

    @Test fun select_changesSelectedTier() = runTest {
        val vm = PaywallViewModel(FakeEntitlementRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.select(PremiumTier.Lifetime); runCurrent()
        assertEquals(PremiumTier.Lifetime, vm.uiState.value.selected)
    }

    @Test fun purchase_successUpdatesEntitlement_andExposesResult() = runTest {
        val repo = FakeEntitlementRepository()
        val vm = PaywallViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.purchase(mockk<Activity>()); runCurrent()
        assertEquals(PremiumTier.Annual, repo.lastPurchased?.tier)
        assertEquals(PurchaseResult.Success, vm.uiState.value.result)
        assertTrue(vm.uiState.value.isPremium)
    }

    @Test fun restore_invokesRepository() = runTest {
        val repo = FakeEntitlementRepository().apply { nextResult = PurchaseResult.Success; setEntitlement(Entitlement(PremiumTier.Lifetime)) }
        val vm = PaywallViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.restore(); runCurrent()
        assertEquals(1, repo.restoreCount)
        assertEquals(PurchaseResult.Success, vm.uiState.value.result)
        assertTrue(vm.uiState.value.isPremium)
    }
}
