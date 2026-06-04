package nart.simpleanki.core.apkg

import android.net.Uri

class FakeApkgImportService(
    var collection: ParsedCollection = ParsedCollection(emptyList(), emptyList(), emptyMap()),
    var parseError: Throwable? = null,
) : ApkgImportService {
    var importedDeckName: String? = null
    var importedCards: List<ApkgPreviewCard> = emptyList()

    override suspend fun parse(uri: Uri): ParsedCollection = parseError?.let { throw it } ?: collection
    override fun filterNotes(collection: ParsedCollection, noteTypeId: Long) =
        collection.notes.filter { it.modelId == noteTypeId }
    override suspend fun previewCards(
        notes: List<AnkiNote>, noteType: AnkiNoteType,
        frontIdx: Int, backIdx: Int, media: Map<String, ByteArray>, importMedia: Boolean,
    ): List<ApkgPreviewCard> = notes.filter { frontIdx < it.fields.size && backIdx < it.fields.size }
        .map { ApkgPreviewCard(it.fields[frontIdx], it.fields[backIdx], null, null) }
    override suspend fun import(cards: List<ApkgPreviewCard>, deckName: String) {
        importedCards = cards.filter { it.selected }; importedDeckName = deckName
    }
}
