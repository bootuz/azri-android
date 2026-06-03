package nart.simpleanki.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import nart.simpleanki.core.data.media.MediaManager
import org.koin.compose.koinInject
import java.io.File

/**
 * Displays a card image local-first: resolves [name] from on-device storage (falling back to
 * the cloud [cloudPath] and caching) via [MediaManager], then loads the file with Coil.
 */
@Composable
fun MediaImage(
    name: String,
    cloudPath: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    media: MediaManager = koinInject(),
) {
    var file by remember(name, cloudPath) { mutableStateOf<File?>(null) }
    LaunchedEffect(name, cloudPath) { file = media.resolve(name, cloudPath) }
    file?.let {
        AsyncImage(
            model = it,
            contentDescription = "Card image",
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}
