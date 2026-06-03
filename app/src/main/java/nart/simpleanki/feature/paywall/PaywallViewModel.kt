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

    val uiState: StateFlow<PaywallUiState> =
        combine(repository.plans, repository.entitlement, selected, purchasing, result) {
            plans, entitlement, sel, isPurchasing, res ->
            PaywallUiState(
                plans = plans,
                selected = plans.firstOrNull { it.tier == sel }?.tier
                    ?: plans.firstOrNull { it.tier == PremiumTier.Annual }?.tier
                    ?: plans.firstOrNull()?.tier ?: sel,
                loading = plans.isEmpty() && !isPurchasing,
                isPremium = entitlement.isPremium,
                purchasing = isPurchasing,
                result = res,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaywallUiState())

    init { viewModelScope.launch { repository.refresh() } }

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

    fun clearResult() { result.value = null }
}
