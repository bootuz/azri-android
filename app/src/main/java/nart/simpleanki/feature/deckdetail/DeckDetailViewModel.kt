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
import nart.simpleanki.core.domain.model.CardState

data class DeckDetailUiState(
    val deckId: String,
    val deckName: String = "",
    val cards: List<Card> = emptyList(),
    val query: String = "",
    val dueCount: Int = 0,
    val newCount: Int = 0,
) {
    val total: Int get() = cards.size

    val visibleCards: List<Card>
        get() = if (query.isBlank()) cards else cards.filter {
            it.front.contains(query, ignoreCase = true) || it.back.contains(query, ignoreCase = true)
        }
}

/** Cards within a deck, with client-side search and study stats. */
class DeckDetailViewModel(
    private val deckId: String,
    cardRepository: CardRepository,
    deckRepository: DeckRepository? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
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
            val nowMillis = now()
            DeckDetailUiState(
                deckId = deckId,
                deckName = name,
                cards = cards,
                query = query,
                newCount = cards.count { it.fsrsState == CardState.New.value },
                dueCount = cards.count { it.fsrsState != CardState.New.value && it.fsrsDue <= nowMillis },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeckDetailUiState(deckId = deckId),
        )

    fun onQueryChange(query: String) {
        queryFlow.value = query
    }
}
