package nart.simpleanki.core.analytics

/** An analytics event. Mirrors the iOS `LoggableEvent`. */
interface LoggableEvent {
    val eventName: String
    val params: Map<String, Any?> get() = emptyMap()
    val type: LogType get() = LogType.Analytic
}

/** Ad-hoc event when a dedicated type isn't warranted. */
data class AnyLoggableEvent(
    override val eventName: String,
    override val params: Map<String, Any?> = emptyMap(),
    override val type: LogType = LogType.Analytic,
) : LoggableEvent

/** Severity / routing hint. Analytics ignores [Info]; Crashlytics only acts on [Warning]/[Severe]. */
enum class LogType {
    Info, Analytic, Warning, Severe;

    val emoji: String
        get() = when (this) {
            Info -> "👋"
            Analytic -> "📈"
            Warning -> "⚠️"
            Severe -> "🚨"
        }
}
