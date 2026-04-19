# Yutori — Settings Specification (v1)

Every settings sub-screen, what it reads/writes, what the UX contract
is, and how it's tested. Settings is where user configuration lives for
the open-question resolutions (§12.1–§12.4) and for the one-shot
operations (import, reparse, purge).

Companion docs: [yutori-plan.md](./yutori-plan.md),
[ui-spec.md](./ui-spec.md) §10,
[ingestion-spec.md](./ingestion-spec.md),
[business-logic-spec.md](./business-logic-spec.md),
[schema.md](./schema.md).

---

## 1. Overview

Settings is a hub screen (per [ui-spec.md](./ui-spec.md) §10) with
deep-links to:

1. Accounts (own accounts for §12.2 self-transfer detection)
2. Recipient rules (shared §12.2 + §12.4 registry)
3. Alert thresholds (alias to the Budget screen's threshold section)
4. Import past SMS
5. Rerun parser on stored messages
6. Purge non-financial SMSes
7. CSV export (owned by [ui-spec.md](./ui-spec.md) §11 — this doc only
   references it)
8. Permissions shortcut
9. About

This document spells out screens 1, 2, 4, 5, 6, and 9. The others are
references or stubs.

## 2. Accounts

### 2.1 Purpose

Register user's own bank/card/investment accounts so the classifier
(§2.3 of [business-logic-spec.md](./business-logic-spec.md)) can
identify self-transfers. Without this screen, every Kotak→Axis transfer
continues to count as spend.

### 2.2 Data contract

Reads/writes the `accounts` table per
[schema.md](./schema.md). Also writes to `recipient_rules` when the
user associates a VPA with an account (see §2.5).

### 2.3 Seed

First-launch seed (shown as a suggestion, not auto-confirmed):

```
Kotak Bank         XX0000   SAVINGS       "Primary"
Axis Bank          XX2222   SAVINGS       "Axis savings"
Vasai Janata Bank  006666   SAVINGS       "Secondary"
Axis Bank          XX1111   CREDIT_CARD   "Axis ACE"
Axis Bank          XX4444   CREDIT_CARD   "Axis ACE (replacement)"
Kotak Bank         x3333    CREDIT_CARD   "Kotak CC"
Paytm Money        X5555    INVESTMENT    "Paytm Money"
```

These come from the feasibility dataset (accounts memo, local-only).
The seed is shipped as static data in the APK, not in the Room
DB — the user confirms each entry individually before it's
written to `accounts`.

**Why confirmation over auto-insert:** the seed is the developer's
accounts. Any future user won't have them; the seed list should be
derived from the actual SMS-inbox scan, not hard-coded. For v1 MVP, we
hard-code for one user and make the UI general enough to remove/edit
entries.

### 2.4 Layout

1. Top bar: "My accounts" + back + add (+) icon.
2. If empty: explainer card — "Register your own accounts so we can tell
   when you're just moving money between them, not spending it." CTA:
   "Add your first account."
3. If non-empty: list of account rows grouped by kind (Savings, Credit
   Card, Investment, Other). Each row shows issuer icon, masked last4,
   display name, and an overflow menu (⋮) with Edit / Delete.
4. Floating action button: "Add account."

### 2.5 Add / edit screen

Fields:
- Issuer (text with autocomplete from known issuers).
- Masked last4 (text, 4–6 chars, validates against `^[A-Za-z0-9]{4,6}$`).
- Kind (dropdown: Savings / Credit Card / Investment / Other).
- Display name (optional text).
- Default spend account toggle (only for Savings/CreditCard; persisted
  as `accounts.is_default_spend`).
- Associated UPI handles (repeatable text; each one becomes a
  `recipient_rules` row with `reclassify_as = SELF_TRANSFER,
  account_id = this.id` on save).

Validation:
- Issuer non-empty.
- Last4 non-empty, unique per (issuer, last4) pair — inserting a
  duplicate fails with inline error.
- UPI handles deduplicated silently.

### 2.6 Delete

- Confirm dialog: "Delete account {name}? {N} recipient rules linked to
  this account will also be removed. Transactions will stay in your
  history but will no longer be auto-classified as self-transfers."
- On confirm: delete the `accounts` row and cascade-delete associated
  `recipient_rules`. Past `transactions` rows keep their recorded
  classifications (deletion does not retroactively reclassify —
  the user can trigger a reparse if they want).

### 2.7 Post-save behavior

After adding or editing an account with UPI handles:

1. DB writes happen atomically (account + rules in one transaction).
2. Optionally offer "Reclassify past transactions using this new
   account?" — one-tap, triggers the reparse worker (§5) scoped to
   relevant `sms_log` rows only (those whose merchant matched one of
   the new rules).

## 3. Recipient rules

### 3.1 Purpose

Surface the §12.2 + §12.4 shared registry directly. Users can add
their own rules for VPA/merchant patterns, or inspect the seed rules
shipped with the app.

### 3.2 Data contract

Reads/writes `recipient_rules` per [schema.md](./schema.md).

### 3.3 Seed

Shipped `recipient_rules` (with `source = SEED`):

| pattern | pattern_kind | reclassify_as | note |
|---|---|---|---|
| `cred\.club[@¡]axisb` | REGEX | CC_BILL_PAYMENT | CRED CC bill payments |
| `.*@paytm(cc\|postpaid\|ccbill)` | REGEX | CC_BILL_PAYMENT | Paytm CC rails |
| `.*@ybl.*creditcard` | REGEX | CC_BILL_PAYMENT | PhonePe CC |
| `.*@okhdfcbankcc` | REGEX | CC_BILL_PAYMENT | HDFC CC via Google Pay |

(`account_id` = null for these — not user-specific.)

### 3.4 Layout

1. Top bar: "Recipient rules" + back + add (+).
2. Info card at top: "Rules let Yutori reclassify transactions based
   on who you're paying. For example, payments to CRED aren't spend —
   they're bill payments for your credit card."
3. Tabs or sections:
   - **Self-transfer rules** (generated from Accounts §2 + user-added
     manual rules). Each row shows pattern + linked account name.
   - **CC bill payment rules** (SEED + user-added).
   - **Other rules** (user-added, reclassify_as = other classifications).
4. Each row: pattern (monospace), kind chip (LITERAL/REGEX/PREFIX),
   reclassify_as pill, toggle enable/disable, overflow menu.

### 3.5 Add / edit rule screen

Fields:
- Pattern (text; monospace input).
- Pattern kind (dropdown: Literal, Prefix, Regex).
  - Literal: exact string match, case-sensitive.
  - Prefix: `startsWith` match, case-sensitive.
  - Regex: Java `Pattern`-compatible regex; shown with a live-match
    preview against the user's most recent 100 UPI recipients (read
    from `transactions.merchant` where classification = UPI_PAYMENT).
- Reclassify as (dropdown).
  - `Don't change` (top entry, info-tinted) — keeps the parser's
    classification; combined with Assigned category this is the
    category-only rule path (mockup v16 State A).
  - Reclassify targets: CC_BILL_PAYMENT, SELF_TRANSFER, REFUND,
    INCOMING_CREDIT, NON_FINANCIAL.
- Assigned category (optional dropdown: None + all `Category` enum
  values). Visible whenever the selected reclassify target can carry a
  category in dashboard math (SPEND/REFUND), or when reclassify-as is
  `Don't change` (the rule defers to the parser's classification, which
  for the rule-addressable cases — UPI_PAYMENT, CC_TRANSACTION,
  DEBIT_CARD, ATM_WITHDRAWAL — is SPEND-effect).
- Linked account (optional; only if reclassify_as = SELF_TRANSFER).
- Note (optional text).

Behavior notes:
- Rule-level category assignment and reclassify_as affect future
  matching transactions only. They do not retroactively recategorize
  history until the reparse pipeline runs.
- A rule with `reclassify_as = null` and `assigned_category = null` is
  a no-op; Save is disabled with an inline explanation.

Validation:
- Pattern non-empty.
- If regex kind: compile check. Invalid regex shows inline error.
- No duplicate (pattern, pattern_kind) — check at save.
- At least one of `reclassify_as` / `assigned_category` must be set.

### 3.5.1 Edit transaction (classification + category)

Transaction-detail screen exposes two action buttons — `Edit
classification` and `Edit category` — each opening a focused bottom
sheet (mockup v16 States C–E). Both sheets:

- Show "Use automatic <field>" at the top (info-tinted), which clears
  the per-tx override flag and copies the matching `*_inferred`
  snapshot back into the live column.
- Show a `Currently: automatic` / `Currently: overridden` hint under the
  title.
- Disable Save unless the selection actually changes the field's value
  or its override flag.

Edit classification additionally:
- Recomputes `budget_effect` via `BudgetEffectMapper` on save.
- Is gated by `budget_effect ∈ {SPEND, REFUND}` only as a UI affordance;
  the underlying mutation accepts any value (matches the per-tx-edit
  power-user model).

### 3.6 Rule test tool

On the add/edit screen, a "Test" button runs the proposed pattern
against all recent UPI merchants, showing the first 20 matches. Helps
the user confirm the rule captures what they intend without false
positives.

### 3.7 Delete / disable

- Seed rules (`source = SEED`) can be **disabled** but not deleted. The
  toggle only flips `is_enabled`.
- User-added rules (`source = USER`) can be both disabled and deleted.
- Disabling a rule does not retroactively change `transactions`. A
  reparse action is offered as a follow-up.

## 4. Import past SMS

### 4.1 Purpose

Trigger the historical import worker per
[ingestion-spec.md](./ingestion-spec.md) §7.

### 4.2 Data contract

Reads from `content://sms` (count query), writes to `sms_log`, triggers
the classifier downstream.

### 4.3 Layout (pre-run)

1. Top bar: "Import past SMS" + back.
2. Permission check: if `READ_SMS` is not granted, full-screen CTA with
   permission request trigger. No import options shown.
3. Date range picker. Defaults: "Last 1 month" quick option selected;
   "Last 3 months" / "Last 6 months" / "Last 1 year" quick options;
   "Custom" opens a date range picker.
4. Preview: after the user picks a range, tap "Count messages" to run a
   count query (fast, ~100ms). Shows: "Found 1,000 SMSes between 01
   Jan 2026 and 15 Apr 2026."
5. "Start import" CTA — disabled until count ran.

### 4.4 Layout (during run)

1. Progress bar + "Processing 340 / 1,000 (27%)."
2. Live stats: transactions created so far, bill payments, non-financial
   dropped, unmatched-financial.
3. "Run in background" button — navigates back to dashboard; worker
   continues; persistent foreground notification shows progress.
4. "Cancel import" button — stops the worker. Rows processed so far
   stay in the DB (idempotent; re-running will pick up where cancelled).

### 4.5 Layout (post-run summary)

1. "Import complete."
2. Numbers: "Imported 1,000 SMSes. Created 241 transactions. 18 are
   pending forex conversion. 69 unmatched financial SMSes need review."
3. "Review unmatched" button → list of unmatched SMSes with sender
   prefix `KOTAKB|AXISBK|ICICI|HDFC|SBI|UPI`. User can report gaps
   back to the developer (v1.1: via a "Report parser gap" button that
   copies sanitized details to clipboard).

### 4.6 Re-import

Re-running over an overlapping range is safe (§7.6 of
[ingestion-spec.md](./ingestion-spec.md)) — dedup by `android_sms_id`.
The summary shows "Imported 0 new messages" when all are duplicates.

## 5. Rerun parser on stored messages

### 5.1 Purpose

After an app update ships new parser rules, let the user re-classify
existing `sms_log` rows with the new parser. Explicit — never auto-runs.

### 5.2 Layout (pre-run)

1. Top bar: "Rerun parser" + back.
2. Explainer: "This will re-classify every SMS we've stored. Some
   transactions may be re-categorized. Budgets will recalculate."
3. Scope selection (radio):
   - Only UNMATCHED SMSes from financial senders (fast — fixes parser
     gaps without touching correctly-classified rows)
   - All SMSes (slow — full reparse)
4. Warning card: "This cannot be undone without reinstalling the
   previous app version. Consider exporting a CSV backup first (→
   Export)."
5. "Rerun parser" CTA.

### 5.3 Layout (during run)

Same shape as import: progress bar, live stats (reclassified count,
transactions created/removed), background toggle.

### 5.4 Layout (post-run summary)

1. "Reparse complete."
2. Numbers: "Reparsed N SMSes. M reclassified. K transactions created.
   L transactions removed."
3. "Review changes" button → a list of transactions whose
   `classification != classification_original`. User can spot-check.

### 5.5 Safety

- Wrapped in a single DB transaction per 500-row chunk.
- Checkpoint so a crash mid-reparse doesn't lose state.
- Cannot be triggered while a historical import is running (they use
  overlapping DB transactions; WorkManager queues them otherwise).

## 6. Purge non-financial SMSes

### 6.1 Purpose

Per [ingestion-spec.md](./ingestion-spec.md) §6.1 — privacy-driven
deletion of stored SMSes that don't contribute to budget tracking.

### 6.2 Data contract

Deletes from `sms_log`. A row is purge-eligible if:
- `classification IN (NON_FINANCIAL, OTP, BALANCE_ALERT)`, OR
- `classification = UNMATCHED` AND sender is not in the
  "financial-looking" pattern (`KOTAKB|AXISBK|ICICI|HDFC|SBI|UPI`).

Never purged:
- Anything that contributed to a `transactions` row (joined through
  `transaction_sms_sources`).
- `classification = UNMATCHED` from a financial-looking sender — those
  are the highest-priority parser gap signal per plan §2.

### 6.3 Layout

1. Top bar: "Purge non-financial SMSes" + back.
2. Age selector: "Delete older than [N] days." Default 90. Options
   30 / 60 / 90 / 180 / 365 / "all time."
3. Preview count: "This will delete 3,421 stored messages (≈ 47 MB)."
4. Warning card: "Your transactions, budgets, and categories are
   unaffected. You can re-import any deleted SMSes from the Android
   SMS inbox later."
5. "Delete" CTA, red/destructive styling.

### 6.4 Post-delete

- Summary: "Deleted 3,421 messages. Freed 47 MB."
- No undo within the app.
- Irreversible unless the user re-runs historical import covering the
  purged date range.

## 7. Alert thresholds

### 7.1 Purpose

Shortcut to the threshold-adjustment UI, which already lives in Budget
setup ([ui-spec.md](./ui-spec.md) §9).

### 7.2 Layout

A settings item that deep-links to Budget setup (current month) with
the thresholds section scrolled into view. No dedicated screen.

Rationale: thresholds are tightly tied to the budget limit and it's
confusing to edit them in isolation. Same screen, same save button.

## 8. Permissions shortcut

### 8.1 Purpose

One-tap navigation to Android's app-permissions page so the user can
grant/revoke SMS permissions without fumbling through Settings → Apps.

### 8.2 Layout

A settings row with current status ("SMS: Allowed" / "SMS: Denied").
Tap → `ACTION_APPLICATION_DETAILS_SETTINGS` intent for our package.

After returning to the app, re-query permission state and update the
dashboard banner accordingly.

## 9. About

### 9.1 Layout

Plain vertical list:
- **Version:** build name + code.
- **Parser rules version:** separate from app version; increments when
  parser rules change (helps support diagnoses).
- **DB schema version:** Room version.
- **Third-party licenses:** navigable list.
- **Privacy:** "All data stays on this device. No network calls except
  to `exchangerate-api.com` for foreign-currency conversion. No
  analytics, no telemetry, no ads."
- **Source / report issue:** link (if open-sourced; otherwise contact
  email).

## 10. Invariants

1. Seed data is shipped as static; never modified by app use. User
   additions/overrides land in separate rows (or the disabled flag for
   seeds).
2. Settings never corrupts the primary data path. Even
   mid-import/mid-reparse, the user can open Settings → Accounts and
   browse; writes that would conflict (editing a related account while
   a reparse holds the DB) are blocked with a "Please wait for {op}
   to finish" message.
3. Every destructive action has an explicit confirmation dialog. No
   undo mechanics in v1 MVP; confirmation is the safety net.
4. Every worker-triggering action (import, reparse, purge) is
   idempotent: triggering twice produces the same end state, not
   double-processing.

## 11. Testing contract

### 11.1 Accounts

```kotlin
@Test fun `add account inserts into accounts table`()
@Test fun `add account with UPI handles creates recipient_rules entries`()
@Test fun `edit account persists changes and updates linked rules`()
@Test fun `delete account cascade-deletes linked rules`()
@Test fun `delete account does NOT reclassify past transactions`()
@Test fun `duplicate (issuer, last4) is rejected with inline error`()
@Test fun `last4 validation rejects too-short input`()
```

### 11.2 Recipient rules

```kotlin
@Test fun `seed rules present after first launch`()
@Test fun `seed rule cannot be deleted`()
@Test fun `seed rule can be disabled`()
@Test fun `regex validation rejects invalid patterns`()
@Test fun `test-match preview matches correctly`()
@Test fun `literal rule matches exact string only`()
@Test fun `prefix rule matches startsWith only`()
```

### 11.3 Historical import

Covered in [ingestion-spec.md](./ingestion-spec.md) §13.
UI wrapper tested with:

```kotlin
@Test fun `count preview shown before import start`()
@Test fun `progress updates stream while worker runs`()
@Test fun `cancel button stops worker without data loss`()
@Test fun `summary screen shown on completion with correct counts`()
```

### 11.4 Rerun parser

```kotlin
@Test fun `scope=UNMATCHED only reparses unmatched rows`()
@Test fun `scope=ALL reparses everything`()
@Test fun `reparse cannot run while import is running`()
@Test fun `classification_original set on first override`()
@Test fun `classification_original NOT set when no change`()
```

### 11.5 Purge

```kotlin
@Test fun `preview count excludes SMSes contributing to transactions`()
@Test fun `preview count excludes UNMATCHED from financial senders`()
@Test fun `delete removes eligible rows only`()
@Test fun `delete leaves transactions intact`()
```

### 11.6 Regression: settings changes reflect in dashboard

After every settings action that affects classification:

```kotlin
@Test fun `adding self-transfer rule reclassifies on next reparse`()
@Test fun `enabling a disabled rule is honored on next parse`()
```

## 12. Testability posture

| Behavior | Category |
|---|---|
| DB operations for each screen | Strict TDD |
| Validation predicates (last4, pattern, amount) | Strict TDD |
| Worker trigger / cancel logic | Strict TDD (with fakes) |
| Dialog + permission intent launches | Integration |
| Material icons + layout | Snapshot |
| Regex-test preview visual | Snapshot + Compose UI |
| `ACTION_APPLICATION_DETAILS_SETTINGS` intent | Manual verify |

## 13. Coverage posture

- **ViewModels / DAOs for each settings screen: 100% line coverage.**
- **Validation predicates: 100% branch coverage.**
- **UI composables: snapshot-tested, no line-coverage goal** (per
  [ui-spec.md](./ui-spec.md) §18).

## 14. Decisions (resolved 2026-04-15)

- **Proactive reclassify on account add: yes.** After saving an account
  with associated UPI handles, immediately scan `sms_log` for matches
  and offer a one-tap "Reclassify 47 matching transactions" action.
  User can skip — but the offer is surfaced.
- **Auto-purge: no.** Purge is always user-initiated from Settings
  §6. Matches the "save everything" data-preservation stance.
- **"Report parser gap" feature: defer to v1.1.** v1 ships with the
  "Review unmatched" list in the import summary (§4.5); exporting
  sanitized details to clipboard is additive UX for later.
- **Rerun-parser dry-run: no.** Reparse is already reversible by
  reinstalling the prior app version. A dry-run would double
  implementation complexity for marginal value.
