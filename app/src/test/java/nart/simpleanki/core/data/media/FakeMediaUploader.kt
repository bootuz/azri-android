package nart.simpleanki.core.data.media

/** In-memory [MediaUploader] for unit tests. */
class FakeMediaUploader(
    var uploadResult: Result<MediaRef> = Result.success(MediaRef("img.jpg", "users/u/images/img.jpg")),
) : MediaUploader {
    var uploadCalls = 0
    var audioUploadCalls = 0
    var audioUploadResult: Result<MediaRef> = Result.success(MediaRef("sound.m4a", "users/u/audio/sound.m4a"))
    override suspend fun uploadImage(bytes: ByteArray): Result<MediaRef> {
        uploadCalls++
        return uploadResult
    }
    override suspend fun uploadAudio(bytes: ByteArray): Result<MediaRef> {
        audioUploadCalls++
        return audioUploadResult
    }
    override suspend fun downloadUrl(storagePath: String): Result<String> =
        Result.success("https://example.com/$storagePath")
}
