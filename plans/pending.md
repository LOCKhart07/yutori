# SpendWise — pending work

Reconciled snapshot. Delete rows as they ship.

## Bugs / silent gaps

- **Pending-FX banner missing on dashboard** — `DashboardUiState.Ready.pendingForexCount` is computed but no banner composable renders it (ui-spec §5.2 item 5). User has no in-app indication when forex conversions are queued.
- **Late-arriving past-month silent alert reconciliation** (business-logic §7.6) — when a tx for an older month arrives, alerts shouldn't re-fire silently. `dispatchAlertsIfNeeded` already filters on `isCurrentMonth`, so we suppress correctly; the spec wants us to *also* mark the past-month thresholds as fired so they're stamped in `budget_alert_state` for accuracy. Thin gap.

## Branding / identity

- **App name** — "SpendWise" is placeholder. Pick a real name (affects AndroidManifest label, string resources, splash, in-app copy).
  - **Direction settled (2026-04-16):** borrowed word from another language, written in English script, that names a *philosophy* — not a literal finance term. The app's emotional payoff is *earned permission to spend without guilt* (mindful tracking → freedom / breathing room), not austerity. Names should reflect that.
  - **Constraints:** ≤8 chars ideal for launcher label; avoid fintech-trademarked words (Spend, Money, Budget); avoid -ify/-ly/-wise/-hub; must read clearly in English.
  - **Top three contenders:**
    - **Yutori** *(Japanese — "spaciousness, breathing room, slack in the budget")* — most precise match to the app's purpose. 6 letters, no fintech overlap.
    - **Lagom** *(Swedish — "just the right amount, not too much, not too little")* — broadest appeal, names the philosophy of enough without preachiness.
    - **Sukoon** *(Hindi/Urdu — "peace, calm of mind")* — most personal / native to user's context; names the *feeling* the app produces rather than the mechanism.
  - **Other strong runners-up explored:** Tsumi (JP, "to stack/accumulate"), Mottainai (JP, "what a waste"), Santosha (Sanskrit, "contentment"), Khulla (Hindi, "open/free"), Mukti (Sanskrit, "liberation"), Spielraum/Spiel (German, "room to play, slack"), Khwah (Hindi/Urdu, "wish, desire").
  - **Rejected directions:** literal finance translations (Hisab, Khata, Bachat, Conto, Soldi); generic English finance words (Tally, Ledger, Margin, Reckon); invented-sound names with no meaning (Kori, Nuvo, Aro).
- **Launcher icon / logo** — currently the default Android icon. Need an adaptive icon (foreground + background layer) at the standard density buckets.
  - **Logo direction deferred until name is locked** (concept depends on which name lands). Sketches explored: tally-mark glyph, lowercase letterform monogram, horizon/balance-line with amber dot, ember dot, gnomon/sundial wedge, sumi ink-stroke, stacked-bar monogram. All amber-on-near-black (#F5B547 on #0E0E0E), single-glyph, must read at 48dp inside the 66dp safe zone of a 108dp adaptive icon.

## Behavioral awareness (remote-control brief, 2026-04-16)

Framing: the real problem isn't budgeting accuracy, it's *awareness at the moment of spending*. SpendWise is SMS-reactive so we can't gate a spend pre-confirm — the closest we get is a push notification within seconds of the SMS. Features below lean into that reality.

- **Post-spend "impact" notification** — when a parsed debit is ≥ some % of the monthly budget (start at 10%), fire a push: "₹2,400 at BLINKIT — 12% of April budget. ₹18,300 left." Opens the tx detail. Distinct from the existing threshold-% alerts, which fire on cumulative totals; this fires per-transaction above a size threshold. Configurable threshold; off by default to avoid spam.
- **Traffic-light dashboard state** — map the existing budget-used % to explicit green/yellow/red color treatments on the hero (not just a number). Thresholds ideally align with alert thresholds (default 60% / 80% / 100%). Already halfway there via `DashboardDerived.computeBanner`; lift the color up to the whole hero card, not just the banner.
- **Frequency insight on dashboard** — alongside "₹X spent", show "N transactions under ₹300 this month" or "42 small txns · median ₹180". Small-debit count hits harder than a lump total. Derive from existing tx rows; no schema change.
- **Annual-cost smoothing buckets** — let the user declare irregular yearly expenses ("Travel ₹60k/yr", "Insurance ₹24k/yr"); app divides by 12 and subtracts from the month's effective budget (or shows as a separate "reserved" line). New entity `annual_allocation(name, amount_paise, start_month)`; BudgetCalculator subtracts the monthly slice from the headline limit. Different from carry-over: this is forward-smoothing of known lumpy spend, not backward reconciliation.
- **Goal-linked savings framing** — user defines a goal (amount + target date, e.g. "House down-payment ₹X by 2028-06"); dashboard shows surplus months as "₹10k saved → 2 days closer". Needs a `goals` entity + a translator from surplus paise to goal-progress units. Motivation layer, not accounting — keep it optional and off by default.
- **Hard-stop / lock-screen when over budget** — when net spend crosses 100%, dashboard swaps to a full-screen red "STOP — over budget by ₹X" state instead of a soft banner. Dismissible for the session. Opt-in in settings; the whole point is that it's aggressive.
- **Bucket simplification mode** — optional UI toggle to collapse all categories into three super-buckets (Daily / Lifestyle / Fixed) for users who don't want to see the fine-grained classifier output. Classifier keeps emitting today's categories underneath; this is a presentation-layer rollup in the dashboard + drilldowns. Mapping table from current `Classification` enum → {Daily, Lifestyle, Fixed}.

### Pace concept — extensions beyond the hero

The traffic-light hero (mocked in `mockups/v3-behavioral.html` §1) buckets by `actual_pct − elapsed_pct`. Same idea unlocks several follow-on places — these were discussed alongside the hero but not mocked, so they ship later:

- **Daily-burn stat pill pace tint.** Today the pill shows raw `₹X/day`. Add `· target ₹Y` next to it and tint the value warn/over when burn ≥ 1.3× expected. Reuses the hero pace bucket. Tiny lift on top of the hero work.
- **Sharper notification copy.** Both the post-spend impact notif and the cumulative threshold alerts currently say `Y% of budget`. Both should add `· +Zpp over pace` so the second line carries pace context, not just raw %.
- **Per-category pacing on dashboard rows.** Each category row gets a small pace tag: `Groceries ₹6,200 · 80% of usual by now`. Needs a per-category historical baseline (median over past 3 months) — synergistic with budget-from-history.
- **Category drill-down hero pace bucket.** Reuse the same rule scoped to one category.
- **BudgetSetup pace anchor.** When changing the limit, show "at last month's pace you'd land around ₹X". Anchors to reality.
- **Card drill-down hero pace bucket.** Same as category drill, scoped to a card. Free once the helper is reusable; less intuitive since users rarely budget per card.

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
- **Bank / CC statement import (PDF + CSV)** — spec §10.1 future scope. Lets the user backfill complete history for months before the app was installed, or recover txs whose SMS was missed/deleted. Architectural prep already in spec: `sms_log.source` + `transactions.source` should carry `STATEMENT_PDF` / `STATEMENT_CSV` / `MANUAL` values alongside SMS_*. Today the schema doesn't even have `source` on the entities (drift from spec) — would need to add that first, then a parser per issuer's statement format.
- **Budgets roll forward by default.** Setting April = ₹45k should also be the budget for May, June, … until explicitly changed. Today each month needs its own Budget row or it shows "No budget set". Two implementation options: (a) on month rollover, auto-create a Budget row by copying the last-set one; (b) treat the latest Budget row as a "template" applied to any subsequent month with no row of its own. (b) is simpler — change `BudgetCalculator` lookups to fall back to the most recent prior Budget when none exists for the requested month.
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
- **Rebuild database** — destructive: wipe `transactions`, `transaction_sms_sources`, `sms_log`, `budget_alert_state` (keep `accounts`, `recipient_rules`, `budgets`) and re-run the historical-import worker so every SMS goes back through the current parser + classifier. Useful after parser/classifier changes or to recover from a bad state. Confirm twice; show a progress notification while it runs.

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
- **Custom splash screen** — not building one. Android 12+ auto-generates a system splash from the launcher icon + window background via the `SplashScreen` API; once the adaptive icon lands it will look right for free. Revisit only if we ever need branded animation or have to mask a slow cold-start (neither applies — DB init is already async).
- **Historical-import lives on the dashboard as a dialog, not as a Settings entry** — ui-spec puts it under Settings §4. The dashboard dialog was a stopgap; eventually move into the proper Settings entry alongside Alert thresholds / Rerun parser / etc.

## External suggestions (unvetted — not decisions)

External agent fed the capabilities digest (2026-04-16). Captured verbatim-in-spirit for consideration; none of this is adopted. Read critically — much of it overlaps with the Behavioral-awareness brief above.

- **Core framing claim:** the app is a tracker, not a behavior-changing system. The moment-of-spending feedback loop is the highest-leverage gap, not more ingestion correctness. Product arc should be "recording money → interrupting bad decisions." Worth weighing against the current spec's more neutral "awareness without guilt" framing before adopting wholesale — aggressive-interruption framing may cut against the Yutori/Lagom/Sukoon direction.
- **Proposed "true MVP" cut:** (1) real-time spend meter with color state on the hero, (2) micro-spend insight ("N small txns this month"), (3) mid-month overshoot prediction, (4) opt-in hard-stop over-budget UI. Items 1, 2, 4 already in Behavioral-awareness brief; **mid-month overshoot projection is the net-new idea** — e.g. "70% spent in 9 days → projected overshoot ₹Y." Cheap to derive from existing tx rows + days-elapsed; no schema change.
- **"Real spend this month" unified number** — suggestion to de-emphasize per-account splits on the hero and lead with a single merged CC+UPI figure. Already how `DashboardUiState` computes net spend; the suggestion is presentation — don't let the card drill-down leak into the primary view.
- **Goal-linked emotional framing made explicit** — "₹5k saved = 1 day closer" framing reinforces the existing Goal-linked savings item in the Behavioral-awareness brief. Note: agent assumes a house-down-payment goal as the canonical example; that's agent-side projection, not a user decision — don't hardcode.
- **"Too many planned features, not one killer feature" critique** — agent flags Goals / smoothing / AI rules / PDF import as nice-to-haves diluting focus. Counter-consideration: PDF/CSV statement import is the only way to backfill pre-install history and is explicitly in spec §10.1; smoothing buckets directly serve the "spend without guilt" framing. Worth revisiting priority order, not dropping outright.
- **Retention / habit-loop concern** — "if the user doesn't open daily, the app dies." Possibly true for consumer fintech at scale; less obviously true for a single-user side-loaded APK where the notification layer *is* the daily touch. Notifications doing the behavior-change work means the app itself doesn't need to be opened daily to succeed.

Rejected framings worth naming so they don't sneak back in:
- "Users don't care about classification correctness" — false for this user; double-counting destroys the budget number's trustworthiness, which destroys the whole premise. Correctness is load-bearing, not polish.
- "Delete the deferred features" — most deferred items (statement import, reparse pipeline, per-tx notes, ignore-tx) address real gaps surfaced by the feasibility dataset, not speculative nice-to-haves. Evaluate per-item, not en masse.

## Distribution & updates

- **In-app autoupdater**, Tachiyomi-style: periodic GitHub Releases check, download APK, invoke `PackageInstaller`. Requires `REQUEST_INSTALL_PACKAGES`.
- **Publish the repo + first release.** Workflow + keystore config shipped (`docs/RELEASING.md`). Remaining: create the GitHub repo, `git remote add origin …`, push, optionally add the 4 `SIGNING_*` secrets, tag `v0.1.0`.
