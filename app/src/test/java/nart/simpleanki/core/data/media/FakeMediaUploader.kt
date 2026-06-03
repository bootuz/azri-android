package nart.simpleanki.core.data.media

/** In-memory [MediaUploader] for unit tests. */
class FakeMediaUploader : MediaUploader {
    /** name -> bytes, simulating the cloud. */
    val uploaded = mutableMapOf<String, ByteArray>()
    var uploadPathCalls = 0
    var downloadCalls = 0
    /** Override to simulate upload failure. */
    var uploadPathResult: ((String) -> Result<String>)? = null

    override suspend fun upload(name: String, bytes: ByteArray): Result<String> {
        uploadPathCalls++
        uploadPathResult?.let { return it(name) }
        uploaded[name] = bytes
        // Mirror FirebaseMediaRepository's folder inference so tests assert realistic paths.
        val folder = if (name.endsWith(".m4a") || name.endsWith(".mp3")) "audio" else "images"
        return Result.success("users/u/$folder/$name")
    }

    override suspend fun downloadBytes(path: String): Result<ByteArray> {
        downloadCalls++
        val name = path.substringAfterLast('/')
        return uploaded[name]?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("not found: $path"))
    }
}
