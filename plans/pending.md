# SpendWise — pending work

Reconciled snapshot. Delete rows as they ship.

## UX / layout gaps

- **"KOTAK UPI" shows 2 similar sources** — user-flagged on device, needs a screenshot to diagnose. Probably two merchant-key variants (spacing, casing) that should normalize to one.

## Branding / identity

- **App name** — "SpendWise" is placeholder. Pick a real name (affects AndroidManifest label, string resources, splash, in-app copy).
- **Launcher icon / logo** — currently the default Android icon. Need an adaptive icon (foreground + background layer) at the standard density buckets.

## Features not yet built

- **Dashboard ring/donut chart** — spec mentions; tinted bars shipped. Decide: keep bars or build donut.
- **Onboarding 4-step flow** (welcome, permissions, import prompt, budget prompt). Only permissions exists.
- **CardDrillDown filter chips** — All / Spend / Refunds / Bills / Self-transfers (ui-spec §7).
- **Tx-detail Edit / Mark as payback** (v1.1 per spec).

## Spec-linked screens not yet built

- Alert thresholds
- CSV export
- Rerun parser
- Purge non-financial
- Review unmatched
- `RecipientRulesScreen` — compiles against theme tokens but not retrofitted to Copilot aesthetic.

## Visually unverified (code exists, no eyes on)

- Dashboard state variants 1b (approaching), 1c (early-month), 1d (end-month-hot), 1e (end-month-surplus) — need seeded data that triggers each.
- Forex tx-detail variant (mockup frame 6).

## Tech debt

- **Carry-over math** uses only current-month transactions (TODO in `DashboardViewModel`). Prior-month REFUNDs that alter surplus aren't reflected.
- **`DashboardDerived.computeBanner`** untested branches — approaching / surplus / early-month paths.
- **Compose render tests** are thin — only `TransactionListItem`. Dashboard / TransactionDetail state-change paths have no regression net.
- **Loading / empty / error states** on drill-down screens fall back to default Material text.

## Distribution & updates

- **GitHub Actions** — signed-APK build + release pipeline on tag push.
- **In-app autoupdater**, Tachiyomi-style: periodic GitHub Releases check, download APK, invoke `PackageInstaller`. Requires `REQUEST_INSTALL_PACKAGES`.
