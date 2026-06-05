package nart.simpleanki

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nart.simpleanki.core.data.settings.SettingsRepository
import nart.simpleanki.core.data.sync.SyncWorker
import nart.simpleanki.core.notifications.STREAK_SAVER_HOUR
import nart.simpleanki.core.notifications.STREAK_SAVER_MINUTE
import nart.simpleanki.core.notifications.ReminderScheduler
import nart.simpleanki.core.notifications.ReminderType
import nart.simpleanki.di.appModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class AzriApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@AzriApplication)
            modules(appModule)
        }
        SyncWorker.schedule(this)
        ensureReminders()
    }

    /**
     * WorkManager survives reboot, but a force-stop clears its jobs until the app runs again — so on
     * every launch we re-arm any enabled reminder (idempotent via the per-type unique work name).
     */
    private fun ensureReminders() {
        CoroutineScope(Dispatchers.Default).launch {
            val settings = get<SettingsRepository>().settings.first()
            val scheduler = get<ReminderScheduler>()
            if (settings.studyReminderEnabled) {
                scheduler.schedule(ReminderType.Study, settings.studyReminderHour, settings.studyReminderMinute)
            }
            if (settings.goalReminderEnabled) {
                scheduler.schedule(ReminderType.Goal, settings.goalReminderHour, settings.goalReminderMinute)
            }
            // The streak-saver is automatic (no toggle): always armed at the fixed evening time.
            scheduler.schedule(ReminderType.StreakSaver, STREAK_SAVER_HOUR, STREAK_SAVER_MINUTE)
        }
    }
}
