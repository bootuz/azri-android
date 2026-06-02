package nart.simpleanki.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import nart.simpleanki.feature.cardform.CardFormScreen
import nart.simpleanki.feature.deckdetail.DeckDetailScreen
import nart.simpleanki.feature.decksettings.DeckEditScreen
import nart.simpleanki.feature.folderdetail.FolderDetailScreen
import nart.simpleanki.feature.library.FolderEditScreen
import nart.simpleanki.feature.library.LibraryScreen
import nart.simpleanki.feature.profile.ProfileScreen
import nart.simpleanki.feature.queue.StudyQueueScreen
import nart.simpleanki.feature.settings.SettingsScreen
import nart.simpleanki.feature.study.StudyScreen

private const val QUEUE = "queue"
private const val LIBRARY = "library"
private const val PROFILE = "profile"

/** Signed-in navigation graph with a bottom tab bar: Queue (default), Library, Profile. */
@Composable
fun AzriNavHost() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // The bottom bar only shows on the top-level tabs, not on pushed detail screens.
    val showBottomBar = currentRoute == QUEUE || currentRoute == LIBRARY || currentRoute == PROFILE

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                // M3's default NavigationBar is 80dp; trim the content to 64dp but keep the
                // gesture-nav inset below it so nothing sits under the system pill.
                val gestureInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                NavigationBar(
                    modifier = Modifier.height(64.dp + gestureInset),
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    NavigationBarItem(
                        selected = currentRoute == QUEUE,
                        onClick = { nav.switchTab(QUEUE) },
                        icon = { Icon(Icons.Filled.School, contentDescription = null) },
                        label = { Text("Queue") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == LIBRARY,
                        onClick = { nav.switchTab(LIBRARY) },
                        icon = { Icon(Icons.Filled.Style, contentDescription = null) },
                        label = { Text("Library") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == PROFILE,
                        onClick = { nav.switchTab(PROFILE) },
                        icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        label = { Text("Profile") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = QUEUE,
            modifier = Modifier.padding(padding),
        ) {
            composable(QUEUE) {
                StudyQueueScreen(
                    onStudyAll = { nav.navigate("studyAll") },
                    onOpenDeck = { nav.navigate("deck/$it") },
                )
            }
            composable(LIBRARY) {
                LibraryScreen(
                    onOpenDeck = { nav.navigate("deck/$it") },
                    onOpenFolder = { nav.navigate("folder/$it") },
                    onNewDeck = { nav.navigate("deckEdit") },
                    onNewFolder = { nav.navigate("folderEdit") },
                )
            }
            composable(PROFILE) {
                ProfileScreen(onOpenFsrsSettings = { nav.navigate("fsrsSettings") })
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
            composable("fsrsSettings") {
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
            composable("studyAll") {
                StudyScreen(deckId = null, onDone = { nav.popBackStack() })
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
}

/** Switches between bottom-bar tabs, preserving each tab's state and avoiding a back-stack pile-up. */
private fun NavController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
