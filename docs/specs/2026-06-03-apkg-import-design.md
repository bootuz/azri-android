# .apkg Import — Design

**Date:** 2026-06-03
**Status:** Approved (design); pending implementation plan
**Builds on:** local-first media rework (imported media saved on-device via `MediaManager`)

## Goal

Let users import Anki `.apkg` decks into Azri. Port the iOS `AzriKit/APKGImport` wizard
(file → note-type selection → field mapping → preview → import) to Android, and **exceed
iOS coverage** by also reading the modern zstd-compressed `collection.anki21b` format that
iOS cannot open.

Import is a deliberately **lossy projection**: Anki's flexible note/notetype/template model
(arbitrary fields, templates producing many cards) is mapped onto Azri's fixed
`Card(front, back, +1 image, +1 audio)`, rendered as plain text. The user explicitly maps
fields, which sidesteps template rendering entirely.

## Background: what iOS does (the spec we port)

`AzriKit/Sources/AzriKit/APKGImport`:
- **Parse:** unzip → open `collection.anki21` else `collection.anki2` (plain SQLite) →
  `SELECT models FROM col` (JSON notetypes) + `SELECT id, guid, mid, flds, tags FROM notes`
  (fields split on ``) → read the `media` JSON map (numberedFile → originalName).
  **iOS supports only plain-SQLite schema 11; it cannot read `collection.anki21b`.**
- **Field processing:** extract the first `<img src>` and first `[sound:…]` → map to a saved
  media file, strip those tags, strip all HTML, trim. First media wins (front before back).
  One image + one audio per card.
- **Import:** new deck named after the file (or an existing deck), cards created as **new**
  (fresh FSRS), `source = apkg`. Anki decks/cards/revlog are ignored.

## What Android adds / changes

- **Also reads the new `.apkg`** (`collection.anki21b`): zstd-compressed SQLite (schema 18),
  with notetype field names in `notetypes` + `fields` tables (not `col.models` JSON), and a
  zstd + protobuf `media` manifest with individually zstd-compressed media blobs.
- **Media is local-first:** imported files are saved on-device via `MediaManager`
  (`imagePath = null`), consistent with the freemium model — no Firebase, works for free users.
- **LaTeX/MathJax is not rendered** (Android cards are plain text); LaTeX delimiters are left
  as literal text rather than preserved for rendering. A small, conscious divergence from iOS.

## Scope boundary (only `notes` + notetype field names are needed)

We import notes as brand-new cards and create our own deck, so Anki's `decks`, `cards`, and
`revlog` tables are ignored entirely (as iOS does). The `notes` table
(`id, guid, mid, flds, tags`) is identical across schema 11 and 18. Therefore the **only**
schema divergence we handle is where notetype field names live:

- **Legacy (schema 11):** `col.models` — a JSON object keyed by model id; each model has
  `name`, `flds` (array of `{name, ord}`), `sortf`.
- **New (schema 18):** `notetypes` (id, name) joined with `fields` (ntid, ord, name).

## UX flow

Entry point: an **Import** action in the Library top bar (alongside New folder / New deck).
For v1 this is **Library-level only** — it always creates a new deck. (Deck-level "import into
this deck" is a easy future addition; the service already accepts an optional target deck.)

Wizard steps (a step state machine in the ViewModel):
1. **Parse** — SAF picker (`rememberLauncherForActivityResult` + `ActivityResultContracts.OpenDocument`)
   yields a `Uri`; show a spinner while parsing on `Dispatchers.IO`.
2. **Note-type selection** — list notetypes (name + note count); user picks **one**
   (multi-notetype files are imported one type per run, matching iOS).
3. **Field mapping** — dropdowns for which field → **front** and which → **back** (must
   differ); an **Import media** toggle; a 3-note sample preview.
4. **Preview** — generated cards (processed front/back, media badges), each with a
   select/deselect checkbox; empty cards auto-skipped; shows "N of M selected".
5. **Import** — create a new deck named after the file (top level), insert the selected cards
   as **new** (fresh FSRS), `source = "apkg"`, with local media filenames and `imagePath = null`.
   Return to Library.

## Components

All new code under `app/src/main/java/nart/simpleanki/core/apkg/` (parsing/service) and
`app/src/main/java/nart/simpleanki/feature/apkgimport/` (UI), following existing package
conventions.

- **`AnkiModels.kt`** — `AnkiNoteType(id: Long, name: String, fields: List<String>, sortField: Int)`,
  `AnkiNote(id: Long, guid: String, modelId: Long, fields: List<String>, tags: List<String>)`,
  `ParsedCollection(noteTypes: List<AnkiNoteType>, notes: List<AnkiNote>, media: Map<String, ByteArray>)`
  (media keyed by original filename), `ApkgPreviewCard(front, back, imageName: String?, audioName: String?, selected: Boolean)`,
  and a sealed `ApkgImportError`.
- **`ApkgUnzipper`** — extracts a `.apkg` (`java.util.zip`) to a temp dir; returns the dir.
- **`AnkiCollectionReader`** (interface) — `suspend fun read(extractedDir: File): ParsedCollection`.
  - **`LegacyApkgReader`** — opens `collection.anki21`/`.anki2` with `SQLiteDatabase.openDatabase(..., OPEN_READONLY)`;
    notetypes from `col.models` JSON; notes from the `notes` table; media from the `media` JSON map.
  - **`V3ApkgReader`** — zstd-decompresses `collection.anki21b` to a temp `.db`, opens it; notetypes
    from `notetypes`+`fields` tables; notes from the `notes` table; media via the manifest reader.
- **`ApkgFormatDetector`** — inspects the extracted dir and returns the right reader
  (`collection.anki21b` present → V3; else legacy). Unknown → `unsupportedSchema`.
- **`ApkgMediaManifestReader`** — maps numbered files → original names. Tries JSON first
  (legacy); falls back to a small hand-rolled protobuf reader for Anki's `MediaEntries`
  message (extracting `name` per entry), zstd-decompressing the manifest and each blob as
  needed. No full protobuf dependency.
- **`ApkgFieldProcessor`** — pure function `process(content, mediaMap): (text, image?, audio?)`:
  first `<img src="…">` → image (mapped + tag removed), first `[sound:…]` → audio (mapped +
  tag removed), strip all `<[^>]+>`, trim.
- **`ApkgImportService`** — orchestration seam (interface + impl), faked in tests:
  - `suspend fun parse(uri: Uri): ParsedCollection`
  - `fun filterNotes(collection, noteTypeId): List<AnkiNote>`
  - `suspend fun previewCards(notes, noteType, frontIdx, backIdx, media, importMedia): List<ApkgPreviewCard>`
  - `suspend fun import(cards: List<ApkgPreviewCard>, deckName: String): Unit`
- **`MediaManager`** gains `suspend fun importImage(bytes, ext): String` and
  `importAudio(bytes, ext): String` — save locally **preserving the original extension**
  (apkg media is png/gif/mp3/ogg, not only jpg/m4a; extension also drives the uploader's
  images/audio folder routing). `saveImage`/`saveAudio` remain for the card form.
- **UI** — `ApkgImportViewModel` (step state machine + selections) and Compose step screens
  (`NoteTypeStep`, `FieldMappingStep`, `PreviewStep`) hosted as a flow from Library. Persists
  via existing `DeckRepository.upsert` + `CardRepository.upsert`. Registered in Koin.

## Dependencies

- **zstd** — `com.github.luben:zstd-jni` (native libs per ABI; minSdk 24 OK). Used by
  `V3ApkgReader` and the manifest/blob decompression.
- **No protobuf library** — the `MediaEntries` manifest is parsed by a small hand-rolled
  wire-format reader (length-delimited entries; extract field 1 = name). The manifest reader
  tries JSON first and only falls back to protobuf for the new format.

## Data flow

SAF `Uri` → copy to temp file → `ApkgUnzipper` → `ApkgFormatDetector` picks a reader →
`ParsedCollection` → validate (notetypes & notes non-empty) → user picks notetype + front/back
fields → `previewCards` (processes fields; if **Import media**, saves each referenced file via
`MediaManager.importImage/importAudio`, building an originalName → localName map; first media
wins) → user selects cards → create deck (`DeckRepository.upsert`) + insert cards
(`CardRepository.upsert`, new FSRS, `source = "apkg"`, `imagePath = null`) → temp dir deleted.

## Error handling

Sealed `ApkgImportError`: `FileAccess`, `InvalidStructure`, `MissingDatabase`,
`DatabaseCorrupted`, `UnsupportedSchema` (schema version newer than handled, or unknown
container), `MediaExtractionFailed(filename)`. Surfaced as a dialog with a clear message
(e.g. unsupported → "This file uses a newer Anki format we don't support yet."). A failed
media save degrades to a text-only card (does not abort the import). Temp directories are
always removed in a `finally`.

## Testing

- **Readers** — unit tests against tiny fixture `.apkg` files built programmatically in the
  test (zip + a minimal SQLite db for legacy; zstd-compressed db + protobuf manifest for v3),
  asserting notetypes / notes / media parse correctly. (`SQLiteDatabase` needs Robolectric or
  instrumentation; if JVM-only is required, the SQLite read is wrapped behind a thin seam that
  is faked, and the row→model mapping is unit-tested directly.)
- **`ApkgMediaManifestReader`** — JSON path and the hand-rolled protobuf path against
  byte fixtures.
- **`ApkgFieldProcessor`** — pure-function tests: img/sound extraction, HTML stripping, empty
  handling, first-media-wins (front before back).
- **`ApkgImportService`** — `previewCards` (field mapping, skip-empty, media on/off) against a
  fake reader; `import` creates exactly one deck + N cards with correct `source`, media
  filenames, and new-FSRS state via fake repositories.
- **`ApkgImportViewModel`** — step transitions, field-mapping validation (front ≠ back),
  selection count, error surfacing.
- **`MediaManager.importImage/importAudio`** — preserves the supplied extension; writes
  locally with `imagePath` semantics unchanged (extends the existing `MediaManagerTest`).
- Compose step screens: build-verified (no UI unit tests, consistent with the codebase).

## Out of scope (explicit)

Cloze conversion; Anki template rendering; importing multiple notetypes in a single pass;
importing Anki scheduling / review history; importing Anki decks or sub-deck hierarchy;
`.colpkg`; multiple images or audio per card; LaTeX/MathJax rendering; deck-level "import into
this deck" entry point (Library-level new-deck only for v1).
