package nart.simpleanki.core.apkg

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ApkgMediaReaderTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun writeVarint(out: java.io.ByteArrayOutputStream, value: Int) {
        var v = value
        while (true) { val x = v and 0x7F; v = v ushr 7; if (v != 0) out.write(x or 0x80) else { out.write(x); break } }
    }

    @Test fun readsJsonManifest_mapsNumberedFilesToOriginalNames() {
        val dir = tmp.newFolder()
        File(dir, "media").writeText("""{"0":"a.jpg","1":"b.mp3"}""")
        File(dir, "0").writeBytes(byteArrayOf(1, 1))
        File(dir, "1").writeBytes(byteArrayOf(2, 2))

        val media = ApkgMediaReader().read(dir)
        assertEquals(setOf("a.jpg", "b.mp3"), media.keys)
        assertArrayEquals(byteArrayOf(1, 1), media["a.jpg"])
        assertArrayEquals(byteArrayOf(2, 2), media["b.mp3"])
    }

    @Test fun noManifest_returnsEmpty() {
        assertTrue(ApkgMediaReader().read(tmp.newFolder()).isEmpty())
    }

    @Test fun readsV3ProtobufManifest_mapsByIndex() {
        val dir = tmp.newFolder()
        // Build a 2-entry MediaEntries protobuf (same wire layout as MediaManifestProtoTest).
        fun entry(name: String): ByteArray {
            val nb = name.toByteArray()
            val inner = java.io.ByteArrayOutputStream()
            inner.write(0x0A); writeVarint(inner, nb.size); inner.write(nb)
            val ib = inner.toByteArray()
            val outer = java.io.ByteArrayOutputStream()
            outer.write(0x0A); writeVarint(outer, ib.size); outer.write(ib)
            return outer.toByteArray()
        }
        File(dir, "media").writeBytes(entry("a.jpg") + entry("b.mp3"))
        File(dir, "0").writeBytes(byteArrayOf(1, 1))
        File(dir, "1").writeBytes(byteArrayOf(2, 2))

        val media = ApkgMediaReader().read(dir)
        assertEquals(setOf("a.jpg", "b.mp3"), media.keys)
        assertArrayEquals(byteArrayOf(1, 1), media["a.jpg"])
        assertArrayEquals(byteArrayOf(2, 2), media["b.mp3"])
    }

    @Test fun malformedManifest_returnsEmpty_doesNotCrash() {
        val dir = tmp.newFolder()
        File(dir, "media").writeBytes(byteArrayOf(0x0A, 0x80.toByte()))  // truncated protobuf, not JSON
        assertEquals(emptyMap<String, ByteArray>(), ApkgMediaReader().read(dir))
    }
}
