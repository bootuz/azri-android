package nart.simpleanki.core.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nart.simpleanki.core.domain.fsrs.FsrsPreset

/** In-memory [SettingsRepository] for unit tests. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun setPreset(preset: FsrsPreset) { state.value = state.value.copy(preset = preset) }
    override suspend fun setThemeMode(mode: ThemeMode) { state.value = state.value.copy(themeMode = mode) }
    override suspend fun setCustomRetention(value: Double) { state.value = state.value.copy(customRetention = value) }
    override suspend fun setCustomMaxInterval(days: Int) { state.value = state.value.copy(customMaxInterval = days) }
    override suspend fun setEnableFuzz(enabled: Boolean) { state.value = state.value.copy(enableFuzz = enabled) }
    override suspend fun setEnableShortTerm(enabled: Boolean) { state.value = state.value.copy(enableShortTerm = enabled) }
    override suspend fun setDailyGoalEnabled(enabled: Boolean) { state.value = state.value.copy(dailyGoalEnabled = enabled) }
    override suspend fun setNewCardsTarget(value: Int) { state.value = state.value.copy(newCardsTarget = value) }
    override suspend fun setReviewCardsTarget(value: Int) { state.value = state.value.copy(reviewCardsTarget = value) }
    override suspend fun setStudyReminder(enabled: Boolean, hour: Int, minute: Int) {
        state.value = state.value.copy(studyReminderEnabled = enabled, studyReminderHour = hour, studyReminderMinute = minute)
    }
    override suspend fun setGoalReminder(enabled: Boolean, hour: Int, minute: Int) {
        state.value = state.value.copy(goalReminderEnabled = enabled, goalReminderHour = hour, goalReminderMinute = minute)
    }
    override suspend fun setQueueSortOrder(order: nart.simpleanki.core.domain.fsrs.QueueSortOrder) {
        state.value = state.value.copy(queueSortOrder = order)
    }
    override suspend fun setQueueShuffleSeed(seed: Long) { state.value = state.value.copy(queueShuffleSeed = seed) }
    override suspend fun setPremiumNudgeDismissed(dismissed: Boolean) {
        state.value = state.value.copy(premiumNudgeDismissed = dismissed)
    }
}
