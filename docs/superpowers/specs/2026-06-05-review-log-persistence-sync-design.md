# Review-Log Persistence + Firestore Sync — Design

**Date:** 2026-06-05
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/review-log-sync` (off `main`).
**Sub-project 1 of 2.** Foundation for the streak system (sub-project 2, separate spec). This adds
the currently-missing review-log write + sync path; the streak will derive from it.

## Goal

Persist every FSRS review as an immutable `ReviewLog` row in Room and sync those logs to Firestore,
fixing the gap where `StudyViewModel.onRate` computes `ScheduleResult.log` and **discards it**. This
gives the app a real per-review history (needed by the streak, and later by stats/heatmap), and a
synced backup across the user's devices.

## Background: current state

- `SchedulingService.schedule(card, rating, now)` returns `ScheduleResult(card, log)` where `log` is
  a `ReviewLog`. `StudyViewModel.onRate` only does `cardRepository.save(result.card)` — `result.log`
  is dropped. No Room table, DAO, repository, or sync writes review logs anywhere.
- `ReviewLog` (domain) has FSRS-snapshot fields + `review: Long` (epoch millis) but **no `id` and no
  `cardId`** — it is currently just an FSRS event payload.
- `ReviewLogDto` exists (Firestore-facing). Sync goes through `RemoteSyncSource` (fetch/push per
  type) → `FirestoreSyncService` writing `users/{uid}/{folders,decks,cards}`, orchestrated by
  `SyncManager` (push dirty rows → clear dirty; pull → last-write-wins by `lastModified`).
- Room: `AzriDatabase` is `version = 1` (entities Card/Deck/Folder), built with
  `fallbackToDestructiveMigration(dropAllTables = true)`.

## Key properties that simplify this

- **Review logs are immutable, append-only events.** A rating that happened never changes. So sync
  needs only **union by id** (push local logs the server lacks; insert remote logs you lack) — no
  `lastModified` last-write-wins merge, no edit/delete propagation.
- **Only `StudyViewModel.onRate` writes logs.** Cram/Review mode is read-only → no logs.
- **`schedule()` stays pure.** It returns the FSRS payload; the repository assigns persistence
  identity (`id`, `cardId`) at append time.

## Decisions

- **Flat Firestore collection `users/{uid}/reviewLogs/{logId}`** (each doc carries a `cardId`
  field), NOT the iOS `cards/{id}/history` subcollection. Fits the existing flat `push`/`fetch`
  seam; "all logs" (streak/stats) is one query. The subcollection/`collectionGroup` path and iOS
  parity are out of scope.
- **Additive `Migration(1, 2)`** that `CREATE TABLE review_logs` — preserves local data instead of
  the destructive wipe-and-re-pull. `fallbackToDestructiveMigration` remains only as a backstop.
- **Log identity:** `id` = a UUID assigned by the repository at append; `cardId` = the rated card.
  The Firestore doc id is the log `id`.

## Components

### 1. Room (`local/`)

**`ReviewLogEntity`** (table `review_logs`):
```kotlin
@Entity(
    tableName = "review_logs",
    indices = [Index("cardId"), Index("review")],
)
data class ReviewLogEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val rating: Int,
    val state: Int?,
    val due: Long?,
    val stability: Double?,
    val difficulty: Double?,
    val elapsedDays: Double,
    val lastElapsedDays: Double,
    val scheduledDays: Double,
    val review: Long,        // epoch millis of the review
    val dirty: Boolean = true,
)
```

**`ReviewLogDao`** (mirrors the existing DAO style; logs are immutable so `clearDirty` needs no
`lastModified` guard):
```kotlin
@Dao
interface ReviewLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)   // union: ignore an id already present
    suspend fun insertAll(logs: List<ReviewLogEntity>)

    @Query("SELECT * FROM review_logs WHERE dirty = 1")
    suspend fun getDirty(): List<ReviewLogEntity>

    @Query("UPDATE review_logs SET dirty = 0 WHERE id = :id")
    suspend fun clearDirty(id: String)

    @Query("SELECT id FROM review_logs")
    suspend fun getAllIds(): List<String>

    @Query("SELECT * FROM review_logs ORDER BY review")
    fun observeAll(): Flow<List<ReviewLogEntity>>   // streak/stats consume this later
}
```
Note: `INSERT ... OnConflictStrategy.IGNORE` makes pull idempotent (an existing log id is a no-op).
The local append uses the same insert with a fresh UUID, so it never collides.

**`AzriDatabase`**: add `ReviewLogEntity` to `entities`, bump `version = 2`, expose
`abstract fun reviewLogDao(): ReviewLogDao`. Add:
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS review_logs (" +
                "id TEXT NOT NULL PRIMARY KEY, cardId TEXT NOT NULL, rating INTEGER NOT NULL, " +
                "state INTEGER, due INTEGER, stability REAL, difficulty REAL, " +
                "elapsedDays REAL NOT NULL, lastElapsedDays REAL NOT NULL, scheduledDays REAL NOT NULL, " +
                "review INTEGER NOT NULL, dirty INTEGER NOT NULL DEFAULT 1)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_review_logs_cardId ON review_logs(cardId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_review_logs_review ON review_logs(review)")
    }
}
```
Wire it in the Room builder (`AppModule.kt:134`): `.addMigrations(MIGRATION_1_2)` (keep
`fallbackToDestructiveMigration` as a backstop). Add `single { get<AzriDatabase>().reviewLogDao() }`.

### 2. Domain + mappers

- `ReviewLog` gains `val id: String` and `val cardId: String`. **Defaulted** (`id: String = ""`,
  `cardId: String = ""`) so `SchedulingService.schedule()` — which constructs the payload without
  identity — still compiles unchanged; the repository fills them in on append.
- `RoomMappers`: `ReviewLogEntity.toDomain(): ReviewLog` and `ReviewLog.toEntity(dirty): ReviewLogEntity`.

### 3. `ReviewLogRepository`

```kotlin
class ReviewLogRepository(
    private val dao: ReviewLogDao,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** Appends one immutable review event (assigns a fresh id, marks dirty for sync). */
    suspend fun append(cardId: String, log: ReviewLog) {
        dao.insertAll(listOf(log.copy(id = newId(), cardId = cardId).toEntity(dirty = true)))
    }
    fun observeLogs(): Flow<List<ReviewLog>> = dao.observeAll().map { it.map(ReviewLogEntity::toDomain) }
}
```
`newId` is injectable so tests are deterministic. Register in Koin.

### 4. Write path (`StudyViewModel.onRate`)

After the existing card save, append the log (best-effort, never blocks rating):
```kotlin
viewModelScope.launch { cardRepository.save(result.card) }
viewModelScope.launch { reviewLogRepository.append(card.id, result.log) }
```
Add `reviewLogRepository: ReviewLogRepository` to the constructor; update the Koin `StudyViewModel`
block and `StudyViewModelTest` construction.

### 5. Sync

- **`ReviewLogDto`** gains `var id: String = ""` and `var cardId: String = ""` (plus existing FSRS
  fields). `fromDomain(r)` maps `r.id`/`r.cardId`; `toDomain()` fills them back.
- **`RemoteSyncSource`**: add
  ```kotlin
  suspend fun fetchReviewLogs(uid: String): List<ReviewLogDto>
  suspend fun pushReviewLogs(uid: String, dtos: List<ReviewLogDto>)
  ```
- **`FirestoreSyncService`**: implement with the existing helpers —
  `fetchReviewLogs` = `col(uid, "reviewLogs").get()...toObjects(ReviewLogDto::class.java)`;
  `pushReviewLogs` = `push(uid, "reviewLogs", dtos) { it.id }`.
- **`SyncManager`**: add a logs block.
  - **push:** `reviewLogDao.getDirty()` → `remote.pushReviewLogs(uid, rows.map { ReviewLogDto.fromDomain(it.toDomain()) })` → `rows.forEach { reviewLogDao.clearDirty(it.id) }`.
  - **pull:** `remote.fetchReviewLogs(uid)` → for each, `insertAll` (IGNORE makes existing ids no-ops); skip blank ids. No `shouldApplyRemote`/LWW — logs are immutable. (Optional optimization: pre-load `getAllIds()` into a set and only insert misses, to avoid redundant writes; either is correct.)
  - Add `reviewLogDao` to the `SyncManager` constructor and the Koin registration.
- The inline `FakeRemote : RemoteSyncSource` in `SyncManagerTest` and any other implementers gain the
  two new methods (backed by an in-memory list).

## Data flow

Rate a card → `schedule()` → `onRate` saves the card **and** `reviewLogRepository.append(card.id,
log)` (UUID, `dirty = true`) → next `SyncManager.sync(uid)` pushes dirty logs to
`users/{uid}/reviewLogs` and clears their dirty flag; pull unions in any remote logs not present
locally. Sub-project 2 (streak) later reads `ReviewLogRepository.observeLogs()`.

## Error handling

- Append runs in `viewModelScope` (fire-and-forget like the card save) — a failure never blocks
  rating; the row simply isn't written (a missed log slightly under-counts history, acceptable).
- A failed push leaves the row `dirty` for the next sync (same as cards).
- Immutability ⇒ no merge conflicts; `INSERT OR IGNORE` ⇒ pull is idempotent.
- A log whose `cardId` references a since-deleted card is harmless — logs are standalone rows with
  no foreign-key enforcement, and the streak only reads `review` timestamps.

## Testing

- **`ReviewLogDao` / Room** (instrumented or Robolectric per existing DAO tests): insert + `getDirty`
  + `clearDirty`; `INSERT OR IGNORE` dedupes a repeated id; `observeAll` ordering.
- **`ReviewLogRepository`** (JVM, fake DAO): `append` assigns the injected id, sets `cardId`, marks
  dirty; `observeLogs` maps to domain.
- **`RoomMappers`**: `ReviewLogEntity ↔ ReviewLog` round-trip.
- **`SyncManagerTest`** (extend, fake remote): dirty logs are pushed then cleared; a clean log is not
  re-pushed; remote logs are unioned into local; an already-present id is not duplicated.
- **`Migration(1,2)`**: a `MigrationTestHelper` test (open v1, run migration, assert `review_logs`
  exists and accepts an insert) if migration-test infra is added; otherwise the migration is
  exercised by the instrumented DAO test on a v2 DB and the build.
- **`StudyViewModelTest`**: rating a card appends exactly one log with the correct `cardId`,
  `rating`, and `review` timestamp (inject a fake `ReviewLogRepository` or assert via a fake DAO).

**Build/test prefix:** Gradle commands MUST be prefixed with
`export JAVA_HOME=/opt/homebrew/opt/openjdk &&`, run from
`/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`. Emulator unavailable → instrumented
(`androidTest`) sources compile-verified only; JVM unit tests run normally.

## Out of scope

The streak itself (sub-project 2); the iOS-parity `cards/{id}/history` subcollection path; log
pruning/retention caps; backfilling history for cards rated before this ships (logs accrue from the
first rating afterward); any stats/heatmap UI; logging cram/Review-mode browsing (it's read-only).
