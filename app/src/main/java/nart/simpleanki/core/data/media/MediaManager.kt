package nart.simpleanki.core.data.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local-first media policy. Reads/writes media on-device by default and only touches the
 * cloud ([MediaUploader]) as part of premium sync. The single seam used by the card form
 * (create), the display components (resolve), and the sync engine (ensureUploaded/prefetch).
 */
class MediaManager(
    private val local: LocalMediaStore,
    private val uploader: MediaUploader,
) {
    /** Create: persist [bytes] locally; returns the new filename. No network. */
    suspend fun saveImage(bytes: ByteArray): String = saveLocal(bytes, "jpg")
    suspend fun saveAudio(bytes: ByteArray): String = saveLocal(bytes, "m4a")

    /** Import: persist [bytes] locally under the given [ext] (preserves the source format). */
    suspend fun importImage(bytes: ByteArray, ext: String): String = saveLocal(bytes, ext.lowercase())
    suspend fun importAudio(bytes: ByteArray, ext: String): String = saveLocal(bytes, ext.lowercase())

    private suspend fun saveLocal(bytes: ByteArray, ext: String): String {
        val name = local.newName(ext)
        local.save(name, bytes)
        return name
    }

    /** Display: local file if present; else download from [cloudPath], cache, return it. */
    suspend fun resolve(name: String?, cloudPath: String?): File? {
        if (name == null) return null
        if (local.exists(name)) return local.fileFor(name)
        if (cloudPath == null) return null
        return uploader.downloadBytes(cloudPath).getOrNull()?.let { local.save(name, it) }
    }

    /** Push: upload a local-only file; returns its cloud path (existing path if already up). */
    suspend fun ensureUploaded(name: String?, cloudPath: String?): String? {
        if (name == null) return cloudPath
        if (cloudPath != null) return cloudPath
        if (!local.exists(name)) return null
        val bytes = withContext(Dispatchers.IO) { local.fileFor(name).readBytes() }
        return uploader.upload(name, bytes).getOrNull()
    }

    /** Pull: download + cache a referenced file that isn't local yet. */
    suspend fun prefetch(name: String?, cloudPath: String?) {
        if (name == null || cloudPath == null || local.exists(name)) return
        uploader.downloadBytes(cloudPath).getOrNull()?.let { local.save(name, it) }
    }

    fun delete(name: String?) { if (name != null) local.delete(name) }
}
