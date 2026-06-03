# Azri Flashcards — Android

A spaced-repetition flashcard app built with Jetpack Compose and the FSRS-6 scheduling
algorithm. Decks, folders, cloud sync, daily study queue, reverse cards, and a daily-goal
tracker — backed by Firebase.

> **Status:** active development. This is the Android port of the iOS app.

## Tech stack

- **UI:** Jetpack Compose + Material 3
- **Language:** Kotlin `2.3.0`
- **Build:** Android Gradle Plugin `9.2.1`, `minSdk 24`, `targetSdk 36`
- **DI:** Koin
- **Local storage:** Room + DataStore (Preferences)
- **Backend:** Firebase Auth (Google Sign-In), Cloud Firestore, Cloud Storage
- **Scheduling:** FSRS-6
- **Background work:** WorkManager (daily study/goal reminders)

## Getting started

### 1. Prerequisites
- Android Studio (latest stable) or the Android SDK + JDK 17+
- An Android device/emulator running API 24+

### 2. Firebase setup (required)

This repo does **not** include `google-services.json` — each developer connects their own
Firebase project so the public code is never tied to a specific backend.

1. Create a Firebase project at <https://console.firebase.google.com>.
2. Add an **Android app** with package name **`nart.simpleanki`**.
3. Enable the products the app uses:
   - **Authentication** → Sign-in methods → **Google** and **Anonymous**
   - **Cloud Firestore**
   - **Cloud Storage**
4. For Google Sign-In, add your debug keystore's **SHA-1** to the Android app
   (`./gradlew signingReport` prints it).
5. Download the generated `google-services.json` and place it at:
   ```
   app/google-services.json
   ```
   See [`app/google-services.json.template`](app/google-services.json.template) for the
   expected shape. The Google Sign-In web client ID is read automatically from this file
   (`R.string.default_web_client_id`) — nothing else to configure.

### 3. Build & run

```bash
./gradlew installDebug      # build + install on a connected device/emulator
./gradlew testDebugUnitTest # JVM unit tests
./gradlew connectedDebugAndroidTest  # instrumented (Compose) tests — needs a device
```

## Security notes

- `google-services.json`, `local.properties`, and any keystores (`*.jks`, `*.keystore`,
  `keystore.properties`) are git-ignored and must never be committed.
- A Firebase **client** config is not a private credential, but data is only as safe as your
  **Firestore/Storage Security Rules** — lock them down and consider enabling **App Check**.

## Contributing

Please be respectful and follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

Licensed under the [Apache License 2.0](LICENSE).
