package nart.simpleanki.feature.study

import nart.simpleanki.core.domain.model.Rating
import kotlin.math.roundToInt

/** (good + easy) / total reviews x 100, rounded to nearest Int. 0 when there are no reviews. */
fun sessionAccuracy(ratingCounts: Map<Rating, Int>): Int {
    val total = ratingCounts.values.sum()
    if (total == 0) return 0
    val correct = (ratingCounts[Rating.Good] ?: 0) + (ratingCounts[Rating.Easy] ?: 0)
    return (correct * 100.0 / total).roundToInt()
}

/** Accuracy-keyed encouragement, mirroring the iOS thresholds. */
fun motivationalMessage(accuracy: Int): String = when {
    accuracy >= 90 -> "Outstanding session!"
    accuracy >= 70 -> "Great work, keep it up!"
    accuracy >= 50 -> "Solid effort, you're improving!"
    else -> "Every review makes you stronger!"
}

/** Abbreviated duration: "0s", "42s", "5m", "5m 12s". Drops a leading 0m and a trailing 0s. */
fun formattedDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes == 0L -> "${seconds}s"
        seconds == 0L -> "${minutes}m"
        else -> "${minutes}m ${seconds}s"
    }
}
