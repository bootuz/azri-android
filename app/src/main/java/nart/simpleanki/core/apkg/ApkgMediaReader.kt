package nart.simpleanki.core.apkg

import org.json.JSONObject
import java.io.File

/**
 * Builds an originalFilename → bytes map from an extracted .apkg directory.
 * Legacy packages ship a JSON `media` manifest ("0" -> "name.jpg") and uncompressed
 * numbered files. (V3 zstd+protobuf handling is added in a later task.)
 */
class ApkgMediaReader {
    fun read(extractedDir: File): Map<String, ByteArray> {
        val manifest = File(extractedDir, "media")
        if (!manifest.exists()) return emptyMap()
        val json = runCatching { JSONObject(manifest.readText()) }.getOrNull() ?: return emptyMap()

        val result = LinkedHashMap<String, ByteArray>()
        for (number in json.keys()) {
            val original = json.optString(number).ifEmpty { continue }
            val file = File(extractedDir, number)
            if (file.exists()) {
                // Last-write-wins if two manifest entries share an original name (malformed manifest).
                result[original] = file.readBytes()
            }
        }
        return result
    }
}
