package nart.simpleanki.core.apkg

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyNoteTypeParserTest {
    // `col.models` is a JSON object keyed by model id; each model has name, flds[], sortf.
    private val json = """
        {
          "1411914114": {"name":"Basic","sortf":0,
            "flds":[{"name":"Front","ord":0},{"name":"Back","ord":1}]},
          "1607392319": {"name":"Cloze","sortf":0,
            "flds":[{"name":"Text","ord":0},{"name":"Extra","ord":1}]}
        }
    """.trimIndent()

    @Test fun parsesModels_idsNamesAndFieldsInOrder() {
        val types = LegacyNoteTypeParser.parse(json).sortedBy { it.name }
        assertEquals(2, types.size)
        val basic = types.first { it.name == "Basic" }
        assertEquals(1411914114L, basic.id)
        assertEquals(listOf("Front", "Back"), basic.fields)
        assertEquals(0, basic.sortField)
    }

    @Test fun malformedModels_areSkipped_validOnesParsed() {
        val json = """
            {
              "1": {"name":"Good","sortf":0,"flds":[{"name":"Front","ord":0}]},
              "2": {},
              "3": {"name":"NoFlds","sortf":0},
              "4": {"name":"","sortf":0,"flds":[{"name":"F","ord":0}]}
            }
        """.trimIndent()
        val types = LegacyNoteTypeParser.parse(json)
        assertEquals(1, types.size)
        assertEquals("Good", types.single().name)
    }

    @Test fun emptyObjectOrInvalidJson_returnsEmpty() {
        assertEquals(emptyList<AnkiNoteType>(), LegacyNoteTypeParser.parse("{}"))
        assertEquals(emptyList<AnkiNoteType>(), LegacyNoteTypeParser.parse("not json"))
    }
}
