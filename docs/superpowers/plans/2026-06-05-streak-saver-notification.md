# Streak-Saver Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an automatic evening "streak-saver" notification that fires only when the study streak is at risk tonight and isn't freeze-protected.

**Architecture:** A third `ReminderType.StreakSaver` reuses the existing self-rescheduling reminder worker. The pure `reminderContent` gains a `StreakSaver` branch gated by `currentStreak>0 && studiedToday==0 && freezeTokens==0`. The worker reads streak + freeze state and always reschedules at a fixed 20:00; `ensureReminders()` arms it unconditionally. No settings, no UI.

**Tech Stack:** Kotlin, WorkManager (`CoroutineWorker`), Koin, JUnit4.

**Branch:** `feature/streak-saver-notification` — **stacked on `feature/streak-freeze-repair` (PR #19)**, which provides `StreakStateRepository` + the 2-arg `StreakProvider`. Retarget this PR onto `main` once #19 merges.

**Build/test prefix:** ALL Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&` and run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

**Commit rule:** No "claude" mention in commit messages; no Co-Authored-By / attribution trailer. Don't `git add` the unrelated untracked `docs/superpowers/plans/2026-06-04-realtime-study-queue.md`.

---

## File Structure
- `core/notifications/ReminderContent.kt` (modify) — `StreakSaver` enum value, time constants, extended `reminderContent` signature + branch.
- `core/notifications/ReminderWorker.kt` (modify) — inject streak/freeze state, pass into `reminderContent`, `scheduleFor` always-on at fixed time.
- `AzriApplication.kt` (modify) — `ensureReminders()` arms the streak-saver unconditionally.
- `core/notifications/ReminderContentTest.kt` (modify) — pure decision tests.

---

## Task 1: Pure StreakSaver decision

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/notifications/ReminderContent.kt`
- Test: `app/src/test/java/nart/simpleanki/core/notifications/ReminderContentTest.kt`

- [ ] **Step 1: Write the failing tests** — append inside `class ReminderContentTest`:
```kotlin
    @Test
    fun streakSaver_postsWhenAtRiskAndUnprotected() {
        val c = reminderContent(
            ReminderType.StreakSaver, settings, studiedToday = 0, readyCount = 0,
            currentStreak = 5, freezeTokens = 0,
        )!!
        assertEquals("Keep your streak alive", c.title)
        assertTrue(c.body.contains("5-day streak"))
    }

    @Test
    fun streakSaver_skipsWhenAlreadyStudiedToday() {
        assertNull(reminderContent(
            ReminderType.StreakSaver, settings, studiedToday = 1, readyCount = 0,
            currentStreak = 5, freezeTokens = 0,
        ))
    }

    @Test
    fun streakSaver_skipsWhenNoStreak() {
        assertNull(reminderContent(
            ReminderType.StreakSaver, settings, studiedToday = 0, readyCount = 0,
            currentStreak = 0, freezeTokens = 0,
        ))
    }

    @Test
    fun streakSaver_skipsWhenFreezeProtected() {
        assertNull(reminderContent(
            ReminderType.StreakSaver, settings, studiedToday = 0, readyCount = 0,
            currentStreak = 5, freezeTokens = 1,
        ))
    }
```

- [ ] **Step 2: Run to verify failure** (compile error — `StreakSaver` + the new params don't exist)

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.notifications.ReminderContentTest"`
Expected: COMPILE FAILURE.

- [ ] **Step 3: Implement in `ReminderContent.kt`**

Add `StreakSaver` to the enum:
```kotlin
enum class ReminderType(val key: String) {
    Study("reminder_study"),
    Goal("reminder_goal"),
    StreakSaver("reminder_streak_saver"),
}
```
Add the fixed-time constants (top-level in the file, e.g. just below the enum):
```kotlin
/** The streak-saver is automatic (no setting): a fixed evening time. */
const val STREAK_SAVER_HOUR = 20
const val STREAK_SAVER_MINUTE = 0
```
Extend the `reminderContent` signature with two defaulted params (Study/Goal ignore them) and add the branch:
```kotlin
fun reminderContent(
    type: ReminderType,
    settings: AppSettings,
    studiedToday: Int,
    readyCount: Int,
    currentStreak: Int = 0,
    freezeTokens: Int = 0,
): NotificationContent? = when (type) {
    ReminderType.Study -> {
        if (readyCount <= 0) null
        else NotificationContent(
            title = "Time to study",
            body = "You have $readyCount ${cards(readyCount)} ready — a quick session keeps you sharp.",
        )
    }

    ReminderType.Goal -> {
        val remaining = settings.dailyGoalTotal - studiedToday
        if (!settings.dailyGoalEnabled || settings.dailyGoalTotal <= 0 || remaining <= 0) null
        else NotificationContent(
            title = "Daily goal",
            body = "You're $remaining ${cards(remaining)} short of today's goal. A few minutes gets you there.",
        )
    }

    ReminderType.StreakSaver -> {
        // Fire only when a streak exists, today hasn't been studied yet, and no freeze can save it.
        if (currentStreak <= 0 || studiedToday > 0 || freezeTokens > 0) null
        else NotificationContent(
            title = "Keep your streak alive",
            body = "Your $currentStreak-day streak ends at midnight — a quick review saves it.",
        )
    }
}
```
(Keep the existing `private fun cards(n: Int)` helper as-is.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.notifications.ReminderContentTest"`
Expected: PASS (existing Study/Goal tests + the 4 new StreakSaver tests).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/nart/simpleanki/core/notifications/ReminderContent.kt \
        app/src/test/java/nart/simpleanki/core/notifications/ReminderContentTest.kt
git commit -m "Add streak-saver reminder decision"
```

---

## Task 2: Wire the worker + auto-arm on launch

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/notifications/ReminderWorker.kt`
- Modify: `app/src/main/java/nart/simpleanki/AzriApplication.kt`

No unit test (Android-coupled `CoroutineWorker` + `Application`); verified by a compiling build. The decision logic is already covered by Task 1.

- [ ] **Step 1: `ReminderWorker.kt` — inject streak + freeze state and pass into `reminderContent`**

Add imports + injected deps (mirroring the existing `by inject()` lines):
```kotlin
import nart.simpleanki.core.data.repository.StreakProvider
import nart.simpleanki.core.data.repository.StreakStateRepository
```
```kotlin
    private val streakProvider: StreakProvider by inject()
    private val streakStateRepository: StreakStateRepository by inject()
```
In `doWork()`, after the existing `readyCount` line, compute the streak + freeze count and pass them into `reminderContent`:
```kotlin
        val readyCount = StudyQueueBuilder.buildStudyQueue(cards, now, Int.MAX_VALUE, Int.MAX_VALUE).size
        val currentStreak = streakProvider.observeStreak().first().current
        val freezeTokens = streakStateRepository.observe().first().freezeTokens

        reminderContent(type, settings, studiedToday, readyCount, currentStreak, freezeTokens)
            ?.let { notifier.post(type, it) }

        scheduler.schedule(type, hour, minute) // chain tomorrow
        return Result.success()
```
(Replace the existing `reminderContent(type, settings, studiedToday, readyCount)?.let { ... }` call with the 6-arg version above. `kotlinx.coroutines.flow.first` is already imported.)

- [ ] **Step 2: `ReminderWorker.kt` — `scheduleFor` always-on at the fixed time**

Add the `StreakSaver` case to the `AppSettings.scheduleFor(type)` `when` (it has no settings, so it's always enabled at the fixed constant time):
```kotlin
    private fun AppSettings.scheduleFor(type: ReminderType): Schedule = when (type) {
        ReminderType.Study -> Schedule(studyReminderEnabled, studyReminderHour, studyReminderMinute)
        ReminderType.Goal -> Schedule(goalReminderEnabled, goalReminderHour, goalReminderMinute)
        ReminderType.StreakSaver -> Schedule(enabled = true, hour = STREAK_SAVER_HOUR, minute = STREAK_SAVER_MINUTE)
    }
```

- [ ] **Step 3: `AzriApplication.kt` — arm the streak-saver unconditionally**

In `ensureReminders()`, after the Study/Goal blocks, add (import `STREAK_SAVER_HOUR`/`STREAK_SAVER_MINUTE` from `nart.simpleanki.core.notifications`):
```kotlin
            // The streak-saver is automatic (no toggle): always armed at the fixed evening time.
            scheduler.schedule(ReminderType.StreakSaver, STREAK_SAVER_HOUR, STREAK_SAVER_MINUTE)
```
Add the imports:
```kotlin
import nart.simpleanki.core.notifications.STREAK_SAVER_HOUR
import nart.simpleanki.core.notifications.STREAK_SAVER_MINUTE
```

- [ ] **Step 4: Verify compile + full unit suite + APK**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL; all unit tests pass (no behavioral change to Study/Goal).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/nart/simpleanki/core/notifications/ReminderWorker.kt \
        app/src/main/java/nart/simpleanki/AzriApplication.kt
git commit -m "Auto-arm the streak-saver reminder and feed it streak + freeze state"
```

---

## Final verification
- [ ] `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] (Optional, emulator) Smoke test: with a streak alive (studied yesterday, not today) and 0 freezes, run the `ReminderWorker` for `StreakSaver` (or set the device clock to ~20:00) → a "Keep your streak alive" notification appears; with a freeze available, or after studying today, it posts nothing.
