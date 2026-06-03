package nart.simpleanki.core.data.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * On-device media storage. Files live under [dir] (typically `filesDir/media/`) and are
 * keyed by their logical filename (`<uuid>.<ext>`, the same name stored on the card).
 * [dir] is injected so the store is unit-testable against a temp directory.
 */
class LocalMediaStore(private val dir: File) {
    init { dir.mkdirs() }

    fun fileFor(name: String): File = File(dir, name)

    fun exists(name: String): Boolean = fileFor(name).exists()

    suspend fun save(name: String, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val file = fileFor(name)
        file.writeBytes(bytes)
        file
    }

    fun delete(name: String) { fileFor(name).delete() }

    fun newName(ext: String): String = "${UUID.randomUUID()}.$ext"
}
