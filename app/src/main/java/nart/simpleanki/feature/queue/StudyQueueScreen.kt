package nart.simpleanki.feature.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nart.simpleanki.core.domain.model.ColorOption
import nart.simpleanki.ui.components.AzriCard
import nart.simpleanki.ui.components.ColorAccentIcon
import nart.simpleanki.ui.theme.AzriTheme
import nart.simpleanki.ui.theme.toColor
import org.koin.androidx.compose.koinViewModel

@Composable
fun StudyQueueScreen(
    onStudyAll: () -> Unit,
    onOpenDeck: (String) -> Unit,
    viewModel: StudyQueueViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    StudyQueueContent(state = state, onStudyAll = onStudyAll, onOpenDeck = onOpenDeck)
}

/** Stateless study-queue ("Today") UI, decoupled from the ViewModel for testing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyQueueContent(
    state: StudyQueueUiState,
    onStudyAll: () -> Unit,
    onOpenDeck: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            HeroCard(state, onStudyAll)
            if (state.decks.isNotEmpty()) {
                Text(
                    "Up next",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.decks, key = { it.deckId }) { deck ->
                        DeckQueueRow(deck, onClick = { onOpenDeck(deck.deckId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(state: StudyQueueUiState, onStudyAll: () -> Unit) {
    AzriCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.hasWork) {
                Text(
                    state.readyCount.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("cards ready", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "~${state.estimatedMinutes} min · ${state.newCount} new · ${state.dueCount} due",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onStudyAll,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Filled.School, contentDescription = null)
                    Text("Study", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("All caught up", style = MaterialTheme.typography.titleLarge)
                Text(
                    "You're all set for today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DeckQueueRow(deck: DeckQueueItem, onClick: () -> Unit) {
    AzriCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorAccentIcon(tint = deck.color.toColor()) {
                Icon(Icons.Filled.School, null, Modifier.size(18.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(deck.deckName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    queueSubtitle(deck),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun queueSubtitle(deck: DeckQueueItem): String {
    val parts = buildList {
        if (deck.dueCount > 0) add("${deck.dueCount} due")
        if (deck.newCount > 0) add("${deck.newCount} new")
    }
    return parts.joinToString(" · ")
}

@Preview(name = "Queue · has work", showBackground = true)
@Composable
private fun StudyQueuePreview() {
    AzriTheme {
        StudyQueueContent(
            state = StudyQueueUiState(
                loading = false, readyCount = 23, newCount = 8, dueCount = 15, estimatedMinutes = 4,
                decks = listOf(
                    DeckQueueItem("d1", "Spanish 101", ColorOption.Indigo, dueCount = 12, newCount = 3),
                    DeckQueueItem("d2", "Biology", ColorOption.Green, dueCount = 3, newCount = 5),
                ),
            ),
            onStudyAll = {}, onOpenDeck = {},
        )
    }
}

@Preview(name = "Queue · all caught up", showBackground = true)
@Composable
private fun StudyQueueEmptyPreview() {
    AzriTheme {
        StudyQueueContent(
            state = StudyQueueUiState(loading = false),
            onStudyAll = {}, onOpenDeck = {},
        )
    }
}
