package nart.simpleanki.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import nart.simpleanki.feature.cardform.CardFormScreen
import nart.simpleanki.feature.deckdetail.DeckDetailScreen
import nart.simpleanki.feature.decksettings.DeckEditScreen
import nart.simpleanki.feature.folderdetail.FolderDetailScreen
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
                onOpenFolder = { nav.navigate("folder/$it") },
                onNewDeck = { nav.navigate("deckEdit") },
                onNewFolder = { nav.navigate("folderEdit") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("folder/{folderId}") { entry ->
            val folderId = entry.arguments?.getString("folderId").orEmpty()
            FolderDetailScreen(
                folderId = folderId,
                onBack = { nav.popBackStack() },
                onOpenDeck = { nav.navigate("deck/$it") },
                onNewDeck = { nav.navigate("deckEditInFolder/$folderId") },
                onEditFolder = { nav.navigate("folderEdit/$folderId") },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable("deck/{deckId}") { entry ->
            val deckId = entry.arguments?.getString("deckId").orEmpty()
            DeckDetailScreen(
                deckId = deckId,
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
        // The card editor stays open after a save (it shows its own "Card saved" toast and resets
        // its inputs for rapid entry); only the back arrow pops it. Editing a card closes itself.
        composable("cardForm/{deckId}") { entry ->
            CardFormScreen(
                deckId = entry.arguments?.getString("deckId").orEmpty(),
                cardId = null,
                onClose = { nav.popBackStack() },
            )
        }
        composable("cardForm/{deckId}/{cardId}") { entry ->
            CardFormScreen(
                deckId = entry.arguments?.getString("deckId").orEmpty(),
                cardId = entry.arguments?.getString("cardId"),
                onClose = { nav.popBackStack() },
            )
        }
        composable("deckEdit") {
            DeckEditScreen(deckId = null, folderId = null, onDone = { nav.popBackStack() })
        }
        composable("deckEdit/{deckId}") { entry ->
            DeckEditScreen(
                deckId = entry.arguments?.getString("deckId"),
                folderId = null,
                onDone = { nav.popBackStack() },
                // Deleting the deck leaves the now-stale deck-detail screen too: pop both.
                onDeleted = { nav.popBackStack(); nav.popBackStack() },
            )
        }
        composable("deckEditInFolder/{folderId}") { entry ->
            DeckEditScreen(deckId = null, folderId = entry.arguments?.getString("folderId"), onDone = { nav.popBackStack() })
        }
        composable("folderEdit") {
            FolderEditScreen(folderId = null, onDone = { nav.popBackStack() })
        }
        composable("folderEdit/{folderId}") { entry ->
            FolderEditScreen(
                folderId = entry.arguments?.getString("folderId"),
                onDone = { nav.popBackStack() },
                // Deleting the folder leaves its now-stale folder-detail screen too: pop both.
                onDeleted = { nav.popBackStack(); nav.popBackStack() },
            )
        }
    }
}
