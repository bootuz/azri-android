package nart.simpleanki.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FolderRepository
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.Folder

data class LibraryUiState(
    val folders: List<Folder> = emptyList(),
    val decksWithoutFolder: List<Deck> = emptyList(),
    val allDecks: List<Deck> = emptyList(),
)

/** Top-level library: folders + decks. Observes Room via repositories. */
class LibraryViewModel(
    folderRepository: FolderRepository,
    deckRepository: DeckRepository,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> =
        combine(
            folderRepository.observeFolders(),
            deckRepository.observeDecks(),
        ) { folders, decks ->
            LibraryUiState(
                folders = folders,
                decksWithoutFolder = decks.filter { it.folderId == null },
                allDecks = decks,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(),
        )
}
