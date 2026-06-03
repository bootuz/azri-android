# Queue Sort Order

Date: 2026-06-03

## Goal

Let the user choose how the study queue is ordered — Due Date, Difficulty, or
Shuffle — from the Queue screen. The order drives both the "Queue" preview list
and the actual study session.

## Decisions (locked)

- Three orders (mirroring iOS `StudyQueueSortOrder`): **DueDate** (default,
  `fsrsDue` ascending), **Difficulty** (`fsrsDifficulty` descending — hardest
  first), **Shuffle** (seeded random; re-selecting reshuffles).
- Applies to **preview + session** (consistent order). Persisted in DataStore so
  both ViewModels read it; a shared `shuffleSeed` keeps shuffle identical across
  the two.

## Design

### Model (`core/domain/fsrs/`)
`enum QueueSortOrder { DueDate, Difficulty, Shuffle }` (title + icon). Pure
`StudyQueueBuilder.sort(cards, order, shuffleSeed): List<Card>`:
- DueDate → `sortedBy { fsrsDue }`
- Difficulty → `sortedByDescending { fsrsDifficulty }`
- Shuffle → `shuffled(Random(shuffleSeed))`

Applied **after** `buildStudyQueue` (uncapped due+new), so it replaces the
due-first ordering with the chosen order.

### Persistence (`AppSettings` + `SettingsRepository`)
`queueSortOrder = DueDate`, `queueShuffleSeed: Long`. Setters
`setQueueSortOrder(order)` + `setQueueShuffleSeed(seed)`. Selecting Shuffle (in
the VM) writes the order **and** a fresh `Random.nextLong()` seed.

### Wiring
- `StudyQueueViewModel` — sorts the built queue before mapping to `queueCards`;
  `StudyQueueUiState` gains `sortOrder`; `setSortOrder(order)` persists (+ new
  seed when Shuffle).
- `StudyViewModel` — applies `StudyQueueBuilder.sort(...)` to its session queue in
  `load()`, reading the persisted order + seed.

### UI
The "Queue" section header gets a sort `IconButton` → `DropdownMenu` with the
three options (icon + checkmark on the active one). `StudyQueueContent` gains
`sortOrder` + `onSortChange`.

### Tests
`StudyQueueBuilder.sort` (due asc, difficulty desc, shuffle deterministic per
seed + differs across seeds), `StudyQueueViewModel` (order reflected + persisted),
`StudyViewModel` (sort changes the first card), `StudyQueueContent` (menu +
callback), `FakeSettingsRepository` update.

## Out of scope (YAGNI)
Per-deck sort overrides; memorized/at-risk sorts; sort for the manual
deck-detail review list.
