package nart.simpleanki.feature.folderdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FolderRepository
import nart.simpleanki.core.domain.model.Deck

data class FolderDetailUiState(
    val folderId: String,
    val folderName: String = "",
    val decks: List<Deck> = emptyList(),
    val cardCounts: Map<String, Int> = emptyMap(),
)

/** Decks that live inside a single folder, with per-deck card counts. Observes Room via repositories. */
class FolderDetailViewModel(
    private val folderId: String,
    deckRepository: DeckRepository,
    cardRepository: CardRepository,
    folderRepository: FolderRepository,
) : ViewModel() {

    private val folderNameFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            folderNameFlow.value = folderRepository.getById(folderId)?.name ?: ""
        }
    }

    val uiState: StateFlow<FolderDetailUiState> =
        combine(
            deckRepository.observeDecksInFolder(folderId),
            cardRepository.observeCardCounts(),
            folderNameFlow,
        ) { decks, counts, name ->
            FolderDetailUiState(
                folderId = folderId,
                folderName = name,
                decks = decks,
                cardCounts = counts,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FolderDetailUiState(folderId = folderId),
        )
}
