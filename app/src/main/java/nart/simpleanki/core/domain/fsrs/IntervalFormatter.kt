package nart.simpleanki.core.domain.fsrs

/**
 * Formats a forward time span (millis from now) into a compact, single-unit label like
 * "< 1m", "10m", "3h", "4d", "2mo" — mirroring the iOS `FSRSManager.formatInterval`
 * (DateComponentsFormatter: abbreviated, maximumUnitCount = 1, units minute/hour/day/month).
 */
object IntervalFormatter {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val MONTH = 30 * DAY

    fun format(millisFromNow: Long): String {
        val ms = millisFromNow.coerceAtLeast(0L)
        return when {
            ms < MINUTE -> "< 1m"
            ms < HOUR -> "${ms / MINUTE}m"
            ms < DAY -> "${ms / HOUR}h"
            ms < MONTH -> "${ms / DAY}d"
            else -> "${ms / MONTH}mo"
        }
    }
}
