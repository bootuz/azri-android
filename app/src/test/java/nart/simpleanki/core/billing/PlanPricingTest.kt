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
        assertEquals(44, PlanPricing.annualSavingsPercent(monthlyMicros = 2_990_000L, annualMicros = 19_990_000L))
        assertEquals(0, PlanPricing.annualSavingsPercent(monthlyMicros = 0L, annualMicros = 19_990_000L))
        assertEquals(0, PlanPricing.annualSavingsPercent(monthlyMicros = 1_000_000L, annualMicros = 24_000_000L))
    }
}
