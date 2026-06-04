# Review / Cram Mode (Android) — Design

**Date:** 2026-06-04
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/review-cram-mode` (off `main`; no overlap with open PRs #12/#13).
**Ports:** iOS `AzriKit/Sources/AzriKit/Review/ReviewManager.swift` +
`SimpleAnkiSwiftUI/.../Core/Review/ReviewView.swift`.

## Goal

Let users browse/self-quiz a deck's (or folder's) cards **on demand, without spaced repetition** —
a read-only flip-through that never touches FSRS scheduling or the study queue. Mirrors the iOS
Review feature: a horizontal paging carousel of flip cards. This is the "review anytime" mode the
user asked for when we built the deck-detail "You're all caught up!" state.

## Background

- iOS `ReviewManager.prepareCards(from: deck)` selects a deck's cards by `deck.reviewFilter`
  (`all` / `originalsOnly` / `reversesOnly`), shuffles if `deck.shuffled`, and excludes
  `memorized` cards. It has **no rating and no scheduling** — `advance()` just moves to the next
  card. `ReviewView` is a horizontal paging `ScrollView` of `FlipCardView`s: swipe between cards,
  tap to flip (resets on scroll to a new card), a "Tap to flip" hint, an "{i} of {n}" title, and a
  "Quit" button.
- Android already has the scaffolding: `StudyQueueBuilder.buildReviewQueue(cards, filter,
  shuffleSeed)` (defined, used by no screen), the `ReviewCardFilter` enum, and the per-deck
  `Deck.reviewFilter` / `Deck.shuffled` fields (synced via Room + Firestore). `Card.memorized`
  exists and is synced but has **no Android UI** (set only on iOS). The reusable
  `FlipCard(card, revealed, onFlip, modifier)` composable (tap-to-flip, scroll support) is ready to
  drop into a pager.

## Decisions (from brainstorming)

- **UX:** mirror the iOS horizontal carousel exactly (swipe between cards, tap to flip, "{i} of
  {n}", Quit, tap-to-flip hint). **No rating, no FSRS, no end-of-deck summary** — bare browse.
- **Read-only:** the mode never writes cards or affects the study queue / scheduling.
- **Entry points (v1):** (a) an **always-present "Review" action on the deck-detail screen**
  (visible in every state, including "all caught up"), and (b) **folder-level review** from the
  folder-detail screen.
- **Deck pool:** cards filtered by that deck's `reviewFilter`, shuffled iff `deck.shuffled`.
- **Folder pool:** all cards across the folder's decks (`ReviewCardFilter.All`), always shuffled.
- **Both pools exclude `memorized` and deleted cards** (parity: a card memorized on iOS stays out
  of Android review too).
- **New self-contained feature** (`feature/review/`), NOT a fork of the rating-based `StudyScreen`
  (keeps the two very different UIs decoupled; avoids regressing the rating flow).

## Components

### `core/domain/fsrs/StudyQueueBuilder.kt` → `buildReviewQueue` (modify, one predicate)

Add a `memorized` exclusion to the existing filter (currently `!it.isDeleted` + direction):

```kotlin
val filtered = cards.filter { !it.isDeleted && !it.memorized }.filter { card ->
    when (filter) {
        ReviewCardFilter.All -> true
        ReviewCardFilter.OriginalsOnly -> !card.isReverse
        ReviewCardFilter.ReversesOnly -> card.isReverse
    }
}
return if (shuffleSeed != null) filtered.shuffled(Random(shuffleSeed)) else filtered
```

Signature unchanged: `buildReviewQueue(cards, filter, shuffleSeed: Long? = null)`. Pure; still
not driven by FSRS due dates.

### `feature/review/ReviewViewModel.kt` (new)

```kotlin
data class ReviewUiState(
    val loading: Boolean = true,
    val cards: List<Card> = emptyList(),
)
```

The top bar shows only "{i} of {n}" + Quit (matching iOS), so the VM needs no deck/folder **name**
and therefore no `FolderRepository` dependency.

Constructor mirrors `StudyViewModel`:
`(deckId: String?, folderId: String?, cardRepository, deckRepository, now: () -> Long = { System.currentTimeMillis() }, logManager)`.
On `init` it `launch { load() }`:

- **Deck** (`deckId != null`): `val deck = deckRepository.getById(deckId)`; cards =
  `cardRepository.observeCards(deckId).first()`; pool =
  `StudyQueueBuilder.buildReviewQueue(cards, deck?.reviewFilter ?: ReviewCardFilter.All,
  shuffleSeed = if (deck?.shuffled == true) now() else null)`.
- **Folder** (`folderId != null`): `deckIds = deckRepository.observeDecksInFolder(folderId).first()
  .map { it.id }.toSet()`; cards = `cardRepository.observeAllCards().first().filter { it.deckId in
  deckIds }`; pool = `buildReviewQueue(cards, ReviewCardFilter.All, shuffleSeed = now())`
  (always shuffled).
- Sets `ReviewUiState(loading = false, cards = pool)`. Logs a `review_session_start`
  event (`{deck_id|folder_id, count}`) via `logManager`, mirroring iOS analytics.

The pool is an immutable snapshot (`.first()`), like the study session — it does not live-update.

### `feature/review/ReviewScreen.kt` (new)

`@Composable fun ReviewScreen(deckId: String?, folderId: String?, onDone: () -> Unit)` —
resolves a `ReviewViewModel` (Koin, keyed by deckId/folderId like `StudyScreen`), collects state:

- **Loading:** centered progress.
- **Empty pool** (`cards.isEmpty()`): centered "No cards to review here." + a "Close" button →
  `onDone()`.
- **Populated:** a `HorizontalPager(state = rememberPagerState { cards.size })` where each page is
  `FlipCard(card = cards[page], revealed = revealed, onFlip = { revealed = true; showHint = false })`
  inside padding. Flip state resets per page:
  `var revealed by remember(pagerState.currentPage) { mutableStateOf(false) }` — swiping to a new
  card shows its front again (mirrors iOS clearing flips on scroll). Only the current page is
  interactive (off-screen pages render `revealed = false`).
  - **Top bar** (a `Row` or small `TopAppBar`): a "Quit" text button → `onDone()` on the left, and
    `"${pagerState.currentPage + 1} of ${cards.size}"` centered.
  - **"Tap to flip" hint** at the bottom (icon + text), shown while `showHint` is true
    (`var showHint by remember { mutableStateOf(true) }`), cross-fading out after the first flip.
- `@Preview`s: a populated 3-card pool and the empty state.

### Wiring (navigation + screens)

- **`ui/navigation/AzriNavHost.kt`:** add two routes mirroring the `study/...` ones:
  - `composable("review/{deckId}") { ReviewScreen(deckId = it, folderId = null, onDone = { nav.popBackStack() }) }`
  - `composable("reviewFolder/{folderId}") { ReviewScreen(deckId = null, folderId = it, onDone = { nav.popBackStack() }) }`
  - In the `deck/{deckId}` composable, pass `onReview = { nav.navigate("review/$deckId") }`.
  - In the `folder/{folderId}` composable, pass `onReview = { nav.navigate("reviewFolder/$folderId") }`.
- **`DeckDetailScreen.kt`:** add an `onReview: () -> Unit` parameter and surface an always-present
  "Review" action — a secondary/outlined button beneath the existing study action area, rendered in
  **every** state (studyable, all-caught-up, and empty-deck the button is hidden only when the deck
  has zero cards). Place it so the all-caught-up state finally offers a way to study on demand.
- **`FolderDetailScreen.kt`:** add an `onReview: () -> Unit` parameter and a "Review" action
  (e.g., a top-bar `IconButton` with a play/cards icon, or a button in the header) that starts a
  folder review.

## Data flow

Tap Review on a deck/folder → nav to `review/{id}` / `reviewFolder/{id}` → `ReviewViewModel.load()`
snapshots cards, applies `buildReviewQueue` (+ shuffle) → `ReviewScreen` renders the pager → swipe
browses, tap flips (resets per page), Quit pops back. No persistence, no scheduling, no effect on
the FSRS study queue.

## Error / empty handling

Pure read-only presentation; the only I/O is the one-shot snapshot read. Empty pool (deck/folder
has no cards, the direction filter excludes everything, or all cards are memorized) → the empty
state, not a crash and not an instantly-dismissing session. A folder with no decks → empty state.

## Testing

- **`StudyQueueBuilderTest`** (extend): `buildReviewQueue` excludes `memorized` and `isDeleted`
  cards; honors each `ReviewCardFilter` direction; shuffle is deterministic for a fixed seed and
  identity-ordered when `shuffleSeed == null`.
- **`ReviewViewModelTest`** (new, `runTest` + fakes, mirroring `StudyQueueViewModelTest` idioms):
  - Deck review builds the pool from that deck's `reviewFilter`, shuffles iff `deck.shuffled`
    (fixed `now` → deterministic order), and excludes memorized/deleted cards.
  - Folder review aggregates cards across the folder's decks with `ReviewCardFilter.All`, shuffled.
  - Empty deck / empty folder → `loading = false`, `cards == []`.
- **`ReviewScreen`:** build-verified + `@Preview`s (populated + empty); no Compose UI unit tests
  (codebase convention).

**Build/test prefix:** Gradle commands MUST be prefixed with
`export JAVA_HOME=/opt/homebrew/opt/openjdk &&`, run from
`/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`. New tests are JVM unit tests and
run normally; the emulator is unavailable, so instrumented sources are compile-verified only.

## Out of scope (v1)

Rating / FSRS effects (read-only by definition); a "mark memorized" toggle (no Android UI today —
synced field only); audio autoplay on page settle (manual play via the existing `AudioPlayButton`);
an end-of-deck "Reviewed N — Restart/Done" summary (iOS has none); a deck-list ⋮ "Review" entry and
a global "review everything" entry (deferred to v2); per-folder shuffle/filter settings; the deck/
folder name in the Review top bar (iOS shows only "{i} of {n}").
