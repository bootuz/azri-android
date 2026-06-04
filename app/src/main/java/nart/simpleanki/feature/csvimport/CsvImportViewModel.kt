package nart.simpleanki.feature.csvimport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nart.simpleanki.core.csv.CsvImportError
import nart.simpleanki.core.csv.CsvImportService
import nart.simpleanki.core.csv.CsvPreviewCard
import nart.simpleanki.core.csv.ParsedCsv

enum class ImportStep { Parsing, ColumnMapping, Preview, Importing }

data class CsvImportUiState(
    val step: ImportStep = ImportStep.Parsing,
    val headers: List<String> = emptyList(),
    val sampleRows: List<List<String>> = emptyList(),
    val frontCol: Int = 0,
    val backCol: Int = 1,
    val hasHeader: Boolean = true,
    val previewCards: List<CsvPreviewCard> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
) {
    val canGeneratePreview: Boolean get() = frontCol != backCol
}

class CsvImportViewModel(
    private val service: CsvImportService,
    private val deckName: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CsvImportUiState())
    val uiState: StateFlow<CsvImportUiState> = _uiState.asStateFlow()

    private var uri: Uri? = null
    private var parsed: ParsedCsv? = null

    fun parse(uri: Uri) {
        this.uri = uri
        load(_uiState.value.hasHeader, advance = true)
    }

    fun setHasHeader(value: Boolean) {
        _uiState.value = _uiState.value.copy(hasHeader = value)
        load(value, advance = false)
    }

    private fun load(hasHeader: Boolean, advance: Boolean) {
        val u = uri ?: return
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(
            busy = true, error = null,
            step = if (advance) ImportStep.Parsing else _uiState.value.step,
        )
        viewModelScope.launch {
            runCatching { service.parse(u, hasHeader) }
                .onSuccess { data ->
                    parsed = data
                    _uiState.value = _uiState.value.copy(
                        step = ImportStep.ColumnMapping, busy = false,
                        headers = data.headers, sampleRows = data.rows.take(3),
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(busy = false, error = messageFor(it)) }
        }
    }

    fun setFrontCol(index: Int) { _uiState.value = _uiState.value.copy(frontCol = index) }
    fun setBackCol(index: Int) { _uiState.value = _uiState.value.copy(backCol = index) }

    fun generatePreview() {
        val data = parsed ?: return
        val s = _uiState.value
        if (s.frontCol == s.backCol) return
        val cards = service.previewCards(data, s.frontCol, s.backCol)
        _uiState.value = s.copy(step = ImportStep.Preview, previewCards = cards)
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
        (t as? CsvImportError)?.message ?: "Import failed. Please try again."
}
