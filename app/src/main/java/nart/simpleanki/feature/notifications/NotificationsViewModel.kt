package nart.simpleanki.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nart.simpleanki.core.data.settings.SettingsRepository
import nart.simpleanki.core.notifications.ReminderScheduler
import nart.simpleanki.core.notifications.ReminderType

data class NotificationsUiState(
    val studyEnabled: Boolean = false,
    val studyHour: Int = 9,
    val studyMinute: Int = 0,
    val goalEnabled: Boolean = false,
    val goalHour: Int = 20,
    val goalMinute: Int = 0,
)

/** Backs the Notifications screen: persists the two reminders and (re)schedules their WorkManager jobs. */
class NotificationsViewModel(
    private val settingsRepository: SettingsRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    val uiState: StateFlow<NotificationsUiState> =
        settingsRepository.settings
            .map {
                NotificationsUiState(
                    studyEnabled = it.studyReminderEnabled,
                    studyHour = it.studyReminderHour,
                    studyMinute = it.studyReminderMinute,
                    goalEnabled = it.goalReminderEnabled,
                    goalHour = it.goalReminderHour,
                    goalMinute = it.goalReminderMinute,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    fun setStudy(enabled: Boolean, hour: Int, minute: Int) = viewModelScope.launch {
        settingsRepository.setStudyReminder(enabled, hour, minute)
        if (enabled) scheduler.schedule(ReminderType.Study, hour, minute) else scheduler.cancel(ReminderType.Study)
    }

    fun setGoal(enabled: Boolean, hour: Int, minute: Int) = viewModelScope.launch {
        settingsRepository.setGoalReminder(enabled, hour, minute)
        if (enabled) scheduler.schedule(ReminderType.Goal, hour, minute) else scheduler.cancel(ReminderType.Goal)
    }
}
