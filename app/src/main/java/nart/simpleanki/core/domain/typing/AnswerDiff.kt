package nart.simpleanki.core.domain.typing

/**
 * Character-level diff between a typed answer and the expected answer, for the wrong-answer reveal.
 * Matching is case-insensitive (the answer check is too) but accent-sensitive, so "é" vs "e" is a
 * mismatch. Returns, for each of the expected and typed strings, a list of coalesced [Segment]s
 * marking which runs are on the longest common subsequence ([Kind.Match]) and which differ
 * ([Kind.Mismatch] — i.e. missing chars in the expected string, extra/wrong chars in the typed one).
 */
object AnswerDiff {
    enum class Kind { Match, Mismatch }
    data class Segment(val text: String, val kind: Kind)
    data class Result(val expected: List<Segment>, val typed: List<Segment>)

    fun diff(typed: String, expected: String): Result {
        val a = typed
        val b = expected
        val n = a.length
        val m = b.length
        // dp[i][j] = LCS length of a[i..] and b[j..] (case-insensitive char equality).
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[i].matchesIgnoreCase(b[j])) dp[i + 1][j + 1] + 1
                else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val aMatch = BooleanArray(n)
        val bMatch = BooleanArray(m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            if (a[i].matchesIgnoreCase(b[j])) {
                aMatch[i] = true
                bMatch[j] = true
                i++
                j++
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                i++
            } else {
                j++
            }
        }
        return Result(expected = segmentsOf(b, bMatch), typed = segmentsOf(a, aMatch))
    }

    private fun Char.matchesIgnoreCase(other: Char): Boolean =
        this == other || lowercaseChar() == other.lowercaseChar()

    /** Coalesces consecutive chars of [s] with the same match-status into [Segment]s. */
    private fun segmentsOf(s: String, match: BooleanArray): List<Segment> {
        val out = mutableListOf<Segment>()
        var k = 0
        while (k < s.length) {
            val kind = if (match[k]) Kind.Match else Kind.Mismatch
            val start = k
            while (k < s.length && (if (match[k]) Kind.Match else Kind.Mismatch) == kind) k++
            out += Segment(s.substring(start, k), kind)
        }
        return out
    }
}
