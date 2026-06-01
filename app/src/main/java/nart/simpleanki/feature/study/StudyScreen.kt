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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nart.simpleanki.core.domain.model.Rating
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun StudyScreen(
    deckId: String,
    onDone: () -> Unit,
    viewModel: StudyViewModel = koinViewModel { parametersOf(deckId) },
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
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        card.imagePath?.let { path ->
            nart.simpleanki.ui.components.MediaImage(path, Modifier.fillMaxWidth().height(160.dp))
            Spacer(Modifier.height(16.dp))
        }
        Text(card.front, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        card.audioPath?.let { path ->
            nart.simpleanki.ui.components.AudioPlayButton(path)
        }
        if (state.isRevealed) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(Modifier.fillMaxWidth(0.6f))
            Spacer(Modifier.height(16.dp))
            Text(card.back, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(48.dp))
        if (!state.isRevealed) {
            Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) { Text("Show answer") }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingButton("Again", Modifier.weight(1f)) { onRate(Rating.Again) }
                RatingButton("Hard", Modifier.weight(1f)) { onRate(Rating.Hard) }
                RatingButton("Good", Modifier.weight(1f)) { onRate(Rating.Good) }
                RatingButton("Easy", Modifier.weight(1f)) { onRate(Rating.Easy) }
            }
        }
    }
}

@Composable
private fun RatingButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
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
