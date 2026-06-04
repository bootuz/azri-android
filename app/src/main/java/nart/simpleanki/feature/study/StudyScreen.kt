package nart.simpleanki.feature.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Rating
import nart.simpleanki.ui.theme.AzriTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import nart.simpleanki.di.StudyArgs

@Composable
fun StudyScreen(
    deckId: String?,
    onDone: () -> Unit,
    folderId: String? = null,
    viewModel: StudyViewModel = koinViewModel { parametersOf(StudyArgs(deckId = deckId, folderId = folderId)) },
) {
    val state by viewModel.uiState.collectAsState()
    StudyContent(
        state = state,
        onReveal = viewModel::onReveal,
        onRate = viewModel::onRate,
        onDone = onDone,
    )
}

/** Stateless study UI, decoupled from the ViewModel for testing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyContent(
    state: StudyUiState,
    onReveal: () -> Unit,
    onRate: (Rating) -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.finished) "Done" else "Studying (${state.remaining} left)") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Default.Close, contentDescription = "Close") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.finished -> SessionSummary(state, onDone)
                else -> StudyCard(state, onReveal, onRate)
            }
        }
    }
}

@Composable
private fun StudyCard(state: StudyUiState, onReveal: () -> Unit, onRate: (Rating) -> Unit) {
    val card = state.current ?: return
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // key(card.id) resets the flip animation per card, so the next card appears on its
        // front instantly with no reverse-flip.
        key(card.id) {
            nart.simpleanki.ui.components.FlipCard(
                card = card,
                revealed = state.isRevealed,
                onFlip = onReveal,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        if (!state.isRevealed) {
            if (state.showFlipHint) {
                Row(
                    Modifier.fillMaxWidth().height(50.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tap to flip",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Keep the layout stable once the hint is gone.
                Spacer(Modifier.height(50.dp))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // iOS rating colors (SwiftUI system): again=pink, hard=orange, good=indigo, easy=mint.
                RatingButton("Again", state.ratingIntervals[Rating.Again], Color(0xFFFF2D55), Modifier.weight(1f)) { onRate(Rating.Again) }
                RatingButton("Hard", state.ratingIntervals[Rating.Hard], Color(0xFFFF9500), Modifier.weight(1f)) { onRate(Rating.Hard) }
                RatingButton("Good", state.ratingIntervals[Rating.Good], Color(0xFF5856D6), Modifier.weight(1f)) { onRate(Rating.Good) }
                RatingButton("Easy", state.ratingIntervals[Rating.Easy], Color(0xFF00C7BE), Modifier.weight(1f)) { onRate(Rating.Easy) }
            }
        }
    }
}

@Composable
private fun RatingButton(label: String, interval: String?, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(if (interval != null) 60.dp else 48.dp),
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            if (interval != null) {
                Text(
                    interval,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun SessionSummary(state: StudyUiState, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Session complete", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("${state.completed} cards reviewed", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Rating.entries.forEach { rating ->
            val count = state.ratingCounts[rating] ?: 0
            if (count > 0) Text("${rating.name}: $count")
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onDone) { Text("Done") }
    }
}

private val previewStudyCard = Card(
    id = "c1", front = "¿Cómo estás?", back = "How are you?", deckId = "d1",
    dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
)

@Preview(name = "Study · question (hint)", showBackground = true)
@Composable
private fun StudyQuestionPreview() {
    AzriTheme {
        StudyContent(
            state = StudyUiState(loading = false, current = previewStudyCard, isRevealed = false, showFlipHint = true, remaining = 5),
            onReveal = {}, onRate = {}, onDone = {},
        )
    }
}

@Preview(name = "Study · question (no hint)", showBackground = true)
@Composable
private fun StudyQuestionNoHintPreview() {
    AzriTheme {
        StudyContent(
            state = StudyUiState(loading = false, current = previewStudyCard, isRevealed = false, showFlipHint = false, remaining = 5),
            onReveal = {}, onRate = {}, onDone = {},
        )
    }
}

@Preview(name = "Study · answer", showBackground = true)
@Composable
private fun StudyAnswerPreview() {
    AzriTheme {
        StudyContent(
            state = StudyUiState(
                loading = false, current = previewStudyCard, isRevealed = true, remaining = 5,
                ratingIntervals = mapOf(
                    Rating.Again to "< 1m", Rating.Hard to "8m",
                    Rating.Good to "4d", Rating.Easy to "9d",
                ),
            ),
            onReveal = {}, onRate = {}, onDone = {},
        )
    }
}

@Preview(name = "Study · summary", showBackground = true)
@Composable
private fun StudySummaryPreview() {
    AzriTheme {
        StudyContent(
            state = StudyUiState(
                loading = false, finished = true, completed = 12,
                ratingCounts = mapOf(Rating.Again to 2, Rating.Good to 8, Rating.Easy to 2),
            ),
            onReveal = {}, onRate = {}, onDone = {},
        )
    }
}
