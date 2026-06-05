package nart.simpleanki.core.data.sync

import nart.simpleanki.core.data.firestore.CardDto
import nart.simpleanki.core.data.firestore.DeckDto
import nart.simpleanki.core.data.firestore.FolderDto
import nart.simpleanki.core.data.firestore.ReviewLogDto
import nart.simpleanki.core.data.local.dao.CardDao
import nart.simpleanki.core.data.local.dao.DeckDao
import nart.simpleanki.core.data.local.dao.FolderDao
import nart.simpleanki.core.data.local.dao.ReviewLogDao
import nart.simpleanki.core.data.local.toDomain
import nart.simpleanki.core.data.local.toEntity
import nart.simpleanki.core.data.media.MediaManager

/**
 * Two-way sync, mirroring the iOS SyncManager:
 *  1. push — send locally-dirty rows to Firestore, then clear their dirty flag.
 *  2. pull — fetch remote docs and apply any that are newer than the local copy
 *     (last-write-wins by `lastModified`). Soft-deletes (`isDeleted`) propagate
 *     because a deleted remote doc simply overwrites the local row.
 *
 * Review logs are the exception: immutable append-only events, unioned by id on pull
 * (never overwritten, no last-write-wins).
 */
class SyncManager(
    private val folderDao: FolderDao,
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val reviewLogDao: ReviewLogDao,
    private val remote: RemoteSyncSource,
    private val media: MediaManager,
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
            val updated = rows.mapNotNull { entity ->
                val card = entity.toDomain()
                val imagePath = media.ensureUploaded(card.image, card.imagePath)
                val audioPath = media.ensureUploaded(card.audioName, card.audioPath)
                // A local-only file that failed to upload: keep the card dirty, retry next sync.
                val imageFailed = card.image != null && card.imagePath == null && imagePath == null
                val audioFailed = card.audioName != null && card.audioPath == null && audioPath == null
                if (imageFailed || audioFailed) null
                else card.copy(imagePath = imagePath, audioPath = audioPath)
            }
            if (updated.isNotEmpty()) {
                remote.pushCards(uid, updated.map { CardDto.fromDomain(it) })
                updated.forEach { card ->
                    val current = cardDao.getById(card.id)
                    if (current != null && current.lastModified == card.lastModified) {
                        cardDao.upsertAll(
                            listOf(current.copy(imagePath = card.imagePath, audioPath = card.audioPath, dirty = false)),
                        )
                    }
                }
            }
        }
        // Review logs are immutable, append-only events: push any dirty rows, then clear the flag.
        reviewLogDao.getDirty().takeIf { it.isNotEmpty() }?.let { rows ->
            remote.pushReviewLogs(uid, rows.map { ReviewLogDto.fromDomain(it.toDomain()) })
            rows.forEach { reviewLogDao.clearDirty(it.id) }
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
                media.prefetch(domain.image, domain.imagePath)
                media.prefetch(domain.audioName, domain.audioPath)
            }
        }
        // Review logs: union by id (immutable, so no last-write-wins) — insert only the ids we lack.
        val localLogIds = reviewLogDao.getAllIds().toSet()
        remote.fetchReviewLogs(uid)
            .filter { it.id.isNotEmpty() && it.id !in localLogIds }
            .map { it.toDomain().toEntity(dirty = false) }
            .takeIf { it.isNotEmpty() }
            ?.let { reviewLogDao.insertAll(it) }
    }

    companion object {
        /** Last-write-wins: apply the remote copy when there is no local row or it is strictly newer. */
        fun shouldApplyRemote(localLastModified: Long?, remoteLastModified: Long): Boolean =
            localLastModified == null || remoteLastModified > localLastModified
    }
}
