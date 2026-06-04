package nart.simpleanki.core.analytics

/**
 * Turns a nav route into a stable screen name for analytics: drops argument segments so
 * `deck/{deckId}` and `study/{a}/{b}` collapse to `deck` / `study`. Null → "unknown".
 */
fun screenName(route: String?): String {
    if (route.isNullOrBlank()) return "unknown"
    return route.substringBefore("/{").substringBefore("/")
}
