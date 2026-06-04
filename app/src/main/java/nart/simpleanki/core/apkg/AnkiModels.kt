package nart.simpleanki.core.apkg

/** A note type ("model") from the Anki collection. */
data class AnkiNoteType(
    val id: Long,
    val name: String,
    val fields: List<String>,
    val sortField: Int,
)

/** A single Anki note (one row of the `notes` table). */
data class AnkiNote(
    val id: Long,
    val guid: String,
    val modelId: Long,
    val fields: List<String>,
    val tags: List<String>,
)

/** What a reader extracts from the collection database (media is read separately). */
data class AnkiCollectionData(
    val noteTypes: List<AnkiNoteType>,
    val notes: List<AnkiNote>,
)

/** Fully parsed package: collection data + media (keyed by original filename). */
data class ParsedCollection(
    val noteTypes: List<AnkiNoteType>,
    val notes: List<AnkiNote>,
    val media: Map<String, ByteArray>,
)

/** A card ready for preview/import after field mapping + processing. */
data class ApkgPreviewCard(
    val front: String,
    val back: String,
    val imageName: String?,
    val audioName: String?,
    val selected: Boolean = true,
)

/** Which on-disk format the package uses. */
enum class ApkgFormat { LEGACY, V3 }

/** Import failures surfaced to the UI. */
sealed class ApkgImportError(message: String) : Exception(message) {
    object FileAccess : ApkgImportError("Unable to access the file.")
    object InvalidStructure : ApkgImportError("Invalid .apkg structure.")
    object MissingDatabase : ApkgImportError("The .apkg is missing its collection database.")
    object DatabaseCorrupted : ApkgImportError("Cannot read the .apkg database; it may be corrupted.")
    object UnsupportedSchema : ApkgImportError("This file uses a newer Anki format we don't support yet.")
    data class MediaExtractionFailed(val filename: String) : ApkgImportError("Failed to extract media: $filename")
}
