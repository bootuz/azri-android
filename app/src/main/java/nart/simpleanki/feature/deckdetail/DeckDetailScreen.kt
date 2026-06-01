package nart.simpleanki.feature.deckdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.ui.components.AzriCard
import nart.simpleanki.ui.theme.AzriTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DeckDetailScreen(
    deckId: String,
    onBack: () -> Unit,
    onStudy: () -> Unit,
    onAddCard: () -> Unit,
    onEditCard: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: DeckDetailViewModel = koinViewModel { parametersOf(deckId) },
) {
    val state by viewModel.uiState.collectAsState()
    DeckDetailContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onBack = onBack,
        onStudy = onStudy,
        onAddCard = onAddCard,
        onEditCard = onEditCard,
        onSettings = onSettings,
    )
}

/** Stateless deck-detail UI, decoupled from the ViewModel for testing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailContent(
    state: DeckDetailUiState,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onStudy: () -> Unit,
    onAddCard: () -> Unit,
    onEditCard: (String) -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.deckName.ifBlank { "Deck" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Deck settings") }
                    IconButton(onClick = onAddCard) { Icon(Icons.Default.Add, "Add card") }
                },
            )
        },
        floatingActionButton = {
            if (state.cards.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onStudy,
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    text = { Text("Study") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search cards") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.cards.isEmpty()) {
                Text(
                    "No cards yet. Tap + to add one.",
                    Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.visibleCards, key = { it.id }) { card ->
                        AzriCard(onClick = { onEditCard(card.id) }) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    card.front,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    card.back,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun previewCard(id: String, front: String, back: String) = Card(
    id = id, front = front, back = back, deckId = "d1",
    dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
)

@Preview(name = "Deck detail", showBackground = true)
@Composable
private fun DeckDetailPreview() {
    AzriTheme {
        DeckDetailContent(
            state = DeckDetailUiState(
                deckId = "d1", deckName = "Spanish 101",
                cards = listOf(
                    previewCard("1", "hola", "hello"),
                    previewCard("2", "gracias", "thank you"),
                    previewCard("3", "por favor", "please"),
                ),
            ),
            onQueryChange = {}, onBack = {}, onStudy = {}, onAddCard = {}, onEditCard = {}, onSettings = {},
        )
    }
}

@Preview(name = "Deck detail · empty", showBackground = true)
@Composable
private fun DeckDetailEmptyPreview() {
    AzriTheme {
        DeckDetailContent(
            state = DeckDetailUiState(deckId = "d1", deckName = "New deck"),
            onQueryChange = {}, onBack = {}, onStudy = {}, onAddCard = {}, onEditCard = {}, onSettings = {},
        )
    }
}
