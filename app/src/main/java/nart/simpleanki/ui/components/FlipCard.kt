package nart.simpleanki.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.ui.theme.AzriTheme

/**
 * Reusable 3D flip card for the study screen. Mirrors iOS `FlipCardView`.
 *
 * Tapping the card while [revealed] is false calls [onFlip]. The container rotates 0 -> 180
 * degrees on the Y axis; while the rotation is past 90 degrees the answer face is shown,
 * itself counter-rotated 180 degrees so its text reads upright instead of mirrored. The image
 * is the question prompt, so it appears on the front only; audio replay appears on both faces.
 *
 * Callers should wrap this in `key(card.id)` so the animation resets to the front (no
 * reverse-flip) when the next card appears.
 */
@Composable
fun FlipCard(
    card: Card,
    revealed: Boolean,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (revealed) 180f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "flip",
    )
    AzriCard(
        onClick = if (!revealed) onFlip else null,
        modifier = modifier.graphicsLayer {
            rotationY = rotation
            cameraDistance = 12f * density
        },
    ) {
        // Reading rotation here is intentional: swapping faces at 90° avoids composing both MediaImage/AudioPlayButton at once; the per-frame recomposition over ~450ms is negligible.
        if (rotation <= 90f) {
            CardFace(
                label = "QUESTION",
                text = card.front,
                textStyle = MaterialTheme.typography.headlineSmall,
                textColor = MaterialTheme.colorScheme.onSurface,
                imageName = card.image,
                imagePath = card.imagePath,
                audioName = card.audioName,
                audioPath = card.audioPath,
            )
        } else {
            // Counter-rotate so the answer reads upright rather than mirrored.
            CardFace(
                modifier = Modifier.graphicsLayer { rotationY = 180f },
                label = "ANSWER",
                text = card.back,
                textStyle = MaterialTheme.typography.titleLarge,
                textColor = MaterialTheme.colorScheme.primary, // answer in the accent color (per design spec)
                // Image is the question prompt — front only. Audio replays on the answer.
                imageName = null,
                imagePath = null,
                audioName = card.audioName,
                audioPath = card.audioPath,
            )
        }
    }
}

@Composable
private fun CardFace(
    label: String,
    text: String,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
    imageName: String? = null,
    imagePath: String? = null,
    audioName: String? = null,
    audioPath: String? = null,
) {
    // Center the content when it fits; scroll when it overflows (heightIn(min = maxHeight)
    // forces the column to at least fill the card so Arrangement.Center works, but lets it
    // grow past the viewport for long text). Mirrors iOS's ScrollView { ... }.minHeight.
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(12.dp))
            imageName?.let { name ->
                MediaImage(name, imagePath, Modifier.fillMaxWidth().height(160.dp))
                Spacer(Modifier.height(16.dp))
            }
            Text(text, style = textStyle, color = textColor, textAlign = TextAlign.Center)
            audioName?.let { name ->
                Spacer(Modifier.height(12.dp))
                AudioPlayButton(name, audioPath)
            }
        }
    }
}

private val previewFlipCard = Card(
    id = "c1", front = "¿Cómo estás?", back = "How are you?", deckId = "d1",
    dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
)

@Preview(name = "FlipCard · front", showBackground = true)
@Composable
private fun FlipCardFrontPreview() {
    AzriTheme {
        FlipCard(
            previewFlipCard, revealed = false, onFlip = {},
            modifier = Modifier.fillMaxWidth().height(400.dp).padding(20.dp),
        )
    }
}

@Preview(name = "FlipCard · back", showBackground = true)
@Composable
private fun FlipCardBackPreview() {
    AzriTheme {
        FlipCard(
            previewFlipCard, revealed = true, onFlip = {},
            modifier = Modifier.fillMaxWidth().height(400.dp).padding(20.dp),
        )
    }
}

private val previewLongCard = Card(
    id = "c2",
    front = "Explain the difference between the present perfect and the simple past tense, " +
        "with at least three examples of each, and describe when a learner should prefer one " +
        "over the other in everyday conversation. Then summarize the key rule in one sentence.",
    back = "The present perfect links a past action to the present; the simple past describes a " +
        "finished action at a definite time.",
    deckId = "d1",
    dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
)

@Preview(name = "FlipCard · long text", showBackground = true)
@Composable
private fun FlipCardLongTextPreview() {
    AzriTheme {
        FlipCard(
            previewLongCard, revealed = false, onFlip = {},
            modifier = Modifier.fillMaxWidth().height(300.dp).padding(20.dp),
        )
    }
}
