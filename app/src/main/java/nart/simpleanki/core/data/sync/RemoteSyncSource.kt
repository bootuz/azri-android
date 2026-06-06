package nart.simpleanki.core.data.sync

import nart.simpleanki.core.data.firestore.CardDto
import nart.simpleanki.core.data.firestore.DeckDto
import nart.simpleanki.core.data.firestore.FolderDto
import nart.simpleanki.core.data.firestore.ReviewLogDto
import nart.simpleanki.core.data.firestore.StreakStateDto
import nart.simpleanki.core.data.firestore.TypingLogDto

/**
 * Remote sync seam over Firestore. Implemented by [FirestoreSyncService]; faked in tests.
 * Documents live under `users/{uid}/{folders,decks,cards,reviewLogs}` to match the iOS contract.
 */
interface RemoteSyncSource {
    suspend fun fetchFolders(uid: String): List<FolderDto>
    suspend fun pushFolders(uid: String, dtos: List<FolderDto>)

    suspend fun fetchDecks(uid: String): List<DeckDto>
    suspend fun pushDecks(uid: String, dtos: List<DeckDto>)

    suspend fun fetchCards(uid: String): List<CardDto>
    suspend fun pushCards(uid: String, dtos: List<CardDto>)

    suspend fun fetchReviewLogs(uid: String): List<ReviewLogDto>
    suspend fun pushReviewLogs(uid: String, dtos: List<ReviewLogDto>)

    suspend fun fetchTypingLogs(uid: String): List<TypingLogDto>
    suspend fun pushTypingLogs(uid: String, dtos: List<TypingLogDto>)

    suspend fun fetchStreakState(uid: String): StreakStateDto?
    suspend fun pushStreakState(uid: String, dto: StreakStateDto)
}
