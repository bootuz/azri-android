package nart.simpleanki.core.data.media

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/** Media upload/lookup seam; faked in tests. */
interface MediaUploader {
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
