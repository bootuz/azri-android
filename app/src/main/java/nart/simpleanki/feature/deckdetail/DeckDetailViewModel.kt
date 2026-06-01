package nart.simpleanki.feature.deckdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.domain.model.Card

data class DeckDetailUiState(
    val deckId: String,
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
) : ViewModel() {

    private val queryFlow = kotlinx.coroutines.flow.MutableStateFlow("")

    val uiState: StateFlow<DeckDetailUiState> =
        kotlinx.coroutines.flow.combine(
            cardRepository.observeCards(deckId),
            queryFlow,
        ) { cards, query ->
            DeckDetailUiState(deckId = deckId, cards = cards, query = query)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeckDetailUiState(deckId = deckId),
        )

    fun onQueryChange(query: String) {
        queryFlow.value = query
    }
}
