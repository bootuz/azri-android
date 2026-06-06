package nart.simpleanki.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities — the local source of truth. Flat mirrors of the domain models
 * (epoch-millis times). The [dirty] flag marks rows with un-pushed local changes
 * for the sync engine.
 */

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String? = null,
    val lastModified: Long,
    val isDeleted: Boolean = false,
    val dirty: Boolean = false,
)

@Entity(
    tableName = "decks",
    indices = [Index("folderId")],
)
data class DeckEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val autoplay: Boolean,
    val shuffled: Boolean,
    val layout: String,
    val reviewFilter: String?,
    val folderId: String?,
    val dateCreated: Long,
    val lastModified: Long,
    val isDeleted: Boolean = false,
    val dirty: Boolean = false,
)

@Entity(
    tableName = "cards",
    indices = [Index("deckId"), Index("fsrsDue")],
)
data class CardEntity(
    @PrimaryKey val id: String,
    val front: String,
    val back: String,
    val image: String?,
    val audioName: String?,
    val imagePath: String?,
    val audioPath: String?,
    val deckId: String,
    val dateCreated: Long,
    val lastModified: Long,
    val memorized: Boolean,
    val fsrsDue: Long,
    val fsrsStability: Double,
    val fsrsDifficulty: Double,
    val fsrsElapsedDays: Double,
    val fsrsScheduledDays: Double,
    val fsrsReps: Int,
    val fsrsLapses: Int,
    val fsrsState: Int,
    val fsrsLastReview: Long?,
    val isDeleted: Boolean = false,
    val source: String?,
    val pairId: String?,
    val isReverse: Boolean = false,
    val dirty: Boolean = false,
)

@Entity(
    tableName = "review_logs",
    indices = [Index("cardId"), Index("review")],
)
data class ReviewLogEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val rating: Int,
    val state: Int?,
    val due: Long?,
    val stability: Double?,
    val difficulty: Double?,
    val elapsedDays: Double,
    val lastElapsedDays: Double,
    val scheduledDays: Double,
    val review: Long,
    val dirty: Boolean = true,
)

@Entity(
    tableName = "typing_logs",
    indices = [Index("cardId"), Index("deckId")],
)
data class TypingLogEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val deckId: String,
    val correct: Boolean,
    val typedText: String,
    val timestamp: Long,
    val dirty: Boolean = true,
)

@Entity(tableName = "streak_state")
data class StreakStateEntity(
    @PrimaryKey val id: String = "current",
    val freezeTokens: Int,
    /** Civil-day indices covered by a freeze/repair, sorted, comma-separated (empty string = none). */
    val frozenDays: String,
    val freezesAwardedForRun: Int,
    val lastReconciledDay: Long,
    val lastRepairDay: Long,
    val lastModified: Long,
    val dirty: Boolean = true,
)
