package nart.simpleanki.feature.deckdetail

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
import nart.simpleanki.core.domain.model.Card

data class DeckDetailUiState(
    val deckId: String,
    val deckName: String = "",
    val cards: List<Card> = emptyList(),
    val query: String = "",
) {
    val visibleCards: List<Card>
        get() = if (query.isBlank()) cards else cards.filter {
            it.front.contains(query, ignoreCase = true) || it.back.contains(query, ignoreCase = true)
        }
}

/** Cards within a deck, with client-side search. */
class DeckDetailViewModel(
    private val deckId: String,
    cardRepository: CardRepository,
    deckRepository: DeckRepository? = null,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val deckNameFlow = MutableStateFlow("")

    init {
        if (deckRepository != null) {
            viewModelScope.launch {
                deckNameFlow.value = deckRepository.getById(deckId)?.name ?: ""
            }
        }
    }

    val uiState: StateFlow<DeckDetailUiState> =
        combine(
            cardRepository.observeCards(deckId),
            queryFlow,
            deckNameFlow,
        ) { cards, query, name ->
            DeckDetailUiState(deckId = deckId, deckName = name, cards = cards, query = query)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeckDetailUiState(deckId = deckId),
        )

    fun onQueryChange(query: String) {
        queryFlow.value = query
    }
}
