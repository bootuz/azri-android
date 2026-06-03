package nart.simpleanki.core.apkg

/** Reads a legacy (schema 11) collection: note types from `col.models` JSON, notes from the table. */
class LegacyApkgReader : AnkiCollectionReader {
    override fun read(db: AnkiSqlite): AnkiCollectionData {
        val modelsJson = db.query("SELECT models FROM col").firstOrNull()?.get("models") as? String
            ?: throw ApkgImportError.DatabaseCorrupted
        val noteTypes = LegacyNoteTypeParser.parse(modelsJson)

        val notes = db.query("SELECT id, guid, mid, flds, tags FROM notes").mapNotNull { row ->
            val id = (row["id"] as? Long) ?: return@mapNotNull null
            val guid = (row["guid"] as? String) ?: return@mapNotNull null
            val mid = (row["mid"] as? Long) ?: return@mapNotNull null
            val flds = (row["flds"] as? String) ?: return@mapNotNull null
            AnkiNote(
                id = id, guid = guid, modelId = mid,
                fields = flds.split('\u001F'),
                tags = (row["tags"] as? String ?: "").split(' ').filter { it.isNotEmpty() },
            )
        }
        return AnkiCollectionData(noteTypes, notes)
    }
}
