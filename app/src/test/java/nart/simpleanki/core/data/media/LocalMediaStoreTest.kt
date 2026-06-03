package nart.simpleanki.core.data.media

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalMediaStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store() = LocalMediaStore(tmp.newFolder("media"))

    @Test fun save_thenExists_andReadsBack() = runTest {
        val store = store()
        assertFalse(store.exists("a.jpg"))
        val file = store.save("a.jpg", byteArrayOf(1, 2, 3))
        assertTrue(store.exists("a.jpg"))
        assertEquals("a.jpg", file.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
    }

    @Test fun delete_removesFile() = runTest {
        val store = store()
        store.save("b.m4a", byteArrayOf(9))
        store.delete("b.m4a")
        assertFalse(store.exists("b.m4a"))
    }

    @Test fun newName_hasExtension_andIsUnique() {
        val store = store()
        val a = store.newName("jpg")
        val b = store.newName("jpg")
        assertTrue(a.endsWith(".jpg"))
        assertTrue(a != b)
    }

    @Test fun delete_nonExistentFile_doesNotThrow() {
        val store = store()
        store.delete("never_saved.jpg") // no-op, not an exception
    }
}
