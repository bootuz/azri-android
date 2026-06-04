package nart.simpleanki.core.apkg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class LegacyApkgReaderTest {
    private val modelsJson = """{"55":{"name":"Basic","sortf":0,"flds":[{"name":"Front","ord":0},{"name":"Back","ord":1}]}}"""

    private fun db() = FakeAnkiSqlite(
        mapOf(
            "SELECT models FROM col" to listOf(mapOf("models" to modelsJson)),
            "SELECT id, guid, mid, flds, tags FROM notes" to listOf(
                mapOf("id" to 1L, "guid" to "g1", "mid" to 55L, "flds" to "Front1\u001FBack1", "tags" to " tag1 tag2 "),
                mapOf("id" to 2L, "guid" to "g2", "mid" to 55L, "flds" to "Front2\u001FBack2", "tags" to ""),
            ),
        ),
    )

    @Test fun readsNoteTypesAndNotes() {
        val sqlite = db()
        val data = LegacyApkgReader().read(sqlite)
        assertEquals(listOf("Front", "Back"), data.noteTypes.single().fields)
        assertEquals(2, data.notes.size)
        assertEquals(listOf("Front1", "Back1"), data.notes[0].fields)
        assertEquals(55L, data.notes[0].modelId)
        assertEquals(listOf("tag1", "tag2"), data.notes[0].tags)
        assertEquals(emptyList<String>(), data.notes[1].tags)
        assertFalse(sqlite.closed)   // read() must not close; the caller (service) owns closing
    }

    @Test fun missingModelsColumn_throwsDatabaseCorrupted() {
        val db = FakeAnkiSqlite(
            mapOf(
                "SELECT models FROM col" to emptyList(),
                "SELECT id, guid, mid, flds, tags FROM notes" to emptyList(),
            ),
        )
        assertThrows(ApkgImportError.DatabaseCorrupted::class.java) { LegacyApkgReader().read(db) }
    }
}
