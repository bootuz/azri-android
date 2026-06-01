package nart.simpleanki.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records microphone audio to a temporary AAC/M4A file and returns the bytes on stop.
 * Caller must hold RECORD_AUDIO permission before [start].
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start() {
        val file = File(context.cacheDir, "rec_${System.nanoTime()}.m4a")
        outputFile = file
        @Suppress("DEPRECATION")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        rec.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = rec
    }

    /** Stops recording and returns the recorded bytes (or null on failure). */
    fun stop(): ByteArray? {
        val rec = recorder ?: return null
        return runCatching {
            rec.stop()
            rec.release()
            outputFile?.readBytes()
        }.getOrNull().also {
            recorder = null
            outputFile?.delete()
            outputFile = null
        }
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
