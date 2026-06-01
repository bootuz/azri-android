package nart.simpleanki.core.data.sync

import nart.simpleanki.core.data.firestore.CardDto
import nart.simpleanki.core.data.firestore.DeckDto
import nart.simpleanki.core.data.firestore.FolderDto

/**
 * Remote sync seam over Firestore. Implemented by [FirestoreSyncService]; faked in tests.
 * Documents live under `users/{uid}/{folders,decks,cards}` to match the iOS contract.
 */
interface RemoteSyncSource {
    suspend fun fetchFolders(uid: String): List<FolderDto>
    suspend fun pushFolders(uid: String, dtos: List<FolderDto>)

    suspend fun fetchDecks(uid: String): List<DeckDto>
    suspend fun pushDecks(uid: String, dtos: List<DeckDto>)

    suspend fun fetchCards(uid: String): List<CardDto>
    suspend fun pushCards(uid: String, dtos: List<CardDto>)
}
