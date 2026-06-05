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
- **Fully automatic — no in-app toggle or time picker.** The streak-saver is always armed at a fixed
  evening time (`20:00`, a code constant); there is no settings field and no Notifications-screen row
  for it. The only "off switch" is the OS notification permission / the app's "reminders" channel
  (which the user already controls at the system level). It reuses the existing "reminders" channel +
  `POST_NOTIFICATIONS` (no new infra). The at-risk condition above is the real gate, so on most
  evenings it posts nothing.

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

- Add a fixed evening time as a constant (e.g. in `ReminderContent.kt` next to the enum):
  ```kotlin
  const val STREAK_SAVER_HOUR = 20
  const val STREAK_SAVER_MINUTE = 0
  ```

### 2. `core/notifications/ReminderWorker.kt`
- Inject `StreakProvider` and `StreakStateRepository` (Koin, like the existing injects).
- Compute `currentStreak = streakProvider.observeStreak().first().current` and
  `freezeTokens = streakStateRepository.observe().first().freezeTokens`, and pass both into
  `reminderContent(...)`. (Computed unconditionally; Study/Goal ignore them — keeps the worker simple.)
- Add `StreakSaver` to `scheduleFor` — **always enabled** at the fixed time (it has no settings):
  `ReminderType.StreakSaver -> Schedule(enabled = true, hour = STREAK_SAVER_HOUR, minute = STREAK_SAVER_MINUTE)`.
  (So the worker never early-returns for being "disabled"; the at-risk condition in `reminderContent`
  is the gate, and it always reschedules tomorrow.)

### 3. `AzriApplication.ensureReminders()`
- **Unconditionally** arm the streak-saver on launch (no settings check):
  `scheduler.schedule(ReminderType.StreakSaver, STREAK_SAVER_HOUR, STREAK_SAVER_MINUTE)`.
  (Study/Goal stay gated on their enabled flags; only the streak-saver is always-on.)

**No Settings and no UI changes** — by design (the feature is automatic). `AppSettings`,
`SettingsRepository`, DataStore, `FakeSettingsRepository`, `NotificationsViewModel`, and
`NotificationsScreen` are untouched.

## Data flow

Evening trigger (fixed 20:00) → `ReminderWorker` reads cards (`studiedToday`), `StreakProvider`
(`currentStreak`), `StreakStateRepository` (`freezeTokens`) → pure `reminderContent(StreakSaver, …)`
returns a notification iff `currentStreak>0 && studiedToday==0 && freezeTokens==0` → `Notifier.post`
(silently dropped by the OS if notifications are disabled) → reschedule for tomorrow.

## Error handling

- The streak-saver always reschedules (it's never "disabled" in-app); the at-risk gate decides
  whether to post.
- Pure decision is null-safe and side-effect-free; a protected or already-extended streak simply
  posts nothing but still reschedules.
- Reading streak/freeze state is a one-shot `.first()` on existing flows; no new failure modes.
- If the user has revoked notification permission, `Notifier.post` is a no-op at the OS level — the
  worker still completes successfully.

## Testing

- **`ReminderContentTest`** (pure, JVM): `StreakSaver` fires for `currentStreak>0, studiedToday==0,
  freezeTokens==0`; returns null for each of — `studiedToday>0` (already studied), `currentStreak==0`
  (no streak), `freezeTokens>0` (protected). Copy uses the streak count directly ("1-day streak" is
  acceptable).
- **`ReminderWorker.scheduleFor` + `ensureReminders`**: compile/integration-verified (the worker and
  Application are Android-coupled; the emulator is unavailable for instrumented runs).
- No settings/VM tests — there are no settings or UI changes.

**Build/test prefix:** Gradle commands MUST be prefixed with
`export JAVA_HOME=/opt/homebrew/opt/openjdk &&`, run from
`/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

## Out of scope (v1)

- **Any in-app toggle or time picker for the streak-saver** — it's intentionally automatic; the OS
  notification controls are the only off switch. (If a user-facing control is wanted later, it's an
  additive follow-up.)
- Per-user-activity send-time optimization (fixed 20:00 only).
- Snooze / action buttons on the notification.
- Changing the Study/Goal reminders, the streak/freeze logic, the notification channel, or sync.
- A separate "streak milestone" celebration notification (separate backlog item).
