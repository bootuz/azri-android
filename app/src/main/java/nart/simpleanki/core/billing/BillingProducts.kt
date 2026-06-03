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
