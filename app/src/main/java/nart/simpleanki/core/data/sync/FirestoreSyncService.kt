package nart.simpleanki.core.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import nart.simpleanki.core.data.firestore.CardDto
import nart.simpleanki.core.data.firestore.DeckDto
import nart.simpleanki.core.data.firestore.FolderDto
import nart.simpleanki.core.data.firestore.ReviewLogDto
import nart.simpleanki.core.data.firestore.StreakStateDto
import nart.simpleanki.core.data.firestore.TypingLogDto

/**
 * Firestore-backed [RemoteSyncSource]. Collections mirror the iOS layout exactly:
 * `users/{uid}/folders`, `users/{uid}/decks`, `users/{uid}/cards`, `users/{uid}/reviewLogs`, `users/{uid}/typingLogs`.
 */
class FirestoreSyncService(
    private val firestore: FirebaseFirestore,
) : RemoteSyncSource {

    private fun col(uid: String, name: String) =
        firestore.collection("users").document(uid).collection(name)

    override suspend fun fetchFolders(uid: String): List<FolderDto> =
        col(uid, "folders").get().await().toObjects(FolderDto::class.java)

    override suspend fun pushFolders(uid: String, dtos: List<FolderDto>) =
        push(uid, "folders", dtos) { it.id }

    override suspend fun fetchDecks(uid: String): List<DeckDto> =
        col(uid, "decks").get().await().toObjects(DeckDto::class.java)

    override suspend fun pushDecks(uid: String, dtos: List<DeckDto>) =
        push(uid, "decks", dtos) { it.id }

    override suspend fun fetchCards(uid: String): List<CardDto> =
        col(uid, "cards").get().await().toObjects(CardDto::class.java)

    override suspend fun pushCards(uid: String, dtos: List<CardDto>) =
        push(uid, "cards", dtos) { it.id }

    override suspend fun fetchReviewLogs(uid: String): List<ReviewLogDto> =
        col(uid, "reviewLogs").get().await().toObjects(ReviewLogDto::class.java)

    override suspend fun pushReviewLogs(uid: String, dtos: List<ReviewLogDto>) =
        push(uid, "reviewLogs", dtos) { it.id }

    override suspend fun fetchTypingLogs(uid: String): List<TypingLogDto> =
        col(uid, "typingLogs").get().await().toObjects(TypingLogDto::class.java)

    override suspend fun pushTypingLogs(uid: String, dtos: List<TypingLogDto>) =
        push(uid, "typingLogs", dtos) { it.id }

    override suspend fun fetchStreakState(uid: String): StreakStateDto? =
        col(uid, "streakState").document("current").get().await().toObject(StreakStateDto::class.java)

    override suspend fun pushStreakState(uid: String, dto: StreakStateDto) {
        col(uid, "streakState").document("current").set(dto).await()
    }

    private suspend fun <T : Any> push(uid: String, name: String, dtos: List<T>, id: (T) -> String?) {
        if (dtos.isEmpty()) return
        val batch = firestore.batch()
        val collection = col(uid, name)
        dtos.forEach { dto ->
            val docId = id(dto) ?: return@forEach
            batch.set(collection.document(docId), dto)
        }
        batch.commit().await()
    }
}
