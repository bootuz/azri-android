# Daily Notifications

Date: 2026-06-03

## Goal

Two daily local reminders, scheduled via WorkManager:
1. **Daily study reminder** — fires at a user-set time every day.
2. **Goal reminder** — fires at its own (evening) time, only if the daily goal
   isn't met yet.

## Decisions (locked)

- Two **independent** reminders, each with its own enable toggle + time.
- Goal reminder has its **own evening time** (default 20:00) and fires only when
  goal tracking is on and `studiedToday < goalTotal`.
- **Dynamic content** (live counts), mirroring iOS `PushManager`.
- **WorkManager** (already in the project via `SyncWorker`), not AlarmManager —
  reminders don't need exact alarms, and WorkManager survives reboot.

## Design

### Persistence (`AppSettings` + `SettingsRepository`)
Six fields (default off until opted-in + permission granted):
`studyReminderEnabled=false`, `studyReminderHour=9`, `studyReminderMinute=0`,
`goalReminderEnabled=false`, `goalReminderHour=20`, `goalReminderMinute=0`.
New DataStore keys + setters.

### `core/notifications/`
- **`ReminderType`** enum { Study, Goal }.
- **`reminderContent(type, settings, studiedToday, readyCount): NotificationContent?`**
  — pure decision (null ⇒ post nothing). Study: skip if `readyCount == 0`.
  Goal: skip unless `dailyGoalEnabled && studiedToday < goalTotal`. Unit-tested.
- **`ReminderScheduler`** — `nextTriggerDelayMillis(now, hour, minute)` (pure;
  rolls to tomorrow if the time has passed today) + `schedule(type, hour, minute)`
  enqueuing a unique `OneTimeWorkRequest` (per type, `REPLACE`) and `cancel(type)`.
- **`ReminderWorker : CoroutineWorker`** (Koin, like `SyncWorker`): builds content
  via `reminderContent`, posts it through `Notifier` when non-null, then
  **reschedules itself for tomorrow** at the same time (chain survives reboot).
- **`Notifier`** — creates the "Reminders" `NotificationChannel` once; posts
  `NotificationCompat` with `ic_stat_notify` (monochrome vector); tap opens
  `MainActivity`.

`studiedToday` = cards with `fsrsLastReview ≥ local midnight`; `readyCount` from
`StudyQueueBuilder` (uncapped). The worker resolves `CardRepository` +
`SettingsRepository` from Koin.

### Recovery
`AzriApplication.onCreate` re-ensures scheduling for any enabled reminder
(idempotent via unique work names) — covers force-stop, where WorkManager work is
cleared until next launch. Reboot is handled by WorkManager persistence.

### Permission (`POST_NOTIFICATIONS`, API 33+)
Manifest entry + a Compose permission launcher in `NotificationsScreen`: toggling
a reminder *on* requests permission; denied ⇒ toggle stays off.

### UI
Profile → new **"Notifications"** row → `NotificationsScreen` (route
`notifications`): a "Daily study reminder" `Switch` + time row (Material
`TimePicker` dialog) and a "Goal reminder" `Switch` + time row. Backed by
`NotificationsViewModel(settingsRepository, reminderScheduler)` whose setters
persist then (re)schedule/cancel.

### Tests
`ReminderScheduler.nextTriggerDelayMillis` (today vs roll-to-tomorrow),
`reminderContent` (skip/format/goal-met short-circuit), `NotificationsViewModel`
setters + scheduling calls, `NotificationsScreen` toggle/time-row Compose,
`FakeSettingsRepository` update.

## Out of scope (YAGNI)
iOS onboarding drip messages, streak-reset notifications, per-deck reminders,
notification actions/snooze.
