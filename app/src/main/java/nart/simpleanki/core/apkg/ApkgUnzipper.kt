package nart.simpleanki.core.apkg

import java.io.File
import java.util.zip.ZipInputStream

/** Extracts a .apkg (a zip) into [destDir]. Flat archive — no nested dirs expected. */
class ApkgUnzipper {
    fun unzip(apkg: File, destDir: File) {
        check(destDir.mkdirs() || destDir.isDirectory) { "Could not create extraction directory: $destDir" }
        ZipInputStream(apkg.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    // Guard against path traversal; archive entries are flat names like "0", "media".
                    val out = File(destDir, File(entry.name).name)
                    out.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
