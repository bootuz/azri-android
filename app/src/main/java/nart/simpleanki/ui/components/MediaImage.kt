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
import nart.simpleanki.core.data.media.MediaUploader
import org.koin.compose.koinInject

/**
 * Displays a card image stored in Firebase Storage. Resolves the download URL for the
 * given [imagePath] (the `users/{uid}/images/...` path shared with iOS) and loads it via Coil.
 */
@Composable
fun MediaImage(
    imagePath: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    uploader: MediaUploader = koinInject(),
) {
    var url by remember(imagePath) { mutableStateOf<String?>(null) }
    LaunchedEffect(imagePath) { url = uploader.downloadUrl(imagePath).getOrNull() }
    val resolved = url
    if (resolved != null) {
        AsyncImage(
            model = resolved,
            contentDescription = "Card image",
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}
