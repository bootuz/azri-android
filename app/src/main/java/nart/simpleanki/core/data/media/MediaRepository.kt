package nart.simpleanki.core.data.media

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** A reference to an uploaded media file: the bare filename + its full Storage path. */
data class MediaRef(val name: String, val path: String)

/** Media upload/lookup seam; faked in tests. */
interface MediaUploader {
    suspend fun uploadImage(bytes: ByteArray): Result<MediaRef>
    suspend fun uploadAudio(bytes: ByteArray): Result<MediaRef>
    suspend fun downloadUrl(storagePath: String): Result<String>

    /** Uploads [bytes] under the given [name]; returns the cloud storage path. */
    suspend fun upload(name: String, bytes: ByteArray): Result<String>
    /** Downloads the raw bytes stored at [path]. */
    suspend fun downloadBytes(path: String): Result<ByteArray>
}

/**
 * Firebase Storage implementation. Paths match the iOS app exactly
 * (`users/{uid}/images/{filename}`) so images created on either platform display on both.
 */
class FirebaseMediaRepository(
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
) : MediaUploader {

    override suspend fun uploadImage(bytes: ByteArray): Result<MediaRef> =
        upload(bytes, folder = "images", ext = "jpg")

    override suspend fun uploadAudio(bytes: ByteArray): Result<MediaRef> =
        upload(bytes, folder = "audio", ext = "m4a")

    private suspend fun upload(bytes: ByteArray, folder: String, ext: String): Result<MediaRef> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val name = "${UUID.randomUUID()}.$ext"
        val path = "users/$uid/$folder/$name"
        storage.reference.child(path).putBytes(bytes).await()
        MediaRef(name = name, path = path)
    }

    override suspend fun downloadUrl(storagePath: String): Result<String> = runCatching {
        storage.reference.child(storagePath).downloadUrl.await().toString()
    }

    override suspend fun upload(name: String, bytes: ByteArray): Result<String> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val folder = if (name.endsWith(".m4a") || name.endsWith(".mp3")) "audio" else "images"
        val path = "users/$uid/$folder/$name"
        storage.reference.child(path).putBytes(bytes).await()
        path
    }

    override suspend fun downloadBytes(path: String): Result<ByteArray> = runCatching {
        storage.reference.child(path).getBytes(MAX_DOWNLOAD_BYTES).await()
    }

    private companion object {
        const val MAX_DOWNLOAD_BYTES = 25L * 1024 * 1024 // 25 MB ceiling per media file
    }
}
