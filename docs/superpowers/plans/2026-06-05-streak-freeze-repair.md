# Streak Freeze + Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add earn-by-studying streak freezes (auto-cover a missed day) and a free, limited streak repair, synced via Firestore, without changing the pure `StreakCalculator`.

**Architecture:** A pure reconciler (`StreakReconciler`) decides which missed days become "frozen" and when freezes are earned; a frozen day is just another active day fed to the existing `StreakCalculator`. New synced `StreakState` (Room + Firestore LWW) persists freezes/frozen-days. A `StreakStateManager` ties the reconciler to the repositories; `StreakProvider` unions frozen days into the streak. UI shows a ❄️ count + a repair banner.

**Tech Stack:** Kotlin, Room (entity/DAO/migration v2→v3), Firestore sync via `SyncManager` (LWW), Koin, Jetpack Compose Material3, JUnit4 + coroutines-test.

**Build/test prefix:** ALL Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&` and run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

**Commit rule:** No "claude" mention in commit messages; no Co-Authored-By / attribution trailer. Don't `git add` the unrelated untracked `docs/superpowers/plans/2026-06-04-realtime-study-queue.md`.

---

## File Structure

- `core/domain/streak/StreakState.kt` (new) — `StreakState`, `RepairOffer`, `StreakReconciler` (pure).
- `core/data/local/RoomEntities.kt` (modify) — add `StreakStateEntity`.
- `core/data/local/dao/Daos.kt` (modify) — add `StreakStateDao`.
- `core/data/local/StreakStateMappers.kt` (new) — entity↔domain (frozenDays String codec).
- `core/data/local/AzriDatabase.kt` (modify) — version 3, entity, dao, `MIGRATION_2_3`.
- `core/data/repository/Repositories.kt` (modify) — add `StreakStateRepository`.
- `core/data/firestore/FirestoreDtos.kt` (modify) — add `StreakStateDto` + entity mappers.
- `core/data/sync/RemoteSyncSource.kt`, `FirestoreSyncService.kt`, `SyncManager.kt` (modify) — streak-state sync.
- `core/data/repository/StreakStateManager.kt` (new) — reconcile/repair over repositories.
- `core/data/repository/StreakProvider.kt` (modify) — union frozen days.
- `di/AppModule.kt` (modify) — DB migration + DAO + repos + manager + SyncManager arg + VM wiring.
- `feature/queue/StudyQueueViewModel.kt` + `StudyQueueScreen.kt` (modify) — freeze chip + repair banner.
- `feature/study/StudyViewModel.kt` + `SessionSummary.kt` (modify) — freeze-used + repair in summary.
- Tests: `StreakReconcilerTest`, `StreakStateRepositoryTest`, `StreakProviderTest` (extend), `SyncManagerTest` (extend), `StudyQueueViewModelTest`/`StudyViewModelTest` (extend); `FakeStreakStateDao` in `FakeDaos.kt`.

---

## Task 1: Pure reconciler + repair logic

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/domain/streak/StreakState.kt`
- Test: `app/src/test/java/nart/simpleanki/core/domain/streak/StreakReconcilerTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `StreakReconcilerTest.kt`:
```kotlin
package nart.simpleanki.core.domain.streak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class StreakReconcilerTest {

    // Helper: a fresh state that has already been reconciled (so first-run seeding doesn't apply).
    private fun seeded(today: Long, tokens: Int = 0, awarded: Int = 0, frozen: Set<Long> = emptySet()) =
        StreakState(freezeTokens = tokens, frozenDays = frozen, freezesAwardedForRun = awarded, lastReconciledDay = today)

    @Test
    fun autoFreeze_coversSingleMissedDay_whenTokenAvailable() {
        // Studied days 1..5, then missed day 6; today = 7. One token covers day 6.
        val reviews = setOf(1L, 2, 3, 4, 5)
        val state = seeded(today = 5, tokens = 1)
        val out = StreakReconciler.reconcile(reviews, state, today = 7)
        assertEquals(setOf(6L), out.frozenDays)
        assertEquals(0, out.freezeTokens)
        // Streak survives across the frozen gap: days 1..5 + frozen 6 + ... today=7 not studied yet,
        // so current run ends at 6 (alive through today since 6 == today-1).
        assertEquals(6, StreakCalculator.compute(reviews + out.frozenDays, 7).current)
    }

    @Test
    fun autoFreeze_breaks_whenNoTokens() {
        val reviews = setOf(1L, 2, 3, 4, 5)
        val state = seeded(today = 5, tokens = 0)
        val out = StreakReconciler.reconcile(reviews, state, today = 7)
        assertEquals(emptySet<Long>(), out.frozenDays)
        assertEquals(0, StreakCalculator.compute(reviews + out.frozenDays, 7).current) // broke
    }

    @Test
    fun earn_grantsOneFreezePerSevenDays_cappedAtTwo() {
        // Studied 1..7 contiguous; today = 7. Should earn 1 freeze.
        val reviews = (1L..7L).toSet()
        val out = StreakReconciler.reconcile(reviews, seeded(today = 6), today = 7)
        assertEquals(1, out.freezeTokens)
        assertEquals(1, out.freezesAwardedForRun)
        // Studied 1..21 contiguous → would earn 3, but cap is 2.
        val reviews3 = (1L..21L).toSet()
        val out3 = StreakReconciler.reconcile(reviews3, seeded(today = 20), today = 21)
        assertEquals(2, out3.freezeTokens)
        assertEquals(3, out3.freezesAwardedForRun) // counter advances past the cap
    }

    @Test
    fun earn_isIdempotent_acrossRepeatedReconciles() {
        val reviews = (1L..7L).toSet()
        val once = StreakReconciler.reconcile(reviews, seeded(today = 6), today = 7)
        val twice = StreakReconciler.reconcile(reviews, once, today = 7)
        assertEquals(once.freezeTokens, twice.freezeTokens)
        assertEquals(once.freezesAwardedForRun, twice.freezesAwardedForRun)
    }

    @Test
    fun firstRun_doesNotFloodFreezes_forPreexistingStreak() {
        // Never reconciled (lastReconciledDay = 0); user already has a 10-day streak.
        val reviews = (1L..10L).toSet()
        val out = StreakReconciler.reconcile(reviews, StreakState(), today = 10)
        assertEquals(0, out.freezeTokens) // seeded awarded = 10/7 = 1, no new award at 10
        assertEquals(1, out.freezesAwardedForRun)
    }

    @Test
    fun brokenRun_resetsAwardCounter() {
        // A new short run after a break: awarded should drop to current/7.
        val reviews = setOf(20L) // single day, today = 20
        val out = StreakReconciler.reconcile(reviews, seeded(today = 19, awarded = 3), today = 20)
        assertEquals(0, out.freezesAwardedForRun)
    }

    @Test
    fun repair_offeredOnlyForSingleRecentGap_afterStudyingToday() {
        // Studied 1..5, missed 6, studied today=7. Prior run (ending 5)=5, gap=6, today studied.
        val reviews = setOf(1L, 2, 3, 4, 5, 7)
        val state = seeded(today = 7) // broken: day 6 not frozen
        val offer = StreakReconciler.repairEligibility(reviews, state, today = 7)
        assertNotNull(offer)
        assertEquals(7, offer!!.restoredStreak) // prior run 5 + frozen gap + today = 7

        val repaired = StreakReconciler.repair(state, today = 7)
        assertEquals(setOf(6L), repaired.frozenDays)
        assertEquals(7L, repaired.lastRepairDay)
        assertEquals(7, StreakCalculator.compute(reviews + repaired.frozenDays, 7).current)
    }

    @Test
    fun repair_notOffered_withoutStudyingToday_orWithinCooldown_orMultiDayGap() {
        // Not studied today.
        assertNull(StreakReconciler.repairEligibility(setOf(1L, 2, 3, 4, 5), seeded(today = 7), today = 7))
        // Within cooldown.
        val recentRepair = seeded(today = 7).copy(lastRepairDay = 6)
        assertNull(StreakReconciler.repairEligibility(setOf(1L, 2, 3, 4, 5, 7), recentRepair, today = 7))
        // Multi-day gap (missed 5 and 6).
        assertNull(StreakReconciler.repairEligibility(setOf(1L, 2, 3, 4, 7), seeded(today = 7), today = 7))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail (compile failure — symbols don't exist)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.streak.StreakReconcilerTest"`
Expected: COMPILE FAILURE.

- [ ] **Step 3: Implement `StreakState.kt`**

Create `app/src/main/java/nart/simpleanki/core/domain/streak/StreakState.kt`:
```kotlin
package nart.simpleanki.core.domain.streak

/** Persisted, synced streak overlay: freezes earned and the days they cover. */
data class StreakState(
    val freezeTokens: Int = 0,
    val frozenDays: Set<Long> = emptySet(),
    val freezesAwardedForRun: Int = 0,
    val lastReconciledDay: Long = 0,
    val lastRepairDay: Long = 0,
)

/** A streak that can be restored by a (free, limited) repair. */
data class RepairOffer(val restoredStreak: Int)

/**
 * Pure streak-overlay logic. Operates on civil-day indices (bucket with `localEpochDay` first).
 * A frozen day is treated as an active day by [StreakCalculator], so the run survives the gap.
 */
object StreakReconciler {
    const val FREEZE_CAP = 2
    const val FREEZE_EARN_EVERY = 7
    const val REPAIR_COOLDOWN_DAYS = 30L

    /** Advances [state] for [today]: auto-freeze elapsed missed days, then earn freezes. Idempotent. */
    fun reconcile(reviewDays: Set<Long>, state: StreakState, today: Long): StreakState {
        var frozen = state.frozenDays
        var tokens = state.freezeTokens
        var awarded = state.freezesAwardedForRun

        // 1. Auto-freeze fully-elapsed missed days (once per civil day).
        if (today > state.lastReconciledDay) {
            val active = reviewDays + frozen
            val lastActive = active.filter { it <= today }.maxOrNull()
            if (lastActive != null) {
                var d = lastActive + 1
                while (d <= today - 1) {
                    if (d !in reviewDays && d !in frozen) {
                        if (tokens > 0) { frozen = frozen + d; tokens -= 1 } else break
                    }
                    d++
                }
            }
        }

        val current = StreakCalculator.compute(reviewDays + frozen, today).current

        // First-ever reconcile: seed the counter so a pre-existing streak doesn't dump freezes.
        if (state.lastReconciledDay == 0L) {
            awarded = maxOf(awarded, current / FREEZE_EARN_EVERY)
        }
        // A new (shorter) run started: reset the counter to the current run.
        if (current < awarded * FREEZE_EARN_EVERY) {
            awarded = current / FREEZE_EARN_EVERY
        }
        // Earn one freeze per new multiple reached (counter advances even when the cap blocks a grant).
        while (current >= (awarded + 1) * FREEZE_EARN_EVERY) {
            awarded += 1
            if (tokens < FREEZE_CAP) tokens += 1
        }

        return state.copy(
            freezeTokens = tokens,
            frozenDays = frozen,
            freezesAwardedForRun = awarded,
            lastReconciledDay = maxOf(state.lastReconciledDay, today),
        )
    }

    /** A repair is offered for a single missed day at [today]-1, only after studying today, once per cooldown. */
    fun repairEligibility(reviewDays: Set<Long>, state: StreakState, today: Long): RepairOffer? {
        if (today !in reviewDays) return null
        if (state.lastRepairDay != 0L && today - state.lastRepairDay < REPAIR_COOLDOWN_DAYS) return null
        val active = reviewDays + state.frozenDays
        if ((today - 1) in active) return null            // no gap at yesterday → nothing to repair
        val priorRun = runEndingAt(active, today - 2)
        if (priorRun < 1) return null                     // no real streak existed before the gap
        return RepairOffer(restoredStreak = priorRun + 2) // prior run + frozen gap + today
    }

    /** Freezes the [today]-1 gap day and records the repair. Caller persists. */
    fun repair(state: StreakState, today: Long): StreakState =
        state.copy(frozenDays = state.frozenDays + (today - 1), lastRepairDay = today)

    private fun runEndingAt(days: Set<Long>, end: Long): Int {
        var n = 0
        var d = end
        while (d in days) { n++; d-- }
        return n
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.streak.StreakReconcilerTest"`
Expected: PASS (all 8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/domain/streak/StreakState.kt \
        app/src/test/java/nart/simpleanki/core/domain/streak/StreakReconcilerTest.kt
git commit -m "Add pure streak freeze/repair reconciler"
```

---

## Task 2: Room persistence (entity, DAO, migration, repository)

**Files:**
- Modify: `core/data/local/RoomEntities.kt`, `core/data/local/dao/Daos.kt`, `core/data/local/AzriDatabase.kt`, `core/data/repository/Repositories.kt`, `di/AppModule.kt`
- Create: `core/data/local/StreakStateMappers.kt`
- Test: `app/src/test/java/nart/simpleanki/core/data/repository/StreakStateRepositoryTest.kt`, and add `FakeStreakStateDao` to `FakeDaos.kt`

- [ ] **Step 1: Add the entity** (`RoomEntities.kt`, append):
```kotlin
@Entity(tableName = "streak_state")
data class StreakStateEntity(
    @PrimaryKey val id: String = "current",
    val freezeTokens: Int,
    /** Civil-day indices covered by a freeze/repair, sorted, comma-separated (empty string = none). */
    val frozenDays: String,
    val freezesAwardedForRun: Int,
    val lastReconciledDay: Long,
    val lastRepairDay: Long,
    val lastModified: Long,
    val dirty: Boolean = true,
)
```
(Ensure `androidx.room.Entity` / `PrimaryKey` are imported — they already are for the other entities in this file.)

- [ ] **Step 2: Add the DAO** (`dao/Daos.kt`, append):
```kotlin
@Dao
interface StreakStateDao {
    @Query("SELECT * FROM streak_state WHERE id = 'current'")
    fun observe(): Flow<StreakStateEntity?>

    @Query("SELECT * FROM streak_state WHERE id = 'current'")
    suspend fun get(): StreakStateEntity?

    @Upsert
    suspend fun upsert(entity: StreakStateEntity)

    @Query("SELECT * FROM streak_state WHERE dirty = 1")
    suspend fun getDirty(): StreakStateEntity?

    @Query("UPDATE streak_state SET dirty = 0 WHERE id = 'current' AND lastModified = :lastModified")
    suspend fun clearDirty(lastModified: Long)
}
```
(Match the existing import style in `Daos.kt`: `androidx.room.Dao/Query/Upsert`, `kotlinx.coroutines.flow.Flow`, and `StreakStateEntity` from `nart.simpleanki.core.data.local`. If the file uses `@Upsert` elsewhere, reuse it; otherwise use `@Insert(onConflict = OnConflictStrategy.REPLACE)`.)

- [ ] **Step 3: Add mappers** — create `core/data/local/StreakStateMappers.kt`:
```kotlin
package nart.simpleanki.core.data.local

import nart.simpleanki.core.domain.streak.StreakState

fun StreakStateEntity.toDomain(): StreakState = StreakState(
    freezeTokens = freezeTokens,
    frozenDays = frozenDays.split(",").filter { it.isNotBlank() }.map { it.toLong() }.toSet(),
    freezesAwardedForRun = freezesAwardedForRun,
    lastReconciledDay = lastReconciledDay,
    lastRepairDay = lastRepairDay,
)

fun StreakState.toEntity(lastModified: Long, dirty: Boolean): StreakStateEntity = StreakStateEntity(
    id = "current",
    freezeTokens = freezeTokens,
    frozenDays = frozenDays.sorted().joinToString(","),
    freezesAwardedForRun = freezesAwardedForRun,
    lastReconciledDay = lastReconciledDay,
    lastRepairDay = lastRepairDay,
    lastModified = lastModified,
    dirty = dirty,
)
```

- [ ] **Step 4: Bump the DB + add migration** (`AzriDatabase.kt`):
  - Add `StreakStateEntity::class` to `entities`, set `version = 3`, add `abstract fun streakStateDao(): StreakStateDao` and its import.
  - Append the migration (column types/nullability MUST match the entity; no SQL DEFAULT — mirror `MIGRATION_1_2`):
```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `streak_state` (" +
                "`id` TEXT NOT NULL, `freezeTokens` INTEGER NOT NULL, `frozenDays` TEXT NOT NULL, " +
                "`freezesAwardedForRun` INTEGER NOT NULL, `lastReconciledDay` INTEGER NOT NULL, " +
                "`lastRepairDay` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL, " +
                "`dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}
```

- [ ] **Step 5: Add the repository** (`Repositories.kt`, append):
```kotlin
class StreakStateRepository(
    private val dao: StreakStateDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun observe(): Flow<StreakState> =
        dao.observe().map { it?.toDomain() ?: StreakState() }

    suspend fun get(): StreakState = dao.get()?.toDomain() ?: StreakState()

    suspend fun update(state: StreakState) {
        dao.upsert(state.toEntity(lastModified = now(), dirty = true))
    }
}
```
(Imports: `nart.simpleanki.core.data.local.dao.StreakStateDao`, `nart.simpleanki.core.data.local.toDomain`/`toEntity`, `nart.simpleanki.core.domain.streak.StreakState`, `kotlinx.coroutines.flow.Flow/map` — match the file's existing imports.)

- [ ] **Step 6: Add `FakeStreakStateDao`** to `FakeDaos.kt`:
```kotlin
class FakeStreakStateDao : StreakStateDao {
    private val store = MutableStateFlow<StreakStateEntity?>(null)
    override fun observe(): Flow<StreakStateEntity?> = store
    override suspend fun get(): StreakStateEntity? = store.value
    override suspend fun upsert(entity: StreakStateEntity) { store.value = entity }
    override suspend fun getDirty(): StreakStateEntity? = store.value?.takeIf { it.dirty }
    override suspend fun clearDirty(lastModified: Long) {
        store.value?.let { if (it.lastModified == lastModified) store.value = it.copy(dirty = false) }
    }
}
```
(Match existing imports in `FakeDaos.kt`: `StreakStateDao`, `StreakStateEntity`, `MutableStateFlow`, `Flow`.)

- [ ] **Step 7: Write the repository test** — `StreakStateRepositoryTest.kt`:
```kotlin
package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.domain.streak.StreakState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakStateRepositoryTest {
    private val now = 1_700_000_000_000L

    @Test
    fun observe_defaultsToEmptyState_whenAbsent() = runTest {
        val repo = StreakStateRepository(FakeStreakStateDao(), now = { now })
        assertEquals(StreakState(), repo.observe().first())
        assertEquals(StreakState(), repo.get())
    }

    @Test
    fun update_stampsLastModifiedAndDirty_andRoundTripsFrozenDays() = runTest {
        val dao = FakeStreakStateDao()
        val repo = StreakStateRepository(dao, now = { now })
        repo.update(StreakState(freezeTokens = 2, frozenDays = setOf(6L, 3L), freezesAwardedForRun = 1, lastReconciledDay = 7))
        val saved = dao.get()!!
        assertEquals(now, saved.lastModified)
        assertTrue(saved.dirty)
        assertEquals("3,6", saved.frozenDays) // sorted
        assertEquals(setOf(3L, 6L), repo.get().frozenDays)
    }
}
```

- [ ] **Step 8: Wire Koin** (`AppModule.kt`):
  - In the Room builder, add `.addMigrations(MIGRATION_2_3)` (alongside `MIGRATION_1_2`) and import `MIGRATION_2_3`.
  - Add `single { get<AzriDatabase>().streakStateDao() }`.
  - Add `single { StreakStateRepository(get()) }`.

- [ ] **Step 9: Verify** — compile + the new unit test + full suite:

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `StreakStateRepositoryTest` passes.

- [ ] **Step 10: Commit**
```bash
git add app/src/main/java/nart/simpleanki/core/data/local app/src/main/java/nart/simpleanki/core/data/repository/Repositories.kt \
        app/src/main/java/nart/simpleanki/di/AppModule.kt \
        app/src/test/java/nart/simpleanki/core/data/repository/FakeDaos.kt \
        app/src/test/java/nart/simpleanki/core/data/repository/StreakStateRepositoryTest.kt
git commit -m "Persist streak state in Room with migration"
```

---

## Task 3: Firestore sync for streak state

**Files:**
- Modify: `core/data/firestore/FirestoreDtos.kt`, `core/data/sync/RemoteSyncSource.kt`, `core/data/sync/FirestoreSyncService.kt`, `core/data/sync/SyncManager.kt`, `di/AppModule.kt`
- Test: `app/src/test/java/nart/simpleanki/core/data/sync/SyncManagerTest.kt` (extend)

- [ ] **Step 1: Write the failing SyncManager tests** (append to `SyncManagerTest.kt`)

Add to the inline `FakeRemote`: a field `var streakState: StreakStateDto? = null`, a `var pushedStreakState: StreakStateDto? = null`, and:
```kotlin
        override suspend fun fetchStreakState(uid: String) = streakState
        override suspend fun pushStreakState(uid: String, dto: StreakStateDto) { pushedStreakState = dto }
```
Add a helper and two tests:
```kotlin
    private fun streakEntity(lastModified: Long, dirty: Boolean) =
        nart.simpleanki.core.data.local.StreakStateEntity(
            id = "current", freezeTokens = 1, frozenDays = "3,6", freezesAwardedForRun = 1,
            lastReconciledDay = 7, lastRepairDay = 0, lastModified = lastModified, dirty = dirty,
        )

    @Test
    fun streakState_pushDirty_thenClearDirty() = runTest {
        val dao = FakeStreakStateDao()
        dao.upsert(streakEntity(lastModified = 100, dirty = true))
        val remote = FakeRemote()
        val (m, _) = media()
        val sync = SyncManager(FakeFolderDao(), FakeDeckDao(), FakeCardDao(), FakeReviewLogDao(), dao, remote, m)
        sync.sync("u1")
        assertEquals(100L, remote.pushedStreakState!!.lastModifiedMillis())
        assertFalse(dao.get()!!.dirty)
    }

    @Test
    fun streakState_pull_appliesNewer_skipsOlder() = runTest {
        val dao = FakeStreakStateDao()
        dao.upsert(streakEntity(lastModified = 100, dirty = false))
        val newer = StreakStateDto.fromEntity(streakEntity(lastModified = 200, dirty = false)).apply { freezeTokens = 2 }
        val sync = SyncManager(FakeFolderDao(), FakeDeckDao(), FakeCardDao(), FakeReviewLogDao(), dao,
            FakeRemote(streakState = newer), media().first)
        sync.sync("u1")
        assertEquals(2, dao.get()!!.freezeTokens) // newer remote applied
    }
```
(Adjust `media().first`/destructuring to match the existing test's `media()` helper shape — it returns `Pair(MediaManager, uploader)`; use `val (m, _) = media()` as the other tests do.)

- [ ] **Step 2: Run to verify failure** (compile error — `StreakStateDto`, the remote methods, and the 7-arg `SyncManager` don't exist):

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.data.sync.SyncManagerTest"`
Expected: COMPILE FAILURE.

- [ ] **Step 3: Add `StreakStateDto`** (`FirestoreDtos.kt`, append; mirror the file's `@PropertyName`/`Timestamp` style):
```kotlin
data class StreakStateDto(
    @DocumentId var id: String? = "current",
    @get:PropertyName("freeze_tokens") @set:PropertyName("freeze_tokens") var freezeTokens: Int = 0,
    @get:PropertyName("frozen_days") @set:PropertyName("frozen_days") var frozenDays: List<Long> = emptyList(),
    @get:PropertyName("freezes_awarded_for_run") @set:PropertyName("freezes_awarded_for_run") var freezesAwardedForRun: Int = 0,
    @get:PropertyName("last_reconciled_day") @set:PropertyName("last_reconciled_day") var lastReconciledDay: Long = 0,
    @get:PropertyName("last_repair_day") @set:PropertyName("last_repair_day") var lastRepairDay: Long = 0,
    @get:PropertyName("last_modified") @set:PropertyName("last_modified") var lastModified: Timestamp = Timestamp(Date(0)),
) {
    fun lastModifiedMillis(): Long = lastModified.toMillis()

    fun toEntity(dirty: Boolean) = nart.simpleanki.core.data.local.StreakStateEntity(
        id = "current",
        freezeTokens = freezeTokens,
        frozenDays = frozenDays.sorted().joinToString(","),
        freezesAwardedForRun = freezesAwardedForRun,
        lastReconciledDay = lastReconciledDay,
        lastRepairDay = lastRepairDay,
        lastModified = lastModified.toMillis(),
        dirty = dirty,
    )

    companion object {
        fun fromEntity(e: nart.simpleanki.core.data.local.StreakStateEntity) = StreakStateDto(
            id = "current",
            freezeTokens = e.freezeTokens,
            frozenDays = e.frozenDays.split(",").filter { it.isNotBlank() }.map { it.toLong() },
            freezesAwardedForRun = e.freezesAwardedForRun,
            lastReconciledDay = e.lastReconciledDay,
            lastRepairDay = e.lastRepairDay,
            lastModified = e.lastModified.toTimestamp(),
        )
    }
}
```
(`toMillis()`/`toTimestamp()` are the existing private helpers in this file — they're accessible to code in the same file. If a member function can't see the file-private extensions, inline `Timestamp(Date(this))` / `toDate().time`.)

- [ ] **Step 4: Extend `RemoteSyncSource`**:
```kotlin
    suspend fun fetchStreakState(uid: String): StreakStateDto?
    suspend fun pushStreakState(uid: String, dto: StreakStateDto)
```
(import `StreakStateDto`.)

- [ ] **Step 5: Implement in `FirestoreSyncService`** (single doc `users/{uid}/streakState/current`):
```kotlin
    override suspend fun fetchStreakState(uid: String): StreakStateDto? =
        col(uid, "streakState").document("current").get().await().toObject(StreakStateDto::class.java)

    override suspend fun pushStreakState(uid: String, dto: StreakStateDto) {
        col(uid, "streakState").document("current").set(dto).await()
    }
```
(`await` and `toObject` are already used in this file; import `StreakStateDto`.)

- [ ] **Step 6: Wire `SyncManager`**:
  - Add `private val streakStateDao: StreakStateDao,` to the constructor (after `reviewLogDao`, before `remote`), and import it.
  - In `push(uid)`, after the review-logs block:
```kotlin
        streakStateDao.getDirty()?.let { row ->
            remote.pushStreakState(uid, StreakStateDto.fromEntity(row))
            streakStateDao.clearDirty(row.lastModified)
        }
```
  - In `pull(uid)`, after the review-logs pull:
```kotlin
        remote.fetchStreakState(uid)?.let { dto ->
            if (shouldApplyRemote(streakStateDao.get()?.lastModified, dto.lastModifiedMillis())) {
                streakStateDao.upsert(dto.toEntity(dirty = false))
            }
        }
```
  - Import `StreakStateDto`.

- [ ] **Step 7: Update Koin `SyncManager` provider** (`AppModule.kt`) to pass `streakStateDao = get()` (or positionally after `reviewLogDao`).

- [ ] **Step 8: Run the SyncManager tests + full suite**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; the two new streak-state sync tests pass; existing sync tests still pass (their `SyncManager(...)` constructions must add the new `FakeStreakStateDao()` arg — update every `SyncManager(...)` call in `SyncManagerTest` to include it in the `reviewLogDao`→`remote` gap).

- [ ] **Step 9: Commit**
```bash
git add app/src/main/java/nart/simpleanki/core/data/firestore/FirestoreDtos.kt \
        app/src/main/java/nart/simpleanki/core/data/sync app/src/main/java/nart/simpleanki/di/AppModule.kt \
        app/src/test/java/nart/simpleanki/core/data/sync/SyncManagerTest.kt
git commit -m "Sync streak state via Firestore (last-write-wins)"
```

---

## Task 4: StreakStateManager + StreakProvider integration

**Files:**
- Create: `core/data/repository/StreakStateManager.kt`
- Modify: `core/data/repository/StreakProvider.kt`, `di/AppModule.kt`
- Test: `app/src/test/java/nart/simpleanki/core/data/repository/StreakStateManagerTest.kt`, `StreakProviderTest.kt` (extend)

- [ ] **Step 1: Write the failing tests** — `StreakStateManagerTest.kt`:
```kotlin
package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.data.local.dao.ReviewLogDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.TimeZone

class StreakStateManagerTest {
    // Civil-day d (UTC) → millis at noon, so localEpochDay(UTC) == d.
    private val utc = TimeZone.getTimeZone("UTC")
    private fun dayMillis(d: Long) = d * 86_400_000L + 43_200_000L

    private fun logsRepo(days: List<Long>): ReviewLogRepository {
        val dao = FakeReviewLogDao()
        // Reuse the repo's append to create logs on the given civil days.
        val repo = ReviewLogRepository(dao, newId = { java.util.UUID.randomUUID().toString() })
        return repo.also { r ->
            kotlinx.coroutines.runBlocking {
                days.forEach { d -> r.append("c1", reviewLogOn(dayMillis(d))) }
            }
        }
    }

    @Test
    fun reconcile_persistsAutoFreeze_andProvidesFreezeCount() = runTest {
        val stateRepo = StreakStateRepository(FakeStreakStateDao(), now = { 0L })
        // pre-seed a token + a prior reconcile so first-run seeding doesn't apply
        stateRepo.update(nart.simpleanki.core.domain.streak.StreakState(freezeTokens = 1, lastReconciledDay = 5))
        val mgr = StreakStateManager(stateRepo, logsRepo((1L..5L).toList()), now = { dayMillis(7) }, timeZone = utc)
        mgr.reconcile()
        assertEquals(setOf(6L), stateRepo.get().frozenDays) // day 6 frozen
    }

    @Test
    fun repair_offeredAndRestores() = runTest {
        val stateRepo = StreakStateRepository(FakeStreakStateDao(), now = { 0L })
        stateRepo.update(nart.simpleanki.core.domain.streak.StreakState(lastReconciledDay = 7))
        val mgr = StreakStateManager(stateRepo, logsRepo(listOf(1L, 2, 3, 4, 5, 7)), now = { dayMillis(7) }, timeZone = utc)
        assertNotNull(mgr.repairOffer())
        mgr.repair()
        assertEquals(setOf(6L), stateRepo.get().frozenDays)
    }
}
```
(`reviewLogOn(millis)` builds a `ReviewLog` with `review = millis` and the other FSRS fields defaulted/zeroed — match the `ReviewLog` constructor. If a test helper for this already exists in the repository tests, reuse it; otherwise add a small private builder.)

- [ ] **Step 2: Run to verify failure**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.data.repository.StreakStateManagerTest"`
Expected: COMPILE FAILURE.

- [ ] **Step 3: Implement `StreakStateManager.kt`**:
```kotlin
package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.first
import nart.simpleanki.core.domain.streak.RepairOffer
import nart.simpleanki.core.domain.streak.StreakReconciler
import nart.simpleanki.core.domain.streak.localEpochDay
import java.util.TimeZone

/** Ties the pure [StreakReconciler] to persisted state + review logs. Call [reconcile] on app
 *  foreground and after a session; never inside a Flow. */
class StreakStateManager(
    private val streakStateRepository: StreakStateRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {
    private suspend fun reviewDays(): Set<Long> =
        reviewLogRepository.observeLogs().first().mapTo(mutableSetOf()) { localEpochDay(it.review, timeZone) }

    suspend fun reconcile() {
        val today = localEpochDay(now(), timeZone)
        val state = streakStateRepository.get()
        val updated = StreakReconciler.reconcile(reviewDays(), state, today)
        if (updated != state) streakStateRepository.update(updated)
    }

    suspend fun repairOffer(): RepairOffer? {
        val today = localEpochDay(now(), timeZone)
        return StreakReconciler.repairEligibility(reviewDays(), streakStateRepository.get(), today)
    }

    suspend fun repair() {
        val today = localEpochDay(now(), timeZone)
        streakStateRepository.update(StreakReconciler.repair(streakStateRepository.get(), today))
    }
}
```

- [ ] **Step 4: Update `StreakProvider`** to union frozen days. Constructor gains `streakStateRepository`:
```kotlin
class StreakProvider(
    private val reviewLogRepository: ReviewLogRepository,
    private val streakStateRepository: StreakStateRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {
    fun observeStreak(): Flow<Streak> =
        combine(reviewLogRepository.observeLogs(), streakStateRepository.observe()) { logs, state ->
            val days = logs.mapTo(mutableSetOf()) { localEpochDay(it.review, timeZone) }
            days += state.frozenDays
            StreakCalculator.compute(days, localEpochDay(now(), timeZone))
        }

    suspend fun streakIncludingToday(): Streak {
        val today = localEpochDay(now(), timeZone)
        val days = reviewLogRepository.observeLogs().first()
            .mapTo(mutableSetOf()) { localEpochDay(it.review, timeZone) }
        days += streakStateRepository.observe().first().frozenDays
        days += today
        return StreakCalculator.compute(days, today)
    }
}
```
(Add `import kotlinx.coroutines.flow.combine`.)

**IMPORTANT — fix the broken call site:** `StudyViewModel.kt:57` currently defaults
`private val streakProvider: StreakProvider = StreakProvider(reviewLogRepository, now)`, which no
longer compiles after this constructor change. Fix it by **removing the inline default** and making
it an injected param:
```kotlin
    private val streakProvider: StreakProvider,
```
Then:
- In `AppModule.kt`, the `StudyViewModel(...)` Koin block must pass `streakProvider = get()`.
- In `StudyViewModelTest`, every `StudyViewModel(...)` construction that relied on the default must
  now pass `streakProvider = StreakProvider(reviewLogRepository, StreakStateRepository(FakeStreakStateDao()), now = { now })`
  (build it from the same fake review-log repo the test already uses).
`StudyQueueViewModel` already takes `streakProvider` as a required injected param (no inline default),
so only its Koin call resolves `get()` — unchanged.

- [ ] **Step 5: Extend `StreakProviderTest`** — a freeze fills a gap so the streak survives:
```kotlin
    @Test
    fun frozenDay_fillsGap_soStreakSurvives() = runTest {
        // logs on civil days 1..5, today = 7, frozen day 6 → run alive through today-1.
        val logs = ReviewLogRepository(FakeReviewLogDao())
        listOf(1L, 2, 3, 4, 5).forEach { d -> logs.append("c1", reviewLogOn(d * 86_400_000L + 43_200_000L)) }
        val stateRepo = StreakStateRepository(FakeStreakStateDao(), now = { 0L })
        stateRepo.update(StreakState(frozenDays = setOf(6L)))
        val provider = StreakProvider(logs, stateRepo, now = { 7L * 86_400_000L + 43_200_000L },
            timeZone = TimeZone.getTimeZone("UTC"))
        assertEquals(6, provider.observeStreak().first().current)
    }
```
(Reuse the existing `StreakProviderTest` constructions — they must now pass a `StreakStateRepository(FakeStreakStateDao())` as the new 2nd arg.)

- [ ] **Step 6: Koin** (`AppModule.kt`): change `single { StreakProvider(get()) }` → `single { StreakProvider(get(), get()) }`; add `single { StreakStateManager(get(), get()) }`.

- [ ] **Step 7: Run the tests + full suite**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; new manager/provider tests pass; existing `StreakProviderTest` updated and green.

- [ ] **Step 8: Commit**
```bash
git add app/src/main/java/nart/simpleanki/core/data/repository/StreakStateManager.kt \
        app/src/main/java/nart/simpleanki/core/data/repository/StreakProvider.kt \
        app/src/main/java/nart/simpleanki/feature/study/StudyViewModel.kt \
        app/src/main/java/nart/simpleanki/di/AppModule.kt \
        app/src/test/java/nart/simpleanki/core/data/repository/StreakStateManagerTest.kt \
        app/src/test/java/nart/simpleanki/core/data/repository/StreakProviderTest.kt \
        app/src/test/java/nart/simpleanki/feature/study/StudyViewModelTest.kt
git commit -m "Add StreakStateManager and union freezes into StreakProvider"
```

---

## Task 5: ViewModels + UI (freeze chip + repair banner)

**Files:**
- Modify: `feature/queue/StudyQueueViewModel.kt`, `feature/queue/StudyQueueScreen.kt`, `feature/study/StudyViewModel.kt`, `feature/study/SessionSummary.kt`, `di/AppModule.kt`
- Test: extend `StudyQueueViewModelTest.kt`

- [ ] **Step 1: Write the failing VM test** (append to `StudyQueueViewModelTest.kt`): with a fake setup where `freezeTokens > 0` and a repair is eligible, `uiState.freezeCount` and `uiState.repairOffer` are exposed. (Construct the VM with a `StreakStateManager`/`StreakProvider` over fakes pre-seeded so a freeze token exists and yesterday is a single gap with today studied; assert `freezeCount` and `repairOffer != null`.) Mirror the existing streak test in this file for wiring.

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.queue.StudyQueueViewModelTest"` → COMPILE FAILURE.

- [ ] **Step 2: `StudyQueueViewModel`** — inject `StreakStateManager`; add `freezeCount: Int = 0` and `repairOffer: RepairOffer? = null` to `StudyQueueUiState`; call `streakStateManager.reconcile()` in `init` (in a `viewModelScope.launch`), then fold the streak-state flow + a computed repair offer into the state (extend the existing outer `combine` that already adds streak, or add another `combine` stage reading `streakStateRepository.observe()` for `freezeCount` and a one-shot `repairOffer()` after reconcile). Add `fun repairStreak()` → `viewModelScope.launch { streakStateManager.repair(); refresh repairOffer/streak }`.

- [ ] **Step 3: `StudyQueueScreen`** — in the header `actions` next to the 🔥 chip, when `state.freezeCount > 0` show `Text("❄️ ${state.freezeCount}")` styled like the streak chip. Add a repair banner item at the top of the `LazyColumn` when `state.repairOffer != null`: a `Card`/`Row` "Repair your ${state.repairOffer.restoredStreak}-day streak" + an `OutlinedButton("Repair", onClick = onRepair)`. Thread `onRepair: () -> Unit = {}` through `StudyQueueScreen`/`StudyQueueContent` to `viewModel::repairStreak`.

- [ ] **Step 4: `StudyViewModel` + `SessionSummary`** — inject `StreakStateManager`; on the session-finish path call `streakStateManager.reconcile()` before reading the streak; add `freezeCount: Int` and `repairOffer: RepairOffer?` to `StudyUiState`; expose them; add `fun repairStreak()`. In `SessionSummary`, when `state.repairOffer != null` show the same repair affordance, and show `"❄️ ${state.freezeCount}"` near the streak badge. (A "freeze used" line is optional polish — show `"❄️ Freeze used — streak safe"` when the reconcile added a frozen day this cycle; if detecting that is awkward, omit it and just show the freeze count. Keep scope tight.)

- [ ] **Step 5: Koin** — add `streakStateManager = get()` to the `StudyQueueViewModel` and `StudyViewModel` provider blocks.

- [ ] **Step 6: Verify** — VM test + compile both targets + assemble:

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug`
Expected: BUILD SUCCESSFUL; the VM test passes.

- [ ] **Step 7: Commit**
```bash
git add app/src/main/java/nart/simpleanki/feature/queue app/src/main/java/nart/simpleanki/feature/study \
        app/src/main/java/nart/simpleanki/di/AppModule.kt \
        app/src/test/java/nart/simpleanki/feature/queue/StudyQueueViewModelTest.kt
git commit -m "Surface streak freezes and repair in the queue and session summary"
```

---

## Final verification

- [ ] Full suite + APK: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] (Optional, emulator) Smoke test: build a streak, force a missed day (adjust device date or seed logs), reopen → a freeze is consumed and the 🔥 survives with ❄️ shown; with no freeze, the streak breaks and a "Repair your N-day streak" banner appears after studying today; tap → streak restored.
