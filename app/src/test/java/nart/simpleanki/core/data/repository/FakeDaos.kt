package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import nart.simpleanki.core.data.local.CardEntity
import nart.simpleanki.core.data.local.DeckEntity
import nart.simpleanki.core.data.local.FolderEntity
import nart.simpleanki.core.data.local.ReviewLogEntity
import nart.simpleanki.core.data.local.dao.CardDao
import nart.simpleanki.core.data.local.dao.DeckDao
import nart.simpleanki.core.data.local.dao.FolderDao
import nart.simpleanki.core.data.local.dao.ReviewLogDao

/** In-memory fakes implementing the Room DAO interfaces for pure-JVM repository tests. */

class FakeFolderDao : FolderDao {
    private val store = MutableStateFlow<Map<String, FolderEntity>>(emptyMap())
    override fun observeAll(): Flow<List<FolderEntity>> =
        store.map { it.values.filter { e -> !e.isDeleted }.sortedBy { e -> e.name } }
    override suspend fun getById(id: String): FolderEntity? = store.value[id]
    override suspend fun getDirty(): List<FolderEntity> = store.value.values.filter { it.dirty }
    override suspend fun upsertAll(folders: List<FolderEntity>) {
        store.value = store.value.toMutableMap().apply { folders.forEach { put(it.id, it) } }
    }
    override suspend fun clearDirty(id: String, lastModified: Long) {
        store.value[id]?.let { if (it.lastModified == lastModified) upsertAll(listOf(it.copy(dirty = false))) }
    }
}

class FakeDeckDao : DeckDao {
    private val store = MutableStateFlow<Map<String, DeckEntity>>(emptyMap())
    override fun observeAll(): Flow<List<DeckEntity>> =
        store.map { it.values.filter { e -> !e.isDeleted }.sortedBy { e -> e.name } }
    override fun observeByFolder(folderId: String?): Flow<List<DeckEntity>> =
        store.map { it.values.filter { e -> !e.isDeleted && e.folderId == folderId }.sortedBy { e -> e.name } }
    override suspend fun getById(id: String): DeckEntity? = store.value[id]
    override suspend fun getDirty(): List<DeckEntity> = store.value.values.filter { it.dirty }
    override suspend fun upsertAll(decks: List<DeckEntity>) {
        store.value = store.value.toMutableMap().apply { decks.forEach { put(it.id, it) } }
    }
    override suspend fun clearDirty(id: String, lastModified: Long) {
        store.value[id]?.let { if (it.lastModified == lastModified) upsertAll(listOf(it.copy(dirty = false))) }
    }
}

class FakeCardDao : CardDao {
    private val store = MutableStateFlow<Map<String, CardEntity>>(emptyMap())
    override fun observeByDeck(deckId: String): Flow<List<CardEntity>> =
        store.map { it.values.filter { e -> !e.isDeleted && e.deckId == deckId }.sortedBy { e -> e.dateCreated } }
    override fun observeAll(): Flow<List<CardEntity>> =
        store.map { it.values.filter { e -> !e.isDeleted }.sortedBy { e -> e.dateCreated } }
    override fun observeCardCountsByDeck(): Flow<List<nart.simpleanki.core.data.local.dao.DeckCardCount>> =
        store.map { m -> m.values.filter { !it.isDeleted }.groupingBy { it.deckId }.eachCount()
            .map { (d, c) -> nart.simpleanki.core.data.local.dao.DeckCardCount(d, c) } }
    override suspend fun getDue(deckId: String, now: Long): List<CardEntity> =
        store.value.values.filter { !it.isDeleted && it.deckId == deckId && it.fsrsDue <= now }.sortedBy { it.fsrsDue }
    override suspend fun getById(id: String): CardEntity? = store.value[id]
    override suspend fun getDirty(): List<CardEntity> = store.value.values.filter { it.dirty }
    override suspend fun upsertAll(cards: List<CardEntity>) {
        store.value = store.value.toMutableMap().apply { cards.forEach { put(it.id, it) } }
    }
    override suspend fun clearDirty(id: String, lastModified: Long) {
        store.value[id]?.let { if (it.lastModified == lastModified) upsertAll(listOf(it.copy(dirty = false))) }
    }
}

class FakeReviewLogDao : ReviewLogDao {
    private val store = MutableStateFlow<Map<String, ReviewLogEntity>>(emptyMap())
    // IGNORE semantics: keep the existing row when an id is already present.
    override suspend fun insertAll(logs: List<ReviewLogEntity>) {
        store.value = store.value.toMutableMap().apply { logs.forEach { putIfAbsent(it.id, it) } }
    }
    override suspend fun getDirty(): List<ReviewLogEntity> = store.value.values.filter { it.dirty }
    override suspend fun clearDirty(id: String) {
        store.value[id]?.let { store.value = store.value.toMutableMap().apply { put(id, it.copy(dirty = false)) } }
    }
    override suspend fun getAllIds(): List<String> = store.value.keys.toList()
    override fun observeAll(): Flow<List<ReviewLogEntity>> =
        store.map { m -> m.values.sortedBy { it.review } }
}
