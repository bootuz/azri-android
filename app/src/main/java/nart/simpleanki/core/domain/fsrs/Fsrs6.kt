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
    /** When false, New/(re)learning grades graduate straight to day-scale intervals (no minute steps). */
    val enableShortTerm: Boolean = true,
    /** When true, Review-state day intervals get deterministic jitter so cards don't clump on one day. */
    val enableFuzz: Boolean = false,
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

    /**
     * Applies deterministic fuzz to a graduated [intervalDays]. Mirrors the
     * open-spaced-repetition fuzz ranges; the RNG is seeded from card state so the same
     * card always fuzzes to the same value (reproducible + unit-testable). No-op when fuzz
     * is disabled or the interval is below the 2.5-day threshold.
     */
    private fun fuzzedDays(intervalDays: Int, stability: Double, reps: Int): Int {
        val capped = intervalDays.coerceIn(1, maximumInterval)
        if (!enableFuzz || capped < FUZZ_THRESHOLD) return capped
        var delta = 1.0
        for ((start, end, factor) in FUZZ_RANGES) {
            delta += factor * maxOf(minOf(capped.toDouble(), end) - start, 0.0)
        }
        val minI = (capped - delta).roundToInt().coerceAtLeast(2)
        val maxI = (capped + delta).roundToInt().coerceIn(minI, maximumInterval)
        val seed = java.lang.Double.doubleToLongBits(stability) xor (reps.toLong() * 2654435761L) xor capped.toLong()
        val unit = kotlin.random.Random(seed).nextDouble()
        return (minI + (unit * (maxI - minI + 1)).toInt()).coerceIn(1, maximumInterval)
    }

    /** Recall stability for a non-lapse grade in the Review state (per-grade difficulty applied). */
    private fun recallStability(rating: Rating, oldDifficulty: Double, stability: Double, r: Double): Double =
        nextRecallStability(nextDifficulty(oldDifficulty, rating), stability, r, rating)

    fun review(card: FsrsCard, rating: Rating, nowMillis: Long): FsrsReview {
        fun result(
            stability: Double, difficulty: Double, state: CardState,
            scheduledDays: Double, dueMillis: Long, elapsedDays: Double, lapses: Int,
        ) = FsrsReview(
            stability = stability, difficulty = difficulty, state = state,
            reps = card.reps + 1, lapses = lapses, dueMillis = dueMillis,
            scheduledDays = scheduledDays, elapsedDays = elapsedDays, lastReviewMillis = nowMillis,
        )
        // Sub-day (re)learning uses FIXED minute steps — mirrors the FSRS short-term scheduler
        // iOS uses (swift-fsrs BasicScheduler): New 1/5/10m, (re)learning 5/10m, lapse 5m.
        // Only graduation to the Review state uses the stability-derived day interval.
        // When enableShortTerm is false, those minute steps are skipped and cards graduate
        // straight to a day-scale interval (long-term scheduler, ts-fsrs `enable_short_term=false`).
        fun step(minutes: Long) = nowMillis + minutes * MILLIS_PER_MINUTE
        fun days(d: Int) = nowMillis + d.toLong() * MILLIS_PER_DAY

        // Graduate to Review with a (fuzzed) day interval derived from [stability].
        fun graduate(stability: Double, difficulty: Double, elapsedDays: Double, lapses: Int): FsrsReview {
            val iv = fuzzedDays(reviewIntervalDays(stability), stability, card.reps)
            return result(stability, difficulty, CardState.Review, iv.toDouble(), days(iv), elapsedDays, lapses)
        }

        return when (card.state) {
            CardState.New -> {
                val difficulty = initDifficulty(rating)
                val stability = initStability(rating)
                if (!enableShortTerm) {
                    graduate(stability, difficulty, 0.0, card.lapses)
                } else when (rating) {
                    Rating.Again -> result(stability, difficulty, CardState.Learning, 0.0, step(1), 0.0, card.lapses)
                    Rating.Hard -> result(stability, difficulty, CardState.Learning, 0.0, step(5), 0.0, card.lapses)
                    Rating.Good -> result(stability, difficulty, CardState.Learning, 0.0, step(10), 0.0, card.lapses)
                    Rating.Easy -> graduate(stability, difficulty, 0.0, card.lapses)
                }
            }

            CardState.Learning, CardState.Relearning -> {
                val difficulty = nextDifficulty(card.difficulty, rating)
                if (!enableShortTerm) {
                    graduate(nextShortTermStability(card.stability, rating), difficulty, 0.0, card.lapses)
                } else when (rating) {
                    Rating.Again ->
                        result(nextShortTermStability(card.stability, Rating.Again), difficulty, card.state, 0.0, step(5), 0.0, card.lapses)
                    Rating.Hard ->
                        result(nextShortTermStability(card.stability, Rating.Hard), difficulty, card.state, 0.0, step(10), 0.0, card.lapses)
                    Rating.Good -> graduate(nextShortTermStability(card.stability, Rating.Good), difficulty, 0.0, card.lapses)
                    Rating.Easy -> {
                        val goodIv = reviewIntervalDays(nextShortTermStability(card.stability, Rating.Good))
                        val s = nextShortTermStability(card.stability, Rating.Easy)
                        val iv = fuzzedDays(maxOf(reviewIntervalDays(s), goodIv + 1), s, card.reps)
                        result(s, difficulty, CardState.Review, iv.toDouble(), days(iv), 0.0, card.lapses)
                    }
                }
            }

            CardState.Review -> {
                val elapsedDays = card.lastReviewMillis
                    ?.let { (nowMillis - it).coerceAtLeast(0L) / MILLIS_PER_DAY.toDouble() }
                    ?: 0.0
                val r = retrievability(elapsedDays, card.stability)
                if (rating == Rating.Again) {
                    val difficulty = nextDifficulty(card.difficulty, rating)
                    val stability = nextForgetStability(difficulty, card.stability, r)
                    // Short-term off: no relearning minute step — reschedule in days, stay in Review.
                    if (!enableShortTerm) {
                        graduate(stability, difficulty, elapsedDays, card.lapses + 1)
                    } else {
                        result(stability, difficulty, CardState.Relearning, 0.0, step(5), elapsedDays, card.lapses + 1)
                    }
                } else {
                    val difficulty = nextDifficulty(card.difficulty, rating)
                    val stability = recallStability(rating, card.difficulty, card.stability, r)
                    // FSRS interval ordering: hard <= good, good >= hard+1, easy >= good+1.
                    val hardRaw = reviewIntervalDays(recallStability(Rating.Hard, card.difficulty, card.stability, r))
                    val goodRaw = reviewIntervalDays(recallStability(Rating.Good, card.difficulty, card.stability, r))
                    val hardIv = minOf(hardRaw, goodRaw)
                    val goodIv = maxOf(goodRaw, hardIv + 1)
                    val rawIv = when (rating) {
                        Rating.Hard -> hardIv
                        Rating.Good -> goodIv
                        else -> maxOf(reviewIntervalDays(stability), goodIv + 1) // Easy
                    }
                    val iv = fuzzedDays(rawIv, stability, card.reps)
                    result(stability, difficulty, CardState.Review, iv.toDouble(), days(iv), elapsedDays, card.lapses)
                }
            }
        }
    }

    companion object {
        /** FSRS-6 default 21 parameters (open-spaced-repetition). */
        val DEFAULT_PARAMETERS = listOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
            1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
        )

        const val MIN_STABILITY = 0.01
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val MILLIS_PER_MINUTE = 60_000L

        /** Intervals shorter than this (days) are never fuzzed. */
        private const val FUZZ_THRESHOLD = 2.5
        /** (start, end, factor) bands for fuzz width — open-spaced-repetition defaults. */
        private val FUZZ_RANGES = listOf(
            Triple(2.5, 7.0, 0.15),
            Triple(7.0, 20.0, 0.1),
            Triple(20.0, Double.MAX_VALUE, 0.05),
        )
    }
}
