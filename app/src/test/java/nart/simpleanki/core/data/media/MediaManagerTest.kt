package nart.simpleanki.core.data.media

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun managerWith(uploader: FakeMediaUploader = FakeMediaUploader()) =
        Pair(MediaManager(LocalMediaStore(tmp.newFolder()), uploader), uploader)

    @Test fun saveImage_writesLocally_andReturnsName() = runTest {
        val (m, _) = managerWith()
        val name = m.saveImage(byteArrayOf(1, 2))
        assertTrue(name.endsWith(".jpg"))
        // Resolving with a null cloud path still finds the local file.
        val file = m.resolve(name, null)!!
        assertArrayEquals(byteArrayOf(1, 2), file.readBytes())
    }

    @Test fun resolve_localHit_doesNotDownload() = runTest {
        val (m, up) = managerWith()
        val name = m.saveImage(byteArrayOf(7))
        m.resolve(name, "users/u/images/$name")
        assertEquals(0, up.downloadCalls)
    }

    @Test fun resolve_cloudFallback_downloadsAndCaches() = runTest {
        val up = FakeMediaUploader().apply { uploaded["x.jpg"] = byteArrayOf(5, 6) }
        val (m, _) = managerWith(up)
        val first = m.resolve("x.jpg", "users/u/images/x.jpg")!!
        assertArrayEquals(byteArrayOf(5, 6), first.readBytes())
        assertEquals(1, up.downloadCalls)
        // Second resolve is a local hit — no second download.
        m.resolve("x.jpg", "users/u/images/x.jpg")
        assertEquals(1, up.downloadCalls)
    }

    @Test fun resolve_nullName_returnsNull() = runTest {
        val (m, _) = managerWith()
        assertNull(m.resolve(null, "users/u/images/x.jpg"))
    }

    @Test fun ensureUploaded_uploadsLocalOnly_andReturnsPath() = runTest {
        val (m, up) = managerWith()
        val name = m.saveImage(byteArrayOf(1))
        val path = m.ensureUploaded(name, null)
        assertEquals("users/u/images/$name", path)   // .jpg → images folder
        assertEquals(1, up.uploadPathCalls)
    }

    @Test fun ensureUploaded_alreadyUploaded_skips() = runTest {
        val (m, up) = managerWith()
        val name = m.saveImage(byteArrayOf(1))
        val path = m.ensureUploaded(name, "users/u/images/$name")
        assertEquals("users/u/images/$name", path)
        assertEquals(0, up.uploadPathCalls)
    }

    @Test fun prefetch_downloadsWhenMissing_skipsWhenPresent() = runTest {
        val up = FakeMediaUploader().apply { uploaded["y.m4a"] = byteArrayOf(3) }
        val (m, _) = managerWith(up)
        m.prefetch("y.m4a", "users/u/audio/y.m4a")
        assertEquals(1, up.downloadCalls)
        m.prefetch("y.m4a", "users/u/audio/y.m4a") // now local
        assertEquals(1, up.downloadCalls)
    }
}
