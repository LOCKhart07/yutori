# SpendWise — pending work

Reconciled snapshot. Delete rows as they ship.

## Priority snapshot

*Last reviewed: 2026-04-16. Assume stale. This repo ships ~15 real commits per working day (check `git log --since="<last-reviewed-date>"` to confirm); a priority list from a previous session is almost certainly out of date. Regenerate from scratch at the start of each planning session rather than patching this snapshot — don't trust ordinal numbers, don't assume "Tier 0 item 1" still means what it did yesterday.* Ordered by value-per-hour, not strict rank. Items within a tier are roughly equivalent. Full context for each bullet lives in its section below.

**Tier 0 — ship-blockers / foundational**
1. Publish repo + first release
2. App name lock-in (Yutori / Lagom / Sukoon)
3. DB migration failure handler

**Tier 1 — easy wins (hours, no schema)**
4. Pending-FX banner on dashboard
5. Mid-month overshoot projection *(net-new from external suggestions)*
6. Frequency insight ("N small txns · median ₹X")
7. Budgets roll forward by default (option b)
8. Budget suggestions from history
9. Daily-burn pill `· target ₹Y` + pace tint
10. Sharper notification copy (`· +Zpp over pace`)
11. Late-arriving past-month alert stamping
12. Rebuild database screen
13. End-to-end profiling round *(user wants soon; prereq for any animation work — profile before animating)*
14. Swipe between months on dashboard *(user wants; pairs with screen-transition animations)*
15. Release-body changelog automation *(git-cliff + APK SHA-256 + tag-annotation header; prereq for a useful in-app autoupdater dialog)*

**Tier 2 — behavior-change core (medium effort)**
16. Hard-stop over-budget state (opt-in)
17. Per-tx notes + Ignore-a-transaction
18. Manual recipient-rule add/edit form
19. "Add rule from this transaction" entry point
20. Reparse pipeline
21. "Offer" reclassify confirm dialog
22. Review unmatched screen

Note: 18–22 form a tight cluster — treat as one mini-milestone.

(Shipped this session: traffic-light hero `e1db251`, post-spend impact notif `ddd51ec`, notification-permission banner `6afefe7`.)

**Tier 3 — structurally important, bigger lifts**
23. Historical-import worker checkpointing + foreground notification
24. Add `source` field to `sms_log` + `transactions` *(prereq for 25)*
25. PDF/CSV statement import
26. Goals entity + translator
27. Annual-cost smoothing buckets
28. Per-category pacing baseline

**Tier 4 — nice-to-have polish**
Onboarding 4-step flow · CardDrillDown filter chips · ring/donut decision · carry-over per-prior-month breakdown · dashboard state variants visual check · forex tx-detail visual check · bucket simplification mode · surfacing suggested accounts · BudgetSetup pace anchor · card drill-down pace · Tx-detail Edit / Mark as payback · animation polish (color transitions, banner fades, progress-bar tween, money counter)

**Tier 5 — deferred / low urgency**
Dashboard ₹0 flash · `computeBanner` untested branches · Compose render tests · Navigation-Compose migration · historical-import → Settings · carry-over genesis month · AI-assisted rule creation · launcher icon (blocked on name) · About screen · Alert thresholds / CSV export / Purge non-financial / Rerun parser screens · in-app autoupdater *(blocked on Tier 0 item 1)*

## Bugs / silent gaps

- **Inconsistent decimal display in money amounts.** `NumberFormat.formatCompact` strips `.00` from whole-rupee amounts and keeps decimals on others, so the dashboard mixes `₹21,386` and `₹9,255.83` and `₹1,155` and `₹288.33` in the same column. Visual scanning misreads the column order because the wider strings look "bigger" regardless of actual value. Fix: pick one — either always show 2 decimals on all money displays, or always strip them. Prefer **always strip** for the dashboard hero/category rows (cleaner) and keep 2 decimals only on TransactionDetail where exact amount matters.
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
- **Swipe between months** on the dashboard — user wants this. Chevrons work, but a horizontal swipe gesture on the hero area feels more native and is discoverable without reading the chrome. Pairs naturally with the screen-transition animation work (see Animations above) — the gesture carries its own slide animation driven by drag offset, so both land in the same change. Implementation: `HorizontalPager` keyed on month, or `Modifier.draggable` on the hero with an `animateFloatAsState` settle.
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
- **Profile the app end-to-end** — user wants a round soon. Measure ingest-per-SMS time (parse + classify + merge + persist), dashboard initial render (Flow cold-start to first frame), and large-drill-down scroll (LazyColumn recomposition on 1000+ txns). Look for synchronous DB reads on the main thread and unnecessary recompositions via the Compose layout inspector. Do this *before* adding animations — motion on top of janky frame pacing reads as broken; profile-then-animate is the right order.

## Animations (all absent — app snaps instantly everywhere)

Zero uses of `animate*`, `AnimatedVisibility`, `Crossfade`, `tween`, `spring` anywhere in the codebase. For the "spend without guilt / breathing room" product framing, small amounts of motion meaningfully change how the app *feels* — but none of these are correctness fixes. Tier 4 polish.

- **Traffic-light hero color transition** — the pace-driven hero (shipped `e1db251`) hard-swaps between green/amber/red when spend crosses a pace bucket. `animateColorAsState` is a two-line change. Closest thing to a bug on this list — a snap-swap looks broken, not intentional.
- **`AnimatedVisibility` on dashboard banners** — FX-pending banner (once rendered, see Bugs), notif-permission denied banner, any future over-budget or traffic-light-driven banners. Fade/slide in instead of pop.
- **Progress bar fill animation** — `animateFloatAsState` on the dashboard spend-progress value so new txns ease in rather than jump-cut to the new width.
- **Money counter on hero** — ₹ figure counts up when a new tx lands, rather than hard-updating. More effort than the others (needs a key-change-driven `Animatable` or a `rememberUpdatedState` + coroutine). Strongest emotional payoff; aligns with the name direction.
- **Screen transitions** — no enter/exit animation on screen change. Every push/pop is an instant cut because the hand-rolled `List<Screen>` nav stack doesn't wrap the current screen in `AnimatedContent`. Two implementation paths: (a) wrap the stack in `AnimatedContent` keyed on top-of-stack with slide-horizontal enter / reverse exit — ~20 lines in `MainActivity`, throwaway; (b) wait for the Navigation-Compose migration (already a Conscious deferral below) and get enter/exit transitions for free via `composable { enterTransition = … }`. Prefer (b) — doing (a) now creates code the nav migration will rip out.

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

- **Android 13+ "Restricted settings" gate blocks SMS-permission prompt on sideloaded installs.** After installing the APK from a browser/file-manager (not Play), the user gets a system notification "app denied access to SMS" and the in-app permission prompt simply won't grant. Cause: Android's Restricted-settings policy for sideloaded apps (RECEIVE_SMS / READ_SMS are on the restricted list). User has to open **Settings → Apps → SpendWise → ⋮ → Allow restricted settings** before the in-app prompt works. We hit this on first install 2026-04-16. Mitigations:
  - **Update the PermissionScreen onboarding** to detect the "permission permanently denied" state (`shouldShowRequestPermissionRationale` returns false on first call) and surface a clear "Open app info → Allow restricted settings → come back" instruction with a deep-link button to the app-info page (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).
  - **Document in `docs/RELEASING.md`** so first-time installs (every release until autoupdater takes over) know the step.
  - This is unfixable in code itself — it's Google's friction layer for sideloaded apps. UX work only.
- **Play Protect "App scan recommended" install warning.** First-install of the side-loaded APK triggers Google's Play Protect dialog ("Play Protect hasn't seen this app before. Scan app / Don't install app"). Side-load reality. Mitigations:
  - Ship a **release-signed** APK (set the four `SIGNING_*` repo secrets per `docs/RELEASING.md`) — same key across builds gives the install a stable identity that Play Protect can build reputation against over time.
  - Optionally **register the package** in Google Play Console (even without publishing) so Play Protect knows the package + signing cert pair. Free, requires a Play Console account.
  - Document the "Install without scanning" path in the autoupdater's first-run dialog so the user knows the warning is expected and benign.
  - Accept the warning as a permanent side-load tax if none of the above feel worth the cost.
- **In-app autoupdater**, Tachiyomi-style: periodic GitHub Releases check, download APK, invoke `PackageInstaller`. Requires `REQUEST_INSTALL_PACKAGES`. Reads release body for in-app changelog dialog — see next item for what that body should look like.
- **Release-body changelog automation** — current release body is GitHub's default blurb. For the autoupdater dialog to be readable, ship a grouped Markdown changelog generated automatically per release. Plan:
  - **Layer 1: `git-cliff` in the workflow.** Single binary, single config (`cliff.toml`). Groups commits by prefix into Features / Bug fixes / Performance / Internal / Other. Pre-existing un-prefixed commits get bucketed via regex map (e.g. `^(Fix|Drop|Scrub)` → Bug fixes / Internal; `^(Dashboard|Nav|Auto-detect|Add|Post-spend|Implement)` → Features). Path filter hides commits touching only `plans/pending.md`.
  - **Layer 2: going-forward conventional-commit prefixes** (`feat:` / `fix:` / `perf:` / `refactor:` / `chore:` / `docs:`). No enforcement hook — cliff's regex fallback covers un-prefixed.
  - **Layer 3: human-written tag annotation** as the release-body header. `git tag -a vX.Y.Z` already prompts for it; cliff prepends it above the auto-generated sections.
  - **APK SHA-256 appended** to the body so the autoupdater can verify download integrity before install.
  - **Backfill v0.1.0 body** by running cliff locally and `gh release edit v0.1.0 --notes "$(...)"` once cliff is configured.
  - Skipped for now: per-release min-DB-version marker (only matters when we ship a one-way Room migration; revisit then).
- **Publish the repo + first release.** Workflow + keystore config shipped (`docs/RELEASING.md`). Remaining: create the GitHub repo, `git remote add origin …`, push, optionally add the 4 `SIGNING_*` secrets, tag `v0.1.0`.
