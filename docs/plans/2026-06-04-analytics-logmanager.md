# Analytics (LogManager) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the iOS `LogManager` analytics system to Android — a single coordinator fanning every tracked event out to Logcat, Firebase Analytics, and Firebase Crashlytics — plus automatic screen-view tracking, user identification on auth, and 3 exemplar instrumented features.

**Architecture:** A new `core/analytics/` package: `LoggableEvent`/`LogType` value types, a `LogService` interface with three implementations (`LogcatService`, `FirebaseAnalyticsService`, `FirebaseCrashlyticsService`), and a `LogManager` coordinator registered as a Koin singleton. Parameter-cleaning and route→screen-name logic are pure functions (unit-tested); the Firebase wrapper classes and Compose/nav hooks are build-verified. Each instrumented ViewModel takes `logManager: LogManager` (no-op default so existing tests are untouched) and defines a nested `sealed interface Event : LoggableEvent`.

**Tech Stack:** Kotlin, Jetpack Compose + navigation-compose, Koin, Firebase Analytics + Crashlytics (via the existing Firebase BOM 34.4.0), the Crashlytics Gradle plugin, JUnit4 + coroutines-test + turbine. Build with `JAVA_HOME=/opt/homebrew/opt/openjdk`; prefix every Gradle command with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`.

**Convention note:** The spec says "nested `enum class Event`"; because Android events carry call-time params, this plan uses a nested **`sealed interface Event : LoggableEvent`** (`data object` for param-less events, `data class` for parameterized ones) — the correct Kotlin analog of iOS's enum-with-associated-values. Event names are pure `snake_case`.

---

## File Structure

**Create (main):**
- `app/src/main/java/nart/simpleanki/core/analytics/LoggableEvent.kt` — `LoggableEvent`, `AnyLoggableEvent`, `LogType`.
- `app/src/main/java/nart/simpleanki/core/analytics/LogService.kt` — the `LogService` interface.
- `app/src/main/java/nart/simpleanki/core/analytics/LogManager.kt` — fan-out coordinator.
- `app/src/main/java/nart/simpleanki/core/analytics/AnalyticsParams.kt` — pure `cleanAnalyticsParams(...)`.
- `app/src/main/java/nart/simpleanki/core/analytics/ScreenNames.kt` — pure `screenName(route)`.
- `app/src/main/java/nart/simpleanki/core/analytics/LogcatService.kt`
- `app/src/main/java/nart/simpleanki/core/analytics/FirebaseAnalyticsService.kt`
- `app/src/main/java/nart/simpleanki/core/analytics/FirebaseCrashlyticsService.kt`

**Create (test):**
- `app/src/test/java/nart/simpleanki/core/analytics/FakeLogService.kt`
- `app/src/test/java/nart/simpleanki/core/analytics/LogManagerTest.kt`
- `app/src/test/java/nart/simpleanki/core/analytics/AnalyticsParamsTest.kt`
- `app/src/test/java/nart/simpleanki/core/analytics/ScreenNamesTest.kt`

**Modify:**
- `gradle/libs.versions.toml`, root `build.gradle.kts`, `app/build.gradle.kts` — Crashlytics plugin + the two Firebase deps.
- `app/src/main/java/nart/simpleanki/di/AppModule.kt` — Firebase singletons, `LogManager` single, inject into 3 VMs.
- `app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt` — screen-view listener.
- `app/src/main/java/nart/simpleanki/ui/AzriRoot.kt` — user identification.
- `app/src/main/java/nart/simpleanki/auth/AuthViewModel.kt` (+ test) — exemplar.
- `app/src/main/java/nart/simpleanki/feature/study/StudyViewModel.kt` (+ test) — exemplar.
- `app/src/main/java/nart/simpleanki/feature/cardform/CardFormViewModel.kt` (+ test) — exemplar.

---

## Task 1: Add Firebase Analytics + Crashlytics dependencies and the Crashlytics plugin

**Files:** `gradle/libs.versions.toml`, root `build.gradle.kts`, `app/build.gradle.kts`

- [ ] **Step 1: Add version + libraries + plugin to the catalog**

In `gradle/libs.versions.toml` under `[versions]`, add:
```toml
crashlyticsPlugin = "3.0.3"
```
Under `[libraries]` (near the other firebase entries), add:
```toml
firebase-analytics = { group = "com.google.firebase", name = "firebase-analytics" }
firebase-crashlytics = { group = "com.google.firebase", name = "firebase-crashlytics" }
```
Under `[plugins]`, add:
```toml
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version.ref = "crashlyticsPlugin" }
```

- [ ] **Step 2: Declare the plugin at the root**

In `build.gradle.kts` (root), add to the `plugins {}` block:
```kotlin
    alias(libs.plugins.firebase.crashlytics) apply false
```

- [ ] **Step 3: Apply the plugin and add the deps in the app module**

In `app/build.gradle.kts`, add to the `plugins {}` block (after `alias(libs.plugins.google.services)`):
```kotlin
    alias(libs.plugins.firebase.crashlytics)
```
In `dependencies {}`, next to the existing firebase deps, add:
```kotlin
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
```

- [ ] **Step 4: Verify it resolves and the plugin applies**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:dependencies --configuration debugRuntimeClasspath -q 2>&1 | grep -iE "firebase-analytics|firebase-crashlytics"`
Expected: lines showing `com.google.firebase:firebase-analytics` and `:firebase-crashlytics`.

Then run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin -q 2>&1 | tail -6`
Expected: `BUILD SUCCESSFUL`.

If the Crashlytics plugin version `3.0.3` fails to resolve or is rejected under AGP 9, find the latest published `com.google.firebase.crashlytics` Gradle plugin version and update `crashlyticsPlugin` in the catalog, then re-run.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts
git commit -m "Add Firebase Analytics and Crashlytics dependencies"
```

---

## Task 2: Core value types + LogManager coordinator

**Files:**
- Create: `core/analytics/LoggableEvent.kt`, `core/analytics/LogService.kt`, `core/analytics/LogManager.kt`
- Test: `test/.../core/analytics/FakeLogService.kt`, `test/.../core/analytics/LogManagerTest.kt`

- [ ] **Step 1: Write the value types**

`app/src/main/java/nart/simpleanki/core/analytics/LoggableEvent.kt`:
```kotlin
package nart.simpleanki.core.analytics

/** An analytics event. Mirrors the iOS `LoggableEvent`. */
interface LoggableEvent {
    val eventName: String
    val params: Map<String, Any?> get() = emptyMap()
    val type: LogType get() = LogType.Analytic
}

/** Ad-hoc event when a dedicated type isn't warranted. */
data class AnyLoggableEvent(
    override val eventName: String,
    override val params: Map<String, Any?> = emptyMap(),
    override val type: LogType = LogType.Analytic,
) : LoggableEvent

/** Severity / routing hint. Analytics ignores [Info]; Crashlytics only acts on [Warning]/[Severe]. */
enum class LogType {
    Info, Analytic, Warning, Severe;

    val emoji: String
        get() = when (this) {
            Info -> "👋"
            Analytic -> "📈"
            Warning -> "⚠️"
            Severe -> "🚨"
        }
}
```

`app/src/main/java/nart/simpleanki/core/analytics/LogService.kt`:
```kotlin
package nart.simpleanki.core.analytics

/** A single analytics backend. The iOS `LogService` protocol. */
interface LogService {
    fun identifyUser(uid: String, name: String?, email: String?)
    fun clearUser()
    fun track(event: LoggableEvent)
    fun trackScreen(name: String)
}
```

- [ ] **Step 2: Write the fake + failing test**

`app/src/test/java/nart/simpleanki/core/analytics/FakeLogService.kt`:
```kotlin
package nart.simpleanki.core.analytics

/** Records calls for assertions. [throwOnTrack] simulates a misbehaving backend. */
class FakeLogService(private val throwOnTrack: Boolean = false) : LogService {
    val events = mutableListOf<LoggableEvent>()
    val screens = mutableListOf<String>()
    var identified: Triple<String, String?, String?>? = null
    var cleared = false

    override fun identifyUser(uid: String, name: String?, email: String?) { identified = Triple(uid, name, email) }
    override fun clearUser() { cleared = true }
    override fun track(event: LoggableEvent) {
        if (throwOnTrack) throw RuntimeException("backend down")
        events.add(event)
    }
    override fun trackScreen(name: String) { screens.add(name) }
}
```

`app/src/test/java/nart/simpleanki/core/analytics/LogManagerTest.kt`:
```kotlin
package nart.simpleanki.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogManagerTest {

    @Test fun track_fansOutToEveryService() {
        val a = FakeLogService(); val b = FakeLogService()
        val mgr = LogManager(listOf(a, b))
        val event = AnyLoggableEvent("test_event", mapOf("k" to 1))
        mgr.track(event)
        assertEquals(listOf(event), a.events)
        assertEquals(listOf(event), b.events)
    }

    @Test fun trackScreen_identify_clear_fanOut() {
        val a = FakeLogService(); val b = FakeLogService()
        val mgr = LogManager(listOf(a, b))
        mgr.trackScreen("home")
        mgr.identifyUser("u1", "Grace", "g@x.com")
        mgr.clearUser()
        assertEquals(listOf("home"), a.screens)
        assertEquals(Triple("u1", "Grace", "g@x.com"), b.identified)
        assertTrue(a.cleared && b.cleared)
    }

    @Test fun oneThrowingService_doesNotStopOthers() {
        val bad = FakeLogService(throwOnTrack = true)
        val good = FakeLogService()
        val mgr = LogManager(listOf(bad, good))
        mgr.track(AnyLoggableEvent("e"))
        assertEquals(1, good.events.size) // good still received it despite bad throwing
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.analytics.LogManagerTest" 2>&1 | tail -12`
Expected: FAIL (Unresolved reference: LogManager).

- [ ] **Step 4: Write `LogManager.kt`**

`app/src/main/java/nart/simpleanki/core/analytics/LogManager.kt`:
```kotlin
package nart.simpleanki.core.analytics

/**
 * Fans every analytics call out to all [services]. Mirrors the iOS `LogManager`.
 * Each service call is isolated so one failing backend can't break a user action
 * or starve the other backends.
 */
class LogManager(private val services: List<LogService>) {

    fun identifyUser(uid: String, name: String?, email: String?) =
        forEachService { it.identifyUser(uid, name, email) }

    fun clearUser() = forEachService { it.clearUser() }

    fun track(event: LoggableEvent) = forEachService { it.track(event) }

    fun trackScreen(name: String) = forEachService { it.trackScreen(name) }

    private inline fun forEachService(action: (LogService) -> Unit) {
        for (service in services) {
            runCatching { action(service) }
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.analytics.LogManagerTest" 2>&1 | tail -12`
Expected: `BUILD SUCCESSFUL`, 3 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/analytics/LoggableEvent.kt app/src/main/java/nart/simpleanki/core/analytics/LogService.kt app/src/main/java/nart/simpleanki/core/analytics/LogManager.kt app/src/test/java/nart/simpleanki/core/analytics/FakeLogService.kt app/src/test/java/nart/simpleanki/core/analytics/LogManagerTest.kt
git commit -m "Add LogManager coordinator and analytics value types"
```

---

## Task 3: `cleanAnalyticsParams` pure function

**Files:**
- Create: `core/analytics/AnalyticsParams.kt`
- Test: `test/.../core/analytics/AnalyticsParamsTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/nart/simpleanki/core/analytics/AnalyticsParamsTest.kt`:
```kotlin
package nart.simpleanki.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsParamsTest {

    @Test fun cleansEventName_clipsAndReplacesSpaces() {
        val (name, _) = cleanAnalyticsParams("hello world event", emptyMap())
        assertEquals("hello_world_event", name)
        val (long, _) = cleanAnalyticsParams("x".repeat(50), emptyMap())
        assertEquals(40, long.first.length) // clipped to 40
    }

    @Test fun dropsNullValues() {
        val (_, params) = cleanAnalyticsParams("e", mapOf("a" to null, "b" to 1L))
        assertFalse(params.containsKey("a"))
        assertTrue(params.containsKey("b"))
    }

    @Test fun coercesValueTypes() {
        val (_, p) = cleanAnalyticsParams("e", mapOf(
            "i" to 3, "l" to 4L, "f" to 1.5f, "d" to 2.5, "bt" to true, "bf" to false, "s" to "hi",
        ))
        assertEquals(3L, p["i"]); assertEquals(4L, p["l"])
        assertEquals(2.5, p["d"]); assertEquals(1L, p["bt"]); assertEquals(0L, p["bf"])
        assertEquals("hi", p["s"])
        assertEquals(1.5, (p["f"] as Double), 0.001)
    }

    @Test fun stringValuesClippedTo100_unknownTypesStringified() {
        val (_, p) = cleanAnalyticsParams("e", mapOf("s" to "x".repeat(150), "list" to listOf(1, 2)))
        assertEquals(100, (p["s"] as String).length)
        assertEquals("[1, 2]", p["list"]) // unknown type -> toString, clipped to 100
    }

    @Test fun clipsLongKeysTo40() {
        val key = "k".repeat(50)
        val (_, p) = cleanAnalyticsParams("e", mapOf(key to 1L))
        assertTrue(p.keys.all { it.length <= 40 })
        assertEquals(1L, p.values.first())
    }

    @Test fun capsAt25Entries() {
        val many = (1..40).associate { "k$it" to it.toLong() }
        val (_, p) = cleanAnalyticsParams("e", many)
        assertEquals(25, p.size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.analytics.AnalyticsParamsTest" 2>&1 | tail -12`
Expected: FAIL (Unresolved reference: cleanAnalyticsParams).

- [ ] **Step 3: Write `AnalyticsParams.kt`**

`app/src/main/java/nart/simpleanki/core/analytics/AnalyticsParams.kt`:
```kotlin
package nart.simpleanki.core.analytics

private const val MAX_NAME = 40
private const val MAX_KEY = 40
private const val MAX_VALUE = 100
private const val MAX_PARAMS = 25

/** lowercases nothing (Firebase is case-sensitive) but clips length and replaces spaces. */
private fun clean(text: String, max: Int): String =
    text.replace(' ', '_').take(max)

/**
 * Applies the iOS Firebase-Analytics hygiene rules and returns a cleaned event name plus a
 * map whose values are only `Long`, `Double`, or `String` (ready to load into a Bundle).
 * Pure and SDK-free so it is unit-testable. Mirrors `FirebaseAnalyticsService` on iOS.
 */
fun cleanAnalyticsParams(
    eventName: String,
    params: Map<String, Any?>,
): Pair<String, Map<String, Any>> {
    val cleaned = LinkedHashMap<String, Any>()
    for ((rawKey, rawValue) in params) {
        if (cleaned.size >= MAX_PARAMS) break
        if (rawValue == null) continue
        val key = clean(rawKey, MAX_KEY)
        val value: Any = when (rawValue) {
            is Int -> rawValue.toLong()
            is Long -> rawValue
            is Boolean -> if (rawValue) 1L else 0L
            is Float -> rawValue.toDouble()
            is Double -> rawValue
            is String -> rawValue.take(MAX_VALUE)
            else -> rawValue.toString().take(MAX_VALUE)
        }
        cleaned[key] = value
    }
    return clean(eventName, MAX_NAME) to cleaned
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.analytics.AnalyticsParamsTest" 2>&1 | tail -12`
Expected: `BUILD SUCCESSFUL`, 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/analytics/AnalyticsParams.kt app/src/test/java/nart/simpleanki/core/analytics/AnalyticsParamsTest.kt
git commit -m "Add cleanAnalyticsParams hygiene function"
```

---

## Task 4: `screenName` pure helper

**Files:**
- Create: `core/analytics/ScreenNames.kt`
- Test: `test/.../core/analytics/ScreenNamesTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/nart/simpleanki/core/analytics/ScreenNamesTest.kt`:
```kotlin
package nart.simpleanki.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenNamesTest {
    @Test fun plainRoutePassesThrough() = assertEquals("library", screenName("library"))
    @Test fun stripsSingleArgPlaceholder() = assertEquals("deck", screenName("deck/{deckId}"))
    @Test fun stripsTrailingArgsKeepsBase() = assertEquals("study", screenName("study/{deckId}/{folderId}"))
    @Test fun nullRouteBecomesUnknown() = assertEquals("unknown", screenName(null))
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.analytics.ScreenNamesTest" 2>&1 | tail -12`
Expected: FAIL (Unresolved reference: screenName).

- [ ] **Step 3: Write `ScreenNames.kt`**

`app/src/main/java/nart/simpleanki/core/analytics/ScreenNames.kt`:
```kotlin
package nart.simpleanki.core.analytics

/**
 * Turns a nav route into a stable screen name for analytics: drops argument segments so
 * `deck/{deckId}` and `study/{a}/{b}` collapse to `deck` / `study`. Null → "unknown".
 */
fun screenName(route: String?): String {
    if (route.isNullOrBlank()) return "unknown"
    return route.substringBefore("/{").substringBefore("/")
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.core.analytics.ScreenNamesTest" 2>&1 | tail -12`
Expected: `BUILD SUCCESSFUL`, 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/analytics/ScreenNames.kt app/src/test/java/nart/simpleanki/core/analytics/ScreenNamesTest.kt
git commit -m "Add screenName route helper"
```

---

## Task 5: The three LogService backends (build-verified)

**Files:** Create `core/analytics/LogcatService.kt`, `FirebaseAnalyticsService.kt`, `FirebaseCrashlyticsService.kt`

These wrap Android/Firebase SDKs (not JVM-unit-testable); the tested logic lives in `cleanAnalyticsParams`. Verify by compiling.

- [ ] **Step 1: Write `LogcatService.kt`**

```kotlin
package nart.simpleanki.core.analytics

import android.util.Log

/** Logs every event to Logcat (all severities). The iOS `ConsoleService`. Always active. */
class LogcatService(private val printParams: Boolean = true) : LogService {

    override fun identifyUser(uid: String, name: String?, email: String?) {
        Log.i(TAG, "👤 identify uid=$uid name=${name ?: "-"} email=${email ?: "-"}")
    }

    override fun clearUser() { Log.i(TAG, "👤 clear user") }

    override fun track(event: LoggableEvent) {
        val msg = buildString {
            append("${event.type.emoji} ${event.eventName}")
            if (printParams && event.params.isNotEmpty()) {
                event.params.toSortedMap(compareBy { it }).forEach { (k, v) -> append("\n  $k=$v") }
            }
        }
        when (event.type) {
            LogType.Info -> Log.i(TAG, msg)
            LogType.Analytic -> Log.d(TAG, msg)
            LogType.Warning -> Log.w(TAG, msg)
            LogType.Severe -> Log.e(TAG, msg)
        }
    }

    override fun trackScreen(name: String) { Log.d(TAG, "📱 screen_view $name") }

    private companion object { const val TAG = "AzriAnalytics" }
}
```

- [ ] **Step 2: Write `FirebaseAnalyticsService.kt`**

```kotlin
package nart.simpleanki.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/** Sends cleaned events + screen views + user props to Firebase Analytics. Skips [LogType.Info]. */
class FirebaseAnalyticsService(private val analytics: FirebaseAnalytics) : LogService {

    override fun identifyUser(uid: String, name: String?, email: String?) {
        analytics.setUserId(uid)
        name?.let { analytics.setUserProperty("account_name", it.take(100)) }
        email?.let { analytics.setUserProperty("account_email", it.take(100)) }
    }

    override fun clearUser() { analytics.setUserId(null) }

    override fun track(event: LoggableEvent) {
        if (event.type == LogType.Info) return
        val (name, params) = cleanAnalyticsParams(event.eventName, event.params)
        val bundle = params.toBundle()
        analytics.logEvent(name, if (params.isEmpty()) null else bundle)
    }

    override fun trackScreen(name: String) {
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName(name))
        })
    }

    private fun Map<String, Any>.toBundle(): Bundle = Bundle().apply {
        forEach { (k, v) ->
            when (v) {
                is Long -> putLong(k, v)
                is Double -> putDouble(k, v)
                is String -> putString(k, v)
            }
        }
    }
}
```

- [ ] **Step 3: Write `FirebaseCrashlyticsService.kt`**

```kotlin
package nart.simpleanki.core.analytics

import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Surfaces [LogType.Warning]/[LogType.Severe] events to Crashlytics; Severe also records a non-fatal. */
class FirebaseCrashlyticsService(private val crashlytics: FirebaseCrashlytics) : LogService {

    override fun identifyUser(uid: String, name: String?, email: String?) { crashlytics.setUserId(uid) }

    override fun clearUser() { crashlytics.setUserId("") }

    override fun track(event: LoggableEvent) {
        if (event.type != LogType.Warning && event.type != LogType.Severe) return
        crashlytics.log("${event.eventName} ${event.params}")
        if (event.type == LogType.Severe) {
            crashlytics.recordException(AnalyticsEventException(event.eventName))
        }
    }

    override fun trackScreen(name: String) { /* screen views are not crash signals */ }
}

/** Non-fatal marker so severe analytics events appear in Crashlytics. */
class AnalyticsEventException(eventName: String) : Exception(eventName)
```

- [ ] **Step 4: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin -q 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`. (If a Firebase symbol like `FirebaseAnalytics.Event.SCREEN_VIEW` differs, check the `com.google.firebase.analytics.FirebaseAnalytics` API on the BOM and adjust the wrapper — do not change behavior.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nart/simpleanki/core/analytics/LogcatService.kt app/src/main/java/nart/simpleanki/core/analytics/FirebaseAnalyticsService.kt app/src/main/java/nart/simpleanki/core/analytics/FirebaseCrashlyticsService.kt
git commit -m "Add Logcat, Firebase Analytics, and Crashlytics log services"
```

---

## Task 6: DI wiring + collection gating

**Files:** Modify `app/src/main/java/nart/simpleanki/di/AppModule.kt`

- [ ] **Step 1: Add imports**

Add near the other firebase imports (top of the file):
```kotlin
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.crashlytics
import nart.simpleanki.BuildConfig
import nart.simpleanki.core.analytics.FirebaseAnalyticsService
import nart.simpleanki.core.analytics.FirebaseCrashlyticsService
import nart.simpleanki.core.analytics.LogManager
import nart.simpleanki.core.analytics.LogcatService
```

- [ ] **Step 2: Register the Firebase singletons + LogManager**

Immediately after the existing `single<FirebaseStorage> { Firebase.storage }` line, add:
```kotlin
    single<FirebaseAnalytics> { Firebase.analytics }
    single<FirebaseCrashlytics> { Firebase.crashlytics }
    single {
        val analytics: FirebaseAnalytics = get()
        val crashlytics: FirebaseCrashlytics = get()
        // Keep dev traffic out of production dashboards; Logcat still shows everything locally.
        analytics.setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)
        crashlytics.isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        LogManager(
            listOf(
                LogcatService(),
                FirebaseAnalyticsService(analytics),
                FirebaseCrashlyticsService(crashlytics),
            ),
        )
    }
```

- [ ] **Step 3: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin -q 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`. (If `Firebase.analytics` / `Firebase.crashlytics` accessor imports differ on this BOM, mirror however `Firebase.auth` is imported — `com.google.firebase.<product>.<accessor>` — and adjust.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nart/simpleanki/di/AppModule.kt
git commit -m "Register LogManager and Firebase analytics singletons in Koin"
```

---

## Task 7: Cross-cutting wiring — screen views + user identification (build-verified)

**Files:** Modify `app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt`, `app/src/main/java/nart/simpleanki/ui/AzriRoot.kt`

- [ ] **Step 1: Add the screen-view listener in `AzriNavHost`**

In `AzriNavHost.kt`, add imports:
```kotlin
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import nart.simpleanki.core.analytics.LogManager
import nart.simpleanki.core.analytics.screenName
import org.koin.compose.koinInject
```
Inside `fun AzriNavHost()`, immediately after `val nav = rememberNavController()`, add:
```kotlin
    val logManager: LogManager = koinInject()
    DisposableEffect(nav) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            logManager.trackScreen(screenName(destination.route))
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }
```

- [ ] **Step 2: Add user identification in `AzriRoot`**

In `AzriRoot.kt`, add imports:
```kotlin
import nart.simpleanki.core.analytics.LogManager
```
(`koinInject` is already imported.) Inside `fun AzriRoot(...)`, after `val scope = rememberCoroutineScope()`, add:
```kotlin
    val logManager: LogManager = koinInject()
```
In the `is AuthUiState.SignedIn` branch, add a second `LaunchedEffect` next to the sync one:
```kotlin
            LaunchedEffect(s.user.uid) {
                logManager.identifyUser(s.user.uid, s.user.displayName, s.user.email)
            }
```
In the `else ->` branch (the `SignInScreen` case), wrap identification clearing by adding before `SignInScreen(`:
```kotlin
            LaunchedEffect(Unit) { logManager.clearUser() }
```
(Place the `LaunchedEffect` as the first statement inside the `else -> { ... }` block; if `else ->` currently has no braces, add them so both the effect and `SignInScreen(...)` are inside.)

- [ ] **Step 3: Verify it compiles**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin -q 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nart/simpleanki/ui/navigation/AzriNavHost.kt app/src/main/java/nart/simpleanki/ui/AzriRoot.kt
git commit -m "Track screen views and identify users via LogManager"
```

---

## Task 8: Exemplar — Auth events

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/auth/AuthViewModel.kt`, `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Test: `app/src/test/java/nart/simpleanki/auth/AuthViewModelTest.kt`

- [ ] **Step 1: Write the failing test (append to `AuthViewModelTest.kt`)**

Add these imports to the test file:
```kotlin
import nart.simpleanki.core.analytics.FakeLogService
import nart.simpleanki.core.analytics.LogManager
```
Add these tests inside the class:
```kotlin
    @Test
    fun guestSignIn_tracksContinueAsGuest() = runTest {
        val log = FakeLogService()
        val vm = AuthViewModel(FakeAuthRepository(), LogManager(listOf(log)))
        vm.onContinueAsGuest()
        assertTrue(log.events.any { it.eventName == "continue_as_guest" })
    }

    @Test
    fun signOut_tracksSignOut() = runTest {
        val log = FakeLogService()
        val vm = AuthViewModel(FakeAuthRepository(), LogManager(listOf(log)))
        vm.onSignOut()
        assertTrue(log.events.any { it.eventName == "sign_out" })
    }

    @Test
    fun googleSignInError_tracksWarning() = runTest {
        val log = FakeLogService()
        val vm = AuthViewModel(FakeAuthRepository(), LogManager(listOf(log)))
        vm.onGoogleSignInError("cancelled")
        val e = log.events.first { it.eventName == "sign_in_failed" }
        assertEquals("cancelled", e.params["reason"])
    }
```
(Add `import nart.simpleanki.core.analytics` items; `assertEquals` is already imported.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.auth.AuthViewModelTest" 2>&1 | tail -15`
Expected: FAIL (constructor takes 1 arg / unresolved events).

- [ ] **Step 3: Update `AuthViewModel.kt`**

Add imports:
```kotlin
import nart.simpleanki.core.analytics.LoggableEvent
import nart.simpleanki.core.analytics.LogManager
import nart.simpleanki.core.analytics.LogType
```
Change the constructor to add the logManager param (no-op default so existing call sites/tests are unaffected):
```kotlin
class AuthViewModel(
    private val repository: AuthRepository,
    private val logManager: LogManager = LogManager(emptyList()),
) : ViewModel() {
```
Add the nested event vocabulary at the end of the class body (before the closing brace):
```kotlin
    private sealed interface Event : LoggableEvent {
        data object SignInGoogle : Event { override val eventName = "sign_in_google" }
        data object ContinueAsGuest : Event { override val eventName = "continue_as_guest" }
        data object SignOut : Event { override val eventName = "sign_out" }
        data class SignInFailed(val reason: String) : Event {
            override val eventName = "sign_in_failed"
            override val params get() = mapOf("reason" to reason)
            override val type get() = LogType.Warning
        }
    }
```
Wire the tracking into the actions:
```kotlin
    fun onGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            repository.signInWithGoogle(idToken)
                .onSuccess { logManager.track(Event.SignInGoogle) }
                .onFailure {
                    logManager.track(Event.SignInFailed(it.message ?: "unknown"))
                    _uiState.value = AuthUiState.Error(it.message ?: "Google sign-in failed")
                }
        }
    }

    fun onContinueAsGuest() {
        viewModelScope.launch {
            repository.signInAnonymously()
                .onSuccess { logManager.track(Event.ContinueAsGuest) }
                .onFailure {
                    logManager.track(Event.SignInFailed(it.message ?: "unknown"))
                    _uiState.value = AuthUiState.Error(it.message ?: "Guest sign-in failed")
                }
        }
    }

    fun onGoogleSignInError(message: String) {
        logManager.track(Event.SignInFailed(message))
        _uiState.value = AuthUiState.Error(message)
    }

    fun onSignOut() {
        logManager.track(Event.SignOut)
        repository.signOut()
    }
```

- [ ] **Step 4: Inject the real LogManager in DI**

In `AppModule.kt`, change `viewModel { AuthViewModel(get()) }` to:
```kotlin
    viewModel { AuthViewModel(get(), get()) }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.auth.AuthViewModelTest" 2>&1 | tail -15`
Expected: `BUILD SUCCESSFUL`, all tests (existing + 3 new) pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/auth/AuthViewModel.kt app/src/main/java/nart/simpleanki/di/AppModule.kt app/src/test/java/nart/simpleanki/auth/AuthViewModelTest.kt
git commit -m "Instrument auth actions with analytics events"
```

---

## Task 9: Exemplar — Study review events

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/study/StudyViewModel.kt`, `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/study/StudyViewModelTest.kt`

- [ ] **Step 1: Write the failing test (append to `StudyViewModelTest.kt`)**

Add imports:
```kotlin
import nart.simpleanki.core.analytics.FakeLogService
import nart.simpleanki.core.analytics.LogManager
```
Add tests:
```kotlin
    @Test
    fun load_tracksReviewSessionStart() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        repo.upsert(newCard("c1"))
        val log = FakeLogService()
        StudyViewModel(null, null, repo, DeckRepository(FakeDeckDao(), now = { now }), FakeSettingsRepository(), now = { now }, logManager = LogManager(listOf(log)))
        runCurrent()
        assertTrue(log.events.any { it.eventName == "review_session_start" })
    }

    @Test
    fun rating_tracksCardRated_andCompletionOnLastCard() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        repo.upsert(newCard("c1"))
        val log = FakeLogService()
        val vm = StudyViewModel(null, null, repo, DeckRepository(FakeDeckDao(), now = { now }), FakeSettingsRepository(), now = { now }, logManager = LogManager(listOf(log)))
        runCurrent()
        vm.onRate(Rating.Good)
        runCurrent()
        // card_rated carries the lowercased rating name; review_session_complete carries the Int count.
        val rated = log.events.first { it.eventName == "card_rated" }
        assertEquals("good", rated.params["rating"])
        val done = log.events.first { it.eventName == "review_session_complete" }
        assertEquals(1, done.params["count"])
    }

- [ ] **Step 2: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.study.StudyViewModelTest" 2>&1 | tail -15`
Expected: FAIL (constructor arg / unresolved).

- [ ] **Step 3: Update `StudyViewModel.kt`**

Add imports:
```kotlin
import nart.simpleanki.core.analytics.LoggableEvent
import nart.simpleanki.core.analytics.LogManager
```
Add the `logManager` param (no-op default, placed after `now`):
```kotlin
class StudyViewModel(
    private val deckId: String?,
    private val folderId: String?,
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val logManager: LogManager = LogManager(emptyList()),
) : ViewModel() {
```
At the end of `load()` (after the `_uiState.value = StudyUiState(...)` assignment), add:
```kotlin
        logManager.track(Event.ReviewSessionStart(deckId, folderId))
```
In `onRate`, after computing `next` and before/after setting state, add the two tracks:
```kotlin
        logManager.track(Event.CardRated(rating))
        if (next == null) logManager.track(Event.ReviewSessionComplete(prev.completed + 1))
```
(Place these right after `_uiState.value = prev.copy(...)`.)
Add the nested event vocabulary before the class's closing brace:
```kotlin
    private sealed interface Event : LoggableEvent {
        data class ReviewSessionStart(val deckId: String?, val folderId: String?) : Event {
            override val eventName = "review_session_start"
            override val params get() = buildMap {
                deckId?.let { put("deck_id", it) }
                folderId?.let { put("folder_id", it) }
            }
        }
        data class CardRated(val rating: Rating) : Event {
            override val eventName = "card_rated"
            override val params get() = mapOf("rating" to rating.name.lowercase())
        }
        data class ReviewSessionComplete(val count: Int) : Event {
            override val eventName = "review_session_complete"
            override val params get() = mapOf("count" to count)
        }
    }
```

- [ ] **Step 4: Inject the real LogManager in DI**

In `AppModule.kt`, in the `StudyViewModel(...)` factory, add `logManager = get()` as the last argument:
```kotlin
        StudyViewModel(
            deckId = args.deckId,
            folderId = args.folderId,
            cardRepository = get(),
            deckRepository = get(),
            settingsRepository = get(),
            logManager = get(),
        )
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.study.StudyViewModelTest" 2>&1 | tail -15`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/study/StudyViewModel.kt app/src/main/java/nart/simpleanki/di/AppModule.kt app/src/test/java/nart/simpleanki/feature/study/StudyViewModelTest.kt
git commit -m "Instrument study review with analytics events"
```

---

## Task 10: Exemplar — Card create/update events

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/cardform/CardFormViewModel.kt`, `app/src/main/java/nart/simpleanki/di/AppModule.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/cardform/CardFormViewModelTest.kt`

- [ ] **Step 1: Write the failing test (append to `CardFormViewModelTest.kt`)**

Add imports:
```kotlin
import nart.simpleanki.core.analytics.FakeLogService
import nart.simpleanki.core.analytics.LogManager
```
Add tests:
```kotlin
    @Test
    fun save_newCard_tracksCardCreated() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        val log = FakeLogService()
        val vm = CardFormViewModel("d1", repo, media(), now = { now }, logManager = LogManager(listOf(log)))
        vm.onFrontChange("hello"); vm.onBackChange("hola")
        vm.save(); runCurrent()
        val e = log.events.first { it.eventName == "card_created" }
        assertEquals(false, e.params["has_image"])
        assertEquals(false, e.params["has_audio"])
    }

    @Test
    fun save_existingCard_tracksCardUpdated() = runTest {
        val repo = CardRepository(FakeCardDao(), now = { now })
        repo.upsert(
            Card(id = "c1", front = "a", back = "b", deckId = "d1",
                dateCreated = now, lastModified = now, fsrsDue = now, fsrsState = CardState.New.value),
        )
        val log = FakeLogService()
        val vm = CardFormViewModel("d1", repo, media(), editingCardId = "c1", now = { now }, logManager = LogManager(listOf(log)))
        runCurrent()
        vm.onFrontChange("a2"); vm.save(); runCurrent()
        assertTrue(log.events.any { it.eventName == "card_updated" })
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.cardform.CardFormViewModelTest" 2>&1 | tail -15`
Expected: FAIL (constructor arg / unresolved).

- [ ] **Step 3: Update `CardFormViewModel.kt`**

Add imports:
```kotlin
import nart.simpleanki.core.analytics.LoggableEvent
import nart.simpleanki.core.analytics.LogManager
```
Add the `logManager` param (no-op default, last param):
```kotlin
class CardFormViewModel(
    private val deckId: String,
    private val cardRepository: CardRepository,
    private val mediaManager: MediaManager,
    private val editingCardId: String? = null,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = { System.currentTimeMillis() },
    private val logManager: LogManager = LogManager(emptyList()),
) : ViewModel() {
```
In `save()`, in the edit branch, after the `cardRepository.upsert(existing.copy(...))` and before setting `finished`, add:
```kotlin
                logManager.track(Event.CardUpdated(state.imageName != null, state.audioName != null))
```
In the new-card branch, after the reverse-card block and before resetting state, add:
```kotlin
                logManager.track(Event.CardCreated(state.imageName != null, state.audioName != null, state.createReverse))
```
Add the nested event vocabulary before the class's closing brace:
```kotlin
    private sealed interface Event : LoggableEvent {
        data class CardCreated(val hasImage: Boolean, val hasAudio: Boolean, val reverse: Boolean) : Event {
            override val eventName = "card_created"
            override val params get() = mapOf("has_image" to hasImage, "has_audio" to hasAudio, "reverse" to reverse)
        }
        data class CardUpdated(val hasImage: Boolean, val hasAudio: Boolean) : Event {
            override val eventName = "card_updated"
            override val params get() = mapOf("has_image" to hasImage, "has_audio" to hasAudio)
        }
    }
```

- [ ] **Step 4: Inject the real LogManager in DI**

In `AppModule.kt`, change the `CardFormViewModel(...)` construction to pass `logManager = get()`:
```kotlin
        CardFormViewModel(deckId = a.deckId, cardRepository = get(), mediaManager = get(), editingCardId = a.cardId, logManager = get())
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.cardform.CardFormViewModelTest" 2>&1 | tail -15`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/cardform/CardFormViewModel.kt app/src/main/java/nart/simpleanki/di/AppModule.kt app/src/test/java/nart/simpleanki/feature/cardform/CardFormViewModelTest.kt
git commit -m "Instrument card create/update with analytics events"
```

---

## Task 11: Full build + test gate

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit suite**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest 2>&1 | tail -12`
Expected: `BUILD SUCCESSFUL`; the new analytics tests + the three extended VM test classes all pass; no regressions.

- [ ] **Step 2: Assemble the debug APK (verifies the Crashlytics plugin + google-services wiring)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:assembleDebug 2>&1 | tail -12`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Final commit (only if anything is outstanding)**

```bash
git status
# Clean tree expected; nothing to commit.
```

---

## Manual verification checklist (post-implementation, on emulator)

Not automated — for the human/agent to sanity-check:

- Run a debug build; in Logcat filter by tag `AzriAnalytics`. Navigating between tabs prints `📱 screen_view <name>`; signing in prints `👤 identify ...`; rating a card prints `📈 card_rated rating=good`; saving a card prints `📈 card_created ...`.
- Because debug disables Firebase collection, these appear in Logcat only (not in the Firebase console). Flip to a release build (or temporarily enable collection) to see them land in Firebase DebugView.
