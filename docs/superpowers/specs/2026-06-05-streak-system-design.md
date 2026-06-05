# Streak System — Design

**Date:** 2026-06-05
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/streak-system` — **stacked on `feature/review-log-sync` (PR #15)**, which
provides `ReviewLogRepository.observeLogs()`. Retarget the streak PR onto `main` once #15 merges.
**Sub-project 2 of 2** (sub-project 1 = review-log persistence + sync, PR #15).

## Goal

Show a daily study **streak** — the number of consecutive days the user has studied — on the
"Today" home header and in the session-complete summary. A streak day = **any review (≥1 card
rated)**. Missing a full day resets the streak to 0, framed encouragingly. The streak is derived
purely from the review logs persisted in sub-project 1.

## Decisions (from brainstorming + research)

- **Streak day = any review ≥1 card** (low barrier; works regardless of the daily-goal setting).
- **Hard reset, encouraging framing** — a full missed day resets to 0; no streak-freeze/grace in
  v1 (deferred). The streak stays "alive" through today until local midnight.
- **Display:** Today home header (🔥 + count) and the session-summary badge (the reserved
  `// streak omitted` slot). NOT the profile screen.
- **Derived, not stored.** No new persistence — the streak is recomputed from review-log days. (No
  streak-freeze means no freeze state to persist.)
- **Longest streak**: computed (free from the same scan); shown as a small secondary line in the
  session-summary badge only. Home header shows just the current 🔥.
- **Milestones**: v1 is a plain "🔥 N day streak" — no 7/30/100 celebration (deferred to v2).

## Key technical constraints

- **minSdk 24, no core-library desugaring → `java.time` is unavailable.** Day bucketing uses
  `Calendar` (like the existing `startOfDay` helpers).
- **DST-safe bucketing:** naive `millis / 86_400_000` mis-buckets on 23h/25h days. Instead compute a
  **civil-day number**: read local `YEAR/MONTH/DAY_OF_MONTH` via `Calendar`, rebuild a UTC midnight
  from those, divide by a day. Consecutive calendar dates then always differ by exactly 1.

## Components

### 1. `core/domain/streak/StreakCalculator.kt` (new — pure, JVM-testable)

```kotlin
data class Streak(val current: Int, val longest: Int)

object StreakCalculator {
    /**
     * [reviewDays] = the set of civil-day indices on which ≥1 review happened; [today] = today's
     * civil-day index. Pure — no timezone logic here.
     * - current: if the latest study day is today or yesterday (streak still alive through today),
     *   the length of the consecutive run ending there; else 0 (hard reset on a full missed day).
     * - longest: the longest consecutive run anywhere in the set.
     */
    fun compute(reviewDays: Set<Long>, today: Long): Streak
}
```

### 2. `core/domain/streak/DayBucketing.kt` (new — the only timezone-aware piece)

```kotlin
/** Civil-day number for [millis] in [timeZone]: DST-safe day index (consecutive dates differ by 1). */
fun localEpochDay(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long
```
Implementation: a `Calendar` in `timeZone` reads `YEAR/MONTH/DAY_OF_MONTH`; rebuild those as a UTC
`Calendar` at midnight; return `utcMidnightMillis / 86_400_000`.

### 3. `core/data/repository/StreakProvider.kt` (new — shared derivation)

```kotlin
class StreakProvider(
    private val reviewLogRepository: ReviewLogRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {
    fun observeStreak(): Flow<Streak> =
        reviewLogRepository.observeLogs().map { logs ->
            val days = logs.mapTo(mutableSetOf()) { localEpochDay(it.review, timeZone) }
            StreakCalculator.compute(days, localEpochDay(now(), timeZone))
        }

    /** Streak treating today as studied — for the post-session summary (race-proof vs. the async
     *  log append). */
    suspend fun streakIncludingToday(): Streak {
        val today = localEpochDay(now(), timeZone)
        val days = reviewLogRepository.observeLogs().first()
            .mapTo(mutableSetOf()) { localEpochDay(it.review, timeZone) }
            .apply { add(today) }
        return StreakCalculator.compute(days, today)
    }
}
```
Registered as a Koin `single`, injected into both view models. Keeps the derivation logic in one
place.

### 4. `feature/queue/StudyQueueViewModel` (modify) — Today home

`StudyQueueUiState` gains `currentStreak: Int = 0` and `longestStreak: Int = 0`. Rather than grow the
existing 5-input `combine`, wrap it: keep the current `combine(...)` producing the base state, then
`combine(baseState, streakProvider.observeStreak()) { s, streak -> s.copy(currentStreak =
streak.current, longestStreak = streak.longest) }.stateIn(...)`. (Avoids the 6-arg combine arity
problem and leaves the existing block untouched.)

### 5. `feature/study/StudyViewModel` (modify) — session summary

`StudyUiState` gains `currentStreak: Int = 0` and `longestStreak: Int = 0`. When the session
finishes (the `next == null` branch in `onRate`, and the empty-queue path in `load()`), set them
from `streakProvider.streakIncludingToday()` (today forced in, so it's correct even though the log
append is fire-and-forget). Inject `StreakProvider`; update the Koin `StudyViewModel` block and
`StudyViewModelTest` constructions.

### 6. Display

- **`feature/queue/StudyQueueScreen` (Today header):** a small 🔥 + `currentStreak` chip near the
  "Today" title, rendered only when `currentStreak > 0`.
- **`feature/study/SessionSummary` (badge):** in the reserved slot (after the stats row, before
  Finish), a `StreakBadge` showing "🔥 {currentStreak} day streak" (singular "day" when 1), plus a
  small "Longest: {longestStreak}" line when `longestStreak > currentStreak`. Hidden when
  `currentStreak == 0`. Remove the `// streak omitted` note.

## Data flow

Rate cards → review logs accrue (PR #15) → `StreakProvider.observeStreak()` re-derives `Streak` from
the distinct civil-days → Today header shows 🔥 N live; on finishing a session,
`streakIncludingToday()` feeds the summary badge.

## Error handling

Pure derivation over local data — no I/O, no failure modes. Empty logs → `Streak(0, 0)` → both
surfaces hide the streak. DST-safe by construction. The `streakIncludingToday()` union guarantees
the summary never shows a stale 0 right after the user studied.

## Testing

- **`StreakCalculatorTest`** (JVM, core): empty → 0/0; only-today → 1/1; N consecutive ending today
  → N; studied yesterday but not today (alive) → run still counts; a gap zeroes `current` but
  `longest` reflects the earlier run; `longest` spans a past run longer than the current.
- **`DayBucketingTest`** (JVM): two times on the same local date → same index; consecutive dates →
  differ by 1; a date around a DST transition still yields a +1 neighbor (use an explicit
  `TimeZone`, e.g. `America/New_York`, so the test is deterministic).
- **`StudyQueueViewModelTest`**: with fake logs across N consecutive days (controlled `now`/`TimeZone`
  via the injected `StreakProvider`), `uiState.currentStreak == N`; no logs → 0.
- **`StudyViewModelTest`**: finishing a session sets `currentStreak >= 1` (today counted) via the
  `streakIncludingToday` path.
- **Display**: build-verified + `@Preview`s (Today header chip at 7; `StreakBadge` at 1, at 7 with a
  longer "Longest", and the hidden-at-0 case).

**Build/test prefix:** Gradle commands MUST be prefixed with
`export JAVA_HOME=/opt/homebrew/opt/openjdk &&`, run from
`/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`. New tests are JVM unit tests; the
emulator is unavailable, so Compose screens are compile-verified + previews only.

## Out of scope (v1)

Streak-freeze / grace days; milestone celebrations (7/30/100); a profile-screen streak; a
calendar/heatmap; notifications about an at-risk streak; persisting streak counters (it's derived);
any change to review-log persistence or sync (sub-project 1).
