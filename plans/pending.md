# SpendWise — pending work

Snapshot after commit `4b7f530` (UI overhaul). Re-evaluate periodically; delete rows as they ship.

## Real bugs (from on-device testing, 2026-04-15)

- **System back button kills the app.** MainActivity routes `Screen` via a single sealed state; no BackHandler is wired. Pressing back from any non-dashboard screen should pop the screen stack; currently Android finishes the activity.
- **Forex stays pending on device even with internet.** `INTERNET` permission is in the manifest (no runtime prompt needed for normal perms), so the likely culprits are: worker not enqueued, NetworkType constraint not satisfied, or exchangerate-api call failing. Needs logcat from device.
- **Transaction detail crash** — fixed in commit `4b7f530` on emulator. User may have tested on older APK; re-install + verify on physical phone.

## UX / UI feedback (on-device, 2026-04-15)

- **Over-budget hero reads busy with a big negative number.** Example: `-₹30,027` with red color + "Budget exceeded" banner. Drop the minus sign; let color + banner carry the semantics.
- **No sort controls** on category drill-down or dashboard "Spend by category". Default is latest / amount-desc; user wants ability to switch (amount asc/desc, date asc/desc).
- **Import has a 1-year cap; no "all" option.** `ImportDialog` caps at 12 months. Add "everything on this phone" preset.

## UI gaps (functional but unpolished)

- **AccountEditScreen, RecipientRulesScreen, BudgetSetupScreen, ImportDialog** — compile against theme tokens but haven't been rebuilt to match the Copilot aesthetic. Headlines, mono amounts, spacing, buttons still default Material.
- **Loading / empty / error states** on drill-down screens still show default Material text, not styled.
- **Dashboard state variants** — only normal (19%) and over-budget have been seen on device. Approaching (1b), early-month (1c), end-month-hot (1d), end-month-surplus (1e) are in code but visually unverified; need seeded data that triggers each.
- **Transaction detail forex variant** (v2 mockup frame 6) — USD transaction with rate + source link. Built, not verified.

## Spec gaps (screens linked but missing)

- **Alert thresholds** screen
- **CSV export** screen
- **Rerun parser** screen
- **Purge non-financial** screen
- **Review unmatched** screen
- **CardDrillDown filter chips** (ui-spec §7) — All / Spend / Refunds / Bills / Self-transfers
- **Onboarding 4-step flow** — spec is welcome, permissions, import prompt, budget prompt; only permissions exists
- **Transaction detail Edit / Mark as payback** — v1.1 per spec
- **Dashboard ring/donut chart** — spec mentions it; tinted bars shipped instead. Call to make: keep bars, or build the donut.

## Distribution & updates

Play Store is not an option. Plan:

- **GitHub Actions** build + signed-APK release pipeline.
- **In-app autoupdater**, similar to [TachiyomiSY's updater](https://github.com/jobobby04/tachiyomisy): periodic GitHub Releases check, download new APK, invoke `PackageInstaller` for side-load install. Requires `REQUEST_INSTALL_PACKAGES` permission.

## Tech debt

- **Carry-over math** uses only current-month transactions (TODO in `DashboardViewModel`). Prior-month REFUNDs that alter surplus aren't reflected.
- **`DashboardDerived.computeBanner`** has untested branches — no integration tests for the approaching / surplus / early-month cases.
- **Compose render test coverage** is thin — only `TransactionListItem`. Dashboard / TransactionDetail state-change paths have no regression net.
