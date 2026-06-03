package nart.simpleanki.core.apkg

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nart.simpleanki.core.data.media.MediaManager
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Deck
import java.io.File
import java.util.UUID

interface ApkgImportService {
    suspend fun parse(uri: Uri): ParsedCollection
    fun filterNotes(collection: ParsedCollection, noteTypeId: Long): List<AnkiNote>
    suspend fun previewCards(
        notes: List<AnkiNote>, noteType: AnkiNoteType,
        frontIdx: Int, backIdx: Int, media: Map<String, ByteArray>, importMedia: Boolean,
    ): List<ApkgPreviewCard>
    suspend fun import(cards: List<ApkgPreviewCard>, deckName: String)
}

class DefaultApkgImportService(
    private val unzipper: ApkgUnzipper,
    private val detector: ApkgFormatDetector,
    private val mediaReader: ApkgMediaReader,
    private val media: MediaManager,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val appContext: Context? = null,          // null in unit tests (parse not exercised there)
    private val legacyReader: AnkiCollectionReader = LegacyApkgReader(),
    private val v3Reader: AnkiCollectionReader = LegacyApkgReader(), // replaced in a later task
    private val openDb: (File) -> AnkiSqlite = { AndroidAnkiSqlite.open(it) },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : ApkgImportService {

    override suspend fun parse(uri: Uri): ParsedCollection = withContext(Dispatchers.IO) {
        val ctx = appContext ?: throw ApkgImportError.FileAccess
        val work = File(ctx.cacheDir, "apkg-${idGenerator()}")
        try {
            val apkg = File(work, "in.apkg").apply { parentFile?.mkdirs() }
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                apkg.outputStream().use { input.copyTo(it) }
            } ?: throw ApkgImportError.FileAccess

            val extracted = File(work, "x")
            unzipper.unzip(apkg, extracted)

            val format = detector.detect(extracted)
            val dbFile = when (format) {
                ApkgFormat.LEGACY -> detector.legacyDbFile(extracted)
                ApkgFormat.V3 -> throw ApkgImportError.UnsupportedSchema   // wired in a later task
            }
            val reader = if (format == ApkgFormat.V3) v3Reader else legacyReader
            val db = openDb(dbFile)
            val data = try { reader.read(db) } finally { db.close() }

            if (data.noteTypes.isEmpty() || data.notes.isEmpty()) throw ApkgImportError.InvalidStructure
            ParsedCollection(data.noteTypes, data.notes, mediaReader.read(extracted))
        } finally {
            work.deleteRecursively()
        }
    }

    override fun filterNotes(collection: ParsedCollection, noteTypeId: Long): List<AnkiNote> =
        collection.notes.filter { it.modelId == noteTypeId }

    override suspend fun previewCards(
        notes: List<AnkiNote>, noteType: AnkiNoteType,
        frontIdx: Int, backIdx: Int, media: Map<String, ByteArray>, importMedia: Boolean,
    ): List<ApkgPreviewCard> {
        val mediaMap: Map<String, String> = if (importMedia) saveMedia(media) else emptyMap()
        return notes.mapNotNull { note ->
            if (frontIdx >= note.fields.size || backIdx >= note.fields.size) return@mapNotNull null
            val f = ApkgFieldProcessor.process(note.fields[frontIdx], mediaMap)
            val b = ApkgFieldProcessor.process(note.fields[backIdx], mediaMap)
            if (f.text.isEmpty() || b.text.isEmpty()) return@mapNotNull null
            ApkgPreviewCard(
                front = f.text, back = b.text,
                imageName = f.image ?: b.image, audioName = f.audio ?: b.audio,
            )
        }
    }

    /** Saves each media blob locally, returning originalName → localFilename.
     *  Param is named [mediaFiles] (not `media`) to avoid shadowing the [media] MediaManager. */
    private suspend fun saveMedia(mediaFiles: Map<String, ByteArray>): Map<String, String> {
        val out = HashMap<String, String>(mediaFiles.size)
        for ((original, bytes) in mediaFiles) {
            val ext = original.substringAfterLast('.', "").ifEmpty { "bin" }
            val isAudio = ext.lowercase() in AUDIO_EXTS
            out[original] = runCatching {
                if (isAudio) media.importAudio(bytes, ext) else media.importImage(bytes, ext)
            }.getOrNull() ?: continue
        }
        return out
    }

    override suspend fun import(cards: List<ApkgPreviewCard>, deckName: String) {
        val toImport = cards.filter { it.selected }
        if (toImport.isEmpty()) return
        val t = now()
        val deck = Deck(id = idGenerator(), name = deckName, dateCreated = t, lastModified = t)
        deckRepository.upsert(deck)
        for (c in toImport) {
            cardRepository.upsert(
                Card(
                    id = idGenerator(), front = c.front, back = c.back,
                    image = c.imageName, audioName = c.audioName, imagePath = null, audioPath = null,
                    deckId = deck.id, dateCreated = t, lastModified = t,
                    fsrsDue = t, fsrsState = CardState.New.value, source = "apkg",
                ),
            )
        }
    }

    private companion object {
        private val AUDIO_EXTS = setOf("mp3", "m4a", "ogg", "oga", "wav", "flac", "aac", "opus")
    }
}
