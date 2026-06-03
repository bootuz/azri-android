# Premium & Paywall — Design

**Date:** 2026-06-03
**Status:** Approved (design); pending implementation plan
**First premium feature:** Cloud Sync

## Goal

Introduce a Premium tier for Azri Android. The first (and currently only) premium
feature is **cloud sync**. Free users work fully offline on a single device; Premium
unlocks Google sign-in + continuous two-way cloud sync. A vibrant "Dark Luxe" paywall
presents the plans and drives purchases via Google Play Billing.

## Product model

- **Subscription product `azri_premium`** with two base plans:
  - `monthly`
  - `annual` (marketed as best value)
- **Non-consumable in-app product `azri_premium_lifetime`** (pay once, own forever).
- **Entitlement** = an active `azri_premium` subscription **OR** ownership of
  `azri_premium_lifetime`.
- Prices and labels are fetched live from Play (localized). Placeholder reference prices:
  monthly $2.99, annual $19.99 (~$1.66/mo), lifetime $49.99. Real values are configured
  in Play Console and are not hard-coded.
- **No trials / intro offers** in this iteration.

## Free vs Premium

| | Free | Premium |
|---|---|---|
| Local study (Room DB) | ✅ | ✅ |
| Works offline | ✅ | ✅ |
| Google sign-in | available, but not required | required for sync |
| Cloud backup & two-way sync | ❌ | ✅ |

Purchases are tied to the Google **Play** account, independent of Firebase auth. A guest
can purchase Premium, but sync additionally requires Google **sign-in** (for a stable
Firebase uid to store data under). The paywall success state therefore nudges the user to
sign in with Google if they aren't already.

## Architecture

Provider-agnostic and testable. The app depends only on an interface; the concrete Play
Billing implementation is thin and swapped for a fake in tests.

```
core/billing/
  Entitlement.kt            // data class: isPremium: Boolean, tier: PremiumTier
                            //   enum PremiumTier { None, Monthly, Annual, Lifetime }
  EntitlementRepository.kt  // interface — single source of truth
                            //   val entitlement: Flow<Entitlement>
                            //   suspend fun refresh()
                            //   suspend fun purchase(activity, plan: PlanOption)
                            //   suspend fun restore()
  PlayBillingRepository.kt  // real impl wrapping Play Billing Library (billing-ktx):
                            //   connection, queryProductDetails, launchBillingFlow,
                            //   PurchasesUpdatedListener, acknowledgePurchase
  BillingProducts.kt        // product-id constants + PURE mapping ProductDetails -> PlanOption
  Entitlements.kt           // PURE gating fn: shouldSync(isPremium, isSignedInWithGoogle): Boolean
```

### Entitlement source of truth & offline

`PlayBillingRepository` derives entitlement from Play's current purchases and exposes it as
`Flow<Entitlement>`. The latest value is **cached in DataStore** so:

- Premium continues to work **offline** (no Play connection needed every launch).
- Gating **fails closed**: if entitlement is unknown/unresolvable, treat the user as free.

## Gating the existing sync

Cloud sync already exists and runs from exactly two trigger points:

1. `ui/AzriRoot.kt` — `syncViewModel.sync(uid)` on sign-in.
2. `core/data/sync/SyncWorker.kt` — periodic background sync.

Both will consult the pure function `Entitlements.shouldSync(isPremium, signedInWithGoogle)`
before calling `SyncManager.sync()`. If it returns false, sync is skipped and the local
Room database is untouched. No changes to `SyncManager` itself.

## Paywall UI — `feature/paywall/`

- `PaywallScreen` + `PaywallViewModel`; stateless `PaywallContent` for Compose tests.
- **Dark Luxe** visual style: deep navy background (`#0E1020`), neon violet→pink accent
  gradient (`#7C5CFF → #FF5BA6`), crown mark, glowing plan cards. **Always dark**,
  independent of the app's light/dark theme (immersive, full-screen).
- `PaywallUiState { plans: List<PlanOption>, selectedPlan, loading, purchaseResult, error }`.
  Plans come from Play `ProductDetails`. Annual is pre-selected and shows a **BEST VALUE**
  badge.
- CTA launches the Play purchase flow for the selected plan. Footer: **Restore purchase**,
  Terms, Privacy. Success state confirms unlock and (if needed) prompts Google sign-in.
- New navigation route `paywall` in `AzriNavHost`.

## Entry points

1. **Profile › Cloud sync row** — free: "Off · tap to back up" → opens paywall;
   premium: "Synced".
2. **Profile › Azri Premium row** — distinct row with a crown/sparkle accent; opens the
   paywall, or shows "Premium · active" when owned.
3. **Today screen nudge** — a tasteful, dismissible banner ("Back up your cards · Go
   Premium") shown to free users only; dismissal persisted in DataStore.
4. **Restore purchases** — available both in Profile and on the paywall (required by Play
   policy for the non-consumable lifetime product).

## Error handling

- Play connection / product-query failure → paywall shows a retry affordance; gating
  defaults to free.
- Purchases are **acknowledged immediately** on receipt (Play requires acknowledgement
  within 3 days or the purchase is refunded).
- Cancelled and pending purchases are handled without crashing; pending resolves when Play
  later confirms.

## Testing

Follows the existing test-seam pattern (pure functions + interfaces + fakes):

- **Pure functions:** `Entitlements.shouldSync(...)`; `ProductDetails → PlanOption`
  mapping; best-value / per-month derivation.
- **`PaywallViewModel`:** with `FakeEntitlementRepository` — plan load, selection,
  purchase result, restore.
- **`PaywallContent`:** Compose tests — renders plans, plan selection, CTA enabled,
  restore present.
- **Sync gating:** tests asserting `shouldSync` is honored at the trigger points.
- The real `PlayBillingRepository` is intentionally thin and verified manually on a signed
  build against Play Console.

## Scope / YAGNI (explicitly out of scope)

- Server-side receipt validation (rely on Play Billing + acknowledgement; noted as future
  hardening).
- Promo codes, trials/intro offers, proration UI beyond Play's native handling.
- Any second premium feature — cloud sync is the only gated feature for now.

## Open-source / CI considerations

- Product-id constants are public identifiers, not secrets. **No new secrets** are
  introduced — Play Billing authenticates via the app's signing key + package name.
- The billing layer degrades gracefully with no Play connection (CI and unmodified local
  builds simply observe "not premium"), so `testDebugUnitTest` and `assembleDebug`
  continue to pass without any Play Console setup.

## Future work (not now)

- Server-side / Play Developer API receipt validation.
- Additional premium features beyond cloud sync.
- Win-back / promotional offers.
