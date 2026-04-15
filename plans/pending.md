# SpendWise — pending work

Reconciled snapshot. Delete rows as they ship.

## UX / layout gaps

- **"KOTAK UPI" shows 2 similar sources** — user-flagged on device, needs a screenshot to diagnose. Probably two merchant-key variants (spacing, casing) that should normalize to one.
- **Back from Subscriptions drill-down kicks the user out of the app** (reported on device, 2026-04-16). Suspected Compose group-stack imbalance or missing BackHandler branch for the new SUBSCRIPTIONS category. Pull logcat next session.
- **Tapping a transaction inside the Subscriptions drill-down returns to the app's Dashboard** instead of opening TransactionDetail (reported 2026-04-16). User confirmed it's the app's home screen, not the phone launcher — so not a crash, a nav bug. `onTransactionClick` is wired the same way as every other drill, so the Subscriptions-specific trigger is suspicious. Pull logcat + narrow down next session.
- **Subscriptions category total on dashboard ≠ sum of txs in drill-down** (reported 2026-04-16). Happens even after the OTHER-null-coalesce fix (d4458c0), so this is a separate second bug — possibly duplicated rows, stale entities lingering with legacy ENTERTAINMENT category while new ingests go to SUBSCRIPTIONS, or another grouping discrepancy. Needs DB inspection.

## Branding / identity

- **App name** — "SpendWise" is placeholder. Pick a real name (affects AndroidManifest label, string resources, splash, in-app copy).
- **Launcher icon / logo** — currently the default Android icon. Need an adaptive icon (foreground + background layer) at the standard density buckets.

## Features not yet built

- **Dashboard ring/donut chart** — spec mentions; tinted bars shipped. Decide: keep bars or build donut.
- **Onboarding 4-step flow** (welcome, permissions, import prompt, budget prompt). Only permissions exists.
- **CardDrillDown filter chips** — All / Spend / Refunds / Bills / Self-transfers (ui-spec §7).
- **Tx-detail Edit / Mark as payback** (v1.1 per spec).
- **AI-assisted rule creation.** Adding a RecipientRule manually is fiddly — user has to know regex vs literal, pick a classification, etc. Better UX: show a recent UNMATCHED or mis-categorized transaction, let the user describe the intent in plain English ("anything from CRED is a CC bill payment"), have a small LLM turn that into a concrete rule row, preview the matches, then save. Needs a local model or an API key in settings.
- **"Add a rule from this transaction"** — entry point on TransactionDetailScreen: an action that prefills a new rule from the current tx's merchant / pattern and drops the user into the (AI-assisted, above) rule creator. Makes mis-categorizations fixable in-place instead of requiring a trip to Settings → Recipient rules with nothing but the raw pattern in hand.
- **Per-transaction notes** — a free-text note field on TransactionDetail so the user can annotate once they know what an opaque UPI merchant actually was ("dentist", "birthday gift for X"). If the note implies a known category (via keyword or AI), offer a one-tap reclassify — and optionally prompt "apply to all matching past/future txns?" which would surface as a new rule candidate. Needs a schema column + tx mapper + detail UI field.
- **Budget suggestions from history** — seed BudgetSetup's limit field from the user's median net-spend over the prior 3 months (skip months before the app had data). Offer as a tap-to-fill, not auto-applied.
- **Swipe between months** on the dashboard. Chevrons work, but a horizontal swipe gesture on the hero area feels more native and is discoverable without reading the chrome.
- **Surfacing suggested accounts** — the SUGGESTED section lives inside Settings → My accounts. Consider: a dashboard one-liner (e.g. "1 new account detected") that deep-links into it, or a pull-down on the Accounts list to also review DISMISSED history.

## Spec-linked screens not yet built

- Alert thresholds
- CSV export
- Rerun parser
- Purge non-financial
- Review unmatched

## Visually unverified (code exists, no eyes on)

- Dashboard state variants 1b (approaching), 1c (early-month), 1d (end-month-hot), 1e (end-month-surplus) — need seeded data that triggers each.
- Forex tx-detail variant (mockup frame 6).

## Tech debt

- **Carry-over math** uses only current-month transactions (TODO in `DashboardViewModel`). Prior-month REFUNDs that alter surplus aren't reflected.
- **`DashboardDerived.computeBanner`** untested branches — approaching / surplus / early-month paths.
- **Compose render tests** are thin — only `TransactionListItem`. Dashboard / TransactionDetail state-change paths have no regression net.
- **Profile the app end-to-end** — measure ingest-per-SMS time (parse + classify + merge + persist), dashboard initial render (Flow cold-start to first frame), and large-drill-down scroll (LazyColumn recomposition on 1000+ txns). Look for synchronous DB reads on the main thread and unnecessary recompositions via the Compose layout inspector.

## Distribution & updates

- **In-app autoupdater**, Tachiyomi-style: periodic GitHub Releases check, download APK, invoke `PackageInstaller`. Requires `REQUEST_INSTALL_PACKAGES`.
- **Publish the repo + first release.** Workflow + keystore config shipped (`docs/RELEASING.md`). Remaining: create the GitHub repo, `git remote add origin …`, push, optionally add the 4 `SIGNING_*` secrets, tag `v0.1.0`.
