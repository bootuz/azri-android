package nart.simpleanki.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.filter
import nart.simpleanki.feature.cardform.CardFormScreen
import nart.simpleanki.feature.deckdetail.DeckDetailScreen
import nart.simpleanki.feature.decksettings.DeckEditScreen
import nart.simpleanki.feature.library.FolderEditScreen
import nart.simpleanki.feature.library.LibraryScreen
import nart.simpleanki.feature.settings.SettingsScreen
import nart.simpleanki.feature.study.StudyScreen

/** Signed-in navigation graph. */
@Composable
fun AzriNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                onOpenDeck = { nav.navigate("deck/$it") },
                onNewDeck = { nav.navigate("deckEdit") },
                onNewFolder = { nav.navigate("folderEdit") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable("deck/{deckId}") { entry ->
            val deckId = entry.arguments?.getString("deckId").orEmpty()
            val snackbarHostState = remember { SnackbarHostState() }
            // Show a "Card saved" snackbar when the card editor reports success via nav result.
            // Keyed on Unit (not the flag) so resetting the flag inside the collector doesn't
            // cancel the coroutine mid-showSnackbar; the StateFlow delivers the one-shot event.
            LaunchedEffect(Unit) {
                entry.savedStateHandle
                    .getStateFlow(RESULT_CARD_SAVED, false)
                    .filter { it }
                    .collect {
                        entry.savedStateHandle[RESULT_CARD_SAVED] = false
                        snackbarHostState.showSnackbar("Card saved")
                    }
            }
            DeckDetailScreen(
                deckId = deckId,
                snackbarHostState = snackbarHostState,
                onBack = { nav.popBackStack() },
                onStudy = { nav.navigate("study/$deckId") },
                onAddCard = { nav.navigate("cardForm/$deckId") },
                onEditCard = { cardId -> nav.navigate("cardForm/$deckId/$cardId") },
                onSettings = { nav.navigate("deckEdit/$deckId") },
            )
        }
        composable("study/{deckId}") { entry ->
            StudyScreen(entry.arguments?.getString("deckId").orEmpty(), onDone = { nav.popBackStack() })
        }
        composable("cardForm/{deckId}") { entry ->
            CardFormScreen(
                deckId = entry.arguments?.getString("deckId").orEmpty(),
                cardId = null,
                onClose = { nav.popBackStack() },
                onSaved = { signalCardSavedAndPop(nav) },
            )
        }
        composable("cardForm/{deckId}/{cardId}") { entry ->
            CardFormScreen(
                deckId = entry.arguments?.getString("deckId").orEmpty(),
                cardId = entry.arguments?.getString("cardId"),
                onClose = { nav.popBackStack() },
                onSaved = { signalCardSavedAndPop(nav) },
            )
        }
        composable("deckEdit") {
            DeckEditScreen(deckId = null, folderId = null, onDone = { nav.popBackStack() })
        }
        composable("deckEdit/{deckId}") { entry ->
            DeckEditScreen(deckId = entry.arguments?.getString("deckId"), folderId = null, onDone = { nav.popBackStack() })
        }
        composable("folderEdit") {
            FolderEditScreen(folderId = null, onDone = { nav.popBackStack() })
        }
        composable("folderEdit/{folderId}") { entry ->
            FolderEditScreen(folderId = entry.arguments?.getString("folderId"), onDone = { nav.popBackStack() })
        }
    }
}

private const val RESULT_CARD_SAVED = "card_saved"

/** Reports a successful save to the previous screen (deck detail), then dismisses the editor. */
private fun signalCardSavedAndPop(nav: NavController) {
    nav.previousBackStackEntry?.savedStateHandle?.set(RESULT_CARD_SAVED, true)
    nav.popBackStack()
}
