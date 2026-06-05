package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import nart.simpleanki.core.data.local.dao.CardDao
import nart.simpleanki.core.data.local.dao.DeckDao
import nart.simpleanki.core.data.local.dao.FolderDao
import nart.simpleanki.core.data.local.dao.ReviewLogDao
import nart.simpleanki.core.data.local.dao.StreakStateDao
import nart.simpleanki.core.data.local.dao.TypingLogDao
import nart.simpleanki.core.data.local.toDomain
import nart.simpleanki.core.data.local.toEntity
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.Folder
import nart.simpleanki.core.domain.model.ReviewLog
import nart.simpleanki.core.domain.model.TypingLog
import nart.simpleanki.core.domain.streak.StreakState
import java.util.UUID

/**
 * Repositories: Room is the source of truth. Local writes bump [Folder.lastModified]
 * (via [now]) and mark the row dirty so the sync engine pushes it. Deletes are soft
 * (isDeleted = true) to propagate removals across devices. [now] is injected for testable time.
 */
class FolderRepository(
    private val dao: FolderDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun observeFolders(): Flow<List<Folder>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getById(id: String): Folder? = dao.getById(id)?.toDomain()

    suspend fun upsert(folder: Folder) {
        dao.upsertAll(listOf(folder.copy(lastModified = now()).toEntity(dirty = true)))
    }

    suspend fun delete(id: String) {
        val existing = dao.getById(id) ?: return
        dao.upsertAll(listOf(existing.copy(isDeleted = true, lastModified = now(), dirty = true)))
    }
}

class DeckRepository(
    private val dao: DeckDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun observeDecks(): Flow<List<Deck>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeDecksInFolder(folderId: String?): Flow<List<Deck>> =
        dao.observeByFolder(folderId).map { rows -> rows.map { it.toDomain() } }

    suspend fun getById(id: String): Deck? = dao.getById(id)?.toDomain()

    suspend fun upsert(deck: Deck) {
        dao.upsertAll(listOf(deck.copy(lastModified = now()).toEntity(dirty = true)))
    }

    suspend fun delete(id: String) {
        val existing = dao.getById(id) ?: return
        dao.upsertAll(listOf(existing.copy(isDeleted = true, lastModified = now(), dirty = true)))
    }

    /** Moves every deck out of [folderId] (folderId -> null) — used when the folder is deleted,
     *  so its decks survive at the top level instead of being orphaned. */
    suspend fun unfolderAll(folderId: String) {
        val t = now()
        val moved = dao.observeByFolder(folderId).first()
            .map { it.copy(folderId = null, lastModified = t, dirty = true) }
        if (moved.isNotEmpty()) dao.upsertAll(moved)
    }
}

class CardRepository(
    private val dao: CardDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun observeCards(deckId: String): Flow<List<Card>> =
        dao.observeByDeck(deckId).map { rows -> rows.map { it.toDomain() } }

    /** Every (non-deleted) card across all decks — for the global study queue. */
    fun observeAllCards(): Flow<List<Card>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Map of deckId → number of (non-deleted) cards, for library badges. */
    fun observeCardCounts(): Flow<Map<String, Int>> =
        dao.observeCardCountsByDeck().map { rows -> rows.associate { it.deckId to it.count } }

    suspend fun getDue(deckId: String, at: Long = now()): List<Card> =
        dao.getDue(deckId, at).map { it.toDomain() }

    suspend fun getById(id: String): Card? = dao.getById(id)?.toDomain()

    suspend fun upsert(card: Card) {
        dao.upsertAll(listOf(card.copy(lastModified = now()).toEntity(dirty = true)))
    }

    /** Persists a post-review FSRS update without re-stamping lastModified to [now] twice. */
    suspend fun save(card: Card) {
        dao.upsertAll(listOf(card.toEntity(dirty = true)))
    }

    suspend fun delete(id: String) {
        val existing = dao.getById(id) ?: return
        dao.upsertAll(listOf(existing.copy(isDeleted = true, lastModified = now(), dirty = true)))
    }

    /** Cascade: soft-deletes every (non-deleted) card in a deck — used when the deck is deleted. */
    suspend fun deleteByDeck(deckId: String) {
        val t = now()
        val deleted = dao.observeByDeck(deckId).first()
            .map { it.copy(isDeleted = true, lastModified = t, dirty = true) }
        if (deleted.isNotEmpty()) dao.upsertAll(deleted)
    }
}

/** Immutable, append-only store of FSRS review events (the streak/stats data source). */
class ReviewLogRepository(
    private val dao: ReviewLogDao,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** Appends one review event: assigns a fresh id + the card id, marked dirty for the next sync. */
    suspend fun append(cardId: String, log: ReviewLog) {
        dao.insertAll(listOf(log.copy(id = newId(), cardId = cardId).toEntity(dirty = true)))
    }

    fun observeLogs(): Flow<List<ReviewLog>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
}

/** Immutable, append-only store of Type-Practice first-attempt outcomes (decoupled from FSRS). */
class TypingLogRepository(
    private val dao: TypingLogDao,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** Appends one typing outcome, assigning a fresh id when none is set; marked dirty for sync. */
    suspend fun append(log: TypingLog) {
        dao.insertAll(listOf(log.copy(id = log.id.ifEmpty { newId() }).toEntity(dirty = true)))
    }

    fun observeLogs(): Flow<List<TypingLog>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeLogsForDeck(deckId: String): Flow<List<TypingLog>> =
        dao.observeForDeck(deckId).map { rows -> rows.map { it.toDomain() } }
}

class StreakStateRepository(
    private val dao: StreakStateDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun observe(): Flow<StreakState> =
        dao.observe().map { it?.toDomain() ?: StreakState() }

    suspend fun get(): StreakState = dao.get()?.toDomain() ?: StreakState()

    suspend fun update(state: StreakState) {
        dao.upsert(state.toEntity(lastModified = now(), dirty = true))
    }
}
