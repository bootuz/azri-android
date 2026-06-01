package nart.simpleanki.core.data.media

/** In-memory [MediaUploader] for unit tests. */
class FakeMediaUploader(
    var uploadResult: Result<MediaRef> = Result.success(MediaRef("img.jpg", "users/u/images/img.jpg")),
) : MediaUploader {
    var uploadCalls = 0
    override suspend fun uploadImage(bytes: ByteArray): Result<MediaRef> {
        uploadCalls++
        return uploadResult
    }
    override suspend fun downloadUrl(storagePath: String): Result<String> =
        Result.success("https://example.com/$storagePath")
}
