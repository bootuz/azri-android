package nart.simpleanki.core.apkg

import org.junit.Assert.assertEquals
import org.junit.Test

class V3ApkgReaderTest {
    private fun db() = FakeAnkiSqlite(
        mapOf(
            "SELECT id, name FROM notetypes" to listOf(mapOf("id" to 7L, "name" to "Basic")),
            "SELECT ntid, ord, name FROM fields ORDER BY ntid, ord" to listOf(
                mapOf("ntid" to 7L, "ord" to 0L, "name" to "Front"),
                mapOf("ntid" to 7L, "ord" to 1L, "name" to "Back"),
            ),
            "SELECT id, guid, mid, flds, tags FROM notes" to listOf(
                mapOf("id" to 1L, "guid" to "g", "mid" to 7L, "flds" to "F\u001FB", "tags" to ""),
            ),
        ),
    )

    @Test fun readsNoteTypesFromTables_andNotes() {
        val data = V3ApkgReader().read(db())
        val nt = data.noteTypes.single()
        assertEquals(7L, nt.id); assertEquals(listOf("Front", "Back"), nt.fields)
        assertEquals(listOf("F", "B"), data.notes.single().fields)
    }

    @Test fun partitionsFieldsByNoteType() {
        val db = FakeAnkiSqlite(
            mapOf(
                "SELECT id, name FROM notetypes" to listOf(
                    mapOf("id" to 7L, "name" to "Basic"),
                    mapOf("id" to 9L, "name" to "Cloze"),
                ),
                "SELECT ntid, ord, name FROM fields ORDER BY ntid, ord" to listOf(
                    mapOf("ntid" to 7L, "ord" to 0L, "name" to "Front"),
                    mapOf("ntid" to 7L, "ord" to 1L, "name" to "Back"),
                    mapOf("ntid" to 9L, "ord" to 0L, "name" to "Text"),
                ),
                "SELECT id, guid, mid, flds, tags FROM notes" to emptyList(),
            ),
        )
        val types = V3ApkgReader().read(db).noteTypes.associateBy { it.id }
        assertEquals(listOf("Front", "Back"), types[7L]!!.fields)
        assertEquals(listOf("Text"), types[9L]!!.fields)
    }
}
