package nart.simpleanki.core.csv

import android.net.Uri

class FakeCsvImportService(
    var parsed: ParsedCsv = ParsedCsv(emptyList(), emptyList()),
    var parseError: Throwable? = null,
) : CsvImportService {
    var lastHasHeader: Boolean? = null
    var importedDeckName: String? = null
    var importedCards: List<CsvPreviewCard> = emptyList()

    override suspend fun parse(uri: Uri, hasHeader: Boolean): ParsedCsv {
        lastHasHeader = hasHeader
        parseError?.let { throw it }
        return parsed
    }

    override fun validate(parsed: ParsedCsv) {}

    override fun previewCards(parsed: ParsedCsv, frontCol: Int, backCol: Int): List<CsvPreviewCard> =
        parsed.rows.mapNotNull { row ->
            val f = row.getOrNull(frontCol)?.trim().orEmpty()
            val b = row.getOrNull(backCol)?.trim().orEmpty()
            if (f.isEmpty() || b.isEmpty()) null else CsvPreviewCard(f, b)
        }

    override suspend fun import(cards: List<CsvPreviewCard>, deckName: String) {
        importedCards = cards.filter { it.selected }
        importedDeckName = deckName
    }
}
