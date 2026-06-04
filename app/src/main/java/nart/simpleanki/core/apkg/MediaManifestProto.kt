package nart.simpleanki.core.apkg

/**
 * Minimal reader for Anki's `MediaEntries` protobuf — extracts the ordered list of entry
 * names (field 1 of each MediaEntry, which is field 1 of MediaEntries). All other fields
 * (size, sha1) are skipped. Avoids a full protobuf dependency.
 */
object MediaManifestProto {
    fun parseEntryNames(bytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        val r = Reader(bytes)
        while (!r.eof()) {
            val tag = r.varint().toInt()
            if (tag ushr 3 == 1 && tag and 7 == 2) {       // entries: length-delimited submessage
                val sub = r.bytes(r.varint().toInt())
                names += entryName(sub)
            } else {
                r.skip(tag and 7)
            }
        }
        return names
    }

    private fun entryName(sub: ByteArray): String {
        val r = Reader(sub)
        var name = ""
        while (!r.eof()) {
            val tag = r.varint().toInt()
            if (tag ushr 3 == 1 && tag and 7 == 2) {       // name: string
                name = String(r.bytes(r.varint().toInt()))
            } else {
                r.skip(tag and 7)
            }
        }
        return name
    }

    private class Reader(private val b: ByteArray) {
        private var i = 0
        fun eof() = i >= b.size
        fun varint(): Long {
            var result = 0L; var shift = 0
            while (true) {
                if (i >= b.size) throw IllegalStateException("truncated varint")
                if (shift >= 64) throw IllegalStateException("varint too long")
                val byte = b[i++].toInt() and 0xFF
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) break
                shift += 7
            }
            return result
        }
        fun bytes(n: Int): ByteArray {
            require(n >= 0 && i + n <= b.size) { "invalid length $n" }
            val out = b.copyOfRange(i, i + n); i += n; return out
        }
        fun skip(wireType: Int) {
            when (wireType) {
                0 -> varint()
                1 -> { require(i + 8 <= b.size) { "truncated fixed64" }; i += 8 }
                2 -> { val n = varint(); require(n >= 0 && i + n <= b.size) { "invalid skip length $n" }; i += n.toInt() }
                5 -> { require(i + 4 <= b.size) { "truncated fixed32" }; i += 4 }
                else -> error("unsupported wire type $wireType")
            }
        }
    }
}
