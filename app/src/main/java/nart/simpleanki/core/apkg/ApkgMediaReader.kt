package nart.simpleanki.core.apkg

import com.github.luben.zstd.ZstdInputStream
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Builds an originalFilename → bytes map from an extracted .apkg directory.
 *
 * Supports two manifest formats:
 * - Legacy: JSON manifest (`{"0":"name.jpg"}`) + uncompressed numbered files.
 * - V3: zstd-compressed protobuf `MediaEntries` manifest + (optionally) zstd-compressed
 *   numbered files. Entries map to numbered files by index (entry[i] → file "i").
 */
class ApkgMediaReader {
    fun read(extractedDir: File): Map<String, ByteArray> {
        val manifest = File(extractedDir, "media")
        if (!manifest.exists()) return emptyMap()
        val raw = manifest.readBytes()

        // Legacy: JSON manifest + uncompressed numbered files.
        runCatching { JSONObject(String(raw)) }.getOrNull()?.let { json ->
            val result = LinkedHashMap<String, ByteArray>()
            for (number in json.keys()) {
                val original = json.optString(number).ifEmpty { continue }
                val f = File(extractedDir, number)
                if (f.exists()) {
                    // Last-write-wins if two manifest entries share an original name (malformed manifest).
                    result[original] = f.readBytes()
                }
            }
            return result
        }

        // V3: zstd + protobuf manifest; entries map to numbered files by index. Best-effort:
        // a malformed manifest degrades to no media (text-only cards) rather than crashing import.
        val names = runCatching { MediaManifestProto.parseEntryNames(maybeUnzstd(raw)) }.getOrElse { return emptyMap() }
        val result = LinkedHashMap<String, ByteArray>()
        names.forEachIndexed { index, original ->
            val f = File(extractedDir, index.toString())
            if (original.isNotEmpty() && f.exists()) {
                runCatching { result[original] = maybeUnzstd(f.readBytes()) }
            }
        }
        return result
    }

    private fun maybeUnzstd(bytes: ByteArray): ByteArray {
        // zstd magic: 0x28 B5 2F FD
        val isZstd = bytes.size >= 4 && bytes[0] == 0x28.toByte() && bytes[1] == 0xB5.toByte() &&
            bytes[2] == 0x2F.toByte() && bytes[3] == 0xFD.toByte()
        return if (isZstd) ZstdInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() } else bytes
    }
}
