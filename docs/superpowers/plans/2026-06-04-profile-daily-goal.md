# Daily Goal in Profile Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Daily goal" row to Profile → Settings that opens the existing daily-goal editor sheet, with live supporting text.

**Architecture:** `ProfileViewModel` surfaces the goal (`dailyGoalEnabled` + `dailyGoalTotal`) from settings it already observes; `ProfileContent` gains a clickable row; the stateful `ProfileScreen` hosts the existing `DailyGoalEditorSheet` (reused from `feature/queue`) behind a visibility flag. No new editor, no settings/data changes.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Koin, JUnit4 + coroutines-test (existing fakes), `material-icons-extended`.

**Build/test prefix:** all Gradle commands MUST be prefixed with `export JAVA_HOME=/opt/homebrew/opt/openjdk &&`. Run from `/Users/astemirboziev/Developer/SimpleAnkiProject/azri_android`.

**Note:** the emulator is currently unavailable, so instrumented (`androidTest`) changes are COMPILE-verified only (`compileDebugAndroidTestKotlin`), not run.

---

### Task 1: Expose the daily goal from `ProfileViewModel`

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/profile/ProfileViewModel.kt`
- Test: `app/src/test/java/nart/simpleanki/feature/profile/ProfileViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

In `ProfileViewModelTest.kt`, add this import near the other `core.data.settings` imports:

```kotlin
import nart.simpleanki.core.data.settings.AppSettings
```

Then add this test method inside `class ProfileViewModelTest`:

```kotlin
    @Test
    fun reflectsDailyGoal_fromSettings() = runTest {
        val settings = FakeSettingsRepository(
            AppSettings(dailyGoalEnabled = true, newCardsTarget = 10, reviewCardsTarget = 20),
        )
        val vm = ProfileViewModel(settings, auth(null), FakeEntitlementRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertTrue(vm.uiState.value.dailyGoalEnabled)
        assertEquals(30, vm.uiState.value.dailyGoalTotal) // 10 new + 20 review
    }
```

- [ ] **Step 2: Run the test to verify it FAILS**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.profile.ProfileViewModelTest"`
Expected: FAIL — compile error `Unresolved reference: dailyGoalEnabled` (and `dailyGoalTotal`) on `ProfileUiState`.

- [ ] **Step 3: Add the two fields to `ProfileUiState`**

In `ProfileViewModel.kt`, add the fields to the `ProfileUiState` data class (after `isPremium`):

```kotlin
data class ProfileUiState(
    val email: String? = null,
    val isAnonymous: Boolean = true,
    val preset: FsrsPreset = FsrsPreset.Optimal,
    val themeMode: ThemeMode = ThemeMode.System,
    val isPremium: Boolean = false,
    val dailyGoalEnabled: Boolean = false,
    val dailyGoalTotal: Int = 0,
)
```

- [ ] **Step 4: Populate them in the `combine` mapping**

In `ProfileViewModel.kt`, add this import near the other `core.data.settings` imports:

```kotlin
import nart.simpleanki.core.data.settings.dailyGoalTotal
```

Then add the two fields to the `ProfileUiState(...)` built inside the `combine` (after `isPremium = entitlement.isPremium,`):

```kotlin
            ProfileUiState(
                email = user?.email,
                isAnonymous = user?.isAnonymous ?: true,
                preset = settings.preset,
                themeMode = settings.themeMode,
                isPremium = entitlement.isPremium,
                dailyGoalEnabled = settings.dailyGoalEnabled,
                dailyGoalTotal = settings.dailyGoalTotal,
            )
```

(`settings.dailyGoalTotal` is the existing extension `val AppSettings.dailyGoalTotal: Int get() = newCardsTarget + reviewCardsTarget` in `SettingsRepository.kt`.)

- [ ] **Step 5: Run the test to verify it PASSES**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest --tests "nart.simpleanki.feature.profile.ProfileViewModelTest"`
Expected: PASS (all `ProfileViewModelTest` methods green, including the new one).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/profile/ProfileViewModel.kt app/src/test/java/nart/simpleanki/feature/profile/ProfileViewModelTest.kt
git commit -m "Expose daily goal in ProfileViewModel"
```

---

### Task 2: "Daily goal" row + sheet hosting in `ProfileScreen`

**Files:**
- Modify: `app/src/main/java/nart/simpleanki/feature/profile/ProfileScreen.kt`
- Modify: `app/src/androidTest/java/nart/simpleanki/feature/profile/ProfileContentTest.kt`

Build-verified (Compose UI + preview; instrumented test compile-verified, not run).

- [ ] **Step 1: Add two imports to `ProfileScreen.kt`**

Add (keep existing imports):

```kotlin
import androidx.compose.material.icons.filled.Flag
import nart.simpleanki.feature.queue.DailyGoalEditorSheet
```

- [ ] **Step 2: Add the `onOpenDailyGoal` parameter to `ProfileContent`**

In `ProfileContent`'s parameter list, add a defaulted parameter (place it right after `onOpenNotifications: () -> Unit = {},`):

```kotlin
    onOpenDailyGoal: () -> Unit = {},
```

- [ ] **Step 3: Add the "Daily goal" row in the Settings section**

In `ProfileContent`, the Settings section currently has the "Spaced repetition" `ListItem` followed by the "Notifications" `ListItem`. Insert this new row BETWEEN them (after "Spaced repetition", before "Notifications"):

```kotlin
            ListItem(
                headlineContent = { Text("Daily goal") },
                supportingContent = {
                    Text(if (state.dailyGoalEnabled) "${state.dailyGoalTotal} cards/day" else "Off")
                },
                leadingContent = { Icon(Icons.Default.Flag, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.clickable(onClick = onOpenDailyGoal),
            )
```

- [ ] **Step 4: Host the sheet in `ProfileScreen` and wire the callback**

In `ProfileScreen`, add a visibility flag and pass the callback, then render the sheet. Replace the body of `ProfileScreen` (the part from `val state by ...` through the `ProfileContent(...)` call) so it reads:

```kotlin
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDailyGoalSheet by remember { mutableStateOf(false) }

    fun openUrl(url: String) =
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    ProfileContent(
        state = state,
        onOpenFsrsSettings = onOpenFsrsSettings,
        onOpenNotifications = onOpenNotifications,
        onOpenDailyGoal = { showDailyGoalSheet = true },
        onOpenPaywall = onOpenPaywall,
        onRestorePurchases = {
            viewModel.restorePurchases { result ->
                val msg = if (result == PurchaseResult.Success) "Purchases restored" else "No purchases to restore"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        },
        onThemeChange = viewModel::setThemeMode,
        onSignOut = viewModel::signOut,
        onDeleteAccount = viewModel::deleteAccount,
        onRate = { openUrl(PLAY_URL) },
        onSupport = { openUrl(SUPPORT_EMAIL) },
        onShare = {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Study smarter with Azri Flashcards: $PLAY_URL")
            runCatching { context.startActivity(Intent.createChooser(send, "Share Azri")) }
        },
        onReddit = { openUrl(REDDIT_URL) },
        onTerms = { openUrl(TERMS_URL) },
        onPrivacy = { openUrl(PRIVACY_URL) },
    )

    if (showDailyGoalSheet) {
        DailyGoalEditorSheet(onDismiss = { showDailyGoalSheet = false })
    }
```

(This is the existing `ProfileContent(...)` call with `onOpenDailyGoal = { showDailyGoalSheet = true }` added and the sheet rendered afterward. `remember`/`mutableStateOf`/`getValue`/`setValue` are already imported in this file.)

- [ ] **Step 5: Add a goal-enabled preview**

In `ProfileScreen.kt`, add this preview after the existing `ProfileGuestPreview`:

```kotlin
@Preview(name = "Profile · daily goal", showBackground = true)
@Composable
private fun ProfileDailyGoalPreview() {
    AzriTheme {
        ProfileContent(
            state = ProfileUiState(
                email = "grace@example.com", isAnonymous = false,
                preset = FsrsPreset.Optimal, themeMode = ThemeMode.System,
                dailyGoalEnabled = true, dailyGoalTotal = 30,
            ),
            onOpenFsrsSettings = {}, onThemeChange = {}, onSignOut = {}, onDeleteAccount = {},
        )
    }
}
```

- [ ] **Step 6: Add an instrumented-test assertion**

In `app/src/androidTest/java/nart/simpleanki/feature/profile/ProfileContentTest.kt`, add this test method to the class:

```kotlin
    @Test
    fun dailyGoalRow_opensEditor() {
        var opened = false
        composeRule.setContent {
            ProfileContent(
                state = state(),
                onOpenFsrsSettings = {}, onThemeChange = {}, onSignOut = {}, onDeleteAccount = {},
                onOpenDailyGoal = { opened = true },
            )
        }
        composeRule.onNodeWithText("Daily goal").assertIsDisplayed().performClick()
        assertTrue(opened)
    }
```

(The existing `state()` helper builds a `ProfileUiState` with the defaults `dailyGoalEnabled = false`, `dailyGoalTotal = 0`, so the row renders "Off" — fine; the test only checks the headline + click.)

- [ ] **Step 7: Verify it compiles (main + instrumented test sources)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL. (Emulator unavailable, so the instrumented test is compile-verified only, not run.)

- [ ] **Step 8: Run the full app unit test suite (no regressions)**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/nart/simpleanki/feature/profile/ProfileScreen.kt app/src/androidTest/java/nart/simpleanki/feature/profile/ProfileContentTest.kt
git commit -m "Add Daily goal row to Profile settings"
```

---

## Final verification

- [ ] **Build the debug APK end-to-end**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Manual smoke (when an emulator is available)**

- Open Profile → Settings: a "Daily goal" row appears between "Spaced repetition" and "Notifications", showing "N cards/day" (or "Off").
- Tap it → the daily-goal editor bottom sheet opens (the same one as the Study Queue); toggling/stepping persists and the row's supporting text updates after dismiss.
- The Study Queue's own goal entry point still works unchanged.
