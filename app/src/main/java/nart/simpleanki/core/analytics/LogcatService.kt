package nart.simpleanki.core.analytics

import android.util.Log

/** Logs every event to Logcat (all severities). The iOS `ConsoleService`. Always active. */
class LogcatService(private val printParams: Boolean = true) : LogService {

    override fun identifyUser(uid: String, name: String?, email: String?) {
        Log.i(TAG, "👤 identify uid=$uid name=${name ?: "-"} email=${email ?: "-"}")
    }

    override fun clearUser() { Log.i(TAG, "👤 clear user") }

    override fun track(event: LoggableEvent) {
        val msg = buildString {
            append("${event.type.emoji} ${event.eventName}")
            if (printParams && event.params.isNotEmpty()) {
                event.params.toSortedMap(compareBy { it }).forEach { (k, v) -> append("\n  $k=$v") }
            }
        }
        when (event.type) {
            LogType.Info -> Log.i(TAG, msg)
            LogType.Analytic -> Log.d(TAG, msg)
            LogType.Warning -> Log.w(TAG, msg)
            LogType.Severe -> Log.e(TAG, msg)
        }
    }

    override fun trackScreen(name: String) { Log.d(TAG, "📱 screen_view $name") }

    private companion object { const val TAG = "AzriAnalytics" }
}
