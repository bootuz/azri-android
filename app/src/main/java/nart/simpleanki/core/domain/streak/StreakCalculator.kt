package nart.simpleanki.core.domain.streak

/** A study streak in days. */
data class Streak(val current: Int, val longest: Int)

object StreakCalculator {
    /**
     * [reviewDays] = civil-day indices on which >=1 review happened; [today] = today's civil-day index.
     * Pure (no timezone logic — bucket with [localEpochDay] first).
     * - current: if the user studied today, or studied yesterday (the streak is still alive through
     *   today), the length of the consecutive run ending there; otherwise 0 (hard reset).
     * - longest: the longest consecutive run anywhere in the set.
     */
    fun compute(reviewDays: Set<Long>, today: Long): Streak {
        if (reviewDays.isEmpty()) return Streak(0, 0)

        var longest = 1
        var run = 1
        var prev: Long? = null
        for (day in reviewDays.toSortedSet()) {
            run = if (prev != null && day == prev + 1) run + 1 else 1
            if (run > longest) longest = run
            prev = day
        }

        val anchor = when {
            reviewDays.contains(today) -> today
            reviewDays.contains(today - 1) -> today - 1
            else -> return Streak(0, longest)
        }
        var current = 0
        var day = anchor
        while (reviewDays.contains(day)) {
            current++
            day--
        }
        return Streak(current, longest)
    }
}
