package nart.simpleanki.core.domain.model

/**
 * Domain models mirroring the iOS AzriKit entities. Times are epoch millis (UTC).
 * These are the shapes the UI and scheduling logic operate on; Room entities and
 * Firestore DTOs map to/from them.
 */

data class ReviewLog(
    val rating: Rating,
    val state: CardState?,
    val due: Long?,
    val stability: Double?,
    val difficulty: Double?,
    val elapsedDays: Double = 0.0,
    val lastElapsedDays: Double = 0.0,
    val scheduledDays: Double = 0.0,
    val review: Long,
)

data class Card(
    val id: String,
    val front: String,
    val back: String,
    val image: String? = null,
    val audioName: String? = null,
    val imagePath: String? = null,
    val audioPath: String? = null,
    val deckId: String,
    val dateCreated: Long,
    val lastModified: Long,
    val memorized: Boolean = false,
    // FSRS state
    val fsrsDue: Long,
    val fsrsStability: Double = 0.0,
    val fsrsDifficulty: Double = 0.0,
    val fsrsElapsedDays: Double = 0.0,
    val fsrsScheduledDays: Double = 0.0,
    val fsrsReps: Int = 0,
    val fsrsLapses: Int = 0,
    val fsrsState: Int = CardState.New.value,
    val fsrsLastReview: Long? = null,
    val isDeleted: Boolean = false,
    val source: String? = null,
    val pairId: String? = null,
    val isReverse: Boolean = false,
)

data class Deck(
    val id: String,
    val name: String,
    val color: ColorOption = ColorOption.Default,
    val autoplay: Boolean = false,
    val shuffled: Boolean = false,
    val layout: DeckLayout = DeckLayout.FrontToBack,
    val reviewFilter: ReviewCardFilter = ReviewCardFilter.All,
    val folderId: String? = null,
    val dateCreated: Long,
    val lastModified: Long,
    val isDeleted: Boolean = false,
)

data class Folder(
    val id: String,
    val name: String,
    val emoji: String? = null,
    val lastModified: Long,
    val isDeleted: Boolean = false,
)
