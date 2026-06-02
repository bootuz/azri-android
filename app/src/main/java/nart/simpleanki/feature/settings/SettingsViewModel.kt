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

/** Spaced-repetition (FSRS) settings: preset choice + custom parameters. Account lives on Profile. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        settingsRepository.settings
            .map { SettingsUiState(settings = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setPreset(preset: FsrsPreset) = viewModelScope.launch { settingsRepository.setPreset(preset) }
    fun setCustomRetention(value: Double) = viewModelScope.launch { settingsRepository.setCustomRetention(value) }
    fun setCustomMaxInterval(days: Int) = viewModelScope.launch { settingsRepository.setCustomMaxInterval(days) }
    fun setEnableFuzz(enabled: Boolean) = viewModelScope.launch { settingsRepository.setEnableFuzz(enabled) }
    fun setEnableShortTerm(enabled: Boolean) = viewModelScope.launch { settingsRepository.setEnableShortTerm(enabled) }

    /** Reset to the Default (Optimal) preset and restore default custom parameters. */
    fun resetToDefaults() = viewModelScope.launch {
        settingsRepository.setPreset(FsrsPreset.Optimal)
        settingsRepository.setCustomRetention(0.90)
        settingsRepository.setCustomMaxInterval(365)
        settingsRepository.setEnableFuzz(true)
        settingsRepository.setEnableShortTerm(true)
    }
}
