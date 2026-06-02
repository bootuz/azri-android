package nart.simpleanki.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nart.simpleanki.auth.AuthRepository
import nart.simpleanki.core.data.settings.SettingsRepository
import nart.simpleanki.core.data.settings.ThemeMode
import nart.simpleanki.core.domain.fsrs.FsrsPreset

data class ProfileUiState(
    val email: String? = null,
    val isAnonymous: Boolean = true,
    val preset: FsrsPreset = FsrsPreset.Optimal,
    val themeMode: ThemeMode = ThemeMode.System,
)

/** The Profile tab: account info, appearance, and the entry point to spaced-repetition settings. */
class ProfileViewModel(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> =
        combine(settingsRepository.settings, authRepository.authState) { settings, user ->
            ProfileUiState(
                email = user?.email,
                isAnonymous = user?.isAnonymous ?: true,
                preset = settings.preset,
                themeMode = settings.themeMode,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun signOut() = authRepository.signOut()
    fun deleteAccount() = viewModelScope.launch { authRepository.deleteAccount() }
}
