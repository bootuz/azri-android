# Premium & Paywall Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Premium tier whose first feature is cloud sync — gating the existing two-way sync behind an entitlement, fronted by a vibrant "Dark Luxe" paywall driven by Google Play Billing.

**Architecture:** A provider-agnostic `EntitlementRepository` interface is the single source of truth for premium state and available plans. Pure functions decide gating (`Entitlements.shouldSync`) and price display (`PlanPricing`). The app, ViewModels, and Compose UI depend only on the interface + a `FakeEntitlementRepository`; the real `PlayBillingRepository` is built last and swapped into Koin, so every prior task ships working, unit-tested software with no Play Console needed.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Koin, DataStore, Google Play Billing Library 7, JUnit4 + MockK + Compose UI test.

**Conventions (match existing code):**
- Run JVM tests: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "<FQN>"`
- Run instrumented tests (emulator): `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQN>`
- Commit messages: imperative, **never mention Claude**.
- Package root: `nart.simpleanki`. Source root: `app/src/main/java/nart/simpleanki/`, unit tests `app/src/test/java/nart/simpleanki/`, instrumented `app/src/androidTest/java/nart/simpleanki/`.

---

## File Structure

**Create:**
- `core/billing/Entitlement.kt` — `PremiumTier` enum, `Entitlement` data class, `PlanOption` data class, `PurchaseResult` enum.
- `core/billing/BillingProducts.kt` — product-id / base-plan constants.
- `core/billing/PlanPricing.kt` — pure price-derivation functions.
- `core/billing/Entitlements.kt` — pure `shouldSync(...)` and `shouldShowPremiumNudge(...)`.
- `core/billing/EntitlementRepository.kt` — interface.
- `core/billing/PlayBillingRepository.kt` — real Play Billing implementation (Task 9).
- `core/billing/EntitlementCache.kt` — DataStore-backed cache of the last known entitlement (Task 9).
- `feature/paywall/PaywallViewModel.kt` — `PaywallUiState` + ViewModel.
- `feature/paywall/PaywallScreen.kt` — `PaywallScreen` (stateful) + `PaywallContent` (stateless, Dark Luxe).
- `app/src/test/java/nart/simpleanki/core/billing/FakeEntitlementRepository.kt` — test double.
- Test files mirrored under `test/` and `androidTest/` (named per task).

**Modify:**
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — add Play Billing dependency.
- `core/data/settings/SettingsRepository.kt` — add `premiumNudgeDismissed` flag.
- `app/src/test/java/nart/simpleanki/core/data/settings/FakeSettingsRepository.kt` — add the new setter.
- `feature/queue/StudyQueueViewModel.kt` + `feature/queue/StudyQueueScreen.kt` — Today-screen premium nudge.
- `feature/profile/ProfileViewModel.kt` + `feature/profile/ProfileScreen.kt` — premium-aware rows + entry points.
- `ui/navigation/AzriNavHost.kt` — `paywall` route + Profile callback.
- `ui/AzriRoot.kt` — gate the 20s foreground sync loop.
- `core/data/sync/SyncWorker.kt` — gate the periodic sync.
- `di/AppModule.kt` — bind `EntitlementRepository`, inject into ViewModels/Worker.

---

## Task 1: Billing dependency + core models

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/nart/simpleanki/core/billing/Entitlement.kt`
- Create: `app/src/main/java/nart/simpleanki/core/billing/BillingProducts.kt`

- [ ] **Step 1: Add the Play Billing version + library to the catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:
```toml
billing = "7.1.1"
```
Under `[libraries]` (near the firebase block) add:
```toml
billing-ktx = { group = "com.android.billingclient", name = "billing-ktx", version.ref = "billing" }
```

- [ ] **Step 2: Add the dependency to the app module**

In `app/build.gradle.kts`, in the `dependencies { }` block after the firebase lines, add:
```kotlin
    implementation(libs.billing.ktx)
```

- [ ] **Step 3: Create the core models**

Create `app/src/main/java/nart/simpleanki/core/billing/Entitlement.kt`:
```kotlin
package nart.simpleanki.core.billing

/** Which premium product unlocked the user, if any. */
enum class PremiumTier { None, Monthly, Annual, Lifetime }

/** Single source-of-truth value for premium state. */
data class Entitlement(val tier: PremiumTier = PremiumTier.None) {
    val isPremium: Boolean get() = tier != PremiumTier.None
}

/** Outcome of a purchase or restore attempt. */
enum class PurchaseResult { Success, Cancelled, Pending, Error }

/**
 * A display-ready plan derived from a Play `ProductDetails`. [basePlanId]/[offerToken] are
 * null for the one-time lifetime product; populated for subscription base plans.
 */
data class PlanOption(
    val tier: PremiumTier,
    val productId: String,
    val basePlanId: String?,
    val offerToken: String?,
    val formattedPrice: String,
    val priceAmountMicros: Long,
)
```

Create `app/src/main/java/nart/simpleanki/core/billing/BillingProducts.kt`:
```kotlin
package nart.simpleanki.core.billing

/** Play Console product identifiers. Configured in Play Console; safe to be public. */
object BillingProducts {
    /** Subscription product carrying both base plans. */
    const val SUBSCRIPTION_ID = "azri_premium"
    const val BASE_PLAN_MONTHLY = "monthly"
    const val BASE_PLAN_ANNUAL = "annual"

    /** One-time, non-consumable lifetime unlock. */
    const val LIFETIME_ID = "azri_premium_lifetime"
}
```

- [ ] **Step 4: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/nart/simpleanki/core/billing/
git commit -m "Add Play Billing dependency and premium core models"
```

---

## Task 2: Pure gating + pricing functions (TDD)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/billing/Entitlements.kt`
- Create: `app/src/main/java/nart/simpleanki/core/billing/PlanPricing.kt`
- Test: `app/src/test/java/nart/simpleanki/core/billing/EntitlementsTest.kt`
- Test: `app/src/test/java/nart/simpleanki/core/billing/PlanPricingTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/nart/simpleanki/core/billing/EntitlementsTest.kt`:
```kotlin
package nart.simpleanki.core.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsTest {
    @Test fun syncsOnlyWhenPremiumAndSignedInWithGoogle() {
        assertTrue(Entitlements.shouldSync(isPremium = true, signedInWithGoogle = true))
        assertFalse(Entitlements.shouldSync(isPremium = true, signedInWithGoogle = false))
        assertFalse(Entitlements.shouldSync(isPremium = false, signedInWithGoogle = true))
        assertFalse(Entitlements.shouldSync(isPremium = false, signedInWithGoogle = false))
    }

    @Test fun nudgeShowsOnlyForFreeUserWithCardsWhoHasNotDismissed() {
        assertTrue(Entitlements.shouldShowPremiumNudge(isPremium = false, dismissed = false, hasAnyCards = true))
        assertFalse("premium hides it", Entitlements.shouldShowPremiumNudge(true, false, true))
        assertFalse("dismissed hides it", Entitlements.shouldShowPremiumNudge(false, true, true))
        assertFalse("no cards hides it", Entitlements.shouldShowPremiumNudge(false, false, false))
    }
}
```

Create `app/src/test/java/nart/simpleanki/core/billing/PlanPricingTest.kt`:
```kotlin
package nart.simpleanki.core.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanPricingTest {
    @Test fun perMonthMicros_monthlyIsItself_annualIsDivided_lifetimeNull() {
        assertEquals(2_990_000L, PlanPricing.perMonthMicros(PremiumTier.Monthly, 2_990_000L))
        assertEquals(1_665_833L, PlanPricing.perMonthMicros(PremiumTier.Annual, 19_990_000L))
        assertNull(PlanPricing.perMonthMicros(PremiumTier.Lifetime, 49_990_000L))
        assertNull(PlanPricing.perMonthMicros(PremiumTier.None, 0L))
    }

    @Test fun annualSavingsPercent_roundsAndClamps() {
        // monthly $2.99 -> annual/12 = $1.665 -> ~44% savings
        assertEquals(44, PlanPricing.annualSavingsPercent(monthlyMicros = 2_990_000L, annualMicros = 19_990_000L))
        // zero monthly -> no savings (avoid div-by-zero)
        assertEquals(0, PlanPricing.annualSavingsPercent(monthlyMicros = 0L, annualMicros = 19_990_000L))
        // annual more expensive per-month -> clamp to 0
        assertEquals(0, PlanPricing.annualSavingsPercent(monthlyMicros = 1_000_000L, annualMicros = 24_000_000L))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.billing.EntitlementsTest" --tests "nart.simpleanki.core.billing.PlanPricingTest"`
Expected: FAIL — `Entitlements`/`PlanPricing` unresolved.

- [ ] **Step 3: Implement the pure functions**

Create `app/src/main/java/nart/simpleanki/core/billing/Entitlements.kt`:
```kotlin
package nart.simpleanki.core.billing

/** Pure premium-gating decisions — no Android, fully unit-testable. */
object Entitlements {
    /** Cloud sync runs only for a premium user signed in with Google (stable account). */
    fun shouldSync(isPremium: Boolean, signedInWithGoogle: Boolean): Boolean =
        isPremium && signedInWithGoogle

    /** The Today-screen upsell shows only to a free user who has cards and hasn't dismissed it. */
    fun shouldShowPremiumNudge(isPremium: Boolean, dismissed: Boolean, hasAnyCards: Boolean): Boolean =
        !isPremium && !dismissed && hasAnyCards
}
```

Create `app/src/main/java/nart/simpleanki/core/billing/PlanPricing.kt`:
```kotlin
package nart.simpleanki.core.billing

import kotlin.math.roundToInt

/** Pure price-derivation helpers operating on Play price micros (1_000_000 micros = 1 unit). */
object PlanPricing {
    /** Per-month micros for a plan, or null when not applicable (lifetime / none). */
    fun perMonthMicros(tier: PremiumTier, priceAmountMicros: Long): Long? = when (tier) {
        PremiumTier.Monthly -> priceAmountMicros
        PremiumTier.Annual -> priceAmountMicros / 12
        else -> null
    }

    /** Whole-percent savings of annual (per month) vs monthly; 0 if monthly is missing/cheaper. */
    fun annualSavingsPercent(monthlyMicros: Long, annualMicros: Long): Int {
        if (monthlyMicros <= 0L) return 0
        val annualPerMonth = annualMicros / 12.0
        val pct = (1.0 - annualPerMonth / monthlyMicros) * 100.0
        return pct.coerceAtLeast(0.0).roundToInt()
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.billing.EntitlementsTest" --tests "nart.simpleanki.core.billing.PlanPricingTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/billing/ app/src/test/java/nart/simpleanki/core/billing/
git commit -m "Add pure gating and pricing functions for premium"
```

---

## Task 3: EntitlementRepository interface + fake + temporary Koin binding

This makes premium injectable everywhere. A `LocalEntitlementRepository` (always free, no-op purchase) keeps the app runnable until Task 9 swaps in real Play Billing.

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/billing/EntitlementRepository.kt`
- Create: `app/src/main/java/nart/simpleanki/core/billing/LocalEntitlementRepository.kt`
- Create: `app/src/test/java/nart/simpleanki/core/billing/FakeEntitlementRepository.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`

- [ ] **Step 1: Create the interface**

Create `app/src/main/java/nart/simpleanki/core/billing/EntitlementRepository.kt`:
```kotlin
package nart.simpleanki.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for premium state and available plans. Implementations:
 * [PlayBillingRepository] (real) and [LocalEntitlementRepository] / FakeEntitlementRepository.
 */
interface EntitlementRepository {
    /** Current entitlement; cached so it survives offline. Defaults to free. */
    val entitlement: StateFlow<Entitlement>

    /** Purchasable plans loaded from the store (empty until loaded / when unavailable). */
    val plans: StateFlow<List<PlanOption>>

    /** Re-query store products and the user's current purchases. */
    suspend fun refresh()

    /** Launch the store purchase flow for [plan]; returns the immediate outcome. */
    suspend fun purchase(activity: Activity, plan: PlanOption): PurchaseResult

    /** Re-check existing purchases (e.g. new device); returns the outcome. */
    suspend fun restore(): PurchaseResult
}
```

- [ ] **Step 2: Create the always-free local implementation**

Create `app/src/main/java/nart/simpleanki/core/billing/LocalEntitlementRepository.kt`:
```kotlin
package nart.simpleanki.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Placeholder binding used until Play Billing is wired in (Task 9). Reports "free" and
 * cannot purchase — lets the paywall and gating run end-to-end in builds without Play.
 */
class LocalEntitlementRepository : EntitlementRepository {
    private val _entitlement = MutableStateFlow(Entitlement())
    override val entitlement: StateFlow<Entitlement> = _entitlement.asStateFlow()

    private val _plans = MutableStateFlow<List<PlanOption>>(emptyList())
    override val plans: StateFlow<List<PlanOption>> = _plans.asStateFlow()

    override suspend fun refresh() { /* no store available */ }
    override suspend fun purchase(activity: Activity, plan: PlanOption): PurchaseResult = PurchaseResult.Error
    override suspend fun restore(): PurchaseResult = PurchaseResult.Error
}
```

- [ ] **Step 3: Create the test fake**

Create `app/src/test/java/nart/simpleanki/core/billing/FakeEntitlementRepository.kt`:
```kotlin
package nart.simpleanki.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory EntitlementRepository for unit/Compose tests. */
class FakeEntitlementRepository(
    initial: Entitlement = Entitlement(),
    plans: List<PlanOption> = DEFAULT_PLANS,
) : EntitlementRepository {
    private val _entitlement = MutableStateFlow(initial)
    override val entitlement: StateFlow<Entitlement> = _entitlement.asStateFlow()

    private val _plans = MutableStateFlow(plans)
    override val plans: StateFlow<List<PlanOption>> = _plans.asStateFlow()

    var refreshCount = 0; private set
    var lastPurchased: PlanOption? = null; private set
    var restoreCount = 0; private set
    var nextResult: PurchaseResult = PurchaseResult.Success

    fun setEntitlement(e: Entitlement) { _entitlement.value = e }
    fun setPlans(p: List<PlanOption>) { _plans.value = p }

    override suspend fun refresh() { refreshCount++ }
    override suspend fun purchase(activity: Activity, plan: PlanOption): PurchaseResult {
        lastPurchased = plan
        if (nextResult == PurchaseResult.Success) _entitlement.value = Entitlement(plan.tier)
        return nextResult
    }
    override suspend fun restore(): PurchaseResult { restoreCount++; return nextResult }

    companion object {
        val DEFAULT_PLANS = listOf(
            PlanOption(PremiumTier.Annual, BillingProducts.SUBSCRIPTION_ID, BillingProducts.BASE_PLAN_ANNUAL, "tokA", "$19.99", 19_990_000L),
            PlanOption(PremiumTier.Monthly, BillingProducts.SUBSCRIPTION_ID, BillingProducts.BASE_PLAN_MONTHLY, "tokM", "$2.99", 2_990_000L),
            PlanOption(PremiumTier.Lifetime, BillingProducts.LIFETIME_ID, null, null, "$49.99", 49_990_000L),
        )
    }
}
```

- [ ] **Step 4: Bind it in Koin**

In `app/src/main/java/nart/simpleanki/di/AppModule.kt`, add the import near the other `core.data` imports:
```kotlin
import nart.simpleanki.core.billing.EntitlementRepository
import nart.simpleanki.core.billing.LocalEntitlementRepository
```
In the `appModule` block, directly after the `// Sync` section (after the `SyncManager` line), add:
```kotlin
    // Billing / entitlement (LocalEntitlementRepository is a placeholder until Play Billing — Task 9)
    single<EntitlementRepository> { LocalEntitlementRepository() }
```

- [ ] **Step 5: Verify it compiles and existing tests still pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/billing/ app/src/test/java/nart/simpleanki/core/billing/ app/src/main/java/nart/simpleanki/di/AppModule.kt
git commit -m "Add EntitlementRepository interface, fake, and placeholder binding"
```

---

## Task 4: PaywallViewModel (TDD)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/feature/paywall/PaywallViewModel.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/paywall/PaywallViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/nart/simpleanki/feature/paywall/PaywallViewModelTest.kt`:
```kotlin
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
        assertEquals(3, s.plans.size)
        assertEquals(PremiumTier.Annual, s.selected)   // best value preselected
        assertEquals(1, repo.refreshCount)             // refreshes on init
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
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.paywall.PaywallViewModelTest"`
Expected: FAIL — `PaywallViewModel` unresolved.

- [ ] **Step 3: Implement the ViewModel**

Create `app/src/main/java/nart/simpleanki/feature/paywall/PaywallViewModel.kt`:
```kotlin
package nart.simpleanki.feature.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nart.simpleanki.core.billing.EntitlementRepository
import nart.simpleanki.core.billing.PlanOption
import nart.simpleanki.core.billing.PremiumTier
import nart.simpleanki.core.billing.PurchaseResult

data class PaywallUiState(
    val plans: List<PlanOption> = emptyList(),
    val selected: PremiumTier = PremiumTier.Annual,
    val loading: Boolean = true,
    val isPremium: Boolean = false,
    val purchasing: Boolean = false,
    val result: PurchaseResult? = null,
)

/** Backs the paywall: exposes store plans + entitlement and drives purchase / restore. */
class PaywallViewModel(
    private val repository: EntitlementRepository,
) : ViewModel() {

    private val selected = MutableStateFlow(PremiumTier.Annual)
    private val purchasing = MutableStateFlow(false)
    private val result = MutableStateFlow<PurchaseResult?>(null)

    val uiState: StateFlow<PaywallUiState> =
        combine(repository.plans, repository.entitlement, selected, purchasing, result) {
            plans, entitlement, sel, isPurchasing, res ->
            PaywallUiState(
                plans = plans,
                // Keep the user's choice if the selected tier exists; else fall back to Annual/first.
                selected = plans.firstOrNull { it.tier == sel }?.tier
                    ?: plans.firstOrNull { it.tier == PremiumTier.Annual }?.tier
                    ?: plans.firstOrNull()?.tier ?: sel,
                loading = plans.isEmpty() && !isPurchasing,
                isPremium = entitlement.isPremium,
                purchasing = isPurchasing,
                result = res,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaywallUiState())

    init { viewModelScope.launch { repository.refresh() } }

    fun select(tier: PremiumTier) { selected.value = tier }

    fun purchase(activity: Activity) {
        val plan = uiState.value.plans.firstOrNull { it.tier == selected.value } ?: return
        if (purchasing.value) return
        viewModelScope.launch {
            purchasing.value = true
            result.value = repository.purchase(activity, plan)
            purchasing.value = false
        }
    }

    fun restore() {
        viewModelScope.launch {
            purchasing.value = true
            result.value = repository.restore()
            purchasing.value = false
        }
    }

    fun clearResult() { result.value = null }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.paywall.PaywallViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/paywall/PaywallViewModel.kt app/src/test/java/nart/simpleanki/feature/paywall/PaywallViewModelTest.kt
git commit -m "Add PaywallViewModel driving plans, purchase, and restore"
```

---

## Task 5: PaywallScreen + Dark Luxe PaywallContent (Compose)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/feature/paywall/PaywallScreen.kt`
- Test: `app/src/androidTest/java/nart/simpleanki/feature/paywall/PaywallContentTest.kt`

- [ ] **Step 1: Implement the screen + stateless content**

Create `app/src/main/java/nart/simpleanki/feature/paywall/PaywallScreen.kt`:
```kotlin
package nart.simpleanki.feature.paywall

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nart.simpleanki.core.billing.PlanOption
import nart.simpleanki.core.billing.PlanPricing
import nart.simpleanki.core.billing.PremiumTier
import nart.simpleanki.core.billing.PurchaseResult
import org.koin.androidx.compose.koinViewModel

// Dark Luxe palette
private val Bg = Color(0xFF0E1020)
private val Ink = Color(0xFFEDEEF7)
private val Muted = Color(0xFF9A9CB5)
private val AccentStart = Color(0xFF7C5CFF)
private val AccentEnd = Color(0xFFFF5BA6)
private val accentBrush = Brush.linearGradient(listOf(AccentStart, AccentEnd))

@Composable
fun PaywallScreen(onClose: () -> Unit, viewModel: PaywallViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? Activity
    // Close automatically once premium is unlocked (side effect, not during composition).
    LaunchedEffect(state.isPremium, state.result) {
        if (state.isPremium && state.result == PurchaseResult.Success) onClose()
    }
    PaywallContent(
        state = state,
        onClose = onClose,
        onSelect = viewModel::select,
        onPurchase = { activity?.let(viewModel::purchase) },
        onRestore = viewModel::restore,
    )
}

/** Stateless Dark Luxe paywall, always dark regardless of app theme. */
@Composable
fun PaywallContent(
    state: PaywallUiState,
    onClose: () -> Unit = {},
    onSelect: (PremiumTier) -> Unit = {},
    onPurchase: () -> Unit = {},
    onRestore: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Muted)
            }
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(accentBrush),
                contentAlignment = Alignment.Center,
            ) { Text("👑", fontSize = 30.sp) }
            Spacer(Modifier.height(16.dp))
            Text("Azri Premium", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Text("Unlock cloud sync & backup", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))

            FeatureRow("Continuous cloud sync")
            FeatureRow("Every device, always current")
            FeatureRow("Safe, encrypted backup")
            Spacer(Modifier.height(20.dp))

            if (state.loading) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentStart)
                }
            } else {
                val monthly = state.plans.firstOrNull { it.tier == PremiumTier.Monthly }
                state.plans.forEach { plan ->
                    PlanRow(
                        plan = plan,
                        selected = plan.tier == state.selected,
                        monthlyMicros = monthly?.priceAmountMicros ?: 0L,
                        onClick = { onSelect(plan.tier) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onPurchase,
                enabled = !state.purchasing && state.plans.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentStart, contentColor = Color.White),
            ) { Text(if (state.purchasing) "Processing…" else "Continue", fontWeight = FontWeight.Bold) }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onRestore) { Text("Restore purchase", color = Muted) }
            }
            Text(
                "Subscriptions renew automatically until cancelled. Terms · Privacy.",
                color = Muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(50)).background(accentBrush), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.size(10.dp))
        Text(text, color = Ink, fontSize = 14.sp)
    }
}

@Composable
private fun PlanRow(plan: PlanOption, selected: Boolean, monthlyMicros: Long, onClick: () -> Unit) {
    val border = if (selected) BorderStroke(2.dp, AccentStart) else BorderStroke(1.dp, Color(0x22FFFFFF))
    val perMonth = PlanPricing.perMonthMicros(plan.tier, plan.priceAmountMicros)
    val savings = if (plan.tier == PremiumTier.Annual) PlanPricing.annualSavingsPercent(monthlyMicros, plan.priceAmountMicros) else 0
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0x267C5CFF) else Color(0x0DFFFFFF))
            .border(border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(planLabel(plan.tier), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(planSubtitle(plan.tier, plan.formattedPrice), color = Muted, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (savings > 0) {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(accentBrush).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text("BEST VALUE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(3.dp))
            }
            Text(perMonth?.let { "${formatMicros(it)}/mo" } ?: plan.formattedPrice, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

private fun planLabel(t: PremiumTier) = when (t) {
    PremiumTier.Monthly -> "Monthly"; PremiumTier.Annual -> "Annual"
    PremiumTier.Lifetime -> "Lifetime"; PremiumTier.None -> ""
}
private fun planSubtitle(t: PremiumTier, price: String) = when (t) {
    PremiumTier.Annual -> "$price / year"; PremiumTier.Monthly -> "Billed monthly"
    PremiumTier.Lifetime -> "$price · pay once"; PremiumTier.None -> ""
}
private fun formatMicros(micros: Long): String = "$" + String.format("%.2f", micros / 1_000_000.0)
```

- [ ] **Step 2: Write the Compose test**

Create `app/src/androidTest/java/nart/simpleanki/feature/paywall/PaywallContentTest.kt`:
```kotlin
package nart.simpleanki.feature.paywall

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nart.simpleanki.core.billing.FakeEntitlementRepository
import nart.simpleanki.core.billing.PremiumTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PaywallContentTest {
    @get:Rule val rule = createComposeRule()

    private val loaded = PaywallUiState(
        plans = FakeEntitlementRepository.DEFAULT_PLANS,
        selected = PremiumTier.Annual,
        loading = false,
    )

    @Test fun showsPlans_andTitle() {
        rule.setContent { PaywallContent(state = loaded) }
        rule.onNodeWithText("Azri Premium").assertIsDisplayed()
        rule.onNodeWithText("Annual").assertIsDisplayed()
        rule.onNodeWithText("Monthly").assertIsDisplayed()
        rule.onNodeWithText("Lifetime").assertIsDisplayed()
        rule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test fun tappingPlan_selectsIt() {
        var picked: PremiumTier? = null
        rule.setContent { PaywallContent(state = loaded, onSelect = { picked = it }) }
        rule.onNodeWithText("Lifetime").performClick()
        assertEquals(PremiumTier.Lifetime, picked)
    }

    @Test fun continueAndRestore_fireCallbacks() {
        var bought = false; var restored = false
        rule.setContent { PaywallContent(state = loaded, onPurchase = { bought = true }, onRestore = { restored = true }) }
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("Restore purchase").performClick()
        assertTrue(bought && restored)
    }
}
```

- [ ] **Step 3: Verify compile + run the Compose test on the emulator**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin && ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=nart.simpleanki.feature.paywall.PaywallContentTest`
Expected: `BUILD SUCCESSFUL`, 3 tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/paywall/PaywallScreen.kt app/src/androidTest/java/nart/simpleanki/feature/paywall/PaywallContentTest.kt
git commit -m "Add Dark Luxe paywall screen and Compose tests"
```

---

## Task 6: Wire the paywall into navigation + Koin

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Modify: `app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt`

- [ ] **Step 1: Register the ViewModel in Koin**

In `AppModule.kt`, add the import:
```kotlin
import nart.simpleanki.feature.paywall.PaywallViewModel
```
In the `// Feature ViewModels` section add:
```kotlin
    viewModel { PaywallViewModel(get()) }
```

- [ ] **Step 2: Add the paywall route**

In `AzriNavHost.kt`, add the import:
```kotlin
import nart.simpleanki.feature.paywall.PaywallScreen
```
Add a route (alongside the other top-level `composable` entries, e.g. after the `notifications` route):
```kotlin
            composable("paywall") {
                PaywallScreen(onClose = { nav.popBackStack() })
            }
```

(The Profile screen's `onOpenPaywall` wiring is added in Task 8, where the `ProfileScreen` parameter itself is introduced. The Today-screen `StudyQueueScreen(...)` wiring is added in Task 7. This task only adds the route + ViewModel.)

- [ ] **Step 3: Verify compile**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nart/simpleanki/di/AppModule.kt app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt
git commit -m "Register paywall route and ViewModel"
```

---

## Task 7: Today-screen premium nudge

**Files:**
- Modify: `core/data/settings/SettingsRepository.kt`
- Modify: `app/src/test/java/nart/simpleanki/core/data/settings/FakeSettingsRepository.kt`
- Modify: `feature/queue/StudyQueueViewModel.kt`
- Modify: `feature/queue/StudyQueueScreen.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/queue/StudyQueueViewModelTest.kt` (add a case)
- Test: `app/src/androidTest/java/nart/simpleanki/feature/queue/StudyQueueContentTest.kt` (add a case)

- [ ] **Step 1: Add the dismissed flag to settings**

In `SettingsRepository.kt`:
- In `data class AppSettings`, after `queueShuffleSeed`, add:
```kotlin
    val premiumNudgeDismissed: Boolean = false,
```
- In the interface, add:
```kotlin
    suspend fun setPremiumNudgeDismissed(dismissed: Boolean)
```
- In the DataStore `map { prefs -> AppSettings(...) }`, add the read:
```kotlin
            premiumNudgeDismissed = prefs[PREMIUM_NUDGE_DISMISSED] ?: false,
```
- Add the setter override:
```kotlin
    override suspend fun setPremiumNudgeDismissed(dismissed: Boolean) {
        context.settingsDataStore.edit { it[PREMIUM_NUDGE_DISMISSED] = dismissed }
    }
```
- In the `companion object`, add the key:
```kotlin
        val PREMIUM_NUDGE_DISMISSED = booleanPreferencesKey("premium_nudge_dismissed")
```

- [ ] **Step 2: Mirror the setter in the fake**

In `app/src/test/java/nart/simpleanki/core/data/settings/FakeSettingsRepository.kt`, add:
```kotlin
    override suspend fun setPremiumNudgeDismissed(dismissed: Boolean) {
        state.value = state.value.copy(premiumNudgeDismissed = dismissed)
    }
```

- [ ] **Step 3: Write the failing ViewModel test**

In `app/src/test/java/nart/simpleanki/feature/queue/StudyQueueViewModelTest.kt`, add the import:
```kotlin
import nart.simpleanki.core.billing.Entitlement
import nart.simpleanki.core.billing.FakeEntitlementRepository
import nart.simpleanki.core.billing.PremiumTier
```
and a test:
```kotlin
    @Test
    fun premiumNudge_showsForFreeUserWithCards_hiddenForPremium() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "Alpha", dateCreated = now, lastModified = now))
        cardRepo.upsert(review("a1", "A"))

        val free = FakeEntitlementRepository(Entitlement(PremiumTier.None))
        val vm = StudyQueueViewModel(cardRepo, deckRepo, FolderRepository(FakeFolderDao(), now = { now }), FakeSettingsRepository(), free, now = { now })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertTrue(vm.uiState.value.showPremiumNudge)

        val premium = FakeEntitlementRepository(Entitlement(PremiumTier.Annual))
        val vm2 = StudyQueueViewModel(cardRepo, deckRepo, FolderRepository(FakeFolderDao(), now = { now }), FakeSettingsRepository(), premium, now = { now })
        backgroundScope.launch { vm2.uiState.collect {} }
        runCurrent()
        assertFalse(vm2.uiState.value.showPremiumNudge)
    }
```

- [ ] **Step 4: Run it to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.queue.StudyQueueViewModelTest"`
Expected: FAIL — `StudyQueueViewModel` constructor has no entitlement param / `showPremiumNudge` unresolved.

- [ ] **Step 5: Wire entitlement into StudyQueueViewModel**

In `feature/queue/StudyQueueViewModel.kt`:
- Add imports:
```kotlin
import nart.simpleanki.core.billing.EntitlementRepository
import nart.simpleanki.core.billing.Entitlements
```
- Add `showPremiumNudge` to `StudyQueueUiState`:
```kotlin
    val showPremiumNudge: Boolean = false,
```
- Add the constructor param (after `settingsRepository`):
```kotlin
    private val entitlementRepository: EntitlementRepository,
```
- Add `entitlementRepository.entitlement` as a 5th flow. kotlinx.coroutines provides a typed 5-arg `combine`, so just extend the existing header. Replace:
```kotlin
        combine(
            cardRepository.observeAllCards(),
            deckRepository.observeDecks(),
            folderRepository.observeFolders(),
            settingsRepository.settings,
        ) { cards, decks, folders, settings ->
```
  with:
```kotlin
        combine(
            cardRepository.observeAllCards(),
            deckRepository.observeDecks(),
            folderRepository.observeFolders(),
            settingsRepository.settings,
            entitlementRepository.entitlement,
        ) { cards, decks, folders, settings, entitlement ->
```
  (Keep the existing body unchanged; `entitlement` is now in scope for the new field below.)
- In the returned `StudyQueueUiState(...)`, add:
```kotlin
                showPremiumNudge = Entitlements.shouldShowPremiumNudge(
                    isPremium = entitlement.isPremium,
                    dismissed = settings.premiumNudgeDismissed,
                    hasAnyCards = cards.any { !it.isDeleted },
                ),
```
- Add a dismissal action:
```kotlin
    fun dismissPremiumNudge() = viewModelScope.launch { settingsRepository.setPremiumNudgeDismissed(true) }
```

- [ ] **Step 6: Update the Koin binding for the new param**

In `AppModule.kt`, update the StudyQueueViewModel factory:
```kotlin
    viewModel { StudyQueueViewModel(cardRepository = get(), deckRepository = get(), folderRepository = get(), settingsRepository = get(), entitlementRepository = get()) }
```

- [ ] **Step 7: Run the ViewModel test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.queue.StudyQueueViewModelTest"`
Expected: PASS. (If other StudyQueueViewModel tests fail to compile because the constructor changed, add `FakeEntitlementRepository()` as the new argument in each existing `StudyQueueViewModel(...)` construction in that test file.)

- [ ] **Step 8: Add the nudge banner UI**

In `feature/queue/StudyQueueScreen.kt`:
- Add a callback param to `StudyQueueContent`:
```kotlin
    onOpenPaywall: () -> Unit = {},
    onDismissNudge: () -> Unit = {},
```
- In `StudyQueueScreen`, pass them through: `onOpenPaywall = onOpenPaywall` (new param on `StudyQueueScreen` too) and `onDismissNudge = viewModel::dismissPremiumNudge`. Add `onOpenPaywall: () -> Unit` to `StudyQueueScreen`'s signature.
- In the `LazyColumn`, as the FIRST `item {}` (above the daily-goal card), add:
```kotlin
            if (state.showPremiumNudge) {
                item { PremiumNudgeCard(onClick = onOpenPaywall, onDismiss = onDismissNudge) }
            }
```
- Add the composable:
```kotlin
@Composable
private fun PremiumNudgeCard(onClick: () -> Unit, onDismiss: () -> Unit) {
    AzriCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("☁️", modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text("Back up your cards", style = MaterialTheme.typography.titleSmall)
                Text("Sync across devices with Premium", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
```
  Add imports as needed: `androidx.compose.material.icons.filled.Close`, `androidx.compose.material3.IconButton`.
- Wire `onOpenPaywall` from `AzriNavHost.kt`'s `StudyQueueScreen(...)` call: add `onOpenPaywall = { nav.navigate("paywall") }`.

- [ ] **Step 9: Add the Compose test for the nudge**

In `app/src/androidTest/java/nart/simpleanki/feature/queue/StudyQueueContentTest.kt`, add:
```kotlin
    @Test
    fun premiumNudge_visible_andOpensPaywall() {
        var opened = false
        composeRule.setContent {
            StudyQueueContent(
                state = StudyQueueUiState(loading = false, hasAnyCards = true, showPremiumNudge = true),
                onStudyAll = {},
                onOpenPaywall = { opened = true },
            )
        }
        composeRule.onNodeWithText("Back up your cards").assertIsDisplayed().performClick()
        assertTrue(opened)
    }
```

- [ ] **Step 10: Run both test suites**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.queue.*" && ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=nart.simpleanki.feature.queue.StudyQueueContentTest`
Expected: all pass.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/data/settings/ app/src/test/java/nart/simpleanki/core/data/settings/ app/src/main/java/nart/simpleanki/feature/queue/ app/src/test/java/nart/simpleanki/feature/queue/ app/src/androidTest/java/nart/simpleanki/feature/queue/ app/src/main/java/nart/simpleanki/di/AppModule.kt
git commit -m "Add dismissible Today-screen premium nudge"
```

---

## Task 8: Premium-aware Profile (Cloud sync row, Azri Premium row, Restore)

**Files:**
- Modify: `feature/profile/ProfileViewModel.kt`
- Modify: `feature/profile/ProfileScreen.kt`
- Modify: `di/AppModule.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/profile/ProfileViewModelTest.kt` (create)

- [ ] **Step 1: Write the failing ViewModel test**

Create `app/src/test/java/nart/simpleanki/feature/profile/ProfileViewModelTest.kt`:
```kotlin
package nart.simpleanki.feature.profile

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.auth.AuthRepository
import nart.simpleanki.auth.AuthUser
import nart.simpleanki.core.billing.Entitlement
import nart.simpleanki.core.billing.FakeEntitlementRepository
import nart.simpleanki.core.billing.PremiumTier
import nart.simpleanki.core.data.settings.FakeSettingsRepository
import org.junit.After
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
}
```

> If `AuthUser` is not a simple constructible type, replace `auth(null)` usage with a relaxed mock returning `flowOf(null)` (already shown). Confirm `AuthUser`'s package import path before running.

- [ ] **Step 2: Run it to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.profile.ProfileViewModelTest"`
Expected: FAIL — `ProfileViewModel` has no entitlement param / `isPremium` unresolved.

- [ ] **Step 3: Add entitlement to ProfileViewModel**

In `feature/profile/ProfileViewModel.kt`:
- Add imports:
```kotlin
import nart.simpleanki.core.billing.EntitlementRepository
```
- Add `val isPremium: Boolean = false` to `ProfileUiState`.
- Add constructor param `private val entitlementRepository: EntitlementRepository`.
- Change `combine(settingsRepository.settings, authRepository.authState) { settings, user ->` to include entitlement:
```kotlin
        combine(settingsRepository.settings, authRepository.authState, entitlementRepository.entitlement) { settings, user, entitlement ->
            ProfileUiState(
                email = user?.email,
                isAnonymous = user?.isAnonymous ?: true,
                preset = settings.preset,
                themeMode = settings.themeMode,
                isPremium = entitlement.isPremium,
            )
        }
```
- Add a restore action:
```kotlin
    fun restorePurchases() = viewModelScope.launch { entitlementRepository.restore() }
```

- [ ] **Step 4: Update Koin binding**

In `AppModule.kt`:
```kotlin
    viewModel { ProfileViewModel(settingsRepository = get(), authRepository = get(), entitlementRepository = get()) }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.profile.ProfileViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Update the Profile UI rows**

In `feature/profile/ProfileScreen.kt`:
- Add params to `ProfileScreen`: `onOpenPaywall: () -> Unit` (stateful screen) and thread to content. Also expose `onRestore` from the viewModel.
- Replace the existing **Cloud sync** `ListItem` (lines ~174-184) with a premium-aware version:
```kotlin
            ListItem(
                headlineContent = { Text("Cloud sync") },
                supportingContent = {
                    Text(
                        when {
                            state.isPremium && !state.isAnonymous -> "Synced"
                            state.isPremium && state.isAnonymous -> "Sign in to start syncing"
                            else -> "Off — tap to back up"
                        }
                    )
                },
                leadingContent = {
                    Icon(
                        if (state.isPremium && !state.isAnonymous) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = null,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = if (state.isPremium) Modifier else Modifier.clickable(onClick = onOpenPaywall),
            )
```
- Add an **Azri Premium** row directly below it:
```kotlin
            ListItem(
                headlineContent = { Text("Azri Premium") },
                supportingContent = { Text(if (state.isPremium) "Active · thank you!" else "Unlock cloud sync & backup") },
                leadingContent = { Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = if (state.isPremium) Modifier else Modifier.clickable(onClick = onOpenPaywall),
            )
```
  Add import `androidx.compose.material.icons.filled.WorkspacePremium`.
- In the existing overflow/footer area (or under a new "Premium" `CategoryHeader`), add a **Restore purchases** row that calls `onRestore`:
```kotlin
            ListItem(
                headlineContent = { Text("Restore purchases") },
                leadingContent = { Icon(Icons.Default.Restore, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.clickable(onClick = onRestore),
            )
```
  Add import `androidx.compose.material.icons.filled.Restore`. Thread `onRestore` from `ProfileScreen` to call `viewModel::restorePurchases`.

- [ ] **Step 7: Pass callbacks from navigation**

In `AzriNavHost.kt`, the `ProfileScreen(...)` call gets:
```kotlin
                    onOpenPaywall = { nav.navigate("paywall") },
```
(matching Task 6 step 3).

- [ ] **Step 8: Verify build + Profile tests**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/profile/ app/src/test/java/nart/simpleanki/feature/profile/ app/src/main/java/nart/simpleanki/di/AppModule.kt app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt
git commit -m "Make Profile premium-aware with paywall and restore entry points"
```

---

## Task 9: Gate the existing sync behind entitlement

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/ui/AzriRoot.kt`
- Modify: `app/src/main/java/nart/simpleanki/core/data/sync/SyncWorker.kt`

- [ ] **Step 1: Gate the foreground sync loop**

In `ui/AzriRoot.kt`:
- Add imports:
```kotlin
import nart.simpleanki.core.billing.EntitlementRepository
import nart.simpleanki.core.billing.Entitlements
```
- Add a parameter: `entitlementRepository: EntitlementRepository = koinInject()`.
- Replace the `LaunchedEffect(s.user.uid) { while (true) { syncViewModel.sync(s.user.uid); delay(20_000) } }` body with a guard:
```kotlin
            LaunchedEffect(s.user.uid) {
                while (true) {
                    val premium = entitlementRepository.entitlement.value.isPremium
                    if (Entitlements.shouldSync(isPremium = premium, signedInWithGoogle = !s.user.isAnonymous)) {
                        syncViewModel.sync(s.user.uid)
                    }
                    kotlinx.coroutines.delay(20_000)
                }
            }
```

- [ ] **Step 2: Gate the background worker**

In `core/data/sync/SyncWorker.kt`:
- Add imports:
```kotlin
import nart.simpleanki.core.billing.EntitlementRepository
import nart.simpleanki.core.billing.Entitlements
```
- Add an injected dependency next to the others:
```kotlin
    private val entitlements: EntitlementRepository by inject()
```
- Replace `doWork()` body:
```kotlin
    override suspend fun doWork(): Result {
        val user = auth.currentUser ?: return Result.success()
        val signedInWithGoogle = !user.isAnonymous
        if (!Entitlements.shouldSync(entitlements.entitlement.value.isPremium, signedInWithGoogle)) {
            return Result.success() // free tier: nothing to sync
        }
        return runCatching { syncManager.sync(user.uid) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
```

- [ ] **Step 3: Verify build + full unit suite**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nart/simpleanki/ui/AzriRoot.kt app/src/main/java/nart/simpleanki/core/data/sync/SyncWorker.kt
git commit -m "Gate cloud sync behind premium entitlement"
```

---

## Task 10: Real Play Billing implementation + cache (swap the binding)

This is the only non-unit-tested unit; it's intentionally thin and verified manually on a signed build against Play Console. Until now `LocalEntitlementRepository` kept everything runnable.

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/billing/EntitlementCache.kt`
- Create: `app/src/main/java/nart/simpleanki/core/billing/PlayBillingRepository.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`

- [ ] **Step 1: Create the DataStore entitlement cache**

Create `app/src/main/java/nart/simpleanki/core/billing/EntitlementCache.kt`:
```kotlin
package nart.simpleanki.core.billing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.premiumDataStore by preferencesDataStore("azri_premium")

/** Persists the last known entitlement so Premium survives offline launches. */
class EntitlementCache(private val context: Context) {
    private val tierKey = stringPreferencesKey("premium_tier")

    val cached: Flow<Entitlement> = context.premiumDataStore.data.map { prefs ->
        val tier = prefs[tierKey]?.let { runCatching { PremiumTier.valueOf(it) }.getOrNull() } ?: PremiumTier.None
        Entitlement(tier)
    }

    suspend fun save(entitlement: Entitlement) {
        context.premiumDataStore.edit { it[tierKey] = entitlement.tier.name }
    }
}
```

- [ ] **Step 2: Implement PlayBillingRepository**

Create `app/src/main/java/nart/simpleanki/core/billing/PlayBillingRepository.kt`:
```kotlin
package nart.simpleanki.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Real Play Billing implementation. Thin and manually verified on a signed build; all app
 * logic is tested against [FakeEntitlementRepository]. Entitlement is mirrored to
 * [EntitlementCache] for offline launches.
 */
class PlayBillingRepository(
    context: Context,
    private val cache: EntitlementCache,
) : EntitlementRepository, PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entitlement = MutableStateFlow(Entitlement())
    override val entitlement: StateFlow<Entitlement> = _entitlement.asStateFlow()

    private val _plans = MutableStateFlow<List<PlanOption>>(emptyList())
    override val plans: StateFlow<List<PlanOption>> = _plans.asStateFlow()

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        // Seed from cache so Premium works before/without a Play connection.
        scope.launch { cache.cached.collect { if (!_entitlement.value.isPremium) _entitlement.value = it } }
        connect { scope.launch { refresh() } }
    }

    private fun connect(onReady: () -> Unit) {
        if (client.isReady) { onReady(); return }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) onReady()
            }
            override fun onBillingServiceDisconnected() { /* will retry on next refresh */ }
        })
    }

    override suspend fun refresh() {
        queryPlans()
        reconcilePurchases()
    }

    private suspend fun queryPlans() {
        val subParams = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(BillingProducts.SUBSCRIPTION_ID)
                    .setProductType(BillingClient.ProductType.SUBS).build(),
            ),
        ).build()
        val inappParams = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(BillingProducts.LIFETIME_ID)
                    .setProductType(BillingClient.ProductType.INAPP).build(),
            ),
        ).build()

        val out = mutableListOf<PlanOption>()
        runCatching { client.queryProductDetails(subParams).productDetailsList }
            .getOrNull()?.forEach { out += it.toSubscriptionPlans() }
        runCatching { client.queryProductDetails(inappParams).productDetailsList }
            .getOrNull()?.forEach { out += it.toLifetimePlan() }
        // Order: Annual, Monthly, Lifetime.
        _plans.value = out.sortedBy { listOf(PremiumTier.Annual, PremiumTier.Monthly, PremiumTier.Lifetime).indexOf(it.tier) }
    }

    private fun ProductDetails.toSubscriptionPlans(): List<PlanOption> =
        subscriptionOfferDetails.orEmpty().mapNotNull { offer ->
            val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
            val tier = when (offer.basePlanId) {
                BillingProducts.BASE_PLAN_ANNUAL -> PremiumTier.Annual
                BillingProducts.BASE_PLAN_MONTHLY -> PremiumTier.Monthly
                else -> return@mapNotNull null
            }
            PlanOption(tier, productId, offer.basePlanId, offer.offerToken, phase.formattedPrice, phase.priceAmountMicros)
        }

    private fun ProductDetails.toLifetimePlan(): List<PlanOption> {
        val offer = oneTimePurchaseOfferDetails ?: return emptyList()
        return listOf(PlanOption(PremiumTier.Lifetime, productId, null, null, offer.formattedPrice, offer.priceAmountMicros))
    }

    override suspend fun purchase(activity: Activity, plan: PlanOption): PurchaseResult {
        val productParams = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(plan.productId)
            .setProductType(if (plan.basePlanId == null) BillingClient.ProductType.INAPP else BillingClient.ProductType.SUBS)
            .build()
        val details = runCatching {
            client.queryProductDetails(
                QueryProductDetailsParams.newBuilder().setProductList(listOf(productParams)).build(),
            ).productDetailsList?.firstOrNull()
        }.getOrNull() ?: return PurchaseResult.Error

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { plan.offerToken?.let { setOfferToken(it) } }
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams)).build()
        val result = client.launchBillingFlow(activity, flowParams)
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> PurchaseResult.Success // final state arrives via onPurchasesUpdated
            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseResult.Cancelled
            else -> PurchaseResult.Error
        }
    }

    override suspend fun restore(): PurchaseResult {
        reconcilePurchases()
        return if (_entitlement.value.isPremium) PurchaseResult.Success else PurchaseResult.Error
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            scope.launch { purchases?.forEach { acknowledge(it) }; reconcilePurchases() }
        }
    }

    private suspend fun reconcilePurchases() {
        val subs = runCatching {
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()).purchasesList
        }.getOrDefault(emptyList())
        val inapp = runCatching {
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()).purchasesList
        }.getOrDefault(emptyList())
        val all = subs + inapp
        all.forEach { acknowledge(it) }
        val tier = when {
            inapp.any { it.products.contains(BillingProducts.LIFETIME_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED } -> PremiumTier.Lifetime
            subs.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } -> PremiumTier.Annual // tier granularity refined later
            else -> PremiumTier.None
        }
        val entitlement = Entitlement(tier)
        _entitlement.value = entitlement
        cache.save(entitlement)
    }

    private suspend fun acknowledge(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            suspendCancellableCoroutine { cont ->
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
                ) { cont.resume(Unit) }
            }
        }
    }
}
```

> **Implementer note:** Play Billing 7's KTX `queryProductDetails`/`queryPurchasesAsync` are `suspend` extensions. If the resolved API differs slightly in your billing version, adapt the suspend wrappers — the *interface contract* (StateFlows + suspend methods) must not change, so the rest of the app and all tests are unaffected.

- [ ] **Step 3: Swap the Koin binding**

In `di/AppModule.kt`:
- Replace `import nart.simpleanki.core.billing.LocalEntitlementRepository` with:
```kotlin
import nart.simpleanki.core.billing.EntitlementCache
import nart.simpleanki.core.billing.PlayBillingRepository
```
- Replace the placeholder binding line with:
```kotlin
    single { EntitlementCache(androidContext()) }
    single<EntitlementRepository> { PlayBillingRepository(androidContext(), get()) }
```
  (You may delete `LocalEntitlementRepository.kt`, or keep it for previews/tests.)

- [ ] **Step 4: Verify build + full unit suite + assemble**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (Unit tests still use the fake; the real repo is exercised manually.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/billing/ app/src/main/java/nart/simpleanki/di/AppModule.kt
git commit -m "Implement Play Billing entitlement repository and offline cache"
```

---

## Task 11: Full verification + spec doc reference

- [ ] **Step 1: Run the entire JVM suite + assemble**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew clean :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run the full instrumented suite (emulator)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:connectedDebugAndroidTest`
Expected: all pass (note: this reinstalls the app and wipes device data).

- [ ] **Step 3: Manual smoke test (signed build, Play Console)**

Configure in Play Console: subscription `azri_premium` with base plans `monthly` + `annual`, and in-app product `azri_premium_lifetime`. Upload a signed build to internal testing, add a license tester, and verify: paywall loads real prices → purchase → entitlement flips → sync begins after Google sign-in → restore on a second device.

- [ ] **Step 4: Final commit (if any docs/cleanup)**

```bash
git add -A
git commit -m "Premium paywall: final verification pass"
```

---

## Notes & deferrals (from the spec)

- **Fail-closed:** unknown entitlement ⇒ treated as free; a Play outage pauses a paying user's sync until it recovers.
- **No server-side receipt validation** in this iteration (rely on Play + acknowledgement). Future hardening.
- **Subscription tier granularity:** `reconcilePurchases` currently reports any active subscription as `Annual`. If monthly/annual must be distinguished in UI later, match the purchased base plan via the Play Developer API or stored offer token.
- **No new secrets / CI-safe:** product IDs are public; the billing layer degrades to "free" with no Play connection, so `testDebugUnitTest` and `assembleDebug` pass without Play Console.
