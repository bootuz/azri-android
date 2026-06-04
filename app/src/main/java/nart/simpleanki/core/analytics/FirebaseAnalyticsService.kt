package nart.simpleanki.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/** Sends cleaned events + screen views + user props to Firebase Analytics. Skips [LogType.Info]. */
class FirebaseAnalyticsService(private val analytics: FirebaseAnalytics) : LogService {

    override fun identifyUser(uid: String, name: String?, email: String?) {
        analytics.setUserId(uid)
        name?.let { analytics.setUserProperty("account_name", it.take(100)) }
        email?.let { analytics.setUserProperty("account_email", it.take(100)) }
    }

    override fun clearUser() { analytics.setUserId(null) }

    override fun track(event: LoggableEvent) {
        if (event.type == LogType.Info) return
        val (name, params) = cleanAnalyticsParams(event.eventName, event.params)
        analytics.logEvent(name, if (params.isEmpty()) null else params.toBundle())
    }

    override fun trackScreen(name: String) {
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName(name))
        })
    }

    private fun Map<String, Any>.toBundle(): Bundle = Bundle().apply {
        forEach { (k, v) ->
            when (v) {
                is Long -> putLong(k, v)
                is Double -> putDouble(k, v)
                is String -> putString(k, v)
            }
        }
    }
}
