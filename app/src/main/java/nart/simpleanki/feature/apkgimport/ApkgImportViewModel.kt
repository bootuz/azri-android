package nart.simpleanki.feature.apkgimport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nart.simpleanki.core.apkg.AnkiNote
import nart.simpleanki.core.apkg.AnkiNoteType
import nart.simpleanki.core.apkg.ApkgImportError
import nart.simpleanki.core.apkg.ApkgImportService
import nart.simpleanki.core.apkg.ApkgPreviewCard
import nart.simpleanki.core.apkg.ParsedCollection

enum class ImportStep { Parsing, NoteTypeSelection, FieldMapping, Preview, Importing }

data class ApkgImportUiState(
    val step: ImportStep = ImportStep.Parsing,
    val noteTypes: List<AnkiNoteType> = emptyList(),
    val noteCounts: Map<Long, Int> = emptyMap(),
    val selectedNoteType: AnkiNoteType? = null,
    val availableFields: List<String> = emptyList(),
    val frontField: String? = null,
    val backField: String? = null,
    val importMedia: Boolean = false,
    val previewCards: List<ApkgPreviewCard> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

class ApkgImportViewModel(
    private val service: ApkgImportService,
    private val deckName: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ApkgImportUiState())
    val uiState: StateFlow<ApkgImportUiState> = _uiState.asStateFlow()

    private var collection: ParsedCollection? = null
    private var filtered: List<AnkiNote> = emptyList()

    fun parse(uri: Uri) {
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(step = ImportStep.Parsing, busy = true, error = null)
        viewModelScope.launch {
            runCatching { service.parse(uri) }
                .onSuccess { data ->
                    collection = data
                    _uiState.value = _uiState.value.copy(
                        step = ImportStep.NoteTypeSelection, busy = false,
                        noteTypes = data.noteTypes,
                        noteCounts = data.noteTypes.associate { nt -> nt.id to data.notes.count { it.modelId == nt.id } },
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(busy = false, error = messageFor(it)) }
        }
    }

    fun selectNoteType(noteType: AnkiNoteType) {
        val data = collection ?: return
        filtered = service.filterNotes(data, noteType.id)
        _uiState.value = _uiState.value.copy(
            step = ImportStep.FieldMapping, selectedNoteType = noteType,
            availableFields = noteType.fields,
            frontField = noteType.fields.getOrNull(0),
            backField = noteType.fields.getOrNull(1),
        )
    }

    fun setFrontField(name: String) { _uiState.value = _uiState.value.copy(frontField = name) }
    fun setBackField(name: String) { _uiState.value = _uiState.value.copy(backField = name) }
    fun setImportMedia(value: Boolean) { _uiState.value = _uiState.value.copy(importMedia = value) }

    fun canGeneratePreview(): Boolean {
        val s = _uiState.value
        return s.frontField != null && s.backField != null && s.frontField != s.backField
    }

    fun generatePreview() {
        val data = collection ?: return
        val s = _uiState.value
        val nt = s.selectedNoteType ?: return
        val fi = nt.fields.indexOf(s.frontField); val bi = nt.fields.indexOf(s.backField)
        if (fi < 0 || bi < 0 || fi == bi) return
        if (_uiState.value.busy) return
        _uiState.value = s.copy(busy = true)
        viewModelScope.launch {
            runCatching { service.previewCards(filtered, nt, fi, bi, data.media, s.importMedia) }
                .onSuccess { _uiState.value = _uiState.value.copy(step = ImportStep.Preview, previewCards = it, busy = false) }
                .onFailure { _uiState.value = _uiState.value.copy(busy = false, error = messageFor(it)) }
        }
    }

    fun toggleCard(index: Int) {
        val list = _uiState.value.previewCards.toMutableList()
        list.getOrNull(index)?.let { list[index] = it.copy(selected = !it.selected) }
        _uiState.value = _uiState.value.copy(previewCards = list)
    }

    fun import(onComplete: () -> Unit) {
        val selected = _uiState.value.previewCards.filter { it.selected }
        if (selected.isEmpty()) return
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(step = ImportStep.Importing, busy = true)
        viewModelScope.launch {
            runCatching { service.import(selected, deckName) }
                .onSuccess { onComplete() }
                .onFailure { _uiState.value = _uiState.value.copy(step = ImportStep.Preview, busy = false, error = messageFor(it)) }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    private fun messageFor(t: Throwable): String =
        (t as? ApkgImportError)?.message ?: "Import failed. Please try again."
}
