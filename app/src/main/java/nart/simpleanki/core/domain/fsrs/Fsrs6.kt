package nart.simpleanki.core.domain.fsrs

import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Rating
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/** Current FSRS memory state of a card (what the scheduler reads). */
data class FsrsCard(
    val stability: Double,
    val difficulty: Double,
    val state: CardState,
    val reps: Int,
    val lapses: Int,
    val lastReviewMillis: Long?,
)

/** Result of scheduling a review (what the scheduler returns). */
data class FsrsReview(
    val stability: Double,
    val difficulty: Double,
    val state: CardState,
    val reps: Int,
    val lapses: Int,
    val dueMillis: Long,
    val scheduledDays: Double,
    val elapsedDays: Double,
    val lastReviewMillis: Long,
)

/**
 * Canonical FSRS-6 scheduler (21 parameters, power forgetting curve), ported from the
 * open-spaced-repetition algorithm. Short-term scheduling is enabled. The structure
 * follows FSRS-Kotlin but uses the canonical power retrievability curve
 * `R(t) = (1 + factor·t/S)^decay` (not the app-extracted `exp(-t/S)` variant) for correctness.
 */
class Fsrs6(
    val w: List<Double> = DEFAULT_PARAMETERS,
    val requestRetention: Double = 0.9,
    val maximumInterval: Int = 36500,
) {
    init {
        require(w.size == 21) { "FSRS-6 requires 21 parameters, got ${w.size}" }
    }

    private val decay = -w[20]
    private val factor = 0.9.pow(1.0 / decay) - 1.0

    private fun clampDifficulty(d: Double) = d.coerceIn(1.0, 10.0)
    private fun clampStability(s: Double) = s.coerceAtLeast(MIN_STABILITY)

    private fun initStability(rating: Rating): Double = clampStability(w[rating.value - 1])

    private fun initDifficulty(rating: Rating): Double =
        clampDifficulty(w[4] - exp(w[5] * (rating.value - 1)) + 1.0)

    private fun linearDamping(deltaD: Double, oldD: Double): Double = deltaD * (10.0 - oldD) / 9.0

    private fun nextDifficulty(d: Double, rating: Rating): Double {
        val deltaD = -w[6] * (rating.value - 3)
        val damped = d + linearDamping(deltaD, d)
        val reverted = w[7] * initDifficulty(Rating.Easy) + (1.0 - w[7]) * damped
        return clampDifficulty(reverted)
    }

    /** Power forgetting curve: probability of recall after [elapsedDays] for memory [stability]. */
    fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1.0 + factor * elapsedDays / stability).pow(decay)

    private fun nextRecallStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.Hard) w[15] else 1.0
        val easyBonus = if (rating == Rating.Easy) w[16] else 1.0
        val growth = exp(w[8]) * (11.0 - d) * s.pow(-w[9]) *
            (exp((1.0 - r) * w[10]) - 1.0) * hardPenalty * easyBonus
        return clampStability(s * (1.0 + growth))
    }

    private fun nextForgetStability(d: Double, s: Double, r: Double): Double {
        val sMin = s / exp(w[17] * w[18])
        val forget = w[11] * d.pow(-w[12]) * ((s + 1.0).pow(w[13]) - 1.0) * exp((1.0 - r) * w[14])
        return clampStability(minOf(forget, sMin))
    }

    private fun nextShortTermStability(s: Double, rating: Rating): Double {
        var sinc = exp(w[17] * (rating.value - 3 + w[18])) * s.pow(-w[19])
        if (rating.value >= Rating.Good.value) sinc = maxOf(sinc, 1.0)
        return clampStability(s * sinc)
    }

    /** Interval in days from stability, for cards that have graduated to Review. */
    private fun reviewIntervalDays(stability: Double): Int {
        val raw = (stability / factor) * (requestRetention.pow(1.0 / decay) - 1.0)
        return raw.roundToInt().coerceIn(1, maximumInterval)
    }

    /** Sub-day interval (fractional days) for cards still in (re)learning. */
    private fun learningIntervalDays(stability: Double): Double {
        val raw = (stability / factor) * (requestRetention.pow(1.0 / decay) - 1.0)
        return raw.coerceIn(ONE_MINUTE_DAYS, 1.0)
    }

    fun review(card: FsrsCard, rating: Rating, nowMillis: Long): FsrsReview {
        val elapsedDays: Double
        val newDifficulty: Double
        var newStability: Double
        var newState: CardState
        var lapses = card.lapses

        when (card.state) {
            CardState.New -> {
                elapsedDays = 0.0
                newDifficulty = initDifficulty(rating)
                newStability = initStability(rating)
                newState = if (rating == Rating.Easy) CardState.Review else CardState.Learning
            }

            CardState.Learning, CardState.Relearning -> {
                elapsedDays = 0.0
                newDifficulty = nextDifficulty(card.difficulty, rating)
                newStability = nextShortTermStability(card.stability, rating)
                newState = when (rating) {
                    Rating.Again, Rating.Hard -> card.state
                    Rating.Good, Rating.Easy -> CardState.Review
                }
            }

            CardState.Review -> {
                elapsedDays = card.lastReviewMillis
                    ?.let { (nowMillis - it).coerceAtLeast(0L) / MILLIS_PER_DAY.toDouble() }
                    ?: 0.0
                val r = retrievability(elapsedDays, card.stability)
                newDifficulty = nextDifficulty(card.difficulty, rating)
                if (rating == Rating.Again) {
                    newStability = nextForgetStability(newDifficulty, card.stability, r)
                    newState = CardState.Relearning
                    lapses += 1
                } else {
                    newStability = nextRecallStability(newDifficulty, card.stability, r, rating)
                    newState = CardState.Review
                }
            }
        }

        val scheduledDays = if (newState == CardState.Review) {
            reviewIntervalDays(newStability).toDouble()
        } else {
            learningIntervalDays(newStability)
        }
        val dueMillis = nowMillis + (scheduledDays * MILLIS_PER_DAY).toLong()

        return FsrsReview(
            stability = newStability,
            difficulty = newDifficulty,
            state = newState,
            reps = card.reps + 1,
            lapses = lapses,
            dueMillis = dueMillis,
            scheduledDays = scheduledDays,
            elapsedDays = elapsedDays,
            lastReviewMillis = nowMillis,
        )
    }

    companion object {
        /** FSRS-6 default 21 parameters (open-spaced-repetition). */
        val DEFAULT_PARAMETERS = listOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
            1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
        )

        const val MIN_STABILITY = 0.01
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val ONE_MINUTE_DAYS = 1.0 / 1440.0
    }
}
