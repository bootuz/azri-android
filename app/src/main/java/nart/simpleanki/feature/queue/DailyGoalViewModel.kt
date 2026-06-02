package nart.simpleanki.feature.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nart.simpleanki.core.data.settings.SettingsRepository

data class DailyGoalUiState(
    val enabled: Boolean = true,
    val newCardsTarget: Int = 10,
    val reviewCardsTarget: Int = 20,
) {
    val total: Int get() = newCardsTarget + reviewCardsTarget
}

/** Backs the daily-goal editor sheet: a goal-tracking toggle plus the two bucket targets. */
class DailyGoalViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<DailyGoalUiState> =
        settingsRepository.settings
            .map {
                DailyGoalUiState(
                    enabled = it.dailyGoalEnabled,
                    newCardsTarget = it.newCardsTarget,
                    reviewCardsTarget = it.reviewCardsTarget,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyGoalUiState())

    fun setEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDailyGoalEnabled(enabled) }
    fun setNewCardsTarget(value: Int) = viewModelScope.launch { settingsRepository.setNewCardsTarget(value) }
    fun setReviewCardsTarget(value: Int) = viewModelScope.launch { settingsRepository.setReviewCardsTarget(value) }

    /** Reset goal tracking on with the iOS default 10 new + 20 review buckets. */
    fun resetToDefaults() = viewModelScope.launch {
        settingsRepository.setDailyGoalEnabled(true)
        settingsRepository.setNewCardsTarget(10)
        settingsRepository.setReviewCardsTarget(20)
    }
}
