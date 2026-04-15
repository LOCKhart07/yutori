# SpendWise — UI Specification (v1)

Screen-by-screen contract: what each screen queries, what it shows in
each state, how the user gets there, and what they can do. Built around
Jetpack Compose + Flow-driven state.

Companion docs: [spendwise-plan.md](./spendwise-plan.md) §8,
[business-logic-spec.md](./business-logic-spec.md),
[schema.md](./schema.md),
[settings-spec.md](./settings-spec.md).

---

## 1. Scope

v1 MVP ships these screens:
1. Onboarding / permissions
2. Dashboard (home)
3. Category drill-down
4. Card/account drill-down
5. Transaction detail (read-only)
6. Budget setup
7. Settings hub (accounts, recipient rules, thresholds, import, export,
   purge)
8. CSV export

Out of scope for v1 MVP, stubbed or placeholder:
- Edit transaction (classification, category override)
- "Mark as payback" (§12.1 resolution)
- Search/filter across all transactions
- Widgets
- Themes / customization

## 2. Navigation graph

```
Onboarding (first launch, skippable after initial permission grant)
    └─► Dashboard (root)
            ├─► Category drill-down (tap category in ring)
            │       └─► Transaction detail
            ├─► Card drill-down (tap masked-card chip)
            │       └─► Transaction detail
            ├─► Budget setup (tap budget header / edit pencil)
            ├─► Settings
            │       ├─► Accounts
            │       ├─► Recipient rules
            │       ├─► Alert thresholds
            │       ├─► Import past SMS
            │       ├─► Rerun parser
            │       ├─► Purge non-financial SMSes
            │       └─► CSV export
            └─► Pending FX banner (tap) → list of pending transactions
```

Uses Jetpack Navigation-Compose. Every non-root destination has a
back stack. System back always works.

## 3. State model

Each screen exposes a single `UiState` sealed type with at minimum:

```kotlin
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Empty(val reason: EmptyReason) : DashboardUiState
    data class Ready(
        val month: String,                 // "April 2026"
        val effectiveBudget: BudgetLine,   // limit, carry-over, effective
        val spend: SpendLine,              // current, remaining, days left
        val categories: List<CategorySlice>,
        val cards: List<CardChip>,
        val burnRate: BurnRateLine,        // daily avg vs. required
        val pendingForexCount: Int,
        val pendingAlertNotice: AlertNotice?,  // most recent unread alert
    ) : DashboardUiState
    data class Error(val throwable: Throwable) : DashboardUiState
}
```

State flows from Room via `Flow<DashboardUiState>` — changes propagate
to the composable without manual refresh.

## 4. Onboarding

Shown on first launch, or whenever no permission has ever been granted.

### 4.1 Screens

**Screen 1 — Welcome.** One headline, two paragraphs of plain text:
"SpendWise reads bank SMSes on this device to auto-track your spending."
"Nothing leaves your phone." One CTA: "Get started."

**Screen 2 — Permission ask.** Explains exactly what each permission is
for:
- `RECEIVE_SMS` — "so new transactions show up automatically."
- `READ_SMS` — "so we can import your past transactions (optional)."

Two buttons: "Grant access" (triggers runtime permission dialog) and
"Skip for now." Skipping puts the user on a reduced-capability
dashboard with a persistent "Enable SMS access" banner.

**Screen 3 — Import decision.** Only shown if `READ_SMS` was granted.
"Would you like to import the last month's SMS history?" Options:
"Yes, last 1 month" / "Yes, last 3 months" / "Custom date" / "Skip."
Running the import kicks off the historical import worker
([ingestion-spec.md](./ingestion-spec.md) §7) and proceeds to the
dashboard.

**Screen 4 — Budget setup prompt.** "Set your monthly budget" — inline
number input (INR). Can be skipped; dashboard shows "No budget set"
with a CTA.

### 4.2 Contract

- Onboarding never blocks access to the dashboard for more than two
  permission taps.
- Every screen has a "Skip" that advances without modification.
- Onboarding never re-fires after completion unless the user clears
  app data.

## 5. Dashboard

The primary screen.

### 5.1 Data contract

Observes a single Flow that joins:
- `budgets` row for current month (or null)
- `transactions` in current month with `budget_effect = SPEND` and
  `inr_amount IS NOT NULL` (summed)
- `transactions` in current month with `budget_effect = REFUND` (summed,
  negative against spend for the dashboard's "effective spend" figure)
- `transactions` in current month grouped by `category` (for the ring)
- `transactions` in current month grouped by `last4` (for the chips)
- `transactions` in current month with `rate_source = 'pending'` (count)
- Most recent unread `budget_alert_state` row (for banner)

All joins happen in a single DAO call returning a denormalized snapshot.
Flow emits on any change.

### 5.2 Layout (top to bottom)

1. **Month selector** — "April 2026 ▾". Tap → date picker that snaps
   to month boundaries. Cannot navigate to future months (pending
   transactions would be zero). Can navigate back as far as data exists.

2. **Budget / spend gauge.** Full-width circular or semi-circular gauge.
   Visual:
   - Track: gray, the full effective budget.
   - Fill: primary color up to 50%; amber at 50–80%; red at 80–100%;
     deeper red with an overage "spike" past 100%.
   - Center label: spend as a large number, "of INR {effective}" as
     subtitle.
   - Below the gauge: three stat chips — `Carry-over: +₹X` / `Days left:
     N` / `Daily burn: ₹X (need ₹Y)`.

3. **Category ring chart.** Full-width donut, 1 ring, slices per
   category in proportion to spend. Tap a slice → category drill-down.
   Cash (from ATM withdrawals) is called out as a separate slice with a
   distinct visual (e.g., dashed stroke) per plan §4.3. Legend below
   the ring: category name + INR spend. Tapping a legend row same as
   tapping its slice.

4. **Card/account chip strip.** Horizontal scroll of chips, one per
   `last4` with transactions this month. Each chip shows the
   `last4`-derived issuer icon (Axis, Kotak, etc. — from a static asset
   map), the masked last4, and this month's total. Tap → card drill-down.

5. **Pending FX banner** (conditionally shown). "1 transaction pending
   conversion. Tap to view." Tap → a list of transactions with
   `rate_source = 'pending'`.

6. **Latest alert banner** (conditionally shown). "You've hit 80% of
   your budget." Dismissible; dismissal sets
   `budget_alert_state.dismissed_at_ms` (new column — v1 can get away
   without this and just use an in-memory dismissed-ids set).

### 5.3 Empty / degraded states

| Condition | What shows |
|---|---|
| No SMS permission at all | Full-screen explainer + "Grant access" CTA. Dashboard disabled. |
| RECEIVE_SMS missing only | Top persistent banner: "Real-time tracking is off. Tap to enable." Dashboard functional with existing data. |
| No transactions this month | Gauge shows "₹0 spent of ₹X". Categories empty ("No spend yet this month"). Cards empty. |
| No budget set | Gauge replaced with "Set a monthly budget" CTA. Category ring still shown (based on spend with no budget context). |
| Transactions exist but no completed FX | Gauge shows spend total; pending FX banner present. |

### 5.4 Refresh semantics

No pull-to-refresh. Flow emits automatically. On resume, DAO re-queries
(Flow-backed, effectively free).

## 6. Category drill-down

Reached by tapping a category slice or legend row on the dashboard.

### 6.1 Data contract

Observes `Flow<List<Transaction>>` filtered by
`month_key = current AND category = selected AND budget_effect = SPEND
(or REFUND, shown separately)`. Sorted by `occurred_at_ms` descending.

### 6.2 Layout

1. Top bar: category name + back button.
2. Summary strip: `X transactions, ₹Y total` + "compared to last month:
   +/-Z%" chip (subtle).
3. Scrollable list. Each row:
   - Merchant (primary line, bold).
   - Date (secondary).
   - Amount (right-aligned, larger).
   - Tap → transaction detail.
4. Empty state: "No transactions in this category this month."

### 6.3 Special cases

- Category `CASH` shows an infobox at the top: "Cash withdrawn is
  counted at time of withdrawal. Smaller cash spends after that are
  not tracked." (Plan §4.3 transparency.)
- Category `UNCATEGORIZED` shows at the top: "These merchants are
  cross-cutting platforms (Amazon, Blinkit, etc.) we can't categorize
  from the SMS alone." (Plan §5.1.)
- Category `OTHER` shows at the top: "Miscellaneous merchants that
  don't fit a specific category."

## 7. Card/account drill-down

Reached by tapping a card chip on the dashboard.

### 7.1 Data contract

Observes `Flow<List<Transaction>>` filtered by
`month_key = current AND last4 = selected`. No `budget_effect` filter —
this view deliberately shows bill payments and self-transfers alongside
spend, because it answers "what happened on this card this month?" not
"how did it affect my budget?"

### 7.2 Layout

1. Top bar: issuer logo + masked `XXnnnn`.
2. Summary strip: `N transactions, ₹X total spend, ₹Y bill payments,
   ₹Z self-transfers`.
3. Filter chips: `All | Spend | Bill payments | Self-transfers`. Toggle
   to show subset.
4. List (same row design as category drill-down).

### 7.3 Why this differs from category drill-down

This view surfaces `CC_BILL_PAYMENT` and `SELF_TRANSFER` transactions
by design. In the category view these would clutter and confuse. Here
they're the point — "I moved ₹X between accounts this month" is useful
information even though it doesn't affect spend.

## 8. Transaction detail

Reached by tapping any transaction in any list.

### 8.1 Data contract

Observes a single `Flow<TransactionDetail>` that joins:
- The `transactions` row.
- All `transaction_sms_sources` for it.
- Each source's `sms_log` row (sender, body, received_at_ms, pattern).
- The resolved `account` row (if account_id present).

### 8.2 Layout

1. Top bar: "Transaction" + back button.
2. Hero block: amount (large), merchant (medium), date+time (small),
   classification pill (colored by budget effect).
3. If forex: "Original: USD 10.00 • Rate: 83.50 (exchangerate-api.com,
   01 Jan 2026)"
4. If merged from multiple SMSes: "Built from 3 sources" expandable
   block showing each `sms_log` source with its role and primary flag.
5. Raw SMS(es): verbatim body display. Monospace font, selectable,
   collapsible. The raw truth is always reachable.
6. Metadata row: `account_id` / `last4` / `category` / `pattern_matched`.

### 8.3 Actions

v1 MVP: **no actions**. Transaction detail is read-only.

v1.1 future actions, placeholder menu items greyed out:
- "Change classification"
- "Change category"
- "Mark as payback for…"
- "Attach receipt" (§10.3 future scope)

## 9. Budget setup

Reached from dashboard's "edit" pencil on the gauge, or from Settings.

### 9.1 Data contract

Reads / writes the `budgets` row for the selected month.

### 9.2 Layout

1. Top bar: "Budget for April 2026" + back.
2. Large numeric input: monthly limit (INR).
3. Thresholds section:
   - Info: "Alerts fire at 50% (fixed), 100% (fixed), and every 10% over."
   - Slider / input for "Warn at X%" — default 80, range 60–95.
4. Carry-over breakdown, read-only:
   - "From March 2026: +₹10,000 underspent"
   - "From February 2026: +₹5,000 underspent"
   - …and so on back to first budgeted month.
   - Final line: "Effective budget: ₹limit + ₹carry-over = ₹total"
5. "Save" button; disabled if no change.

### 9.3 Rules

- Limit must be ≥ 0. Negative values rejected with inline error.
- Limit may be 0 (user wants to track without a budget target). Gauge
  shows the bar as "overflow" coloring immediately.
- Changing mid-month does not retroactively reset alerts (state machine
  §7.3 only fires on upward crossing; lowering limit past current spend
  would otherwise fire all previously-uncrossed thresholds at once).
  Handled by the state machine ignoring already-recorded fires.

## 10. Settings

Hub screen; each item deep-links to its own sub-screen.

### 10.1 Item list

| Item | Destination | Covered in |
|---|---|---|
| Accounts | My Accounts screen | settings-spec.md §2 |
| Recipient rules | Recipient rules screen | settings-spec.md §3 |
| Alert thresholds | Inline within Budget setup (shortcut) | §9 above |
| Import past SMS | Historical import screen | settings-spec.md §4 |
| Rerun parser on stored messages | Confirm + progress screen | settings-spec.md §5 |
| Purge non-financial SMSes | Confirm + progress screen | settings-spec.md §6 |
| Export CSV | Export screen | §11 below |
| Permissions | System settings shortcut | n/a |
| About | Version, credits, open-source notice | n/a |

### 10.2 Layout

Plain vertical list with section dividers. Icons per item (Material).
Tap → navigate. Secondary line under each shows status where relevant
("Last import: 01 January 2026, 1,000 messages").

## 11. CSV export

Reached from Settings.

### 11.1 Data contract

Writes a CSV to the device's public Downloads folder using the Storage
Access Framework for the destination (no blanket `WRITE_EXTERNAL_STORAGE`
permission needed on modern Android).

### 11.2 Options

- Date range picker (default: current month).
- Include fields selector (checkboxes):
  - ✓ Classification
  - ✓ Amount (INR)
  - ☐ Original amount + currency (only relevant for forex)
  - ✓ Merchant
  - ✓ Category
  - ✓ Last4
  - ✓ Date
  - ☐ Raw SMS body (privacy-sensitive default off)
  - ☐ Source SMS id(s)

### 11.3 Output format

- UTF-8 CSV, RFC 4180 quoting.
- Header row: chosen field names.
- One row per `transactions` row (not per `sms_log`). Multi-source
  transactions collapse to one row; raw SMS column joins bodies with
  `" || "` separator if included.
- Filename: `spendwise_{startDate}_to_{endDate}.csv`.

### 11.4 Scale

Dataset volume (200–500 rows typical) → export completes in <500 ms.
No progress indicator needed.

## 12. Notifications

### 12.1 Budget alerts (from business-logic-spec §7.4)

- Channel: "Budget alerts" — user-configurable importance (default Low).
- Title: derived from threshold (50% / 80% / 100% / 120%).
- Body: current spend / effective / remaining or overage.
- Tap action: deep-link to dashboard. The `pendingAlertNotice` banner
  on the dashboard will be present until dismissed.

### 12.2 Historical import progress

- Channel: "Import progress" — default Low.
- Sticky notification for the duration of the worker.
- Not dismissible while running.
- On completion: replaced by a brief summary notification (dismissible).

### 12.3 Pending FX (conditional)

- Channel: "Data issues" — default Min (appears only in shade, no
  heads-up).
- Fires only if pending FX transactions haven't been resolved after
  24 hours.
- Tap → dashboard pending-FX list.

### 12.4 Not in v1

- Daily summary notifications (would be nice; not essential).
- Per-transaction notifications ("You spent ₹X at Zomato"). No — noise.
  Alerts fire on budget thresholds, not individual spends.

## 13. Empty and error states — consolidated

| Scenario | Screen | Handling |
|---|---|---|
| First launch, no permission | Dashboard | Full-screen explainer; "Grant access" CTA. |
| First launch, permission granted, no data | Dashboard | "Import past SMS or wait for new ones" CTA. |
| Permission revoked after data exists | Dashboard | Persistent top banner; dashboard functional. |
| No budget set | Dashboard | "Set a budget" CTA in place of gauge. Categories / cards still shown. |
| DB migration failed on upgrade | App root | Full-screen error; "Reinstall from backup or clear data" message. |
| Pending FX | Dashboard | Banner. Transactions excluded from spend total. |
| Historical import running | Dashboard | Non-blocking. Persistent notification shown; dashboard updates as rows land. |
| Rerun parser running | Dashboard | Non-blocking. Same as import. |
| Classifier throws on one row | Transactions list | Row is skipped; debug log captures stack; subsequent rows unaffected. |

## 14. Accessibility

- **Content descriptions** on every icon, gauge, chart, and chip.
- **Dynamic type** respected — no fixed `sp` values; use Compose
  `MaterialTheme.typography`.
- **Colors** are never the sole signal — every color-coded indicator
  (budget progress, classification pill) also has text / shape.
- **TalkBack** — each transaction row reads as "{merchant}, {category},
  ₹{amount}, {date}." No lookup required to understand.
- **Landscape + tablet** — layouts reflow; the dashboard uses a 2-column
  arrangement (budget + categories left, cards + alerts right) on >600dp
  width. Specifics are flexible.

## 15. Visual design

Constraints, not a complete design system:

- Material 3 theming. Dynamic color on Android 12+.
- Primary color reserved for the budget gauge fill and key CTAs — no
  other blue accents to prevent visual competition.
- Classification pill colors (constant across dark/light):
  - SPEND: neutral (text color on surface)
  - REFUND: success green
  - INCOME: info blue
  - DROP: muted outline (for when it appears on a card drill-down)
- No gradients. No drop shadows beyond Material's default elevation.
- Mono font (e.g., JetBrains Mono) for raw SMS bodies. Everything else
  sans-serif.

## 16. Testing contract

### 16.1 Compose UI tests (instrumentation)

Per-screen happy-path + each empty state:

```kotlin
@Test fun `dashboard shows gauge and ring when data is present`()
@Test fun `dashboard shows empty-state CTA when no transactions`()
@Test fun `category drill-down lists transactions for selected category`()
@Test fun `tapping a transaction opens detail with raw SMS body`()
@Test fun `budget setup disables Save when nothing changed`()
```

### 16.2 Snapshot tests

One per top-level screen in each state (loading/empty/ready/error),
for both light and dark themes, phone and tablet widths. Tool: Paparazzi
or Roborazzi (run on JVM, no device).

### 16.3 Navigation tests

Deep-link assertions: tapping an alert notification from outside the
app lands on the dashboard with the correct month selected and the
alert banner visible.

### 16.4 Accessibility audit

One test per screen runs the Compose accessibility scanner; fails on
any issue of severity warning or higher.

### 16.5 State contract tests (pure JVM)

The `DashboardUiState` mapping function (`ViewModel` internals) is pure
Kotlin. Full unit coverage:

```kotlin
@Test fun `Loading state when no DB query yet returned`()
@Test fun `Ready state merges transactions, budgets, and alerts`()
@Test fun `Empty state reason is NO_DATA when month has no txns`()
```

## 17. Testability posture

| Behavior | Category |
|---|---|
| State mapping (Flow → UiState) | Strict TDD |
| Navigation route definitions | Strict TDD (URL assertions) |
| Screen layout (Compose) | Snapshot + Compose UI test |
| Animation feel, gesture responsiveness | Manual verify |
| Color contrast, touch target size | Accessibility scanner |
| Deep-link from notification | Integration |
| Dynamic color / dark theme visuals | Snapshot |
| Real device rendering at different densities | Manual verify |

## 18. Coverage posture

- **ViewModel state-mapping logic: 100% line coverage.** It's pure
  Kotlin; any gap is dead code.
- **Compose composables: no line-coverage goal.** Snapshot tests cover
  rendering; Compose UI tests cover interaction. Trying to hit a
  percent on composables wastes effort.
- **Navigation graph: 100% route coverage.** Every destination reachable
  from at least one test.

## 19. Decisions (resolved 2026-04-15)

- **Alert notification importance: Low.** Silent in shade, no heads-up.
  Budget alerts are reference info, not interruptions.
- **Category ring: theme palette.** 11 accents derived from Material
  dynamic color. No brand colors — they'd fight the primary accent.
- **Month-over-month comparisons: off in v1 MVP.** Volatility makes
  simple deltas misleading across the sample data. Revisit when
  smoothing approaches are designed.
- **Tablet/landscape: phone-only, portrait for v1 MVP.** Accessibility
  §14 still calls out dynamic type + TalkBack; dimensional flexibility
  is a later concern.
