package nart.simpleanki.feature.cardform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nart.simpleanki.core.data.media.MediaUploader
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
    val saved: Boolean = false,
) {
    val canSave: Boolean
        get() = front.isNotBlank() && back.isNotBlank() && !uploadingImage && !uploadingAudio
}

/**
 * Add or edit a card. Supports attaching an image (uploaded to Firebase Storage at the
 * iOS-matching path). When [CardFormUiState.createReverse] is set for a new card, a second
 * reversed card is created with swapped front/back and a shared [Card.pairId].
 */
class CardFormViewModel(
    private val deckId: String,
    private val cardRepository: CardRepository,
    private val mediaUploader: MediaUploader,
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
                    _uiState.value = _uiState.value.copy(
                        front = card.front, back = card.back,
                        imageName = card.image, imagePath = card.imagePath,
                        audioName = card.audioName, audioPath = card.audioPath,
                    )
                }
            }
        }
    }

    fun onFrontChange(value: String) { _uiState.value = _uiState.value.copy(front = value) }
    fun onBackChange(value: String) { _uiState.value = _uiState.value.copy(back = value) }
    fun onToggleReverse(value: Boolean) { _uiState.value = _uiState.value.copy(createReverse = value) }
    fun onRemoveImage() { _uiState.value = _uiState.value.copy(imageName = null, imagePath = null) }
    fun onRemoveAudio() { _uiState.value = _uiState.value.copy(audioName = null, audioPath = null) }

    fun onImagePicked(bytes: ByteArray) {
        _uiState.value = _uiState.value.copy(uploadingImage = true)
        viewModelScope.launch {
            mediaUploader.uploadImage(bytes)
                .onSuccess { ref ->
                    _uiState.value = _uiState.value.copy(
                        imageName = ref.name, imagePath = ref.path, uploadingImage = false,
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(uploadingImage = false) }
        }
    }

    fun onAudioRecorded(bytes: ByteArray) {
        _uiState.value = _uiState.value.copy(uploadingAudio = true)
        viewModelScope.launch {
            mediaUploader.uploadAudio(bytes)
                .onSuccess { ref ->
                    _uiState.value = _uiState.value.copy(
                        audioName = ref.name, audioPath = ref.path, uploadingAudio = false,
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(uploadingAudio = false) }
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
            } else {
                val baseId = idGenerator()
                cardRepository.upsert(
                    newCard(baseId, state.front, state.back, isReverse = false,
                        pairId = if (state.createReverse) baseId else null,
                        image = state.imageName, imagePath = state.imagePath,
                        audioName = state.audioName, audioPath = state.audioPath),
                )
                if (state.createReverse) {
                    // Reverse cards are intentionally audio-free (mirrors iOS).
                    cardRepository.upsert(
                        newCard(idGenerator(), state.back, state.front, isReverse = true, pairId = baseId,
                            image = null, imagePath = null, audioName = null, audioPath = null),
                    )
                }
            }
            _uiState.value = _uiState.value.copy(saved = true)
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
}
