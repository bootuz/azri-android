package nart.simpleanki.core.data.local

import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.ColorOption
import nart.simpleanki.core.domain.model.Deck
import nart.simpleanki.core.domain.model.DeckLayout
import nart.simpleanki.core.domain.model.Folder
import nart.simpleanki.core.domain.model.Rating
import nart.simpleanki.core.domain.model.ReviewCardFilter
import nart.simpleanki.core.domain.model.ReviewLog

/** Room entity <-> domain mappers. */

fun FolderEntity.toDomain(): Folder = Folder(
    id = id, name = name, emoji = emoji, lastModified = lastModified, isDeleted = isDeleted,
)

fun Folder.toEntity(dirty: Boolean = false): FolderEntity = FolderEntity(
    id = id, name = name, emoji = emoji, lastModified = lastModified, isDeleted = isDeleted, dirty = dirty,
)

fun DeckEntity.toDomain(): Deck = Deck(
    id = id,
    name = name,
    color = ColorOption.fromWire(color),
    autoplay = autoplay,
    shuffled = shuffled,
    layout = DeckLayout.fromWire(layout),
    reviewFilter = ReviewCardFilter.fromWire(reviewFilter),
    folderId = folderId,
    dateCreated = dateCreated,
    lastModified = lastModified,
    isDeleted = isDeleted,
)

fun Deck.toEntity(dirty: Boolean = false): DeckEntity = DeckEntity(
    id = id,
    name = name,
    color = color.wire,
    autoplay = autoplay,
    shuffled = shuffled,
    layout = layout.wire,
    reviewFilter = reviewFilter.wire,
    folderId = folderId,
    dateCreated = dateCreated,
    lastModified = lastModified,
    isDeleted = isDeleted,
    dirty = dirty,
)

fun CardEntity.toDomain(): Card = Card(
    id = id,
    front = front,
    back = back,
    image = image,
    audioName = audioName,
    imagePath = imagePath,
    audioPath = audioPath,
    deckId = deckId,
    dateCreated = dateCreated,
    lastModified = lastModified,
    memorized = memorized,
    fsrsDue = fsrsDue,
    fsrsStability = fsrsStability,
    fsrsDifficulty = fsrsDifficulty,
    fsrsElapsedDays = fsrsElapsedDays,
    fsrsScheduledDays = fsrsScheduledDays,
    fsrsReps = fsrsReps,
    fsrsLapses = fsrsLapses,
    fsrsState = fsrsState,
    fsrsLastReview = fsrsLastReview,
    isDeleted = isDeleted,
    source = source,
    pairId = pairId,
    isReverse = isReverse,
)

fun Card.toEntity(dirty: Boolean = false): CardEntity = CardEntity(
    id = id,
    front = front,
    back = back,
    image = image,
    audioName = audioName,
    imagePath = imagePath,
    audioPath = audioPath,
    deckId = deckId,
    dateCreated = dateCreated,
    lastModified = lastModified,
    memorized = memorized,
    fsrsDue = fsrsDue,
    fsrsStability = fsrsStability,
    fsrsDifficulty = fsrsDifficulty,
    fsrsElapsedDays = fsrsElapsedDays,
    fsrsScheduledDays = fsrsScheduledDays,
    fsrsReps = fsrsReps,
    fsrsLapses = fsrsLapses,
    fsrsState = fsrsState,
    fsrsLastReview = fsrsLastReview,
    isDeleted = isDeleted,
    source = source,
    pairId = pairId,
    isReverse = isReverse,
    dirty = dirty,
)

fun ReviewLogEntity.toDomain(): ReviewLog = ReviewLog(
    rating = Rating.fromValue(rating),
    state = CardState.fromValue(state),
    due = due,
    stability = stability,
    difficulty = difficulty,
    elapsedDays = elapsedDays,
    lastElapsedDays = lastElapsedDays,
    scheduledDays = scheduledDays,
    review = review,
    id = id,
    cardId = cardId,
)

fun ReviewLog.toEntity(dirty: Boolean = true): ReviewLogEntity = ReviewLogEntity(
    id = id,
    cardId = cardId,
    rating = rating.value,
    state = state?.value,
    due = due,
    stability = stability,
    difficulty = difficulty,
    elapsedDays = elapsedDays,
    lastElapsedDays = lastElapsedDays,
    scheduledDays = scheduledDays,
    review = review,
    dirty = dirty,
)
