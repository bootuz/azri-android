package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nart.simpleanki.core.data.local.dao.CardDao
import nart.simpleanki.core.data.local.dao.DeckDao
import nart.simpleanki.core.data.local.dao.FolderDao
import nart.simpleanki.core.data.local.toDomain
import nart.simpleanki.core.data.local.toEntity
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.Folder

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
}

class CardRepository(
    private val dao: CardDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun observeCards(deckId: String): Flow<List<Card>> =
        dao.observeByDeck(deckId).map { rows -> rows.map { it.toDomain() } }

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
}
