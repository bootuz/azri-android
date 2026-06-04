package nart.simpleanki.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nart.simpleanki.auth.AuthRepository
import nart.simpleanki.core.billing.EntitlementRepository
import nart.simpleanki.core.billing.PurchaseResult
import nart.simpleanki.core.data.settings.SettingsRepository
import nart.simpleanki.core.data.settings.ThemeMode
import nart.simpleanki.core.data.settings.dailyGoalTotal
import nart.simpleanki.core.domain.fsrs.FsrsPreset

data class ProfileUiState(
    val email: String? = null,
    val isAnonymous: Boolean = true,
    val preset: FsrsPreset = FsrsPreset.Optimal,
    val themeMode: ThemeMode = ThemeMode.System,
    val isPremium: Boolean = false,
    val dailyGoalEnabled: Boolean = false,
    val dailyGoalTotal: Int = 0,
)

/** The Profile tab: account info, appearance, and the entry point to spaced-repetition settings. */
class ProfileViewModel(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val entitlementRepository: EntitlementRepository,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> =
        combine(settingsRepository.settings, authRepository.authState, entitlementRepository.entitlement) { settings, user, entitlement ->
            ProfileUiState(
                email = user?.email,
                isAnonymous = user?.isAnonymous ?: true,
                preset = settings.preset,
                themeMode = settings.themeMode,
                isPremium = entitlement.isPremium,
                dailyGoalEnabled = settings.dailyGoalEnabled,
                dailyGoalTotal = settings.dailyGoalTotal,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun signOut() = authRepository.signOut()
    fun deleteAccount() = viewModelScope.launch { authRepository.deleteAccount() }
    fun restorePurchases(onResult: (PurchaseResult) -> Unit = {}) = viewModelScope.launch {
        onResult(entitlementRepository.restore())
    }
}
