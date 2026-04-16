# Yutori — backlog index

Thin index into GitHub issues. Issues are the source of truth; this file is just a sorted view.

Regenerate or re-order freely — don't trust ordinal numbers in old git revisions of this file. For the current live state filter issues by label: https://github.com/LOCKhart07/spendwise/issues?q=is%3Aissue+is%3Aopen

## Tier 0 — ship-blockers / foundational

- #8 — Lock app name: Yutori — rename SpendWise → Yutori across code, docs, plans

## Tier 1 — easy wins (hours, no schema)

- #12 — Mid-month overshoot projection
- #13 — Frequency insight on dashboard ("N small txns · median ₹X")
- #14 — Budgets roll forward by default
- #15 — Budget suggestions from history
- #16 — Daily-burn stat pill — pace tint + target value
- #18 — Late-arriving past-month alert stamping
- #19 — Rebuild database screen

## Tier 2 — behavior-change core (medium effort)

- #26 — Hard-stop over-budget state (opt-in)
- #27 — Per-tx notes + ignore-a-transaction
- #28 — Manual recipient-rule add/edit form
- #29 — "Add a rule from this transaction" entry point
- #30 — Reparse pipeline
- #31 — "Offer" reclassify confirm dialog after saving account UPI handle
- #32 — Review unmatched screen
- #75 — Backfill account_id on existing txs when a new account is registered
- #76 — Add Kotlin linter (detekt or ktlint) + wire into CI
- #83 — MigrationErrorScreen: destructive button styled as primary

## Tier 3 — structurally important, bigger lifts

- #33 — Historical-import worker checkpointing + foreground notification
- #34 — Add source field to sms_log + transactions *(prereq for #35)*
- #35 — PDF/CSV statement import
- #36 — Goals entity + translator
- #37 — Annual-cost smoothing buckets
- #38 — Per-category pacing baseline (median-of-3 historical)
- #85 — MigrationErrorScreen: stack trace not scrollable; Copy has no feedback
- #87 — Permission gate: row order mismatch + misleading check/warning glyphs
- #93 — Auto-promote active accounts from SUGGESTED to CONFIRMED *(follow-up to #82)*
- #94 — Dashboard has no P99 headroom — fix before piling on animations *(gates #52–#56)*
- #96 — Upgrade toolchain + libraries (Kotlin 2.x, AGP 9, Gradle 9, compileSdk 36)

## Tier 4 — nice-to-have polish

- #39 — Onboarding 4-step flow
- #40 — CardDrillDown filter chips
- #41 — Dashboard ring/donut decision
- #42 — Carry-over per-prior-month breakdown in BudgetSetup
- #43 — Dashboard state variants — visual verification
- #44 — Forex tx-detail variant — visual verification
- #45 — Bucket simplification mode
- #46 — Surfacing suggested accounts on dashboard
- #47 — BudgetSetup pace anchor
- #48 — Card drill-down hero pace bucket
- #49 — Category drill-down hero pace bucket
- #50 — Per-category pacing tag on dashboard rows
- #51 — Tx-detail Edit / Mark as payback
- #52 — Animation: traffic-light hero color transition
- #53 — Animation: AnimatedVisibility on dashboard banners
- #54 — Animation: progress-bar fill
- #55 — Animation: money counter on hero
- #56 — Animation: screen transitions
- #77 — In-app changelog / "What's new" view
- #78 — Migration error screen: 'Report this error' action (bug-report flow + PII sanitization)
- #91 — Month-picker grid — quick-jump to arbitrary month *(follow-up to #21)*
- #92 — Dashboard pager peek is invisible *(follow-up to #21)*
- #97 — Expose precise pace delta (pp) in a detail/analysis surface *(follow-up to #17)*
- #99 — Over-banner third pill shows 'Deficit' — redundant with the banner above it *(follow-up to #16)*

## Tier 5 — deferred / low urgency

- #58 — Dashboard flashes "₹0" when re-entered
- #59 — DashboardDerived.computeBanner untested branches
- #60 — Compose render tests are thin
- #61 — Navigation-Compose migration
- #62 — Move historical-import from dashboard dialog to Settings
- #63 — Carry-over genesis month setting
- #64 — AI-assisted rule creation
- #65 — Launcher icon / logo design (Yutori)
- #66 — About screen
- #67 — Alert thresholds screen
- #68 — CSV export screen
- #69 — Purge non-financial screen
- #70 — Rerun parser screen
- #71 — In-app autoupdater

## Deferrals (no tier — decision log)

- #72 — Custom splash screen — deferred

## Unvetted external suggestions

- #73 — External agent critique — unvetted framings and suggestions

## Unlabelled

- #79 — Add an easter egg somewhere in the app

## Label conventions

Priority: `tier-0` `tier-1` `tier-2` `tier-3` `tier-4` `tier-5`
Type: `bug` `enhancement` `tech-debt` `branding` `deferred` `unvetted`
Topic: `ui` `distribution` `animation` `audit`

Useful filters:
- Open ship-blockers: `is:open label:tier-0`
- Open bugs: `is:open label:bug`
- Tech debt backlog: `is:open label:tech-debt`
- Distribution / release: `is:open label:distribution`
- UI-touching work (mockup needed — see CLAUDE.md): `is:open label:ui`
