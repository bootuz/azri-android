package nart.simpleanki.core.csv

/**
 * A parsed delimited file. [rows] are positional (list-of-columns), NOT keyed by header,
 * so the same grid serves both header-on and header-off views. Every row is padded to
 * [headers].size, so indexing by column is always safe.
 */
data class ParsedCsv(
    val headers: List<String>,
    val rows: List<List<String>>,
)

/** A card ready for preview/import after column mapping. */
data class CsvPreviewCard(
    val front: String,
    val back: String,
    val selected: Boolean = true,
)

/** Import failures surfaced to the UI. */
sealed class CsvImportError(message: String) : Exception(message) {
    object FileAccess : CsvImportError("Unable to open the file. Please try again.")
    object EmptyFile : CsvImportError("This file has no rows to import.")
    object TooFewColumns : CsvImportError("A CSV needs at least two columns (a front and a back).")
    object ParseFailed : CsvImportError("We couldn't read this file as CSV.")
}
