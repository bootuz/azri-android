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
