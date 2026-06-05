# Azri — Games Deep Dive

Research notes on game formats for flashcard apps: what games exist, how they map to content
types/subjects, which are single-player + mobile-friendly, and how they interact with spaced
repetition. Companion to `docs/feature-ideas.md`.

**Last updated:** 2026-06-05
**Sources:** Quizlet, Cram, Knowt, Memrise, Drops, Clozemaster, Vocabulary.com, Brainscape, Reverso
(flashcard-app game modes); Blooket, Gimkit, Kahoot, Quizizz, Wordwall, Baamboozle, Quizalize
(game-based learning platforms); Duolingo + classic game-design patterns.

---

## The one insight that matters most

**There are two fundamentally different kinds of "games":**

1. **Engagement wrappers** (Quizlet Match/Blast, Cram Stellar Speller, Blooket, Gimkit, Kahoot) —
   fun, drive motivation/recognition, but **do NOT feed a spaced-repetition scheduler**. They're
   pure play; no per-card memory signal is recorded.
2. **SRS-coupled games** (Memrise Speed Review, Brainscape CBR, Clozemaster) — the game **session is
   the due queue**, and answering advances/resets the card's interval. Engagement *and* retrieval
   practice serve the same scheduler.

**For Azri (an FSRS-6 app), the highest-leverage design is #2:** a game whose content is pulled from
the FSRS due queue and whose results grade the card back into FSRS — not a free-play game against
arbitrary cards. Offer engagement wrappers (#1) as optional "warm-up / for fun" modes on top.

A second proven pattern: **Wordwall's architecture** — one term/definition set, one-click switch
between 30+ game templates, no re-authoring. That's the cheapest engineering pattern: build a few
**content-agnostic** games that run off any deck's existing front/back.

---

## Azri's card shape (what we can build from today)
Azri cards already have: **front + back** (term↔definition equivalent), optional **image**, optional
**audio**, and **reverse** variants. That means every "content-agnostic" game below works *now* with
auto-generated distractors from the same deck — no new card authoring. Games needing cloze
sentences, label coordinates, or category tags would require new card data (call out separately).

---

## Catalog — single-player, mobile-friendly games (ranked by fit)

| Game | Mechanic | Card data | SRS-couplable? | Notes |
|---|---|---|---|---|
| **Speed Review** (Memrise model) | Timed MC/tap, 3 "lives", combo bonus; **session = due queue** | front + back (distractors auto) | **Yes — ideal** | The flagship recommendation: wrap FSRS review in a lives+combo loop, grade back into FSRS |
| **Multiple-choice survival / "floating answers"** (Quizlet Blast) | Tap the correct floating/falling answer to a prompt before it leaves; points, time pressure | front + back (distractors auto) | Partial (track accuracy) | Mobile-native tap; great for vocab/short defs; degrades on long cards |
| **Whack-a-mole** | Prompt shown; definition "moles" pop up; tap the correct one fast | front + back (distractors auto) | Partial | Excellent touch fit; very short sessions |
| **Memory / Concentration match** (Quizlet Match, Wordwall Matching Pairs) | Flip/tap tiles to pair term↔definition (or image↔word); timed | front + back (image optional) | **No** (recognition only) | Best as a low-pressure warm-up; works beautifully with image cards |
| **Typing speed race** (Anki type-answer, Quizlet Gravity) | See front → type back before timer; auto-compare | front + exact-ish back string | **Yes** (highest recall signal) | Strongest active recall, but needs a keyboard; weaker accessibility |
| **Flashcard duel / battle** (HP-based) | Answer correctly to deal damage / lose HP on miss; vs AI or self | front + back | Partial | Turn-based, offline-friendly, low latency; pure engagement wrapper |
| **Anagram / word scramble** | Reorder letter tiles to form the answer word | back must be a single word/short phrase | Weak (orthography) | Good for language/spelling decks only |
| **Hangman / word reveal** | Guess letters of the answer given the front as clue | back = a word; front = clue | Weak | Single-word vocab only |
| **Image occlusion game** | Hidden regions on a diagram; recall the label | image + label coordinates (**new card data**) | **Yes** | High-efficacy for anatomy/geography/diagrams; needs an occlusion authoring tool |
| **Cloze / fill-in-the-blank** | Sentence with a gap; type/pick the missing word | sentence-with-gap field (**new card data**) | **Yes** | Strongest context recall; needs a cloze card type (also a Tier-2 backlog item) |

**Multiplayer-only primitives (NOT solo-viable — skip for a focused mobile SRS app):** steal/sabotage,
territory/color-claiming, PvP projectile combat, co-op collective goals, social deduction
(Gimkit Trust No One), real-time team races (Quizlet Live, Kahoot). These need live opponents.

---

## Game format × content type (which game for which deck)

| Content type | Best-fit games |
|---|---|
| **Vocabulary / language** | Match, Speed Review, typing race, anagram, hangman, audio-dictation (needs audio), cloze |
| **Definitions / concepts / facts** | MC survival, whack-a-mole, true/false survival, Jeopardy/trivia board, group-sort (needs category field) |
| **Math / numeric** | Speed arithmetic (timed MC/type), equation matching (memory), ordering |
| **Images / visual / diagrams** | Image occlusion, label-the-diagram, memory match with images |
| **Sequences / ordering / timelines** | Timeline ordering, sort-into-order (needs an ordered/position field) |

**Content-agnostic (work from any front/back deck, cheapest to ship):** MC survival, Memory match,
Speed Review, Whack-a-mole, Typing race, Flashcard duel. (Hangman/Anagram are agnostic *only* when
the back is a single word.)

---

## The meta-layer: game-design primitives worth borrowing (solo-viable)

These wrap *around* the question loop to drive retention; all work single-player:

1. **Streak / combo multiplier** — consecutive correct answers multiply reward non-linearly (punishes
   guessing). Simplest, highest-impact.
2. **Answer-to-earn currency → spend-to-upgrade** — earn coins per correct card → buy deck themes,
   card cosmetics, hint unlocks. Session economy (Gimkit Tycoon model).
3. **Collect-and-convert loop** — answer → earn "bait" → fish for rare cosmetics → sell/spend
   (Gimkit Fishtopia). Turns a session into a satisfying loop with a tangible end-product.
4. **Randomness / luck reward** — finishing a deck opens a mystery chest (random cosmetic/bonus).
   Breaks monotony, lowers pressure (Blooket Gold Quest).
5. **Idle / passive income** — study fills "production slots" that earn XP between sessions
   (Blooket Factory) — a gentle return hook.
6. **Earned power-ups (no real money)** — "Hint", "Skip", "50/50 remove two options"; earned via
   streak/currency, used strategically (Quizizz power-ups).
7. **Tower-defense / siege framing** — a wave of *hard/leech* cards "attacks"; survive by answering.
   A thematic skin for reviewing difficult material.
8. **Arcade dexterity layer** — present the same MC question inside a whack-a-mole/airplane frame;
   adds motor engagement without changing the learning content.

---

## Recommendation for Azri

**Phase 1 — the flagship (SRS-coupled):** a **"Speed Review" game mode** that:
- pulls cards from the **FSRS due queue** (not free-play),
- presents each as multiple-choice/tap (distractors auto-generated from the deck) with a per-item
  timer, **3 lives**, and a **combo multiplier**,
- maps the result back to an **FSRS grade** (fast+correct → Good/Easy, slow → Hard, miss → Again),
- shows a score + best-combo summary and feeds the existing **streak/daily-goal** systems.

This is mobile-native, works from existing front/back cards, needs no new card data, and — crucially
— it's *real studying*, not a side game. It's the Memrise/Brainscape lesson: couple the fun to the
scheduler.

**Phase 2 — content-agnostic "for fun" modes (engagement wrappers):** **Memory match** (great with
image cards) and **MC survival / whack-a-mole** as optional, non-SRS warm-ups launched from a deck.
Reuse the auto-distractor + timer engine from Phase 1.

**Phase 3 — meta-layer:** add a **combo multiplier + earned hint/50-50 power-ups**, then optionally a
light **currency → cosmetics** economy. Keep it cosmetic (no pay-to-win, no real-money power-ups).

**Later / needs new card types:** image-occlusion game (pairs with the Tier-2 image-occlusion card
type), cloze fill-in-the-blank (pairs with the Tier-2 cloze card type), group-sort (needs a category
field), timeline ordering (needs a position field).

### Design guardrails
- **Couple to FSRS wherever possible** — engagement-only games don't improve retention; the research
  is consistent on this.
- **Short sessions** (under ~3 min) and **tap-first** interactions win on mobile; reserve typing for
  an opt-in "hard mode".
- **Auto-distractors** from the same deck make every MC game zero-authoring.
- **Accessibility**: keep large tap targets, don't encode correctness by color alone, offer a
  no-timer option.
