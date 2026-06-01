# Azri Android Port — Phase 1 (MVP Core Slice) Design

**Date:** 2026-06-01
**Status:** Approved (via goal directive + clarifying-question selections)
**Author:** Claude (autonomous goal session)

## 1. Goal & Context

Port the Azri (SimpleAnki) iOS flashcard app to Android with **feature parity over time**, using
**Jetpack Compose + Material 3 (default Android components)**, **tests for every component**, **Google
Sign-In** (replacing Apple Sign-In), against the **same Firebase project** (`simple-anki-166ea`).

The iOS app is large (~77 SwiftUI views, ~40 feature areas) with shared business logic in a Swift
package, `AzriKit`. **None of that Swift code is reusable on Android** — it is re-implemented in Kotlin
against the **identical Firestore document shapes**, so iOS and Android sync through one backend.

Because the full surface is multi-month, the work is **decomposed into sequenced sub-projects**. This
spec covers **Phase 1 only**.

### Scope decisions (from clarifying questions)

- **Sequencing:** MVP core slice first, then layer features.
- **In-scope later phases:** (2) Paywall + Google Play Billing, (3) Import (CSV + APKG).
- **Dropped from the port:** AI card generation, Text-to-Speech, Stats/Daily Goals/Sharing.
- **Entitlement (until Play Billing exists):** treat the user as **premium in dev** so gated paths are testable.

### Phase 1 (this spec) includes

Google Auth · Folders/Decks/Cards CRUD · FSRS study & review · Firestore two-way sync · local Room
cache · core settings (FSRS preset, daily limits, account). A runnable, syncing app.

## 2. Backend Contract (must match iOS exactly)

Firebase project **`simple-anki-166ea`** (billing enabled). Firestore layout:

- `users/{uid}` — user document.
- `users/{uid}/cards/{cardId}` — `CardFirestore`.
- `users/{uid}/decks/{deckId}` — `DeckFirestore`.
- `users/{uid}/folders/{folderId}` — `FolderFirestore`.
- `users/{uid}/cards/{cardId}/history/{logId}` (review logs) — `ReviewLogFirestore`.
- `shared_decks/{id}` — top-level (Phase 3, not Phase 1).

### Document fields (Android DTOs mirror these names/types verbatim)

**CardFirestore:** `id, front, back, image?, audioName?, imagePath?, audioPath?, deckId, dateCreated,
lastModified, memorized, fsrsDue, fsrsStability, fsrsDifficulty, fsrsElapsedDays, fsrsScheduledDays,
fsrsReps, fsrsLapses, fsrsState, fsrsLastReview?, isDeleted, source?, pairId?, isReverse?`

**DeckFirestore:** `id, name, color, autoplay, shuffled, layout, reviewFilter?, folderId?, dateCreated,
lastModified, isDeleted`

**FolderFirestore:** `id, name, lastModified, isDeleted, emoji?`

**ReviewLogFirestore:** `rating, state?, due?, stability?, difficulty?, elapsedDays?, lastElapsedDays?,
scheduledDays?, review`

Reconciliation: **last-write-wins by `lastModified`**, soft-delete via `isDeleted` (mirrors iOS
`SyncManager`). Exact `CodingKeys`/field-name mapping will be transcribed from
`AzriKit/Sources/AzriKit/Sync/FirestoreModels.swift` during implementation.

## 3. Architecture

**Offline-first, Room as single source of truth + a sync engine** (chosen over Firestore-cache-only and
online-only because it reproduces iOS sync semantics and is deterministically testable).

### Layers / Gradle modules

- **`core:data`**
  - Room entities: `CardEntity`, `DeckEntity`, `FolderEntity`, `ReviewLogEntity` + DAOs (Flow-returning).
  - Firestore DTOs mirroring section 2 + mappers (DTO ↔ Room ↔ domain).
  - Repositories: `CardRepository`, `DeckRepository`, `FolderRepository`, `AuthRepository`.
  - `SyncManager` + `FirestoreSyncService`: push local dirty rows to Firestore; Firestore snapshot
    listeners → reconcile (last-write-wins) → Room. Network awareness + pending-delete handling.
- **`core:domain`**
  - Pure-Kotlin domain models (`Card`, `Deck`, `Folder`, `ReviewLog`, `Rating`, `CardState`).
  - **FSRS scheduling** via `open-spaced-repetition/FSRS-Kotlin` (see section 4), wrapped in a
    `SchedulingService`.
  - `StudyQueueBuilder` (due/new selection honoring daily limits, shuffle, reviewFilter).
  - Use cases where they add clarity (e.g. `RateCardUseCase`).
- **`feature:*`** — Compose screen + ViewModel per area:
  - `auth` (Google Sign-In), `library` (folders + decks), `deckDetail` (cards list),
    `study` (flip / rate 1–4 / FSRS update / session summary / undo), `cardForm` (add/edit),
    `deckSettings`, `folders` (CRUD), `settings`.
- **`app`** — Application class, Hilt graph, Navigation-Compose host, Material 3 theme, Firebase init.

### Cross-cutting

- **DI:** Hilt. **Async:** Coroutines + Flow / StateFlow. **Nav:** Navigation-Compose.
- **UI:** Jetpack Compose + Material 3, light/dark themes mirroring the iOS color tokens (SAPrimary, etc.).
- **Min SDK 24**, target/compile SDK 36.
- **Package / applicationId:** `nart.simpleanki` (matches the iOS `nart.SimpleAnki` family).
  The scaffold's `com.example.azriflashcards` is replaced.

### Data flow

Compose → ViewModel (`StateFlow`) → Repository → Room (reads emit Flows). Writes:
Room (mark dirty) → `SyncManager` → Firestore. Inbound: Firestore listener → reconcile → Room → Flow → UI.

## 4. FSRS Scheduling (highest-risk component)

**Decision:** Android uses **`open-spaced-repetition/FSRS-Kotlin` (FSRS-6, 21 params)**, vendored as
source (no published Maven artifact). iOS remains on FSRS-5 (`swift-fsrs`) for now.

### Version mismatch — accepted, with mitigation

iOS schedules with FSRS-5 (19 params); Android with FSRS-6 (21 params). The **card-state schema is
identical** across versions (`stability, difficulty, due, state, reps, lapses, lastReview`), so Firestore
documents remain fully compatible — no migration, no corruption. The only difference is that a card
reviewed alternately on both platforms gets *valid but slightly different* intervals. This is acceptable
per the user decision. **Optional follow-up (out of Phase 1 scope):** upgrade iOS to FSRS-6 for exact
cross-platform parity.

### Configuration parity within Android

- Pin **FSRS-6 default 21 weights** explicitly so behavior can't drift with library updates:
  ```
  0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
  1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542
  ```
- Map the iOS presets to FSRS-6 `requestRetention` / `maximumInterval`:

  | Preset | requestRetention | maximumInterval |
  |---|---|---|
  | optimal | 0.90 | 365 |
  | aggressive | 0.95 | 90 |
  | relaxed | 0.85 | 365 |

- `enableShortTerm = true` (matches iOS). Fuzz: keep **off** for deterministic stored `due` unless the
  library makes it deterministic per-card; revisit if iOS fuzz behavior must be matched.

### Integration

FSRS-Kotlin has no Maven release, so its Kotlin sources are **vendored** into `core:domain` under a
clearly-labelled package (e.g. `nart.simpleanki.fsrs.vendor`) with an upstream-commit note, wrapped by a
project-owned `SchedulingService` interface so the rest of the app never touches the vendored API
directly. This isolates any future library swap or iOS-parity change behind one seam.

### Tests

- `SchedulingService` golden vectors: fixed (card-state, rating) → expected (stability, difficulty,
  state, reps, lapses, interval) for all four ratings, both short-term and long-term paths, asserting the
  vendored v6 output is stable across builds.
- Round-trip: a card's FSRS fields survive Room ↔ Firestore DTO ↔ domain mapping unchanged.

## 5. Testing Strategy ("tests for every component")

- **Unit (JVM, `test/`):** `SchedulingService`/FSRS (golden vectors), all mappers, repositories
  (fake DAO + fake Firestore), all ViewModels (coroutine-test + Turbine), `SyncManager` reconciliation,
  `StudyQueueBuilder`.
- **Instrumented (`androidTest/`):** Room DAO tests (in-memory DB), Compose UI tests per screen
  (render, key interactions, state changes).
- **TDD** for scheduler, mappers, and sync reconciliation (test-first).
- A component is "done" only with its test(s) green.

## 6. Build Order (Phase 1)

1. **Re-scaffold:** Compose + Material 3, Hilt, Firebase BoM, `google-services.json`, Google Sign-In
   (Credential Manager + Firebase Auth), package `nart.simpleanki`, module skeleton, version catalog.
2. **Data layer:** Room entities/DAOs, Firestore DTOs + mappers, repositories, `SyncManager` +
   `FirestoreSyncService`. (TDD mappers + reconciliation.)
3. **FSRS + study queue:** integrate FSRS-Kotlin, `SchedulingService`, `StudyQueueBuilder`, golden tests.
4. **Auth + shell:** Google Sign-In flow, anonymous fallback if iOS supports it, app nav host,
   sign-in-required gating.
5. **Library + Deck detail:** folders/decks list with due badges; deck detail = card list + search.
6. **Card form + Deck settings + Folder CRUD:** add/edit card (front/back, reverse pair), deck
   settings (layout/color/autoplay/shuffle/reviewFilter/folder), folder add/edit/delete.
7. **Study/Review flow:** flip card, rate 1–4, apply FSRS, write review log, session summary, undo.
8. **Settings:** FSRS preset, daily new/review limits, account info, sign-out, account deletion.
9. **Live sync verification:** round-trip cards/decks/folders against `simple-anki-166ea`; confirm iOS
   ↔ Android convergence and FSRS state agreement.

## 7. Manual Actions Required From User

1. **Confirm package name** `nart.simpleanki` (assumed; say so if you want different). Drives Firebase
   Android-app registration.
2. **Register Android app in Firebase** — Claude will do this via the Firebase MCP
   (`firebase_create_app`) + obtain `google-services.json` (`firebase_get_sdk_config`).
3. **Enable Google sign-in provider** in Firebase Auth (console toggle) if not already enabled — Claude
   will check state first and report if action is needed.
4. **SHA-1 fingerprint** — Claude generates the debug-keystore SHA-1 and registers it via MCP
   (`firebase_create_android_sha`). For a release build later, user provides the release SHA-1.
5. **Provide an emulator or physical device** for UI verification.

## 8. Out of Scope (Phase 1)

AI generation, TTS, Paywall/Play Billing (Phase 2), CSV/APKG import (Phase 3), deck sharing, stats,
daily goals, widgets, MCP server, media (image/audio) capture UI (sync of media *refs* is preserved in
DTOs but capture/upload UI is deferred).

## 9. Risks

- **FSRS parity** — mitigated by same-org library + golden vectors.
- **Sync reconciliation edge cases** (clock skew, concurrent edits, deletes) — mirror iOS rules; test
  explicitly.
- **Google Sign-In config** (SHA-1 / OAuth client) — most common setup failure; handled via MCP + early
  end-to-end auth test.
- **Firestore security rules** — confirm existing rules permit the Android app's reads/writes under the
  same `users/{uid}` ownership model (no rule change expected; verify early).
