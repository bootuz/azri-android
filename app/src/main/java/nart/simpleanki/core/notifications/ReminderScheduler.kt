package nart.simpleanki.core.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** WorkManager input-data key carrying the [ReminderType.name]. */
const val REMINDER_TYPE_KEY = "reminder_type"

/** Arms / cancels the daily reminders. Interface so the ViewModel is unit-testable. */
interface ReminderScheduler {
    fun schedule(type: ReminderType, hour: Int, minute: Int)
    fun cancel(type: ReminderType)
}

/**
 * Pure: milliseconds from [nowMillis] until the next [hour]:[minute] — today if still ahead,
 * otherwise tomorrow. Independent of WorkManager so it's unit-testable.
 */
fun nextTriggerDelayMillis(nowMillis: Long, hour: Int, minute: Int): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= nowMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
    return cal.timeInMillis - nowMillis
}

/**
 * Schedules each reminder as a self-rescheduling one-time WorkManager job (one per [ReminderType],
 * keyed by [ReminderType.key]). A daily reminder needs no exact alarm, and WorkManager survives
 * reboot — so this avoids AlarmManager + SCHEDULE_EXACT_ALARM entirely.
 */
class WorkManagerReminderScheduler(private val context: Context) : ReminderScheduler {

    override fun schedule(type: ReminderType, hour: Int, minute: Int) {
        val delay = nextTriggerDelayMillis(System.currentTimeMillis(), hour, minute)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(REMINDER_TYPE_KEY to type.name))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(type.key, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(type: ReminderType) {
        WorkManager.getInstance(context).cancelUniqueWork(type.key)
    }
}
