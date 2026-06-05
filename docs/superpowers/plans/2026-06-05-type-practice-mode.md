# Type Practice Mode (Phase 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a standalone, FSRS-decoupled "Type Practice" study mode (whole deck, retry-until-correct, typed answers auto-checked) with its own append-only typing-log store, a per-deck mastery ring, and an end-of-session report.

**Architecture:** Pure domain units (`AnswerMatcher`, `TypePracticeSession`, `TypingMastery`) hold all logic and are unit-tested without Android. A new append-only `TypingLog` store (Room `typing_logs` table + Firestore sync) mirrors the existing review-log path exactly. `TypePracticeViewModel` drives the pure session, appends exactly one log per card at first-attempt finalization, and never touches FSRS. The deck-detail screen gains a Type Practice entry + a mastery ring derived live from the logs (same pure-derivation style as `StreakProvider`).

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Koin, Room, Firestore, kotlinx-coroutines, JUnit4 + coroutines-test.

**Branch:** `feature/type-practice-mode` (off `main`).

**Build/test prefix:** ALL Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&` and run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

**Commit rules:** No "claude" mention in commit messages; no Co-Authored-By / attribution trailer. NEVER `git add -A` or `git add .` — always add explicit paths, so the untracked `docs/superpowers/plans/2026-06-04-realtime-study-queue.md` is never staged.

---

## File Structure

**New files:**
- `app/src/main/java/nart/simpleanki/core/domain/typing/AnswerMatcher.kt` — normalize + compare typed answers (pure).
- `app/src/main/java/nart/simpleanki/core/domain/typing/TypePracticeSession.kt` — in-memory retry-until-correct session state machine (pure).
- `app/src/main/java/nart/simpleanki/core/domain/typing/TypingMastery.kt` — mastery derivation from logs (pure) + `DeckMastery`.
- `app/src/main/java/nart/simpleanki/core/data/repository/TypingMasteryProvider.kt` — `Flow<DeckMastery>` for a deck.
- `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeViewModel.kt` — session driver + Ui state.
- `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt` — Compose UI + session report + previews.
- Tests: `AnswerMatcherTest.kt`, `TypePracticeSessionTest.kt`, `TypingMasteryTest.kt`, `TypingLogMapperTest.kt`, `TypingLogDtoTest.kt`, `TypingLogRepositoryTest.kt`, `TypingMasteryProviderTest.kt`, `TypePracticeViewModelTest.kt`.

**Modified files:**
- `core/domain/model/DomainModels.kt` — add `TypingLog`.
- `core/data/local/RoomEntities.kt` — add `TypingLogEntity`.
- `core/data/local/dao/Daos.kt` — add `TypingLogDao`.
- `core/data/local/RoomMappers.kt` — add `TypingLog` mappers.
- `core/data/local/AzriDatabase.kt` — version 4 + `typingLogDao()` + `MIGRATION_3_4`.
- `core/data/repository/Repositories.kt` — add `TypingLogRepository`.
- `core/data/firestore/FirestoreDtos.kt` — add `TypingLogDto`.
- `core/data/sync/RemoteSyncSource.kt` + `FirestoreSyncService.kt` + `SyncManager.kt` — typing-log fetch/push + union-pull.
- `di/AppModule.kt` — dao/repo/provider/VM registrations, `SyncManager` arg, `MIGRATION_3_4`.
- `feature/deckdetail/DeckDetailViewModel.kt` + `DeckDetailScreen.kt` — mastery flow + Type Practice button/ring.
- `ui/navigation/AzriNavHost.kt` — `typePractice/{deckId}` route + deck-detail wiring.
- `app/src/test/java/nart/simpleanki/core/data/repository/FakeDaos.kt` — `FakeTypingLogDao`.
- `app/src/test/java/nart/simpleanki/core/data/sync/SyncManagerTest.kt` — thread the new DAO + 2 typing-log tests.

---

## Task 1: `AnswerMatcher` (pure answer comparison)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/domain/typing/AnswerMatcher.kt`
- Test: `app/src/test/java/nart/simpleanki/core/domain/typing/AnswerMatcherTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/nart/simpleanki/core/domain/typing/AnswerMatcherTest.kt`:
```kotlin
package nart.simpleanki.core.domain.typing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMatcherTest {
    @Test fun exactMatch() = assertTrue(AnswerMatcher.matches("hello", "hello"))

    @Test fun caseInsensitive() = assertTrue(AnswerMatcher.matches("Hello", "hELLO"))

    @Test fun trimsAndCollapsesWhitespace() =
        assertTrue(AnswerMatcher.matches("  how   are  you ", "how are you"))

    @Test fun ignoresSurroundingPunctuation() {
        assertTrue(AnswerMatcher.matches("hello!", "hello"))
        assertTrue(AnswerMatcher.matches("¿cómo estás?", "cómo estás"))
    }

    @Test fun accentsAreEnforced() {
        assertFalse(AnswerMatcher.matches("cafe", "café"))
        assertTrue(AnswerMatcher.matches("café", "café"))
    }

    @Test fun blankNeverMatchesNonBlank() {
        assertFalse(AnswerMatcher.matches("", "hello"))
        assertFalse(AnswerMatcher.matches("   ", "hello"))
    }

    @Test fun wrongIsWrong() = assertFalse(AnswerMatcher.matches("hola", "hello"))
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.typing.AnswerMatcherTest"`
Expected: COMPILE FAILURE (`AnswerMatcher` does not exist).

- [ ] **Step 3: Implement `AnswerMatcher.kt`**

Create `app/src/main/java/nart/simpleanki/core/domain/typing/AnswerMatcher.kt`:
```kotlin
package nart.simpleanki.core.domain.typing

/**
 * Normalizes and compares typed answers for Type Practice. Case-insensitive, whitespace-insensitive,
 * and surrounding-punctuation-insensitive (leading/trailing Unicode punctuation is stripped — e.g.
 * "¿cómo estás?" -> "cómo estás"), but accent/diacritic-SENSITIVE ("café" != "cafe"). The objective
 * signal it produces is the basis for mastery + (Phase 2) diagnostics, so it stays strict on accents.
 */
object AnswerMatcher {
    // Leading/trailing Unicode punctuation (\p{P}) and whitespace.
    private val edges = Regex("^[\\p{P}\\s]+|[\\p{P}\\s]+$")
    private val innerWhitespace = Regex("\\s+")

    fun normalize(input: String): String =
        input.replace(edges, "").replace(innerWhitespace, " ").trim().lowercase()

    fun matches(typed: String, expected: String): Boolean {
        val n = normalize(expected)
        return n.isNotEmpty() && normalize(typed) == n
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.typing.AnswerMatcherTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/nart/simpleanki/core/domain/typing/AnswerMatcher.kt \
        app/src/test/java/nart/simpleanki/core/domain/typing/AnswerMatcherTest.kt
git commit -m "Add AnswerMatcher for typed-answer comparison"
```

---

## Task 2: `TypingLog` storage (domain, entity, DAO, mapper, DB v4)

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/domain/model/DomainModels.kt`
- Modify: `app/src/main/java/nart/simpleanki/core/data/local/RoomEntities.kt`
- Modify: `app/src/main/java/nart/simpleanki/core/data/local/dao/Daos.kt`
- Modify: `app/src/main/java/nart/simpleanki/core/data/local/RoomMappers.kt`
- Modify: `app/src/main/java/nart/simpleanki/core/data/local/AzriDatabase.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Test: `app/src/test/java/nart/simpleanki/core/data/local/TypingLogMapperTest.kt`

- [ ] **Step 1: Add the `TypingLog` domain model**

In `DomainModels.kt`, immediately after the `ReviewLog` data class (ends at the line with `val cardId: String = "",` then `)`), add:
```kotlin

/** One Type-Practice first-attempt outcome for a card (append-only; decoupled from FSRS). */
data class TypingLog(
    val id: String = "",
    val cardId: String = "",
    val deckId: String = "",
    val correct: Boolean,
    val typedText: String,
    val timestamp: Long,
)
```

- [ ] **Step 2: Add the Room entity**

In `RoomEntities.kt`, after the `ReviewLogEntity` data class, add:
```kotlin

@Entity(
    tableName = "typing_logs",
    indices = [Index("cardId"), Index("deckId")],
)
data class TypingLogEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val deckId: String,
    val correct: Boolean,
    val typedText: String,
    val timestamp: Long,
    val dirty: Boolean = true,
)
```

- [ ] **Step 3: Add the DAO**

In `dao/Daos.kt`, add the import alongside the other entity imports at the top:
```kotlin
import nart.simpleanki.core.data.local.TypingLogEntity
```
Then add this DAO after `ReviewLogDao` (mirrors it; append-only, IGNORE for idempotent append + pull-union):
```kotlin

@Dao
interface TypingLogDao {
    // IGNORE makes both append (fresh UUID) and pull (union) idempotent: an existing id is a no-op.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(logs: List<TypingLogEntity>)

    @Query("SELECT * FROM typing_logs WHERE dirty = 1")
    suspend fun getDirty(): List<TypingLogEntity>

    @Query("UPDATE typing_logs SET dirty = 0 WHERE id = :id")
    suspend fun clearDirty(id: String)

    @Query("SELECT id FROM typing_logs")
    suspend fun getAllIds(): List<String>

    @Query("SELECT * FROM typing_logs ORDER BY timestamp")
    fun observeAll(): Flow<List<TypingLogEntity>>

    @Query("SELECT * FROM typing_logs WHERE deckId = :deckId ORDER BY timestamp")
    fun observeForDeck(deckId: String): Flow<List<TypingLogEntity>>
}
```

- [ ] **Step 4: Add the mappers**

In `RoomMappers.kt`, add the import next to the other domain imports:
```kotlin
import nart.simpleanki.core.domain.model.TypingLog
```
Then append after the `ReviewLog.toEntity` mapper:
```kotlin

fun TypingLogEntity.toDomain(): TypingLog = TypingLog(
    id = id, cardId = cardId, deckId = deckId, correct = correct, typedText = typedText, timestamp = timestamp,
)

fun TypingLog.toEntity(dirty: Boolean = true): TypingLogEntity = TypingLogEntity(
    id = id, cardId = cardId, deckId = deckId, correct = correct, typedText = typedText, timestamp = timestamp, dirty = dirty,
)
```

- [ ] **Step 5: Bump the database version + add the migration**

In `AzriDatabase.kt`:
1. Add the import after the `StreakStateDao` import:
```kotlin
import nart.simpleanki.core.data.local.dao.TypingLogDao
```
2. Change the `@Database(...)` annotation's `entities` and `version`:
```kotlin
@Database(
    entities = [CardEntity::class, DeckEntity::class, FolderEntity::class, ReviewLogEntity::class, StreakStateEntity::class, TypingLogEntity::class],
    version = 4,
    exportSchema = false,
)
```
3. Add the abstract DAO accessor after `streakStateDao()`:
```kotlin
    abstract fun typingLogDao(): TypingLogDao
```
4. Add the migration after `MIGRATION_2_3` (CREATE TABLE + 2 indices; **no SQL `DEFAULT`** — the entity has no `@ColumnInfo` default, so a SQL default would be a schema mismatch):
```kotlin

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `typing_logs` (" +
                "`id` TEXT NOT NULL, `cardId` TEXT NOT NULL, `deckId` TEXT NOT NULL, " +
                "`correct` INTEGER NOT NULL, `typedText` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                "`dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_typing_logs_cardId` ON `typing_logs` (`cardId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_typing_logs_deckId` ON `typing_logs` (`deckId`)")
    }
}
```

- [ ] **Step 6: Wire the DAO + migration into Koin**

In `di/AppModule.kt`:
1. Add the migration import next to `MIGRATION_2_3`:
```kotlin
import nart.simpleanki.core.data.local.MIGRATION_3_4
```
2. In the `Room.databaseBuilder(...)` block, extend the migrations call:
```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
```
3. After `single { get<AzriDatabase>().streakStateDao() }`, add:
```kotlin
    single { get<AzriDatabase>().typingLogDao() }
```

- [ ] **Step 7: Write the mapper test**

Create `app/src/test/java/nart/simpleanki/core/data/local/TypingLogMapperTest.kt`:
```kotlin
package nart.simpleanki.core.data.local

import nart.simpleanki.core.domain.model.TypingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingLogMapperTest {
    @Test fun roundTrip_preservesFields() {
        val domain = TypingLog(
            id = "t1", cardId = "c1", deckId = "d1", correct = true, typedText = "café", timestamp = 1_700L,
        )
        val back = domain.toEntity(dirty = false).toDomain()
        assertEquals(domain, back)
    }

    @Test fun toEntity_defaultsDirtyTrue() {
        val e = TypingLog(id = "t1", cardId = "c1", deckId = "d1", correct = false, typedText = "x", timestamp = 1L).toEntity()
        assertTrue(e.dirty)
    }
}
```

- [ ] **Step 8: Build + run the mapper test**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "nart.simpleanki.core.data.local.TypingLogMapperTest"`
Expected: BUILD SUCCESSFUL; 2 tests pass.

- [ ] **Step 9: Verify the migration SQL matches Room's generated schema (the footgun)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Then confirm the generated `typing_logs` CREATE matches the migration char-for-char:
```bash
grep -rn "CREATE TABLE IF NOT EXISTS \`typing_logs\`" app/build/generated/ksp/ 2>/dev/null || \
grep -rn "typing_logs" app/build/generated/ 2>/dev/null | grep "CREATE TABLE"
```
Expected: the generated `createAllTables` string for `typing_logs` is byte-identical to the `MIGRATION_3_4` string (same column order, `INTEGER`/`TEXT` types, `NOT NULL`, `PRIMARY KEY(\`id\`)`, no DEFAULT). If they differ, edit `MIGRATION_3_4` to match the generated one exactly.

- [ ] **Step 10: Commit**
```bash
git add app/src/main/java/nart/simpleanki/core/domain/model/DomainModels.kt \
        app/src/main/java/nart/simpleanki/core/data/local/RoomEntities.kt \
        app/src/main/java/nart/simpleanki/core/data/local/dao/Daos.kt \
        app/src/main/java/nart/simpleanki/core/data/local/RoomMappers.kt \
        app/src/main/java/nart/simpleanki/core/data/local/AzriDatabase.kt \
        app/src/main/java/nart/simpleanki/di/AppModule.kt \
        app/src/test/java/nart/simpleanki/core/data/local/TypingLogMapperTest.kt
git commit -m "Add typing_logs table, entity, DAO, mappers, and v4 migration"
```

---

## Task 3: `TypingMastery` (pure mastery derivation)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/domain/typing/TypingMastery.kt`
- Test: `app/src/test/java/nart/simpleanki/core/domain/typing/TypingMasteryTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/nart/simpleanki/core/domain/typing/TypingMasteryTest.kt`:
```kotlin
package nart.simpleanki.core.domain.typing

import nart.simpleanki.core.domain.model.TypingLog
import org.junit.Assert.assertEquals
import org.junit.Test

class TypingMasteryTest {
    private fun log(card: String, correct: Boolean, ts: Long, deck: String = "d") =
        TypingLog(id = "$card-$ts", cardId = card, deckId = deck, correct = correct, typedText = "", timestamp = ts)

    @Test fun latestFirstTryWins_masteryRegresses() {
        // c1: correct then later wrong -> NOT mastered. c2: wrong then later correct -> mastered.
        val logs = listOf(
            log("c1", correct = true, ts = 1),
            log("c1", correct = false, ts = 2),
            log("c2", correct = false, ts = 1),
            log("c2", correct = true, ts = 2),
        )
        assertEquals(setOf("c2"), TypingMastery.masteredCardIds(logs))
    }

    @Test fun deckMastery_countsAgainstCurrentDeckCards() {
        val logs = listOf(log("c1", true, 1), log("c2", true, 1), log("gone", true, 1))
        // "gone" has a log but is no longer in the deck -> excluded; c3 is in the deck but never typed.
        val m = TypingMastery.deckMastery(logs, deckCardIds = setOf("c1", "c2", "c3"))
        assertEquals(DeckMastery(mastered = 2, total = 3), m)
    }

    @Test fun emptyLogs_zeroMastered() {
        assertEquals(DeckMastery(0, 2), TypingMastery.deckMastery(emptyList(), setOf("c1", "c2")))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.typing.TypingMasteryTest"`
Expected: COMPILE FAILURE (`TypingMastery` / `DeckMastery` do not exist).

- [ ] **Step 3: Implement `TypingMastery.kt`**

Create `app/src/main/java/nart/simpleanki/core/domain/typing/TypingMastery.kt`:
```kotlin
package nart.simpleanki.core.domain.typing

import nart.simpleanki.core.domain.model.TypingLog

/** Mastered/total typed cards for one deck. */
data class DeckMastery(val mastered: Int, val total: Int)

/**
 * Derives typing mastery purely from logs (single source of truth, like StreakProvider over review
 * logs). A card is "mastered" iff its LATEST first-attempt log is correct, so mastery regresses
 * honestly when a later session misses it.
 */
object TypingMastery {
    fun latestPerCard(logs: List<TypingLog>): Map<String, TypingLog> =
        logs.groupBy { it.cardId }.mapValues { (_, group) -> group.maxBy { it.timestamp } }

    fun masteredCardIds(logs: List<TypingLog>): Set<String> =
        latestPerCard(logs).filterValues { it.correct }.keys

    fun deckMastery(logs: List<TypingLog>, deckCardIds: Set<String>): DeckMastery =
        DeckMastery(
            mastered = masteredCardIds(logs).count { it in deckCardIds },
            total = deckCardIds.size,
        )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.typing.TypingMasteryTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/nart/simpleanki/core/domain/typing/TypingMastery.kt \
        app/src/test/java/nart/simpleanki/core/domain/typing/TypingMasteryTest.kt
git commit -m "Add TypingMastery derivation from typing logs"
```

---

## Task 4: `TypingLogRepository` + `TypingMasteryProvider`

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/data/repository/Repositories.kt`
- Create: `app/src/main/java/nart/simpleanki/core/data/repository/TypingMasteryProvider.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Modify: `app/src/test/java/nart/simpleanki/core/data/repository/FakeDaos.kt`
- Test: `app/src/test/java/nart/simpleanki/core/data/repository/TypingLogRepositoryTest.kt`
- Test: `app/src/test/java/nart/simpleanki/core/data/repository/TypingMasteryProviderTest.kt`

- [ ] **Step 1: Add `FakeTypingLogDao` to `FakeDaos.kt`**

In `FakeDaos.kt`, add the import next to the other entity imports:
```kotlin
import nart.simpleanki.core.data.local.TypingLogEntity
import nart.simpleanki.core.data.local.dao.TypingLogDao
```
Then append this fake after `FakeReviewLogDao` (mirrors it; adds `observeForDeck` + an `inserted` capture list):
```kotlin

class FakeTypingLogDao : TypingLogDao {
    private val store = MutableStateFlow<Map<String, TypingLogEntity>>(emptyMap())
    val inserted = mutableListOf<TypingLogEntity>()
    override suspend fun insertAll(logs: List<TypingLogEntity>) {
        inserted += logs
        store.value = store.value.toMutableMap().apply { logs.forEach { putIfAbsent(it.id, it) } }
    }
    override suspend fun getDirty(): List<TypingLogEntity> = store.value.values.filter { it.dirty }
    override suspend fun clearDirty(id: String) {
        store.value[id]?.let { store.value = store.value.toMutableMap().apply { put(id, it.copy(dirty = false)) } }
    }
    override suspend fun getAllIds(): List<String> = store.value.keys.toList()
    override fun observeAll(): Flow<List<TypingLogEntity>> =
        store.map { m -> m.values.sortedBy { it.timestamp } }
    override fun observeForDeck(deckId: String): Flow<List<TypingLogEntity>> =
        store.map { m -> m.values.filter { it.deckId == deckId }.sortedBy { it.timestamp } }
}
```

- [ ] **Step 2: Write the repository + provider tests**

Create `app/src/test/java/nart/simpleanki/core/data/repository/TypingLogRepositoryTest.kt`:
```kotlin
package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.domain.model.TypingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingLogRepositoryTest {
    private fun log(card: String, deck: String, correct: Boolean) =
        TypingLog(cardId = card, deckId = deck, correct = correct, typedText = "x", timestamp = 1L)

    @Test fun append_assignsId_marksDirty_andObservable() = runTest {
        val dao = FakeTypingLogDao()
        val repo = TypingLogRepository(dao, newId = { "fixed-id" })
        repo.append(log("c1", "d1", correct = true))

        val all = repo.observeLogs().first()
        assertEquals(1, all.size)
        assertEquals("fixed-id", all.first().id)
        assertTrue(dao.getDirty().isNotEmpty())
    }

    @Test fun observeLogsForDeck_filtersByDeck() = runTest {
        val repo = TypingLogRepository(FakeTypingLogDao(), newId = { java.util.UUID.randomUUID().toString() })
        repo.append(log("c1", "d1", true))
        repo.append(log("c2", "d2", true))
        assertEquals(listOf("c1"), repo.observeLogsForDeck("d1").first().map { it.cardId })
    }
}
```

Create `app/src/test/java/nart/simpleanki/core/data/repository/TypingMasteryProviderTest.kt`:
```kotlin
package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.TypingLog
import nart.simpleanki.core.domain.typing.DeckMastery
import org.junit.Assert.assertEquals
import org.junit.Test

class TypingMasteryProviderTest {
    private val now = 1_700_000_000_000L
    private fun card(id: String, deck: String, back: String = "b") = Card(
        id = id, front = "f", back = back, deckId = deck,
        dateCreated = now, lastModified = now, fsrsDue = now, fsrsState = CardState.New.value,
    )

    @Test fun deckMastery_excludesBlankBackCards_andCountsLatestCorrect() = runTest {
        val cardDao = FakeCardDao()
        val cardRepo = CardRepository(cardDao, now = { now })
        cardRepo.upsert(card("c1", "d1"))
        cardRepo.upsert(card("c2", "d1"))
        cardRepo.upsert(card("c3", "d1", back = "   "))   // blank back -> not typeable, excluded from total
        val logDao = FakeTypingLogDao()
        val logRepo = TypingLogRepository(logDao, newId = { java.util.UUID.randomUUID().toString() })
        logRepo.append(TypingLog(cardId = "c1", deckId = "d1", correct = true, typedText = "x", timestamp = 1))

        val provider = TypingMasteryProvider(logRepo, cardRepo)
        assertEquals(DeckMastery(mastered = 1, total = 2), provider.observeDeckMastery("d1").first())
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.data.repository.TypingLogRepositoryTest" --tests "nart.simpleanki.core.data.repository.TypingMasteryProviderTest"`
Expected: COMPILE FAILURE (`TypingLogRepository` / `TypingMasteryProvider` do not exist).

- [ ] **Step 4: Implement `TypingLogRepository`**

In `Repositories.kt`, add the import:
```kotlin
import nart.simpleanki.core.domain.model.TypingLog
```
and the DAO import next to `ReviewLogDao`:
```kotlin
import nart.simpleanki.core.data.local.dao.TypingLogDao
```
Then add this class after `ReviewLogRepository`:
```kotlin

/** Immutable, append-only store of Type-Practice first-attempt outcomes (decoupled from FSRS). */
class TypingLogRepository(
    private val dao: TypingLogDao,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** Appends one typing outcome, assigning a fresh id when none is set; marked dirty for sync. */
    suspend fun append(log: TypingLog) {
        dao.insertAll(listOf(log.copy(id = log.id.ifEmpty { newId() }).toEntity(dirty = true)))
    }

    fun observeLogs(): Flow<List<TypingLog>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeLogsForDeck(deckId: String): Flow<List<TypingLog>> =
        dao.observeForDeck(deckId).map { rows -> rows.map { it.toDomain() } }
}
```

- [ ] **Step 5: Implement `TypingMasteryProvider`**

Create `app/src/main/java/nart/simpleanki/core/data/repository/TypingMasteryProvider.kt`:
```kotlin
package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import nart.simpleanki.core.domain.typing.DeckMastery
import nart.simpleanki.core.domain.typing.TypingMastery

/**
 * Live per-deck typing mastery for the deck-detail ring. Derives from logs + the deck's current
 * (non-deleted, typeable) cards — blank-back cards are excluded, matching what Type Practice studies.
 */
class TypingMasteryProvider(
    private val typingLogRepository: TypingLogRepository,
    private val cardRepository: CardRepository,
) {
    fun observeDeckMastery(deckId: String): Flow<DeckMastery> =
        combine(
            typingLogRepository.observeLogsForDeck(deckId),
            cardRepository.observeCards(deckId),
        ) { logs, cards ->
            val typeable = cards.filter { it.back.isNotBlank() }.map { it.id }.toSet()
            TypingMastery.deckMastery(logs, typeable)
        }
}
```

- [ ] **Step 6: Register both in Koin**

In `di/AppModule.kt`, add imports next to the other repository imports:
```kotlin
import nart.simpleanki.core.data.repository.TypingLogRepository
import nart.simpleanki.core.data.repository.TypingMasteryProvider
```
Then, after `single { ReviewLogRepository(get()) }`, add:
```kotlin
    single { TypingLogRepository(get()) }
    single { TypingMasteryProvider(get(), get()) }
```

- [ ] **Step 7: Run the tests**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.data.repository.TypingLogRepositoryTest" --tests "nart.simpleanki.core.data.repository.TypingMasteryProviderTest"`
Expected: PASS (3 tests).

- [ ] **Step 8: Commit**
```bash
git add app/src/main/java/nart/simpleanki/core/data/repository/Repositories.kt \
        app/src/main/java/nart/simpleanki/core/data/repository/TypingMasteryProvider.kt \
        app/src/main/java/nart/simpleanki/di/AppModule.kt \
        app/src/test/java/nart/simpleanki/core/data/repository/FakeDaos.kt \
        app/src/test/java/nart/simpleanki/core/data/repository/TypingLogRepositoryTest.kt \
        app/src/test/java/nart/simpleanki/core/data/repository/TypingMasteryProviderTest.kt
git commit -m "Add TypingLogRepository and TypingMasteryProvider"
```

---

## Task 5: `TypePracticeSession` (pure retry-until-correct state machine)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/domain/typing/TypePracticeSession.kt`
- Test: `app/src/test/java/nart/simpleanki/core/domain/typing/TypePracticeSessionTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/nart/simpleanki/core/domain/typing/TypePracticeSessionTest.kt`:
```kotlin
package nart.simpleanki.core.domain.typing

import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypePracticeSessionTest {
    private fun card(id: String, back: String) = Card(
        id = id, front = "f-$id", back = back, deckId = "d",
        dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
    )

    private class Recorder {
        data class Entry(val cardId: String, val correct: Boolean, val typed: String)
        val entries = mutableListOf<Entry>()
        val sink: (Card, Boolean, String) -> Unit = { c, ok, t -> entries += Entry(c.id, ok, t) }
    }

    @Test fun correctFirstTry_finalizesCorrect_advances_andCombos() {
        val rec = Recorder()
        val s = TypePracticeSession(listOf(card("c1", "a"), card("c2", "b")), emptySet(), rec.sink)
        assertEquals("c1", s.current!!.id)
        assertEquals(SubmitResult.Correct, s.submit("a"))
        assertEquals("c2", s.current!!.id)
        assertEquals(SubmitResult.Correct, s.submit("b"))
        assertTrue(s.isFinished)
        assertEquals(listOf(Recorder.Entry("c1", true, "a"), Recorder.Entry("c2", true, "b")), rec.entries)
        assertEquals(2, s.report().bestCombo)
        assertEquals(100, s.report().firstTryAccuracy)
        assertEquals(2, s.report().newlyMastered)
    }

    @Test fun wrongFirstTry_thenContinue_finalizesWrong_andRequeues() {
        val rec = Recorder()
        val s = TypePracticeSession(listOf(card("c1", "a"), card("c2", "b")), emptySet(), rec.sink)
        assertEquals(SubmitResult.Wrong("a"), s.submit("zzz"))
        assertTrue(s.isRevealing)
        assertTrue(s.canOverride)
        s.continueAfterWrong()
        // c1 finalized wrong (one log) and requeued behind c2.
        assertEquals(Recorder.Entry("c1", false, "zzz"), rec.entries.single())
        assertEquals("c2", s.current!!.id)
        assertFalse(s.isRevealing)
        // Clear c2, then c1 comes back; typing it right now clears it WITHOUT a new log.
        assertEquals(SubmitResult.Correct, s.submit("b"))
        assertEquals("c1", s.current!!.id)
        assertEquals(SubmitResult.Correct, s.submit("a"))
        assertTrue(s.isFinished)
        assertEquals(2, rec.entries.size)                         // exactly one log per card
        assertEquals(50, s.report().firstTryAccuracy)             // c1 wrong, c2 right
        assertEquals(0, s.report().bestCombo)                     // wrong reset the combo before any run
    }

    @Test fun override_marksCorrect_clears_andCountsNewlyMastered() {
        val rec = Recorder()
        val s = TypePracticeSession(listOf(card("c1", "a")), emptySet(), rec.sink)
        s.submit("close-but-wrong")
        assertTrue(s.canOverride)
        s.override()
        assertTrue(s.isFinished)
        assertEquals(Recorder.Entry("c1", true, "close-but-wrong"), rec.entries.single())
        assertEquals(1, s.report().newlyMastered)
    }

    @Test fun previouslyMastered_notRecountedAsNewly() {
        val rec = Recorder()
        val s = TypePracticeSession(listOf(card("c1", "a")), previouslyMastered = setOf("c1"), onFinalize = rec.sink)
        s.submit("a")
        assertEquals(0, s.report().newlyMastered)
    }

    @Test fun emptyPool_isFinished_zeroReport() {
        val s = TypePracticeSession(emptyList(), emptySet())
        assertTrue(s.isFinished)
        assertNull(s.current)
        assertEquals(SessionReport(0, 0, 0, 0, 0), s.report())
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.typing.TypePracticeSessionTest"`
Expected: COMPILE FAILURE (`TypePracticeSession` / `SubmitResult` / `SessionReport` do not exist).

- [ ] **Step 3: Implement `TypePracticeSession.kt`**

Create `app/src/main/java/nart/simpleanki/core/domain/typing/TypePracticeSession.kt`:
```kotlin
package nart.simpleanki.core.domain.typing

import nart.simpleanki.core.domain.model.Card

/** Result of submitting a typed answer. */
sealed interface SubmitResult {
    data object Correct : SubmitResult
    data class Wrong(val expected: String) : SubmitResult
}

/** End-of-session summary (first-try based; accuracy is a 0..100 percent). */
data class SessionReport(
    val completed: Int,
    val firstTryCorrect: Int,
    val firstTryAccuracy: Int,
    val bestCombo: Int,
    val newlyMastered: Int,
)

/**
 * In-memory state machine for one Type-Practice session — Android-free and unit-testable.
 *
 * Whole deck, retry-until-correct: a wrong FIRST attempt is revealed then requeued to later in the
 * session; the card's first-attempt correctness is what's scored. Each first-attempt outcome is
 * finalized exactly once (correct-on-first, or after [continueAfterWrong] / [override]) and emitted
 * to [onFinalize] so the caller persists exactly one log per card. Requeued retries clear the loop
 * but never re-score and never emit.
 */
class TypePracticeSession(
    pool: List<Card>,
    private val previouslyMastered: Set<String> = emptySet(),
    private val onFinalize: (card: Card, correct: Boolean, typed: String) -> Unit = { _, _, _ -> },
) {
    private val queue = ArrayDeque(pool)
    private val firstTry = LinkedHashMap<String, Boolean>()   // finalized first-try outcome per card
    private var combo = 0
    private var bestCombo = 0

    // Reveal state after a wrong submit, until continue/override.
    private var awaiting = false
    private var awaitingFirstAttempt = false
    private var awaitingTyped = ""

    val current: Card? get() = queue.firstOrNull()
    val remaining: Int get() = queue.size
    val isFinished: Boolean get() = queue.isEmpty()
    /** True while a wrong answer is revealed, awaiting Continue/override. */
    val isRevealing: Boolean get() = awaiting
    /** "I was right" is only offered on a first attempt. */
    val canOverride: Boolean get() = awaiting && awaitingFirstAttempt

    fun submit(answer: String): SubmitResult {
        val card = current ?: return SubmitResult.Correct
        if (awaiting) return SubmitResult.Wrong(card.back)        // UI gates this; be safe
        val firstAttempt = card.id !in firstTry
        return if (AnswerMatcher.matches(answer, card.back)) {
            if (firstAttempt) {
                firstTry[card.id] = true
                combo += 1
                if (combo > bestCombo) bestCombo = combo
                onFinalize(card, true, answer)
            }
            queue.removeFirst()
            SubmitResult.Correct
        } else {
            combo = 0
            awaiting = true
            awaitingFirstAttempt = firstAttempt
            awaitingTyped = answer
            SubmitResult.Wrong(card.back)
        }
    }

    /** Dismiss the reveal as wrong: finalize the first-try outcome (once) and requeue the card. */
    fun continueAfterWrong() {
        val card = current ?: return
        if (!awaiting) return
        if (awaitingFirstAttempt && card.id !in firstTry) {
            firstTry[card.id] = false
            onFinalize(card, false, awaitingTyped)
        }
        awaiting = false
        queue.removeFirst()
        queue.addLast(card)                                       // returns later this session
    }

    /** "I was right" — first attempts only: finalize correct and clear the card. */
    fun override() {
        val card = current ?: return
        if (!awaiting || !awaitingFirstAttempt) return
        if (card.id !in firstTry) {
            firstTry[card.id] = true
            onFinalize(card, true, awaitingTyped)
        }
        awaiting = false
        queue.removeFirst()
    }

    fun report(): SessionReport {
        val completed = firstTry.size
        val correct = firstTry.values.count { it }
        return SessionReport(
            completed = completed,
            firstTryCorrect = correct,
            firstTryAccuracy = if (completed == 0) 0 else correct * 100 / completed,
            bestCombo = bestCombo,
            newlyMastered = firstTry.filterValues { it }.keys.count { it !in previouslyMastered },
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.typing.TypePracticeSessionTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/nart/simpleanki/core/domain/typing/TypePracticeSession.kt \
        app/src/test/java/nart/simpleanki/core/domain/typing/TypePracticeSessionTest.kt
git commit -m "Add TypePracticeSession retry-until-correct state machine"
```

---

## Task 6: Firestore sync for typing logs

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/data/firestore/FirestoreDtos.kt`
- Modify: `app/src/main/java/nart/simpleanki/core/data/sync/RemoteSyncSource.kt`
- Modify: `app/src/main/java/nart/simpleanki/core/data/sync/FirestoreSyncService.kt`
- Modify: `app/src/main/java/nart/simpleanki/core/data/sync/SyncManager.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Modify: `app/src/test/java/nart/simpleanki/core/data/sync/SyncManagerTest.kt`
- Test: `app/src/test/java/nart/simpleanki/core/data/firestore/TypingLogDtoTest.kt`

- [ ] **Step 1: Add `TypingLogDto`**

In `FirestoreDtos.kt`, add the import:
```kotlin
import nart.simpleanki.core.domain.model.TypingLog
```
Then append after `StreakStateDto` (snake_case `@PropertyName`, `timestamp` as Firestore `Timestamp`):
```kotlin

// MARK: - Typing log (stored flat under users/{uid}/typingLogs)

data class TypingLogDto(
    var id: String = "",
    @get:PropertyName("card_id") @set:PropertyName("card_id") var cardId: String = "",
    @get:PropertyName("deck_id") @set:PropertyName("deck_id") var deckId: String = "",
    var correct: Boolean = false,
    @get:PropertyName("typed_text") @set:PropertyName("typed_text") var typedText: String = "",
    var timestamp: Timestamp = Timestamp(Date(0)),
) {
    fun toDomain(): TypingLog = TypingLog(
        id = id,
        cardId = cardId,
        deckId = deckId,
        correct = correct,
        typedText = typedText,
        timestamp = timestamp.toMillis(),
    )

    companion object {
        fun fromDomain(t: TypingLog): TypingLogDto = TypingLogDto(
            id = t.id,
            cardId = t.cardId,
            deckId = t.deckId,
            correct = t.correct,
            typedText = t.typedText,
            timestamp = t.timestamp.toTimestamp(),
        )
    }
}
```

- [ ] **Step 2: Add fetch/push to the remote seam**

In `RemoteSyncSource.kt`, add the import:
```kotlin
import nart.simpleanki.core.data.firestore.TypingLogDto
```
Then add inside the interface (after the reviewLogs pair):
```kotlin
    suspend fun fetchTypingLogs(uid: String): List<TypingLogDto>
    suspend fun pushTypingLogs(uid: String, dtos: List<TypingLogDto>)
```

- [ ] **Step 3: Implement them in `FirestoreSyncService`**

In `FirestoreSyncService.kt`, add the import:
```kotlin
import nart.simpleanki.core.data.firestore.TypingLogDto
```
Then add after the `pushReviewLogs` override:
```kotlin

    override suspend fun fetchTypingLogs(uid: String): List<TypingLogDto> =
        col(uid, "typingLogs").get().await().toObjects(TypingLogDto::class.java)

    override suspend fun pushTypingLogs(uid: String, dtos: List<TypingLogDto>) =
        push(uid, "typingLogs", dtos) { it.id }
```

- [ ] **Step 4: Thread typing logs through `SyncManager`**

In `SyncManager.kt`:
1. Add imports:
```kotlin
import nart.simpleanki.core.data.firestore.TypingLogDto
import nart.simpleanki.core.data.local.dao.TypingLogDao
```
2. Add the constructor param immediately after `reviewLogDao`:
```kotlin
        private val reviewLogDao: ReviewLogDao,
        private val typingLogDao: TypingLogDao,
        private val streakStateDao: StreakStateDao,
```
3. In `push(...)`, after the `reviewLogDao` dirty-push block, add:
```kotlin
        // Typing logs are immutable, append-only events: push any dirty rows, then clear the flag.
        typingLogDao.getDirty().takeIf { it.isNotEmpty() }?.let { rows ->
            remote.pushTypingLogs(uid, rows.map { TypingLogDto.fromDomain(it.toDomain()) })
            rows.forEach { typingLogDao.clearDirty(it.id) }
        }
```
4. In `pull(...)`, after the review-logs union block, add:
```kotlin
        // Typing logs: union by id (immutable, so no last-write-wins) — insert only the ids we lack.
        val localTypingIds = typingLogDao.getAllIds().toSet()
        remote.fetchTypingLogs(uid)
            .filter { it.id.isNotEmpty() && it.id !in localTypingIds }
            .map { it.toDomain().toEntity(dirty = false) }
            .takeIf { it.isNotEmpty() }
            ?.let { typingLogDao.insertAll(it) }
```

- [ ] **Step 5: Update the Koin `SyncManager` registration**

In `di/AppModule.kt`, change the `SyncManager` single to pass 8 dependencies (it now resolves `typingLogDao` after `reviewLogDao`):
```kotlin
    single { SyncManager(get(), get(), get(), get(), get(), get(), get(), get()) }
```

- [ ] **Step 6: Update existing `SyncManagerTest` construction + add typing-log tests**

In `SyncManagerTest.kt`:
1. Add imports next to the existing fakes/dtos:
```kotlin
import nart.simpleanki.core.data.firestore.TypingLogDto
import nart.simpleanki.core.data.repository.FakeTypingLogDao
```
2. Make `FakeRemote` implement the new seam methods. Add fields + overrides inside `FakeRemote`:
```kotlin
        var typingLogs: MutableList<TypingLogDto> = mutableListOf()
```
```kotlin
        val pushedTypingLogs = mutableListOf<TypingLogDto>()
```
```kotlin
        override suspend fun fetchTypingLogs(uid: String) = typingLogs
        override suspend fun pushTypingLogs(uid: String, dtos: List<TypingLogDto>) { pushedTypingLogs += dtos }
```
   (Put the `var typingLogs` next to the other `var ...: MutableList<...>` constructor fields, the `pushedTypingLogs` next to the other `pushed...` vals, and the two overrides next to the reviewLogs overrides.)
3. **Thread the new DAO into every `SyncManager(...)` call.** Each call currently passes the review-log DAO as the 4th arg and the streak DAO as the 5th; insert a `FakeTypingLogDao()` between them. There are 11 call sites — the two shapes are:
   - With literal fakes, e.g. `SyncManager(folderDao, FakeDeckDao(), FakeCardDao(), FakeReviewLogDao(), FakeStreakStateDao(), remote, m)` becomes
     `SyncManager(folderDao, FakeDeckDao(), FakeCardDao(), FakeReviewLogDao(), FakeTypingLogDao(), FakeStreakStateDao(), remote, m)`.
   - With variables, e.g. `SyncManager(FakeFolderDao(), FakeDeckDao(), FakeCardDao(), logDao, FakeStreakStateDao(), remote, m)` becomes
     `SyncManager(FakeFolderDao(), FakeDeckDao(), FakeCardDao(), logDao, FakeTypingLogDao(), FakeStreakStateDao(), remote, m)`
     and the streak tests `..., FakeReviewLogDao(), dao, ...` become `..., FakeReviewLogDao(), FakeTypingLogDao(), dao, ...`.
4. Add a helper + 2 tests at the end of the class (before the final `}`):
```kotlin
    private fun typingLogEntity(id: String, dirty: Boolean) = nart.simpleanki.core.data.local.TypingLogEntity(
        id = id, cardId = "c1", deckId = "d1", correct = true, typedText = "x", timestamp = 1_000, dirty = dirty,
    )

    @Test
    fun typingLogs_pushDirty_thenClearDirty() = runTest {
        val logDao = FakeTypingLogDao()
        logDao.insertAll(listOf(typingLogEntity("t1", dirty = true)))
        val remote = FakeRemote()
        val (m, _) = media()
        val sync = SyncManager(FakeFolderDao(), FakeDeckDao(), FakeCardDao(), FakeReviewLogDao(), logDao, FakeStreakStateDao(), remote, m)

        sync.sync("u1")

        assertEquals(listOf("t1"), remote.pushedTypingLogs.map { it.id })
        assertTrue(logDao.getDirty().isEmpty())
    }

    @Test
    fun typingLogs_pullUnionsRemote_andSkipsExisting() = runTest {
        val logDao = FakeTypingLogDao()
        logDao.insertAll(listOf(typingLogEntity("t1", dirty = false)))
        val remote = FakeRemote(typingLogs = mutableListOf(
            TypingLogDto.fromDomain(typingLogEntity("t1", dirty = false).toDomain()),
            TypingLogDto.fromDomain(typingLogEntity("t2", dirty = false).toDomain()),
        ))
        val (m, _) = media()
        val sync = SyncManager(FakeFolderDao(), FakeDeckDao(), FakeCardDao(), FakeReviewLogDao(), logDao, FakeStreakStateDao(), remote, m)

        sync.sync("u1")

        assertEquals(setOf("t1", "t2"), logDao.getAllIds().toSet())
        assertEquals(listOf("t1", "t2"), logDao.inserted.map { it.id })
    }
```
   Note: the `FakeRemote(typingLogs = ...)` named-arg construction requires `typingLogs` to be a constructor parameter of `FakeRemote` (added in sub-step 2).

- [ ] **Step 7: Add the DTO round-trip test**

Create `app/src/test/java/nart/simpleanki/core/data/firestore/TypingLogDtoTest.kt`:
```kotlin
package nart.simpleanki.core.data.firestore

import nart.simpleanki.core.domain.model.TypingLog
import org.junit.Assert.assertEquals
import org.junit.Test

class TypingLogDtoTest {
    @Test fun roundTrip_preservesFields() {
        val domain = TypingLog(
            id = "t1", cardId = "c1", deckId = "d1", correct = true, typedText = "café", timestamp = 1_700_000L,
        )
        assertEquals(domain, TypingLogDto.fromDomain(domain).toDomain())
    }
}
```

- [ ] **Step 8: Build + run the sync + DTO tests**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "nart.simpleanki.core.data.sync.SyncManagerTest" --tests "nart.simpleanki.core.data.firestore.TypingLogDtoTest"`
Expected: BUILD SUCCESSFUL; all `SyncManagerTest` tests (existing + 2 new) and the DTO test pass.

- [ ] **Step 9: Commit**
```bash
git add app/src/main/java/nart/simpleanki/core/data/firestore/FirestoreDtos.kt \
        app/src/main/java/nart/simpleanki/core/data/sync/RemoteSyncSource.kt \
        app/src/main/java/nart/simpleanki/core/data/sync/FirestoreSyncService.kt \
        app/src/main/java/nart/simpleanki/core/data/sync/SyncManager.kt \
        app/src/main/java/nart/simpleanki/di/AppModule.kt \
        app/src/test/java/nart/simpleanki/core/data/sync/SyncManagerTest.kt \
        app/src/test/java/nart/simpleanki/core/data/firestore/TypingLogDtoTest.kt
git commit -m "Sync typing logs to Firestore (append-only union)"
```

---

## Task 7: `TypePracticeViewModel`

**Files:**
- Create: `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeViewModel.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/typepractice/TypePracticeViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/nart/simpleanki/feature/typepractice/TypePracticeViewModelTest.kt`:
```kotlin
package nart.simpleanki.feature.typepractice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.data.repository.FakeDeckDao
import nart.simpleanki.core.data.repository.FakeTypingLogDao
import nart.simpleanki.core.data.repository.TypingLogRepository
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Deck
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TypePracticeViewModelTest {
    private val now = 1_700_000_000_000L

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun card(id: String, back: String) = Card(
        id = id, front = "f-$id", back = back, deckId = "A",
        dateCreated = now, lastModified = now, fsrsDue = now, fsrsState = CardState.New.value,
    )

    private fun vm(logRepo: TypingLogRepository): TypePracticeViewModel {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        // Seed via repos (suspend) is awkward here; use direct DAO upserts through the repos instead.
        return TypePracticeViewModel("A", cardRepo, deckRepo, logRepo, now = { now })
    }

    @Test
    fun correctAnswer_advances_appendsOneLog_andFinishes() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "A", dateCreated = now, lastModified = now))
        cardRepo.upsert(card("c1", "answer"))
        val logRepo = TypingLogRepository(FakeTypingLogDao(), newId = { java.util.UUID.randomUUID().toString() })
        val model = TypePracticeViewModel("A", cardRepo, deckRepo, logRepo, now = { now })
        backgroundScope.launch { model.uiState.collect {} }
        runCurrent()

        assertFalse(model.uiState.value.loading)
        assertEquals("c1", model.uiState.value.current!!.id)

        model.onInput("answer")
        model.onSubmit()
        runCurrent()

        assertTrue(model.uiState.value.finished)
        assertEquals(1, model.uiState.value.report!!.firstTryCorrect)
        val logs = logRepo.observeLogs().first()
        assertEquals(1, logs.size)
        assertTrue(logs.single().correct)
    }

    @Test
    fun wrongAnswer_revealsThenContinue_logsWrong_andRequeues() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "A", dateCreated = now, lastModified = now))
        cardRepo.upsert(card("c1", "answer"))
        val logRepo = TypingLogRepository(FakeTypingLogDao(), newId = { java.util.UUID.randomUUID().toString() })
        val model = TypePracticeViewModel("A", cardRepo, deckRepo, logRepo, now = { now })
        backgroundScope.launch { model.uiState.collect {} }
        runCurrent()

        model.onInput("nope")
        model.onSubmit()
        runCurrent()
        assertTrue(model.uiState.value.revealing)
        assertEquals("answer", model.uiState.value.revealedAnswer)
        assertTrue(model.uiState.value.canOverride)

        model.onContinue()
        runCurrent()
        assertFalse(model.uiState.value.revealing)
        assertEquals("c1", model.uiState.value.current!!.id)        // requeued back to itself (only card)
        val logs = logRepo.observeLogs().first()
        assertEquals(1, logs.size)
        assertFalse(logs.single().correct)
    }

    @Test
    fun blankBackCards_excluded_emptyPoolFinishesImmediately() = runTest {
        val deckRepo = DeckRepository(FakeDeckDao(), now = { now })
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        deckRepo.upsert(Deck(id = "A", name = "A", dateCreated = now, lastModified = now))
        cardRepo.upsert(card("c1", "   "))                          // blank back -> not typeable
        val logRepo = TypingLogRepository(FakeTypingLogDao(), newId = { java.util.UUID.randomUUID().toString() })
        val model = TypePracticeViewModel("A", cardRepo, deckRepo, logRepo, now = { now })
        backgroundScope.launch { model.uiState.collect {} }
        runCurrent()

        assertTrue(model.uiState.value.finished)
        assertEquals(0, model.uiState.value.report!!.completed)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.typepractice.TypePracticeViewModelTest"`
Expected: COMPILE FAILURE (`TypePracticeViewModel` does not exist).

- [ ] **Step 3: Implement `TypePracticeViewModel.kt`**

Create `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeViewModel.kt`:
```kotlin
package nart.simpleanki.feature.typepractice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nart.simpleanki.core.analytics.LoggableEvent
import nart.simpleanki.core.analytics.LogManager
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.TypingLogRepository
import nart.simpleanki.core.domain.fsrs.StudyQueueBuilder
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.ReviewCardFilter
import nart.simpleanki.core.domain.model.TypingLog
import nart.simpleanki.core.domain.typing.SessionReport
import nart.simpleanki.core.domain.typing.SubmitResult
import nart.simpleanki.core.domain.typing.TypePracticeSession
import nart.simpleanki.core.domain.typing.TypingMastery

data class TypePracticeUiState(
    val loading: Boolean = true,
    val current: Card? = null,
    val input: String = "",
    /** Showing the correct answer after a wrong submit. */
    val revealing: Boolean = false,
    val revealedAnswer: String = "",
    val lastTyped: String = "",
    /** Whether "I was right" is offered (first attempts only). */
    val canOverride: Boolean = false,
    val remaining: Int = 0,
    val finished: Boolean = false,
    val report: SessionReport? = null,
    /** Increments whenever the prompt card changes; the screen keys autofocus on it. */
    val cardTick: Int = 0,
)

/**
 * Drives one Type-Practice session. Decoupled from FSRS: snapshots the deck's typeable cards
 * (respecting the deck's review filter), runs the pure [TypePracticeSession], and appends exactly
 * one [TypingLog] per card when its first attempt finalizes. No scheduler or review-log writes.
 */
class TypePracticeViewModel(
    private val deckId: String?,
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
    private val typingLogRepository: TypingLogRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val logManager: LogManager = LogManager(emptyList()),
) : ViewModel() {

    private lateinit var session: TypePracticeSession
    private val _uiState = MutableStateFlow(TypePracticeUiState())
    val uiState: StateFlow<TypePracticeUiState> = _uiState.asStateFlow()

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        val deck = deckId?.let { deckRepository.getById(it) }
        val cards = if (deckId != null) cardRepository.observeCards(deckId).first() else emptyList()
        val pool = StudyQueueBuilder.buildReviewQueue(
            cards = cards,
            filter = deck?.reviewFilter ?: ReviewCardFilter.All,
            shuffleSeed = now(),
        ).filter { it.back.isNotBlank() }
        val previouslyMastered = TypingMastery.masteredCardIds(
            typingLogRepository.observeLogsForDeck(deckId.orEmpty()).first(),
        )
        session = TypePracticeSession(pool, previouslyMastered) { card, correct, typed ->
            viewModelScope.launch {
                typingLogRepository.append(
                    TypingLog(cardId = card.id, deckId = card.deckId, correct = correct, typedText = typed, timestamp = now()),
                )
            }
        }
        logManager.track(Event.Start(deckId, pool.size))
        renderAdvance()
        if (session.isFinished) logComplete()
    }

    fun onInput(text: String) {
        _uiState.value = _uiState.value.copy(input = text)
    }

    fun onSubmit() {
        val typed = _uiState.value.input
        when (val r = session.submit(typed)) {
            SubmitResult.Correct -> {
                logManager.track(Event.Answered(true))
                renderAdvance()
                if (session.isFinished) logComplete()
            }
            is SubmitResult.Wrong -> {
                logManager.track(Event.Answered(false))
                _uiState.value = _uiState.value.copy(
                    revealing = true, revealedAnswer = r.expected, lastTyped = typed, canOverride = session.canOverride,
                )
            }
        }
    }

    /** "Don't know": reveal the answer without an attempt; only Continue is offered. */
    fun onDontKnow() {
        val card = session.current ?: return
        when (session.submit("")) {
            is SubmitResult.Wrong -> {
                logManager.track(Event.Answered(false))
                _uiState.value = _uiState.value.copy(
                    revealing = true, revealedAnswer = card.back, lastTyped = "", canOverride = false,
                )
            }
            SubmitResult.Correct -> renderAdvance()   // unreachable (blank backs are filtered out)
        }
    }

    fun onContinue() {
        session.continueAfterWrong()
        renderAdvance()
        if (session.isFinished) logComplete()
    }

    fun onOverride() {
        session.override()
        renderAdvance()
        if (session.isFinished) logComplete()
    }

    fun restart() { viewModelScope.launch { load() } }

    /** Refreshes state from the session after the prompt changes (clears input, bumps autofocus tick). */
    private fun renderAdvance() {
        val prev = _uiState.value
        _uiState.value = prev.copy(
            loading = false,
            current = session.current,
            input = "",
            revealing = false,
            revealedAnswer = "",
            lastTyped = "",
            canOverride = false,
            remaining = session.remaining,
            finished = session.isFinished,
            report = if (session.isFinished) session.report() else null,
            cardTick = prev.cardTick + 1,
        )
    }

    private fun logComplete() {
        val r = session.report()
        logManager.track(Event.Complete(r.completed, r.firstTryAccuracy))
    }

    private sealed interface Event : LoggableEvent {
        data class Start(val deckId: String?, val count: Int) : Event {
            override val eventName = "type_practice_start"
            override val params get() = buildMap<String, Any?> {
                deckId?.let { put("deck_id", it) }
                put("count", count)
            }
        }
        data class Answered(val correct: Boolean) : Event {
            override val eventName = "type_practice_answer"
            override val params get() = mapOf("correct" to correct)
        }
        data class Complete(val count: Int, val accuracy: Int) : Event {
            override val eventName = "type_practice_complete"
            override val params get() = mapOf("count" to count, "accuracy" to accuracy)
        }
    }
}
```

- [ ] **Step 4: Register the ViewModel in Koin**

In `di/AppModule.kt`, add imports:
```kotlin
import nart.simpleanki.feature.typepractice.TypePracticeViewModel
```
Then, after the `ReviewViewModel` `viewModel { ... }` block, add (reuses the existing `StudyArgs`):
```kotlin
    viewModel { params ->
        val args = params.get<StudyArgs>()
        TypePracticeViewModel(
            deckId = args.deckId,
            cardRepository = get(),
            deckRepository = get(),
            typingLogRepository = get(),
            logManager = get(),
        )
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "nart.simpleanki.feature.typepractice.TypePracticeViewModelTest"`
Expected: BUILD SUCCESSFUL; 3 tests pass.

- [ ] **Step 6: Commit**
```bash
git add app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeViewModel.kt \
        app/src/main/java/nart/simpleanki/di/AppModule.kt \
        app/src/test/java/nart/simpleanki/feature/typepractice/TypePracticeViewModelTest.kt
git commit -m "Add TypePracticeViewModel driving the session and logging"
```

---

## Task 8: `TypePracticeScreen` (Compose UI + session report)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt`

No unit tests (Compose UI; logic is covered by Tasks 5 & 7). Verified by compile + previews.

- [ ] **Step 1: Implement `TypePracticeScreen.kt`**

Create `app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt`:
```kotlin
package nart.simpleanki.feature.typepractice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.typing.SessionReport
import nart.simpleanki.di.StudyArgs
import nart.simpleanki.ui.components.AudioPlayButton
import nart.simpleanki.ui.components.MediaImage
import nart.simpleanki.ui.theme.AzriTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TypePracticeScreen(
    deckId: String,
    onDone: () -> Unit,
    viewModel: TypePracticeViewModel = koinViewModel { parametersOf(StudyArgs(deckId = deckId)) },
) {
    val state by viewModel.uiState.collectAsState()
    TypePracticeContent(
        state = state,
        onInput = viewModel::onInput,
        onSubmit = viewModel::onSubmit,
        onDontKnow = viewModel::onDontKnow,
        onContinue = viewModel::onContinue,
        onOverride = viewModel::onOverride,
        onRestart = viewModel::restart,
        onDone = onDone,
    )
}

/** Stateless Type-Practice UI, decoupled from the ViewModel for previews. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypePracticeContent(
    state: TypePracticeUiState,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onContinue: () -> Unit,
    onOverride: () -> Unit,
    onRestart: () -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.finished) "Done" else "Type · ${state.remaining} left") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Default.Close, contentDescription = "Close") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.finished -> SessionReportView(state.report, onRestart, onDone)
                else -> PracticeCard(state, onInput, onSubmit, onDontKnow, onContinue, onOverride)
            }
        }
    }
}

@Composable
private fun PracticeCard(
    state: TypePracticeUiState,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onContinue: () -> Unit,
    onOverride: () -> Unit,
) {
    val card = state.current ?: return
    val focus = remember { FocusRequester() }
    // Re-focus the field each time the prompt changes.
    LaunchedEffect(state.cardTick) { runCatching { focus.requestFocus() } }

    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "PROMPT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(12.dp))
        card.image?.let { name ->
            MediaImage(name, card.imagePath, Modifier.fillMaxWidth().height(160.dp))
            Spacer(Modifier.height(16.dp))
        }
        Text(
            card.front,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        card.audioName?.let { name ->
            Spacer(Modifier.height(12.dp))
            AudioPlayButton(name, card.audioPath)
        }

        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = state.input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            enabled = !state.revealing,
            singleLine = true,
            label = { Text("Type the answer") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!state.revealing) onSubmit() }),
        )

        Spacer(Modifier.height(16.dp))

        if (state.revealing) {
            RevealPanel(state, onContinue, onOverride)
        } else {
            Button(
                onClick = onSubmit,
                enabled = state.input.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.large,
            ) { Text("Check") }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDontKnow, modifier = Modifier.fillMaxWidth()) {
                Text("Don't know")
            }
        }
    }
}

@Composable
private fun RevealPanel(state: TypePracticeUiState, onContinue: () -> Unit, onOverride: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Correct answer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            state.revealedAnswer,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        if (state.lastTyped.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "You typed: ${state.lastTyped}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text("Continue") }
        if (state.canOverride) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onOverride, modifier = Modifier.fillMaxWidth()) {
                Text("I was right")
            }
        }
    }
}

@Composable
private fun SessionReportView(report: SessionReport?, onRestart: () -> Unit, onDone: () -> Unit) {
    val r = report ?: SessionReport(0, 0, 0, 0, 0)
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Practice complete", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        ReportRow("Cards", r.completed.toString())
        ReportRow("First-try accuracy", "${r.firstTryAccuracy}%")
        ReportRow("Best combo", r.bestCombo.toString())
        ReportRow("Newly mastered", r.newlyMastered.toString())
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text("Practice again") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text("Done") }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val previewCard = Card(
    id = "c1", front = "¿Cómo estás?", back = "How are you?", deckId = "d1",
    dateCreated = 0, lastModified = 0, fsrsDue = 0, fsrsState = CardState.New.value,
)

@Preview(name = "Type · prompt", showBackground = true)
@Composable
private fun TypePromptPreview() {
    AzriTheme {
        TypePracticeContent(
            state = TypePracticeUiState(loading = false, current = previewCard, input = "How are", remaining = 5),
            onInput = {}, onSubmit = {}, onDontKnow = {}, onContinue = {}, onOverride = {}, onRestart = {}, onDone = {},
        )
    }
}

@Preview(name = "Type · revealed (wrong)", showBackground = true)
@Composable
private fun TypeRevealPreview() {
    AzriTheme {
        TypePracticeContent(
            state = TypePracticeUiState(
                loading = false, current = previewCard, remaining = 5,
                revealing = true, revealedAnswer = "How are you?", lastTyped = "how is you", canOverride = true,
            ),
            onInput = {}, onSubmit = {}, onDontKnow = {}, onContinue = {}, onOverride = {}, onRestart = {}, onDone = {},
        )
    }
}

@Preview(name = "Type · report", showBackground = true)
@Composable
private fun TypeReportPreview() {
    AzriTheme {
        TypePracticeContent(
            state = TypePracticeUiState(
                loading = false, finished = true,
                report = SessionReport(completed = 12, firstTryCorrect = 9, firstTryAccuracy = 75, bestCombo = 5, newlyMastered = 3),
            ),
            onInput = {}, onSubmit = {}, onDontKnow = {}, onContinue = {}, onOverride = {}, onRestart = {}, onDone = {},
        )
    }
}
```

- [ ] **Step 2: Verify it compiles (main + previews)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If `MediaImage` / `AudioPlayButton` signatures differ, match their definitions in `ui/components` — they're used in `FlipCard.kt` as `MediaImage(name, path, modifier)` and `AudioPlayButton(name, path)`.)

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/nart/simpleanki/feature/typepractice/TypePracticeScreen.kt
git commit -m "Add Type Practice screen with reveal and session report"
```

---

## Task 9: Deck-detail integration + navigation

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/deckdetail/DeckDetailViewModel.kt`
- Modify: `app/src/main/java/nart/simpleanki/feature/deckdetail/DeckDetailScreen.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Modify: `app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt`

- [ ] **Step 1: Add the mastery flow to `DeckDetailViewModel`**

In `DeckDetailViewModel.kt`:
1. Add imports:
```kotlin
import kotlinx.coroutines.flow.flowOf
import nart.simpleanki.core.data.repository.TypingMasteryProvider
import nart.simpleanki.core.domain.typing.DeckMastery
```
2. Add `mastery` to the UI state (after `newCount`):
```kotlin
    val newCount: Int = 0,
    val mastery: DeckMastery = DeckMastery(0, 0),
```
3. Add the provider as a constructor param (nullable, like `deckRepository`, so previews/tests can omit it):
```kotlin
class DeckDetailViewModel(
    private val deckId: String,
    private val cardRepository: CardRepository,
    deckRepository: DeckRepository? = null,
    typingMasteryProvider: TypingMasteryProvider? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
```
4. Add a mastery flow field (after `deckNameFlow`):
```kotlin
    private val masteryFlow = typingMasteryProvider?.observeDeckMastery(deckId) ?: flowOf(DeckMastery(0, 0))
```
5. Extend the `combine` to include it (add the 4th flow + lambda param, and set `mastery`):
```kotlin
    val uiState: StateFlow<DeckDetailUiState> =
        combine(
            cardRepository.observeCards(deckId).withDueTicks(now),
            queryFlow,
            deckNameFlow,
            masteryFlow,
        ) { (cards, nowMillis), query, name, mastery ->
            DeckDetailUiState(
                deckId = deckId,
                deckName = name,
                cards = cards,
                query = query,
                newCount = cards.count { it.fsrsState == CardState.New.value },
                dueCount = cards.count { it.fsrsState != CardState.New.value && it.fsrsDue <= nowMillis },
                mastery = mastery,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeckDetailUiState(deckId = deckId),
        )
```

- [ ] **Step 2: Add the Type Practice button + mastery ring to `DeckDetailScreen`**

In `DeckDetailScreen.kt`:
1. Add imports (the project already depends on material-icons-extended — `School`/`Style` are used here):
```kotlin
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
```
   (If `Spacer`/`width`/`Row` are already imported, skip the duplicates.)
2. Add `onTypePractice` to **both** `DeckDetailScreen(...)` and `DeckDetailContent(...)` signatures. In `DeckDetailScreen`'s parameter list, after `onReview: () -> Unit,`:
```kotlin
    onTypePractice: () -> Unit,
```
   and pass it through in the `DeckDetailContent(...)` call:
```kotlin
        onReview = onReview,
        onTypePractice = onTypePractice,
```
   In `DeckDetailContent`'s parameter list, after `onReview: () -> Unit = {},`:
```kotlin
    onTypePractice: () -> Unit = {},
```
3. In the header `Column`, after the existing `if (state.total > 0) { OutlinedButton(onReview...) { ... } }` block, add the Type Practice button + ring:
```kotlin
                if (state.total > 0) {
                    OutlinedButton(
                        onClick = onTypePractice,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        if (state.mastery.total > 0) {
                            CircularProgressIndicator(
                                progress = { state.mastery.mastered.toFloat() / state.mastery.total },
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Keyboard, contentDescription = null)
                        }
                        Text(
                            "Type Practice",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        if (state.mastery.total > 0) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${state.mastery.mastered}/${state.mastery.total} mastered",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
```
4. Update each `DeckDetailContent(...)` / `DeckDetailScreen(...)` call in the previews at the bottom of the file to pass `onTypePractice = {}` (there are preview calls passing `onReview = {}` / `onStudy = {}`; add `onTypePractice = {}` to each so they compile).

- [ ] **Step 3: Wire Koin + navigation**

In `di/AppModule.kt`, update the `DeckDetailViewModel` registration to inject the provider:
```kotlin
    viewModel { params ->
        DeckDetailViewModel(
            deckId = params.get(),
            cardRepository = get(),
            deckRepository = get(),
            typingMasteryProvider = get(),
        )
    }
```

In `AzriNavHost.kt`:
1. Add the import next to the other screen imports:
```kotlin
import nart.simpleanki.feature.typepractice.TypePracticeScreen
```
2. In the `composable("deck/{deckId}")` block, add the `onTypePractice` lambda to the `DeckDetailScreen(...)` call (after `onReview`):
```kotlin
                    onReview = { nav.navigate("review/$deckId") },
                    onTypePractice = { nav.navigate("typePractice/$deckId") },
```
3. After the `composable("review/{deckId}")` block, add the route:
```kotlin
            composable("typePractice/{deckId}") { entry ->
                TypePracticeScreen(
                    deckId = entry.arguments?.getString("deckId").orEmpty(),
                    onDone = { nav.popBackStack() },
                )
            }
```

- [ ] **Step 4: Full build + the whole unit suite + APK**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL; all unit tests pass. (`compileDebugAndroidTestKotlin` guards against an androidTest call site that constructs `DeckDetailContent`/`DeckDetailScreen` without `onTypePractice`.)

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/nart/simpleanki/feature/deckdetail/DeckDetailViewModel.kt \
        app/src/main/java/nart/simpleanki/feature/deckdetail/DeckDetailScreen.kt \
        app/src/main/java/nart/simpleanki/di/AppModule.kt \
        app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt
git commit -m "Add Type Practice entry point and mastery ring to deck detail"
```

---

## Final verification
- [ ] `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL, all unit tests green.
- [ ] Confirm no commit message mentions "claude" and none carry a Co-Authored-By/attribution trailer: `git log --format='%an <%ae>%n%B' origin/main..HEAD | grep -i -E "claude|co-authored-by"` → no output.
- [ ] Confirm the untracked realtime-study-queue plan was never staged: `git status --short` still shows `?? docs/superpowers/plans/2026-06-04-realtime-study-queue.md`.
- [ ] (Optional, emulator) Open a deck → **Type Practice**: the field auto-focuses; a correct answer advances; a wrong answer reveals the correct answer with **Continue** + **I was right**; finishing shows the report; the deck-detail ring reflects mastered/total and does **not** change FSRS due counts or the study streak.
```
