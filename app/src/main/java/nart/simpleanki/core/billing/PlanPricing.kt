package nart.simpleanki.core.billing

import kotlin.math.roundToInt

/** Pure price-derivation helpers operating on Play price micros (1_000_000 micros = 1 unit). */
object PlanPricing {
    /** Per-month micros for a plan, or null when not applicable (lifetime / none). */
    fun perMonthMicros(tier: PremiumTier, priceAmountMicros: Long): Long? = when (tier) {
        PremiumTier.Monthly -> priceAmountMicros
        // Annual price is whole-year; divide for a display-only per-month figure (integer-truncated).
        PremiumTier.Annual -> priceAmountMicros / 12
        PremiumTier.Lifetime -> null
        PremiumTier.None -> null
    }

    /** Whole-percent savings of annual (per month) vs monthly; 0 if monthly is missing/cheaper. */
    fun annualSavingsPercent(monthlyMicros: Long, annualMicros: Long): Int {
        if (monthlyMicros <= 0L) return 0
        val annualPerMonth = annualMicros / 12.0
        val pct = (1.0 - annualPerMonth / monthlyMicros) * 100.0
        return pct.coerceAtLeast(0.0).roundToInt()
    }
}
