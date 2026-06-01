package nart.simpleanki.feature.decksettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FolderRepository
import nart.simpleanki.core.domain.model.ColorOption
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.Folder
import nart.simpleanki.core.domain.model.ReviewCardFilter
import java.util.UUID

data class DeckEditUiState(
    val name: String = "",
    val color: ColorOption = ColorOption.Default,
    val autoplay: Boolean = false,
    val shuffled: Boolean = false,
    val reviewFilter: ReviewCardFilter = ReviewCardFilter.All,
    val folderId: String? = null,
    /** Folders the deck can be moved into (for the picker); empty = "No folder" only. */
    val folders: List<Folder> = emptyList(),
    val isEdit: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank()
}

/** Create a new deck or edit an existing deck's settings (including which folder it lives in). */
class DeckEditViewModel(
    private val deckRepository: DeckRepository,
    folderRepository: FolderRepository,
    private val editingDeckId: String? = null,
    private val initialFolderId: String? = null,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DeckEditUiState(isEdit = editingDeckId != null, folderId = initialFolderId),
    )
    val uiState: StateFlow<DeckEditUiState> = _uiState.asStateFlow()

    private var editingDeck: Deck? = null

    init {
        // Keep the folder picker options in sync with the user's folders.
        viewModelScope.launch {
            folderRepository.observeFolders().collect { fs ->
                _uiState.value = _uiState.value.copy(folders = fs)
            }
        }
        if (editingDeckId != null) {
            viewModelScope.launch {
                deckRepository.getById(editingDeckId)?.let { d ->
                    editingDeck = d
                    _uiState.value = _uiState.value.copy(
                        name = d.name, color = d.color, autoplay = d.autoplay, shuffled = d.shuffled,
                        reviewFilter = d.reviewFilter, folderId = d.folderId, isEdit = true,
                    )
                }
            }
        }
    }

    fun onNameChange(v: String) { _uiState.value = _uiState.value.copy(name = v) }
    fun onColorChange(v: ColorOption) { _uiState.value = _uiState.value.copy(color = v) }
    fun onAutoplayChange(v: Boolean) { _uiState.value = _uiState.value.copy(autoplay = v) }
    fun onShuffledChange(v: Boolean) { _uiState.value = _uiState.value.copy(shuffled = v) }
    fun onReviewFilterChange(v: ReviewCardFilter) { _uiState.value = _uiState.value.copy(reviewFilter = v) }
    fun onFolderChange(folderId: String?) { _uiState.value = _uiState.value.copy(folderId = folderId) }

    fun save() {
        val s = _uiState.value
        if (!s.canSave) return
        viewModelScope.launch {
            val base = editingDeck
            val deck = if (base != null) {
                base.copy(
                    name = s.name, color = s.color, autoplay = s.autoplay,
                    shuffled = s.shuffled, reviewFilter = s.reviewFilter, folderId = s.folderId,
                )
            } else {
                val t = now()
                Deck(
                    id = idGenerator(), name = s.name, color = s.color, autoplay = s.autoplay,
                    shuffled = s.shuffled, reviewFilter = s.reviewFilter, folderId = s.folderId,
                    dateCreated = t, lastModified = t,
                )
            }
            deckRepository.upsert(deck)
            _uiState.value = _uiState.value.copy(saved = true)
        }
    }
}
