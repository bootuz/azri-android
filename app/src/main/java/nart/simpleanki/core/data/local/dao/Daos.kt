package nart.simpleanki.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import nart.simpleanki.core.data.local.CardEntity
import nart.simpleanki.core.data.local.DeckEntity
import nart.simpleanki.core.data.local.FolderEntity
import nart.simpleanki.core.data.local.ReviewLogEntity
import nart.simpleanki.core.data.local.StreakStateEntity

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE isDeleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE dirty = 1")
    suspend fun getDirty(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(folders: List<FolderEntity>)

    @Query("UPDATE folders SET dirty = 0 WHERE id = :id AND lastModified = :lastModified")
    suspend fun clearDirty(id: String, lastModified: Long)
}

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks WHERE isDeleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE isDeleted = 0 AND folderId IS :folderId ORDER BY name")
    fun observeByFolder(folderId: String?): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getById(id: String): DeckEntity?

    @Query("SELECT * FROM decks WHERE dirty = 1")
    suspend fun getDirty(): List<DeckEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(decks: List<DeckEntity>)

    @Query("UPDATE decks SET dirty = 0 WHERE id = :id AND lastModified = :lastModified")
    suspend fun clearDirty(id: String, lastModified: Long)
}

/** Row for per-deck card counts. */
data class DeckCardCount(val deckId: String, val count: Int)

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE isDeleted = 0 AND deckId = :deckId ORDER BY dateCreated")
    fun observeByDeck(deckId: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE isDeleted = 0 ORDER BY dateCreated")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT deckId AS deckId, COUNT(*) AS count FROM cards WHERE isDeleted = 0 GROUP BY deckId")
    fun observeCardCountsByDeck(): Flow<List<DeckCardCount>>

    @Query("SELECT * FROM cards WHERE isDeleted = 0 AND deckId = :deckId AND fsrsDue <= :now ORDER BY fsrsDue")
    suspend fun getDue(deckId: String, now: Long): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getById(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE dirty = 1")
    suspend fun getDirty(): List<CardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cards: List<CardEntity>)

    @Query("UPDATE cards SET dirty = 0 WHERE id = :id AND lastModified = :lastModified")
    suspend fun clearDirty(id: String, lastModified: Long)
}

@Dao
interface ReviewLogDao {
    // IGNORE makes both append (fresh UUID) and pull (union) idempotent: an existing id is a no-op.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(logs: List<ReviewLogEntity>)

    @Query("SELECT * FROM review_logs WHERE dirty = 1")
    suspend fun getDirty(): List<ReviewLogEntity>

    @Query("UPDATE review_logs SET dirty = 0 WHERE id = :id")
    suspend fun clearDirty(id: String)

    @Query("SELECT id FROM review_logs")
    suspend fun getAllIds(): List<String>

    @Query("SELECT * FROM review_logs ORDER BY review")
    fun observeAll(): Flow<List<ReviewLogEntity>>
}

@Dao
interface StreakStateDao {
    @Query("SELECT * FROM streak_state WHERE id = 'current'")
    fun observe(): Flow<StreakStateEntity?>

    @Query("SELECT * FROM streak_state WHERE id = 'current'")
    suspend fun get(): StreakStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StreakStateEntity)

    @Query("SELECT * FROM streak_state WHERE dirty = 1")
    suspend fun getDirty(): StreakStateEntity?

    @Query("UPDATE streak_state SET dirty = 0 WHERE id = 'current' AND lastModified = :lastModified")
    suspend fun clearDirty(lastModified: Long)
}
