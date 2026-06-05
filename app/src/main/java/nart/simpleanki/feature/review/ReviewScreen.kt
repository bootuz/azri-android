package nart.simpleanki.feature.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.di.StudyArgs
import nart.simpleanki.ui.components.FlipCard
import nart.simpleanki.ui.theme.AzriTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ReviewScreen(
    deckId: String?,
    onDone: () -> Unit,
    folderId: String? = null,
    viewModel: ReviewViewModel = koinViewModel { parametersOf(StudyArgs(deckId = deckId, folderId = folderId)) },
) {
    val state by viewModel.uiState.collectAsState()
    ReviewContent(state = state, onDone = onDone)
}

/** Stateless review carousel, decoupled from the ViewModel for previews. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewContent(state: ReviewUiState, onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { state.cards.size })
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.cards.isNotEmpty()) {
                        Text("${pagerState.currentPage + 1} of ${state.cards.size}")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    TextButton(onClick = onDone) { Text("Quit") }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading -> CircularProgressIndicator()
                state.cards.isEmpty() -> EmptyReview(onDone)
                else -> {
                    // Flip resets when the page changes (mirrors iOS clearing flips on scroll).
                    var revealed by remember(pagerState.currentPage) { mutableStateOf(false) }
                    var showHint by remember { mutableStateOf(true) }
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        val card = state.cards[page]
                        key(card.id) {
                            FlipCard(
                                card = card,
                                revealed = page == pagerState.currentPage && revealed,
                                onFlip = {
                                    revealed = true
                                    showHint = false
                                },
                                modifier = Modifier.fillMaxSize().padding(20.dp),
                            )
                        }
                    }
                    if (showHint) {
                        Row(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
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
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyReview(onDone: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "No cards to review here.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDone) { Text("Close") }
    }
}

private fun previewCard(id: String, front: String, back: String) = Card(
    id = id, front = front, back = back, deckId = "d",
    dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.Review.value,
)

@Preview(name = "Review · populated", showBackground = true)
@Composable
private fun ReviewPopulatedPreview() {
    AzriTheme {
        ReviewContent(
            state = ReviewUiState(
                loading = false,
                cards = listOf(
                    previewCard("1", "hola", "hello"),
                    previewCard("2", "adiós", "goodbye"),
                ),
            ),
            onDone = {},
        )
    }
}

@Preview(name = "Review · empty", showBackground = true)
@Composable
private fun ReviewEmptyPreview() {
    AzriTheme {
        ReviewContent(state = ReviewUiState(loading = false, cards = emptyList()), onDone = {})
    }
}
