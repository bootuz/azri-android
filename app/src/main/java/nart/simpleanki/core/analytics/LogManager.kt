package nart.simpleanki.core.analytics

/**
 * Fans every analytics call out to all [services]. Mirrors the iOS `LogManager`.
 * Each service call is isolated so one failing backend can't break a user action
 * or starve the other backends.
 */
class LogManager(private val services: List<LogService>) {

    fun identifyUser(uid: String, name: String?, email: String?) =
        forEachService { it.identifyUser(uid, name, email) }

    fun clearUser() = forEachService { it.clearUser() }

    fun track(event: LoggableEvent) = forEachService { it.track(event) }

    fun trackScreen(name: String) = forEachService { it.trackScreen(name) }

    private inline fun forEachService(action: (LogService) -> Unit) {
        for (service in services) {
            runCatching { action(service) }
        }
    }
}
