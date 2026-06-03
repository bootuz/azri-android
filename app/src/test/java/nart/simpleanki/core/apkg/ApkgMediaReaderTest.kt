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
}
