package nart.simpleanki.core.data.media

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * On-device media storage. Files live under [dir] (typically `filesDir/media/`) and are
 * keyed by their logical filename (`<uuid>.<ext>`, the same name stored on the card).
 * [dir] is injected so the store is unit-testable against a temp directory.
 * [ioDispatcher] defaults to [Dispatchers.IO] and can be overridden in tests.
 */
class LocalMediaStore(
    private val dir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    init { dir.mkdirs() }

    fun fileFor(name: String): File = File(dir, name)

    fun exists(name: String): Boolean = fileFor(name).exists()

    suspend fun save(name: String, bytes: ByteArray): File = withContext(ioDispatcher) {
        val file = fileFor(name)
        file.writeBytes(bytes)
        file
    }

    fun delete(name: String) { fileFor(name).delete() }

    fun newName(ext: String): String = "${UUID.randomUUID()}.$ext"
}
