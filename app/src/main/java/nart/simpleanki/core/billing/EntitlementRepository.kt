package nart.simpleanki.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for premium state and available plans. Implementations:
 * [PlayBillingRepository] (real) and FakeEntitlementRepository (tests).
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
