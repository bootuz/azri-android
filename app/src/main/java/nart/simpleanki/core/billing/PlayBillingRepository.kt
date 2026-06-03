package nart.simpleanki.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Real Play Billing implementation. Thin and manually verified on a signed build; all app
 * logic is tested against [FakeEntitlementRepository]. Entitlement is mirrored to
 * [EntitlementCache] for offline launches.
 */
class PlayBillingRepository(
    context: Context,
    private val cache: EntitlementCache,
) : EntitlementRepository, PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entitlement = MutableStateFlow(Entitlement())
    override val entitlement: StateFlow<Entitlement> = _entitlement.asStateFlow()

    private val _plans = MutableStateFlow<List<PlanOption>>(emptyList())
    override val plans: StateFlow<List<PlanOption>> = _plans.asStateFlow()

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        // Seed from cache so Premium works before/without a Play connection.
        scope.launch {
            val seed = cache.cached.first()
            if (!_entitlement.value.isPremium) _entitlement.value = seed
        }
        connect { scope.launch { refresh() } }
    }

    private fun connect(onReady: () -> Unit) {
        if (client.isReady) { onReady(); return }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) onReady()
            }
            override fun onBillingServiceDisconnected() { /* will retry on next refresh */ }
        })
    }

    override suspend fun refresh() {
        queryPlans()
        reconcilePurchases()
    }

    private suspend fun queryPlans() {
        val subParams = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(BillingProducts.SUBSCRIPTION_ID)
                    .setProductType(BillingClient.ProductType.SUBS).build(),
            ),
        ).build()
        val inappParams = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(BillingProducts.LIFETIME_ID)
                    .setProductType(BillingClient.ProductType.INAPP).build(),
            ),
        ).build()

        val out = mutableListOf<PlanOption>()
        runCatching { client.queryProductDetails(subParams).productDetailsList }
            .getOrNull()?.forEach { out += it.toSubscriptionPlans() }
        runCatching { client.queryProductDetails(inappParams).productDetailsList }
            .getOrNull()?.forEach { out += it.toLifetimePlan() }
        // Order: Annual, Monthly, Lifetime.
        _plans.value = out.sortedBy { listOf(PremiumTier.Annual, PremiumTier.Monthly, PremiumTier.Lifetime).indexOf(it.tier) }
    }

    private fun ProductDetails.toSubscriptionPlans(): List<PlanOption> =
        subscriptionOfferDetails.orEmpty().mapNotNull { offer ->
            val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
            val tier = when (offer.basePlanId) {
                BillingProducts.BASE_PLAN_ANNUAL -> PremiumTier.Annual
                BillingProducts.BASE_PLAN_MONTHLY -> PremiumTier.Monthly
                else -> return@mapNotNull null
            }
            PlanOption(tier, productId, offer.basePlanId, offer.offerToken, phase.formattedPrice, phase.priceAmountMicros)
        }

    private fun ProductDetails.toLifetimePlan(): List<PlanOption> {
        val offer = oneTimePurchaseOfferDetails ?: return emptyList()
        return listOf(PlanOption(PremiumTier.Lifetime, productId, null, null, offer.formattedPrice, offer.priceAmountMicros))
    }

    override suspend fun purchase(activity: Activity, plan: PlanOption): PurchaseResult {
        val productParams = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(plan.productId)
            .setProductType(if (plan.basePlanId == null) BillingClient.ProductType.INAPP else BillingClient.ProductType.SUBS)
            .build()
        val details = runCatching {
            client.queryProductDetails(
                QueryProductDetailsParams.newBuilder().setProductList(listOf(productParams)).build(),
            ).productDetailsList?.firstOrNull()
        }.getOrNull() ?: return PurchaseResult.Error

        val offerToken = plan.basePlanId?.let { basePlanId ->
            details.subscriptionOfferDetails?.firstOrNull { it.basePlanId == basePlanId }?.offerToken
                ?: return PurchaseResult.Error
        }
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { offerToken?.let { setOfferToken(it) } }
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams)).build()
        val result = client.launchBillingFlow(activity, flowParams)
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> PurchaseResult.Success // final state arrives via onPurchasesUpdated
            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseResult.Cancelled
            else -> PurchaseResult.Error
        }
    }

    override suspend fun restore(): PurchaseResult {
        reconcilePurchases()
        return if (_entitlement.value.isPremium) PurchaseResult.Success else PurchaseResult.Error
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            scope.launch { purchases?.forEach { acknowledge(it) }; reconcilePurchases() }
        }
    }

    private suspend fun reconcilePurchases() {
        val subsResult = runCatching {
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        }.getOrNull()
        val inappResult = runCatching {
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        }.getOrNull()

        val subsOk = subsResult?.billingResult?.responseCode == BillingClient.BillingResponseCode.OK
        val inappOk = inappResult?.billingResult?.responseCode == BillingClient.BillingResponseCode.OK
        // Total failure (offline / service down): leave entitlement + cache untouched — never
        // downgrade a paying user on a transient error.
        if (!subsOk && !inappOk) return

        val subs = if (subsOk) subsResult.purchasesList else emptyList()
        val inapp = if (inappOk) inappResult.purchasesList else emptyList()
        (subs + inapp).forEach { acknowledge(it) }

        val ownsLifetime = inapp.any { it.products.contains(BillingProducts.LIFETIME_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val hasActiveSub = subs.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val tier = when {
            ownsLifetime -> PremiumTier.Lifetime
            hasActiveSub -> PremiumTier.Annual            // tier granularity refined later
            subsOk && inappOk -> PremiumTier.None         // both queries succeeded → definitively free
            else -> return                                // partial failure, no premium found yet → don't downgrade
        }
        val entitlement = Entitlement(tier)
        _entitlement.value = entitlement
        cache.save(entitlement)
    }

    private suspend fun acknowledge(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            suspendCancellableCoroutine { cont ->
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
                ) { cont.resume(Unit) }
            }
        }
    }
}
