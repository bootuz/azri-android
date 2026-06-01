package nart.simpleanki.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nart.simpleanki.auth.AuthRepository
import nart.simpleanki.core.data.settings.AppSettings
import nart.simpleanki.core.data.settings.SettingsRepository
import nart.simpleanki.core.domain.fsrs.FsrsPreset

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val email: String? = null,
    val uid: String = "",
    val isAnonymous: Boolean = true,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.settings, authRepository.authState) { settings, user ->
            SettingsUiState(
                settings = settings,
                email = user?.email,
                uid = user?.uid.orEmpty(),
                isAnonymous = user?.isAnonymous ?: true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setPreset(preset: FsrsPreset) = viewModelScope.launch { settingsRepository.setPreset(preset) }
    fun setNewCardsPerDay(value: Int) = viewModelScope.launch { settingsRepository.setNewCardsPerDay(value) }
    fun setMaxReviewsPerDay(value: Int) = viewModelScope.launch { settingsRepository.setMaxReviewsPerDay(value) }
    fun signOut() = authRepository.signOut()
    fun deleteAccount() = viewModelScope.launch { authRepository.deleteAccount() }
}
