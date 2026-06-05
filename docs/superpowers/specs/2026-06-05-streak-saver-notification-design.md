# Streak-Saver Notification — Design

**Date:** 2026-06-05
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/streak-saver-notification` (off `main`).

## Goal

Add a third daily reminder — a **streak-saver** — that fires in the evening only when the user's
study streak is **at risk of breaking tonight** and isn't already protected by a freeze. This is the
single highest-ROI retention nudge (case studies: ~21% retention lift, ~40% churn reduction), and it
rides the existing reminder infrastructure + the freeze system just shipped.

## Background

The notification system (`core/notifications/`) already has two self-rescheduling daily reminders:
- `ReminderType { Study, Goal }` (each `key` seeds a unique WorkManager name + notification id).
- A **pure** `reminderContent(type, settings, studiedToday, readyCount): NotificationContent?` —
  returns null to post nothing (kept Android-free for unit tests).
- `ReminderWorker` (CoroutineWorker, KoinComponent): reads settings + cards, computes `studiedToday`
  (`fsrsLastReview >= startOfDay`) and `readyCount` (`StudyQueueBuilder`), calls `reminderContent`,
  posts via `Notifier`, then `scheduler.schedule(type, hour, minute)` to chain tomorrow.
- `WorkManagerReminderScheduler` (a unique self-rescheduling `OneTimeWorkRequest` per type).
- Settings: `AppSettings` + `SettingsRepository` (DataStore) hold `studyReminderEnabled/Hour(9)/Minute(0)`
  and `goalReminderEnabled/Hour(20)/Minute(0)`, with combined setters `setStudyReminder(enabled,h,m)`
  / `setGoalReminder(...)`. `FakeSettingsRepository` mirrors them.
- `AzriApplication.ensureReminders()` re-arms enabled reminders on launch.
- UI: `NotificationsScreen` (two Switch + `TimePicker` rows) backed by `NotificationsViewModel`
  (`setStudy`/`setGoal`).
- Just shipped: `StreakProvider.observeStreak(): Flow<Streak>` (`.current`) and
  `StreakStateRepository.observe(): Flow<StreakState>` (`.freezeTokens`).

## Decisions (from brainstorming)

- **Fire condition:** post when `currentStreak > 0` **and** `studiedToday == 0` **and**
  `freezeTokens == 0`. (Studied yesterday → streak alive; not studied today → lapses at midnight; no
  freeze → a freeze can't save it.)
- **Skip when freeze-protected** (`freezeTokens == 0` is required to fire) — don't nag a safe streak.
- **Fire regardless of due count** — any review (incl. cramming) keeps the streak, so don't gate on
  `readyCount`.
- **Matches the existing reminders otherwise:** an opt-in toggle + configurable time, default **20:00**,
  default **off**, same "reminders" channel + `POST_NOTIFICATIONS` permission (no new infra).

## Components

### 1. `core/notifications/ReminderContent.kt`
- Add `StreakSaver("reminder_streak_saver")` to the `ReminderType` enum.
- Extend the pure decision signature with two defaulted params (Study/Goal ignore them):
  ```kotlin
  fun reminderContent(
      type: ReminderType,
      settings: AppSettings,
      studiedToday: Int,
      readyCount: Int,
      currentStreak: Int = 0,
      freezeTokens: Int = 0,
  ): NotificationContent?
  ```
- Add the branch:
  ```kotlin
  ReminderType.StreakSaver -> {
      if (currentStreak <= 0 || studiedToday > 0 || freezeTokens > 0) null
      else NotificationContent(
          title = "Keep your streak alive",
          body = "Your $currentStreak-day streak ends at midnight — a quick review saves it.",
      )
  }
  ```
  (The `enabled` flag is NOT checked here — the worker already returns early when the reminder is
  disabled, matching how Study/Goal work.)

### 2. `core/notifications/ReminderWorker.kt`
- Inject `StreakProvider` and `StreakStateRepository` (Koin, like the existing injects).
- Compute `currentStreak = streakProvider.observeStreak().first().current` and
  `freezeTokens = streakStateRepository.observe().first().freezeTokens`, and pass both into
  `reminderContent(...)`. (Computed unconditionally; Study/Goal ignore them — keeps the worker simple.)
- Add `StreakSaver` to `scheduleFor`:
  `ReminderType.StreakSaver -> Schedule(streakSaverEnabled, streakSaverHour, streakSaverMinute)`.

### 3. Settings (`AppSettings` + `SettingsRepository` + DataStore + `FakeSettingsRepository`)
- `AppSettings` gains `streakSaverEnabled = false`, `streakSaverHour = 20`, `streakSaverMinute = 0`.
- DataStore keys `STREAK_SAVER_ON`/`STREAK_SAVER_HOUR`/`STREAK_SAVER_MIN`; read them in the settings
  mapping with the same defaults.
- `SettingsRepository` interface + impl gain `suspend fun setStreakSaverReminder(enabled, hour, minute)`
  (mirrors `setStudyReminder`/`setGoalReminder`).
- `FakeSettingsRepository` implements the new field/setter.

### 4. `AzriApplication.ensureReminders()`
- Re-arm the streak-saver when enabled:
  `if (settings.streakSaverEnabled) scheduler.schedule(ReminderType.StreakSaver, settings.streakSaverHour, settings.streakSaverMinute)`.

### 5. UI (`feature/notifications/NotificationsViewModel.kt` + `NotificationsScreen.kt`)
- `NotificationsUiState` gains `streakSaverEnabled`/`streakSaverHour`/`streakSaverMinute`; the
  settings-collection block maps them; add `setStreakSaver(enabled, hour, minute)` → repository.
- `NotificationsScreen` adds a third Switch + `TimePicker` row, "Streak saver" (subtitle e.g.
  "An evening nudge when your streak is about to lapse"), identical in structure to the Study/Goal
  rows, wired to `viewModel::setStreakSaver`. Enabling it requests `POST_NOTIFICATIONS` via the same
  existing launcher path the other toggles use.

## Data flow

Evening trigger → `ReminderWorker` reads settings (streak-saver enabled? else stop), cards
(`studiedToday`), `StreakProvider` (`currentStreak`), `StreakStateRepository` (`freezeTokens`) →
pure `reminderContent(StreakSaver, …)` returns a notification iff `currentStreak>0 && studiedToday==0
&& freezeTokens==0` → `Notifier.post` → reschedule for tomorrow.

## Error handling

- Disabled mid-chain → worker returns `Result.success()` without rescheduling (same as Study/Goal).
- Pure decision is null-safe and side-effect-free; a protected or already-extended streak simply
  posts nothing but still reschedules.
- Reading streak/freeze state is a one-shot `.first()` on existing flows; no new failure modes.

## Testing

- **`ReminderContentTest`** (pure, JVM): `StreakSaver` fires for `currentStreak>0, studiedToday==0,
  freezeTokens==0`; returns null for each of — `studiedToday>0` (already studied), `currentStreak==0`
  (no streak), `freezeTokens>0` (protected). Singular/plural copy uses the streak count directly
  ("1-day streak" is acceptable).
- **`NotificationsViewModelTest`**: `setStreakSaver(true, 21, 30)` persists via the repository and
  surfaces in `uiState`.
- **`ReminderWorker.scheduleFor` + `ensureReminders`**: compile/integration-verified (the worker and
  Application are Android-coupled; the emulator is unavailable for instrumented runs).

**Build/test prefix:** Gradle commands MUST be prefixed with
`export JAVA_HOME=/opt/homebrew/opt/openjdk &&`, run from
`/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

## Out of scope (v1)

- Per-user-activity send-time optimization (fixed configurable time only).
- Snooze / action buttons on the notification.
- Changing the Study/Goal reminders, the streak/freeze logic, the notification channel, or sync.
- A separate "streak milestone" celebration notification (separate backlog item).
