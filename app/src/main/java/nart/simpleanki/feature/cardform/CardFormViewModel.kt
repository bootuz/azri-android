package nart.simpleanki.feature.cardform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nart.simpleanki.core.analytics.LogManager
import nart.simpleanki.core.analytics.LoggableEvent
import nart.simpleanki.core.data.media.MediaManager
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import java.util.UUID

/** A deck choice for the in-editor selector (queue-path "picker mode"). */
data class DeckOption(val id: String, val name: String)

data class CardFormUiState(
    val front: String = "",
    val back: String = "",
    val createReverse: Boolean = false,
    val isEdit: Boolean = false,
    val imageName: String? = null,
    val imagePath: String? = null,
    val uploadingImage: Boolean = false,
    val audioName: String? = null,
    val audioPath: String? = null,
    val uploadingAudio: Boolean = false,
    /** True ⇒ the user picks the destination deck in-editor (opened from the Study tab). */
    val pickDeck: Boolean = false,
    val decks: List<DeckOption> = emptyList(),
    val selectedDeckId: String? = null,
    /** Increments on each successful new-card save; drives the "Card saved" toast (re-triggerable). */
    val savedTick: Int = 0,
    /** Set once after editing an existing card, signaling the screen to close. */
    val finished: Boolean = false,
) {
    val canSave: Boolean
        get() = front.isNotBlank() && back.isNotBlank() &&
            !uploadingImage && !uploadingAudio && selectedDeckId != null
}

/**
 * Add or edit a card. Supports attaching an image and an audio clip, saved on-device via
 * [MediaManager]; upload to the cloud happens later during premium sync. When
 * [CardFormUiState.createReverse] is set for a new card, a second reversed card is created
 * with swapped front/back and a shared [Card.pairId].
 *
 * Pass [deckId] = null to enter "picker mode": the user selects the destination deck
 * in-editor (e.g. when opening the editor from the Study tab without a deck context).
 * Picker mode ([deckId] == null) requires a non-null [deckRepository]; fixed-deck mode leaves it null.
 */
class CardFormViewModel(
    private val deckId: String?,
    private val cardRepository: CardRepository,
    private val mediaManager: MediaManager,
    private val deckRepository: DeckRepository? = null,
    private val editingCardId: String? = null,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = { System.currentTimeMillis() },
    private val logManager: LogManager = LogManager(emptyList()),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CardFormUiState(
            isEdit = editingCardId != null,
            pickDeck = deckId == null,
            selectedDeckId = deckId,
        ),
    )
    val uiState: StateFlow<CardFormUiState> = _uiState.asStateFlow()

    private var editingCard: Card? = null

    init {
        if (editingCardId != null) {
            viewModelScope.launch {
                cardRepository.getById(editingCardId)?.let { card ->
                    editingCard = card
                    _uiState.value = _uiState.value.copy(
                        front = card.front, back = card.back,
                        imageName = card.image, imagePath = card.imagePath,
                        audioName = card.audioName, audioPath = card.audioPath,
                    )
                }
            }
        }
        if (deckId == null) {
            checkNotNull(deckRepository) { "deckRepository is required in picker mode (deckId == null)" }
            viewModelScope.launch {
                deckRepository.observeDecks().collect { decks ->
                    _uiState.value = _uiState.value.copy(
                        decks = decks.map { DeckOption(it.id, it.name) },
                    )
                }
            }
        }
    }

    fun onFrontChange(value: String) {
        _uiState.value = _uiState.value.copy(front = value)
    }

    fun onBackChange(value: String) {
        _uiState.value = _uiState.value.copy(back = value)
    }

    fun onSelectDeck(id: String) {
        _uiState.value = _uiState.value.copy(selectedDeckId = id)
    }

    fun onToggleReverse(value: Boolean) {
        _uiState.value = _uiState.value.copy(createReverse = value)
    }

    fun onRemoveImage() {
        _uiState.value = _uiState.value.copy(imageName = null, imagePath = null)
    }

    fun onRemoveAudio() {
        _uiState.value = _uiState.value.copy(audioName = null, audioPath = null)
    }

    fun onImagePicked(bytes: ByteArray) {
        _uiState.value = _uiState.value.copy(uploadingImage = true)
        viewModelScope.launch {
            val name = mediaManager.saveImage(bytes)
            _uiState.value =
                _uiState.value.copy(imageName = name, imagePath = null, uploadingImage = false)
        }
    }

    fun onAudioRecorded(bytes: ByteArray) {
        _uiState.value = _uiState.value.copy(uploadingAudio = true)
        viewModelScope.launch {
            val name = mediaManager.saveAudio(bytes)
            _uiState.value =
                _uiState.value.copy(audioName = name, audioPath = null, uploadingAudio = false)
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val existing = editingCard
            if (existing != null) {
                cardRepository.upsert(
                    existing.copy(
                        front = state.front, back = state.back,
                        image = state.imageName, imagePath = state.imagePath,
                        audioName = state.audioName, audioPath = state.audioPath,
                    ),
                )
                logManager.track(
                    Event.CardUpdated(
                        state.imageName != null,
                        state.audioName != null
                    )
                )
                // Editing targets one card: close the editor once saved.
                _uiState.value = _uiState.value.copy(finished = true)
            } else {
                val targetDeckId = state.selectedDeckId ?: return@launch
                val baseId = idGenerator()
                cardRepository.upsert(
                    newCard(
                        baseId, targetDeckId, state.front, state.back, isReverse = false,
                        pairId = if (state.createReverse) baseId else null,
                        image = state.imageName, imagePath = state.imagePath,
                        audioName = state.audioName, audioPath = state.audioPath,
                    ),
                )
                if (state.createReverse) {
                    // Reverse cards are intentionally audio-free (mirrors iOS).
                    cardRepository.upsert(
                        newCard(
                            idGenerator(), targetDeckId, state.back, state.front,
                            isReverse = true, pairId = baseId,
                            image = null, imagePath = null, audioName = null, audioPath = null,
                        ),
                    )
                }
                logManager.track(
                    Event.CardCreated(
                        state.imageName != null,
                        state.audioName != null,
                        state.createReverse
                    )
                )
                // Keep the editor open for rapid entry: clear inputs and bump the toast counter.
                // In picker mode, preserve the deck selection and list for the next card.
                _uiState.value = CardFormUiState(
                    isEdit = false,
                    savedTick = state.savedTick + 1,
                    pickDeck = state.pickDeck,
                    decks = state.decks,
                    selectedDeckId = state.selectedDeckId,
                )
            }
        }
    }

    private fun newCard(
        id: String, deckId: String, front: String, back: String, isReverse: Boolean, pairId: String?,
        image: String?, imagePath: String?, audioName: String?, audioPath: String?,
    ): Card {
        val t = now()
        return Card(
            id = id, front = front, back = back, deckId = deckId,
            dateCreated = t, lastModified = t, fsrsDue = t, fsrsState = CardState.New.value,
            image = image, imagePath = imagePath, audioName = audioName, audioPath = audioPath,
            pairId = pairId, isReverse = isReverse,
        )
    }

    private sealed interface Event : LoggableEvent {
        data class CardCreated(val hasImage: Boolean, val hasAudio: Boolean, val reverse: Boolean) :
            Event {
            override val eventName = "card_created"
            override val params
                get() = mapOf(
                    "has_image" to hasImage,
                    "has_audio" to hasAudio,
                    "reverse" to reverse
                )
        }

        data class CardUpdated(val hasImage: Boolean, val hasAudio: Boolean) : Event {
            override val eventName = "card_updated"
            override val params get() = mapOf("has_image" to hasImage, "has_audio" to hasAudio)
        }
    }
}
