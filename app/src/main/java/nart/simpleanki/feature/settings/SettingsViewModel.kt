package nart.simpleanki.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nart.simpleanki.core.data.settings.AppSettings
import nart.simpleanki.core.data.settings.SettingsRepository
import nart.simpleanki.core.domain.fsrs.FsrsPreset

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
)

/** Spaced-repetition (FSRS) settings: preset + daily limits. Account lives on the Profile tab. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        settingsRepository.settings
            .map { SettingsUiState(settings = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setPreset(preset: FsrsPreset) = viewModelScope.launch { settingsRepository.setPreset(preset) }
    fun setNewCardsPerDay(value: Int) = viewModelScope.launch { settingsRepository.setNewCardsPerDay(value) }
    fun setMaxReviewsPerDay(value: Int) = viewModelScope.launch { settingsRepository.setMaxReviewsPerDay(value) }
}
