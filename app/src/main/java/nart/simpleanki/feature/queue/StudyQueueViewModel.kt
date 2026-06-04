package nart.simpleanki.feature.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FolderRepository
import nart.simpleanki.core.billing.EntitlementRepository
import nart.simpleanki.core.billing.Entitlements
import nart.simpleanki.core.data.settings.SettingsRepository
import nart.simpleanki.core.data.settings.dailyGoalTotal
import nart.simpleanki.core.domain.fsrs.QueueSortOrder
import nart.simpleanki.core.domain.fsrs.StudyQueueBuilder
import nart.simpleanki.core.domain.fsrs.withDueTicks
import kotlinx.coroutines.launch
import kotlin.random.Random
import java.util.Calendar
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.ColorOption

/** A deck with cards waiting today — a chip in the "Study by" strip (Decks mode). */
data class DeckQueueItem(
    val deckId: String,
    val deckName: String,
    val color: ColorOption,
    val dueCount: Int,
    val newCount: Int,
) {
    val total: Int get() = dueCount + newCount
}

/** A folder with cards waiting today — a chip in the "Study by" strip (Folders mode). */
data class FolderQueueItem(
    val folderId: String,
    val name: String,
    val deckCount: Int,
    val dueCount: Int,
    val newCount: Int,
) {
    val total: Int get() = dueCount + newCount
}

/** One ready card, for the read-only "Queue" preview list. */
data class QueueCardItem(
    val cardId: String,
    val front: String,
    val deckName: String?,
    val folderName: String?,
)

data class StudyQueueUiState(
    val loading: Boolean = true,
    /** Cards ready to study today across all decks (uncapped — the daily goal does not limit this). */
    val readyCount: Int = 0,
    val newCount: Int = 0,
    val dueCount: Int = 0,
    val estimatedMinutes: Int = 0,
    val decks: List<DeckQueueItem> = emptyList(),
    val folders: List<FolderQueueItem> = emptyList(),
    /** The ready cards in queue order — a display-only preview ("Queue" list). */
    val queueCards: List<QueueCardItem> = emptyList(),
    // Daily goal (soft target). [goalMet] is independent of [hasWork]: you can hit your goal
    // with cards still queued, or clear the queue without reaching it.
    val dailyGoalEnabled: Boolean = false,
    val goalTotal: Int = 0,
    val studiedToday: Int = 0,
    val sortOrder: QueueSortOrder = QueueSortOrder.DueDate,
    /**
     * Whether the user owns any (non-deleted) card at all — a *lifetime* signal, not a "today"
     * one. Distinguishes a brand-new user (show onboarding) from a returning user who has merely
     * cleared today's queue (show "All caught up").
     */
    val hasAnyCards: Boolean = false,
    val showPremiumNudge: Boolean = false,
) {
    val hasWork: Boolean get() = readyCount > 0
    val hasFolders: Boolean get() = folders.isNotEmpty()
    val goalMet: Boolean get() = dailyGoalEnabled && goalTotal > 0 && studiedToday >= goalTotal
    val goalRemaining: Int get() = (goalTotal - studiedToday).coerceAtLeast(0)
}

/**
 * The global "Today" queue across every deck: applies the user's FSRS daily limits to all
 * cards combined (mirrors the iOS study-queue home), and breaks down per-deck for "Up next".
 */
class StudyQueueViewModel(
    cardRepository: CardRepository,
    deckRepository: DeckRepository,
    folderRepository: FolderRepository,
    private val settingsRepository: SettingsRepository,
    private val entitlementRepository: EntitlementRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    val uiState: StateFlow<StudyQueueUiState> =
        combine(
            cardRepository.observeAllCards().withDueTicks(now),
            deckRepository.observeDecks(),
            folderRepository.observeFolders(),
            settingsRepository.settings,
            entitlementRepository.entitlement,
        ) { (cards, nowMillis), decks, folders, settings, entitlement ->
            val queue = StudyQueueBuilder.buildStudyQueue(
                cards = cards,
                nowMillis = nowMillis,
                // Uncapped: the daily goal is a soft target, not a queue limit.
                newLimit = Int.MAX_VALUE,
                reviewLimit = Int.MAX_VALUE,
            )
            val sorted = StudyQueueBuilder.sort(queue, settings.queueSortOrder, settings.queueShuffleSeed)
            val newCount = queue.count { it.fsrsState == CardState.New.value }
            val dueCount = queue.size - newCount

            val startOfToday = startOfDay(nowMillis)
            val studiedToday = cards.count {
                !it.isDeleted && (it.fsrsLastReview ?: 0L) >= startOfToday
            }

            val deckById = decks.associateBy { it.id }
            val folderNameById = folders.associate { it.id to it.name }

            // Decks-mode chips: decks that have something waiting.
            val perDeck = decks.mapNotNull { deck ->
                val deckCards = cards.filter { it.deckId == deck.id && !it.isDeleted }
                val due = deckCards.count { it.fsrsState != CardState.New.value && it.fsrsDue <= nowMillis }
                val new = deckCards.count { it.fsrsState == CardState.New.value }
                if (due + new == 0) null
                else DeckQueueItem(deck.id, deck.name, deck.color, dueCount = due, newCount = new)
            }.sortedWith(compareByDescending<DeckQueueItem> { it.dueCount }.thenByDescending { it.total })

            // Folders-mode chips: folders whose decks have something waiting.
            val perFolder = folders.mapNotNull { folder ->
                val folderDecks = decks.filter { it.folderId == folder.id }
                val deckIds = folderDecks.map { it.id }.toSet()
                val folderCards = cards.filter { it.deckId in deckIds && !it.isDeleted }
                val due = folderCards.count { it.fsrsState != CardState.New.value && it.fsrsDue <= nowMillis }
                val new = folderCards.count { it.fsrsState == CardState.New.value }
                if (due + new == 0) null
                else FolderQueueItem(folder.id, folder.name, deckCount = folderDecks.size, dueCount = due, newCount = new)
            }.sortedWith(compareByDescending<FolderQueueItem> { it.dueCount }.thenByDescending { it.total })

            // The ready cards, in the chosen order, with deck + folder names for the preview list.
            val queueCards = sorted.map { card ->
                val deck = deckById[card.deckId]
                QueueCardItem(
                    cardId = card.id,
                    front = card.front,
                    deckName = deck?.name,
                    folderName = deck?.folderId?.let { folderNameById[it] },
                )
            }

            val hasAnyCards = cards.any { !it.isDeleted }

            StudyQueueUiState(
                loading = false,
                readyCount = queue.size,
                newCount = newCount,
                dueCount = dueCount,
                estimatedMinutes = estimateMinutes(queue.size),
                decks = perDeck,
                folders = perFolder,
                queueCards = queueCards,
                sortOrder = settings.queueSortOrder,
                dailyGoalEnabled = settings.dailyGoalEnabled,
                goalTotal = settings.dailyGoalTotal,
                studiedToday = studiedToday,
                hasAnyCards = hasAnyCards,
                showPremiumNudge = Entitlements.shouldShowPremiumNudge(
                    isPremium = entitlement.isPremium,
                    dismissed = settings.premiumNudgeDismissed,
                    hasAnyCards = hasAnyCards,
                ),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StudyQueueUiState(),
        )

    fun dismissPremiumNudge() = viewModelScope.launch { settingsRepository.setPremiumNudgeDismissed(true) }

    /** Persist the chosen order; selecting Shuffle also rolls a new seed (so re-tapping reshuffles). */
    fun setSortOrder(order: QueueSortOrder) = viewModelScope.launch {
        settingsRepository.setQueueSortOrder(order)
        if (order == QueueSortOrder.Shuffle) settingsRepository.setQueueShuffleSeed(Random.nextLong())
    }

    /** Rough study-time estimate: ~9s per card, at least a minute if there's anything to do. */
    private fun estimateMinutes(cardCount: Int): Int =
        if (cardCount == 0) 0 else ((cardCount * 9 + 59) / 60).coerceAtLeast(1)

    /** Device-local midnight for [nowMillis] — the cutoff for "studied today". */
    private fun startOfDay(nowMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
