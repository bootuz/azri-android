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
    suspend fun downloadUrl(storagePath: String): Result<String>
}

/**
 * Firebase Storage implementation. Paths match the iOS app exactly
 * (`users/{uid}/images/{filename}`) so images created on either platform display on both.
 */
class FirebaseMediaRepository(
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
) : MediaUploader {

    override suspend fun uploadImage(bytes: ByteArray): Result<MediaRef> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val name = "${UUID.randomUUID()}.jpg"
        val path = "users/$uid/images/$name"
        storage.reference.child(path).putBytes(bytes).await()
        MediaRef(name = name, path = path)
    }

    override suspend fun downloadUrl(storagePath: String): Result<String> = runCatching {
        storage.reference.child(storagePath).downloadUrl.await().toString()
    }
}
