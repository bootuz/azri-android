package nart.simpleanki.core.apkg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ApkgFormatDetectorTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun detectsV3_whenAnki21bPresent() {
        val dir = tmp.newFolder(); File(dir, "collection.anki21b").writeText("x")
        assertEquals(ApkgFormat.V3, ApkgFormatDetector().detect(dir))
    }

    @Test fun detectsLegacy_forAnki21orAnki2() {
        val d1 = tmp.newFolder(); File(d1, "collection.anki21").writeText("x")
        assertEquals(ApkgFormat.LEGACY, ApkgFormatDetector().detect(d1))
        val d2 = tmp.newFolder(); File(d2, "collection.anki2").writeText("x")
        assertEquals(ApkgFormat.LEGACY, ApkgFormatDetector().detect(d2))
    }

    @Test fun missingDatabase_throws() {
        assertThrows(ApkgImportError.MissingDatabase::class.java) { ApkgFormatDetector().detect(tmp.newFolder()) }
    }
}
