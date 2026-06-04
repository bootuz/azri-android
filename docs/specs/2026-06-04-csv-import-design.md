# CSV Import — Design

**Date:** 2026-06-04
**Status:** Approved (design); pending implementation plan
**Builds on:** the `.apkg` import wizard (this feature reuses its file/class layout, Library
entry point, and persistence path) and local-first media (no media involved here, but the
same `source`-tagged, fresh-FSRS card creation).

## Goal

Let users import a CSV (or TSV / plain-text delimited) file of flashcards into Azri. Port the
iOS `AzriKit/CSVImport` flow (file → column mapping → preview → import) to Android, and fix two
latent iOS gaps along the way:

1. iOS ships a delimiter picker (comma / semicolon / tab) that is **never wired into parsing** —
   `SwiftCSVImportService.parseCSV` always parses as comma. Android **auto-detects** the
   delimiter instead.
2. iOS always treats row 1 as a header (`NamedCSV`), silently eating the first card of a
   headerless `front,back` file. Android offers a **"First row is header" toggle**.

Import is a simple two-column projection: the user maps one column to the card **front** and
one to the **back**; everything else is ignored. Cards are created as **new** (fresh FSRS),
`source = "csv"`.

## Background: what iOS does (the flow we port)

`AzriKit/Sources/AzriKit/CSVImport` + `SimpleAnkiSwiftUI/.../CSVImport`:
- **Parse:** `SwiftCSV.NamedCSV(url:)` → `headers` (deduplicated: repeated names become
  `name`, `name_2`, …) + `rows` (array of `[columnName: value]`). Row 1 is always the header.
- **Validate:** non-empty, and at least 2 columns.
- **Column mapping:** pick a **Question** column (front) and an **Answer** column (back); they
  must differ. A 3-row live preview is shown.
- **Preview:** generate cards — trim front/back, **skip the row if either side is empty**; all
  cards selected by default, each deselectable.
- **Import:** library-level creates a **new deck named after the file**; deck-level imports into
  an existing deck. Cards created as new, `source = csv`.

## What Android changes

- **Auto-detect delimiter** (comma / semicolon / tab) by tallying candidate characters in the
  first line. No delimiter UI.
- **"First row is header" toggle** (default ON). OFF → row 1 is data and columns are labelled
  `Column 1 … Column N`. The 3-row preview updates live so the user sees whether their first
  card would be consumed.
- **Library-level only for v1** — always creates a new deck named after the file (matching the
  Android `.apkg` import). The service signature still takes a `deckName`, so a deck-level
  entry point is a trivial future addition.
- **Real RFC 4180 parsing** via the `kotlin-csv` library (quoted fields, escaped `""`, embedded
  delimiters and newlines), rather than a naive split.

## Entry point & routing

The Library top bar keeps its single **Import** action (`FileDownload` IconButton). The existing
`OpenDocument` picker already passes `*/*`, so `.csv` / `.tsv` / `.txt` / `.apkg` all appear.

Routing happens in `AzriNavHost` after the picker returns a `Uri`:

- SAF `content://` URIs have **no usable file extension in their path**. The router queries the
  `ContentResolver` for `OpenableColumns.DISPLAY_NAME` to recover the real filename
  (e.g. `deck.csv`), then switches on the lowercased extension:
  - `apkg` → existing `ApkgImportScreen` overlay (deck name now derived from the filename,
    a small upgrade over today's hardcoded `"Imported deck"`).
  - `csv` / `tsv` / `txt` → new `CsvImportScreen` overlay (deck name from the filename,
    fallback `"Imported cards"`).
  - anything else → a brief "Unsupported file type" message (no crash).
- The same display name (minus extension) becomes the new deck's name.

A tiny `pickedFileName(resolver, uri): String?` helper (or inline) reads the display name; the
extension drives the route and the base name becomes the deck name.

## UX flow

Wizard steps (a step state machine in the ViewModel):

1. **Parsing** — copy the picked file to a temp file on `Dispatchers.IO`, auto-detect the
   delimiter, parse, validate. Show a spinner.
2. **Column mapping** — two dropdowns (front column, back column; must differ), a
   **"First row is header"** `Switch`, and a live 3-row sample preview. Changing the toggle
   re-derives headers/rows from the already-parsed file (no re-read of disk needed — we keep the
   raw parsed grid and re-slice it).
3. **Preview** — generated cards (trimmed front/back), each with a select/deselect checkbox;
   empty-on-either-side rows auto-skipped; shows "N of M selected".
4. **Importing** — create the new deck, insert the selected cards as new (fresh FSRS),
   `source = "csv"`. Return to Library.

## Components

All new code under `app/src/main/java/nart/simpleanki/core/csv/` (parsing/service) and
`app/src/main/java/nart/simpleanki/feature/csvimport/` (UI), mirroring the `apkg` packages.

- **`CsvModels.kt`**
  - `ParsedCsv(headers: List<String>, rows: List<List<String>>)` — `rows` are positional
    (list-of-columns), not keyed by header, so the same parsed grid serves both header-on and
    header-off views.
  - `CsvPreviewCard(front: String, back: String, selected: Boolean = true)`.
  - sealed `CsvImportError : Exception` — `FileAccess`, `EmptyFile`, `TooFewColumns`,
    `ParseFailed`, each with a user-facing message.

- **`CsvParser.kt`** — pure, JVM-testable. Responsibilities:
  - `detectDelimiter(firstLine: String): Char` — among `,`/`;`/`\t`, pick the highest count
    outside quotes; default `,` on a tie/zero.
  - `parse(text: String, hasHeader: Boolean): ParsedCsv` — strip a leading UTF-8 BOM, detect the
    delimiter, parse with kotlin-csv into a `List<List<String>>`, then:
    - `hasHeader = true` → first row = headers (deduplicated), remaining rows = data.
    - `hasHeader = false` → headers = `Column 1 … Column N` (N = max row width), all rows = data.
  - Header de-duplication mirrors iOS (`name`, `name_2`, …).
  - Ragged rows (fewer columns than the header) are padded with empty strings so column indexing
    is always safe.

- **`CsvImportService.kt`** — interface + `DefaultCsvImportService` (faked in tests):
  - `suspend fun parse(uri: Uri, hasHeader: Boolean): ParsedCsv` — copy `uri` → temp file on IO,
    read as UTF-8, delegate to `CsvParser`, validate, delete temp in `finally`.
  - `fun validate(parsed: ParsedCsv): Unit` — throws `EmptyFile` (no data rows) or
    `TooFewColumns` (< 2 headers).
  - `fun previewCards(parsed: ParsedCsv, frontCol: Int, backCol: Int): List<CsvPreviewCard>` —
    map each data row: trim `row[frontCol]` / `row[backCol]`, skip if either is empty.
  - `suspend fun import(cards: List<CsvPreviewCard>, deckName: String)` — create a `Deck`
    (`DeckRepository.upsert`), then for each selected card `CardRepository.upsert` a `Card`
    with `source = "csv"`, `fsrsState = New`, `fsrsDue = now`, `image/audio = null`. Identical
    shape to `ApkgImportService.import`.
  - Re-parsing on toggle change is handled in the ViewModel by re-running `CsvParser.parse` on
    the retained raw text — but to avoid re-reading disk, the service exposes the raw text once
    (see ViewModel note) **or** the ViewModel simply re-calls `service.parse(uri, hasHeader)`.
    Decision: **re-call `service.parse(uri, hasHeader)`** for simplicity; CSV files are small and
    SAF re-reads are cheap. (No raw-text caching seam — keeps the service stateless.)

- **`CsvImportViewModel.kt`** — `ImportStep { Parsing, ColumnMapping, Preview, Importing }`,
  `CsvImportUiState(step, headers, sampleRows, frontCol, backCol, hasHeader, previewCards, busy,
  error)`. Retains two private fields outside the UI state (mirroring how `ApkgImportViewModel`
  holds `collection`): the picked **`Uri`** (so `setHasHeader` can re-parse) and the latest
  **`ParsedCsv`** (so `generatePreview` can build cards without re-reading disk). `sampleRows` in
  the UI state is the first 3 data rows of that `ParsedCsv`, used for the live column-mapping
  preview. Methods: `parse(uri)`, `setHasHeader(Boolean)` (re-parses from the retained `Uri`,
  preserves column choices when still in range), `setFrontCol(Int)`, `setBackCol(Int)`,
  `generatePreview()`, `toggleCard(index)`, `import(onComplete)`, `clearError()`.
  `canGeneratePreview = frontCol != backCol`. Mirrors `ApkgImportViewModel`.

- **`CsvImportScreen.kt`** — Compose wizard hosted as an overlay from `AzriNavHost` (like
  `ApkgImportScreen`). Reuses apkg's dropdown / step / preview-card conventions. Column-mapping
  step: two `ExposedDropdownMenuBox` column pickers + a "First row is header" `Switch` +
  3-row preview. Preview step: `LazyColumn` of selectable rows + "N of M selected" + Import
  button. Importing: centered spinner. An error `AlertDialog` driven by `uiState.error`.

## Dependencies

- **kotlin-csv** — `com.github.doyaaaaaken:kotlin-csv-jvm` (pure Kotlin/JVM; works on Android and
  in JVM unit tests). Added to `gradle/libs.versions.toml` and the `app` module as
  `implementation`. No native libs, no Android-runtime dependency.

## Data flow

SAF `Uri` → `ContentResolver` display name → extension routes to CSV wizard, base name = deck
name → `CsvImportService.parse(uri, hasHeader=true)` copies to temp, reads UTF-8, `CsvParser`
detects delimiter + builds `ParsedCsv` → `validate` → user picks front/back columns + toggles
header (re-parse on toggle) → `previewCards` (trim, skip-empty) → user selects cards →
`import(selected, deckName)` creates 1 deck + N new `source="csv"` cards → overlay dismissed,
back to Library.

## Error handling

`CsvImportError` is surfaced as an `AlertDialog`:
- `FileAccess` → "Unable to open the file. Please try again."
- `EmptyFile` → "This file has no rows to import."
- `TooFewColumns` → "A CSV needs at least two columns (a front and a back)."
- `ParseFailed` → "We couldn't read this file as CSV."

A wrong delimiter guess degrades gracefully: the file still parses (into one wide column), which
trips the `TooFewColumns` check with an actionable message rather than crashing. Temp files are
always removed in a `finally`. The picker routing never throws — an unknown extension shows an
inline "Unsupported file type" message and dismisses.

## Testing

- **`CsvParser`** (pure JVM unit tests):
  - delimiter detection: comma, semicolon, tab, tie → comma, delimiter inside quotes ignored.
  - quoted fields: embedded delimiter, embedded newline, escaped `""` quotes.
  - line endings: `\n` and `\r\n`.
  - BOM stripped from the first header.
  - `hasHeader = true` vs `false` (synthetic `Column N` names; first row retained as data).
  - header de-duplication (`name`, `name_2`).
  - ragged rows padded so indexing is safe.
- **`CsvImportService`** (fake `DeckRepository`/`CardRepository`):
  - `previewCards` — trims, skips rows with an empty front or back, respects column indices.
  - `validate` — throws on empty / single-column input.
  - `import` — creates exactly one deck + N cards, each `source = "csv"`, fresh-FSRS
    (`state = New`, `due = now`), `image/audio = null`, correct `deckId`.
- **`CsvImportViewModel`** (fake service):
  - step transitions (Parsing → ColumnMapping → Preview → Importing).
  - `setHasHeader` re-parses and keeps valid column choices.
  - front == back blocks preview (`canGeneratePreview`).
  - selection count; error surfacing from a failing service.
- **`CsvImportScreen`** — build-verified only (no Compose UI unit tests, consistent with the
  codebase).

## Out of scope (explicit)

Deck-level "import into this deck" entry point (library-level new-deck only for v1); image/audio
columns or any media; importing tags or columns beyond front/back; a manual delimiter override;
non-UTF-8 encodings (UTF-8 / UTF-8+BOM only); Anki-style HTML rendering of fields; `.xlsx` or
other spreadsheet binaries (delimited text only).
