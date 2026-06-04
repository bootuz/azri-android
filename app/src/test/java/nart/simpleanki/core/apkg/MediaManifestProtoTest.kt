package nart.simpleanki.core.apkg

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class MediaManifestProtoTest {
    // Build a MediaEntries protobuf with two entries by hand.
    private fun entry(name: String): ByteArray {
        val nameBytes = name.toByteArray()
        val inner = ByteArrayOutputStream()
        inner.write(0x0A); writeVarint(inner, nameBytes.size); inner.write(nameBytes)  // field1 (name), wire type 2
        val innerBytes = inner.toByteArray()
        val outer = ByteArrayOutputStream()
        outer.write(0x0A); writeVarint(outer, innerBytes.size); outer.write(innerBytes)  // field1 (entries), wire type 2
        return outer.toByteArray()
    }
    private fun writeVarint(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (true) { val b = v and 0x7F; v = v ushr 7; if (v != 0) out.write(b or 0x80) else { out.write(b); break } }
    }

    @Test fun parsesEntryNamesInOrder() {
        val bytes = entry("a.jpg") + entry("b.mp3")
        assertEquals(listOf("a.jpg", "b.mp3"), MediaManifestProto.parseEntryNames(bytes))
    }

    @Test fun emptyInput_returnsEmpty() {
        assertEquals(emptyList<String>(), MediaManifestProto.parseEntryNames(ByteArray(0)))
    }

    @Test fun skipsSizeField_beforeName() {
        // MediaEntry with size (field 2, varint) BEFORE name (field 1) — skip() must handle it.
        val inner = ByteArrayOutputStream()
        inner.write(0x10); writeVarint(inner, 42)               // field 2 (size) varint
        val nb = "a.jpg".toByteArray()
        inner.write(0x0A); writeVarint(inner, nb.size); inner.write(nb)  // field 1 (name)
        val ib = inner.toByteArray()
        val outer = ByteArrayOutputStream()
        outer.write(0x0A); writeVarint(outer, ib.size); outer.write(ib)
        assertEquals(listOf("a.jpg"), MediaManifestProto.parseEntryNames(outer.toByteArray()))
    }

    @Test(expected = IllegalStateException::class)
    fun truncatedVarint_throws() {
        MediaManifestProto.parseEntryNames(byteArrayOf(0x0A, 0x80.toByte()))  // continuation bit set, then EOF
    }
}
