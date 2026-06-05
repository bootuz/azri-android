# Streak Freeze + Repair — Design

**Date:** 2026-06-05
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/streak-freeze-repair` (off `main`).

## Goal

Make study streaks more forgiving (and stickier) by adding **freezes** — earned by studying, which
auto-cover a missed day so the streak survives — and **repair** — a free, limited way to restore a
streak that just broke. Today the streak hard-resets on any missed day. This extends it without
changing the (good) derivation core.

## Key constraint & the core idea

Today the streak is **purely derived**: `StreakCalculator.compute(reviewDays, today)` over the civil
days with ≥1 review (from synced review logs); any gap hard-resets. Freeze/repair need **stored
state**. The elegant part: **`StreakCalculator` does not change** — a frozen day is just another
"active day," so the streak becomes `StreakCalculator.compute(reviewDays ∪ frozenDays, today)`. All
new complexity (which missed days become frozen, earning freezes, repair) lives in **one new
reconciler**; the calculator stays a pure function.

## Decisions (from brainstorming)

- **Freeze economy: earn by studying, no currency.** Earn **1 freeze per 7 streak-days**, store up
  to **2**, auto-consume **1 per missed day**. Free for everyone; no billing, no gem economy.
- **Repair: free, limited.** Offered when the streak broke from a **single recent missed day**
  (yesterday) **and** the user has **studied today**; capped to **once per 30 days**. One tap freezes
  that gap day and the streak returns. Multi-day breaks are not repairable in v1 (YAGNI).
- **Persistence: synced via Firestore**, through the existing `SyncManager` (last-write-wins by
  `lastModified`), as a single per-user `streak_state` doc — so the streak number stays consistent
  across devices (the streak itself already syncs via review logs).

## Components

### 1. `StreakState` (new persisted state)
```kotlin
data class StreakState(
    val freezeTokens: Int = 0,           // available freezes (earned, capped at FREEZE_CAP)
    val frozenDays: Set<Long> = emptySet(), // civil-days covered by an auto-freeze or a repair
    val freezesAwardedForRun: Int = 0,   // freezes already granted for the current unbroken run (idempotent earning)
    val lastReconciledDay: Long = 0,     // today-index we last advanced to (reconcile once per civil day)
    val lastRepairDay: Long = 0,         // civil-day of the last repair (for the once-per-30-days cap)
    val lastModified: Long = 0,          // for sync LWW
)
```
Constants: `FREEZE_CAP = 2`, `FREEZE_EARN_EVERY = 7`, `REPAIR_COOLDOWN_DAYS = 30`.

### 2. `StreakStateManager` (new — the reconciler)
Pure-ish logic (civil-day math via the existing `localEpochDay`), persisting through
`StreakStateRepository`. `reconcile(reviewDays, state, today)` returns an updated `StreakState`
(idempotent — safe to call repeatedly):

1. **One-shot per day:** if `today <= state.lastReconciledDay`, only re-run the earning step
   (cheap/idempotent) and return; otherwise continue.
2. **Auto-freeze:** let `active = reviewDays ∪ state.frozenDays`; `lastActive = max(active ≤ today)`.
   For each fully-elapsed missed day `d` in `lastActive+1 .. today-1` (not already active): if
   `freezeTokens > 0`, add `d` to `frozenDays` and decrement `freezeTokens`; else **stop** (the
   streak breaks at `d`).
3. **Recompute** `current = StreakCalculator.compute(reviewDays ∪ frozenDays, today).current`.
4. **Break reset:** if the run is broken (the consecutive run ending at `today`/`today-1` is shorter
   than `freezesAwardedForRun*7`, i.e. a new run started), reset `freezesAwardedForRun` to
   `current / FREEZE_EARN_EVERY` (floor) — see "first run" below.
5. **Earn:** while `current >= (freezesAwardedForRun + 1) * FREEZE_EARN_EVERY` and
   `freezeTokens < FREEZE_CAP`: `freezeTokens++`, `freezesAwardedForRun++`. (If the cap blocks an
   award, still advance `freezesAwardedForRun` so the next freeze is earned at the next multiple —
   no retroactive flood when a token is later spent.)
6. Set `lastReconciledDay = today`; bump `lastModified`; persist if anything changed (mark dirty).

**First run / migration init:** when no prior state exists, initialize `freezesAwardedForRun =
current / FREEZE_EARN_EVERY` so an existing long streak doesn't instantly dump freezes; earning
proceeds from the next multiple.

`reconcile()` is called (a) on app foreground/start and (b) after a completed review session — both
idempotent. It is an explicit suspend call, **never inside a Flow** (no side-effects in observation).

### 3. Repair
`repairEligibility(reviewDays, state, today): RepairOffer?` (pure): returns an offer when —
- the streak is currently **broken** (`compute(...).current == 0` over `reviewDays ∪ frozenDays`),
- there is exactly one uncovered missed day at `today-1` whose preceding run (ending `today-2`) had
  length ≥ 1 (a real streak was lost),
- the user **studied today** (`today ∈ reviewDays`),
- `today - state.lastRepairDay >= REPAIR_COOLDOWN_DAYS`.

The offer carries the would-be-restored streak length. `repair(state, today)` adds the gap day
(`today-1`) to `frozenDays`, sets `lastRepairDay = today`, bumps `lastModified`/dirty, and persists.
The next streak compute then includes the restored run.

### 4. Persistence (Room) + sync (Firestore)
- **`StreakStateEntity`** — single-row table `streak_state` (`@PrimaryKey id: String` = a fixed key
  `"current"`). `frozenDays` stored as a sorted comma-delimited `String` via a small Room
  `TypeConverter` (or an encoded column the repository owns). Fields mirror `StreakState` + `dirty`.
- **`StreakStateDao`** — `observe(): Flow<StreakStateEntity?>`, `get()`, `upsert()`, `getDirty()`,
  `clearDirty(lastModified)`.
- **`StreakStateRepository`** — `observe(): Flow<StreakState>` (defaulting to `StreakState()` when the
  row is absent), `get()`, `update(StreakState)` (stamps `lastModified` + `dirty = true`).
- **Room migration `MIGRATION_2_3`** — `CREATE TABLE streak_state (...)`; column types/nullability
  must match the entity exactly (no SQL `DEFAULT`), per the `MIGRATION_1_2` precedent. `version = 3`;
  add the entity + `streakStateDao()` to `AzriDatabase`.
- **`StreakStateDto`** (Firestore) — mirrors the fields; `frozenDays` as a `List<Long>` array;
  `lastModified` as `Timestamp`. Single doc at `users/{uid}/streakState/current`.
- **`RemoteSyncSource`** gains `fetchStreakState(uid): StreakStateDto?` and
  `pushStreakState(uid, dto)`; **`FirestoreSyncService`** implements them via the existing `col`
  helper.
- **`SyncManager`** gains a streak-state block: push the dirty row → clear dirty; pull → apply when
  `shouldApplyRemote(localLastModified, remoteLastModified)` (last-write-wins, same as folders). Add
  `streakStateDao` to the constructor + Koin.
- **Koin** (`AppModule`): register the DAO, repository, and `StreakStateManager`; add
  `streakStateDao` to the `SyncManager` provider; add `.addMigrations(MIGRATION_2_3)`.

### 5. `StreakProvider` (modify)
Combine logs with frozen days so the displayed streak honors freezes:
```kotlin
fun observeStreak(): Flow<Streak> =
    combine(reviewLogRepository.observeLogs(), streakStateRepository.observe()) { logs, state ->
        val days = logs.mapTo(mutableSetOf()) { localEpochDay(it.review, timeZone) }
        days += state.frozenDays
        StreakCalculator.compute(days, localEpochDay(now(), timeZone))
    }
```
`streakIncludingToday()` similarly unions `frozenDays` (plus today). The existing `StreakCalculator`
tests stay green (unchanged).

### 6. UI
- **Today header (`StudyQueueScreen`):** next to the 🔥 chip, show a small **❄️ + `freezeTokens`**
  when `freezeTokens > 0`. When a repair offer exists, show a **"Repair your N-day streak"** banner
  with a one-tap button (calls the VM's `repairStreak()`). `StudyQueueUiState` gains
  `freezeCount: Int` and `repairOffer: RepairOffer?`; `StudyQueueViewModel` reconciles on init and
  exposes these (combine the existing streak flow with the streak-state flow).
- **Session summary (`SessionSummary`):** when a freeze covered a day this cycle, show **"❄️ Freeze
  used — streak safe"**; show remaining freeze count; surface the repair offer here too.
  `StudyViewModel` reconciles on the finish path and exposes freeze/repair fields in `StudyUiState`.

## Data flow

Study → review logs accrue (synced) → on app foreground / session finish, `StreakStateManager`
reconciles (auto-freeze missed days, earn freezes), persisting `StreakState` (dirty → synced LWW) →
`StreakProvider` computes the streak over `reviewDays ∪ frozenDays` → Today header shows 🔥 + ❄️;
if the streak broke recently and the user studied today, a repair offer appears → tap → gap day
frozen → streak restored.

## Error handling

- Reconcile is idempotent and guarded by `lastReconciledDay`; re-running it (multiple foregrounds,
  re-sync) doesn't double-spend or double-award.
- Auto-freeze only ever spends available tokens; with no tokens the streak breaks exactly as today.
- Cross-device: LWW by `lastModified` resolves the rare case of two devices reconciling the same day
  (acceptable — the streak from synced logs is already consistent; freezes are a small overlay).
- A repair is bounded (single day, once/30-days, requires studying today) so it can't be abused.

## Testing

- **`StreakStateManager` / reconcile** (JVM, pure + fake repo): auto-freeze covers 1 missed day;
  covers up to `FREEZE_CAP` consecutive missed days then breaks; earning grants at 7/14 and respects
  the cap; award idempotency across repeated reconciles and recompute; first-run init doesn't flood;
  break resets `freezesAwardedForRun`.
- **Repair** (pure): eligible only with a single `today-1` gap + studied-today + a prior run + the
  30-day cooldown; `repair()` restores the streak and stamps `lastRepairDay`; not offered for
  multi-day gaps or within cooldown.
- **`StreakStateRepository`** (fake DAO): defaults to `StreakState()` when absent; `update` stamps
  `lastModified` + dirty; `frozenDays` String round-trips.
- **`StreakProvider`** (fake repos): freeze fills a gap so the streak survives; no frozen days =
  unchanged behavior.
- **`SyncManager`** (extend, fake remote): dirty streak-state pushed then cleared; newer remote
  applied, older skipped (LWW).
- **`MIGRATION_2_3`**: exercised by the build + (if infra present) a migration test; column names
  match the entity.
- **VMs / Compose**: `StudyQueueViewModel` exposes `freezeCount` + `repairOffer`; the header chip +
  repair banner render (previews / `@Preview`); `SessionSummary` shows freeze-used + repair. Emulator
  unavailable ⇒ Compose is compile/preview-verified; instrumented sources compile-checked.

**Build/test prefix:** Gradle commands MUST be prefixed with
`export JAVA_HOME=/opt/homebrew/opt/openjdk &&`, run from
`/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

## Out of scope (v1)

- Purchasable/premium freezes, paid instant repair, any billing (decided: earn-by-studying + free
  repair).
- Multi-day repair, streak-freeze gifting, streak milestones / leagues (separate backlog items).
- A dedicated streak/calendar screen — v1 surfaces freeze count + repair on the Today header and the
  session summary only.
- Changing `StreakCalculator`, the review-log persistence/sync, or the daily-reminder system.
