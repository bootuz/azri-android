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
