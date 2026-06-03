# Local-First Media — Design

**Date:** 2026-06-03
**Status:** Approved (design); pending implementation plan
**Foundation for:** `.apkg` import (next project)

## Goal

Rework card media (images + audio) to be **local-first**, matching the existing
free=local / premium=sync model. Today media is **cloud-only**: it uploads to Firebase
Storage the moment a card is created, and both display components require a Firebase
`downloadUrl` to show anything. This violates the freemium contract (a free user's media
leaves the device even though their card *data* never syncs) and makes media unavailable
offline or signed-out.

After this rework:

- **Free users:** media is stored on-device and displayed from disk. It never leaves the
  device.
- **Premium users:** media is still stored and displayed locally first; it is additionally
  uploaded to and downloaded from Firebase Storage as part of the existing premium sync, so
  it works fully offline and propagates across devices.

This also lays the foundation for `.apkg` import: once media is local-first, importing an
`.apkg`'s images/audio is just "copy bytes into the local media dir, set the filename" —
no Firebase, no sign-in, works for free users.

## Background: current architecture

- `Card` carries a logical filename (`image` / `audioName`, e.g. `<uuid>.jpg`) and a
  Firebase Storage path (`imagePath` / `audioPath`, `users/{uid}/{images|audio}/<name>`).
  The filename/path scheme is shared with the iOS app.
- **Create:** `CardFormViewModel.onImagePicked(bytes)` → `MediaUploader.uploadImage(bytes)`
  → `MediaRef(name, path)` stored on the card. Eager, always, regardless of premium.
- **Display:** `MediaImage(imagePath)` and `AudioPlayButton(audioPath)` resolve
  `MediaUploader.downloadUrl(path)` and load via Coil / `MediaPlayer`. No local path exists.
- **Sync:** `cards`/`decks`/`folders` sync via a `dirty` flag (push) + last-write-wins
  (`pull`), gated on `Entitlements.shouldSync(isPremium, signedInWithGoogle)`. Media is
  **not** part of this flow — it is implicitly "synced" because the path already points at
  the cloud.

## Storage model & identity (no schema change)

- Media files live in app-internal storage: `context.filesDir/media/<name>`, where `<name>`
  is the existing logical filename already stored in `Card.image` / `Card.audioName`.
- `Card.imagePath` / `Card.audioPath` stay as the **cloud location** and double as sync
  state:
  - `name != null && cloudPath == null` → **local-only, pending upload** (free users stay
    here permanently).
  - `name != null && cloudPath != null` → uploaded; a cloud copy exists.
- **No new Room columns and no DTO changes.** The cross-platform Firestore/Storage contract
  with iOS is untouched — the cloud path scheme (`users/{uid}/{images|audio}/<name>`) is
  preserved; we simply stop *requiring* a cloud round-trip for local display.

## Components

### `LocalMediaStore` (new, `core/data/media/`)
Thin wrapper over `filesDir/media/`:
- `fileFor(name: String): File`
- `exists(name: String): Boolean`
- `suspend save(name: String, bytes: ByteArray): File` (atomic write)
- `delete(name: String)`
- `newName(ext: String): String` → `"<uuid>.<ext>"`

### `MediaManager` (new coordinator — the single policy seam)
Owns the local-first / freemium policy. Sits over `LocalMediaStore` + `MediaUploader`.
- `suspend saveImage(bytes: ByteArray): String` / `saveAudio(bytes): String` — write
  locally, return the new filename. **No network.**
- `suspend resolve(name: String?, cloudPath: String?): File?` — local-first read: return the
  local file if present; else if `cloudPath != null`, download bytes, cache locally, return
  the file; else `null`.
- `suspend ensureUploaded(name: String?, cloudPath: String?): String?` — push side: if a
  local file exists and `cloudPath == null`, upload it under the same `name` and return the
  new cloud path; otherwise return the existing `cloudPath`.
- `suspend prefetch(name: String?, cloudPath: String?)` — pull side: if `cloudPath != null`
  and no local file exists, download + cache.
- `delete(name: String?)`.

### `MediaUploader` (existing cloud seam) — changes
- Add `suspend downloadBytes(path: String): Result<ByteArray>`
  (`storage.reference.child(path).getBytes(MAX).await()`).
- Replace the name-generating `uploadImage`/`uploadAudio` with a **name-stable**
  `suspend upload(name: String, bytes: ByteArray): Result<String>` that returns the cloud
  path and uses the given `name` (so identity is stable across upload/download round-trips).
  Folder (`images`/`audio`) is derived from the file extension.
- Remove `downloadUrl` — nothing needs URLs once display reads cached files.

## Data flow

- **Create** (`CardFormViewModel.onImagePicked` / `onAudioRecorded`):
  `mediaManager.saveImage(bytes)` → set `imageName = name`, `imagePath = null`. Instant,
  offline, works for free users. The `uploadingImage`/`uploadingAudio` states collapse to
  near-instant local writes.
- **Display** (`MediaImage`, `AudioPlayButton`): take `(name, cloudPath)` and call
  `mediaManager.resolve(...)`, feeding the resulting `File` to Coil / `MediaPlayer`. Call
  sites (StudyScreen, card preview, anywhere media renders) pass `card.image` / `card.imagePath`
  (and the audio pair). Existing cloud-only media migrates lazily here on first view as a
  safety net.
- **Push** (`SyncManager.push`, premium-only): for each dirty card, `ensureUploaded` the
  image and audio, fold the returned cloud paths back into the row, push the DTO, then
  `clearDirty` while persisting the new paths (`dirty = false`). Wrap each card in `try` so
  one upload failure doesn't block the batch.
- **Pull / eager prefetch** (`SyncManager.pull`, premium-only): after applying remote card
  rows, `prefetch` every referenced media file not yet local (downloaded in the sync
  coroutine). Delivers the full-offline premium experience.
- **Delete**: soft-delete leaves local files in place (cheap; avoids races with sync).
  Cleanup of orphaned files is a later concern.

## Premium gating

Sync (`AzriRoot` 20s loop + `SyncWorker`) already gates on
`Entitlements.shouldSync(isPremium, signedInWithGoogle)`. Therefore `ensureUploaded` and
`prefetch` only ever run for premium, signed-in users. Free users: media is purely local and
never leaves the device — consistent with the free=local / premium=sync model.

## Error handling

- **Local write fails** (disk full): surface a transient error in the card form; the card is
  not saved with a dangling filename.
- **Upload fails on push**: leave `cloudPath = null`; the card stays `dirty` and is retried
  next sync. Other cards are unaffected.
- **Download fails on display/prefetch**: `resolve` returns `null` → media simply doesn't
  render (no crash); retried on next view / sync.
- **Missing local file + null cloud path**: nothing to show (graceful).

## Testing

- `LocalMediaStore`: temp-dir `save` / `exists` / `delete` / `newName`.
- `MediaManager` (fake in-memory `MediaUploader` + temp-dir store): local-hit `resolve`;
  cloud-fallback `resolve` caches locally; `saveImage` writes a file and returns a name with
  `imagePath` left null; `ensureUploaded` uploads only when `cloudPath == null`; `prefetch`
  caches.
- `SyncManager` (extend the existing fake remote): upload-on-push fills the path + clears
  dirty; prefetch-on-pull caches locally; **no** upload/prefetch occurs when not premium.
- `CardFormViewModel`: picking an image writes locally and sets the name with
  `imagePath == null`, with no upload call.

## Out of scope (explicit)

- Cache eviction / size caps (keep all media).
- Media compression / re-encoding (store bytes as provided).
- Multiple images/audio per card (the model is single image + single audio).
- Deleting cloud files when a card is deleted.
- HTML rendering of card fields.
- The `.apkg` import feature itself (the next project, which builds on this).
