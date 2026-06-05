# Streak System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a daily study streak (consecutive days with ≥1 review) on the "Today" home header and the session-complete summary, derived purely from the review logs.

**Architecture:** A pure `StreakCalculator` (current + longest from civil-day indices) + a DST-safe `localEpochDay` bucketing helper feed a shared `StreakProvider` over `ReviewLogRepository.observeLogs()`. `StudyQueueViewModel` exposes a live streak (outer-combine); `StudyViewModel` exposes a race-proof post-session streak. Hard reset on a missed day; derived, not stored.

**Tech Stack:** Kotlin, `Calendar` (minSdk 24 → no `java.time`), Jetpack Compose, Koin, JUnit4 + coroutines-test.

**Build/test prefix:** ALL Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`. Run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

**Spec:** `docs/superpowers/specs/2026-06-05-streak-system-design.md`. **Branch is stacked on `feature/review-log-sync` (PR #15)** — `ReviewLogRepository.observeLogs()` comes from there.

**Note:** new tests are JVM unit tests (run normally). Compose screens are compile-verified + previews (emulator down).

---

### Task 1: `StreakCalculator` + `localEpochDay` (pure core)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/domain/streak/StreakCalculator.kt`
- Create: `app/src/main/java/nart/simpleanki/core/domain/streak/DayBucketing.kt`
- Test: `app/src/test/java/nart/simpleanki/core/domain/streak/StreakCalculatorTest.kt`
- Test: `app/src/test/java/nart/simpleanki/core/domain/streak/DayBucketingTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/nart/simpleanki/core/domain/streak/StreakCalculatorTest.kt`:

```kotlin
package nart.simpleanki.core.domain.streak

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {

    @Test fun empty_isZeroZero() {
        assertEquals(Streak(0, 0), StreakCalculator.compute(emptySet(), today = 100))
    }

    @Test fun onlyToday_isOneOne() {
        assertEquals(Streak(1, 1), StreakCalculator.compute(setOf(100L), today = 100))
    }

    @Test fun consecutiveEndingToday_countsRun() {
        assertEquals(Streak(3, 3), StreakCalculator.compute(setOf(98L, 99L, 100L), today = 100))
    }

    @Test fun studiedYesterdayNotToday_streakStillAlive() {
        // Latest study day is yesterday (99) — streak is alive through today (100).
        assertEquals(Streak(2, 2), StreakCalculator.compute(setOf(98L, 99L), today = 100))
    }

    @Test fun missedAFullDay_currentResetsButLongestPersists() {
        // Studied days 90-94 (run of 5), then nothing until today only -> current 1, longest 5.
        assertEquals(Streak(1, 5), StreakCalculator.compute(setOf(90L, 91L, 92L, 93L, 94L, 100L), today = 100))
    }

    @Test fun neitherTodayNorYesterday_currentZero_longestFromPastRun() {
        // Last study day is 97 (two days ago) -> streak broken; longest run was 96,97 = 2.
        assertEquals(Streak(0, 2), StreakCalculator.compute(setOf(96L, 97L), today = 100))
    }
}
```

Create `app/src/test/java/nart/simpleanki/core/domain/streak/DayBucketingTest.kt`:

```kotlin
package nart.simpleanki.core.domain.streak

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DayBucketingTest {

    private val ny = TimeZone.getTimeZone("America/New_York")

    private fun millis(tz: TimeZone, year: Int, month0: Int, day: Int, hour: Int): Long =
        Calendar.getInstance(tz).apply {
            clear(); set(year, month0, day, hour, 0, 0)
        }.timeInMillis

    @Test fun sameLocalDate_differentTimes_sameIndex() {
        val morning = millis(ny, 2026, Calendar.JUNE, 5, 8)
        val evening = millis(ny, 2026, Calendar.JUNE, 5, 23)
        assertEquals(localEpochDay(morning, ny), localEpochDay(evening, ny))
    }

    @Test fun consecutiveDates_differByOne() {
        val d1 = millis(ny, 2026, Calendar.JUNE, 5, 12)
        val d2 = millis(ny, 2026, Calendar.JUNE, 6, 9)
        assertEquals(localEpochDay(d1, ny) + 1, localEpochDay(d2, ny))
    }

    @Test fun acrossDstSpringForward_stillDiffersByOne() {
        // US DST spring-forward 2026 is Sun Mar 8 (a 23-hour day). The civil-day index must still +1.
        val before = millis(ny, 2026, Calendar.MARCH, 8, 12)
        val after = millis(ny, 2026, Calendar.MARCH, 9, 12)
        assertEquals(localEpochDay(before, ny) + 1, localEpochDay(after, ny))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail (do not compile)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.streak.*"`
Expected: FAIL — `Streak`, `StreakCalculator`, `localEpochDay` don't exist.

- [ ] **Step 3a: Implement `StreakCalculator`**

Create `app/src/main/java/nart/simpleanki/core/domain/streak/StreakCalculator.kt`:

```kotlin
package nart.simpleanki.core.domain.streak

/** A study streak in days. */
data class Streak(val current: Int, val longest: Int)

object StreakCalculator {
    /**
     * [reviewDays] = civil-day indices on which ≥1 review happened; [today] = today's civil-day index.
     * Pure (no timezone logic — bucket with [localEpochDay] first).
     * - current: if the user studied today, or studied yesterday (the streak is still alive through
     *   today), the length of the consecutive run ending there; otherwise 0 (hard reset).
     * - longest: the longest consecutive run anywhere in the set.
     */
    fun compute(reviewDays: Set<Long>, today: Long): Streak {
        if (reviewDays.isEmpty()) return Streak(0, 0)

        var longest = 1
        var run = 1
        var prev: Long? = null
        for (day in reviewDays.toSortedSet()) {
            run = if (prev != null && day == prev + 1) run + 1 else 1
            if (run > longest) longest = run
            prev = day
        }

        val anchor = when {
            reviewDays.contains(today) -> today
            reviewDays.contains(today - 1) -> today - 1
            else -> return Streak(0, longest)
        }
        var current = 0
        var day = anchor
        while (reviewDays.contains(day)) {
            current++
            day--
        }
        return Streak(current, longest)
    }
}
```

- [ ] **Step 3b: Implement `localEpochDay`**

Create `app/src/main/java/nart/simpleanki/core/domain/streak/DayBucketing.kt`:

```kotlin
package nart.simpleanki.core.domain.streak

import java.util.Calendar
import java.util.TimeZone

/**
 * Civil-day number for [millis] in [timeZone] — a DST-safe day index where consecutive calendar
 * dates always differ by exactly 1. Reads the local Y/M/D, then rebuilds them as a UTC midnight and
 * divides by a day, so 23h/25h DST days don't shift the index. (minSdk 24 rules out java.time.)
 */
fun localEpochDay(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
    val local = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
    val utcMidnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }
    return utcMidnight.timeInMillis / 86_400_000L
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.domain.streak.*"`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/domain/streak/ app/src/test/java/nart/simpleanki/core/domain/streak/
git commit -m "Add StreakCalculator and DST-safe day bucketing"
```

---

### Task 2: `StreakProvider`

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/data/repository/StreakProvider.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Test: `app/src/test/java/nart/simpleanki/core/data/repository/StreakProviderTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/nart/simpleanki/core/data/repository/StreakProviderTest.kt`:

```kotlin
package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.data.local.ReviewLogEntity
import nart.simpleanki.core.domain.streak.Streak
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class StreakProviderTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L
    private val today = 1_700_000_000_000L  // some instant; we anchor everything off it

    private fun logEntity(id: String, reviewMillis: Long) = ReviewLogEntity(
        id = id, cardId = "c1", rating = 3, state = 2, due = 0, stability = 1.0, difficulty = 5.0,
        elapsedDays = 0.0, lastElapsedDays = 0.0, scheduledDays = 0.0, review = reviewMillis, dirty = false,
    )

    private fun provider(dao: FakeReviewLogDao) =
        StreakProvider(ReviewLogRepository(dao), now = { today }, timeZone = utc)

    @Test
    fun observeStreak_countsConsecutiveDaysEndingToday() = runTest {
        val dao = FakeReviewLogDao()
        // today, yesterday, day-before — 3 consecutive (multiple logs on one day collapse to one day).
        dao.insertAll(listOf(
            logEntity("a", today),
            logEntity("b", today),          // same day, still counts once
            logEntity("c", today - day),
            logEntity("d", today - 2 * day),
        ))
        val streak = provider(dao).observeStreak().first()
        assertEquals(Streak(3, 3), streak)
    }

    @Test
    fun observeStreak_noLogs_isZero() = runTest {
        assertEquals(Streak(0, 0), provider(FakeReviewLogDao()).observeStreak().first())
    }

    @Test
    fun streakIncludingToday_countsTodayEvenIfNotYetLogged() = runTest {
        val dao = FakeReviewLogDao()
        dao.insertAll(listOf(logEntity("y", today - day)))  // only yesterday logged
        // observeStreak sees yesterday only (alive) -> current 1; including-today forces today -> 2.
        assertEquals(Streak(1, 1), provider(dao).observeStreak().first())
        assertEquals(Streak(2, 2), provider(dao).streakIncludingToday())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (does not compile)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.data.repository.StreakProviderTest"`
Expected: FAIL — `unresolved reference: StreakProvider`.

- [ ] **Step 3: Implement `StreakProvider`**

Create `app/src/main/java/nart/simpleanki/core/data/repository/StreakProvider.kt`:

```kotlin
package nart.simpleanki.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import nart.simpleanki.core.domain.streak.Streak
import nart.simpleanki.core.domain.streak.StreakCalculator
import nart.simpleanki.core.domain.streak.localEpochDay
import java.util.TimeZone

/** Derives the study [Streak] from the review logs. Stateless — pure derivation, nothing stored. */
class StreakProvider(
    private val reviewLogRepository: ReviewLogRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {
    /** Live streak for the home header — reacts to new review logs. */
    fun observeStreak(): Flow<Streak> =
        reviewLogRepository.observeLogs().map { logs ->
            val days = logs.mapTo(mutableSetOf()) { localEpochDay(it.review, timeZone) }
            StreakCalculator.compute(days, localEpochDay(now(), timeZone))
        }

    /**
     * Streak treating today as studied — for the post-session summary, so it's correct even though
     * the per-rating log append is fire-and-forget and may not have landed yet.
     */
    suspend fun streakIncludingToday(): Streak {
        val today = localEpochDay(now(), timeZone)
        val days = reviewLogRepository.observeLogs().first()
            .mapTo(mutableSetOf()) { localEpochDay(it.review, timeZone) }
            .apply { add(today) }
        return StreakCalculator.compute(days, today)
    }
}
```

- [ ] **Step 4: Register in DI**

In `app/src/main/java/nart/simpleanki/di/AppModule.kt`, add after `single { ReviewLogRepository(get()) }`:

```kotlin
    single { StreakProvider(get()) }
```

Add `import nart.simpleanki.core.data.repository.StreakProvider` only if the file imports repositories individually (check first; skip if same-package/wildcard).

- [ ] **Step 5: Run the test + compile**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.data.repository.StreakProviderTest" :app:compileDebugKotlin`
Expected: PASS (3 tests) + BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/data/repository/StreakProvider.kt app/src/main/java/nart/simpleanki/di/AppModule.kt app/src/test/java/nart/simpleanki/core/data/repository/StreakProviderTest.kt
git commit -m "Add StreakProvider deriving streak from review logs"
```

---

### Task 3: Wire streak into `StudyQueueViewModel` (Today home)

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/queue/StudyQueueViewModel.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/queue/StudyQueueViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

In `StudyQueueViewModelTest.kt`, add these imports if missing:

```kotlin
import nart.simpleanki.core.data.local.ReviewLogEntity
import nart.simpleanki.core.data.repository.FakeReviewLogDao
import nart.simpleanki.core.data.repository.ReviewLogRepository
import nart.simpleanki.core.data.repository.StreakProvider
import java.util.TimeZone
```

Add a helper inside the class (an empty-streak provider for the existing tests that don't care):

```kotlin
    private fun emptyStreak() = StreakProvider(ReviewLogRepository(FakeReviewLogDao()))
```

Add this new test (uses a UTC-anchored provider seeded with two consecutive days ending "today"):

```kotlin
    @Test
    fun currentStreak_reflectsConsecutiveReviewDays() = runTest {
        val day = 86_400_000L
        val logDao = FakeReviewLogDao()
        logDao.insertAll(listOf(
            ReviewLogEntity("a", "c1", 3, 2, 0, 1.0, 5.0, 0.0, 0.0, 0.0, now, false),
            ReviewLogEntity("b", "c1", 3, 2, 0, 1.0, 5.0, 0.0, 0.0, 0.0, now - day, false),
        ))
        val streak = StreakProvider(ReviewLogRepository(logDao), now = { now }, timeZone = TimeZone.getTimeZone("UTC"))

        val vm = StudyQueueViewModel(
            CardRepository(FakeCardDao(), now = { now }),
            DeckRepository(FakeDeckDao(), now = { now }),
            FolderRepository(FakeFolderDao(), now = { now }),
            FakeSettingsRepository(), FakeEntitlementRepository(), streak, now = { now },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertEquals(2, vm.uiState.value.currentStreak)
    }
```

(Adapt the `ReviewLogEntity(...)` positional args to the real constructor field order if it differs — it is `id, cardId, rating, state, due, stability, difficulty, elapsedDays, lastElapsedDays, scheduledDays, review, dirty`.)

- [ ] **Step 2: Update every existing `StudyQueueViewModel(...)` construction**

In `StudyQueueViewModelTest.kt`, every existing `StudyQueueViewModel(...)` call needs the new `streakProvider` argument (it goes after `entitlementRepository`/`FakeEntitlementRepository()` and before `now = { ... }`). For each existing call shaped like
`StudyQueueViewModel(cardRepo, deckRepo, FolderRepository(...), FakeSettingsRepository(), FakeEntitlementRepository(), now = { now })`,
insert `emptyStreak(), ` before `now = { now }`:
`StudyQueueViewModel(cardRepo, deckRepo, FolderRepository(...), FakeSettingsRepository(), FakeEntitlementRepository(), emptyStreak(), now = { now })`.
Search the file for every `StudyQueueViewModel(` and apply.

- [ ] **Step 3: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.queue.StudyQueueViewModelTest"`
Expected: FAIL to compile — `StudyQueueViewModel` has no `streakProvider` parameter / `currentStreak` not on `StudyQueueUiState`.

- [ ] **Step 4a: Add the state fields + constructor param + outer combine**

In `StudyQueueViewModel.kt`:

Add to `StudyQueueUiState` (after `studiedToday`):

```kotlin
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
```

Add the import `import nart.simpleanki.core.data.repository.StreakProvider`. Add the constructor parameter after `entitlementRepository` and before `now`:

```kotlin
    private val entitlementRepository: EntitlementRepository,
    private val streakProvider: StreakProvider,
    private val now: () -> Long = { System.currentTimeMillis() },
```

Now wrap the existing `combine(...) { ... }.stateIn(...)`. Change the existing assignment so the 5-input `combine` becomes a private `baseState` (drop its `.stateIn(...)`), then add an outer combine that folds in the streak. Concretely, the current code is:

```kotlin
    val uiState: StateFlow<StudyQueueUiState> =
        combine(
            cardRepository.observeAllCards().withDueTicks(now),
            deckRepository.observeDecks(),
            folderRepository.observeFolders(),
            settingsRepository.settings,
            entitlementRepository.entitlement,
        ) { (cards, nowMillis), decks, folders, settings, entitlement ->
            ... // unchanged body producing StudyQueueUiState(...)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StudyQueueUiState(),
        )
```

Change it to:

```kotlin
    private val baseState: Flow<StudyQueueUiState> =
        combine(
            cardRepository.observeAllCards().withDueTicks(now),
            deckRepository.observeDecks(),
            folderRepository.observeFolders(),
            settingsRepository.settings,
            entitlementRepository.entitlement,
        ) { (cards, nowMillis), decks, folders, settings, entitlement ->
            ... // SAME body, unchanged
        }

    val uiState: StateFlow<StudyQueueUiState> =
        combine(baseState, streakProvider.observeStreak()) { base, streak ->
            base.copy(currentStreak = streak.current, longestStreak = streak.longest)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StudyQueueUiState(),
        )
```

Add the import `import kotlinx.coroutines.flow.Flow` if not present. (The inner `combine` body is unchanged — only the surrounding declaration changes.)

- [ ] **Step 4b: Update the Koin registration**

In `AppModule.kt`, the `StudyQueueViewModel { ... }` block currently lists `cardRepository`, `deckRepository`, `folderRepository`, `settingsRepository`, `entitlementRepository`. Add `streakProvider = get()` after `entitlementRepository = get()`:

```kotlin
    viewModel {
        StudyQueueViewModel(
            cardRepository = get(),
            deckRepository = get(),
            folderRepository = get(),
            settingsRepository = get(),
            entitlementRepository = get(),
            streakProvider = get(),
        )
    }
```

(If the existing block uses positional `get()`s, add one more `get()` in the streakProvider slot instead.)

- [ ] **Step 5: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.queue.StudyQueueViewModelTest"`
Expected: PASS (new + all pre-existing tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/queue/StudyQueueViewModel.kt app/src/main/java/nart/simpleanki/di/AppModule.kt app/src/test/java/nart/simpleanki/feature/queue/StudyQueueViewModelTest.kt
git commit -m "Expose live streak in StudyQueueViewModel"
```

---

### Task 4: Wire streak into `StudyViewModel` (session summary)

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/study/StudyViewModel.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/study/StudyViewModelTest.kt`

This uses a **defaulted** `streakProvider` param (derived from the already-injected `reviewLogRepository`), so the existing `StudyViewModel(...)` test call sites do NOT need changes.

- [ ] **Step 1: Write the failing test**

In `StudyViewModelTest.kt`, add this test (model the study/flip/rate idiom on the existing `rating_appendsOneReviewLog_withCardIdAndRating` test from PR #15; adapt helper names):

```kotlin
    @Test
    fun finishingSession_setsCurrentStreakAtLeastOne() = runTest {
        val cardRepo = CardRepository(FakeCardDao(), now = { now })
        cardRepo.upsert(newCard("c1", deckId = "d1"))

        val vm = StudyViewModel(
            "d1", null, cardRepo, DeckRepository(FakeDeckDao(), now = { now }),
            FakeSettingsRepository(), ReviewLogRepository(FakeReviewLogDao()), now = { now },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        vm.onReveal()
        vm.onRate(Rating.Good)   // last (only) card -> session finishes
        runCurrent()

        assertTrue(vm.uiState.value.finished)
        // streakIncludingToday forces today in, so the just-finished session yields >= 1.
        assertEquals(1, vm.uiState.value.currentStreak)
    }
```

Add the import `import org.junit.Assert.assertTrue` if missing (`FakeReviewLogDao`/`ReviewLogRepository` were imported in PR #15's Task 4).

- [ ] **Step 2: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.study.StudyViewModelTest"`
Expected: FAIL — `currentStreak` not on `StudyUiState`.

- [ ] **Step 3a: Add state fields + defaulted provider + finish wiring**

In `StudyViewModel.kt`:

Add to `StudyUiState` (after `durationMillis`):

```kotlin
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
```

Add the import `import nart.simpleanki.core.data.repository.StreakProvider`. Add a **defaulted** constructor parameter — it must come AFTER `reviewLogRepository` and `now` (its default references both), so place it right after `now`:

```kotlin
    private val reviewLogRepository: ReviewLogRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val streakProvider: StreakProvider = StreakProvider(reviewLogRepository, now),
    private val logManager: LogManager = LogManager(emptyList()),
```

Add a private helper that fills the summary streak (race-proof — today forced in):

```kotlin
    private fun refreshSummaryStreak() = viewModelScope.launch {
        val s = streakProvider.streakIncludingToday()
        _uiState.value = _uiState.value.copy(currentStreak = s.current, longestStreak = s.longest)
    }
```

Call `refreshSummaryStreak()` at BOTH finish points:
- In `load()`, right after the `_uiState.value = StudyUiState(... finished = queue.isEmpty() ...)` assignment, add: `if (queue.isEmpty()) refreshSummaryStreak()`.
- In `onRate()`, right after the `_uiState.value = prev.copy(... finished = next == null ...)` assignment, add: `if (next == null) refreshSummaryStreak()`.

- [ ] **Step 3b: Update the Koin registration to use the shared provider**

In `AppModule.kt`, in the `StudyViewModel` `viewModel { params -> ... }` block, add `streakProvider = get(),` (after `reviewLogRepository = get(),`) so production uses the singleton rather than a per-VM default:

```kotlin
        StudyViewModel(
            deckId = args.deckId,
            folderId = args.folderId,
            cardRepository = get(),
            deckRepository = get(),
            settingsRepository = get(),
            reviewLogRepository = get(),
            streakProvider = get(),
            logManager = get(),
        )
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.study.StudyViewModelTest"`
Expected: PASS (new + all pre-existing tests — existing call sites compile because `streakProvider` is defaulted).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/study/StudyViewModel.kt app/src/main/java/nart/simpleanki/di/AppModule.kt app/src/test/java/nart/simpleanki/feature/study/StudyViewModelTest.kt
git commit -m "Expose post-session streak in StudyViewModel"
```

---

### Task 5: Display — Today header chip + session-summary badge

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/queue/StudyQueueScreen.kt`
- Modify: `app/src/main/java/nart/simpleanki/feature/study/SessionSummary.kt`

Build-verified + previews.

- [ ] **Step 1: Add the Today-header streak chip**

In `StudyQueueScreen.kt`, the `TopAppBar` has `title = { Text("Today", fontWeight = FontWeight.Bold) }`. Add an `actions` slot showing the streak when > 0. Add these imports if missing: `androidx.compose.foundation.layout.Row`, `androidx.compose.foundation.layout.Arrangement`, `androidx.compose.foundation.layout.padding`, `androidx.compose.ui.Alignment`, `androidx.compose.ui.unit.dp`, `androidx.compose.ui.unit.sp`. In the `TopAppBar(...)`, add (alongside the existing `title =`/`colors =` args):

```kotlin
                actions = {
                    if (state.currentStreak > 0) {
                        Row(
                            modifier = Modifier.padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("🔥", fontSize = 18.sp)
                            Text(
                                state.currentStreak.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
```

(If the `TopAppBar` already has an `actions = { ... }`, add the `if (state.currentStreak > 0) { ... }` block inside it instead.)

- [ ] **Step 2: Add the session-summary streak badge**

In `SessionSummary.kt`, remove the "streak omitted" note in the file's top comment (change `(streak omitted)` → `(with streak badge)`), and add a badge after the `RatingDistributionBar` block and before the trailing `Spacer(Modifier.weight(1f))`. Insert:

```kotlin
        if (state.currentStreak > 0) {
            Spacer(Modifier.height(24.dp))
            StreakBadge(current = state.currentStreak, longest = state.longestStreak)
        }
```

Then add the private composable (near the other private composables like `SessionStatsRow`):

```kotlin
@Composable
private fun StreakBadge(current: Int, longest: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "🔥 $current day${if (current == 1) "" else "s"} streak",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF9500),
        )
        if (longest > current) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Longest: $longest",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

Ensure these imports exist in `SessionSummary.kt` (add any missing): `androidx.compose.foundation.layout.Column`, `androidx.compose.foundation.layout.Spacer`, `androidx.compose.foundation.layout.height`, `androidx.compose.material3.Text`, `androidx.compose.material3.MaterialTheme`, `androidx.compose.runtime.Composable`, `androidx.compose.ui.Alignment`, `androidx.compose.ui.graphics.Color`, `androidx.compose.ui.text.font.FontWeight`, `androidx.compose.ui.unit.dp`.

- [ ] **Step 3: Update previews**

In `SessionSummary.kt`, find the existing `@Preview` that builds a `StudyUiState(...)` and add `currentStreak = 7, longestStreak = 12` to it so the badge renders in the preview. In `StudyQueueScreen.kt`, find a preview building `StudyQueueUiState(...)` and add `currentStreak = 7` so the header chip renders.

- [ ] **Step 4: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/queue/StudyQueueScreen.kt app/src/main/java/nart/simpleanki/feature/study/SessionSummary.kt
git commit -m "Show streak in Today header and session summary"
```

---

## Final verification

- [ ] **Run the full app unit-test suite (no regressions)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Build the debug APK end-to-end**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Manual smoke (when an emulator is available)**

- Study at least one card today → the "Today" header shows "🔥 1"; finishing the session shows a "🔥 1 day streak" badge in the summary.
- Study again the next day → header shows "🔥 2".
- (If you can backdate review logs) a gap of a full day resets the header to 0 (chip hidden) while the session summary still reflects today after studying.
