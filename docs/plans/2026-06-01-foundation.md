# Azri Android — Foundation Sub-Plan (Phase 1, part 1 of 5)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-scaffold `azri_android` into a Jetpack Compose + Material 3 + Hilt app wired to Firebase (`simple-anki-166ea`), that launches, performs Google Sign-In + anonymous auth, gates content behind auth, and shows a signed-in shell — with tests.

**Architecture:** Single Gradle module `app` to start (modularization into `core:data`/`core:domain`/`feature:*` happens in later sub-plans as code volume justifies it). MVVM + Hilt DI + Navigation-Compose. `AuthRepository` wraps Firebase Auth; `AuthViewModel` exposes `StateFlow<AuthUiState>`.

**Tech Stack:** Kotlin, Jetpack Compose (BOM) + Material 3, Hilt, Navigation-Compose, Firebase BOM (Auth, Firestore), AndroidX Credential Manager + Google ID for Google Sign-In, JUnit4 + MockK + Turbine + coroutines-test (unit), Compose UI test + Robolectric where useful.

**Subsequent sub-plans (not here):** (2) Data + Sync (Room + Firestore DTOs + repos + SyncManager), (3) FSRS-6 + Study Queue, (4) Library/DeckDetail/CardForm/Settings UI, (5) Study/Review flow.

---

## File Structure

- `azri_android/gradle/libs.versions.toml` — version catalog (Compose, Hilt, Firebase, credentials).
- `azri_android/build.gradle.kts`, `azri_android/settings.gradle.kts` — root config, plugins, google-services.
- `azri_android/app/build.gradle.kts` — Compose/Hilt/Firebase plugins + deps, namespace `nart.simpleanki`.
- `azri_android/app/google-services.json` — from Firebase (obtained via MCP).
- `app/src/main/java/nart/simpleanki/`
  - `AzriApplication.kt` — `@HiltAndroidApp`.
  - `MainActivity.kt` — `@AndroidEntryPoint`, sets Compose content, edge-to-edge.
  - `ui/theme/{Color.kt,Theme.kt,Type.kt}` — Material 3 theme mirroring iOS SA color tokens, light/dark.
  - `ui/navigation/AzriNavHost.kt` — routes: `signin`, `home`.
  - `auth/AuthRepository.kt` — interface + `FirebaseAuthRepository` impl (`authState: Flow<AuthUser?>`, `signInWithGoogle(idToken)`, `signInAnonymously()`, `signOut()`).
  - `auth/AuthUser.kt` — domain model (`uid`, `displayName?`, `email?`, `isAnonymous`).
  - `auth/GoogleSignInClient.kt` — Credential Manager wrapper returning a Google ID token.
  - `auth/AuthViewModel.kt` — `AuthUiState` + `StateFlow`, calls repository.
  - `feature/auth/SignInScreen.kt` — Compose sign-in UI (Google button, "continue as guest").
  - `feature/home/HomeScreen.kt` — placeholder signed-in shell (shows uid/email + sign-out).
  - `di/AppModule.kt` — Hilt provides FirebaseAuth, FirebaseFirestore, AuthRepository binding.
- Tests:
  - `app/src/test/java/nart/simpleanki/auth/AuthViewModelTest.kt`
  - `app/src/test/java/nart/simpleanki/auth/FakeAuthRepository.kt`
  - `app/src/androidTest/java/nart/simpleanki/feature/auth/SignInScreenTest.kt`
  - `app/src/androidTest/java/nart/simpleanki/feature/home/HomeScreenTest.kt`

---

## Pre-Task: Firebase Android app registration (Claude-run, via MCP)

- [ ] Register Android app `nart.simpleanki` in project `simple-anki-166ea` (`firebase_create_app`).
- [ ] Generate debug-keystore SHA-1 (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`) and register it (`firebase_create_android_sha`).
- [ ] Fetch `google-services.json` (`firebase_get_sdk_config`) → write to `azri_android/app/`.
- [ ] Verify Google sign-in provider is enabled in Auth; if not, **report to user as a manual action**.
- [ ] Record the Google **Web client ID** (server-side OAuth client) needed by Credential Manager → if absent, **report to user as a manual action**.

---

## Task 1: Convert Gradle build to Compose + Hilt + Firebase

**Files:** `gradle/libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`

- [ ] **Step 1:** Rewrite `libs.versions.toml` adding: kotlin, compose-bom, androidx activity-compose, lifecycle-viewmodel-compose, navigation-compose, hilt + hilt-navigation-compose, firebase-bom + auth + firestore, credentials + credentials-play-services-auth + googleid, and test libs (mockk, turbine, kotlinx-coroutines-test, compose-ui-test-junit4). Plugins: android-application, kotlin-android, kotlin-compose, hilt, google-services, ksp.
- [ ] **Step 2:** Update root `build.gradle.kts` to declare the new plugins `apply false`; add the `google-services` and `hilt` classpath/plugin entries.
- [ ] **Step 3:** Rewrite `app/build.gradle.kts`: apply plugins (android-application, kotlin-android, kotlin-compose, ksp, hilt, google-services); set `namespace`/`applicationId` to `nart.simpleanki`; enable `buildFeatures { compose = true }`; Java 17; add all dependencies from the catalog.
- [ ] **Step 4:** Run `./gradlew :app:help` (or `:app:dependencies`) to confirm the build configures cleanly.
  Expected: `BUILD SUCCESSFUL`, no plugin/version resolution errors.
- [ ] **Step 5:** Commit. `git -C azri_android add -A && git -C azri_android commit -m "build: convert to Compose + Hilt + Firebase"` *(init git in azri_android first if absent)*.

---

## Task 2: App skeleton (Application, Activity, theme, manifest, DI)

**Files:** `AzriApplication.kt`, `MainActivity.kt`, `ui/theme/*`, `di/AppModule.kt`, `AndroidManifest.xml`, delete template `MainActivity`/`res/layout` if present.

- [ ] **Step 1:** Create `AzriApplication` (`@HiltAndroidApp`) and register it in the manifest (`android:name`). Remove `appcompat`/XML-views theme leftovers; set the Material3 Compose theme.
- [ ] **Step 2:** Create `ui/theme/{Color,Theme,Type}.kt` — define `LightColorScheme`/`DarkColorScheme` from the iOS SA tokens (SAPrimary etc., read from `SimpleAnkiSwiftUI/.../Assets.xcassets/Colors`), `AzriTheme { }` honoring system dark mode + dynamic color off (brand colors).
- [ ] **Step 2b:** Create `di/AppModule.kt` — `@Module @InstallIn(SingletonComponent)` providing `FirebaseAuth`, `FirebaseFirestore`; `@Binds` `AuthRepository` → `FirebaseAuthRepository`.
- [ ] **Step 3:** Create `MainActivity` (`@AndroidEntryPoint`) calling `enableEdgeToEdge()` and `setContent { AzriTheme { AzriNavHost() } }`.
- [ ] **Step 4:** Build + install debug to the emulator/device; confirm it launches without crash.
  Run: `./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`. Launch and confirm no crash via logcat.
- [ ] **Step 5:** Commit `feat: Hilt app skeleton + Material3 theme + Compose entry`.

---

## Task 3: AuthRepository (TDD) — domain + Firebase impl

**Files:** `auth/AuthUser.kt`, `auth/AuthRepository.kt`, test `auth/FakeAuthRepository.kt`.

- [ ] **Step 1 (test-first):** Write `FakeAuthRepository` implementing the `AuthRepository` interface with an in-memory `MutableStateFlow<AuthUser?>` and recordable calls; this is the seam used by `AuthViewModelTest`. Define the interface to satisfy it:
  ```kotlin
  data class AuthUser(val uid: String, val displayName: String?, val email: String?, val isAnonymous: Boolean)
  interface AuthRepository {
      val authState: Flow<AuthUser?>
      suspend fun signInWithGoogle(idToken: String): Result<AuthUser>
      suspend fun signInAnonymously(): Result<AuthUser>
      fun signOut()
  }
  ```
- [ ] **Step 2:** Implement `FirebaseAuthRepository(auth: FirebaseAuth)`: `authState` as a `callbackFlow` over `AuthStateListener` mapping `FirebaseUser` → `AuthUser`; `signInWithGoogle` via `GoogleAuthProvider.getCredential(idToken, null)` + `auth.signInWithCredential(...).await()` wrapped in `runCatching`; `signInAnonymously` via `auth.signInAnonymously().await()`; `signOut` via `auth.signOut()`.
- [ ] **Step 3:** Build (`:app:compileDebugKotlin`). Expected: compiles.
- [ ] **Step 4:** Commit `feat: AuthRepository (Firebase + fake)`.

---

## Task 4: AuthViewModel (TDD)

**Files:** `auth/AuthViewModel.kt`, test `auth/AuthViewModelTest.kt`.

- [ ] **Step 1 (failing test):** With `FakeAuthRepository` + `kotlinx-coroutines-test` + Turbine, assert: initial state `SignedOut`; after `authState` emits a user → `SignedIn(user)`; `signInAnonymously()` success flips to `SignedIn`; a failing `signInWithGoogle` sets `error`.
  ```kotlin
  sealed interface AuthUiState { object Loading: AuthUiState; object SignedOut: AuthUiState
    data class SignedIn(val user: AuthUser): AuthUiState; data class Error(val message: String): AuthUiState }
  ```
- [ ] **Step 2:** Run the test → FAIL (`AuthViewModel` unresolved).
  Run: `./gradlew :app:testDebugUnitTest --tests "*AuthViewModelTest*"`
- [ ] **Step 3:** Implement `AuthViewModel @Inject constructor(repo)`: collect `authState` into `_uiState`; `onGoogleIdToken(token)` → `repo.signInWithGoogle`; `onContinueAsGuest()` → `repo.signInAnonymously`; `onSignOut()` → `repo.signOut`.
- [ ] **Step 4:** Run the test → PASS.
- [ ] **Step 5:** Commit `feat: AuthViewModel + tests`.

---

## Task 5: Google Sign-In client (Credential Manager)

**Files:** `auth/GoogleSignInClient.kt`.

- [ ] **Step 1:** Implement `GoogleSignInClient(context, webClientId)` exposing `suspend fun getIdToken(): Result<String>` using `CredentialManager` + `GetCredentialRequest` with `GetGoogleIdOption` (`setServerClientId(webClientId)`, `setFilterByAuthorizedAccounts(false)`), extracting the token from `GoogleIdTokenCredential`. Provide `webClientId` via Hilt from a `BuildConfig`/string resource set from the Firebase Web client ID.
- [ ] **Step 2:** Build (`:app:compileDebugKotlin`). Expected: compiles. *(Behavior is verified in the Task 7 device run, since Credential Manager requires Google Play services.)*
- [ ] **Step 3:** Commit `feat: Google Sign-In via Credential Manager`.

---

## Task 6: SignInScreen + HomeScreen + NavHost (Compose UI tests)

**Files:** `feature/auth/SignInScreen.kt`, `feature/home/HomeScreen.kt`, `ui/navigation/AzriNavHost.kt`, tests `SignInScreenTest.kt`, `HomeScreenTest.kt`.

- [ ] **Step 1 (UI test-first):** `SignInScreenTest` — given a state-hoisted `SignInScreen(onGoogleClick, onGuestClick)`, assert the Google button + guest button render and invoke their lambdas on click (`createComposeRule`, `onNodeWithText`, `performClick`).
- [ ] **Step 2:** Implement `SignInScreen` (logo, tagline, Material3 `Button` "Sign in with Google", `TextButton` "Continue as guest"); state-hoisted (no direct VM dependency) for testability.
- [ ] **Step 3:** Implement `HomeScreen(user, onSignOut)` placeholder (welcome + uid/email + sign-out button) and `HomeScreenTest` asserting it shows the email and fires `onSignOut`.
- [ ] **Step 4:** Implement `AzriNavHost`: collect `AuthViewModel.uiState`; when `SignedIn` → `home`, else → `signin`; wire screen lambdas to VM (`onGuestClick` → `onContinueAsGuest`, Google click → launch `GoogleSignInClient.getIdToken()` then `onGoogleIdToken`).
- [ ] **Step 5:** Run UI tests on device/emulator. Expected: PASS.
  Run: `./gradlew :app:connectedDebugAndroidTest --tests "*SignInScreenTest*" --tests "*HomeScreenTest*"`
- [ ] **Step 6:** Commit `feat: sign-in + home screens + auth-gated nav`.

---

## Task 7: Live end-to-end auth verification

- [ ] **Step 1:** Launch on emulator/device. Tap "Continue as guest" → lands on Home with an anonymous uid. Verify a corresponding anonymous user appears via `auth_get_users` (Firebase MCP).
- [ ] **Step 2:** Sign out → returns to sign-in. Tap "Sign in with Google", complete the flow → Home shows the Google email; verify the user in Firebase Auth.
- [ ] **Step 3:** If Google sign-in fails with a config error (SHA-1 / web client ID / provider disabled), capture the exact error and **report the precise manual action to the user**.
- [ ] **Step 4:** Commit any fixups `chore: foundation e2e auth verified`.

---

## Self-Review notes

- Covers spec §3 (DI/nav/theme), §7 (Firebase registration, SHA-1, Google provider, web client ID as manual actions), and the Auth slice of §6 build-order step 1 & 4.
- Modularization (`core:*`, `feature:*` modules) is deferred to later sub-plans by design — Foundation stays single-module to reduce setup risk; this is an explicit scope choice, not a gap.
- No persistence/sync/FSRS here — those are sub-plans 2 and 3.
