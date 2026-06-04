# CSV Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users import a CSV/TSV/delimited-text file of flashcards into Azri via a parse → map two columns → preview → import wizard, reachable from the existing Library "Import" button.

**Architecture:** Mirror the existing `.apkg` import feature. Pure logic in `core/csv/` (a `CsvParser` over the `kotlin-csv` library + a `CsvImportService` that reads the SAF `Uri` and persists cards), UI in `feature/csvimport/` (a `CsvImportViewModel` step machine + a `CsvImportScreen` Compose wizard). `AzriNavHost` reads the picked file's SAF display name and routes `.apkg` to the apkg wizard and `.csv`/`.tsv`/`.txt` to the new CSV wizard. Cards persist via `CardRepository.upsert` with `source = "csv"` and fresh FSRS.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Koin, `kotlin-csv-jvm`, kotlinx-coroutines, JUnit4 + mockk + coroutines-test. Build with `JAVA_HOME=/opt/homebrew/opt/openjdk` (JDK 21 from prior sessions is gone; Gradle 9.4.1 builds on the installed JDK 26). All Gradle commands prefix `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`.

---

## File Structure

**Create:**
- `app/src/main/java/nart/simpleanki/core/csv/CsvModels.kt` — `ParsedCsv`, `CsvPreviewCard`, sealed `CsvImportError`.
- `app/src/main/java/nart/simpleanki/core/csv/CsvParser.kt` — pure object: `detectDelimiter`, `parse`, header de-dup, BOM strip, ragged-row padding.
- `app/src/main/java/nart/simpleanki/core/csv/CsvImportService.kt` — interface + `DefaultCsvImportService` (reads `Uri`, validates, builds preview cards, persists).
- `app/src/main/java/nart/simpleanki/feature/csvimport/CsvImportViewModel.kt` — step state machine.
- `app/src/main/java/nart/simpleanki/feature/csvimport/CsvImportScreen.kt` — Compose wizard.
- `app/src/test/java/nart/simpleanki/core/csv/CsvParserTest.kt`
- `app/src/test/java/nart/simpleanki/core/csv/CsvImportServiceTest.kt`
- `app/src/test/java/nart/simpleanki/core/csv/FakeCsvImportService.kt`
- `app/src/test/java/nart/simpleanki/feature/csvimport/CsvImportViewModelTest.kt`

**Modify:**
- `gradle/libs.versions.toml` — add `kotlinCsv` version + `kotlin-csv` library.
- `app/build.gradle.kts` — add `implementation(libs.kotlin.csv)`.
- `app/src/main/java/nart/simpleanki/di/AppModule.kt` — register `CsvImportService` + `CsvImportViewModel`.
- `app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt` — display-name routing + CSV overlay; derive apkg deck name from filename.

---

## Task 1: Add the kotlin-csv dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the version to the catalog**

In `gradle/libs.versions.toml`, under `[versions]`, add (alphabetical-ish, near `kotlin`):

```toml
kotlinCsv = "1.10.0"
```

Under `[libraries]`, add:

```toml
kotlin-csv = { group = "com.github.doyaaaaaken", name = "kotlin-csv-jvm", version.ref = "kotlinCsv" }
```

- [ ] **Step 2: Add the implementation dependency**

In `app/build.gradle.kts`, next to `implementation(libs.zstd.jni)`, add:

```kotlin
implementation(libs.kotlin.csv)
```

- [ ] **Step 3: Verify it resolves**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:dependencies --configuration debugRuntimeClasspath -q 2>&1 | grep -i kotlin-csv`
Expected: a line showing `com.github.doyaaaaaken:kotlin-csv-jvm:1.10.0`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "Add kotlin-csv dependency for CSV import"
```

---

## Task 2: CSV domain models

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/csv/CsvModels.kt`

No test (plain data classes; exercised by later tasks).

- [ ] **Step 1: Write the models**

```kotlin
package nart.simpleanki.core.csv

/**
 * A parsed delimited file. [rows] are positional (list-of-columns), NOT keyed by header,
 * so the same grid serves both header-on and header-off views. Every row is padded to
 * [headers].size, so indexing by column is always safe.
 */
data class ParsedCsv(
    val headers: List<String>,
    val rows: List<List<String>>,
)

/** A card ready for preview/import after column mapping. */
data class CsvPreviewCard(
    val front: String,
    val back: String,
    val selected: Boolean = true,
)

/** Import failures surfaced to the UI. */
sealed class CsvImportError(message: String) : Exception(message) {
    object FileAccess : CsvImportError("Unable to open the file. Please try again.")
    object EmptyFile : CsvImportError("This file has no rows to import.")
    object TooFewColumns : CsvImportError("A CSV needs at least two columns (a front and a back).")
    object ParseFailed : CsvImportError("We couldn't read this file as CSV.")
}
```

- [ ] **Step 2: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin -q 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL` (no errors).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/csv/CsvModels.kt
git commit -m "Add CSV import domain models"
```

---

## Task 3: CsvParser (delimiter detection + parsing)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/csv/CsvParser.kt`
- Test: `app/src/test/java/nart/simpleanki/core/csv/CsvParserTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package nart.simpleanki.core.csv

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParserTest {

    @Test fun detectsComma() {
        val p = CsvParser.parse("front,back\nhola,hello", hasHeader = true)
        assertEquals(listOf("front", "back"), p.headers)
        assertEquals(listOf(listOf("hola", "hello")), p.rows)
    }

    @Test fun detectsSemicolon() {
        val p = CsvParser.parse("front;back;extra\na;b;c", hasHeader = true)
        assertEquals(listOf("front", "back", "extra"), p.headers)
        assertEquals(listOf(listOf("a", "b", "c")), p.rows)
    }

    @Test fun detectsTab() {
        val p = CsvParser.parse("front\tback\nhola\thello", hasHeader = true)
        assertEquals(listOf("front", "back"), p.headers)
        assertEquals(listOf(listOf("hola", "hello")), p.rows)
    }

    @Test fun delimiterInsideQuotesIsNotCounted() {
        // The only real delimiter is the tab; the commas live inside a quoted field.
        val p = CsvParser.parse("\"a,b,c\"\tback\nx\ty", hasHeader = true)
        assertEquals(listOf("a,b,c", "back"), p.headers)
        assertEquals(listOf(listOf("x", "y")), p.rows)
    }

    @Test fun quotedFieldWithEmbeddedDelimiterAndNewline() {
        val text = "front,back\n\"line1\nline2\",\"a,b\""
        val p = CsvParser.parse(text, hasHeader = true)
        assertEquals(listOf(listOf("line1\nline2", "a,b")), p.rows)
    }

    @Test fun escapedDoubleQuotes() {
        val p = CsvParser.parse("front,back\n\"she said \"\"hi\"\"\",ok", hasHeader = true)
        assertEquals(listOf(listOf("she said \"hi\"", "ok")), p.rows)
    }

    @Test fun handlesCrLfLineEndings() {
        val p = CsvParser.parse("front,back\r\nhola,hello\r\n", hasHeader = true)
        assertEquals(listOf(listOf("hola", "hello")), p.rows)
    }

    @Test fun stripsUtf8Bom() {
        // \uFEFF is the BOM; written as an escape (never a literal char) to keep the source clean.
        val p = CsvParser.parse("\uFEFFfront,back\nhola,hello", hasHeader = true)
        assertEquals("front", p.headers.first())
    }

    @Test fun headerOffSynthesisesColumnNamesAndKeepsFirstRow() {
        val p = CsvParser.parse("hola,hello\nadios,bye", hasHeader = false)
        assertEquals(listOf("Column 1", "Column 2"), p.headers)
        assertEquals(listOf(listOf("hola", "hello"), listOf("adios", "bye")), p.rows)
    }

    @Test fun deduplicatesRepeatedHeaders() {
        val p = CsvParser.parse("word,word,word\na,b,c", hasHeader = true)
        assertEquals(listOf("word", "word_2", "word_3"), p.headers)
    }

    @Test fun ragdedRowsArePaddedToHeaderWidth() {
        val p = CsvParser.parse("a,b,c\n1,2\n3,4,5", hasHeader = true)
        assertEquals(listOf(listOf("1", "2", ""), listOf("3", "4", "5")), p.rows)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.csv.CsvParserTest" 2>&1 | tail -15`
Expected: FAIL — compilation error `Unresolved reference: CsvParser`.

- [ ] **Step 3: Write the implementation**

```kotlin
package nart.simpleanki.core.csv

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader

/**
 * Parses delimited text into a [ParsedCsv]. Delimiter is auto-detected (comma/semicolon/tab)
 * from the first physical line, ignoring delimiters inside quoted fields. RFC 4180 quoting,
 * escaped quotes, and embedded newlines are handled by kotlin-csv.
 */
object CsvParser {

    private val CANDIDATES = listOf(',', ';', '\t')

    fun detectDelimiter(firstLine: String): Char {
        val counts = IntArray(CANDIDATES.size)
        var inQuotes = false
        for (ch in firstLine) {
            if (ch == '"') { inQuotes = !inQuotes; continue }
            if (inQuotes) continue
            val idx = CANDIDATES.indexOf(ch)
            if (idx >= 0) counts[idx]++
        }
        // Highest count wins; ties (including all-zero) fall back to comma.
        var best = 0
        for (i in 1 until counts.size) if (counts[i] > counts[best]) best = i
        return if (counts[best] == 0) ',' else CANDIDATES[best]
    }

    fun parse(text: String, hasHeader: Boolean): ParsedCsv {
        val clean = text.removePrefix("\uFEFF")
        val firstLine = clean.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        val delimiter = detectDelimiter(firstLine)

        // skipEmptyLine drops blank lines (incl. a trailing newline's phantom row) — they aren't cards.
        val grid = csvReader { this.delimiter = delimiter; this.skipEmptyLine = true }.readAll(clean)
        if (grid.isEmpty()) return ParsedCsv(emptyList(), emptyList())

        val width = grid.maxOf { it.size }
        val padded = grid.map { row -> if (row.size < width) row + List(width - row.size) { "" } else row }

        return if (hasHeader) {
            ParsedCsv(headers = dedupe(padded.first()), rows = padded.drop(1))
        } else {
            ParsedCsv(headers = (1..width).map { "Column $it" }, rows = padded)
        }
    }

    /** Mirrors iOS: repeated names become name, name_2, name_3, … */
    private fun dedupe(headers: List<String>): List<String> {
        val seen = HashMap<String, Int>()
        return headers.map { h ->
            val n = (seen[h] ?: 0) + 1
            seen[h] = n
            if (n == 1) h else "${h}_$n"
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.csv.CsvParserTest" 2>&1 | tail -15`
Expected: `BUILD SUCCESSFUL`, all 11 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/csv/CsvParser.kt app/src/test/java/nart/simpleanki/core/csv/CsvParserTest.kt
git commit -m "Add CsvParser with delimiter detection and RFC 4180 parsing"
```

---

## Task 4: CsvImportService (validate, previewCards, import)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/csv/CsvImportService.kt`
- Test: `app/src/test/java/nart/simpleanki/core/csv/CsvImportServiceTest.kt`

The `parse(uri, …)` method needs an Android `Context` (`contentResolver`), so — exactly like `ApkgImportServiceTest` — the unit test exercises `validate`, `previewCards`, and `import` only, using the in-memory `FakeDeckDao`/`FakeCardDao` already in the repo.

- [ ] **Step 1: Write the failing test**

```kotlin
package nart.simpleanki.core.csv

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.data.repository.FakeDeckDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CsvImportServiceTest {

    private fun service(): Triple<DefaultCsvImportService, FakeDeckDao, FakeCardDao> {
        val deckDao = FakeDeckDao(); val cardDao = FakeCardDao()
        var seq = 0
        val svc = DefaultCsvImportService(
            deckRepository = DeckRepository(deckDao) { 1L },
            cardRepository = CardRepository(cardDao) { 1L },
            appContext = null,
            idGenerator = { "id${seq++}" },
            now = { 1L },
        )
        return Triple(svc, deckDao, cardDao)
    }

    private val parsed = ParsedCsv(
        headers = listOf("front", "back"),
        rows = listOf(
            listOf("hola", "hello"),
            listOf("  adios  ", "  bye  "),  // trimmed
            listOf("", "orphan"),            // empty front -> skipped
            listOf("solo", ""),              // empty back  -> skipped
        ),
    )

    @Test fun previewCards_trims_andSkipsEmptySides() {
        val (svc, _, _) = service()
        val cards = svc.previewCards(parsed, frontCol = 0, backCol = 1)
        assertEquals(2, cards.size)
        assertEquals("hola", cards[0].front); assertEquals("hello", cards[0].back)
        assertEquals("adios", cards[1].front); assertEquals("bye", cards[1].back)
    }

    @Test fun previewCards_respectsColumnIndices() {
        val (svc, _, _) = service()
        val cards = svc.previewCards(parsed, frontCol = 1, backCol = 0)
        assertEquals("hello", cards[0].front); assertEquals("hola", cards[0].back)
    }

    @Test fun validate_throwsOnNoRows() {
        val (svc, _, _) = service()
        assertThrows(CsvImportError.EmptyFile::class.java) {
            svc.validate(ParsedCsv(listOf("a", "b"), emptyList()))
        }
    }

    @Test fun validate_throwsOnSingleColumn() {
        val (svc, _, _) = service()
        assertThrows(CsvImportError.TooFewColumns::class.java) {
            svc.validate(ParsedCsv(listOf("only"), listOf(listOf("x"))))
        }
    }

    @Test fun import_onlySelected_intoOneNewDeck_withCsvSource() = runTest {
        val (svc, deckDao, cardDao) = service()
        val cards = listOf(
            CsvPreviewCard("F1", "B1", selected = true),
            CsvPreviewCard("F2", "B2", selected = false),  // skipped
        )
        svc.import(cards, deckName = "MyDeck")
        val decks = deckDao.observeAll().first()
        assertEquals(listOf("MyDeck"), decks.map { it.name })
        val saved = cardDao.observeAll().first()
        assertEquals(listOf("F1"), saved.map { it.front })
        val card = saved.single()
        assertEquals("csv", card.source)
        assertEquals(0, card.fsrsState)
        assertEquals(decks.single().id, card.deckId)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.csv.CsvImportServiceTest" 2>&1 | tail -15`
Expected: FAIL — `Unresolved reference: DefaultCsvImportService`.

- [ ] **Step 3: Write the implementation**

```kotlin
package nart.simpleanki.core.csv

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Deck
import java.util.UUID

interface CsvImportService {
    /** Reads [uri] as UTF-8, parses, and validates. Throws [CsvImportError] on failure. */
    suspend fun parse(uri: Uri, hasHeader: Boolean): ParsedCsv
    fun validate(parsed: ParsedCsv)
    fun previewCards(parsed: ParsedCsv, frontCol: Int, backCol: Int): List<CsvPreviewCard>
    suspend fun import(cards: List<CsvPreviewCard>, deckName: String)
}

class DefaultCsvImportService(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val appContext: Context? = null,        // null in unit tests (parse not exercised there)
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : CsvImportService {

    override suspend fun parse(uri: Uri, hasHeader: Boolean): ParsedCsv = withContext(Dispatchers.IO) {
        val ctx = appContext ?: throw CsvImportError.FileAccess
        // CSV files are small; read straight into memory (no temp file needed).
        val text = try {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw CsvImportError.FileAccess
        } catch (e: CsvImportError) {
            throw e
        } catch (e: Exception) {
            throw CsvImportError.FileAccess
        }
        val parsed = try {
            CsvParser.parse(text, hasHeader)
        } catch (e: Exception) {
            throw CsvImportError.ParseFailed
        }
        validate(parsed)
        parsed
    }

    override fun validate(parsed: ParsedCsv) {
        if (parsed.rows.isEmpty()) throw CsvImportError.EmptyFile
        if (parsed.headers.size < 2) throw CsvImportError.TooFewColumns
    }

    override fun previewCards(parsed: ParsedCsv, frontCol: Int, backCol: Int): List<CsvPreviewCard> =
        parsed.rows.mapNotNull { row ->
            val front = row.getOrNull(frontCol)?.trim().orEmpty()
            val back = row.getOrNull(backCol)?.trim().orEmpty()
            if (front.isEmpty() || back.isEmpty()) null else CsvPreviewCard(front, back)
        }

    override suspend fun import(cards: List<CsvPreviewCard>, deckName: String) {
        val toImport = cards.filter { it.selected }
        if (toImport.isEmpty()) return
        val t = now()
        val deck = Deck(id = idGenerator(), name = deckName, dateCreated = t, lastModified = t)
        deckRepository.upsert(deck)
        for (c in toImport) {
            cardRepository.upsert(
                Card(
                    id = idGenerator(), front = c.front, back = c.back,
                    image = null, audioName = null, imagePath = null, audioPath = null,
                    deckId = deck.id, dateCreated = t, lastModified = t,
                    fsrsDue = t, fsrsState = CardState.New.value, source = "csv",
                ),
            )
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.csv.CsvImportServiceTest" 2>&1 | tail -15`
Expected: `BUILD SUCCESSFUL`, all 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/csv/CsvImportService.kt app/src/test/java/nart/simpleanki/core/csv/CsvImportServiceTest.kt
git commit -m "Add CsvImportService with validation, preview, and import"
```

---

## Task 5: CsvImportViewModel (step machine) + fake service

**Files:**
- Create: `app/src/test/java/nart/simpleanki/core/csv/FakeCsvImportService.kt`
- Create: `app/src/main/java/nart/simpleanki/feature/csvimport/CsvImportViewModel.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/csvimport/CsvImportViewModelTest.kt`

Toggling "first row is header" never changes the *number* of columns, only whether row 1 is a name or data — so `frontCol`/`backCol` stay valid across a re-parse and need no clamping.

- [ ] **Step 1: Write the fake service**

```kotlin
package nart.simpleanki.core.csv

import android.net.Uri

class FakeCsvImportService(
    var parsed: ParsedCsv = ParsedCsv(emptyList(), emptyList()),
    var parseError: Throwable? = null,
) : CsvImportService {
    var lastHasHeader: Boolean? = null
    var importedDeckName: String? = null
    var importedCards: List<CsvPreviewCard> = emptyList()

    override suspend fun parse(uri: Uri, hasHeader: Boolean): ParsedCsv {
        lastHasHeader = hasHeader
        parseError?.let { throw it }
        return parsed
    }

    override fun validate(parsed: ParsedCsv) {}

    override fun previewCards(parsed: ParsedCsv, frontCol: Int, backCol: Int): List<CsvPreviewCard> =
        parsed.rows.mapNotNull { row ->
            val f = row.getOrNull(frontCol)?.trim().orEmpty()
            val b = row.getOrNull(backCol)?.trim().orEmpty()
            if (f.isEmpty() || b.isEmpty()) null else CsvPreviewCard(f, b)
        }

    override suspend fun import(cards: List<CsvPreviewCard>, deckName: String) {
        importedCards = cards.filter { it.selected }
        importedDeckName = deckName
    }
}
```

- [ ] **Step 2: Write the failing ViewModel test**

```kotlin
package nart.simpleanki.feature.csvimport

import android.net.Uri
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nart.simpleanki.core.csv.FakeCsvImportService
import nart.simpleanki.core.csv.ParsedCsv
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CsvImportViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val parsed = ParsedCsv(
        headers = listOf("front", "back"),
        rows = listOf(listOf("hola", "hello"), listOf("adios", "bye")),
    )

    private fun vm(service: FakeCsvImportService) = CsvImportViewModel(service, "MyDeck")

    @Test fun parse_advancesToColumnMapping_withDefaults() = runTest {
        val vm = vm(FakeCsvImportService(parsed = parsed))
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        assertEquals(ImportStep.ColumnMapping, vm.uiState.value.step)
        assertEquals(listOf("front", "back"), vm.uiState.value.headers)
        assertEquals(0, vm.uiState.value.frontCol)
        assertEquals(1, vm.uiState.value.backCol)
        assertEquals(2, vm.uiState.value.sampleRows.size)
    }

    @Test fun setHasHeader_reparsesWithNewFlag() = runTest {
        val service = FakeCsvImportService(parsed = parsed)
        val vm = vm(service)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        vm.setHasHeader(false); runCurrent()
        assertFalse(vm.uiState.value.hasHeader)
        assertEquals(false, service.lastHasHeader)
    }

    @Test fun sameFrontAndBack_blocksPreview() = runTest {
        val vm = vm(FakeCsvImportService(parsed = parsed))
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        vm.setBackCol(0)
        assertFalse(vm.uiState.value.canGeneratePreview)
        vm.setBackCol(1)
        assertTrue(vm.uiState.value.canGeneratePreview)
    }

    @Test fun generatePreview_thenImport_passesSelectedCards() = runTest {
        val service = FakeCsvImportService(parsed = parsed)
        val vm = vm(service)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        vm.generatePreview()
        assertEquals(ImportStep.Preview, vm.uiState.value.step)
        assertEquals(2, vm.uiState.value.previewCards.size)
        vm.import {}; runCurrent()
        assertEquals("MyDeck", service.importedDeckName)
        assertEquals(2, service.importedCards.size)
    }

    @Test fun toggleCard_deselects_excludesFromImport_andHandlesOutOfBounds() = runTest {
        val service = FakeCsvImportService(parsed = parsed)
        val vm = vm(service)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        vm.generatePreview()
        vm.toggleCard(0); vm.toggleCard(1)   // deselect both
        vm.toggleCard(99)                     // out of bounds: no-op, must not crash
        vm.import {}; runCurrent()
        assertEquals(ImportStep.Preview, vm.uiState.value.step)  // nothing selected -> short-circuits
        assertEquals(0, service.importedCards.size)
    }

    @Test fun parseError_setsErrorMessage() = runTest {
        val vm = vm(FakeCsvImportService(parseError = RuntimeException("boom")))
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        assertTrue(vm.uiState.value.error != null)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.csvimport.CsvImportViewModelTest" 2>&1 | tail -15`
Expected: FAIL — `Unresolved reference: CsvImportViewModel`.

- [ ] **Step 4: Write the ViewModel**

```kotlin
package nart.simpleanki.feature.csvimport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nart.simpleanki.core.csv.CsvImportError
import nart.simpleanki.core.csv.CsvImportService
import nart.simpleanki.core.csv.CsvPreviewCard
import nart.simpleanki.core.csv.ParsedCsv

enum class ImportStep { Parsing, ColumnMapping, Preview, Importing }

data class CsvImportUiState(
    val step: ImportStep = ImportStep.Parsing,
    val headers: List<String> = emptyList(),
    val sampleRows: List<List<String>> = emptyList(),
    val frontCol: Int = 0,
    val backCol: Int = 1,
    val hasHeader: Boolean = true,
    val previewCards: List<CsvPreviewCard> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
) {
    val canGeneratePreview: Boolean get() = frontCol != backCol
}

class CsvImportViewModel(
    private val service: CsvImportService,
    private val deckName: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CsvImportUiState())
    val uiState: StateFlow<CsvImportUiState> = _uiState.asStateFlow()

    private var uri: Uri? = null
    private var parsed: ParsedCsv? = null

    fun parse(uri: Uri) {
        this.uri = uri
        load(_uiState.value.hasHeader, advance = true)
    }

    fun setHasHeader(value: Boolean) {
        _uiState.value = _uiState.value.copy(hasHeader = value)
        load(value, advance = false)
    }

    private fun load(hasHeader: Boolean, advance: Boolean) {
        val u = uri ?: return
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(
            busy = true, error = null,
            step = if (advance) ImportStep.Parsing else _uiState.value.step,
        )
        viewModelScope.launch {
            runCatching { service.parse(u, hasHeader) }
                .onSuccess { data ->
                    parsed = data
                    _uiState.value = _uiState.value.copy(
                        step = ImportStep.ColumnMapping, busy = false,
                        headers = data.headers, sampleRows = data.rows.take(3),
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(busy = false, error = messageFor(it)) }
        }
    }

    fun setFrontCol(index: Int) { _uiState.value = _uiState.value.copy(frontCol = index) }
    fun setBackCol(index: Int) { _uiState.value = _uiState.value.copy(backCol = index) }

    fun generatePreview() {
        val data = parsed ?: return
        val s = _uiState.value
        if (s.frontCol == s.backCol) return
        val cards = service.previewCards(data, s.frontCol, s.backCol)
        _uiState.value = s.copy(step = ImportStep.Preview, previewCards = cards)
    }

    fun toggleCard(index: Int) {
        val list = _uiState.value.previewCards.toMutableList()
        list.getOrNull(index)?.let { list[index] = it.copy(selected = !it.selected) }
        _uiState.value = _uiState.value.copy(previewCards = list)
    }

    fun import(onComplete: () -> Unit) {
        val selected = _uiState.value.previewCards.filter { it.selected }
        if (selected.isEmpty()) return
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(step = ImportStep.Importing, busy = true)
        viewModelScope.launch {
            runCatching { service.import(selected, deckName) }
                .onSuccess { onComplete() }
                .onFailure { _uiState.value = _uiState.value.copy(step = ImportStep.Preview, busy = false, error = messageFor(it)) }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    private fun messageFor(t: Throwable): String =
        (t as? CsvImportError)?.message ?: "Import failed. Please try again."
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.csvimport.CsvImportViewModelTest" 2>&1 | tail -15`
Expected: `BUILD SUCCESSFUL`, all 6 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/nart/simpleanki/core/csv/FakeCsvImportService.kt app/src/main/java/nart/simpleanki/feature/csvimport/CsvImportViewModel.kt app/src/test/java/nart/simpleanki/feature/csvimport/CsvImportViewModelTest.kt
git commit -m "Add CsvImportViewModel step machine"
```

---

## Task 6: CsvImportScreen (Compose wizard)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/feature/csvimport/CsvImportScreen.kt`

Build-verified only (no Compose UI unit tests, consistent with the codebase). Mirrors `ApkgImportScreen`'s structure: `Scaffold` + `TopAppBar` close button, a `when(step)` body, an error `AlertDialog`, and an `ExposedDropdownMenuBox`-based column picker. Column dropdowns show header *names* but report the selected *index*.

- [ ] **Step 1: Write the screen**

```kotlin
package nart.simpleanki.feature.csvimport

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(uri: Uri, deckName: String, onClose: () -> Unit) {
    val vm: CsvImportViewModel = koinViewModel { parametersOf(deckName) }
    val state by vm.uiState.collectAsState()
    LaunchedEffect(uri) { vm.parse(uri) }

    state.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearError(); onClose() },
            confirmButton = { TextButton(onClick = { vm.clearError(); onClose() }) { Text("OK") } },
            title = { Text("Import error") },
            text = { Text(msg) },
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Import CSV") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") } },
        )
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            when (state.step) {
                ImportStep.Parsing, ImportStep.Importing -> CircularProgressIndicator()
                ImportStep.ColumnMapping -> ColumnMappingStep(state, vm)
                ImportStep.Preview -> PreviewStep(state, vm, onClose)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnMappingStep(state: CsvImportUiState, vm: CsvImportViewModel) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ColumnDropdown("Front column", state.headers, state.frontCol, vm::setFrontCol)
        ColumnDropdown("Back column", state.headers, state.backCol, vm::setBackCol)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.hasHeader, onCheckedChange = vm::setHasHeader)
            Spacer(Modifier.width(8.dp))
            Text("First row is header")
        }
        if (state.sampleRows.isNotEmpty()) {
            HorizontalDivider()
            Text("Preview", style = MaterialTheme.typography.titleSmall)
            state.sampleRows.forEach { row ->
                val front = row.getOrNull(state.frontCol).orEmpty()
                val back = row.getOrNull(state.backCol).orEmpty()
                Column {
                    Text("Q: $front", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text("A: $back", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        Button(onClick = vm::generatePreview, enabled = state.canGeneratePreview && !state.busy) {
            Text("Next")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnDropdown(label: String, headers: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = headers.getOrNull(selected).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            headers.forEachIndexed { index, name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(index); expanded = false })
            }
        }
    }
}

@Composable
private fun PreviewStep(state: CsvImportUiState, vm: CsvImportViewModel, onClose: () -> Unit) {
    val selectedCount = state.previewCards.count { it.selected }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("$selectedCount of ${state.previewCards.size} selected", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.previewCards) { i, card ->
                ListItem(
                    leadingContent = { Checkbox(checked = card.selected, onCheckedChange = { vm.toggleCard(i) }) },
                    headlineContent = { Text(card.front, maxLines = 1) },
                    supportingContent = { Text(card.back, maxLines = 1) },
                )
            }
        }
        Button(
            onClick = { vm.import(onComplete = onClose) },
            enabled = selectedCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Import $selectedCount cards")
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin -q 2>&1 | tail -10`
Expected: `BUILD SUCCESSFUL` (deprecation warnings on `MenuAnchorType` are pre-existing and OK).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/csvimport/CsvImportScreen.kt
git commit -m "Add CsvImportScreen wizard UI"
```

---

## Task 7: Register in Koin

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`

- [ ] **Step 1: Add imports**

Near the other apkg imports (around lines 33-38), add:

```kotlin
import nart.simpleanki.core.csv.CsvImportService
import nart.simpleanki.core.csv.DefaultCsvImportService
import nart.simpleanki.feature.csvimport.CsvImportViewModel
```

- [ ] **Step 2: Register the service + ViewModel**

Immediately after the `viewModel { (deckName: String) -> ApkgImportViewModel(...) }` line (around line 87), add:

```kotlin
single<CsvImportService> {
    DefaultCsvImportService(
        deckRepository = get(),
        cardRepository = get(),
        appContext = androidContext(),
    )
}
viewModel { (deckName: String) -> CsvImportViewModel(service = get(), deckName = deckName) }
```

- [ ] **Step 3: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin -q 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nart/simpleanki/di/AppModule.kt
git commit -m "Register CSV import in Koin"
```

---

## Task 8: Route the Library import picker by file type

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt`

Today the picker result feeds a single `importUri` → `ApkgImportScreen`. Change it to read the SAF display name, derive the extension + base name, and route to the right wizard. `content://` URIs have no path extension, so the display name must come from `ContentResolver` (`OpenableColumns.DISPLAY_NAME`).

- [ ] **Step 1: Add imports**

Add to the import block:

```kotlin
import android.provider.OpenableColumns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import nart.simpleanki.feature.csvimport.CsvImportScreen
```

- [ ] **Step 2: Add a routing target type**

At the bottom of `AzriNavHost.kt` (file scope, next to the existing `private fun NavController.switchTab`), add:

```kotlin
/** A picked import file routed to a wizard: the source [uri] and the deck name derived from the filename. */
private data class ImportTarget(val uri: Uri, val deckName: String)

/** SAF content:// URIs have no path extension; recover the real filename from the provider. */
private fun displayName(resolver: android.content.ContentResolver, uri: Uri): String? =
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
```

- [ ] **Step 3: Replace the picker state + callback**

Find (around lines 75-78):

```kotlin
    var importUri by remember { mutableStateOf<Uri?>(null) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importUri = uri
    }
```

Replace with:

```kotlin
    val context = LocalContext.current
    var apkgImport by remember { mutableStateOf<ImportTarget?>(null) }
    var csvImport by remember { mutableStateOf<ImportTarget?>(null) }
    var unsupportedImport by remember { mutableStateOf(false) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = displayName(context.contentResolver, uri).orEmpty()
        val ext = name.substringAfterLast('.', "").lowercase()
        val base = name.substringBeforeLast('.', name)
        when (ext) {
            "apkg" -> apkgImport = ImportTarget(uri, base.ifBlank { "Imported deck" })
            "csv", "tsv", "txt" -> csvImport = ImportTarget(uri, base.ifBlank { "Imported cards" })
            else -> unsupportedImport = true
        }
    }
```

- [ ] **Step 4: Replace the apkg overlay block**

Find (around lines 274-276):

```kotlin
    importUri?.let { uri ->
        ApkgImportScreen(uri = uri, deckName = "Imported deck", onClose = { importUri = null })
    }
```

Replace with:

```kotlin
    apkgImport?.let { t ->
        ApkgImportScreen(uri = t.uri, deckName = t.deckName, onClose = { apkgImport = null })
    }
    csvImport?.let { t ->
        CsvImportScreen(uri = t.uri, deckName = t.deckName, onClose = { csvImport = null })
    }
    if (unsupportedImport) {
        AlertDialog(
            onDismissRequest = { unsupportedImport = false },
            confirmButton = { TextButton(onClick = { unsupportedImport = false }) { Text("OK") } },
            title = { Text("Unsupported file") },
            text = { Text("Pick a .apkg, .csv, .tsv, or .txt file to import.") },
        )
    }
```

- [ ] **Step 5: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin -q 2>&1 | tail -10`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt
git commit -m "Route Library import by file type (apkg vs csv)"
```

---

## Task 9: Full build + test gate

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit test suite**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`; the new `CsvParserTest`, `CsvImportServiceTest`, and `CsvImportViewModelTest` all pass; no pre-existing tests regress.

- [ ] **Step 2: Assemble the debug APK**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:assembleDebug 2>&1 | tail -10`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Final commit (if anything outstanding)**

```bash
git status
# If clean, nothing to do. Otherwise stage and commit the remaining changes.
```

---

## Manual verification checklist (post-implementation, on emulator)

These are for the human/agent to sanity-check after the plan completes — not automated:

- Library → Import → pick a comma `front,back` CSV with a header → column mapping defaults to front/back → Next → preview lists cards → Import → new deck named after the file appears with the cards.
- Toggle "First row is header" OFF on a headerless file → the first row stops being eaten (appears as a card; columns labelled "Column 1/2").
- Import a semicolon-delimited file → still parses into two columns (auto-detect).
- Pick a `.apkg` → still routes to the apkg wizard, now with the deck named after the file.
- Pick an unsupported type (e.g. a `.png`) → "Unsupported file" dialog, no crash.
```
