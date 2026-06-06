package nart.simpleanki.core.domain.typing

/**
 * Normalizes and compares typed answers for Type Practice. Case-insensitive, whitespace-insensitive,
 * and surrounding-punctuation-insensitive (leading/trailing Unicode punctuation is stripped — e.g.
 * "¿cómo estás?" -> "cómo estás"), but accent/diacritic-SENSITIVE ("café" != "cafe"). The objective
 * signal it produces is the basis for mastery + (Phase 2) diagnostics, so it stays strict on accents.
 */
object AnswerMatcher {
    // Leading/trailing Unicode punctuation (\p{P}) and whitespace.
    // Note: Unicode Symbol chars (\p{S}, e.g. ©, ★) are intentionally preserved.
    private val edges = Regex("^[\\p{P}\\s]+|[\\p{P}\\s]+$")
    private val innerWhitespace = Regex("\\s+")

    fun normalize(input: String): String =
        input.replace(edges, "").replace(innerWhitespace, " ").lowercase()

    fun matches(typed: String, expected: String): Boolean {
        val n = normalize(expected)
        return n.isNotEmpty() && normalize(typed) == n
    }
}
