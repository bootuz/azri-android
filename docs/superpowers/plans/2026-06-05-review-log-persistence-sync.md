# Review-Log Persistence + Firestore Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist every FSRS review as an immutable `ReviewLog` row in Room and sync those logs to Firestore (the write path currently dropped in `StudyViewModel.onRate`).

**Architecture:** New `review_logs` Room table (additive `Migration(1,2)`) + `ReviewLogDao` + `ReviewLogRepository`; `StudyViewModel.onRate` appends `result.log`; sync via a flat `users/{uid}/reviewLogs` collection with **append-only union** semantics (logs are immutable — no last-write-wins). Identity (`id`, `cardId`) is assigned by the repository; `schedule()` stays pure.

**Tech Stack:** Kotlin, Room, Firestore, Koin, JUnit4 + coroutines-test. Test DAOs are in-memory fakes (`core.data.repository.FakeDaos`).

**Build/test prefix:** ALL Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`. Run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

**Spec:** `docs/superpowers/specs/2026-06-05-review-log-persistence-sync-design.md`.

**Note:** No migration-test infra exists; the `review_logs` entity/DAO/migration are compile-verified (Room validates schema against entities at open time, so the migration SQL must match the entity exactly — see Task 2). DAO/repo/sync behavior is covered by JVM tests using a `FakeReviewLogDao`. Emulator down → `androidTest` sources compile-verified only.

---

### Task 1: `ReviewLog` + `ReviewLogDto` gain `id` / `cardId`

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/domain/model/DomainModels.kt`
- Modify: `app/src/main/java/nart/simpleanki/core/data/firestore/FirestoreDtos.kt`
- Test: `app/src/test/java/nart/simpleanki/core/data/firestore/ReviewLogDtoTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/nart/simpleanki/core/data/firestore/ReviewLogDtoTest.kt`:

```kotlin
package nart.simpleanki.core.data.firestore

import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Rating
import nart.simpleanki.core.domain.model.ReviewLog
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewLogDtoTest {

    @Test
    fun roundTrip_preservesIdCardIdRatingAndReview() {
        val log = ReviewLog(
            rating = Rating.Good,
            state = CardState.Review,
            due = 5_000L,
            stability = 1.5,
            difficulty = 6.0,
            elapsedDays = 2.0,
            lastElapsedDays = 1.0,
            scheduledDays = 4.0,
            review = 1_700_000_000_000L,
            id = "log-1",
            cardId = "card-7",
        )
        val back = ReviewLogDto.fromDomain(log).toDomain()
        assertEquals("log-1", back.id)
        assertEquals("card-7", back.cardId)
        assertEquals(Rating.Good, back.rating)
        assertEquals(1_700_000_000_000L, back.review)
        assertEquals(CardState.Review, back.state)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (does not compile)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.data.firestore.ReviewLogDtoTest"`
Expected: FAIL — compilation error: `ReviewLog` has no `id`/`cardId` parameter and `ReviewLogDto` has no `id`/`cardId`.

- [ ] **Step 3a: Add `id`/`cardId` to the `ReviewLog` domain model**

In `DomainModels.kt`, the `ReviewLog` data class currently ends with `val review: Long,`. Add two **defaulted** fields at the end (defaults keep `SchedulingService.schedule()` — which doesn't pass them — compiling):

```kotlin
data class ReviewLog(
    val rating: Rating,
    val state: CardState?,
    val due: Long?,
    val stability: Double?,
    val difficulty: Double?,
    val elapsedDays: Double = 0.0,
    val lastElapsedDays: Double = 0.0,
    val scheduledDays: Double = 0.0,
    val review: Long,
    val id: String = "",
    val cardId: String = "",
)
```

- [ ] **Step 3b: Add `id`/`cardId` to `ReviewLogDto` + mapping**

In `FirestoreDtos.kt`, update `ReviewLogDto`. Add the two fields (plain `id`, snake_case `card_id` to match the other fields' naming) and map them in both directions:

```kotlin
data class ReviewLogDto(
    var id: String = "",
    @get:PropertyName("card_id") @set:PropertyName("card_id") var cardId: String = "",
    var rating: Int = Rating.Again.value,
    var state: Int? = null,
    var due: Timestamp? = null,
    var stability: Double? = null,
    var difficulty: Double? = null,
    var review: Timestamp = Timestamp(Date(0)),
    @get:PropertyName("elapsed_days") @set:PropertyName("elapsed_days") var elapsedDays: Double? = null,
    @get:PropertyName("last_elapsed_days") @set:PropertyName("last_elapsed_days") var lastElapsedDays: Double? = null,
    @get:PropertyName("scheduled_days") @set:PropertyName("scheduled_days") var scheduledDays: Double? = null,
) {
    fun toDomain(): ReviewLog = ReviewLog(
        rating = Rating.fromValue(rating),
        state = nart.simpleanki.core.domain.model.CardState.fromValue(state),
        due = due?.toMillis(),
        stability = stability,
        difficulty = difficulty,
        elapsedDays = elapsedDays ?: 0.0,
        lastElapsedDays = lastElapsedDays ?: 0.0,
        scheduledDays = scheduledDays ?: 0.0,
        review = review.toMillis(),
        id = id,
        cardId = cardId,
    )

    companion object {
        fun fromDomain(r: ReviewLog): ReviewLogDto = ReviewLogDto(
            id = r.id,
            cardId = r.cardId,
            rating = r.rating.value,
            state = r.state?.value,
            due = r.due?.toTimestamp(),
            stability = r.stability,
            difficulty = r.difficulty,
            review = r.review.toTimestamp(),
            elapsedDays = r.elapsedDays,
            lastElapsedDays = r.lastElapsedDays,
            scheduledDays = r.scheduledDays,
        )
    }
}
```

Also update the stale comment above the class from `// MARK: - Review log (stored under cards/{id}/history)` to `// MARK: - Review log (stored flat under users/{uid}/reviewLogs)`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.data.firestore.ReviewLogDtoTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/domain/model/DomainModels.kt app/src/main/java/nart/simpleanki/core/data/firestore/FirestoreDtos.kt app/src/test/java/nart/simpleanki/core/data/firestore/ReviewLogDtoTest.kt
git commit -m "Add id and cardId to ReviewLog and ReviewLogDto"
```

---

### Task 2: Room layer — entity, DAO, mappers, migration, DB wiring

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/data/local/RoomEntities.kt`
- Modify: `app/src/main/java/nart/simpleanki/core/data/local/dao/Daos.kt`
- Modify: `app/src/main/java/nart/simpleanki/core/data/local/RoomMappers.kt`
- Modify: `app/src/main/java/nart/simpleanki/core/data/local/AzriDatabase.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Test: `app/src/test/java/nart/simpleanki/core/data/local/ReviewLogMapperTest.kt`

The mapper is the TDD unit; the entity/DAO/migration/DB are compile-verified (no migration-test infra).

- [ ] **Step 1: Write the failing mapper test**

Create `app/src/test/java/nart/simpleanki/core/data/local/ReviewLogMapperTest.kt`:

```kotlin
package nart.simpleanki.core.data.local

import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Rating
import nart.simpleanki.core.domain.model.ReviewLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewLogMapperTest {

    @Test
    fun roundTrip_entityToDomainToEntity() {
        val log = ReviewLog(
            rating = Rating.Hard, state = CardState.Learning, due = 9_000L,
            stability = 2.0, difficulty = 5.0, elapsedDays = 1.0, lastElapsedDays = 0.5,
            scheduledDays = 3.0, review = 1_700_000_000_000L, id = "l1", cardId = "c1",
        )
        val entity = log.toEntity(dirty = true)
        assertEquals("l1", entity.id)
        assertEquals("c1", entity.cardId)
        assertTrue(entity.dirty)
        val back = entity.toDomain()
        assertEquals(log.copy(), back)   // all fields preserved
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (does not compile)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.data.local.ReviewLogMapperTest"`
Expected: FAIL — `ReviewLogEntity`, `toEntity`, `toDomain` for `ReviewLog` don't exist yet.

- [ ] **Step 3a: Add `ReviewLogEntity`**

In `RoomEntities.kt` (it already imports `Entity`, `Index`, `PrimaryKey`), append:

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
    val review: Long,
    val dirty: Boolean = true,
)
```

- [ ] **Step 3b: Add `ReviewLogDao`**

In `Daos.kt` (it already imports `Dao`, `Insert`, `OnConflictStrategy`, `Query`, `Flow`), add the import `import nart.simpleanki.core.data.local.ReviewLogEntity` and append:

```kotlin
@Dao
interface ReviewLogDao {
    // IGNORE makes both append (fresh UUID) and pull (union) idempotent: an existing id is a no-op.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(logs: List<ReviewLogEntity>)

    @Query("SELECT * FROM review_logs WHERE dirty = 1")
    suspend fun getDirty(): List<ReviewLogEntity>

    @Query("UPDATE review_logs SET dirty = 0 WHERE id = :id")
    suspend fun clearDirty(id: String)

    @Query("SELECT id FROM review_logs")
    suspend fun getAllIds(): List<String>

    @Query("SELECT * FROM review_logs ORDER BY review")
    fun observeAll(): Flow<List<ReviewLogEntity>>
}
```

- [ ] **Step 3c: Add mappers**

In `RoomMappers.kt`, add imports `import nart.simpleanki.core.domain.model.Rating`, `import nart.simpleanki.core.domain.model.CardState`, `import nart.simpleanki.core.domain.model.ReviewLog`, and append:

```kotlin
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
```

- [ ] **Step 3d: Add the entity to the DB, bump version, add the migration**

Replace the contents of `AzriDatabase.kt` with:

```kotlin
package nart.simpleanki.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import nart.simpleanki.core.data.local.dao.CardDao
import nart.simpleanki.core.data.local.dao.DeckDao
import nart.simpleanki.core.data.local.dao.FolderDao
import nart.simpleanki.core.data.local.dao.ReviewLogDao

@Database(
    entities = [CardEntity::class, DeckEntity::class, FolderEntity::class, ReviewLogEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AzriDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun deckDao(): DeckDao
    abstract fun folderDao(): FolderDao
    abstract fun reviewLogDao(): ReviewLogDao
}

/**
 * v1 -> v2: add the immutable `review_logs` table. Additive (preserves local data) — the column
 * types, nullability, and the two index names below MUST match [ReviewLogEntity] exactly, because
 * Room validates the live schema against the entity on open.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `review_logs` (" +
                "`id` TEXT NOT NULL, `cardId` TEXT NOT NULL, `rating` INTEGER NOT NULL, " +
                "`state` INTEGER, `due` INTEGER, `stability` REAL, `difficulty` REAL, " +
                "`elapsedDays` REAL NOT NULL, `lastElapsedDays` REAL NOT NULL, `scheduledDays` REAL NOT NULL, " +
                "`review` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_cardId` ON `review_logs` (`cardId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_review` ON `review_logs` (`review`)")
    }
}
```

- [ ] **Step 3e: Wire the migration + DAO in DI**

In `AppModule.kt`:

Add the import `import nart.simpleanki.core.data.local.MIGRATION_1_2` (near `import nart.simpleanki.core.data.local.AzriDatabase`).

Change the Room builder to add the migration (keep the destructive fallback as a backstop):

```kotlin
    single {
        Room.databaseBuilder(androidContext(), AzriDatabase::class.java, "azri.db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
```

Add a DAO single right after `single { get<AzriDatabase>().cardDao() }`:

```kotlin
    single { get<AzriDatabase>().reviewLogDao() }
```

- [ ] **Step 4: Run the mapper test + compile everything**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.data.local.ReviewLogMapperTest" :app:compileDebugKotlin`
Expected: PASS + BUILD SUCCESSFUL (entity/DAO/DB/migration all compile).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/data/local/ app/src/main/java/nart/simpleanki/di/AppModule.kt app/src/test/java/nart/simpleanki/core/data/local/ReviewLogMapperTest.kt
git commit -m "Add review_logs Room table, DAO, mappers, and migration 1->2"
```

---

### Task 3: `ReviewLogRepository` + `FakeReviewLogDao`

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/data/repository/Repositories.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Modify: `app/src/test/java/nart/simpleanki/core/data/repository/FakeDaos.kt`
- Test: `app/src/test/java/nart/simpleanki/core/data/repository/ReviewLogRepositoryTest.kt`

- [ ] **Step 1: Add `FakeReviewLogDao` (needed by this and later tasks)**

In `app/src/test/java/nart/simpleanki/core/data/repository/FakeDaos.kt`, add imports `import nart.simpleanki.core.data.local.ReviewLogEntity` and `import nart.simpleanki.core.data.local.dao.ReviewLogDao`, then append:

```kotlin
class FakeReviewLogDao : ReviewLogDao {
    private val store = MutableStateFlow<Map<String, ReviewLogEntity>>(emptyMap())
    // IGNORE semantics: keep the existing row when an id is already present.
    override suspend fun insertAll(logs: List<ReviewLogEntity>) {
        store.value = store.value.toMutableMap().apply { logs.forEach { putIfAbsent(it.id, it) } }
    }
    override suspend fun getDirty(): List<ReviewLogEntity> = store.value.values.filter { it.dirty }
    override suspend fun clearDirty(id: String) {
        store.value[id]?.let { store.value = store.value.toMutableMap().apply { put(id, it.copy(dirty = false)) } }
    }
    override suspend fun getAllIds(): List<String> = store.value.keys.toList()
    override fun observeAll(): Flow<List<ReviewLogEntity>> =
        store.map { m -> m.values.sortedBy { it.review } }
}
```

- [ ] **Step 2: Write the failing repository test**

Create `app/src/test/java/nart/simpleanki/core/data/repository/ReviewLogRepositoryTest.kt`:

```kotlin
package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Rating
import nart.simpleanki.core.domain.model.ReviewLog
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewLogRepositoryTest {

    private fun sampleLog(review: Long) = ReviewLog(
        rating = Rating.Good, state = CardState.Review, due = 0L,
        stability = 1.0, difficulty = 5.0, review = review,
    )

    @Test
    fun append_assignsInjectedId_cardId_andMarksDirty() = runTest {
        val dao = FakeReviewLogDao()
        val repo = ReviewLogRepository(dao, newId = { "log-1" })

        repo.append(cardId = "card-7", log = sampleLog(review = 1_000L))

        val logs = repo.observeLogs().first()
        assertEquals(1, logs.size)
        assertEquals("log-1", logs[0].id)
        assertEquals("card-7", logs[0].cardId)
        assertEquals(Rating.Good, logs[0].rating)
        assertEquals(1_000L, logs[0].review)
        // Marked dirty so the next sync pushes it.
        assertEquals(listOf("log-1"), dao.getDirty().map { it.id })
    }
}
```

- [ ] **Step 3: Run the test to verify it fails (does not compile)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.data.repository.ReviewLogRepositoryTest"`
Expected: FAIL — `unresolved reference: ReviewLogRepository`.

- [ ] **Step 4: Implement `ReviewLogRepository`**

In `Repositories.kt`, ensure these imports exist (add any missing): `import nart.simpleanki.core.data.local.dao.ReviewLogDao`, `import nart.simpleanki.core.data.local.toDomain`, `import nart.simpleanki.core.data.local.toEntity`, `import nart.simpleanki.core.domain.model.ReviewLog`, `import kotlinx.coroutines.flow.Flow`, `import kotlinx.coroutines.flow.map`, `import java.util.UUID`. Append:

```kotlin
/** Immutable, append-only store of FSRS review events (the streak/stats data source). */
class ReviewLogRepository(
    private val dao: ReviewLogDao,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** Appends one review event: assigns a fresh id + the card id, marked dirty for the next sync. */
    suspend fun append(cardId: String, log: ReviewLog) {
        dao.insertAll(listOf(log.copy(id = newId(), cardId = cardId).toEntity(dirty = true)))
    }

    fun observeLogs(): Flow<List<ReviewLog>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
}
```

- [ ] **Step 5: Register in DI**

In `AppModule.kt`, add after `single { CardRepository(get()) }`:

```kotlin
    single { ReviewLogRepository(get()) }
```

Add the import `import nart.simpleanki.core.data.repository.ReviewLogRepository` if the file imports repositories individually (check the import block; if it uses a wildcard or already imports the package, skip).

- [ ] **Step 6: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.data.repository.ReviewLogRepositoryTest" :app:compileDebugKotlin`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/data/repository/Repositories.kt app/src/main/java/nart/simpleanki/di/AppModule.kt app/src/test/java/nart/simpleanki/core/data/repository/FakeDaos.kt app/src/test/java/nart/simpleanki/core/data/repository/ReviewLogRepositoryTest.kt
git commit -m "Add ReviewLogRepository and FakeReviewLogDao"
```

---

### Task 4: Persist the log on rating (`StudyViewModel.onRate`)

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/study/StudyViewModel.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/study/StudyViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

In `StudyViewModelTest.kt`, add these imports if missing:

```kotlin
import kotlinx.coroutines.flow.first
import nart.simpleanki.core.data.repository.FakeReviewLogDao
import nart.simpleanki.core.data.repository.ReviewLogRepository
```

Add this test method inside the class (model its setup on the existing rate-a-card tests — e.g. the one around line 105 that uses `val dao = FakeCardDao()`; it studies deck `"d1"`, flips, and rates). Use whatever the file's helpers are named for adding a card to a deck (e.g. `newCard("c1", deckId = "d1")` / `cardRepo.upsert(...)`):

```kotlin
    @Test
    fun rating_appendsOneReviewLog_withCardIdAndRating() = runTest {
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        cardRepo.upsert(newCard("c1", deckId = "d1"))
        val logRepo = ReviewLogRepository(FakeReviewLogDao(), newId = { "log-1" })

        val vm = StudyViewModel(
            "d1", null, cardRepo, DeckRepository(FakeDeckDao(), now = { now }),
            FakeSettingsRepository(), logRepo, now = { now },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.onReveal()
        vm.onRate(Rating.Good)
        runCurrent()

        val logs = logRepo.observeLogs().first()
        assertEquals(1, logs.size)
        assertEquals("c1", logs[0].cardId)
        assertEquals(Rating.Good, logs[0].rating)
        assertEquals(now, logs[0].review)
    }
```

(If the existing tests don't keep a collector / use `runCurrent`, match whatever idiom they use; the key assertions are: exactly one log, `cardId == "c1"`, `rating == Good`.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.study.StudyViewModelTest"`
Expected: FAIL to compile — `StudyViewModel` has no `reviewLogRepository` parameter (the 6th positional arg in the new test doesn't exist yet).

- [ ] **Step 3a: Add the dependency + append call to `StudyViewModel`**

In `StudyViewModel.kt`, add the import `import nart.simpleanki.core.data.repository.ReviewLogRepository`. Add the constructor parameter **after `settingsRepository` and before `now`**:

```kotlin
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
    private val settingsRepository: SettingsRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val logManager: LogManager = LogManager(emptyList()),
```

In `onRate`, right after the existing `viewModelScope.launch { cardRepository.save(result.card) }`, add:

```kotlin
        viewModelScope.launch { reviewLogRepository.append(card.id, result.log) }
```

- [ ] **Step 3b: Update the Koin registration**

In `AppModule.kt`, in the `StudyViewModel` `viewModel { params -> ... }` block, add `reviewLogRepository = get(),` (after `settingsRepository = get(),`):

```kotlin
    viewModel { params ->
        val args = params.get<StudyArgs>()
        StudyViewModel(
            deckId = args.deckId,
            folderId = args.folderId,
            cardRepository = get(),
            deckRepository = get(),
            settingsRepository = get(),
            reviewLogRepository = get(),
            logManager = get(),
        )
    }
```

- [ ] **Step 3c: Fix the other `StudyViewModel(...)` constructions in the test file**

Every other `StudyViewModel(...)` call in `StudyViewModelTest.kt` now needs the new 6th positional argument. For each existing call of the form
`StudyViewModel("d1", null, repo, DeckRepository(FakeDeckDao(), now = { now }), FakeSettingsRepository(), now = { now })`,
insert `ReviewLogRepository(FakeReviewLogDao()), ` before `now = { now }`:
`StudyViewModel("d1", null, repo, DeckRepository(FakeDeckDao(), now = { now }), FakeSettingsRepository(), ReviewLogRepository(FakeReviewLogDao()), now = { now })`.
Do this for all such calls (there are several — search the file for `StudyViewModel(`). The settings-variant call that passes `settings` instead of `FakeSettingsRepository()` gets the same insertion before `now = { now }`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.study.StudyViewModelTest"`
Expected: PASS (the new test + all pre-existing tests in the class).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/study/StudyViewModel.kt app/src/main/java/nart/simpleanki/di/AppModule.kt app/src/test/java/nart/simpleanki/feature/study/StudyViewModelTest.kt
git commit -m "Persist a review log on each rating in StudyViewModel"
```

---

### Task 5: Sync review logs to Firestore (append-only union)

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/data/sync/RemoteSyncSource.kt`
- Modify: `app/src/main/java/nart/simpleanki/core/data/sync/FirestoreSyncService.kt`
- Modify: `app/src/main/java/nart/simpleanki/core/data/sync/SyncManager.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Test: `app/src/test/java/nart/simpleanki/core/data/sync/SyncManagerTest.kt`

- [ ] **Step 1: Write the failing test**

In `SyncManagerTest.kt`:

1a. Add the new methods + capture fields to the inline `FakeRemote` class (it currently implements `fetch/push` for folders/decks/cards). Add an import `import nart.simpleanki.core.data.firestore.ReviewLogDto` at the top, then inside `FakeRemote` add a constructor field `var reviewLogs: MutableList<ReviewLogDto> = mutableListOf(),` and:

```kotlin
        val pushedReviewLogs = mutableListOf<ReviewLogDto>()
        override suspend fun fetchReviewLogs(uid: String) = reviewLogs
        override suspend fun pushReviewLogs(uid: String, dtos: List<ReviewLogDto>) { pushedReviewLogs += dtos }
```

1b. Add `import nart.simpleanki.core.data.repository.FakeReviewLogDao` and add a `FakeReviewLogDao()` argument to **every** `SyncManager(...)` construction in the file (the new 4th positional arg, after the card DAO): e.g. `SyncManager(folderDao, FakeDeckDao(), FakeCardDao(), FakeReviewLogDao(), remote, m)`.

1c. Add these two tests:

```kotlin
    @Test
    fun reviewLogs_pushDirty_thenClearDirty() = runTest {
        val logDao = FakeReviewLogDao()
        logDao.insertAll(listOf(reviewLogEntity("l1", dirty = true)))
        val remote = FakeRemote()
        val (m, _) = media()
        val sync = SyncManager(FakeFolderDao(), FakeDeckDao(), FakeCardDao(), logDao, remote, m)

        sync.sync("u1")

        assertEquals(listOf("l1"), remote.pushedReviewLogs.map { it.id })
        assertTrue(logDao.getDirty().isEmpty())   // cleared after push
    }

    @Test
    fun reviewLogs_pullUnionsRemote_andSkipsExisting() = runTest {
        val logDao = FakeReviewLogDao()
        logDao.insertAll(listOf(reviewLogEntity("l1", dirty = false)))   // already local
        val remote = FakeRemote(reviewLogs = mutableListOf(
            ReviewLogDto.fromDomain(reviewLogEntity("l1", dirty = false).toDomain()),  // dup → ignored
            ReviewLogDto.fromDomain(reviewLogEntity("l2", dirty = false).toDomain()),  // new → inserted
        ))
        val (m, _) = media()
        val sync = SyncManager(FakeFolderDao(), FakeDeckDao(), FakeCardDao(), logDao, remote, m)

        sync.sync("u1")

        assertEquals(setOf("l1", "l2"), logDao.getAllIds().toSet())
    }
```

Add a helper near the other entity helpers in the file:

```kotlin
    private fun reviewLogEntity(id: String, dirty: Boolean) = nart.simpleanki.core.data.local.ReviewLogEntity(
        id = id, cardId = "c1", rating = 3, state = 2, due = 0, stability = 1.0, difficulty = 5.0,
        elapsedDays = 0.0, lastElapsedDays = 0.0, scheduledDays = 0.0, review = 1_000, dirty = dirty,
    )
```

Add `import nart.simpleanki.core.data.local.toDomain` (for `reviewLogEntity(...).toDomain()` used above) if not already present.

- [ ] **Step 2: Run the test to verify it fails (does not compile)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.data.sync.SyncManagerTest"`
Expected: FAIL — `RemoteSyncSource` has no `fetchReviewLogs`/`pushReviewLogs`; `SyncManager` constructor has no review-log DAO param.

- [ ] **Step 3a: Extend `RemoteSyncSource`**

In `RemoteSyncSource.kt`, add `import nart.simpleanki.core.data.firestore.ReviewLogDto` and two methods to the interface:

```kotlin
    suspend fun fetchReviewLogs(uid: String): List<ReviewLogDto>
    suspend fun pushReviewLogs(uid: String, dtos: List<ReviewLogDto>)
```

- [ ] **Step 3b: Implement in `FirestoreSyncService`**

In `FirestoreSyncService.kt`, add `import nart.simpleanki.core.data.firestore.ReviewLogDto`, update the class doc comment's collection list to include `reviewLogs`, and add (next to the `cards` overrides):

```kotlin
    override suspend fun fetchReviewLogs(uid: String): List<ReviewLogDto> =
        col(uid, "reviewLogs").get().await().toObjects(ReviewLogDto::class.java)

    override suspend fun pushReviewLogs(uid: String, dtos: List<ReviewLogDto>) =
        push(uid, "reviewLogs", dtos) { it.id }
```

- [ ] **Step 3c: Extend `SyncManager`**

In `SyncManager.kt`, add imports `import nart.simpleanki.core.data.firestore.ReviewLogDto` and `import nart.simpleanki.core.data.local.dao.ReviewLogDao` (`toDomain`/`toEntity` are already imported). Add the constructor parameter after `cardDao`:

```kotlin
    private val cardDao: CardDao,
    private val reviewLogDao: ReviewLogDao,
    private val remote: RemoteSyncSource,
    private val media: MediaManager,
```

At the END of `push(uid)`, append:

```kotlin
        reviewLogDao.getDirty().takeIf { it.isNotEmpty() }?.let { rows ->
            remote.pushReviewLogs(uid, rows.map { ReviewLogDto.fromDomain(it.toDomain()) })
            rows.forEach { reviewLogDao.clearDirty(it.id) }
        }
```

At the END of `pull(uid)`, append (union by id — logs are immutable, so no last-write-wins):

```kotlin
        val localLogIds = reviewLogDao.getAllIds().toSet()
        remote.fetchReviewLogs(uid)
            .filter { it.id.isNotEmpty() && it.id !in localLogIds }
            .map { it.toDomain().toEntity(dirty = false) }
            .takeIf { it.isNotEmpty() }
            ?.let { reviewLogDao.insertAll(it) }
```

- [ ] **Step 3d: Update the Koin `SyncManager` registration**

In `AppModule.kt`, the registration is `single { SyncManager(get(), get(), get(), get(), get()) }`. Add one more `get()` (for the review-log DAO, the new 4th argument):

```kotlin
    single { SyncManager(get(), get(), get(), get(), get(), get()) }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.data.sync.SyncManagerTest" :app:compileDebugKotlin`
Expected: PASS (new + pre-existing sync tests) + BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/data/sync/ app/src/main/java/nart/simpleanki/di/AppModule.kt app/src/test/java/nart/simpleanki/core/data/sync/SyncManagerTest.kt
git commit -m "Sync review logs to Firestore with append-only union"
```

---

## Final verification

- [ ] **Run the full app unit-test suite (no regressions)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Compile instrumented test sources + build the debug APK**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugAndroidTestKotlin :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Manual smoke (when an emulator is available — verifies the real Room migration)**

- Install over an existing v1 install (don't uninstall) → app opens without a Room schema crash (the additive migration ran; data preserved).
- Rate several cards in a study session → no crash; (if inspecting the DB) `review_logs` has one row per rating with the right `cardId`/`rating`/`review`.
- With a signed-in account, trigger a sync → `users/{uid}/reviewLogs` fills with the rated logs; on a second device, sync pulls them in (no duplicates on repeat syncs).
