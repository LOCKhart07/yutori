# SpendWise — pending work

Reconciled snapshot. Delete rows as they ship.

## Bugs / silent gaps

- **Budget alerts never fire** — `AlertStateMachine` and `AndroidAlertNotifier` are implemented and unit-tested, but no caller wires them into the ingestion pipeline, so threshold crossings never produce notifications. Spec (business-logic §7) treats this as v1. Biggest silent divergence in the app today.
- **Pending-FX banner missing on dashboard** — `DashboardUiState.Ready.pendingForexCount` is computed but no banner composable renders it (ui-spec §5.2 item 5). User has no in-app indication when forex conversions are queued.
- **Late-arriving past-month silent alert reconciliation** (business-logic §7.6) — when a tx for an older month arrives, alerts shouldn't re-fire silently.

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
- **Ignore a transaction** — one-tap action on TransactionDetail to flag a tx as "don't count" (flip its `budget_effect` to DROP or add an `is_ignored` column). Drops it from the month's net spend and category totals but keeps it visible (strikethrough, like existing DROP rows). Covers the case where a parsed tx is noise the user wants out of their budget without having to delete or reclassify. Could share the TransactionDetail action row with per-tx notes above, or stay orthogonal — same screen, different verbs.
- **Budget suggestions from history** — seed BudgetSetup's limit field from the user's median net-spend over the prior 3 months (skip months before the app had data). Offer as a tap-to-fill, not auto-applied.
- **Budget carry-over genesis month** — today, carry-over only walks months where a Budget row exists, which is an implicit genesis (prior months without budgets contribute 0). Once users can record historical budgets retroactively (for history/suggestions), we'll want a `carryStartsFrom` setting so those historical records don't bleed into the current month's effective budget. Options: a per-Budget `isHistoricalOnly` flag, or a single app-level genesis-month setting.
- **Swipe between months** on the dashboard. Chevrons work, but a horizontal swipe gesture on the hero area feels more native and is discoverable without reading the chrome.
- **Surfacing suggested accounts** — the SUGGESTED section lives inside Settings → My accounts. Consider: a dashboard one-liner (e.g. "1 new account detected") that deep-links into it, or a pull-down on the Accounts list to also review DISMISSED history.
- **Manual recipient-rule add/edit form** — settings-spec §3.5 requires an add/edit screen with pattern-kind dropdown and a "Test" match-preview tool. Today `RecipientRulesScreen` only toggles or deletes existing rules; new rules can only be added implicitly via the AccountEdit UPI-handles field. The bigger AI-assisted creation flow (above) is the long-term direction, but a basic manual form is the closer minimal fix.
- **"Offer" reclassify after saving an account UPI handle** — currently we silently call `ReclassifyOnRuleAdd` and show a toast count. Settings-spec §2.7 calls for an explicit confirm prompt before bulk-reclassifying past txs as self-transfers.
- **Reparse pipeline** — business-logic §8 describes reclassifying historical SMSes after rules change. No code exists.
- **Carry-over per-prior-month breakdown in BudgetSetup** — ui-spec §9.2 item 4 says BudgetSetup should show how each prior month contributed to the current carry. Today the screen just shows a final number.

## Spec-linked screens not yet built

- **About** — version, build commit, "Check for updates" button once the autoupdater lands, licenses, link to repo.
- Alert thresholds
- CSV export
- Rerun parser
- Purge non-financial
- Review unmatched

## Visually unverified (code exists, no eyes on)

- Dashboard state variants 1b (approaching), 1c (early-month), 1d (end-month-hot), 1e (end-month-surplus) — need seeded data that triggers each.
- Forex tx-detail variant (mockup frame 6).

## Tech debt

- **Dashboard flashes "₹0" when re-entered** — `stateIn(WhileSubscribed(5s))` means the upstream Flow restarts with `initialValue = Loading` after the Dashboard leaves composition for >5 s. Switching screens and coming back briefly shows 0 before the real net-spend renders. Options: widen the SharingStarted timeout, cache the last emission as the initial value, or prime `uiState` with `runBlocking` from a cold DB read (only safe off-main).
- **DB migration failure full-screen handler** — error-states §5.1: when a Room migration throws, surface a recoverable error screen with export option, not a hard crash. Today: `Room.databaseBuilder` has no `addCallback`/error path; a bad migration would throw on first DB access.
- **Notification-permission banner (Android 13+)** — error-states §2.6 requires a dismissible dashboard banner when `POST_NOTIFICATIONS` is denied so threshold alerts can't silently disappear. Not implemented.
- **Historical-import worker checkpointing + foreground notification** — ingestion §7.2/§7.3: the worker should persist progress so a kill mid-import resumes from the last-imported `androidSmsId`, and should run as a foreground service with a sticky notification. Today it's a normal CoroutineWorker with no resume.
- **`DashboardDerived.computeBanner`** untested branches — approaching / surplus / early-month paths.
- **Compose render tests** are thin — only `TransactionListItem`. Dashboard / TransactionDetail state-change paths have no regression net.
- **Profile the app end-to-end** — measure ingest-per-SMS time (parse + classify + merge + persist), dashboard initial render (Flow cold-start to first frame), and large-drill-down scroll (LazyColumn recomposition on 1000+ txns). Look for synchronous DB reads on the main thread and unnecessary recompositions via the Compose layout inspector.

## Conscious deferrals (revisit when they earn their weight)

- **Custom nav stack instead of Navigation-Compose** — ui-spec §2 calls for Navigation-Compose. We ship a hand-rolled `List<Screen>` stack in `MainActivity` because it was simpler and fully covers v1 needs. Swap in Navigation-Compose when we want deep-link URIs, type-safe args, animation primitives, or a back-stack inspector.
- **Historical-import lives on the dashboard as a dialog, not as a Settings entry** — ui-spec puts it under Settings §4. The dashboard dialog was a stopgap; eventually move into the proper Settings entry alongside Alert thresholds / Rerun parser / etc.

## Distribution & updates

- **In-app autoupdater**, Tachiyomi-style: periodic GitHub Releases check, download APK, invoke `PackageInstaller`. Requires `REQUEST_INSTALL_PACKAGES`.
- **Publish the repo + first release.** Workflow + keystore config shipped (`docs/RELEASING.md`). Remaining: create the GitHub repo, `git remote add origin …`, push, optionally add the 4 `SIGNING_*` secrets, tag `v0.1.0`.
