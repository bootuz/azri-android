package nart.simpleanki.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FolderRepository
import nart.simpleanki.core.domain.model.Folder
import java.util.UUID

data class FolderEditUiState(
    val name: String = "",
    val emoji: String? = null,
    val isEdit: Boolean = false,
    val saved: Boolean = false,
    /** Set once the folder is deleted (its decks moved out, not removed), signalling the screen. */
    val deleted: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank()
}

/** Create a new folder or rename/re-emoji an existing one. */
class FolderEditViewModel(
    private val folderRepository: FolderRepository,
    private val deckRepository: DeckRepository,
    private val editingFolderId: String? = null,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(FolderEditUiState(isEdit = editingFolderId != null))
    val uiState: StateFlow<FolderEditUiState> = _uiState.asStateFlow()

    private var editing: Folder? = null

    init {
        if (editingFolderId != null) {
            viewModelScope.launch {
                folderRepository.getById(editingFolderId)?.let { f ->
                    editing = f
                    _uiState.value = FolderEditUiState(name = f.name, emoji = f.emoji, isEdit = true)
                }
            }
        }
    }

    fun onNameChange(v: String) { _uiState.value = _uiState.value.copy(name = v) }
    fun onEmojiChange(v: String?) { _uiState.value = _uiState.value.copy(emoji = v) }

    fun save() {
        val s = _uiState.value
        if (!s.canSave) return
        viewModelScope.launch {
            val base = editing
            val folder = if (base != null) {
                base.copy(name = s.name, emoji = s.emoji)
            } else {
                Folder(id = idGenerator(), name = s.name, emoji = s.emoji, lastModified = now())
            }
            folderRepository.upsert(folder)
            _uiState.value = _uiState.value.copy(saved = true)
        }
    }

    /** Deletes the folder being edited but KEEPS its decks: they're moved to no folder first. */
    fun delete() {
        val id = editingFolderId ?: return
        viewModelScope.launch {
            deckRepository.unfolderAll(id)
            folderRepository.delete(id)
            _uiState.value = _uiState.value.copy(deleted = true)
        }
    }
}
