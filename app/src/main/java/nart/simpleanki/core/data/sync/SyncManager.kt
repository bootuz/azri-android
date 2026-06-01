package nart.simpleanki.core.data.sync

import nart.simpleanki.core.data.firestore.CardDto
import nart.simpleanki.core.data.firestore.DeckDto
import nart.simpleanki.core.data.firestore.FolderDto
import nart.simpleanki.core.data.local.dao.CardDao
import nart.simpleanki.core.data.local.dao.DeckDao
import nart.simpleanki.core.data.local.dao.FolderDao
import nart.simpleanki.core.data.local.toDomain
import nart.simpleanki.core.data.local.toEntity

/**
 * Two-way sync, mirroring the iOS SyncManager:
 *  1. push — send locally-dirty rows to Firestore, then clear their dirty flag.
 *  2. pull — fetch remote docs and apply any that are newer than the local copy
 *     (last-write-wins by `lastModified`). Soft-deletes (`isDeleted`) propagate
 *     because a deleted remote doc simply overwrites the local row.
 */
class SyncManager(
    private val folderDao: FolderDao,
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val remote: RemoteSyncSource,
) {
    suspend fun sync(uid: String) {
        push(uid)
        pull(uid)
    }

    private suspend fun push(uid: String) {
        folderDao.getDirty().takeIf { it.isNotEmpty() }?.let { rows ->
            remote.pushFolders(uid, rows.map { FolderDto.fromDomain(it.toDomain()) })
            rows.forEach { folderDao.clearDirty(it.id, it.lastModified) }
        }
        deckDao.getDirty().takeIf { it.isNotEmpty() }?.let { rows ->
            remote.pushDecks(uid, rows.map { DeckDto.fromDomain(it.toDomain()) })
            rows.forEach { deckDao.clearDirty(it.id, it.lastModified) }
        }
        cardDao.getDirty().takeIf { it.isNotEmpty() }?.let { rows ->
            remote.pushCards(uid, rows.map { CardDto.fromDomain(it.toDomain()) })
            rows.forEach { cardDao.clearDirty(it.id, it.lastModified) }
        }
    }

    private suspend fun pull(uid: String) {
        remote.fetchFolders(uid).forEach { dto ->
            val domain = dto.toDomain()
            if (domain.id.isNotEmpty() &&
                shouldApplyRemote(folderDao.getById(domain.id)?.lastModified, domain.lastModified)
            ) {
                folderDao.upsertAll(listOf(domain.toEntity(dirty = false)))
            }
        }
        remote.fetchDecks(uid).forEach { dto ->
            val domain = dto.toDomain()
            if (domain.id.isNotEmpty() &&
                shouldApplyRemote(deckDao.getById(domain.id)?.lastModified, domain.lastModified)
            ) {
                deckDao.upsertAll(listOf(domain.toEntity(dirty = false)))
            }
        }
        remote.fetchCards(uid).forEach { dto ->
            val domain = dto.toDomain()
            if (domain.id.isNotEmpty() &&
                shouldApplyRemote(cardDao.getById(domain.id)?.lastModified, domain.lastModified)
            ) {
                cardDao.upsertAll(listOf(domain.toEntity(dirty = false)))
            }
        }
    }

    companion object {
        /** Last-write-wins: apply the remote copy when there is no local row or it is strictly newer. */
        fun shouldApplyRemote(localLastModified: Long?, remoteLastModified: Long): Boolean =
            localLastModified == null || remoteLastModified > localLastModified
    }
}
