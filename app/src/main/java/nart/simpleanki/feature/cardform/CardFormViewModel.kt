package nart.simpleanki.feature.cardform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import java.util.UUID

data class CardFormUiState(
    val front: String = "",
    val back: String = "",
    val createReverse: Boolean = false,
    val isEdit: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = front.isNotBlank() && back.isNotBlank()
}

/**
 * Add or edit a card. When [CardFormUiState.createReverse] is set for a new card, a
 * second reversed card is created with swapped front/back and a shared [Card.pairId],
 * matching the iOS reverse-pairing behavior.
 */
class CardFormViewModel(
    private val deckId: String,
    private val cardRepository: CardRepository,
    private val editingCardId: String? = null,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardFormUiState(isEdit = editingCardId != null))
    val uiState: StateFlow<CardFormUiState> = _uiState.asStateFlow()

    private var editingCard: Card? = null

    init {
        if (editingCardId != null) {
            viewModelScope.launch {
                cardRepository.getById(editingCardId)?.let { card ->
                    editingCard = card
                    _uiState.value = _uiState.value.copy(front = card.front, back = card.back)
                }
            }
        }
    }

    fun onFrontChange(value: String) { _uiState.value = _uiState.value.copy(front = value) }
    fun onBackChange(value: String) { _uiState.value = _uiState.value.copy(back = value) }
    fun onToggleReverse(value: Boolean) { _uiState.value = _uiState.value.copy(createReverse = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val existing = editingCard
            if (existing != null) {
                cardRepository.upsert(existing.copy(front = state.front, back = state.back))
            } else {
                val baseId = idGenerator()
                val original = newCard(
                    id = baseId,
                    front = state.front,
                    back = state.back,
                    isReverse = false,
                    pairId = if (state.createReverse) baseId else null,
                )
                cardRepository.upsert(original)
                if (state.createReverse) {
                    cardRepository.upsert(
                        newCard(
                            id = idGenerator(),
                            front = state.back,
                            back = state.front,
                            isReverse = true,
                            pairId = baseId,
                        ),
                    )
                }
            }
            _uiState.value = _uiState.value.copy(saved = true)
        }
    }

    private fun newCard(id: String, front: String, back: String, isReverse: Boolean, pairId: String?): Card {
        val t = now()
        return Card(
            id = id,
            front = front,
            back = back,
            deckId = deckId,
            dateCreated = t,
            lastModified = t,
            fsrsDue = t,
            fsrsState = CardState.New.value,
            pairId = pairId,
            isReverse = isReverse,
        )
    }
}
