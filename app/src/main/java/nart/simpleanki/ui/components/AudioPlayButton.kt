package nart.simpleanki.ui.components

import android.media.MediaPlayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import nart.simpleanki.core.data.media.MediaManager
import org.koin.compose.koinInject

/** Plays a card's audio local-first (resolves the file via [MediaManager], streams via MediaPlayer). */
@Composable
fun AudioPlayButton(
    name: String,
    cloudPath: String?,
    media: MediaManager = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose { runCatching { player.release() } }
    }
    IconButton(onClick = {
        scope.launch {
            val file = media.resolve(name, cloudPath) ?: return@launch
            runCatching {
                player.reset()
                player.setDataSource(file.absolutePath)
                player.setOnPreparedListener { it.start() }
                player.prepareAsync()
            }
        }
    }) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Play audio")
    }
}
