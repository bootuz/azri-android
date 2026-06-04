package nart.simpleanki.core.csv

import com.github.doyaaaaaken.kotlincsv.dsl.context.InsufficientFieldsRowBehaviour
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader

/**
 * Parses delimited text into a [ParsedCsv]. Delimiter is auto-detected (comma/semicolon/tab)
 * from the first physical line, ignoring delimiters inside quoted fields. RFC 4180 quoting,
 * escaped quotes, and embedded newlines are handled by kotlin-csv.
 */
object CsvParser {

    private val CANDIDATES = listOf(',', ';', '\t')

    fun detectDelimiter(firstLine: String): Char {
        val counts = IntArray(CANDIDATES.size)
        var inQuotes = false
        for (ch in firstLine) {
            if (ch == '"') { inQuotes = !inQuotes; continue }
            if (inQuotes) continue
            val idx = CANDIDATES.indexOf(ch)
            if (idx >= 0) counts[idx]++
        }
        // Highest count wins; ties (including all-zero) fall back to comma.
        var best = 0
        for (i in 1 until counts.size) if (counts[i] > counts[best]) best = i
        return if (counts[best] == 0) ',' else CANDIDATES[best]
    }

    fun parse(text: String, hasHeader: Boolean): ParsedCsv {
        val clean = text.removePrefix("﻿")
        val firstLine = clean.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        val delimiter = detectDelimiter(firstLine)

        // skipEmptyLine drops blank lines (incl. a trailing newline's phantom row) — they aren't cards.
        // insufficientFieldsRowBehaviour = EMPTY_STRING: kotlin-csv 1.10.0 throws on ragged rows by
        // default; instead it pads short rows with "" (the max-width padding below is a safety net).
        val grid = csvReader {
            this.delimiter = delimiter
            this.skipEmptyLine = true
            this.insufficientFieldsRowBehaviour = InsufficientFieldsRowBehaviour.EMPTY_STRING
        }.readAll(clean)
        if (grid.isEmpty()) return ParsedCsv(emptyList(), emptyList())

        val width = grid.maxOf { it.size }
        val padded = grid.map { row -> if (row.size < width) row + List(width - row.size) { "" } else row }

        return if (hasHeader) {
            ParsedCsv(headers = dedupe(padded.first()), rows = padded.drop(1))
        } else {
            ParsedCsv(headers = (1..width).map { "Column $it" }, rows = padded)
        }
    }

    /** Mirrors iOS: repeated names become name, name_2, name_3, … */
    private fun dedupe(headers: List<String>): List<String> {
        val seen = HashMap<String, Int>()
        return headers.map { h ->
            val n = (seen[h] ?: 0) + 1
            seen[h] = n
            if (n == 1) h else "${h}_$n"
        }
    }
}
