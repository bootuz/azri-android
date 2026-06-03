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
