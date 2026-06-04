package nart.simpleanki.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsParamsTest {

    @Test fun cleansEventName_clipsAndReplacesSpaces() {
        val (name, _) = cleanAnalyticsParams("hello world event", emptyMap())
        assertEquals("hello_world_event", name)
        val (long, _) = cleanAnalyticsParams("x".repeat(50), emptyMap())
        assertEquals(40, long.length) // clipped to 40
    }

    @Test fun dropsNullValues() {
        val (_, params) = cleanAnalyticsParams("e", mapOf("a" to null, "b" to 1L))
        assertFalse(params.containsKey("a"))
        assertTrue(params.containsKey("b"))
    }

    @Test fun coercesValueTypes() {
        val (_, p) = cleanAnalyticsParams("e", mapOf(
            "i" to 3, "l" to 4L, "f" to 1.5f, "d" to 2.5, "bt" to true, "bf" to false, "s" to "hi",
        ))
        assertEquals(3L, p["i"]); assertEquals(4L, p["l"])
        assertEquals(2.5, p["d"]); assertEquals(1L, p["bt"]); assertEquals(0L, p["bf"])
        assertEquals("hi", p["s"])
        assertEquals(1.5, (p["f"] as Double), 0.001)
    }

    @Test fun stringValuesClippedTo100_unknownTypesStringified() {
        val (_, p) = cleanAnalyticsParams("e", mapOf("s" to "x".repeat(150), "list" to listOf(1, 2)))
        assertEquals(100, (p["s"] as String).length)
        assertEquals("[1, 2]", p["list"]) // unknown type -> toString, clipped to 100
    }

    @Test fun clipsLongKeysTo40() {
        val key = "k".repeat(50)
        val (_, p) = cleanAnalyticsParams("e", mapOf(key to 1L))
        assertTrue(p.keys.all { it.length <= 40 })
        assertEquals(1L, p.values.first())
    }

    @Test fun capsAt25Entries() {
        val many = (1..40).associate { "k$it" to it.toLong() }
        val (_, p) = cleanAnalyticsParams("e", many)
        assertEquals(25, p.size)
    }
}
