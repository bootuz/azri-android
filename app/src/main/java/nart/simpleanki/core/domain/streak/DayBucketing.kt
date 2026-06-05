package nart.simpleanki.core.domain.streak

import java.util.Calendar
import java.util.TimeZone

/**
 * Civil-day number for [millis] in [timeZone] — a DST-safe day index where consecutive calendar
 * dates always differ by exactly 1. Reads the local Y/M/D, then rebuilds them as a UTC midnight and
 * divides by a day, so 23h/25h DST days don't shift the index. (minSdk 24 rules out java.time.)
 */
fun localEpochDay(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
    val local = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
    val utcMidnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }
    return utcMidnight.timeInMillis / 86_400_000L
}
