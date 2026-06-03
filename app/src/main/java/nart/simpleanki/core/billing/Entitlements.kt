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
