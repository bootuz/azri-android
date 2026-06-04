package nart.simpleanki.core.analytics

/** Records calls for assertions. [throwOnTrack] simulates a misbehaving backend. */
class FakeLogService(private val throwOnTrack: Boolean = false) : LogService {
    val events = mutableListOf<LoggableEvent>()
    val screens = mutableListOf<String>()
    var identified: Triple<String, String?, String?>? = null
    var cleared = false

    override fun identifyUser(uid: String, name: String?, email: String?) { identified = Triple(uid, name, email) }
    override fun clearUser() { cleared = true }
    override fun track(event: LoggableEvent) {
        if (throwOnTrack) throw RuntimeException("backend down")
        events.add(event)
    }
    override fun trackScreen(name: String) { screens.add(name) }
}
