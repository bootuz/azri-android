# Analytics (LogManager) — Design

**Date:** 2026-06-04
**Status:** Approved (design); pending implementation plan
**Ports:** the iOS `AzriKit/Sources/AzriKit/Logs` system (`LogManager` + `LogService` +
`LoggableEvent` + `LogType`, with Console / FirebaseAnalytics / FirebaseCrashlytics services).

## Goal

Give the Android app the same analytics backbone the iOS app has: a single `LogManager`
coordinator that fans every tracked event out to multiple pluggable backends (Logcat,
Firebase Analytics, Firebase Crashlytics). Every user action and screen view becomes a
tracked event, defined close to the code that fires it.

This project delivers the **infrastructure + cross-cutting wiring + 3 exemplar features** to
lock the conventions. A focused **follow-up project** instruments the remaining ViewModels.

## Background: the iOS pattern we port

`AzriKit/Sources/AzriKit/Logs`:
- `LogManager` (coordinator) holds `[any LogService]` and forwards `trackEvent`,
  `trackScreenEvent`, `identifyUser`, `addUserProperties`, `deleteUserProfile` to each.
- `LogService` (protocol) is implemented by `ConsoleService` (OSLog), `FirebaseAnalyticsService`,
  `FirebaseCrashlyticsService`.
- `LoggableEvent` (protocol): `eventName`, `parameters: [String: Any]?`, `type: LogType`.
  `AnyLoggableEvent` is the concrete ad-hoc struct.
- `LogType`: `info`, `analytic`, `warning`, `severe`.
- Each feature defines a nested `enum Event: LoggableEvent` (e.g. `CSVImportViewModel.Event`)
  giving each case an `eventName`, `parameters`, and `type`.
- `FirebaseAnalyticsService` cleans parameters before sending: event name ≤40 chars with spaces
  replaced by underscores, keys ≤40 chars, string values ≤100 chars, at most 25 parameters,
  and unsupported value types coerced to strings or dropped. It skips `.info` events and emits
  screen views via `AnalyticsEventScreenView`.

## Decisions (from brainstorming)

- **Backends:** Logcat + Firebase Analytics + Firebase Crashlytics (full iOS parity).
- **Scope:** phased — infrastructure + automatic screen tracking + user identification + 3
  exemplar features now; remaining 14 ViewModels in a follow-up.
- **`LogManager` is injected into ViewModels** as a Koin constructor parameter.
- **Event names are pure `snake_case`** (Firebase-native), not mirrored from iOS names.
- **Debug builds disable Analytics + Crashlytics collection**; Logcat is always on. Keeps dev
  noise out of production dashboards while preserving local visibility.

## Components

All new code under `app/src/main/java/nart/simpleanki/core/analytics/`.

### `LoggableEvent.kt`
```kotlin
interface LoggableEvent {
    val eventName: String
    val params: Map<String, Any?> get() = emptyMap()
    val type: LogType get() = LogType.Analytic
}
data class AnyLoggableEvent(
    override val eventName: String,
    override val params: Map<String, Any?> = emptyMap(),
    override val type: LogType = LogType.Analytic,
) : LoggableEvent

enum class LogType { Info, Analytic, Warning, Severe }
```
`LogType` also exposes an Android Logcat priority mapping (Info→`Log.i`, Analytic→`Log.d`,
Warning→`Log.w`, Severe→`Log.e`) and an emoji for readable Logcat lines (mirrors iOS).

### `LogService.kt` (interface)
```kotlin
interface LogService {
    fun identifyUser(uid: String, name: String?, email: String?)
    fun clearUser()
    fun track(event: LoggableEvent)
    fun trackScreen(name: String)
}
```
(iOS's `addUserProperties`/`deleteUserProfile` collapse to `clearUser` for v1 — YAGNI; the
firebase user-property path can be added when a feature needs it.)

### `LogcatService.kt`
Logs everything (all `LogType`s) to Logcat at the mapped priority, with the type emoji, event
name, and sorted params. `trackScreen` logs a `screen_view` line. `identifyUser`/`clearUser`
log informational lines. Always active (debug and release).

### `FirebaseAnalyticsService.kt`
Wraps `FirebaseAnalytics`.
- `track`: skips `Info` events; cleans params; calls `analytics.logEvent(cleanName, bundle)`.
- `trackScreen`: `logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, { SCREEN_NAME = cleanName })`.
- `identifyUser`: `setUserId(uid)`, `setUserProperty("account_name", name)` /
  `"account_email", email` when present. `clearUser`: `setUserId(null)`.
- Parameter cleaning is a **pure top-level function** `cleanAnalyticsParams(name, params):
  Pair<String, Bundle-input>` (returns the cleaned event name + a cleaned `Map<String, Any>`
  ready to load into a `Bundle`), so it is unit-testable without the Firebase SDK:
  - event name & keys: `.take(40)`, spaces→`_`;
  - drop entries with `null` values;
  - `String` → clipped to 100 chars; `Int`/`Long` → `Long`; `Float`/`Double` → `Double`;
    `Boolean` → `Long` (0/1); anything else → `toString()` clipped to 100;
  - keep at most the first 25 entries (stable order).
- The thin `track`/`trackScreen` methods that build the `Bundle` and call Firebase are
  build-verified only; the cleaning function carries the tested logic.

### `FirebaseCrashlyticsService.kt`
Wraps `FirebaseCrashlytics`. Acts only on `Warning`/`Severe` events:
- `Warning` → `crashlytics.log("event_name {params}")`.
- `Severe` → `crashlytics.log(...)` **and** `recordException(AnalyticsEvent(eventName))` (a small
  internal `Exception` subtype) so severe events surface as non-fatals.
- `identifyUser` → `setUserId(uid)`; `clearUser` → `setUserId("")`. `track` ignores
  `Info`/`Analytic`; `trackScreen` is a no-op.

### `LogManager.kt` (coordinator)
```kotlin
class LogManager(private val services: List<LogService>) {
    fun identifyUser(uid: String, name: String?, email: String?) { services.forEach { it.identifyUser(uid, name, email) } }
    fun clearUser() { services.forEach { it.clearUser() } }
    fun track(event: LoggableEvent) { services.forEach { it.track(event) } }
    fun trackScreen(name: String) { services.forEach { it.trackScreen(name) } }
}
```

## Dependencies & build

- `gradle/libs.versions.toml`: add `firebase-analytics` and `firebase-crashlytics` libraries
  (versioned by the existing Firebase BOM), plus the `firebase-crashlytics` Gradle plugin
  (`com.google.firebase.crashlytics`) with a version.
- Root `build.gradle.kts`: declare the crashlytics plugin (apply false); `app/build.gradle.kts`:
  apply the plugin and add the two `implementation` deps.
- **Collection gating** (in DI, see below): in `BuildConfig.DEBUG`, call
  `analytics.setAnalyticsCollectionEnabled(false)` and
  `crashlytics.isCrashlyticsCollectionEnabled = false`; enabled otherwise.

## DI wiring (`AppModule.kt`)

```kotlin
single { Firebase.analytics }
single { Firebase.crashlytics }
single {
    val analytics: FirebaseAnalytics = get()
    val crashlytics: FirebaseCrashlytics = get()
    analytics.setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)
    crashlytics.isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
    LogManager(listOf(
        LogcatService(),
        FirebaseAnalyticsService(analytics),
        FirebaseCrashlyticsService(crashlytics),
    ))
}
```
Exemplar ViewModel factories gain `logManager = get()`.

## Cross-cutting wiring

### Screen views (automatic) — `AzriNavHost.kt`
A single listener attached once:
```kotlin
val logManager: LogManager = koinInject()
DisposableEffect(nav) {
    val listener = NavController.OnDestinationChangedListener { _, dest, _ ->
        dest.route?.let { logManager.trackScreen(screenName(it)) }
    }
    nav.addOnDestinationChangedListener(listener)
    onDispose { nav.removeOnDestinationChangedListener(listener) }
}
```
`screenName(route)` strips argument placeholders (e.g. `deck/{deckId}` → `deck`) so screen
names are stable. This covers all 16 screens with one hook.

### User identification — `AzriRoot.kt`
In the `SignedIn` branch:
```kotlin
val logManager: LogManager = koinInject()
LaunchedEffect(s.user.uid) {
    logManager.identifyUser(s.user.uid, s.user.displayName, s.user.email)
}
```
In the signed-out (`else`) branch, a `LaunchedEffect(Unit) { logManager.clearUser() }`.

## Exemplar instrumentation (locks the conventions)

Each feature gets a nested `enum class Event(...) : LoggableEvent` and `logManager.track(...)`
calls. ViewModels receive `logManager: LogManager` via Koin.

1. **Auth — `AuthViewModel`**: `sign_in_google`, `continue_as_guest`, `sign_out`. User
   identification (`identifyUser`) is owned by `AzriRoot`'s auth-state hook, so the VM only
   tracks the action events. Sign-in failures → a `Warning` event `sign_in_failed` with a
   `reason` param.
2. **Study review — `StudyViewModel`**: `review_session_start` (param `deck_id` when scoped),
   `card_rated` (param `rating` ∈ again/hard/good/easy), `review_session_complete` (param
   `count`).
3. **Card CRUD — `CardFormViewModel`**: `card_created` / `card_updated` (params `has_image`,
   `has_audio` as booleans).

Event names are `snake_case`; params use `snake_case` keys.

## Data flow

User action in a ViewModel → `logManager.track(Event.X)` → fan-out to all services →
LogcatService prints (always); FirebaseAnalyticsService cleans + `logEvent` (release, non-Info);
FirebaseCrashlyticsService logs/records (release, Warning+Severe). Screen navigation →
`OnDestinationChangedListener` → `logManager.trackScreen(name)`. Auth state change in AzriRoot →
`identifyUser` / `clearUser` fanned to all services.

## Error handling

Analytics is best-effort and must never crash the app: each `LogService` swallows its own
exceptions (a backend failure cannot break a user action). `LogManager` fan-out wraps each
service call so one failing service does not stop the others. Disabled collection in debug means
no network in dev.

## Testing

- **`LogManager`** — a `FakeLogService` records calls; assert `track`/`trackScreen`/
  `identifyUser`/`clearUser` fan out to every service, and that one throwing service does not
  prevent the others from being called.
- **`cleanAnalyticsParams`** (pure) — event-name clipping + space→underscore; key clipping;
  null drop; String clip to 100; Int/Long→Long; Float/Double→Double; Boolean→Long; unknown
  type→toString clip; ≤25 entries cap.
- **`LogType`** — Logcat priority + emoji mapping.
- **`screenName(route)`** (pure helper) — strips `/{arg}` placeholders; passes through plain
  routes.
- **Exemplar ViewModels** — inject a fake `LogManager` (or real `LogManager` with a
  `FakeLogService`) and assert the expected `Event` (name + key params + type) is tracked for
  each action; e.g. `card_rated` carries the correct `rating`, `review_session_complete` the
  `count`.
- The Firebase/Crashlytics wrapper classes and the NavHost listener / AzriRoot hook are
  build-verified (SDK + Compose-nav calls aren't JVM-unit-testable).

## Out of scope (this project)

Instrumenting the other 14 ViewModels (follow-up project: Library, DeckDetail, FolderDetail,
DeckEdit, FolderEdit, Settings, Profile, Paywall, Sync, Notifications, DailyGoal, StudyQueue,
Apkg/Csv import — the import VMs already have iOS event vocabularies to mirror); A/B testing;
Remote Config; arbitrary user properties beyond name/email; conversion/funnel definitions in the
Firebase console.
