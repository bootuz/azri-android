# Local-First Media Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store card media (images/audio) on-device by default and display it from disk; upload to / download from Firebase Storage only as part of premium sync.

**Architecture:** A `MediaManager` coordinator sits over a thin `LocalMediaStore` (wraps `filesDir/media/`) and the existing `MediaUploader` cloud seam. It owns the local-first policy: save-locally on create, local-first read with cloud fallback + cache on display, upload-on-push and prefetch-on-pull during sync. `Card.image`/`audioName` is the local filename; `Card.imagePath`/`audioPath` (Firebase path) stays the cloud location and encodes sync state (`null` = local-only, not yet uploaded). No Room/DTO schema change.

**Tech Stack:** Kotlin, Coroutines, Koin DI, Coil3 (image loading), Android `MediaPlayer`, Firebase Storage, JUnit4.

**Conventions:**
- Build green after **every** task (the interface change is additive first, cleanup last).
- Commit messages: plain imperative, **no "claude" mention, no `Co-Authored-By`/attribution trailers**.
- Run a single unit-test class with: `./gradlew testDebugUnitTest --tests "<FQCN>"` (JDK 21 toolchain; ensure `JAVA_HOME` points at a JDK 21 if the daemon complains).

---

## File Structure

**Create:**
- `app/src/main/java/nart/simpleanki/core/data/media/LocalMediaStore.kt` — on-device file storage under `filesDir/media/`.
- `app/src/main/java/nart/simpleanki/core/data/media/MediaManager.kt` — local-first coordinator (the policy seam).
- `app/src/test/java/nart/simpleanki/core/data/media/LocalMediaStoreTest.kt`
- `app/src/test/java/nart/simpleanki/core/data/media/MediaManagerTest.kt`

**Modify:**
- `app/src/main/java/nart/simpleanki/core/data/media/MediaRepository.kt` — new `MediaUploader` API (`upload(name,bytes)`, `downloadBytes(path)`); remove old methods + `MediaRef` (in cleanup task).
- `app/src/test/java/nart/simpleanki/core/data/media/FakeMediaUploader.kt` — track the new API.
- `app/src/main/java/nart/simpleanki/ui/components/MediaImage.kt` — `(name, cloudPath)` via `MediaManager.resolve`.
- `app/src/main/java/nart/simpleanki/ui/components/AudioPlayButton.kt` — `(name, cloudPath)` via `MediaManager.resolve`.
- `app/src/main/java/nart/simpleanki/feature/study/StudyScreen.kt` — call sites + gating on `card.image`/`card.audioName`.
- `app/src/main/java/nart/simpleanki/feature/cardform/CardFormScreen.kt` — call sites + preview gating.
- `app/src/main/java/nart/simpleanki/feature/cardform/CardFormViewModel.kt` — save media locally (`imagePath = null`).
- `app/src/test/java/nart/simpleanki/feature/cardform/CardFormViewModelTest.kt` — updated for local save.
- `app/src/main/java/nart/simpleanki/core/data/sync/SyncManager.kt` — upload-on-push, prefetch-on-pull.
- `app/src/test/java/nart/simpleanki/core/data/sync/SyncManagerTest.kt` — media sync tests.
- `app/src/main/java/nart/simpleanki/di/AppModule.kt` — register `LocalMediaStore` + `MediaManager`; rewire `CardFormViewModel` + `SyncManager`.

**Premium-gating note:** `SyncManager.sync` is only ever called behind `Entitlements.shouldSync(...)` (in `SyncWorker.doWork` and `AzriRoot`), which is already covered by `EntitlementsTest`. So free users never reach upload/prefetch. SyncManager tests therefore verify upload/prefetch *behavior*, not the gate.

---

## Task 1: LocalMediaStore

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/data/media/LocalMediaStore.kt`
- Test: `app/src/test/java/nart/simpleanki/core/data/media/LocalMediaStoreTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package nart.simpleanki.core.data.media

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalMediaStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store() = LocalMediaStore(tmp.newFolder("media"))

    @Test fun save_thenExists_andReadsBack() = runTest {
        val store = store()
        assertFalse(store.exists("a.jpg"))
        val file = store.save("a.jpg", byteArrayOf(1, 2, 3))
        assertTrue(store.exists("a.jpg"))
        assertEquals("a.jpg", file.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
    }

    @Test fun delete_removesFile() = runTest {
        val store = store()
        store.save("b.m4a", byteArrayOf(9))
        store.delete("b.m4a")
        assertFalse(store.exists("b.m4a"))
    }

    @Test fun newName_hasExtension_andIsUnique() {
        val store = store()
        val a = store.newName("jpg")
        val b = store.newName("jpg")
        assertTrue(a.endsWith(".jpg"))
        assertTrue(a != b)
    }
}
```

(Note: `assertArrayEquals` is `org.junit.Assert.assertArrayEquals` — add the import.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.data.media.LocalMediaStoreTest"`
Expected: FAIL — `LocalMediaStore` is unresolved (does not compile).

- [ ] **Step 3: Write minimal implementation**

```kotlin
package nart.simpleanki.core.data.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * On-device media storage. Files live under [dir] (typically `filesDir/media/`) and are
 * keyed by their logical filename (`<uuid>.<ext>`, the same name stored on the card).
 * [dir] is injected so the store is unit-testable against a temp directory.
 */
class LocalMediaStore(private val dir: File) {
    init { dir.mkdirs() }

    fun fileFor(name: String): File = File(dir, name)

    fun exists(name: String): Boolean = fileFor(name).exists()

    suspend fun save(name: String, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val file = fileFor(name)
        file.writeBytes(bytes)
        file
    }

    fun delete(name: String) { fileFor(name).delete() }

    fun newName(ext: String): String = "${UUID.randomUUID()}.$ext"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.data.media.LocalMediaStoreTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/data/media/LocalMediaStore.kt \
        app/src/test/java/nart/simpleanki/core/data/media/LocalMediaStoreTest.kt
git commit -m "Add LocalMediaStore for on-device media storage"
```

---

## Task 2: Extend MediaUploader with name-stable upload + downloadBytes (additive)

Add the new cloud-seam methods alongside the existing ones so the build stays green. The old methods (`uploadImage`/`uploadAudio`/`downloadUrl`) are removed in Task 8 once no caller remains.

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/data/media/MediaRepository.kt`
- Modify: `app/src/test/java/nart/simpleanki/core/data/media/FakeMediaUploader.kt`

- [ ] **Step 1: Add the new methods to the `MediaUploader` interface**

In `MediaRepository.kt`, the interface becomes (keep the three existing methods for now):

```kotlin
/** Media upload/lookup seam; faked in tests. */
interface MediaUploader {
    suspend fun uploadImage(bytes: ByteArray): Result<MediaRef>
    suspend fun uploadAudio(bytes: ByteArray): Result<MediaRef>
    suspend fun downloadUrl(storagePath: String): Result<String>

    /** Uploads [bytes] under the given [name]; returns the cloud storage path. */
    suspend fun upload(name: String, bytes: ByteArray): Result<String>
    /** Downloads the raw bytes stored at [path]. */
    suspend fun downloadBytes(path: String): Result<ByteArray>
}
```

- [ ] **Step 2: Implement the new methods in `FirebaseMediaRepository`**

Add inside `FirebaseMediaRepository` (in the same file):

```kotlin
    override suspend fun upload(name: String, bytes: ByteArray): Result<String> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val folder = if (name.endsWith(".m4a") || name.endsWith(".mp3")) "audio" else "images"
        val path = "users/$uid/$folder/$name"
        storage.reference.child(path).putBytes(bytes).await()
        path
    }

    override suspend fun downloadBytes(path: String): Result<ByteArray> = runCatching {
        storage.reference.child(path).getBytes(MAX_DOWNLOAD_BYTES).await()
    }

    private companion object {
        const val MAX_DOWNLOAD_BYTES = 25L * 1024 * 1024 // 25 MB ceiling per media file
    }
```

- [ ] **Step 3: Add the new methods to `FakeMediaUploader`**

In `FakeMediaUploader.kt`, add an in-memory store + the two methods (keep existing fields/methods):

```kotlin
    /** name -> bytes, simulating the cloud. */
    val uploaded = mutableMapOf<String, ByteArray>()
    var uploadPathCalls = 0
    var downloadCalls = 0
    /** Override to simulate upload failure. */
    var uploadPathResult: ((String) -> Result<String>)? = null

    override suspend fun upload(name: String, bytes: ByteArray): Result<String> {
        uploadPathCalls++
        uploadPathResult?.let { return it(name) }
        uploaded[name] = bytes
        return Result.success("users/u/media/$name")
    }

    override suspend fun downloadBytes(path: String): Result<ByteArray> {
        downloadCalls++
        val name = path.substringAfterLast('/')
        return uploaded[name]?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("not found: $path"))
    }
```

- [ ] **Step 4: Verify everything still compiles + existing tests pass**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.*media*" --tests "nart.simpleanki.feature.cardform.*"`
Expected: PASS (old card-form tests still green; the seam change is additive).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/data/media/MediaRepository.kt \
        app/src/test/java/nart/simpleanki/core/data/media/FakeMediaUploader.kt
git commit -m "Add name-stable upload and downloadBytes to MediaUploader"
```

---

## Task 3: MediaManager coordinator

**Files:**
- Create: `app/src/main/java/nart/simpleanki/core/data/media/MediaManager.kt`
- Test: `app/src/test/java/nart/simpleanki/core/data/media/MediaManagerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package nart.simpleanki.core.data.media

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun managerWith(uploader: FakeMediaUploader = FakeMediaUploader()) =
        Pair(MediaManager(LocalMediaStore(tmp.newFolder("media")), uploader), uploader)

    @Test fun saveImage_writesLocally_andReturnsName() = runTest {
        val (m, _) = managerWith()
        val name = m.saveImage(byteArrayOf(1, 2))
        assertTrue(name.endsWith(".jpg"))
        // Resolving with a null cloud path still finds the local file.
        val file = m.resolve(name, null)!!
        assertArrayEquals(byteArrayOf(1, 2), file.readBytes())
    }

    @Test fun resolve_localHit_doesNotDownload() = runTest {
        val (m, up) = managerWith()
        val name = m.saveImage(byteArrayOf(7))
        m.resolve(name, "users/u/media/$name")
        assertEquals(0, up.downloadCalls)
    }

    @Test fun resolve_cloudFallback_downloadsAndCaches() = runTest {
        val up = FakeMediaUploader().apply { uploaded["x.jpg"] = byteArrayOf(5, 6) }
        val (m, _) = managerWith(up)
        val first = m.resolve("x.jpg", "users/u/media/x.jpg")!!
        assertArrayEquals(byteArrayOf(5, 6), first.readBytes())
        assertEquals(1, up.downloadCalls)
        // Second resolve is a local hit — no second download.
        m.resolve("x.jpg", "users/u/media/x.jpg")
        assertEquals(1, up.downloadCalls)
    }

    @Test fun resolve_nullName_returnsNull() = runTest {
        val (m, _) = managerWith()
        assertNull(m.resolve(null, "users/u/media/x.jpg"))
    }

    @Test fun ensureUploaded_uploadsLocalOnly_andReturnsPath() = runTest {
        val (m, up) = managerWith()
        val name = m.saveImage(byteArrayOf(1))
        val path = m.ensureUploaded(name, null)
        assertEquals("users/u/images/$name", path)   // .jpg → images folder (matches FakeMediaUploader)
        assertEquals(1, up.uploadPathCalls)
    }

    @Test fun ensureUploaded_alreadyUploaded_skips() = runTest {
        val (m, up) = managerWith()
        val name = m.saveImage(byteArrayOf(1))
        val path = m.ensureUploaded(name, "users/u/media/$name")
        assertEquals("users/u/media/$name", path)
        assertEquals(0, up.uploadPathCalls)
    }

    @Test fun prefetch_downloadsWhenMissing_skipsWhenPresent() = runTest {
        val up = FakeMediaUploader().apply { uploaded["y.m4a"] = byteArrayOf(3) }
        val (m, _) = managerWith(up)
        m.prefetch("y.m4a", "users/u/media/y.m4a")
        assertEquals(1, up.downloadCalls)
        m.prefetch("y.m4a", "users/u/media/y.m4a") // now local
        assertEquals(1, up.downloadCalls)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.data.media.MediaManagerTest"`
Expected: FAIL — `MediaManager` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package nart.simpleanki.core.data.media

import java.io.File

/**
 * Local-first media policy. Reads/writes media on-device by default and only touches the
 * cloud ([MediaUploader]) as part of premium sync. The single seam used by the card form
 * (create), the display components (resolve), and the sync engine (ensureUploaded/prefetch).
 */
class MediaManager(
    private val local: LocalMediaStore,
    private val uploader: MediaUploader,
) {
    /** Create: persist [bytes] locally; returns the new filename. No network. */
    suspend fun saveImage(bytes: ByteArray): String = saveLocal(bytes, "jpg")
    suspend fun saveAudio(bytes: ByteArray): String = saveLocal(bytes, "m4a")

    private suspend fun saveLocal(bytes: ByteArray, ext: String): String {
        val name = local.newName(ext)
        local.save(name, bytes)
        return name
    }

    /** Display: local file if present; else download from [cloudPath], cache, return it. */
    suspend fun resolve(name: String?, cloudPath: String?): File? {
        if (name == null) return null
        if (local.exists(name)) return local.fileFor(name)
        if (cloudPath == null) return null
        return uploader.downloadBytes(cloudPath).getOrNull()?.let { local.save(name, it) }
    }

    /** Push: upload a local-only file; returns its cloud path (existing path if already up). */
    suspend fun ensureUploaded(name: String?, cloudPath: String?): String? {
        if (name == null) return cloudPath
        if (cloudPath != null) return cloudPath
        if (!local.exists(name)) return null
        return uploader.upload(name, local.fileFor(name).readBytes()).getOrNull()
    }

    /** Pull: download + cache a referenced file that isn't local yet. */
    suspend fun prefetch(name: String?, cloudPath: String?) {
        if (name == null || cloudPath == null || local.exists(name)) return
        uploader.downloadBytes(cloudPath).getOrNull()?.let { local.save(name, it) }
    }

    fun delete(name: String?) { if (name != null) local.delete(name) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.data.media.MediaManagerTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/data/media/MediaManager.kt \
        app/src/test/java/nart/simpleanki/core/data/media/MediaManagerTest.kt
git commit -m "Add MediaManager local-first coordinator"
```

---

## Task 4: Register LocalMediaStore + MediaManager in DI (additive)

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`

- [ ] **Step 1: Add the import for `java.io.File`**

Near the other imports in `AppModule.kt`:

```kotlin
import java.io.File
import nart.simpleanki.core.data.media.LocalMediaStore
import nart.simpleanki.core.data.media.MediaManager
```

- [ ] **Step 2: Register the singletons**

Immediately after the existing `single<MediaUploader> { FirebaseMediaRepository(get(), get()) }` (line ~64) add:

```kotlin
    single { LocalMediaStore(File(androidContext().filesDir, "media")) }
    single { MediaManager(get(), get()) }
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nart/simpleanki/di/AppModule.kt
git commit -m "Register LocalMediaStore and MediaManager in DI"
```

---

## Task 5: Display components read local-first via MediaManager

Change `MediaImage` / `AudioPlayButton` to take `(name, cloudPath)` and resolve through `MediaManager`, and update both call sites. Gating switches to the filename so local-only media (no cloud path) renders.

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/ui/components/MediaImage.kt`
- Modify: `app/src/main/java/nart/simpleanki/ui/components/AudioPlayButton.kt`
- Modify: `app/src/main/java/nart/simpleanki/feature/study/StudyScreen.kt`
- Modify: `app/src/main/java/nart/simpleanki/feature/cardform/CardFormScreen.kt`

- [ ] **Step 1: Rewrite `MediaImage.kt`**

```kotlin
package nart.simpleanki.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import nart.simpleanki.core.data.media.MediaManager
import org.koin.compose.koinInject
import java.io.File

/**
 * Displays a card image local-first: resolves [name] from on-device storage (falling back to
 * the cloud [cloudPath] and caching) via [MediaManager], then loads the file with Coil.
 */
@Composable
fun MediaImage(
    name: String,
    cloudPath: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    media: MediaManager = koinInject(),
) {
    var file by remember(name, cloudPath) { mutableStateOf<File?>(null) }
    LaunchedEffect(name, cloudPath) { file = media.resolve(name, cloudPath) }
    file?.let {
        AsyncImage(
            model = it,
            contentDescription = "Card image",
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}
```

- [ ] **Step 2: Rewrite `AudioPlayButton.kt`**

```kotlin
package nart.simpleanki.ui.components

import android.media.MediaPlayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import nart.simpleanki.core.data.media.MediaManager
import org.koin.compose.koinInject

/** Plays a card's audio local-first (resolves the file via [MediaManager], streams via MediaPlayer). */
@Composable
fun AudioPlayButton(
    name: String,
    cloudPath: String?,
    media: MediaManager = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose { runCatching { player.release() } }
    }
    IconButton(onClick = {
        scope.launch {
            val file = media.resolve(name, cloudPath) ?: return@launch
            runCatching {
                player.reset()
                player.setDataSource(file.absolutePath)
                player.setOnPreparedListener { it.start() }
                player.prepareAsync()
            }
        }
    }) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Play audio")
    }
}
```

- [ ] **Step 3: Update `StudyScreen.kt` call sites + gating**

Replace the block at lines ~107-113 (currently gating on `card.imagePath` / `card.audioPath`):

```kotlin
                card.image?.let { name ->
                    nart.simpleanki.ui.components.MediaImage(name, card.imagePath, Modifier.fillMaxWidth().height(160.dp))
                    Spacer(Modifier.height(16.dp))
                }
                Text(card.front, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                card.audioName?.let { name ->
                    nart.simpleanki.ui.components.AudioPlayButton(name, card.audioPath)
                }
```

- [ ] **Step 4: Update `CardFormScreen.kt` call sites**

The image preview (line ~224) — keep its existing `if (state.imagePath != null)` wrapper for now (Task 6 flips it to `imageName`); change only the `MediaImage(...)` call:

```kotlin
                    MediaImage(
                        state.imageName ?: "",
                        state.imagePath,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
```

The audio row (line ~244): change the call:

```kotlin
                    AudioPlayButton(state.audioName ?: "", state.audioPath)
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (No unit tests for Compose UI; correctness is build + manual verification.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/ui/components/MediaImage.kt \
        app/src/main/java/nart/simpleanki/ui/components/AudioPlayButton.kt \
        app/src/main/java/nart/simpleanki/feature/study/StudyScreen.kt \
        app/src/main/java/nart/simpleanki/feature/cardform/CardFormScreen.kt
git commit -m "Resolve card media local-first in display components"
```

---

## Task 6: Card form saves media locally

Switch `CardFormViewModel` to persist picked media via `MediaManager` (local, `imagePath = null`), flip the card-form preview gating to the filename, and rewire DI + tests.

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/cardform/CardFormViewModel.kt`
- Modify: `app/src/main/java/nart/simpleanki/feature/cardform/CardFormScreen.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Modify: `app/src/test/java/nart/simpleanki/feature/cardform/CardFormViewModelTest.kt`

- [ ] **Step 1: Update the two media tests in `CardFormViewModelTest.kt`**

Replace `imagePicked_uploads_andSavesImageRefOnCard` and `audioRecorded_uploads_andSavesAudioRefOnOriginalOnly` with local-save versions. Add imports: `import nart.simpleanki.core.data.media.LocalMediaStore`, `import nart.simpleanki.core.data.media.MediaManager`, `import org.junit.Rule`, `import org.junit.rules.TemporaryFolder`, `import org.junit.Assert.assertNull`, `import org.junit.Assert.assertNotNull`. Add the rule + helper to the class:

```kotlin
    @get:Rule val tmp = TemporaryFolder()
    private fun media() = MediaManager(LocalMediaStore(tmp.newFolder()), FakeMediaUploader())
```

```kotlin
    @Test
    fun imagePicked_savesLocally_withNoCloudPath() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { now })
        val vm = CardFormViewModel("d1", repo, media(), idGenerator = ids("c-1"), now = { now })
        vm.onFrontChange("hello"); vm.onBackChange("hola")
        vm.onImagePicked(byteArrayOf(1, 2, 3)); runCurrent()
        assertNotNull(vm.uiState.value.imageName)
        assertNull(vm.uiState.value.imagePath)
        assertFalse(vm.uiState.value.uploadingImage)

        vm.save(); runCurrent()
        val saved = dao.observeByDeck("d1").first().first()
        assertNotNull(saved.image)      // filename persisted on the card
        assertNull(saved.imagePath)     // local-only: no cloud path yet
    }

    @Test
    fun audioRecorded_savesLocally_onOriginalOnly() = runTest {
        val dao = FakeCardDao()
        val repo = CardRepository(dao, now = { now })
        val vm = CardFormViewModel("d1", repo, media(), idGenerator = ids("base", "rev"), now = { now })
        vm.onFrontChange("dog"); vm.onBackChange("perro"); vm.onToggleReverse(true)
        vm.onAudioRecorded(byteArrayOf(9, 9)); runCurrent()
        assertNotNull(vm.uiState.value.audioName)
        assertNull(vm.uiState.value.audioPath)

        vm.save(); runCurrent()
        val cards = dao.observeByDeck("d1").first().sortedBy { it.isReverse }
        assertNotNull(cards.first { !it.isReverse }.audioName)   // original keeps audio
        assertNull(cards.first { it.isReverse }.audioName)        // reverse is audio-free
    }
```

Also update the other constructor calls in this file (lines ~41, 53, 74-75, 95, 156) from `FakeMediaUploader()` to `media()` so they compile against the new `CardFormViewModel` signature. Remove the now-unused `import ...MediaRef` if present.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.feature.cardform.CardFormViewModelTest"`
Expected: FAIL — `CardFormViewModel` still takes a `MediaUploader`; `media()` returns a `MediaManager`.

- [ ] **Step 3: Update `CardFormViewModel.kt`**

Change the import and constructor parameter:

```kotlin
import nart.simpleanki.core.data.media.MediaManager
```

```kotlin
class CardFormViewModel(
    private val deckId: String,
    private val cardRepository: CardRepository,
    private val mediaManager: MediaManager,
    private val editingCardId: String? = null,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
```

Replace `onImagePicked` and `onAudioRecorded`:

```kotlin
    fun onImagePicked(bytes: ByteArray) {
        _uiState.value = _uiState.value.copy(uploadingImage = true)
        viewModelScope.launch {
            val name = mediaManager.saveImage(bytes)
            _uiState.value = _uiState.value.copy(imageName = name, imagePath = null, uploadingImage = false)
        }
    }

    fun onAudioRecorded(bytes: ByteArray) {
        _uiState.value = _uiState.value.copy(uploadingAudio = true)
        viewModelScope.launch {
            val name = mediaManager.saveAudio(bytes)
            _uiState.value = _uiState.value.copy(audioName = name, audioPath = null, uploadingAudio = false)
        }
    }
```

(The `MediaUploader` import is no longer needed in this file — remove it.)

- [ ] **Step 4: Flip the card-form preview gating in `CardFormScreen.kt`**

The image preview wrapper: change `if (state.imagePath != null) {` → `if (state.imageName != null) {`.
The audio row wrapper: change `if (state.audioPath != null) {` → `if (state.audioName != null) {`.

- [ ] **Step 5: Rewire DI in `AppModule.kt`**

Line ~131 — change the `CardFormViewModel` factory:

```kotlin
        CardFormViewModel(deckId = a.deckId, cardRepository = get(), mediaManager = get(), editingCardId = a.cardId)
```

- [ ] **Step 6: Run tests + compile to verify pass**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.feature.cardform.CardFormViewModelTest"`
Expected: PASS.
Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/cardform/CardFormViewModel.kt \
        app/src/main/java/nart/simpleanki/feature/cardform/CardFormScreen.kt \
        app/src/main/java/nart/simpleanki/di/AppModule.kt \
        app/src/test/java/nart/simpleanki/feature/cardform/CardFormViewModelTest.kt
git commit -m "Save card media locally instead of uploading on create"
```

---

## Task 7: Sync uploads on push and prefetches on pull

Add `MediaManager` to `SyncManager`: upload any local-only media before pushing a dirty card (persisting the resulting cloud path), and prefetch referenced media after pulling.

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/data/sync/SyncManager.kt`
- Modify: `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Modify: `app/src/test/java/nart/simpleanki/core/data/sync/SyncManagerTest.kt`

- [ ] **Step 1: Write failing tests in `SyncManagerTest.kt`**

Add imports at top: `import nart.simpleanki.core.data.local.CardEntity`, `import nart.simpleanki.core.data.media.FakeMediaUploader`, `import nart.simpleanki.core.data.media.LocalMediaStore`, `import nart.simpleanki.core.data.media.MediaManager`, `import org.junit.Rule`, `import org.junit.rules.TemporaryFolder`, `import org.junit.Assert.assertNotNull`, `import org.junit.Assert.assertNull`.

Add a rule + helper to the class, and update **every** existing `SyncManager(folderDao, ..., remote)` construction to pass a media manager (add `, media` as the 5th arg, where `val media = media()`):

```kotlin
    @get:Rule val tmp = TemporaryFolder()
    private fun media(uploader: FakeMediaUploader = FakeMediaUploader()) =
        Pair(MediaManager(LocalMediaStore(tmp.newFolder()), uploader), uploader)

    private fun cardEntity(
        id: String, image: String? = null, imagePath: String? = null,
        lastModified: Long = 1, dirty: Boolean = false,
    ) = CardEntity(
        id = id, front = "f", back = "b", image = image, audioName = null,
        imagePath = imagePath, audioPath = null, deckId = "d1",
        dateCreated = 0, lastModified = lastModified, memorized = false,
        fsrsDue = 0, fsrsStability = 0.0, fsrsDifficulty = 0.0, fsrsElapsedDays = 0.0,
        fsrsScheduledDays = 0.0, fsrsReps = 0, fsrsLapses = 0, fsrsState = 0,
        fsrsLastReview = null, source = null, pairId = null, dirty = dirty,
    )
```

```kotlin
    @Test
    fun push_uploadsLocalOnlyMedia_andPersistsCloudPath() = runTest {
        val cardDao = FakeCardDao()
        val (m, up) = media()
        // A dirty card whose image is local-only: seed the local store via the manager,
        // then reference that filename with no cloud path yet.
        val name = m.saveImage(byteArrayOf(1, 2, 3))
        cardDao.upsertAll(listOf(cardEntity(id = "c1", image = name, imagePath = null, dirty = true)))
        val remote = FakeRemote()
        val sync = SyncManager(FakeFolderDao(), FakeDeckDao(), cardDao, remote, m)

        sync.sync("u1")

        // Uploaded once; the local row now carries the cloud path and is no longer dirty.
        assertEquals(1, up.uploadPathCalls)
        val saved = cardDao.getById("c1")!!
        assertNotNull(saved.imagePath)
        assertFalse(saved.dirty)
    }

    @Test
    fun push_skipsUpload_whenNoLocalMedia() = runTest {
        val cardDao = FakeCardDao()
        val (m, up) = media()
        cardDao.upsertAll(listOf(cardEntity(id = "c1", image = null, imagePath = null, dirty = true)))
        val sync = SyncManager(FakeFolderDao(), FakeDeckDao(), cardDao, FakeRemote(), m)

        sync.sync("u1")

        assertEquals(0, up.uploadPathCalls)
        assertFalse(cardDao.getById("c1")!!.dirty)
    }

    @Test
    fun pull_prefetchesRemoteMedia_locally() = runTest {
        val cardDao = FakeCardDao()
        val up = FakeMediaUploader().apply { uploaded["pic.jpg"] = byteArrayOf(8, 8) }
        val store = LocalMediaStore(tmp.newFolder())
        val m = MediaManager(store, up)
        val remote = FakeRemote(
            cards = mutableListOf(
                CardDto(id = "c1", image = "pic.jpg", imagePath = "users/u/media/pic.jpg", lastModified = ts(100)),
            ),
        )
        val sync = SyncManager(FakeFolderDao(), FakeDeckDao(), cardDao, remote, m)

        sync.sync("u1")

        assertTrue(store.exists("pic.jpg"))          // prefetched + cached locally
        assertEquals(1, up.downloadCalls)
    }
```

(Check the `CardDto` constructor argument names against `FirestoreDtos.kt` — `id`, `image`, `imagePath`, `lastModified` are present; supply any other required non-default fields the constructor demands.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.data.sync.SyncManagerTest"`
Expected: FAIL — `SyncManager` constructor takes 4 args, not 5.

- [ ] **Step 3: Update `SyncManager.kt`**

Add the constructor param + import, rework the card branches of `push` and `pull`:

```kotlin
import nart.simpleanki.core.data.media.MediaManager
```

```kotlin
class SyncManager(
    private val folderDao: FolderDao,
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val remote: RemoteSyncSource,
    private val media: MediaManager,
) {
```

Replace the `cardDao.getDirty()...` block in `push` with:

```kotlin
        cardDao.getDirty().takeIf { it.isNotEmpty() }?.let { rows ->
            val updated = rows.map { entity ->
                val card = entity.toDomain()
                card.copy(
                    imagePath = media.ensureUploaded(card.image, card.imagePath),
                    audioPath = media.ensureUploaded(card.audioName, card.audioPath),
                )
            }
            remote.pushCards(uid, updated.map { CardDto.fromDomain(it) })
            // Persist any newly-assigned cloud paths and clear dirty, guarding against a
            // concurrent local edit (lastModified changed mid-push).
            updated.forEach { card ->
                val current = cardDao.getById(card.id)
                if (current != null && current.lastModified == card.lastModified) {
                    cardDao.upsertAll(listOf(card.toEntity(dirty = false)))
                }
            }
        }
```

In `pull`, replace the `remote.fetchCards(uid).forEach { ... }` block with a version that prefetches after applying:

```kotlin
        remote.fetchCards(uid).forEach { dto ->
            val domain = dto.toDomain()
            if (domain.id.isNotEmpty() &&
                shouldApplyRemote(cardDao.getById(domain.id)?.lastModified, domain.lastModified)
            ) {
                cardDao.upsertAll(listOf(domain.toEntity(dirty = false)))
                media.prefetch(domain.image, domain.imagePath)
                media.prefetch(domain.audioName, domain.audioPath)
            }
        }
```

- [ ] **Step 4: Rewire DI in `AppModule.kt`**

Line ~90 — add the 5th `get()`:

```kotlin
    single { SyncManager(get(), get(), get(), get(), get()) }
```

- [ ] **Step 5: Run tests + compile to verify pass**

Run: `./gradlew testDebugUnitTest --tests "nart.simpleanki.core.data.sync.SyncManagerTest"`
Expected: PASS (existing sync tests + the 3 new media tests).
Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/data/sync/SyncManager.kt \
        app/src/main/java/nart/simpleanki/di/AppModule.kt \
        app/src/test/java/nart/simpleanki/core/data/sync/SyncManagerTest.kt
git commit -m "Upload media on push and prefetch on pull during sync"
```

---

## Task 8: Remove the obsolete MediaUploader API (cleanup)

Now that no caller uses the eager-upload / download-URL methods, remove them and `MediaRef`.

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/core/data/media/MediaRepository.kt`
- Modify: `app/src/test/java/nart/simpleanki/core/data/media/FakeMediaUploader.kt`

- [ ] **Step 1: Confirm there are no remaining callers**

Run: `grep -rn "uploadImage\|uploadAudio\|downloadUrl\|MediaRef" app/src/main app/src/test`
Expected: matches only inside `MediaRepository.kt` and `FakeMediaUploader.kt` (the definitions being removed). If anything else matches, migrate it first.

- [ ] **Step 2: Trim `MediaRepository.kt`**

The interface becomes:

```kotlin
/** Media upload/lookup seam; faked in tests. */
interface MediaUploader {
    /** Uploads [bytes] under the given [name]; returns the cloud storage path. */
    suspend fun upload(name: String, bytes: ByteArray): Result<String>
    /** Downloads the raw bytes stored at [path]. */
    suspend fun downloadBytes(path: String): Result<ByteArray>
}
```

Delete the `MediaRef` data class, and delete `uploadImage`, `uploadAudio`, `downloadUrl`, and the private `upload(bytes, folder, ext)` helper from `FirebaseMediaRepository` (keep the new `upload(name,bytes)` + `downloadBytes`). Remove the now-unused `java.util.UUID` import if present.

- [ ] **Step 3: Trim `FakeMediaUploader.kt`**

Remove `uploadImage`/`uploadAudio`/`downloadUrl` and the `MediaRef`-typed fields (`uploadResult`, `audioUploadResult`, `uploadCalls`, `audioUploadCalls`). Keep `uploaded`, `uploadPathCalls`, `downloadCalls`, `uploadPathResult`, and the `upload`/`downloadBytes` overrides. Final file:

```kotlin
package nart.simpleanki.core.data.media

/** In-memory [MediaUploader] for unit tests. */
class FakeMediaUploader : MediaUploader {
    /** name -> bytes, simulating the cloud. */
    val uploaded = mutableMapOf<String, ByteArray>()
    var uploadPathCalls = 0
    var downloadCalls = 0
    /** Override to simulate upload failure. */
    var uploadPathResult: ((String) -> Result<String>)? = null

    override suspend fun upload(name: String, bytes: ByteArray): Result<String> {
        uploadPathCalls++
        uploadPathResult?.let { return it(name) }
        uploaded[name] = bytes
        return Result.success("users/u/media/$name")
    }

    override suspend fun downloadBytes(path: String): Result<ByteArray> {
        downloadCalls++
        val name = path.substringAfterLast('/')
        return uploaded[name]?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("not found: $path"))
    }
}
```

- [ ] **Step 4: Run the full unit-test suite + compile**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (whole suite).
Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/data/media/MediaRepository.kt \
        app/src/test/java/nart/simpleanki/core/data/media/FakeMediaUploader.kt
git commit -m "Remove obsolete eager-upload MediaUploader API"
```

---

## Final verification (after Task 8)

- [ ] `./gradlew testDebugUnitTest` — full unit suite green.
- [ ] `./gradlew assembleDebug` — app builds.
- [ ] Manual smoke (emulator): create a card with an image while **signed out / free** → image displays from local storage (no network); kill & reopen → still displays. Existing synced cards with cloud-only media still display (lazy migration on first view).

---

## Spec coverage check

| Spec section | Task(s) |
|---|---|
| Storage model (filesDir/media, `imagePath==null` = local-only) | 1, 3 |
| `LocalMediaStore` | 1 |
| `MediaManager` (saveImage/saveAudio/resolve/ensureUploaded/prefetch/delete) | 3 |
| `MediaUploader` API change (upload+downloadBytes, drop downloadUrl) | 2, 8 |
| Create flow (local save, no network) | 6 |
| Display flow (local-first + cloud fallback, gate on filename) | 5 |
| Push flow (upload-on-sync, persist path) | 7 |
| Pull flow (eager prefetch) | 7 |
| Premium gating (already at call sites; tests verify behavior) | note + 7 |
| Error handling (graceful nulls, per-card resilience) | 3, 7 |
| Testing | 1, 3, 6, 7 |
| Out of scope (eviction, compression, multi-media, HTML, apkg) | — (excluded) |
