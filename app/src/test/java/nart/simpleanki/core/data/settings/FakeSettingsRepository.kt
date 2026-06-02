package nart.simpleanki.core.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nart.simpleanki.core.domain.fsrs.FsrsPreset

/** In-memory [SettingsRepository] for unit tests. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun setPreset(preset: FsrsPreset) { state.value = state.value.copy(preset = preset) }
    override suspend fun setNewCardsPerDay(value: Int) { state.value = state.value.copy(newCardsPerDay = value) }
    override suspend fun setMaxReviewsPerDay(value: Int) { state.value = state.value.copy(maxReviewsPerDay = value) }
    override suspend fun setThemeMode(mode: ThemeMode) { state.value = state.value.copy(themeMode = mode) }
    override suspend fun setCustomRetention(value: Double) { state.value = state.value.copy(customRetention = value) }
    override suspend fun setCustomMaxInterval(days: Int) { state.value = state.value.copy(customMaxInterval = days) }
    override suspend fun setEnableFuzz(enabled: Boolean) { state.value = state.value.copy(enableFuzz = enabled) }
    override suspend fun setEnableShortTerm(enabled: Boolean) { state.value = state.value.copy(enableShortTerm = enabled) }
}
