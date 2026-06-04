package nart.simpleanki.core.apkg

import java.io.File

/** Picks the package format from the extracted directory's database file. */
class ApkgFormatDetector {
    fun detect(extractedDir: File): ApkgFormat = when {
        File(extractedDir, "collection.anki21b").exists() -> ApkgFormat.V3
        File(extractedDir, "collection.anki21").exists() ||
            File(extractedDir, "collection.anki2").exists() -> ApkgFormat.LEGACY
        else -> throw ApkgImportError.MissingDatabase
    }

    /** The database filename for a detected legacy package (prefers .anki21). */
    fun legacyDbFile(extractedDir: File): File =
        File(extractedDir, "collection.anki21").takeIf { it.exists() }
            ?: File(extractedDir, "collection.anki2")
}
