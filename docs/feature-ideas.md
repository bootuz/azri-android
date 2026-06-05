# Azri — Feature Ideas Backlog

A living list of feature ideas for the Azri flashcard app, grounded in a 2024-2025 competitive
analysis (Anki/AnkiDroid, RemNote, Mochi, SuperMemo, Quizlet, Brainscape, Knowt, StudySmarter/Vaia,
Cram, Memrise, Duolingo, Busuu, Drops). Revisit and reprioritize over time.

**Last updated:** 2026-06-05

## What Azri already has (baseline)
FSRS-6 with custom parameter presets · decks + folders · cards with image/audio/reverse variants ·
cloud sync (Firestore) · study streaks · daily goals · CSV + APKG import · daily reminder
notifications · cram/review browse mode · real-time study queue · paywall + Play Billing.

**Intentionally deferred earlier:** AI card generation · text-to-speech · deck sharing. *(Note: the
research shows the market has shifted — AI generation and audio are now near table-stakes; see Tier
3 / "market has moved" below.)*

**Competitive edge to communicate:** FSRS-6 puts Azri ahead of most competitors, which are still on
SM-2 or opt-in FSRS-4.5/5.

---

## Tier 1 — High ROI, builds on systems already shipped
Cheap because the foundations (streak system, review logs, WorkManager reminders, queue builder)
already exist.

- **Streak freeze + repair** — auto-consume a freeze on a missed day; free earn-back within hours,
  paid repair after. *Duolingo: ~48% longer streaks with freezes.* Extends the `StreakProvider` /
  review-log system.
- **Streak-saver notification** — a *separate* evening "your streak is at risk" nudge, distinct from
  the daily reminder. *Case studies: ~21% retention lift, ~40% churn reduction.* Extends
  `WorkManagerReminderScheduler`.
- **Streak milestones** — pick a goal (7/30/100 days); reward + re-goal at each. The 7-day mark is
  where churn drops sharply. Pure derivation from review-log days.
- **Home-screen widget (Android Glance) + Wear OS tile** — due-count / streak / quick-flip. Near
  table-stakes; a daily passive touchpoint. Reads the existing `StudyQueueBuilder`.
- **Day-1 achievement** — something completable in the first session (early win → ~33% vs ~20%
  retention delta in Duolingo data).

## Tier 2 — Close the classic "Anki gap" (card types & study)

- **Cloze deletion** — the single most-cited gap; every serious SRS app has it. Highest-impact
  card-type add.
- **Quiz / Test mode** — auto-generate multiple-choice / true-false / written tests **from existing
  cards** (no AI needed). Quizlet's most-used modes.
- **Image occlusion** — tap-to-hide regions on an image; big for visual/medical learners. Builds on
  existing image-card support.
- **Type-in-the-answer** — typed response auto-compared to the back; valued for languages.
- **Filtered / custom study** — query-based temporary sessions ("failed cards, last 7 days, tag X")
  + **leech detection** (auto-suspend after N lapses). Leverages the queue builder + review logs.
- **Stats / heatmap** — calendar activity heatmap + retention/forecast graphs. Review logs already
  persisted, so the data exists.
- **LaTeX / MathJax** — for STEM users.

## Tier 3 — Differentiators (bigger bets)

- **Games / game modes** — see the dedicated deep-dive: `docs/feature-ideas-games.md` *(in
  progress)*. Quizlet Match/Gravity are among their most-used features; a light game layer boosts
  engagement and is a strong fit for vocabulary/definition content.
- **Voice review mode** — speak the answer, transcribe, mark correct. Emerging; few SRS apps do it;
  complements FSRS.
- **Wear OS / Apple Watch** — due-count complication + quick flip. No major SRS app ships this
  natively — genuinely differentiating.

## The three previously cut — market has moved

- **AI card generation** (photo / PDF / YouTube → cards) — went from niche to near table-stakes in
  2024-25; the #1 acquisition feature for new apps (Quizlet Magic Notes, Knowt, Vaia). Even a single
  source (photo of notes → cards) would close the largest perceived gap.
- **TTS / audio** — Vaia's AI study-podcasts and Cram's free TTS are praised; audio-first study for
  commuters is underserved.
- **Deck sharing / marketplace** — even read-only browsing of community decks drives discovery
  (Quizlet's 800M-set library is their moat).

---

## Recommendation snapshot
- **Max impact / least effort:** the Tier 1 engagement bundle (streak freeze + streak-saver
  notification + milestones).
- **Most visible to users:** Cloze deletion or Quiz/Test mode.
- **Currently exploring:** Games (see games deep-dive doc).

## Status legend
`idea` (unscoped) · `brainstorming` · `spec'd` · `planned` · `in progress` · `shipped` · `dropped`

All items above are `idea` unless noted.
