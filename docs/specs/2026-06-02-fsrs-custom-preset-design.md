# FSRS Custom Preset & Picker Redesign

Date: 2026-06-02

## Goal

Add a user-defined **Custom** FSRS preset (parity with iOS `FSRSSettingsView` "manual"
mode) and replace the chip-based preset picker with selectable list rows. Remove the
daily-limit steppers from this screen — daily goals will live elsewhere.

## Decisions (locked)

- Custom exposes **all four** iOS knobs: target retention, maximum interval, Enable Fuzz,
  Short-term optimization. This requires extending the Android `Fsrs6` scheduler (it
  currently only reads retention + max interval).
- Picker = **selectable list rows** (leading icon + name + description + trailing radio).
- **Reset** → returns to the **Optimal** preset (labelled "Default") and resets custom
  values to their defaults.
- Maximum-interval control = **segmented buttons** (30 / 90 / 180 / 365 days).
- Daily-limit steppers are **removed from the UI only**; `newCardsPerDay` /
  `maxReviewsPerDay` stay in `AppSettings` + repository so `StudyQueueBuilder` keeps
  working and a future daily-goals screen can edit them.

## Design

### 1. Domain — separate preset (choice) from parameters (values)

- `data class FsrsParameters(requestRetention: Double, maximumInterval: Int, enableFuzz: Boolean, enableShortTerm: Boolean)`.
- `FsrsPreset` gains case `Custom` and metadata `displayName` (Optimal→"Default"),
  `description`, `icon`. Fixed presets carry constant parameters (fuzz + short-term on).
- `AppSettings.fsrsParameters(): FsrsParameters` resolves the active params — constants
  for fixed presets, stored custom fields for `Custom`. The scheduler never sees a preset.

### 2. Persistence

`AppSettings` adds: `customRetention=0.90`, `customMaxInterval=365`, `enableFuzz=true`,
`enableShortTerm=true`. New DataStore keys + setters on `SettingsRepository`:
`setCustomRetention`, `setCustomMaxInterval`, `setEnableFuzz`, `setEnableShortTerm`.

### 3. Scheduler — extend `Fsrs6`

New ctor params `enableShortTerm: Boolean = true`, `enableFuzz: Boolean = false`
(defaults reproduce current behaviour, so existing golden tests stay green; presets pass
the real values).

- **Short-term OFF**: New / (re)learning grades skip the fixed minute steps and graduate
  to a stability-derived day interval (mirrors ts-fsrs `enable_short_term=false`).
- **Fuzz ON**: Review-state day intervals ≥ 2.5d get the open-spaced-repetition fuzz
  ranges `[(2.5,7,.15),(7,20,.1),(20,∞,.05)]`. RNG seeded **deterministically** from card
  state (stability + reps) → reproducible, unit-testable, respects `maximumInterval`.

`SchedulingService(params: FsrsParameters)` replaces `SchedulingService(preset)`.
`StudyViewModel` passes `settings.fsrsParameters()`.

### 4. UI — `SettingsScreen` ("Spaced repetition")

- Preset list rows (`RadioButton` trailing). Selecting `Custom` reveals an animated
  Custom Parameters section: retention `Slider` (0.80–0.99), max-interval segmented
  control, Fuzz `Switch`, Short-term `Switch`.
- Top-bar **Reset** action → Optimal + default custom values.
- Daily-limit steppers removed.
- Profile "Spaced repetition" row supporting text → `preset.displayName`.

### 5. Tests

- `Fsrs6Test`: fuzz determinism (same card → same interval), fuzz range bounds, fuzz-off
  == current output, short-term-off graduation (New→Good schedules in days not minutes).
- `SchedulingServiceTest`: params constructor builds the right `Fsrs6`.
- `SettingsViewModelTest`: new setters + `resetToDefaults`.
- `SettingsContentTest`: preset rows render, Custom reveals the section, slider/segmented/
  switches invoke callbacks.
- `FakeSettingsRepository`: new fields + setters.

## Out of scope

Daily goals UI; per-deck FSRS params; FSRS weight (w[0..20]) editing; optimizer.
