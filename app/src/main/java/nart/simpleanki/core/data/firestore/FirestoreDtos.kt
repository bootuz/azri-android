package nart.simpleanki.core.data.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.ColorOption
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.DeckLayout
import nart.simpleanki.core.domain.model.Folder
import nart.simpleanki.core.domain.model.Rating
import nart.simpleanki.core.domain.model.ReviewCardFilter
import nart.simpleanki.core.domain.model.ReviewLog
import nart.simpleanki.core.domain.model.TypingLog
import java.util.Date

/**
 * Firestore-storable DTOs. Field names (snake_case) and document layout match the
 * iOS `*Firestore` models exactly so both platforms share `simple-anki-166ea`.
 * All properties have defaults so Firestore's deserializer can no-arg construct them.
 * Times are Firestore [Timestamp]; mappers convert to/from epoch millis.
 */

private fun Timestamp.toMillis(): Long = toDate().time
private fun Long.toTimestamp(): Timestamp = Timestamp(Date(this))

// MARK: - Card

data class CardDto(
    @DocumentId var id: String? = null,
    var front: String = "",
    var back: String = "",
    var image: String? = null,
    var memorized: Boolean = false,
    var source: String? = null,
    @get:PropertyName("pair_id") @set:PropertyName("pair_id") var pairId: String? = null,
    @get:PropertyName("is_reverse") @set:PropertyName("is_reverse") var isReverse: Boolean? = null,
    @get:PropertyName("audio_name") @set:PropertyName("audio_name") var audioName: String? = null,
    @get:PropertyName("image_path") @set:PropertyName("image_path") var imagePath: String? = null,
    @get:PropertyName("audio_path") @set:PropertyName("audio_path") var audioPath: String? = null,
    @get:PropertyName("deck_id") @set:PropertyName("deck_id") var deckId: String = "",
    @get:PropertyName("date_created") @set:PropertyName("date_created") var dateCreated: Timestamp = Timestamp(Date(0)),
    @get:PropertyName("last_modified") @set:PropertyName("last_modified") var lastModified: Timestamp = Timestamp(Date(0)),
    @get:PropertyName("fsrs_due") @set:PropertyName("fsrs_due") var fsrsDue: Timestamp = Timestamp(Date(0)),
    @get:PropertyName("fsrs_stability") @set:PropertyName("fsrs_stability") var fsrsStability: Double = 0.0,
    @get:PropertyName("fsrs_difficulty") @set:PropertyName("fsrs_difficulty") var fsrsDifficulty: Double = 0.0,
    @get:PropertyName("fsrs_elapsed_days") @set:PropertyName("fsrs_elapsed_days") var fsrsElapsedDays: Double = 0.0,
    @get:PropertyName("fsrs_scheduled_days") @set:PropertyName("fsrs_scheduled_days") var fsrsScheduledDays: Double = 0.0,
    @get:PropertyName("fsrs_reps") @set:PropertyName("fsrs_reps") var fsrsReps: Int = 0,
    @get:PropertyName("fsrs_lapses") @set:PropertyName("fsrs_lapses") var fsrsLapses: Int = 0,
    @get:PropertyName("fsrs_state") @set:PropertyName("fsrs_state") var fsrsState: Int = 0,
    @get:PropertyName("fsrs_last_review") @set:PropertyName("fsrs_last_review") var fsrsLastReview: Timestamp? = null,
    @get:PropertyName("is_deleted") @set:PropertyName("is_deleted") var isDeleted: Boolean = false,
) {
    fun toDomain(): Card = Card(
        id = id.orEmpty(),
        front = front,
        back = back,
        image = image,
        audioName = audioName,
        imagePath = imagePath,
        audioPath = audioPath,
        deckId = deckId,
        dateCreated = dateCreated.toMillis(),
        lastModified = lastModified.toMillis(),
        memorized = memorized,
        fsrsDue = fsrsDue.toMillis(),
        fsrsStability = fsrsStability,
        fsrsDifficulty = fsrsDifficulty,
        fsrsElapsedDays = fsrsElapsedDays,
        fsrsScheduledDays = fsrsScheduledDays,
        fsrsReps = fsrsReps,
        fsrsLapses = fsrsLapses,
        fsrsState = fsrsState,
        fsrsLastReview = fsrsLastReview?.toMillis(),
        isDeleted = isDeleted,
        source = source,
        pairId = pairId,
        isReverse = isReverse ?: false,
    )

    companion object {
        fun fromDomain(c: Card): CardDto = CardDto(
            id = c.id.ifEmpty { null },
            front = c.front,
            back = c.back,
            image = c.image,
            memorized = c.memorized,
            source = c.source,
            pairId = c.pairId,
            isReverse = c.isReverse,
            audioName = c.audioName,
            imagePath = c.imagePath,
            audioPath = c.audioPath,
            deckId = c.deckId,
            dateCreated = c.dateCreated.toTimestamp(),
            lastModified = c.lastModified.toTimestamp(),
            fsrsDue = c.fsrsDue.toTimestamp(),
            fsrsStability = c.fsrsStability,
            fsrsDifficulty = c.fsrsDifficulty,
            fsrsElapsedDays = c.fsrsElapsedDays,
            fsrsScheduledDays = c.fsrsScheduledDays,
            fsrsReps = c.fsrsReps,
            fsrsLapses = c.fsrsLapses,
            fsrsState = c.fsrsState,
            fsrsLastReview = c.fsrsLastReview?.toTimestamp(),
            isDeleted = c.isDeleted,
        )
    }
}

// MARK: - Deck

data class DeckDto(
    @DocumentId var id: String? = null,
    var name: String = "",
    var color: String = ColorOption.Default.wire,
    var autoplay: Boolean = false,
    var shuffled: Boolean = false,
    var layout: String = DeckLayout.FrontToBack.wire,
    @get:PropertyName("review_filter") @set:PropertyName("review_filter") var reviewFilter: String? = null,
    @get:PropertyName("folder_id") @set:PropertyName("folder_id") var folderId: String? = null,
    @get:PropertyName("date_created") @set:PropertyName("date_created") var dateCreated: Timestamp = Timestamp(Date(0)),
    @get:PropertyName("last_modified") @set:PropertyName("last_modified") var lastModified: Timestamp = Timestamp(Date(0)),
    @get:PropertyName("is_deleted") @set:PropertyName("is_deleted") var isDeleted: Boolean = false,
) {
    fun toDomain(): Deck = Deck(
        id = id.orEmpty(),
        name = name,
        color = ColorOption.fromWire(color),
        autoplay = autoplay,
        shuffled = shuffled,
        layout = DeckLayout.fromWire(layout),
        reviewFilter = ReviewCardFilter.fromWire(reviewFilter),
        folderId = folderId,
        dateCreated = dateCreated.toMillis(),
        lastModified = lastModified.toMillis(),
        isDeleted = isDeleted,
    )

    companion object {
        fun fromDomain(d: Deck): DeckDto = DeckDto(
            id = d.id.ifEmpty { null },
            name = d.name,
            color = d.color.wire,
            autoplay = d.autoplay,
            shuffled = d.shuffled,
            layout = d.layout.wire,
            reviewFilter = d.reviewFilter.wire,
            folderId = d.folderId,
            dateCreated = d.dateCreated.toTimestamp(),
            lastModified = d.lastModified.toTimestamp(),
            isDeleted = d.isDeleted,
        )
    }
}

// MARK: - Folder

data class FolderDto(
    @DocumentId var id: String? = null,
    var name: String = "",
    var emoji: String? = null,
    @get:PropertyName("last_modified") @set:PropertyName("last_modified") var lastModified: Timestamp = Timestamp(Date(0)),
    @get:PropertyName("is_deleted") @set:PropertyName("is_deleted") var isDeleted: Boolean = false,
) {
    fun toDomain(): Folder = Folder(
        id = id.orEmpty(),
        name = name,
        emoji = emoji,
        lastModified = lastModified.toMillis(),
        isDeleted = isDeleted,
    )

    companion object {
        fun fromDomain(f: Folder): FolderDto = FolderDto(
            id = f.id.ifEmpty { null },
            name = f.name,
            emoji = f.emoji,
            lastModified = f.lastModified.toTimestamp(),
            isDeleted = f.isDeleted,
        )
    }
}

// MARK: - Review log (stored flat under users/{uid}/reviewLogs)

data class ReviewLogDto(
    var id: String = "",
    @get:PropertyName("card_id") @set:PropertyName("card_id") var cardId: String = "",
    var rating: Int = Rating.Again.value,
    var state: Int? = null,
    var due: Timestamp? = null,
    var stability: Double? = null,
    var difficulty: Double? = null,
    var review: Timestamp = Timestamp(Date(0)),
    @get:PropertyName("elapsed_days") @set:PropertyName("elapsed_days") var elapsedDays: Double? = null,
    @get:PropertyName("last_elapsed_days") @set:PropertyName("last_elapsed_days") var lastElapsedDays: Double? = null,
    @get:PropertyName("scheduled_days") @set:PropertyName("scheduled_days") var scheduledDays: Double? = null,
) {
    fun toDomain(): ReviewLog = ReviewLog(
        rating = Rating.fromValue(rating),
        state = nart.simpleanki.core.domain.model.CardState.fromValue(state),
        due = due?.toMillis(),
        stability = stability,
        difficulty = difficulty,
        elapsedDays = elapsedDays ?: 0.0,
        lastElapsedDays = lastElapsedDays ?: 0.0,
        scheduledDays = scheduledDays ?: 0.0,
        review = review.toMillis(),
        id = id,
        cardId = cardId,
    )

    companion object {
        fun fromDomain(r: ReviewLog): ReviewLogDto = ReviewLogDto(
            id = r.id,
            cardId = r.cardId,
            rating = r.rating.value,
            state = r.state?.value,
            due = r.due?.toTimestamp(),
            stability = r.stability,
            difficulty = r.difficulty,
            review = r.review.toTimestamp(),
            elapsedDays = r.elapsedDays,
            lastElapsedDays = r.lastElapsedDays,
            scheduledDays = r.scheduledDays,
        )
    }
}

// MARK: - Streak state (single doc per user: users/{uid}/streakState/current)

data class StreakStateDto(
    @DocumentId var id: String? = "current",
    @get:PropertyName("freeze_tokens") @set:PropertyName("freeze_tokens") var freezeTokens: Int = 0,
    @get:PropertyName("frozen_days") @set:PropertyName("frozen_days") var frozenDays: List<Long> = emptyList(),
    @get:PropertyName("freezes_awarded_for_run") @set:PropertyName("freezes_awarded_for_run") var freezesAwardedForRun: Int = 0,
    @get:PropertyName("last_reconciled_day") @set:PropertyName("last_reconciled_day") var lastReconciledDay: Long = 0,
    @get:PropertyName("last_repair_day") @set:PropertyName("last_repair_day") var lastRepairDay: Long = 0,
    @get:PropertyName("last_modified") @set:PropertyName("last_modified") var lastModified: Timestamp = Timestamp(Date(0)),
) {
    fun lastModifiedMillis(): Long = lastModified.toMillis()

    fun toEntity(dirty: Boolean) = nart.simpleanki.core.data.local.StreakStateEntity(
        id = "current",
        freezeTokens = freezeTokens,
        frozenDays = frozenDays.sorted().joinToString(","),
        freezesAwardedForRun = freezesAwardedForRun,
        lastReconciledDay = lastReconciledDay,
        lastRepairDay = lastRepairDay,
        lastModified = lastModified.toMillis(),
        dirty = dirty,
    )

    companion object {
        fun fromEntity(e: nart.simpleanki.core.data.local.StreakStateEntity) = StreakStateDto(
            id = "current",
            freezeTokens = e.freezeTokens,
            frozenDays = e.frozenDays.split(",").filter { it.isNotBlank() }.map { it.toLong() },
            freezesAwardedForRun = e.freezesAwardedForRun,
            lastReconciledDay = e.lastReconciledDay,
            lastRepairDay = e.lastRepairDay,
            lastModified = e.lastModified.toTimestamp(),
        )
    }
}

// MARK: - Typing log (stored flat under users/{uid}/typingLogs)

data class TypingLogDto(
    var id: String = "",
    @get:PropertyName("card_id") @set:PropertyName("card_id") var cardId: String = "",
    @get:PropertyName("deck_id") @set:PropertyName("deck_id") var deckId: String = "",
    var correct: Boolean = false,
    @get:PropertyName("typed_text") @set:PropertyName("typed_text") var typedText: String = "",
    var timestamp: Timestamp = Timestamp(Date(0)),
) {
    fun toDomain(): TypingLog = TypingLog(
        id = id,
        cardId = cardId,
        deckId = deckId,
        correct = correct,
        typedText = typedText,
        timestamp = timestamp.toMillis(),
    )

    companion object {
        fun fromDomain(t: TypingLog): TypingLogDto = TypingLogDto(
            id = t.id,
            cardId = t.cardId,
            deckId = t.deckId,
            correct = t.correct,
            typedText = t.typedText,
            timestamp = t.timestamp.toTimestamp(),
        )
    }
}
