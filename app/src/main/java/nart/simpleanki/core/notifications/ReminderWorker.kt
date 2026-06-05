package nart.simpleanki.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.settings.AppSettings
import nart.simpleanki.core.data.settings.SettingsRepository
import nart.simpleanki.core.domain.fsrs.StudyQueueBuilder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Calendar

/**
 * Fires one daily reminder, then reschedules itself for tomorrow at the same time. Dependencies
 * resolved from Koin (matching [nart.simpleanki.core.data.sync.SyncWorker]). The decision of
 * *what* to post lives in the pure [reminderContent]; this worker is just plumbing.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val settingsRepository: SettingsRepository by inject()
    private val cardRepository: CardRepository by inject()
    private val notifier: Notifier by inject()
    private val scheduler: ReminderScheduler by inject()

    override suspend fun doWork(): Result {
        val type = inputData.getString(REMINDER_TYPE_KEY)
            ?.let { runCatching { ReminderType.valueOf(it) }.getOrNull() }
            ?: return Result.success()

        val settings = settingsRepository.settings.first()
        val (enabled, hour, minute) = settings.scheduleFor(type)
        if (!enabled) return Result.success() // toggled off since it was scheduled — stop the chain.

        val now = System.currentTimeMillis()
        val cards = cardRepository.observeAllCards().first().filter { !it.isDeleted }
        val studiedToday = cards.count { (it.fsrsLastReview ?: 0L) >= startOfDay(now) }
        val readyCount = StudyQueueBuilder.buildStudyQueue(cards, now, Int.MAX_VALUE, Int.MAX_VALUE).size

        reminderContent(type, settings, studiedToday, readyCount)?.let { notifier.post(type, it) }

        scheduler.schedule(type, hour, minute) // chain tomorrow
        return Result.success()
    }

    private data class Schedule(val enabled: Boolean, val hour: Int, val minute: Int)

    private fun AppSettings.scheduleFor(type: ReminderType): Schedule = when (type) {
        ReminderType.Study -> Schedule(studyReminderEnabled, studyReminderHour, studyReminderMinute)
        ReminderType.Goal -> Schedule(goalReminderEnabled, goalReminderHour, goalReminderMinute)
        ReminderType.StreakSaver -> Schedule(enabled = true, STREAK_SAVER_HOUR, STREAK_SAVER_MINUTE)
    }

    private fun startOfDay(nowMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
