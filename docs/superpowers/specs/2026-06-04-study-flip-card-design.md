# Study Flip Card — Design

**Date:** 2026-06-04
**Status:** Approved (design); pending implementation plan
**Ports:** the iOS `SimpleAnkiSwiftUI/.../Core/Components/FlipCardView.swift` — a true 3D
Y-axis flip used by the spaced-repetition / review screens.

## Goal

Replace the current **additive** reveal on the Android study screen (tap "Show answer" →
the back appears *below* the question with a divider) with a true **3D flipping card** that
matches iOS: the front rotates away and the answer rotates into view. The user flips by
**tapping the card**, guided by a small per-session "tap to flip" hint.

## Background: what we have / what iOS does

- **Android today** (`feature/study/StudyScreen.kt`): a `StudyCard` shows the front; a
  "Show answer" `Button` sets `isRevealed = true`, after which the back is appended below the
  front inside the same static `AzriCard`. No animation.
- **iOS** (`FlipCardView`): a `ZStack` of `cardFront` (opacity 1 when not showing answer) and
  `cardBack` (opacity 1 when showing answer, **pre-rotated 180°** so it is not mirrored). The
  whole stack gets `rotation3DEffect(showingAnswer ? 180 : 0, axis: y, perspective: 0.3)`.
  Front shows an uppercase "QUESTION" label; back shows "ANSWER" + the answer in the accent
  color. Media (image/audio) render on the relevant face.

## Decisions (from brainstorming)

- **Trigger: tap the card.** No "Show answer" button. Tapping the card flips front → back; once
  revealed the card is inert and the rating buttons drive advancement.
- **Hint: a small "tap to flip" label + finger icon** below the card. **Per-session lifespan** —
  it shows until the first flip of the session, then never returns for the rest of that session.
- **Back face = answer only** (iOS parity): an "ANSWER" label + `card.back` in the accent color,
  plus image/audio if present. The question is not shown on the back.
- **Instant next card:** after rating, the next card appears on its **front with no
  reverse-flip** animation.
- **Animation:** 3D Y-axis rotation with a perspective camera (Compose analog of iOS's
  `perspective: 0.3`).

## Components

### `ui/components/FlipCard.kt` (new, reusable, stateless)

Mirrors iOS's reusable `FlipCardView`. Owns only the flip animation and the two faces — no
session/study logic.

```kotlin
@Composable
fun FlipCard(
    card: Card,
    revealed: Boolean,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Mechanics:
- `val rotation by animateFloatAsState(if (revealed) 180f else 0f, animationSpec = tween(450, easing = FastOutSlowInEasing), label = "flip")`.
- Container: `Modifier.graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }`.
- Front face shown while `rotation <= 90f`; otherwise the **back face**, itself wrapped in
  `Modifier.graphicsLayer { rotationY = 180f }` so its text is upright (un-mirrored). This is the
  direct Compose equivalent of iOS's counter-rotated `cardBack`.
- The card surface is the existing `AzriCard` look (radius-20 surface, hairline border, soft
  shadow) so it stays visually consistent with the rest of the app.
- Tap handling: `Modifier.clickable(enabled = !revealed) { onFlip() }` — the front is tappable;
  after reveal the card no longer responds to taps.

Faces:
- **Front:** uppercase "QUESTION" label (muted, `onSurfaceVariant`, letter-spaced) + `card.front`
  (`headlineSmall`, centered) + `MediaImage` (if `card.image != null`) + `AudioPlayButton`
  (if `card.audioName != null`).
- **Back:** uppercase "ANSWER" label + `card.back` (`titleLarge`) in `colorScheme.primary` +
  the same media affordances.

### `feature/study/StudyScreen.kt` (modify)

- `StudyCard` no longer renders the static `AzriCard` + "Show answer" button + additive back.
  Instead it renders `key(card.id) { FlipCard(card, state.isRevealed, onReveal, Modifier.fillMaxWidth().weight(1f)) }`.
  The `key(card.id)` recreates the flip's animation state per card, giving the **instant front**
  on the next card (no reverse-flip).
- Below the card:
  - while `!state.isRevealed && state.showFlipHint`: a centered `Row` with a touch/finger icon
    (`Icons.Outlined.TouchApp`) + "Tap to flip" in muted text.
  - while `!state.isRevealed && !state.showFlipHint`: empty spacer (keeps layout stable).
  - while `state.isRevealed`: the existing four rating buttons (unchanged).
- Previews updated: question (hint visible), answer (rating buttons), and a hint-hidden variant.

### `feature/study/StudyViewModel.kt` + `StudyUiState` (modify)

- Add `val showFlipHint: Boolean = true` to `StudyUiState`.
- `onReveal()` sets both `isRevealed = true` **and** `showFlipHint = false`. Because the
  ViewModel instance lives for the whole session, `showFlipHint` never returns to `true` once any
  card is flipped → exact per-session behavior. No persistence/DataStore needed.
- `load()` and `onRate()` continue to construct state with `showFlipHint` carried forward
  (true until the first flip, false thereafter). Analytics events (`review_session_start`,
  `card_rated`, `review_session_complete`) and the `isRevealed` reset on advance are unchanged.

## Data flow

Session starts → `StudyUiState(showFlipHint = true, isRevealed = false)` → `FlipCard` shows the
front, hint visible → user taps card → `onReveal()` sets `isRevealed = true, showFlipHint = false`
→ `FlipCard`'s `rotation` animates 0 → 180, back face rotates in → user rates → `onRate()` advances
to the next card (`isRevealed = false`), `key(card.id)` change snaps the new card to its front with
no animation; hint stays hidden for the rest of the session.

## Error handling

Pure UI animation with no new failure modes. A card with no image/audio simply omits those
affordances (existing `?.let` guards). The flip is driven entirely by local state; no I/O.

## Testing

- **`StudyViewModelTest`** (JVM, existing fakes):
  - `showFlipHint` starts `true`.
  - after the first `onReveal()`, `showFlipHint` is `false` and `isRevealed` is `true`.
  - after `onRate(...)` advances to the next card, `showFlipHint` stays `false` and `isRevealed`
    resets to `false`.
- **`FlipCard`** — build-verified + `@Preview` (front, back, hint), consistent with the
  codebase's no-Compose-UI-unit-test convention. The rotation/`graphicsLayer` math is not
  JVM-unit-testable.

## Out of scope (this project)

Per-card font size / weight / alignment settings (iOS's gear button); haptic feedback on flip;
swipe-to-rate gestures; auto-flip / auto-advance; persisting the hint across sessions; flipping
on the Queue preview screen. Just the flip animation + per-session tap hint on the study screen.
