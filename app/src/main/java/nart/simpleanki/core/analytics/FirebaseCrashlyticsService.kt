package nart.simpleanki.core.analytics

import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Surfaces [LogType.Warning]/[LogType.Severe] events to Crashlytics; Severe also records a non-fatal. */
class FirebaseCrashlyticsService(private val crashlytics: FirebaseCrashlytics) : LogService {

    override fun identifyUser(uid: String, name: String?, email: String?) { crashlytics.setUserId(uid) }

    override fun clearUser() { crashlytics.setUserId("") }

    override fun track(event: LoggableEvent) {
        if (event.type != LogType.Warning && event.type != LogType.Severe) return
        crashlytics.log("${event.eventName} ${event.params}")
        if (event.type == LogType.Severe) {
            crashlytics.recordException(AnalyticsEventException(event.eventName))
        }
    }

    override fun trackScreen(name: String) { /* screen views are not crash signals */ }
}

/** Non-fatal marker so severe analytics events appear in Crashlytics. */
class AnalyticsEventException(eventName: String) : Exception(eventName)
