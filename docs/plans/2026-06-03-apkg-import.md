# .apkg Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import Anki `.apkg` decks (legacy plain-SQLite *and* modern zstd `collection.anki21b`) into Azri via a file → note-type → field-mapping → preview → import wizard.

**Architecture:** Two `AnkiCollectionReader` backends (legacy schema-11, v3 schema-18) behind a format detector, both consuming a thin faked `AnkiSqlite` seam so all row→model mapping is JVM-unit-testable. A pure `ApkgFieldProcessor` strips HTML and extracts the first image/audio. `ApkgImportService` orchestrates parse/preview/import; imported media is saved on-device via `MediaManager` (local-first). A step-machine `ApkgImportViewModel` drives Compose wizard screens launched from Library.

**Tech Stack:** Kotlin, Coroutines, Koin, Jetpack Compose, `java.util.zip`, Android `SQLiteDatabase` (behind a seam), `com.github.luben:zstd-jni` (zstd), `org.json` (legacy notetype JSON; built into Android, real jar added as a test dependency), JUnit4.

**Conventions:**
- Build green after **every** task. Legacy import is fully working after Task 12; v3 is added in Tasks 13–15.
- Commit messages: plain imperative, **no "claude" mention, no `Co-Authored-By`/attribution trailers**.
- Single test class: `./gradlew testDebugUnitTest --tests "<FQCN>"`. Compile: `./gradlew :app:compileDebugKotlin`. If Gradle complains about Java: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
- New code lives in `app/src/main/java/nart/simpleanki/core/apkg/` (parsing/service) and `app/src/main/java/nart/simpleanki/feature/apkgimport/` (UI). Tests mirror under `app/src/test/...`.

---

## File Structure

**Create — core/apkg:**
- `AnkiModels.kt` — `AnkiNoteType`, `AnkiNote`, `AnkiCollectionData`, `ParsedCollection`, `ApkgPreviewCard`, `ApkgFormat`, sealed `ApkgImportError`.
- `ApkgFieldProcessor.kt` — pure HTML/media field processing.
- `LegacyNoteTypeParser.kt` — `col.models` JSON → `List<AnkiNoteType>` (org.json).
- `AnkiSqlite.kt` — `AnkiSqlite` interface + `AndroidAnkiSqlite` (thin `SQLiteDatabase` impl) + `AnkiSqliteFactory`.
- `AnkiCollectionReader.kt` — interface.
- `LegacyApkgReader.kt` — schema-11 reader.
- `V3ApkgReader.kt` — schema-18 reader.
- `ApkgUnzipper.kt` — zip extraction to a temp dir.
- `ApkgMediaReader.kt` — `media` manifest (JSON + hand-rolled protobuf) + numbered files (+ zstd) → `Map<String, ByteArray>`.
- `ApkgFormatDetector.kt` — choose reader/format from the extracted dir.
- `ApkgImportService.kt` — interface + `DefaultApkgImportService`.

**Create — feature/apkgimport:**
- `ApkgImportViewModel.kt` — step state machine.
- `ApkgImportScreen.kt` — wizard host + step composables.

**Create — tests:**
- `core/apkg/{ApkgFieldProcessorTest, LegacyNoteTypeParserTest, LegacyApkgReaderTest, ApkgMediaReaderTest, V3ApkgReaderTest, ApkgImportServiceTest}.kt`, `FakeAnkiSqlite.kt`, `FakeApkgImportService.kt`.
- `feature/apkgimport/ApkgImportViewModelTest.kt`.
- Extend `core/data/media/MediaManagerTest.kt`.

**Modify:**
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — add zstd (main) + org.json (test).
- `core/data/media/MediaManager.kt` — `importImage/importAudio(bytes, ext)`.
- `di/AppModule.kt` — register service + ViewModel.
- `ui/navigation/AzriNavHost.kt` + `feature/library/LibraryScreen.kt` — Import entry point + SAF picker + flow host.

---

## Task 1: Dependencies + Anki data models

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `app/src/main/java/nart/simpleanki/core/apkg/AnkiModels.kt`

- [ ] **Step 1: Add dependency coordinates to the version catalog**

In `gradle/libs.versions.toml` under `[versions]` add:
```toml
zstd = "1.5.6-8"
orgJson = "20240303"
```
Under `[libraries]` add:
```toml
zstd-jni = { group = "com.github.luben", name = "zstd-jni", version.ref = "zstd" }
org-json = { group = "org.json", name = "json", version.ref = "orgJson" }
```
(If `zstd-jni:1.5.6-8` fails to resolve, use the latest `1.5.x` on Maven Central.)

- [ ] **Step 2: Wire the dependencies in the app module**

In `app/build.gradle.kts` dependencies block add:
```kotlin
    implementation(libs.zstd.jni)
    testImplementation(libs.org.json)
```
(`org.json` is built into Android for `main`; the test jar gives JVM unit tests a real implementation instead of the throwing stub.)

- [ ] **Step 3: Create `AnkiModels.kt`**

```kotlin
package nart.simpleanki.core.apkg

/** A note type ("model") from the Anki collection. */
data class AnkiNoteType(
    val id: Long,
    val name: String,
    val fields: List<String>,
    val sortField: Int,
)

/** A single Anki note (one row of the `notes` table). */
data class AnkiNote(
    val id: Long,
    val guid: String,
    val modelId: Long,
    val fields: List<String>,
    val tags: List<String>,
)

/** What a reader extracts from the collection database (media is read separately). */
data class AnkiCollectionData(
    val noteTypes: List<AnkiNoteType>,
    val notes: List<AnkiNote>,
)

/** Fully parsed package: collection data + media (keyed by original filename). */
data class ParsedCollection(
    val noteTypes: List<AnkiNoteType>,
    val notes: List<AnkiNote>,
    val media: Map<String, ByteArray>,
)

/** A card ready for preview/import after field mapping + processing. */
data class ApkgPreviewCard(
    val front: String,
    val back: String,
    val imageName: String?,
    val audioName: String?,
    val selected: Boolean = true,
)

/** Which on-disk format the package uses. */
enum class ApkgFormat { LEGACY, V3 }

/** Import failures surfaced to the UI. */
sealed class ApkgImportError(message: String) : Exception(message) {
    object FileAccess : ApkgImportError("Unable to access the file.")
    object InvalidStructure : ApkgImportError("Invalid .apkg structure.")
    object MissingDatabase : ApkgImportError("The .apkg is missing its collection database.")
    object DatabaseCorrupted : ApkgImportError("Cannot read the .apkg database; it may be corrupted.")
    object UnsupportedSchema : ApkgImportError("This file uses a newer Anki format we don't support yet.")
    data class MediaExtractionFailed(val filename: String) : ApkgImportError("Failed to extract media: $filename")
}
```

- [ ] **Step 4: Verify it compiles + deps resolve**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/java/nart/simpleanki/core/apkg/AnkiModels.kt
git commit -m "Add apkg import data models and zstd/json dependencies"
```

---

## Task 2: ApkgFieldProcessor (pure)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/apkg/ApkgFieldProcessor.kt`
- Test: `app/src/test/java/nart/simpleanki/core/apkg/ApkgFieldProcessorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package nart.simpleanki.core.apkg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApkgFieldProcessorTest {
    private val media = mapOf("pic.jpg" to "local-1.jpg", "snd.mp3" to "local-1.mp3")

    @Test fun stripsHtml_andTrims() {
        val r = ApkgFieldProcessor.process("<b>Hello</b>&nbsp;world ", media)
        assertEquals("Hello world", r.text)
        assertNull(r.image)
        assertNull(r.audio)
    }

    @Test fun extractsFirstImage_mapsToLocalName_andRemovesTag() {
        val r = ApkgFieldProcessor.process("see <img src=\"pic.jpg\"> here", media)
        assertEquals("see  here".trim(), r.text.trim())
        assertEquals("local-1.jpg", r.image)
    }

    @Test fun extractsSound_mapsToLocalName_andRemovesTag() {
        val r = ApkgFieldProcessor.process("listen [sound:snd.mp3]", media)
        assertEquals("listen", r.text)
        assertEquals("local-1.mp3", r.audio)
    }

    @Test fun unmappedMedia_yieldsNullName_butStillStripsTag() {
        val r = ApkgFieldProcessor.process("x <img src=\"missing.png\"> y", emptyMap())
        assertNull(r.image)
        assertEquals("x  y".trim(), r.text.trim())
    }

    @Test fun decodesCommonEntities() {
        assertEquals("a & b < c > d", ApkgFieldProcessor.process("a &amp; b &lt; c &gt; d", media).text)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.ApkgFieldProcessorTest"`
Expected: FAIL — `ApkgFieldProcessor` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package nart.simpleanki.core.apkg

/**
 * Pure projection of an Anki field's HTML onto Azri's plain-text card.
 * Extracts the first <img src> and first [sound:…], maps each to a saved local
 * filename, removes those tags, strips all remaining HTML, decodes common
 * entities, and trims. LaTeX delimiters are left as literal text (no MathJax on Android).
 */
object ApkgFieldProcessor {
    data class Result(val text: String, val image: String?, val audio: String?)

    private val IMG = Regex("""<img[^>]+src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val SOUND = Regex("""\[sound:([^]]+)]""")
    private val TAG = Regex("""<[^>]+>""")

    fun process(content: String, mediaMap: Map<String, String>): Result {
        var text = content
        var image: String? = null
        var audio: String? = null

        IMG.find(text)?.let { m ->
            image = mediaMap[m.groupValues[1]]
            text = IMG.replaceFirst(text, "")
        }
        SOUND.find(text)?.let { m ->
            audio = mediaMap[m.groupValues[1]]
            text = SOUND.replaceFirst(text, "")
        }

        text = TAG.replace(text, "")
        text = decodeEntities(text)
        text = text.replace(' ', ' ').trim()
        return Result(text, image, audio)
    }

    private fun decodeEntities(s: String): String =
        s.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.ApkgFieldProcessorTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/apkg/ApkgFieldProcessor.kt \
        app/src/test/java/nart/simpleanki/core/apkg/ApkgFieldProcessorTest.kt
git commit -m "Add ApkgFieldProcessor for HTML/media field projection"
```

---

## Task 3: MediaManager.importImage/importAudio (preserve extension)

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/data/media/MediaManager.kt`
- Test: `app/src/test/java/nart/simpleanki/core/data/media/MediaManagerTest.kt`

- [ ] **Step 1: Add failing tests to `MediaManagerTest.kt`**

```kotlin
    @Test fun importImage_preservesExtension() = runTest {
        val (m, _) = managerWith()
        val name = m.importImage(byteArrayOf(1, 2), "png")
        assertTrue(name.endsWith(".png"))
        assertArrayEquals(byteArrayOf(1, 2), m.resolve(name, null)!!.readBytes())
    }

    @Test fun importAudio_preservesExtension() = runTest {
        val (m, _) = managerWith()
        val name = m.importAudio(byteArrayOf(9), "ogg")
        assertTrue(name.endsWith(".ogg"))
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.data.media.MediaManagerTest"`
Expected: FAIL — `importImage`/`importAudio` unresolved.

- [ ] **Step 3: Add the methods to `MediaManager`**

After `saveAudio`, add:
```kotlin
    /** Import: persist [bytes] locally under the given [ext] (preserves the source format). */
    suspend fun importImage(bytes: ByteArray, ext: String): String = saveLocal(bytes, ext.lowercase())
    suspend fun importAudio(bytes: ByteArray, ext: String): String = saveLocal(bytes, ext.lowercase())
```
(They share the existing private `saveLocal(bytes, ext)`. Keeping two names mirrors `saveImage`/`saveAudio` and documents intent at call sites.)

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.data.media.MediaManagerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/data/media/MediaManager.kt \
        app/src/test/java/nart/simpleanki/core/data/media/MediaManagerTest.kt
git commit -m "Add MediaManager import helpers that preserve media extension"
```

---

## Task 4: LegacyNoteTypeParser (org.json, pure)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/apkg/LegacyNoteTypeParser.kt`
- Test: `app/src/test/java/nart/simpleanki/core/apkg/LegacyNoteTypeParserTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package nart.simpleanki.core.apkg

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyNoteTypeParserTest {
    // `col.models` is a JSON object keyed by model id; each model has name, flds[], sortf.
    private val json = """
        {
          "1411914114": {"name":"Basic","sortf":0,
            "flds":[{"name":"Front","ord":0},{"name":"Back","ord":1}]},
          "1607392319": {"name":"Cloze","sortf":0,
            "flds":[{"name":"Text","ord":0},{"name":"Extra","ord":1}]}
        }
    """.trimIndent()

    @Test fun parsesModels_idsNamesAndFieldsInOrder() {
        val types = LegacyNoteTypeParser.parse(json).sortedBy { it.name }
        assertEquals(2, types.size)
        val basic = types.first { it.name == "Basic" }
        assertEquals(1411914114L, basic.id)
        assertEquals(listOf("Front", "Back"), basic.fields)
        assertEquals(0, basic.sortField)
    }

    @Test fun emptyOrInvalid_returnsEmpty() {
        assertEquals(emptyList<AnkiNoteType>(), LegacyNoteTypeParser.parse("{}"))
        assertEquals(emptyList<AnkiNoteType>(), LegacyNoteTypeParser.parse("not json"))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.LegacyNoteTypeParserTest"`
Expected: FAIL — `LegacyNoteTypeParser` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package nart.simpleanki.core.apkg

import org.json.JSONObject

/** Parses Anki legacy `col.models` JSON into note types. The top-level keys are model ids. */
object LegacyNoteTypeParser {
    fun parse(modelsJson: String): List<AnkiNoteType> {
        val root = runCatching { JSONObject(modelsJson) }.getOrNull() ?: return emptyList()
        val result = mutableListOf<AnkiNoteType>()
        for (key in root.keys()) {
            val model = root.optJSONObject(key) ?: continue
            val id = key.toLongOrNull() ?: continue
            val name = model.optString("name").ifEmpty { continue }
            val flds = model.optJSONArray("flds") ?: continue
            val fields = (0 until flds.length()).mapNotNull { i ->
                flds.optJSONObject(i)?.optString("name")?.ifEmpty { null }
            }
            result += AnkiNoteType(id = id, name = name, fields = fields, sortField = model.optInt("sortf", 0))
        }
        return result
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.LegacyNoteTypeParserTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/apkg/LegacyNoteTypeParser.kt \
        app/src/test/java/nart/simpleanki/core/apkg/LegacyNoteTypeParserTest.kt
git commit -m "Add legacy col.models note-type parser"
```

---

## Task 5: AnkiSqlite seam + reader interface + fake

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/apkg/AnkiSqlite.kt`
- Create: `app/src/main/java/nart/simpleanki/core/apkg/AnkiCollectionReader.kt`
- Create (test): `app/src/test/java/nart/simpleanki/core/apkg/FakeAnkiSqlite.kt`

- [ ] **Step 1: Create the seam `AnkiSqlite.kt`**

```kotlin
package nart.simpleanki.core.apkg

import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Minimal read-only SQLite query seam so reader mapping logic is unit-testable. */
interface AnkiSqlite {
    /** Returns each row as a column-name → value map. */
    fun query(sql: String): List<Map<String, Any?>>
    fun close()
}

/** Thin Android implementation over a SQLite file. Verified via instrumentation/manual, not JVM tests. */
class AndroidAnkiSqlite(private val db: SQLiteDatabase) : AnkiSqlite {
    override fun query(sql: String): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                val row = HashMap<String, Any?>(c.columnCount)
                for (i in 0 until c.columnCount) {
                    row[c.getColumnName(i)] = when (c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        android.database.Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                        android.database.Cursor.FIELD_TYPE_NULL -> null
                        else -> c.getString(i)
                    }
                }
                rows += row
            }
        }
        return rows
    }

    override fun close() = db.close()

    companion object {
        /** Opens a SQLite db file read-only. */
        fun open(file: File): AnkiSqlite =
            AndroidAnkiSqlite(SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY))
    }
}
```

- [ ] **Step 2: Create `AnkiCollectionReader.kt`**

```kotlin
package nart.simpleanki.core.apkg

/** Reads note types + notes from an open Anki collection database. */
interface AnkiCollectionReader {
    fun read(db: AnkiSqlite): AnkiCollectionData
}
```

- [ ] **Step 3: Create the test fake `FakeAnkiSqlite.kt`**

```kotlin
package nart.simpleanki.core.apkg

/** In-memory [AnkiSqlite] for tests: maps an SQL string to canned rows. */
class FakeAnkiSqlite(private val responses: Map<String, List<Map<String, Any?>>>) : AnkiSqlite {
    var closed = false
    override fun query(sql: String): List<Map<String, Any?>> =
        responses[sql] ?: error("no canned response for SQL: $sql")
    override fun close() { closed = true }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (No behavior to unit-test yet; the fake is exercised by Task 6.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/apkg/AnkiSqlite.kt \
        app/src/main/java/nart/simpleanki/core/apkg/AnkiCollectionReader.kt \
        app/src/test/java/nart/simpleanki/core/apkg/FakeAnkiSqlite.kt
git commit -m "Add AnkiSqlite seam and collection-reader interface"
```

---

## Task 6: LegacyApkgReader

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/apkg/LegacyApkgReader.kt`
- Test: `app/src/test/java/nart/simpleanki/core/apkg/LegacyApkgReaderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package nart.simpleanki.core.apkg

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyApkgReaderTest {
    private val modelsJson = """{"55":{"name":"Basic","sortf":0,"flds":[{"name":"Front","ord":0},{"name":"Back","ord":1}]}}"""

    private fun db() = FakeAnkiSqlite(
        mapOf(
            "SELECT models FROM col" to listOf(mapOf("models" to modelsJson)),
            "SELECT id, guid, mid, flds, tags FROM notes" to listOf(
                mapOf("id" to 1L, "guid" to "g1", "mid" to 55L, "flds" to "Front1\u001FBack1", "tags" to " tag1 tag2 "),
                mapOf("id" to 2L, "guid" to "g2", "mid" to 55L, "flds" to "Front2\u001FBack2", "tags" to ""),
            ),
        ),
    )

    @Test fun readsNoteTypesAndNotes() {
        val data = LegacyApkgReader().read(db())
        assertEquals(listOf("Front", "Back"), data.noteTypes.single().fields)
        assertEquals(2, data.notes.size)
        assertEquals(listOf("Front1", "Back1"), data.notes[0].fields)
        assertEquals(55L, data.notes[0].modelId)
        assertEquals(listOf("tag1", "tag2"), data.notes[0].tags)
        assertEquals(emptyList<String>(), data.notes[1].tags)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.LegacyApkgReaderTest"`
Expected: FAIL — `LegacyApkgReader` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package nart.simpleanki.core.apkg

/** Reads a legacy (schema 11) collection: note types from `col.models` JSON, notes from the table. */
class LegacyApkgReader : AnkiCollectionReader {
    override fun read(db: AnkiSqlite): AnkiCollectionData {
        val modelsJson = db.query("SELECT models FROM col").firstOrNull()?.get("models") as? String
            ?: throw ApkgImportError.DatabaseCorrupted
        val noteTypes = LegacyNoteTypeParser.parse(modelsJson)

        val notes = db.query("SELECT id, guid, mid, flds, tags FROM notes").mapNotNull { row ->
            val id = (row["id"] as? Long) ?: return@mapNotNull null
            val guid = (row["guid"] as? String) ?: return@mapNotNull null
            val mid = (row["mid"] as? Long) ?: return@mapNotNull null
            val flds = (row["flds"] as? String) ?: return@mapNotNull null
            AnkiNote(
                id = id, guid = guid, modelId = mid,
                fields = flds.split('\u001F'),
                tags = (row["tags"] as? String ?: "").split(' ').filter { it.isNotEmpty() },
            )
        }
        return AnkiCollectionData(noteTypes, notes)
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.LegacyApkgReaderTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/apkg/LegacyApkgReader.kt \
        app/src/test/java/nart/simpleanki/core/apkg/LegacyApkgReaderTest.kt
git commit -m "Add legacy schema-11 apkg collection reader"
```

---

## Task 7: ApkgUnzipper + ApkgMediaReader (JSON path)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/apkg/ApkgUnzipper.kt`
- Create: `app/src/main/java/nart/simpleanki/core/apkg/ApkgMediaReader.kt`
- Test: `app/src/test/java/nart/simpleanki/core/apkg/ApkgMediaReaderTest.kt`

- [ ] **Step 1: Write the failing test (legacy JSON media)**

```kotlin
package nart.simpleanki.core.apkg

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ApkgMediaReaderTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun readsJsonManifest_mapsNumberedFilesToOriginalNames() {
        val dir = tmp.newFolder()
        File(dir, "media").writeText("""{"0":"a.jpg","1":"b.mp3"}""")
        File(dir, "0").writeBytes(byteArrayOf(1, 1))
        File(dir, "1").writeBytes(byteArrayOf(2, 2))

        val media = ApkgMediaReader().read(dir)
        assertEquals(setOf("a.jpg", "b.mp3"), media.keys)
        assertArrayEquals(byteArrayOf(1, 1), media["a.jpg"])
    }

    @Test fun noManifest_returnsEmpty() {
        assertTrue(ApkgMediaReader().read(tmp.newFolder()).isEmpty())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.ApkgMediaReaderTest"`
Expected: FAIL — unresolved.

- [ ] **Step 3: Write `ApkgUnzipper.kt`**

```kotlin
package nart.simpleanki.core.apkg

import java.io.File
import java.util.zip.ZipInputStream

/** Extracts a .apkg (a zip) into [destDir]. Flat archive — no nested dirs expected. */
class ApkgUnzipper {
    fun unzip(apkg: File, destDir: File) {
        destDir.mkdirs()
        ZipInputStream(apkg.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    // Guard against path traversal; archive entries are flat names like "0", "media".
                    val out = File(destDir, File(entry.name).name)
                    out.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
```

- [ ] **Step 4: Write `ApkgMediaReader.kt` (JSON path only for now; protobuf added in Task 14)**

```kotlin
package nart.simpleanki.core.apkg

import org.json.JSONObject
import java.io.File

/**
 * Builds an originalFilename → bytes map from an extracted .apkg directory.
 * Legacy packages ship a JSON `media` manifest ("0" -> "name.jpg") and uncompressed
 * numbered files. (V3 zstd+protobuf handling is added in a later task.)
 */
class ApkgMediaReader {
    fun read(extractedDir: File): Map<String, ByteArray> {
        val manifest = File(extractedDir, "media")
        if (!manifest.exists()) return emptyMap()
        val json = runCatching { JSONObject(manifest.readText()) }.getOrNull() ?: return emptyMap()

        val result = LinkedHashMap<String, ByteArray>()
        for (number in json.keys()) {
            val original = json.optString(number).ifEmpty { continue }
            val file = File(extractedDir, number)
            if (file.exists()) result[original] = file.readBytes()
        }
        return result
    }
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.ApkgMediaReaderTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/apkg/ApkgUnzipper.kt \
        app/src/main/java/nart/simpleanki/core/apkg/ApkgMediaReader.kt \
        app/src/test/java/nart/simpleanki/core/apkg/ApkgMediaReaderTest.kt
git commit -m "Add apkg unzipper and JSON media manifest reader"
```

---

## Task 8: ApkgFormatDetector

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/apkg/ApkgFormatDetector.kt`
- Test: `app/src/test/java/nart/simpleanki/core/apkg/ApkgFormatDetectorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package nart.simpleanki.core.apkg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ApkgFormatDetectorTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun detectsV3_whenAnki21bPresent() {
        val dir = tmp.newFolder(); File(dir, "collection.anki21b").writeText("x")
        assertEquals(ApkgFormat.V3, ApkgFormatDetector().detect(dir))
    }

    @Test fun detectsLegacy_forAnki21orAnki2() {
        val d1 = tmp.newFolder(); File(d1, "collection.anki21").writeText("x")
        assertEquals(ApkgFormat.LEGACY, ApkgFormatDetector().detect(d1))
        val d2 = tmp.newFolder(); File(d2, "collection.anki2").writeText("x")
        assertEquals(ApkgFormat.LEGACY, ApkgFormatDetector().detect(d2))
    }

    @Test fun missingDatabase_throws() {
        assertThrows(ApkgImportError.MissingDatabase::class.java) { ApkgFormatDetector().detect(tmp.newFolder()) }
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.ApkgFormatDetectorTest"`
Expected: FAIL — unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package nart.simpleanki.core.apkg

import java.io.File

/** Picks the package format from the extracted directory's database file. */
class ApkgFormatDetector {
    fun detect(extractedDir: File): ApkgFormat = when {
        File(extractedDir, "collection.anki21b").exists() -> ApkgFormat.V3
        File(extractedDir, "collection.anki21").exists() ||
            File(extractedDir, "collection.anki2").exists() -> ApkgFormat.LEGACY
        else -> throw ApkgImportError.MissingDatabase
    }

    /** The database filename for a detected legacy package (prefers .anki21). */
    fun legacyDbFile(extractedDir: File): File =
        File(extractedDir, "collection.anki21").takeIf { it.exists() }
            ?: File(extractedDir, "collection.anki2")
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.ApkgFormatDetectorTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/apkg/ApkgFormatDetector.kt \
        app/src/test/java/nart/simpleanki/core/apkg/ApkgFormatDetectorTest.kt
git commit -m "Add apkg format detector"
```

---

## Task 9: ApkgImportService — previewCards + import (legacy parse via injected seams)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/apkg/ApkgImportService.kt`
- Create (test): `app/src/test/java/nart/simpleanki/core/apkg/FakeApkgImportService.kt`
- Test: `app/src/test/java/nart/simpleanki/core/apkg/ApkgImportServiceTest.kt`

This task builds the orchestration seam and the two pure-ish operations (`previewCards`, `import`). `parse` is wired in Task 10 (it needs Android file/Uri/SQLite); here we define the interface and the testable operations.

- [ ] **Step 1: Write the failing test**

```kotlin
package nart.simpleanki.core.apkg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import nart.simpleanki.core.data.media.LocalMediaStore
import nart.simpleanki.core.data.media.MediaManager
import nart.simpleanki.core.data.media.FakeMediaUploader
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FakeCardDao
import nart.simpleanki.core.data.repository.FakeDeckDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkgImportServiceTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun service(): Triple<DefaultApkgImportService, FakeDeckDao, FakeCardDao> {
        val deckDao = FakeDeckDao(); val cardDao = FakeCardDao()
        val media = MediaManager(LocalMediaStore(tmp.newFolder(), Dispatchers.Unconfined), FakeMediaUploader())
        val svc = DefaultApkgImportService(
            unzipper = ApkgUnzipper(), detector = ApkgFormatDetector(), mediaReader = ApkgMediaReader(),
            media = media, deckRepository = DeckRepository(deckDao) { 1L },
            cardRepository = CardRepository(cardDao) { 1L },
            idGenerator = { "id" }, now = { 1L },
        )
        return Triple(svc, deckDao, cardDao)
    }

    private val noteType = AnkiNoteType(1, "Basic", listOf("Front", "Back"), 0)
    private val notes = listOf(
        AnkiNote(1, "g1", 1, listOf("F1", "B1"), emptyList()),
        AnkiNote(2, "g2", 1, listOf("", "B2"), emptyList()),     // empty front -> skipped
    )

    @Test fun previewCards_mapsFields_skipsEmpty_noMediaWhenDisabled() = runTest {
        val (svc, _, _) = service()
        val cards = svc.previewCards(notes, noteType, frontIdx = 0, backIdx = 1, media = emptyMap(), importMedia = false)
        assertEquals(1, cards.size)
        assertEquals("F1", cards[0].front); assertEquals("B1", cards[0].back)
        assertNull(cards[0].imageName)
    }

    @Test fun import_createsOneDeck_andNewCardsWithApkgSource() = runTest {
        val (svc, deckDao, cardDao) = service()
        val cards = listOf(ApkgPreviewCard("F1", "B1", imageName = null, audioName = null, selected = true))
        svc.import(cards, deckName = "MyDeck")
        val decks = deckDao.store.value.values
        assertEquals(listOf("MyDeck"), decks.map { it.name })
        val saved = cardDao.store.value.values.single()
        assertEquals("F1", saved.front)
        assertEquals("apkg", saved.source)
        assertEquals(0, saved.fsrsState)            // CardState.New
        assertEquals(decks.single().id, saved.deckId)
    }
}
```

(Confirm `FakeDeckDao`/`FakeCardDao` expose a `store` map — they do in `app/src/test/.../repository/FakeDaos.kt`; if the accessor name differs, query via the DAO's observe/get methods instead.)

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.ApkgImportServiceTest"`
Expected: FAIL — `DefaultApkgImportService` unresolved.

- [ ] **Step 3: Write `ApkgImportService.kt`**

```kotlin
package nart.simpleanki.core.apkg

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nart.simpleanki.core.data.media.MediaManager
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.domain.model.Card
import nart.simpleanki.core.domain.model.CardState
import nart.simpleanki.core.domain.model.Deck
import java.io.File
import java.util.UUID

interface ApkgImportService {
    suspend fun parse(uri: Uri): ParsedCollection
    fun filterNotes(collection: ParsedCollection, noteTypeId: Long): List<AnkiNote>
    suspend fun previewCards(
        notes: List<AnkiNote>, noteType: AnkiNoteType,
        frontIdx: Int, backIdx: Int, media: Map<String, ByteArray>, importMedia: Boolean,
    ): List<ApkgPreviewCard>
    suspend fun import(cards: List<ApkgPreviewCard>, deckName: String)
}

class DefaultApkgImportService(
    private val unzipper: ApkgUnzipper,
    private val detector: ApkgFormatDetector,
    private val mediaReader: ApkgMediaReader,
    private val media: MediaManager,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val appContext: Context? = null,          // null in unit tests (parse not exercised there)
    private val legacyReader: AnkiCollectionReader = LegacyApkgReader(),
    private val v3Reader: AnkiCollectionReader = LegacyApkgReader(), // replaced in Task 15
    private val openDb: (File) -> AnkiSqlite = { AndroidAnkiSqlite.open(it) },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : ApkgImportService {

    override suspend fun parse(uri: Uri): ParsedCollection = withContext(Dispatchers.IO) {
        val ctx = appContext ?: throw ApkgImportError.FileAccess
        val work = File(ctx.cacheDir, "apkg-${idGenerator()}")
        try {
            val apkg = File(work, "in.apkg").apply { parentFile?.mkdirs() }
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                apkg.outputStream().use { input.copyTo(it) }
            } ?: throw ApkgImportError.FileAccess

            val extracted = File(work, "x")
            unzipper.unzip(apkg, extracted)

            val format = detector.detect(extracted)
            val dbFile = when (format) {
                ApkgFormat.LEGACY -> detector.legacyDbFile(extracted)
                ApkgFormat.V3 -> throw ApkgImportError.UnsupportedSchema   // wired in Task 15
            }
            val reader = if (format == ApkgFormat.V3) v3Reader else legacyReader
            val db = openDb(dbFile)
            val data = try { reader.read(db) } finally { db.close() }

            if (data.noteTypes.isEmpty() || data.notes.isEmpty()) throw ApkgImportError.InvalidStructure
            ParsedCollection(data.noteTypes, data.notes, mediaReader.read(extracted))
        } finally {
            work.deleteRecursively()
        }
    }

    override fun filterNotes(collection: ParsedCollection, noteTypeId: Long): List<AnkiNote> =
        collection.notes.filter { it.modelId == noteTypeId }

    override suspend fun previewCards(
        notes: List<AnkiNote>, noteType: AnkiNoteType,
        frontIdx: Int, backIdx: Int, media: Map<String, ByteArray>, importMedia: Boolean,
    ): List<ApkgPreviewCard> {
        val mediaMap: Map<String, String> = if (importMedia) saveMedia(media) else emptyMap()
        return notes.mapNotNull { note ->
            if (frontIdx >= note.fields.size || backIdx >= note.fields.size) return@mapNotNull null
            val f = ApkgFieldProcessor.process(note.fields[frontIdx], mediaMap)
            val b = ApkgFieldProcessor.process(note.fields[backIdx], mediaMap)
            if (f.text.isEmpty() || b.text.isEmpty()) return@mapNotNull null
            ApkgPreviewCard(
                front = f.text, back = b.text,
                imageName = f.image ?: b.image, audioName = f.audio ?: b.audio,
            )
        }
    }

    /** Saves each media blob locally, returning originalName → localFilename.
     *  Param is named [mediaFiles] (not `media`) to avoid shadowing the [media] MediaManager. */
    private suspend fun saveMedia(mediaFiles: Map<String, ByteArray>): Map<String, String> {
        val out = HashMap<String, String>(mediaFiles.size)
        for ((original, bytes) in mediaFiles) {
            val ext = original.substringAfterLast('.', "").ifEmpty { "bin" }
            val isAudio = ext.lowercase() in AUDIO_EXTS
            out[original] = runCatching {
                if (isAudio) media.importAudio(bytes, ext) else media.importImage(bytes, ext)
            }.getOrNull() ?: continue
        }
        return out
    }

    override suspend fun import(cards: List<ApkgPreviewCard>, deckName: String) {
        val t = now()
        val deck = Deck(id = idGenerator(), name = deckName, dateCreated = t, lastModified = t)
        deckRepository.upsert(deck)
        for (c in cards) {
            cardRepository.upsert(
                Card(
                    id = idGenerator(), front = c.front, back = c.back,
                    image = c.imageName, audioName = c.audioName, imagePath = null, audioPath = null,
                    deckId = deck.id, dateCreated = t, lastModified = t,
                    fsrsDue = t, fsrsState = CardState.New.value, source = "apkg",
                ),
            )
        }
    }

    private companion object {
        val AUDIO_EXTS = setOf("mp3", "m4a", "ogg", "oga", "wav", "flac", "aac", "opus")
    }
}
```

(Note: `legacyDbFile` is only meaningful for legacy; V3's db-file resolution is added in Task 15 along with `v3Reader`.)

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.ApkgImportServiceTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Create `FakeApkgImportService.kt` (for the ViewModel test in Task 11)**

```kotlin
package nart.simpleanki.core.apkg

import android.net.Uri

class FakeApkgImportService(
    var collection: ParsedCollection = ParsedCollection(emptyList(), emptyList(), emptyMap()),
    var parseError: Throwable? = null,
) : ApkgImportService {
    var importedDeckName: String? = null
    var importedCards: List<ApkgPreviewCard> = emptyList()

    override suspend fun parse(uri: Uri): ParsedCollection = parseError?.let { throw it } ?: collection
    override fun filterNotes(collection: ParsedCollection, noteTypeId: Long) =
        collection.notes.filter { it.modelId == noteTypeId }
    override suspend fun previewCards(
        notes: List<AnkiNote>, noteType: AnkiNoteType,
        frontIdx: Int, backIdx: Int, media: Map<String, ByteArray>, importMedia: Boolean,
    ): List<ApkgPreviewCard> = notes.filter { frontIdx < it.fields.size && backIdx < it.fields.size }
        .map { ApkgPreviewCard(it.fields[frontIdx], it.fields[backIdx], null, null) }
    override suspend fun import(cards: List<ApkgPreviewCard>, deckName: String) {
        importedCards = cards; importedDeckName = deckName
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/apkg/ApkgImportService.kt \
        app/src/test/java/nart/simpleanki/core/apkg/ApkgImportServiceTest.kt \
        app/src/test/java/nart/simpleanki/core/apkg/FakeApkgImportService.kt
git commit -m "Add apkg import service: preview + import (legacy parse)"
```

---

## Task 10: ApkgImportViewModel

**Files:**
- Create: `app/src/main/java/nart/simpleanki/feature/apkgimport/ApkgImportViewModel.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/apkgimport/ApkgImportViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package nart.simpleanki.feature.apkgimport

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
import nart.simpleanki.core.apkg.AnkiNote
import nart.simpleanki.core.apkg.AnkiNoteType
import nart.simpleanki.core.apkg.FakeApkgImportService
import nart.simpleanki.core.apkg.ParsedCollection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApkgImportViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val collection = ParsedCollection(
        noteTypes = listOf(AnkiNoteType(1, "Basic", listOf("Front", "Back"), 0)),
        notes = listOf(AnkiNote(1, "g", 1, listOf("F", "B"), emptyList())),
        media = emptyMap(),
    )

    private fun vm(service: FakeApkgImportService) = ApkgImportViewModel(service, "MyDeck")

    @Test fun parse_advancesToNoteTypeSelection() = runTest {
        val vm = vm(FakeApkgImportService(collection = collection))
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        assertEquals(ImportStep.NoteTypeSelection, vm.uiState.value.step)
        assertEquals(1, vm.uiState.value.noteTypes.size)
    }

    @Test fun selectNoteType_thenValidateFieldMapping_requiresDistinctFields() = runTest {
        val vm = vm(FakeApkgImportService(collection = collection))
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        vm.selectNoteType(collection.noteTypes[0]); runCurrent()
        assertEquals(ImportStep.FieldMapping, vm.uiState.value.step)
        vm.setFrontField("Front"); vm.setBackField("Front")
        assertFalse(vm.canGeneratePreview())
        vm.setBackField("Back")
        assertTrue(vm.canGeneratePreview())
    }

    @Test fun generatePreview_thenImport_callsServiceWithSelectedCards() = runTest {
        val service = FakeApkgImportService(collection = collection)
        val vm = vm(service)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        vm.selectNoteType(collection.noteTypes[0]); runCurrent()
        vm.setFrontField("Front"); vm.setBackField("Back")
        vm.generatePreview(); runCurrent()
        assertEquals(ImportStep.Preview, vm.uiState.value.step)
        assertEquals(1, vm.uiState.value.previewCards.size)
        vm.import {}; runCurrent()
        assertEquals("MyDeck", service.importedDeckName)
        assertEquals(1, service.importedCards.size)
    }

    @Test fun parseError_setsErrorMessage() = runTest {
        val vm = vm(FakeApkgImportService(parseError = RuntimeException("boom")))
        backgroundScope.launch { vm.uiState.collect {} }
        vm.parse(mockk<Uri>()); runCurrent()
        assertTrue(vm.uiState.value.error != null)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.feature.apkgimport.ApkgImportViewModelTest"`
Expected: FAIL — unresolved.

- [ ] **Step 3: Write `ApkgImportViewModel.kt`**

```kotlin
package nart.simpleanki.feature.apkgimport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nart.simpleanki.core.apkg.AnkiNote
import nart.simpleanki.core.apkg.AnkiNoteType
import nart.simpleanki.core.apkg.ApkgImportError
import nart.simpleanki.core.apkg.ApkgImportService
import nart.simpleanki.core.apkg.ApkgPreviewCard
import nart.simpleanki.core.apkg.ParsedCollection

enum class ImportStep { Parsing, NoteTypeSelection, FieldMapping, Preview, Importing }

data class ApkgImportUiState(
    val step: ImportStep = ImportStep.Parsing,
    val noteTypes: List<AnkiNoteType> = emptyList(),
    val noteCounts: Map<Long, Int> = emptyMap(),
    val selectedNoteType: AnkiNoteType? = null,
    val availableFields: List<String> = emptyList(),
    val frontField: String? = null,
    val backField: String? = null,
    val importMedia: Boolean = false,
    val previewCards: List<ApkgPreviewCard> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

class ApkgImportViewModel(
    private val service: ApkgImportService,
    private val deckName: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ApkgImportUiState())
    val uiState: StateFlow<ApkgImportUiState> = _uiState.asStateFlow()

    private var collection: ParsedCollection? = null
    private var filtered: List<AnkiNote> = emptyList()

    fun parse(uri: Uri) {
        _uiState.value = _uiState.value.copy(step = ImportStep.Parsing, busy = true, error = null)
        viewModelScope.launch {
            runCatching { service.parse(uri) }
                .onSuccess { data ->
                    collection = data
                    _uiState.value = _uiState.value.copy(
                        step = ImportStep.NoteTypeSelection, busy = false,
                        noteTypes = data.noteTypes,
                        noteCounts = data.noteTypes.associate { nt -> nt.id to data.notes.count { it.modelId == nt.id } },
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(busy = false, error = messageFor(it)) }
        }
    }

    fun selectNoteType(noteType: AnkiNoteType) {
        val data = collection ?: return
        filtered = service.filterNotes(data, noteType.id)
        _uiState.value = _uiState.value.copy(
            step = ImportStep.FieldMapping, selectedNoteType = noteType,
            availableFields = noteType.fields,
            frontField = noteType.fields.getOrNull(0),
            backField = noteType.fields.getOrNull(1),
        )
    }

    fun setFrontField(name: String) { _uiState.value = _uiState.value.copy(frontField = name) }
    fun setBackField(name: String) { _uiState.value = _uiState.value.copy(backField = name) }
    fun setImportMedia(value: Boolean) { _uiState.value = _uiState.value.copy(importMedia = value) }

    fun canGeneratePreview(): Boolean {
        val s = _uiState.value
        return s.frontField != null && s.backField != null && s.frontField != s.backField
    }

    fun generatePreview() {
        val data = collection ?: return
        val s = _uiState.value
        val nt = s.selectedNoteType ?: return
        val fi = nt.fields.indexOf(s.frontField); val bi = nt.fields.indexOf(s.backField)
        if (fi < 0 || bi < 0 || fi == bi) return
        _uiState.value = s.copy(busy = true)
        viewModelScope.launch {
            runCatching { service.previewCards(filtered, nt, fi, bi, data.media, s.importMedia) }
                .onSuccess { _uiState.value = _uiState.value.copy(step = ImportStep.Preview, previewCards = it, busy = false) }
                .onFailure { _uiState.value = _uiState.value.copy(busy = false, error = messageFor(it)) }
        }
    }

    fun toggleCard(index: Int) {
        val list = _uiState.value.previewCards.toMutableList()
        list.getOrNull(index)?.let { list[index] = it.copy(selected = !it.selected) }
        _uiState.value = _uiState.value.copy(previewCards = list)
    }

    fun import(onComplete: () -> Unit) {
        val selected = _uiState.value.previewCards.filter { it.selected }
        if (selected.isEmpty()) return
        _uiState.value = _uiState.value.copy(step = ImportStep.Importing, busy = true)
        viewModelScope.launch {
            runCatching { service.import(selected, deckName) }
                .onSuccess { onComplete() }
                .onFailure { _uiState.value = _uiState.value.copy(step = ImportStep.Preview, busy = false, error = messageFor(it)) }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    private fun messageFor(t: Throwable): String =
        (t as? ApkgImportError)?.message ?: "Import failed. Please try again."
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.feature.apkgimport.ApkgImportViewModelTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/apkgimport/ApkgImportViewModel.kt \
        app/src/test/java/nart/simpleanki/feature/apkgimport/ApkgImportViewModelTest.kt
git commit -m "Add apkg import wizard ViewModel"
```

---

## Task 11: DI registration

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`

- [ ] **Step 1: Register the service + ViewModel**

Add imports and registrations. After the media singletons add:
```kotlin
    single<ApkgImportService> {
        DefaultApkgImportService(
            unzipper = ApkgUnzipper(),
            detector = ApkgFormatDetector(),
            mediaReader = ApkgMediaReader(),
            media = get(),
            deckRepository = get(),
            cardRepository = get(),
            appContext = androidContext(),
        )
    }
    viewModel { (deckName: String) -> ApkgImportViewModel(service = get(), deckName = deckName) }
```
Imports:
```kotlin
import nart.simpleanki.core.apkg.ApkgFormatDetector
import nart.simpleanki.core.apkg.ApkgImportService
import nart.simpleanki.core.apkg.ApkgMediaReader
import nart.simpleanki.core.apkg.ApkgUnzipper
import nart.simpleanki.core.apkg.DefaultApkgImportService
import nart.simpleanki.feature.apkgimport.ApkgImportViewModel
```
(Match the existing `viewModel { (param) -> … }` parameterized style already used for `CardFormViewModel`. `DeckRepository`/`CardRepository` are already singletons — confirm and use `get()`.)

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nart/simpleanki/di/AppModule.kt
git commit -m "Register apkg import service and ViewModel in DI"
```

---

## Task 12: Wizard UI + Library entry point (legacy end-to-end)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/feature/apkgimport/ApkgImportScreen.kt`
- Modify: `app/src/main/java/nart/simpleanki/feature/library/LibraryScreen.kt`
- Modify: `app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt`

No unit tests (Compose UI); correctness is build + manual verification.

- [ ] **Step 1: Create `ApkgImportScreen.kt`**

A composable that takes the picked `Uri`, obtains the ViewModel (`koinViewModel { parametersOf(deckName) }`), calls `parse(uri)` once via `LaunchedEffect`, and renders the current `ImportStep`. Provide:
- `ApkgImportScreen(uri: Uri, deckName: String, onClose: () -> Unit)` — top-level `Scaffold` with a TopAppBar (title "Import", close icon → `onClose`), body switches on `state.step`:
  - `Parsing`/`Importing` → centered `CircularProgressIndicator`.
  - `NoteTypeSelection` → a `LazyColumn` of note types (`"${nt.name} · ${state.noteCounts[nt.id] ?: 0} notes"`), each row `clickable { vm.selectNoteType(nt) }`.
  - `FieldMapping` → two dropdowns (use `ExposedDropdownMenuBox`) bound to `state.availableFields` for front/back, a `Switch` for `importMedia`, and a "Generate preview" `Button(enabled = vm.canGeneratePreview())` → `vm.generatePreview()`.
  - `Preview` → header "N of M selected", a `LazyColumn` of cards (front/back text + a `Checkbox` calling `vm.toggleCard(index)`), and an "Import N cards" `Button` → `vm.import(onClose)`.
- Show `state.error` in an `AlertDialog` with an OK button → `vm.clearError()`.

Reference the existing Compose style in `feature/cardform/CardFormScreen.kt` for theming/imports. Use `org.koin.androidx.compose.koinViewModel` and `org.koin.core.parameter.parametersOf`.

```kotlin
package nart.simpleanki.feature.apkgimport

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkgImportScreen(uri: Uri, deckName: String, onClose: () -> Unit) {
    val vm: ApkgImportViewModel = koinViewModel { parametersOf(deckName) }
    val state by vm.uiState.collectAsState()
    LaunchedEffect(uri) { vm.parse(uri) }

    state.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            confirmButton = { TextButton(onClick = { vm.clearError(); onClose() }) { Text("OK") } },
            title = { Text("Import error") }, text = { Text(msg) },
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Import deck") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") } },
        )
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            when (state.step) {
                ImportStep.Parsing, ImportStep.Importing -> CircularProgressIndicator()
                ImportStep.NoteTypeSelection -> NoteTypeStep(state, vm::selectNoteType)
                ImportStep.FieldMapping -> FieldMappingStep(state, vm)
                ImportStep.Preview -> PreviewStep(state, vm, onClose)
            }
        }
    }
}

@Composable
private fun NoteTypeStep(state: ApkgImportUiState, onSelect: (nart.simpleanki.core.apkg.AnkiNoteType) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.noteTypes.size) { i ->
            val nt = state.noteTypes[i]
            ListItem(
                headlineContent = { Text(nt.name) },
                supportingContent = { Text("${state.noteCounts[nt.id] ?: 0} notes · ${nt.fields.size} fields") },
                modifier = Modifier.clickable { onSelect(nt) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldMappingStep(state: ApkgImportUiState, vm: ApkgImportViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FieldDropdown("Front", state.availableFields, state.frontField, vm::setFrontField)
        FieldDropdown("Back", state.availableFields, state.backField, vm::setBackField)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.importMedia, onCheckedChange = vm::setImportMedia)
            Spacer(Modifier.width(8.dp)); Text("Import media")
        }
        Button(onClick = vm::generatePreview, enabled = vm.canGeneratePreview() && !state.busy) {
            Text("Generate preview")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldDropdown(label: String, options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected ?: "", onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

@Composable
private fun PreviewStep(state: ApkgImportUiState, vm: ApkgImportViewModel, onClose: () -> Unit) {
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
        Button(onClick = { vm.import(onClose) }, enabled = selectedCount > 0, modifier = Modifier.fillMaxWidth()) {
            Text("Import $selectedCount cards")
        }
    }
}
```
(If any Material3 API here is experimental and the compiler flags it, add the matching `@OptIn` — the codebase already opts into `ExperimentalMaterial3Api` elsewhere; follow that pattern. `items(count)` vs `itemsIndexed` imports: add `androidx.compose.foundation.lazy.items` if needed.)

- [ ] **Step 2: Add the Import action + SAF picker host in `AzriNavHost.kt`**

In the nav host where the Library tab is rendered, add state + a SAF launcher and pass an `onImport` lambda into `LibraryScreen`:
```kotlin
        var importUri by remember { mutableStateOf<Uri?>(null) }
        val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importUri = uri
        }
```
Pass `onImport = { importPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }` to `LibraryScreen`. After the Scaffold (sibling to the existing `if (showPaywall) …`):
```kotlin
        importUri?.let { uri ->
            ApkgImportScreen(uri = uri, deckName = "Imported deck", onClose = { importUri = null })
        }
```
Add imports for `Uri`, `rememberLauncherForActivityResult`, `ActivityResultContracts`, and `ApkgImportScreen`. (Deck name: use a sensible default like "Imported deck"; deriving from the file display name is a nice-to-have but the SAF `Uri` display name requires a content-resolver query — out of scope for v1, keep the constant.)

- [ ] **Step 3: Add the Import button to `LibraryScreen.kt`**

Add `onImport: () -> Unit` to `LibraryScreen`'s parameters and an icon button in the TopAppBar `actions` (before New folder):
```kotlin
                    IconButton(onClick = onImport) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Import deck")
                    }
```
Add `import androidx.compose.material.icons.filled.FileDownload`. Update the `LibraryScreen(...)` call site in `AzriNavHost.kt` to pass `onImport`.

- [ ] **Step 4: Verify build**

Run: `./gradlew :app:compileDebugKotlin` then `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL; full suite green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/apkgimport/ApkgImportScreen.kt \
        app/src/main/java/nart/simpleanki/feature/library/LibraryScreen.kt \
        app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt
git commit -m "Add apkg import wizard UI and Library entry point"
```

**Milestone:** legacy `.apkg` import works end-to-end. Manually verify on the emulator with a legacy-exported `.apkg` (Anki: Export → check "Support older Anki versions").

---

## Task 13: V3ApkgReader (schema-18 tables)

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/apkg/V3ApkgReader.kt`
- Test: `app/src/test/java/nart/simpleanki/core/apkg/V3ApkgReaderTest.kt`

In schema 18, note-type field names live in the `notetypes` (id, name) and `fields` (ntid, ord, name) tables; the `notes` table is unchanged.

- [ ] **Step 1: Write the failing test**

```kotlin
package nart.simpleanki.core.apkg

import org.junit.Assert.assertEquals
import org.junit.Test

class V3ApkgReaderTest {
    private fun db() = FakeAnkiSqlite(
        mapOf(
            "SELECT id, name FROM notetypes" to listOf(mapOf("id" to 7L, "name" to "Basic")),
            "SELECT ntid, ord, name FROM fields ORDER BY ntid, ord" to listOf(
                mapOf("ntid" to 7L, "ord" to 0L, "name" to "Front"),
                mapOf("ntid" to 7L, "ord" to 1L, "name" to "Back"),
            ),
            "SELECT id, guid, mid, flds, tags FROM notes" to listOf(
                mapOf("id" to 1L, "guid" to "g", "mid" to 7L, "flds" to "F\u001FB", "tags" to ""),
            ),
        ),
    )

    @Test fun readsNoteTypesFromTables_andNotes() {
        val data = V3ApkgReader().read(db())
        val nt = data.noteTypes.single()
        assertEquals(7L, nt.id); assertEquals(listOf("Front", "Back"), nt.fields)
        assertEquals(listOf("F", "B"), data.notes.single().fields)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.V3ApkgReaderTest"`
Expected: FAIL — unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package nart.simpleanki.core.apkg

/** Reads a schema-18 collection: note-type field names from the notetypes/fields tables. */
class V3ApkgReader : AnkiCollectionReader {
    override fun read(db: AnkiSqlite): AnkiCollectionData {
        val names = db.query("SELECT id, name FROM notetypes").associate {
            (it["id"] as Long) to (it["name"] as? String ?: "")
        }
        val fieldsByType = HashMap<Long, MutableList<String>>()
        db.query("SELECT ntid, ord, name FROM fields ORDER BY ntid, ord").forEach { row ->
            val ntid = row["ntid"] as Long
            fieldsByType.getOrPut(ntid) { mutableListOf() }.add(row["name"] as? String ?: "")
        }
        val noteTypes = names.map { (id, name) ->
            AnkiNoteType(id = id, name = name, fields = fieldsByType[id].orEmpty(), sortField = 0)
        }

        val notes = db.query("SELECT id, guid, mid, flds, tags FROM notes").mapNotNull { row ->
            val id = (row["id"] as? Long) ?: return@mapNotNull null
            val guid = (row["guid"] as? String) ?: return@mapNotNull null
            val mid = (row["mid"] as? Long) ?: return@mapNotNull null
            val flds = (row["flds"] as? String) ?: return@mapNotNull null
            AnkiNote(id, guid, mid, flds.split('\u001F'),
                (row["tags"] as? String ?: "").split(' ').filter { it.isNotEmpty() })
        }
        return AnkiCollectionData(noteTypes, notes)
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.V3ApkgReaderTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/apkg/V3ApkgReader.kt \
        app/src/test/java/nart/simpleanki/core/apkg/V3ApkgReaderTest.kt
git commit -m "Add v3 schema-18 apkg collection reader"
```

---

## Task 14: V3 media — zstd + protobuf manifest

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/apkg/MediaManifestProto.kt`
- Modify: `app/src/main/java/nart/simpleanki/core/apkg/ApkgMediaReader.kt`
- Test: `app/src/test/java/nart/simpleanki/core/apkg/MediaManifestProtoTest.kt`, extend `ApkgMediaReaderTest.kt`

Anki v3 `media` manifest is a protobuf `MediaEntries { repeated MediaEntry entries = 1; }` where `MediaEntry { string name = 1; uint32 size = 2; bytes sha1 = 3; }`. The numbered archive files correspond to entries **by index** (entry[i] → file "i"). Manifest and media blobs may be zstd-compressed.

- [ ] **Step 1: Write the failing test for the protobuf reader**

```kotlin
package nart.simpleanki.core.apkg

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class MediaManifestProtoTest {
    // Build a MediaEntries protobuf with two entries by hand.
    private fun entry(name: String): ByteArray {
        val nameBytes = name.toByteArray()
        val inner = ByteArrayOutputStream()
        inner.write(0x0A); writeVarint(inner, nameBytes.size); inner.write(nameBytes)  // field1 (name), wire type 2
        val innerBytes = inner.toByteArray()
        val outer = ByteArrayOutputStream()
        outer.write(0x0A); writeVarint(outer, innerBytes.size); outer.write(innerBytes)  // field1 (entries), wire type 2
        return outer.toByteArray()
    }
    private fun writeVarint(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (true) { val b = v and 0x7F; v = v ushr 7; if (v != 0) out.write(b or 0x80) else { out.write(b); break } }
    }

    @Test fun parsesEntryNamesInOrder() {
        val bytes = entry("a.jpg") + entry("b.mp3")
        assertEquals(listOf("a.jpg", "b.mp3"), MediaManifestProto.parseEntryNames(bytes))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.MediaManifestProtoTest"`
Expected: FAIL — unresolved.

- [ ] **Step 3: Write `MediaManifestProto.kt` (minimal protobuf wire reader)**

```kotlin
package nart.simpleanki.core.apkg

/**
 * Minimal reader for Anki's `MediaEntries` protobuf — extracts the ordered list of entry
 * names (field 1 of each MediaEntry, which is field 1 of MediaEntries). All other fields
 * (size, sha1) are skipped. Avoids a full protobuf dependency.
 */
object MediaManifestProto {
    fun parseEntryNames(bytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        val r = Reader(bytes)
        while (!r.eof()) {
            val tag = r.varint().toInt()
            if (tag ushr 3 == 1 && tag and 7 == 2) {       // entries: length-delimited submessage
                val sub = r.bytes(r.varint().toInt())
                names += entryName(sub)
            } else {
                r.skip(tag and 7)
            }
        }
        return names
    }

    private fun entryName(sub: ByteArray): String {
        val r = Reader(sub)
        var name = ""
        while (!r.eof()) {
            val tag = r.varint().toInt()
            if (tag ushr 3 == 1 && tag and 7 == 2) {       // name: string
                name = String(r.bytes(r.varint().toInt()))
            } else {
                r.skip(tag and 7)
            }
        }
        return name
    }

    private class Reader(private val b: ByteArray) {
        private var i = 0
        fun eof() = i >= b.size
        fun varint(): Long {
            var result = 0L; var shift = 0
            while (true) {
                val byte = b[i++].toInt() and 0xFF
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) break
                shift += 7
            }
            return result
        }
        fun bytes(n: Int): ByteArray { val out = b.copyOfRange(i, i + n); i += n; return out }
        fun skip(wireType: Int) {
            when (wireType) {
                0 -> varint()
                1 -> i += 8
                2 -> i += varint().toInt()
                5 -> i += 4
                else -> error("unsupported wire type $wireType")
            }
        }
    }
}
```

- [ ] **Step 4: Run the proto test to verify pass**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.MediaManifestProtoTest"`
Expected: PASS.

- [ ] **Step 5: Extend `ApkgMediaReader` to handle v3 (zstd + protobuf), add a test, verify**

Replace `ApkgMediaReader.read` so it: reads the `media` file bytes; if they parse as JSON, use the legacy path (numbered files read as-is); otherwise treat as v3 — zstd-decompress the manifest if needed, `MediaManifestProto.parseEntryNames`, and for each index read file "i", zstd-decompressing if it is zstd-framed. Use a helper:
```kotlin
import com.github.luben.zstd.ZstdInputStream
import java.io.ByteArrayInputStream

private fun maybeUnzstd(bytes: ByteArray): ByteArray {
    // zstd magic: 0x28 B5 2F FD (little-endian 0xFD2FB528)
    val isZstd = bytes.size >= 4 && bytes[0] == 0x28.toByte() && bytes[1] == 0xB5.toByte() &&
        bytes[2] == 0x2F.toByte() && bytes[3] == 0xFD.toByte()
    return if (isZstd) ZstdInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() } else bytes
}
```
v3 branch:
```kotlin
        val raw = manifest.readBytes()
        // Legacy JSON first.
        runCatching { JSONObject(String(raw)) }.getOrNull()?.let { json ->
            val result = LinkedHashMap<String, ByteArray>()
            for (number in json.keys()) {
                val original = json.optString(number).ifEmpty { continue }
                val f = File(extractedDir, number); if (f.exists()) result[original] = f.readBytes()
            }
            return result
        }
        // V3: zstd + protobuf manifest; entries map to numbered files by index.
        val names = MediaManifestProto.parseEntryNames(maybeUnzstd(raw))
        val result = LinkedHashMap<String, ByteArray>()
        names.forEachIndexed { index, original ->
            val f = File(extractedDir, index.toString())
            if (original.isNotEmpty() && f.exists()) result[original] = maybeUnzstd(f.readBytes())
        }
        return result
```
Add an `ApkgMediaReaderTest` case building a v3 fixture: write a protobuf `media` file (reuse the test's hand-built entries) + numbered files "0","1" (plain bytes; `maybeUnzstd` is a no-op on non-zstd), assert the original-name → bytes map. Run:
`./gradlew testDebugUnitTest --tests "nart.simpleanki.core.apkg.ApkgMediaReaderTest"` → PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/apkg/MediaManifestProto.kt \
        app/src/main/java/nart/simpleanki/core/apkg/ApkgMediaReader.kt \
        app/src/test/java/nart/simpleanki/core/apkg/MediaManifestProtoTest.kt \
        app/src/test/java/nart/simpleanki/core/apkg/ApkgMediaReaderTest.kt
git commit -m "Add v3 zstd+protobuf media manifest support"
```

---

## Task 15: Wire v3 into the service (zstd db + reader selection)

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/apkg/ApkgImportService.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`

- [ ] **Step 1: Resolve the V3 db file (zstd-decompress) and select the reader**

In `DefaultApkgImportService`, change the constructor default `v3Reader` to `V3ApkgReader()`, and replace the `dbFile`/`reader` selection in `parse` so V3 decompresses `collection.anki21b` to a temp `.db` before `openDb`:
```kotlin
            val reader: AnkiCollectionReader
            val dbFile: File = when (format) {
                ApkgFormat.LEGACY -> { reader = legacyReader; detector.legacyDbFile(extracted) }
                ApkgFormat.V3 -> {
                    reader = v3Reader
                    val out = File(work, "collection.db")
                    com.github.luben.zstd.ZstdInputStream(File(extracted, "collection.anki21b").inputStream()).use { zin ->
                        out.outputStream().use { zin.copyTo(it) }
                    }
                    out
                }
            }
            val db = openDb(dbFile)
            val data = try { reader.read(db) } finally { db.close() }
```
(Remove the earlier `throw ApkgImportError.UnsupportedSchema` for V3. `UnsupportedSchema` now only fires from the detector for unknown containers, and could additionally be thrown if a future `col`/schema check fails — leave the detector as the source of that error.)

- [ ] **Step 2: Update the DI default**

The `single<ApkgImportService>` block uses `DefaultApkgImportService(...)`; it now relies on the constructor defaults `legacyReader = LegacyApkgReader()` and `v3Reader = V3ApkgReader()`. No DI change needed unless readers were passed explicitly — confirm the DI block does not override them (it doesn't, per Task 11). If desired, pass them explicitly for clarity:
```kotlin
            legacyReader = LegacyApkgReader(),
            v3Reader = V3ApkgReader(),
```
with the matching imports.

- [ ] **Step 3: Verify build + full suite**

Run: `./gradlew testDebugUnitTest` then `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL; full suite green.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/apkg/ApkgImportService.kt \
        app/src/main/java/nart/simpleanki/di/AppModule.kt
git commit -m "Wire v3 zstd reader into the apkg import service"
```

**Milestone:** both legacy and v3 `.apkg` import. Manually verify on the emulator with a modern default-exported `.apkg` (zstd). If a real v3 file reveals different manifest/blob framing than assumed, adjust `ApkgMediaReader`/`MediaManifestProto` against that fixture (the protobuf and zstd helpers are isolated for exactly this).

---

## Final verification

- [ ] `./gradlew testDebugUnitTest` — full suite green.
- [ ] `./gradlew assembleDebug` — app builds (confirms zstd-jni native libs package).
- [ ] Manual (emulator): import a legacy `.apkg` and a modern (zstd) `.apkg`; pick a note type; map fields; toggle media; deselect a card; import; verify the new deck + cards appear, media shows from local storage (signed out / free), and HTML is stripped.

---

## Spec coverage check

| Spec section | Task(s) |
|---|---|
| Wizard UX (file → note type → field map → preview → import) | 10, 12 |
| Library entry point + SAF picker | 12 |
| Legacy reader (col.models JSON + notes + media JSON) | 4, 6, 7 |
| V3 reader (schema-18 tables) | 13 |
| Format detection | 8 |
| zstd container + protobuf media manifest | 14, 15 |
| Field processing (img/sound extract, HTML strip, first-media) | 2 |
| Media saved local-first, original extension preserved | 3, 9 |
| Import: new deck, new cards, source=apkg | 9 |
| AnkiSqlite testability seam | 5 |
| Error handling (sealed errors, dialog, temp cleanup) | 1, 9, 10, 12 |
| Dependencies (zstd-jni, org.json test) | 1 |
| DI registration | 11, 15 |
| Testing (readers, processor, manifest, service, viewmodel, media) | 2,3,4,6,7,8,9,10,13,14 |
| Out of scope (cloze, templates, multi-notetype, scheduling, decks, colpkg, LaTeX) | — (excluded) |
