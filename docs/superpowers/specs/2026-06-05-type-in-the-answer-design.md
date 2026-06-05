# Type-in-the-Answer (Type Practice Mode) — Design

**Date:** 2026-06-05
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/type-practice-mode` (off `main`).
**Scope:** Phase 1 of 2. Phase 2 (typing diagnostics: trouble-words list + recognition–recall gap)
is a separate, stacked spec built on Phase 1's logs.

## Goal

Add a standalone **Type Practice** study mode: the user types each card's answer, it's auto-checked,
and wrong cards are retried until correct. It is **fully decoupled from FSRS** — it writes no
scheduler state and no review logs — and keeps its **own separate progress** (a per-deck mastery ring
+ an end-of-session report) derived from a new typing-log store.

## Why decoupled (the core product decision)

Typing produces an **objective** signal (the app *knows* whether you got it right), unlike FSRS
ratings, which are **self-assessed**. Keeping typing logs separate from FSRS is deliberate: it makes
the typing record a more trustworthy account of what the user can actually *produce*, and it is the
foundation for the Phase-2 "recognition–recall gap" (cards FSRS thinks are known but the user can't
type) — a diagnostic that is only possible *because* the two systems are not coupled.

Trade-off accepted: practicing does **not** advance the FSRS schedule and does **not** count toward
the study streak / daily goal. This mode is positioned as *practice*, not *review*. (An opt-in
"apply this session to my schedule" bridge is explicitly deferred.)

## Background (existing code this builds on)

- **Card model** (`core/domain/model/DomainModels.kt`): flat `Card(front, back, image?, audio?, …,
  isReverse, pairId)`. No Anki-style note-types, so "type the answer" must be a **study mode**, not a
  per-card template. The `back` is always text (the image is the *front* prompt), so every card is
  typeable; reverse cards are separate rows with swapped front/back.
- **Decoupled study precedent** (`feature/review/ReviewViewModel.kt`): the cram/Review mode already
  snapshots a deck's cards once, applies `deck.reviewFilter` (Originals/Reverses/All) + optional
  shuffle, and writes nothing back. Type Practice mirrors this skeleton and adds the typing loop.
- **Deck entry points** (`feature/deckdetail/DeckDetailScreen.kt`): a primary **Study** (FSRS) button
  + an outlined **Review** (cram) button. Type Practice slots in as a third action here.
- **Append-only event store precedent** (`ReviewLogRepository` / `ReviewLogDao` /
  `ReviewLogEntity`): append-only, `@Insert(onConflict = IGNORE)`, `dirty` flag for push,
  `getAllIds()` for pull dedup, synced via `SyncManager` + `RemoteSyncSource` + `FirestoreSyncService`.
  Typing logs are also append-only events and mirror this path exactly.
- **Pure-derivation precedent** (`StreakProvider` derives the streak from review logs;
  `StreakReconciler` is a pure object). Typing mastery is derived the same way (pure functions over
  typing logs), not stored as per-card state.

## Decisions (from brainstorming)

- **Separate mode, fully decoupled from FSRS.** No `SchedulingService` calls, no `cardRepository.save`,
  no review-log writes. (Camp 2 / Quizlet "Write" model, not Anki's in-review typing.)
- **Card source / loop:** whole deck, **retry-until-correct**. Cards snapshot once, shuffled; respects
  `deck.reviewFilter`. Cards with a blank `back` are skipped.
- **Answer matching:** **normalized, accents enforced** — case-insensitive, trims + collapses inner
  whitespace, ignores surrounding punctuation, but `café` ≠ `cafe`. A manual **"I was right"**
  override on wrong answers is always available (handles legitimate near-misses / synonyms).
- **Wrong-answer behavior:** **reveal, then requeue later.** A wrong (or "Don't know") first attempt
  reveals the correct answer + the user's input, then the card returns later in the same session; the
  user must type it correctly to clear it. Session ends when every card is cleared.
- **What's recorded:** the card's **first attempt** only (one log row per card per session) — its
  correctness drives all stats/mastery. Requeued retries are pure session-loop mechanics and are not
  persisted. An "I was right" override on the first attempt stores `correct = true`.
- **Mastery rule:** a card is **mastered** iff its **latest first-try was correct** (latest log per
  card wins). Honestly regresses if missed in a later session.
- **Phase-1 progress surfaces:** end-of-session **report** + persistent per-deck **mastery ring**.
- **Entry point:** deck detail, new nav route `typePractice/{deckId}`. Deck-level only for v1.

## Components

### Pure domain (Android-free, unit-tested — the testable heart)

**`core/domain/typing/AnswerMatcher.kt`**
- `normalize(s: String): String` — lowercase, `trim`, collapse internal whitespace to single spaces,
  strip surrounding punctuation; **preserve accents/diacritics**.
- `matches(typed: String, expected: String): Boolean` — `normalize(typed) == normalize(expected)`.

**`core/domain/typing/TypePracticeSession.kt`** — a pure reducer for the loop. Constructed with the
ordered card pool + the set of previously-mastered card ids (for "newly mastered"). Holds the
remaining/requeue queue and per-card first-try outcomes. Operations:
- `current`: the card being prompted (or null when finished).
- `submit(answer): SubmitResult` — on the **first** attempt for a card, records the (provisional,
  in-memory) first-try outcome; returns `Correct` (advance) or `Wrong(expected)` (reveal). On a
  **requeue** attempt, returns `Correct` (clear) or `Wrong(expected)` (re-reveal, stays queued)
  without changing the recorded first-try outcome.
- `override()` — flips the current card's (still-provisional) first-try outcome to correct and clears
  it (the "I was right" path).
- `continueAfterWrong()` — finalizes the wrong first-try outcome, requeues the current card to later
  in the session, and advances.
- Each first-try outcome is held **in memory** by the session until finalized; the VM appends exactly
  one `TypingLog` per card at finalization (see data flow), so the persisted `correct` already
  reflects any override — no row updates against the append-only store.
- `report(): SessionReport(completed, firstTryCorrect, firstTryAccuracy, bestCombo, newlyMastered)`.

**`core/domain/typing/TypingMastery.kt`**
- `latestPerCard(logs): Map<String, TypingLog>` (latest by timestamp).
- `masteredCardIds(logs): Set<String>` (latest first-try correct).
- `deckMastery(logs, deckCardIds: Set<String>): DeckMastery(mastered: Int, total: Int)`.

### Data layer (mirrors review logs)

- **Domain:** `TypingLog(id: String = "", cardId: String = "", deckId: String = "", correct: Boolean,
  typedText: String, timestamp: Long)` in `DomainModels.kt`.
- **`TypingLogEntity`** (`RoomEntities.kt`, table `typing_log`, `@PrimaryKey id`, indices on `cardId`
  and `deckId`, `dirty: Boolean = true`).
- **`TypingLogDao`** (`dao/Daos.kt`): `@Insert(onConflict = IGNORE) insertAll`, `getDirty`,
  `clearDirty(id)`, `getAllIds`, `observeAll()` (ORDER BY timestamp), `observeForDeck(deckId)`.
- **Mappers** (`TypingLogMappers.kt`): entity↔domain.
- **`TypingLogRepository(dao)`** (`Repositories.kt`): `append(log)`, `observeLogs()`,
  `observeLogsForDeck(deckId)`.
- **`TypingMasteryProvider(typingLogRepository, cardRepository)`**: `observeDeckMastery(deckId):
  Flow<DeckMastery>` = `combine(observeLogsForDeck, cardsForDeck) -> TypingMastery.deckMastery`.
- **`AzriDatabase`**: version **3 → 4**; add `TypingLogEntity` to `entities`, add `typingLogDao()`,
  add **`MIGRATION_3_4`** (CREATE TABLE `typing_log` + the two indices; **no SQL `DEFAULT` clauses**;
  verified char-for-char against the generated `AzriDatabase_Impl` schema).

### Firestore sync (append-only, like review logs)

- **`TypingLogDto`** (`FirestoreDtos.kt`): snake_case `@PropertyName`s; document path
  `users/{uid}/typingLogs/{id}`; `fromDomain` / `toDomain`.
- **`RemoteSyncSource`**: `fetchTypingLogs(uid): List<TypingLogDto>`,
  `pushTypingLogs(uid, dtos)`.
- **`FirestoreSyncService`**: implement both against the `typingLogs` subcollection.
- **`SyncManager`**: add a `typingLogDao` constructor param (after `reviewLogDao`); push dirty rows
  then `clearDirty`, and pull-union with `getAllIds` dedup — identical to the review-log steps. No
  LWW (logs are immutable).

### Feature layer (UI)

- **`feature/typepractice/TypePracticeViewModel(deckId, cardRepository, deckRepository,
  typingLogRepository, now, logManager)`**: in `load()`, snapshot deck cards (respect
  `reviewFilter` + shuffle like `ReviewViewModel`), drop blank-back cards, read previously-mastered
  ids from typing logs, construct `TypePracticeSession`. Exposes `TypePracticeUiState(loading,
  prompt card, input, phase = Typing|Revealed, revealedAnswer, lastTypedText, remaining, finished,
  report)`. Methods `onInput`, `onSubmit`, `onContinue`, `onOverride`, `onDontKnow`, `restart`. On
  each **first** attempt, `typingLogRepository.append(...)`. Analytics events mirror the existing
  `cram_session_start` style.
- **`feature/typepractice/TypePracticeScreen.kt`**: `TypePracticeScreen` (Koin VM) +
  stateless `TypePracticeContent` + `SessionReport` composable. Top bar `"Type · N left"` with a
  close action. Prompt panel renders the **front** (reusing `MediaImage` / `AudioPlayButton`); an
  **auto-focused** `TextField` below with IME "Done" → submit (same autofocus approach as the card
  editor). Revealed state shows the correct answer + the user's input with **Continue** and **I was
  right** buttons.
- **Deck detail:** add a **Type** action button (next to Study/Review) and a **"X / N mastered"**
  ring; `DeckDetailViewModel` reads `TypingMasteryProvider.observeDeckMastery(deckId)`.

### Wiring

- **`AzriNavHost`**: route `typePractice/{deckId}` → `TypePracticeScreen`; deck detail wires
  `onTypePractice`.
- **Koin `AppModule`**: `single { TypingLogRepository(get()) }`,
  `single { TypingMasteryProvider(get(), get()) }`, `viewModel { TypePracticeViewModel(...) }`;
  expose `typingLogDao` from the database and add it to the `SyncManager` registration.

## Data flow

Launch `typePractice/{deckId}` → VM snapshots deck cards (filter + shuffle, drop blank backs) + reads
previously-mastered ids → builds `TypePracticeSession`. User types → `onSubmit` → `AnswerMatcher`:
- **Correct (first try)** → outcome finalized as correct → VM appends `TypingLog(correct = true)` →
  advance.
- **Wrong (first try)** → reveal (no log yet; outcome held provisionally) → user taps **Continue**
  (finalize wrong → VM appends `TypingLog(correct = false)` → requeue) or **I was right** (override →
  finalize correct → VM appends `TypingLog(correct = true)` → card clears).
- Requeued card returns later → correct clears it; wrong re-reveals. Neither writes a new log (the
  first-try outcome is already finalized and persisted).
Session end → `SessionReport`. Separately, deck detail's ring observes `TypingMasteryProvider`, which
recomputes mastered/total whenever typing logs change.

**Logging invariant:** exactly **one** `TypingLog` per card per session, appended by the VM at the
moment the card's first attempt is *finalized* (correct-on-first-try, or after **Continue** /
**I was right** on a wrong first try). Because the write is deferred to finalization, the persisted
`correct` already reflects any override — there are no updates to the append-only store.

## Error handling / edge cases

- **Empty pool** (deck has no typeable cards / all blank backs) → immediately show an empty-session
  state with a Done action; write nothing.
- **Blank input** submit → treated as a wrong first attempt (reveal path), not a crash.
- **"I was right" override** → authoritative; records `correct = true`. (No abuse mitigation in v1; an
  `overridden` flag for honesty analytics is deferred to Phase 2 if wanted.)
- **Sync** → append-only, `@Insert IGNORE` + `getAllIds` dedup make append and pull idempotent;
  offline appends flush on next sync via the `dirty` flag, exactly like review logs.
- **Deck deleted / card removed mid-life** → logs are historical events keyed by id; mastery
  derivation intersects logs with *current* deck card ids, so removed cards drop out of the ring
  naturally.

## Testing

All Gradle commands prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`, run from
`/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

- **`AnswerMatcherTest`** (JVM): case-insensitivity; whitespace trim/collapse; surrounding punctuation
  stripped; **accents enforced** (`café` ≠ `cafe`); blank/empty; exact match.
- **`TypePracticeSessionTest`** (JVM): first-try correct advances; wrong → reveal → requeue → card
  returns; clearing needs a correct retype; **override** clears + records correct; combo tracking;
  **newly-mastered** reporting; end-of-session stats (completed, first-try accuracy, best combo).
- **`TypingMasteryTest`** (JVM): latest-log-per-card wins (mastery **regresses** when the latest
  first-try is wrong); deck mastered/total counts; empty-logs.
- **`TypingLogMappersTest`**: entity↔domain round-trip.
- **`TypePracticeViewModelTest`**: with a new `FakeTypingLogRepository` + existing fake card/deck
  repos — `load` builds the session; a first attempt appends exactly one log; override path; finish
  produces the report. Mirrors `StudyViewModelTest`.
- **Sync**: extend existing `SyncManager` coverage with typing-log push-dirty / pull-union dedup (or
  compile-verified, matching how streak-state sync was validated).
- **Build gate:** `:app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug` +
  `:app:compileDebugAndroidTestKotlin` (since composable signatures change).

## Out of scope (Phase 1)

- **Phase 2 diagnostics** — trouble-words list, recognition–recall gap shelf (separate stacked spec).
- Char-level colored diff (wrong answers show the full correct answer + the user's input only).
- Folder / "all decks" Type entry (deck-level only; extendable later like Review).
- Home / deck-list mastery ring (deck-detail only for v1).
- Opt-in "apply this session to my FSRS schedule" bridge.
- Time-to-answer / WPM / closeness fields in the log (store just `correct` + `typedText`).
- Any streak / daily-goal credit for practice (deliberately none).
- A Settings toggle or selectable strictness (fixed: normalized + accents, with the manual override).
- Audio-dictation / TTS prompts.

## Commit / process rules

- No "claude" mention in commit messages; **no Co-Authored-By / attribution trailer**.
- Do **not** `git add` the unrelated untracked `docs/superpowers/plans/2026-06-04-realtime-study-queue.md`.
- PR body (when opened) includes the `🤖 Generated with [Claude Code](https://claude.com/claude-code)`
  footer.
