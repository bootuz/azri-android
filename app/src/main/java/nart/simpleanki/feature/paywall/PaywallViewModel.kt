package nart.simpleanki.feature.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nart.simpleanki.core.billing.EntitlementRepository
import nart.simpleanki.core.billing.PlanOption
import nart.simpleanki.core.billing.PremiumTier
import nart.simpleanki.core.billing.PurchaseResult

data class PaywallUiState(
    val plans: List<PlanOption> = emptyList(),
    val selected: PremiumTier = PremiumTier.Annual,
    val loading: Boolean = true,
    /** A load finished but the store returned no plans (offline, Play unavailable, or not installed from Play). */
    val plansUnavailable: Boolean = false,
    val isPremium: Boolean = false,
    val purchasing: Boolean = false,
    val result: PurchaseResult? = null,
)

/** Backs the paywall: exposes store plans + entitlement and drives purchase / restore. */
class PaywallViewModel(
    private val repository: EntitlementRepository,
) : ViewModel() {

    private val selected = MutableStateFlow(PremiumTier.Annual)
    private val purchasing = MutableStateFlow(false)
    private val result = MutableStateFlow<PurchaseResult?>(null)
    /** False until the first plan-load attempt completes — drives the initial spinner. */
    private val loadAttempted = MutableStateFlow(false)

    private data class Bits(
        val selected: PremiumTier,
        val purchasing: Boolean,
        val result: PurchaseResult?,
        val attempted: Boolean,
    )

    val uiState: StateFlow<PaywallUiState> =
        combine(
            repository.plans,
            repository.entitlement,
            combine(selected, purchasing, result, loadAttempted) { s, p, r, a -> Bits(s, p, r, a) },
        ) { plans, entitlement, bits ->
            PaywallUiState(
                plans = plans,
                selected = plans.firstOrNull { it.tier == bits.selected }?.tier
                    ?: plans.firstOrNull { it.tier == PremiumTier.Annual }?.tier
                    ?: plans.firstOrNull()?.tier ?: bits.selected,
                // Spinner only until the first load attempt finishes; afterwards an empty list
                // means "unavailable", not "still loading".
                loading = !bits.attempted,
                plansUnavailable = bits.attempted && plans.isEmpty(),
                isPremium = entitlement.isPremium,
                purchasing = bits.purchasing,
                result = bits.result,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaywallUiState())

    /** (Re)load plans from the store. Called when the sheet opens and from the Retry button. */
    fun retry() {
        viewModelScope.launch {
            loadAttempted.value = false
            repository.refresh()
            loadAttempted.value = true
        }
    }

    fun select(tier: PremiumTier) { selected.value = tier }

    fun purchase(activity: Activity) {
        val plan = repository.plans.value.firstOrNull { it.tier == selected.value } ?: return
        if (purchasing.value) return
        viewModelScope.launch {
            purchasing.value = true
            result.value = repository.purchase(activity, plan)
            purchasing.value = false
        }
    }

    fun restore() {
        if (purchasing.value) return
        viewModelScope.launch {
            purchasing.value = true
            result.value = repository.restore()
            purchasing.value = false
        }
    }
}
