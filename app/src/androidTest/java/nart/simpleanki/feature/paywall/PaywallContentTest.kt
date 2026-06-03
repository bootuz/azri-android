package nart.simpleanki.feature.paywall

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nart.simpleanki.core.billing.PlanOption
import nart.simpleanki.core.billing.PremiumTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PaywallContentTest {
    @get:Rule val rule = createComposeRule()

    // FakeEntitlementRepository lives in unit-test sources (src/test/) and is not visible
    // from androidTest sources. Plans are inlined here to match DEFAULT_PLANS exactly.
    private val loaded = PaywallUiState(
        plans = listOf(
            PlanOption(PremiumTier.Annual, "azri_premium", "annual", "tokA", "$19.99", 19_990_000L),
            PlanOption(PremiumTier.Monthly, "azri_premium", "monthly", "tokM", "$2.99", 2_990_000L),
            PlanOption(PremiumTier.Lifetime, "azri_premium_lifetime", null, null, "$49.99", 49_990_000L),
        ),
        selected = PremiumTier.Annual,
        loading = false,
    )

    @Test fun showsPlans_andTitle() {
        rule.setContent { PaywallContent(state = loaded) }
        rule.onNodeWithText("Unlock Cloud Sync").assertIsDisplayed()
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

    @Test fun plansUnavailable_showsRetry_andFiresCallback() {
        var retried = false
        rule.setContent {
            PaywallContent(
                state = PaywallUiState(loading = false, plansUnavailable = true),
                onRetry = { retried = true },
            )
        }
        rule.onNodeWithText("Plans unavailable").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()
        assertTrue(retried)
    }
}
