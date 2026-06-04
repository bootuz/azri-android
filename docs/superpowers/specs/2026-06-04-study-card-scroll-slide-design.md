# Study Card: Slide-Up Buttons + Scrollable Content — Design

**Date:** 2026-06-04
**Status:** Approved (design); pending implementation plan
**Branch:** `feature/study-card-scroll-slide` (off the current `main`, which has the merged
flip-card screen, PR #8).

## Goal

Two study-screen polish improvements on top of the flip card:

1. **Slide-up rating buttons** — when the user taps the card to reveal the answer, the four
   rating buttons should animate **up from the bottom** (slide + fade) instead of just appearing.
2. **Scrollable card content** — long question/answer text currently overflows and cannot be
   scrolled. The card face should scroll when content exceeds the available height, while staying
   centered when it fits.

## Background: current behavior

- `StudyScreen.kt` → `StudyCard`: a `Column` with `FlipCard` (`weight(1f)`) on top, then a fixed
  bottom slot chosen by a plain `if (!state.isRevealed) { hint/spacer } else { rating Row }`. The
  swap is instant — no transition.
- `FlipCard.kt` → `CardFace`: `Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement =
  Arrangement.Center)`. With no scroll modifier, long text clips at the card bounds.
- iOS `FlipCardView` already solves the scroll case with a `ScrollView` whose content has
  `minHeight: proxy.size.height` (centers short content, scrolls long content).

## Change 1: Slide-up rating buttons (`StudyScreen.kt` → `StudyCard`)

Replace the `if/else` bottom slot with a `Box` (fixed height `60.dp`, matching the rating-button
height so the card above never shifts) stacking two `AnimatedVisibility`s:

```kotlin
Box(Modifier.fillMaxWidth().height(60.dp)) {
    androidx.compose.animation.AnimatedVisibility(
        visible = !state.isRevealed,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        if (state.showFlipHint) { /* existing hint Row */ } else { /* empty */ }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = state.isRevealed,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = fadeOut(),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            /* existing four RatingButton(...) calls, unchanged */
        }
    }
}
```

- `slideInVertically(initialOffsetY = { it })` starts the row offset by its full height
  (below the slot) and animates it to its resting position → "slides up from the bottom".
  Combined with `fadeIn()`.
- On reveal: hint fades out; rating row slides up + fades in.
- On advancing to the next card (`isRevealed → false`): the rating row fades out (no downward
  slide — cleaner on advance) and the hint fades back in.
- The slot stays `60.dp` tall in all states (hint Row, empty spacer, and rating row are all
  `60.dp`), so the `weight(1f)` `FlipCard` above keeps a stable size.
- Default animation durations (Compose's ~250 ms) are used; no custom spec needed. The hint inner
  content (the `if (showFlipHint)` Row vs. empty) is preserved exactly as today.

## Change 2: Scrollable card content (`FlipCard.kt` → `CardFace`)

Wrap the face's `Column` so it scrolls when its content overflows the card, and stays centered
when it fits — the Compose analog of iOS's `minHeight: proxy.size.height`:

```kotlin
@Composable
private fun CardFace(...) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // label, optional image, text, optional audio — unchanged content
        }
    }
}
```

- `heightIn(min = maxHeight)` (where `maxHeight` is the `BoxWithConstraints` viewport height)
  forces the column to be at least viewport-tall, so `verticalArrangement = Arrangement.Center`
  centers short content. When content exceeds the viewport, the column grows beyond it and
  `verticalScroll` lets the user scroll.
- Applies to both faces (front question and back answer), so either a long question or a long
  answer scrolls.
- **Gesture coexistence:** `AzriCard(onClick = onFlip)` (front, when not revealed) handles taps;
  `verticalScroll` consumes vertical drags. A tap flips; a drag scrolls. No extra wiring.
- **Flip transform:** the card's `graphicsLayer { rotationY }` rotates around the Y axis, which
  mirrors horizontally only — vertical scrolling inside the (counter-rotated, upright) back face
  is unaffected.

## Data flow

No state or ViewModel changes. `state.isRevealed` / `state.showFlipHint` already drive the bottom
slot; they now drive `AnimatedVisibility` instead of an `if/else`. `CardFace` gains a local
`rememberScrollState()` — purely view-local.

## Error handling

Pure presentation. Short content centers (no scrollbar, nothing to scroll); long content scrolls.
No new I/O or failure modes. A card with no image/audio omits those as before.

## Testing

- Pure visual/animation + layout change with no new logic → **build-verified + `@Preview`**.
- Add a **long-text `FlipCard` preview** (a `front` with several paragraphs, `revealed = false`)
  to visually confirm the content scrolls rather than clips.
- Existing `StudyViewModelTest` is unaffected (no state changes).
- The instrumented `StudyContentTest` remains valid: the rating buttons are still composed when
  `revealed = true` (`AnimatedVisibility(visible = true)` renders its content), so any
  reveal/rating assertions still hold.

## Out of scope

Changing the flip animation, the tap-to-flip hint, the rating logic, or the session summary;
horizontal scroll, pinch-zoom, scroll indicators/fade edges, or HTML/rich-text rendering of card
fields.
