package nart.simpleanki.core.analytics

/** A single analytics backend. The iOS `LogService` protocol. */
interface LogService {
    fun identifyUser(uid: String, name: String?, email: String?)
    fun clearUser()
    fun track(event: LoggableEvent)
    fun trackScreen(name: String)
}
