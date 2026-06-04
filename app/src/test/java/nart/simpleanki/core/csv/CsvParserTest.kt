package nart.simpleanki.core.csv

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParserTest {

    @Test fun detectsComma() {
        val p = CsvParser.parse("front,back\nhola,hello", hasHeader = true)
        assertEquals(listOf("front", "back"), p.headers)
        assertEquals(listOf(listOf("hola", "hello")), p.rows)
    }

    @Test fun detectsSemicolon() {
        val p = CsvParser.parse("front;back;extra\na;b;c", hasHeader = true)
        assertEquals(listOf("front", "back", "extra"), p.headers)
        assertEquals(listOf(listOf("a", "b", "c")), p.rows)
    }

    @Test fun detectsTab() {
        val p = CsvParser.parse("front\tback\nhola\thello", hasHeader = true)
        assertEquals(listOf("front", "back"), p.headers)
        assertEquals(listOf(listOf("hola", "hello")), p.rows)
    }

    @Test fun delimiterInsideQuotesIsNotCounted() {
        // The only real delimiter is the tab; the commas live inside a quoted field.
        val p = CsvParser.parse("\"a,b,c\"\tback\nx\ty", hasHeader = true)
        assertEquals(listOf("a,b,c", "back"), p.headers)
        assertEquals(listOf(listOf("x", "y")), p.rows)
    }

    @Test fun quotedFieldWithEmbeddedDelimiterAndNewline() {
        val text = "front,back\n\"line1\nline2\",\"a,b\""
        val p = CsvParser.parse(text, hasHeader = true)
        assertEquals(listOf(listOf("line1\nline2", "a,b")), p.rows)
    }

    @Test fun escapedDoubleQuotes() {
        val p = CsvParser.parse("front,back\n\"she said \"\"hi\"\"\",ok", hasHeader = true)
        assertEquals(listOf(listOf("she said \"hi\"", "ok")), p.rows)
    }

    @Test fun handlesCrLfLineEndings() {
        val p = CsvParser.parse("front,back\r\nhola,hello\r\n", hasHeader = true)
        assertEquals(listOf(listOf("hola", "hello")), p.rows)
    }

    @Test fun stripsUtf8Bom() {
        // The leading char is the BOM; written as an escape (never a literal char) to keep the source clean.
        val p = CsvParser.parse("\uFEFFfront,back\nhola,hello", hasHeader = true)
        assertEquals("front", p.headers.first())
    }

    @Test fun headerOffSynthesisesColumnNamesAndKeepsFirstRow() {
        val p = CsvParser.parse("hola,hello\nadios,bye", hasHeader = false)
        assertEquals(listOf("Column 1", "Column 2"), p.headers)
        assertEquals(listOf(listOf("hola", "hello"), listOf("adios", "bye")), p.rows)
    }

    @Test fun deduplicatesRepeatedHeaders() {
        val p = CsvParser.parse("word,word,word\na,b,c", hasHeader = true)
        assertEquals(listOf("word", "word_2", "word_3"), p.headers)
    }

    @Test fun ragdedRowsArePaddedToHeaderWidth() {
        val p = CsvParser.parse("a,b,c\n1,2\n3,4,5", hasHeader = true)
        assertEquals(listOf(listOf("1", "2", ""), listOf("3", "4", "5")), p.rows)
    }
}
