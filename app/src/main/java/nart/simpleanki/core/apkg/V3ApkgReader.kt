package nart.simpleanki.core.apkg

/** Reads a schema-18 collection: note-type field names from the notetypes/fields tables. */
class V3ApkgReader : AnkiCollectionReader {
    override fun read(db: AnkiSqlite): AnkiCollectionData {
        val names = db.query("SELECT id, name FROM notetypes").mapNotNull {
            val id = it["id"] as? Long ?: return@mapNotNull null
            id to (it["name"] as? String ?: "")
        }.toMap()
        val fieldsByType = HashMap<Long, MutableList<String>>()
        db.query("SELECT ntid, ord, name FROM fields ORDER BY ntid, ord").forEach { row ->
            val ntid = row["ntid"] as? Long ?: return@forEach
            fieldsByType.getOrPut(ntid) { mutableListOf() }.add(row["name"] as? String ?: "")
        }
        val noteTypes = names.map { (id, name) ->
            AnkiNoteType(id = id, name = name, fields = fieldsByType[id].orEmpty(), sortField = 0)
        }

        val notes = db.query("SELECT id, guid, mid, flds, tags FROM notes").mapNotNull { row ->
            val id = (row["id"] as? Long) ?: return@mapNotNull null
            val guid = (row["guid"] as? String) ?: return@mapNotNull null
            val mid = (row["mid"] as? Long) ?: return@mapNotNull null
            val flds = (row["flds"] as? String) ?: return@mapNotNull null
            AnkiNote(id, guid, mid, flds.split('\u001F'),
                (row["tags"] as? String ?: "").split(' ').filter { it.isNotEmpty() })
        }
        return AnkiCollectionData(noteTypes, notes)
    }
}
