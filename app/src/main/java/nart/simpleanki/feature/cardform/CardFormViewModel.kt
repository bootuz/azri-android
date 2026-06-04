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
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import java.util.UUID

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
    /** Increments on each successful new-card save; drives the "Card saved" toast (re-triggerable). */
    val savedTick: Int = 0,
    /** Set once after editing an existing card, signaling the screen to close. */
    val finished: Boolean = false,
) {
    val canSave: Boolean
        get() = front.isNotBlank() && back.isNotBlank() && !uploadingImage && !uploadingAudio
}

/**
 * Add or edit a card. Supports attaching an image and an audio clip, saved on-device via
 * [MediaManager]; upload to the cloud happens later during premium sync. When
 * [CardFormUiState.createReverse] is set for a new card, a second reversed card is created
 * with swapped front/back and a shared [Card.pairId].
 */
class CardFormViewModel(
    private val deckId: String,
    private val cardRepository: CardRepository,
    private val mediaManager: MediaManager,
    private val editingCardId: String? = null,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = { System.currentTimeMillis() },
    private val logManager: LogManager = LogManager(emptyList()),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardFormUiState(isEdit = editingCardId != null))
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
    }

    fun onFrontChange(value: String) {
        _uiState.value = _uiState.value.copy(front = value)
    }

    fun onBackChange(value: String) {
        _uiState.value = _uiState.value.copy(back = value)
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
                val baseId = idGenerator()
                cardRepository.upsert(
                    newCard(
                        baseId, state.front, state.back, isReverse = false,
                        pairId = if (state.createReverse) baseId else null,
                        image = state.imageName, imagePath = state.imagePath,
                        audioName = state.audioName, audioPath = state.audioPath
                    ),
                )
                if (state.createReverse) {
                    // Reverse cards are intentionally audio-free (mirrors iOS).
                    cardRepository.upsert(
                        newCard(
                            idGenerator(),
                            state.back,
                            state.front,
                            isReverse = true,
                            pairId = baseId,
                            image = null,
                            imagePath = null,
                            audioName = null,
                            audioPath = null
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
                _uiState.value = CardFormUiState(isEdit = false, savedTick = state.savedTick + 1)
            }
        }
    }

    private fun newCard(
        id: String, front: String, back: String, isReverse: Boolean, pairId: String?,
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
