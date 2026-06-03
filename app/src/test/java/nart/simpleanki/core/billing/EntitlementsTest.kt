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
