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
