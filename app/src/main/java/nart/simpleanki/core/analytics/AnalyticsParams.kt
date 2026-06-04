package nart.simpleanki.core.analytics

private const val MAX_NAME = 40
private const val MAX_KEY = 40
private const val MAX_VALUE = 100
private const val MAX_PARAMS = 25

/** lowercases nothing (Firebase is case-sensitive) but clips length and replaces spaces. */
private fun clean(text: String, max: Int): String =
    text.replace(' ', '_').take(max)

/**
 * Applies the iOS Firebase-Analytics hygiene rules and returns a cleaned event name plus a
 * map whose values are only `Long`, `Double`, or `String` (ready to load into a Bundle).
 * Pure and SDK-free so it is unit-testable. Mirrors `FirebaseAnalyticsService` on iOS.
 */
fun cleanAnalyticsParams(
    eventName: String,
    params: Map<String, Any?>,
): Pair<String, Map<String, Any>> {
    val cleaned = LinkedHashMap<String, Any>()
    for ((rawKey, rawValue) in params) {
        if (cleaned.size >= MAX_PARAMS) break
        if (rawValue == null) continue
        val key = clean(rawKey, MAX_KEY)
        val value: Any = when (rawValue) {
            is Int -> rawValue.toLong()
            is Long -> rawValue
            is Boolean -> if (rawValue) 1L else 0L
            is Float -> rawValue.toDouble()
            is Double -> rawValue
            is String -> rawValue.take(MAX_VALUE)
            else -> rawValue.toString().take(MAX_VALUE)
        }
        cleaned[key] = value
    }
    return clean(eventName, MAX_NAME) to cleaned
}
