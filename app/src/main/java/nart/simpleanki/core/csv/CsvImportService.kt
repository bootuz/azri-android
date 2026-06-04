package nart.simpleanki.core.csv

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Deck
import java.util.UUID

interface CsvImportService {
    /** Reads [uri] as UTF-8, parses, and validates. Throws [CsvImportError] on failure. */
    suspend fun parse(uri: Uri, hasHeader: Boolean): ParsedCsv
    fun validate(parsed: ParsedCsv)
    fun previewCards(parsed: ParsedCsv, frontCol: Int, backCol: Int): List<CsvPreviewCard>
    suspend fun import(cards: List<CsvPreviewCard>, deckName: String)
}

class DefaultCsvImportService(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val appContext: Context? = null,        // null in unit tests (parse not exercised there)
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : CsvImportService {

    override suspend fun parse(uri: Uri, hasHeader: Boolean): ParsedCsv = withContext(Dispatchers.IO) {
        val ctx = appContext ?: throw CsvImportError.FileAccess
        // CSV files are small; read straight into memory (no temp file needed).
        val text = try {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw CsvImportError.FileAccess
        } catch (e: CsvImportError) {
            throw e
        } catch (e: Exception) {
            throw CsvImportError.FileAccess
        }
        val parsed = try {
            CsvParser.parse(text, hasHeader)
        } catch (e: Exception) {
            throw CsvImportError.ParseFailed
        }
        validate(parsed)
        parsed
    }

    override fun validate(parsed: ParsedCsv) {
        if (parsed.rows.isEmpty()) throw CsvImportError.EmptyFile
        if (parsed.headers.size < 2) throw CsvImportError.TooFewColumns
    }

    override fun previewCards(parsed: ParsedCsv, frontCol: Int, backCol: Int): List<CsvPreviewCard> =
        parsed.rows.mapNotNull { row ->
            val front = row.getOrNull(frontCol)?.trim().orEmpty()
            val back = row.getOrNull(backCol)?.trim().orEmpty()
            if (front.isEmpty() || back.isEmpty()) null else CsvPreviewCard(front, back)
        }

    override suspend fun import(cards: List<CsvPreviewCard>, deckName: String) {
        val toImport = cards.filter { it.selected }
        if (toImport.isEmpty()) return
        val t = now()
        val deck = Deck(id = idGenerator(), name = deckName, dateCreated = t, lastModified = t)
        deckRepository.upsert(deck)
        for (c in toImport) {
            cardRepository.upsert(
                Card(
                    id = idGenerator(), front = c.front, back = c.back,
                    image = null, audioName = null, imagePath = null, audioPath = null,
                    deckId = deck.id, dateCreated = t, lastModified = t,
                    fsrsDue = t, fsrsState = CardState.New.value, source = "csv",
                ),
            )
        }
    }
}
