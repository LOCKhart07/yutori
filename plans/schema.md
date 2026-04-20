# Yutori — Room Schema (v1)

Concrete schema for the v1 Android app. Companion to
[yutori-plan.md](./yutori-plan.md) §7. Open decisions from the
draft were resolved 2026-04-15 — see "Decisions" at the bottom.

## Design principles

1. **Save everything.** `sms_log` is the source of truth; every row the
   `BroadcastReceiver` sees lands here regardless of classification.
2. **`sms_log` → `transactions` is 1-to-N** (§12.3). One transaction may
   have multiple contributing SMSes (bank debit + gateway + merchant ack).
   Use a join table, not a comma-separated field.
3. **Prefer many small tables to fat JSON blobs.** Room is schema-typed;
   we pay migration cost once for normalization and gain queryability
   forever.
4. **Reserve enum values now**, even if behind a feature flag
   (`SELF_TRANSFER`, `INCOMING_CREDIT`). Retroactive re-classification via
   query is cheap; schema migrations are not.

## Tables

### `sms_log`

Raw capture layer. Every SMS lands here — financial or not — once the
`BroadcastReceiver` filter considers it "from a financial-looking sender."

| column             | type       | notes |
|--------------------|-----------|-------|
| `id`               | INTEGER PK | autoincrement |
| `android_sms_id`   | INTEGER    | **UNIQUE**. From `content://sms`; dedup key for §11.5 historical-import re-runs. |
| `sender`           | TEXT       | e.g. `VK-HDFCBK`, raw from SMS |
| `body`             | TEXT       | verbatim |
| `received_at_ms`   | INTEGER    | epoch ms (not just date — §11.5 pattern analysis) |
| `classification`   | TEXT       | one of the classification enum; `UNMATCHED` if parser gave up |
| `pattern_matched`  | TEXT?      | name of the parser rule that fired (e.g. `kotak_cc_spend`); null if UNMATCHED |
| `source`           | TEXT       | `SMS_REALTIME` / `SMS_IMPORT` / `STATEMENT_PDF` / `STATEMENT_CSV` / `MANUAL` (§10.1 forward-compat) |
| `reparsed_at_ms`   | INTEGER?   | when the parser last re-ran over this row (after rule updates) |

**Indexes:** `android_sms_id` (unique), `received_at_ms`, `classification`.

### `transactions`

Derived budget layer. Only rows that affect *something* budget-relevant —
spend, refund, bill payment, incoming credit. One row per logical event,
even if multiple SMSes contributed.

| column                | type       | notes |
|-----------------------|-----------|-------|
| `id`                  | INTEGER PK | |
| `classification`      | TEXT       | CC_TRANSACTION / UPI_PAYMENT / CC_BILL_PAYMENT / … |
| `classification_original` | TEXT?  | parser's verdict before any user override; null if never overridden (decision 1). |
| `inr_amount`          | REAL       | always INR, always positive. Sign comes from classification + `budget_effect`. |
| `original_amount`     | REAL?      | null if same as `inr_amount` |
| `original_currency`   | TEXT       | default `INR` |
| `exchange_rate`       | REAL?      | rate used when converting (§4.5, §11.1) |
| `rate_source`         | TEXT?      | `exchangerate-api.com` / `manual` / `pending` |
| `merchant`            | TEXT?      | extracted merchant or VPA |
| `merchant_key`        | TEXT?      | lowercased/trimmed for grouping (§5 categorization) |
| `category`            | TEXT       | one of §5's 11 categories; defaults to `Uncategorized` |
| `account_id`          | INTEGER?   | FK `accounts.id` if resolved (§12.2); null otherwise |
| `last4`               | TEXT?      | masked account/card number as extracted; survives even if `account_id` not resolved |
| `issuer`              | TEXT?      | derived bank name ("Kotak", "Axis") |
| `occurred_at_ms`      | INTEGER    | from the bank SMS (may differ from `received_at_ms`) |
| `month_key`           | TEXT       | `YYYY-MM`, UTC. Indexed. |
| `budget_effect`       | TEXT       | `SPEND` / `REFUND` / `INCOME` / `DROP`. Precomputed to keep dashboard queries one-pass. |
| `is_manual_entry`     | INTEGER    | 0/1 (§7 forward-compat even though manual entry is ruled out in v1) |
| `manually_adjusted`   | INTEGER    | 0/1 — did the user change classification/category post-hoc? |
| `notes`               | TEXT?      | user-added note (optional) |

**Indexes:** `month_key`, `account_id`, `last4`, `classification`,
`merchant_key`.

When the user overrides, set `classification_original` to whatever the
parser produced and then write the new value to `classification`. When
`manually_adjusted = 0` the `_original` column stays null to save space.

### `transaction_sms_sources`

Join table — links a `transactions` row to contributing `sms_log` rows.
The §12.3 dedup flow is the whole reason this table exists.

| column             | type       | notes |
|--------------------|-----------|-------|
| `transaction_id`   | INTEGER    | FK → `transactions.id`, ON DELETE CASCADE |
| `sms_log_id`       | INTEGER    | FK → `sms_log.id`, ON DELETE RESTRICT |
| `role`             | TEXT       | `BANK_DEBIT` / `GATEWAY` / `MERCHANT_ACK` / `CC_PAYMENT_RECEIPT` / `DUPLICATE_NOTIF` |
| `is_primary`       | INTEGER    | 0/1 — the winning source per §12.3 preference order |

**Primary key:** `(transaction_id, sms_log_id)`.
**Indexes:** `sms_log_id` (reverse lookup).

### `accounts`

User's own accounts (§12.2). Registered once in Settings; used by the
parser/classifier to reclassify self-transfers.

| column             | type       | notes |
|--------------------|-----------|-------|
| `id`               | INTEGER PK | |
| `kind`             | TEXT       | `SAVINGS` / `CREDIT_CARD` / `INVESTMENT` / `OTHER` |
| `issuer`           | TEXT       | display name ("Kotak Bank") |
| `last4`            | TEXT       | masked identifier as it appears in SMS (`XX0000`, `x3333`) |
| `display_name`     | TEXT?      | user-friendly label ("Primary salary a/c") |
| `is_default_spend` | INTEGER    | 0/1 — used to resolve ambiguous SMSes |
| `created_at_ms`    | INTEGER    | |

**Seed from the feasibility dataset** (accounts memo, local-only):
Kotak `XX0000`, Axis `XX2222`, Vasai Janata `006666`, Axis CC `XX1111`,
Axis CC `XX4444`, Kotak CC `x3333`, Paytm Money `X5555`.

### `recipient_rules`

Single registry for §12.2 (self-transfer) + §12.4 (CC-bill middlemen) +
any future "reclassify UPI by recipient" use case.

| column                | type       | notes |
|-----------------------|-----------|-------|
| `id`                  | INTEGER PK | |
| `pattern`             | TEXT       | regex or literal to match against recipient VPA/merchant |
| `pattern_kind`        | TEXT       | `LITERAL` / `REGEX` / `PREFIX` |
| `reclassify_as`       | TEXT       | target classification (`CC_BILL_PAYMENT`, `SELF_TRANSFER`, …) |
| `account_id`          | INTEGER?   | FK `accounts.id` if the rule ties a VPA to a specific own account |
| `source`              | TEXT       | `SEED` (shipped with app) / `USER` / `LEARNED` (heuristic suggestion accepted, #64 part 1) / `AI` (AI-extracted then accepted, #64 part 2) |
| `note`                | TEXT?      | human label: "CRED CC bill payments" |
| `is_enabled`          | INTEGER    | 0/1 |

**Seed rules** (shipped): `cred.club@axisb[@¡]` → CC_BILL_PAYMENT, Paytm
CC rails → CC_BILL_PAYMENT, PhonePe CC → CC_BILL_PAYMENT, HDFC CC via
Google Pay → CC_BILL_PAYMENT. Own-UPI handle pattern
`examplename-*@oksbi` → SELF_TRANSFER, account Axis `XX2222`.

### `budgets`

One row per month. Carry-over is computed on read, not stored — the
underlying transactions are the source of truth; caching invites drift.

| column               | type       | notes |
|----------------------|-----------|-------|
| `month_key`          | TEXT PK    | `YYYY-MM` |
| `limit_inr`          | REAL       | user-set monthly limit |
| `threshold_warn_pct` | INTEGER    | default 80, user-adjustable (§6) |
| `created_at_ms`      | INTEGER    | |
| `updated_at_ms`      | INTEGER    | |

Carry-over is computed on read from prior months' transactions — not
cached on this row (decision 2).

### `budget_alert_state`

Which progressive thresholds have fired this month (§6 — each fires at
most once).

| column         | type       | notes |
|----------------|-----------|-------|
| `month_key`    | TEXT       | FK `budgets.month_key` |
| `threshold_pct`| INTEGER    | 50, 80, 100, 110, 120, … |
| `fired_at_ms`  | INTEGER    | |

**Primary key:** `(month_key, threshold_pct)`.

## Not-a-table: enums & config

Keep these in Kotlin code, not DB tables — they change with parser
releases, not with data.

- `Classification` — sealed class / enum of the 11 values (CC_TRANSACTION,
  CC_BILL_PAYMENT, UPI_PAYMENT, DEBIT_CARD, ATM_WITHDRAWAL, REFUND,
  CASHBACK, INCOMING_CREDIT, OTP, BALANCE_ALERT, NON_FINANCIAL,
  SELF_TRANSFER, UNMATCHED).
- `Category` — enum of §5's 11 values.
- `BudgetEffect` — enum (SPEND, REFUND, INCOME, DROP). Derived from
  classification but cached on `transactions` for single-pass dashboard
  reads.

## Decisions (resolved 2026-04-15)

1. **`classification_original` audit field — YES.** Preserve the parser's
   verdict when the user overrides. Null when `manually_adjusted = 0`.
2. **Carry-over caching — NO.** Compute from prior months' transactions on
   read. No `budgets.carry_over_inr` column.
3. **`sms_log.budget_included` flag — DROP.** Answer is derivable from
   `transaction_sms_sources` via `LEFT JOIN`. One source of truth; no
   coherency risk.
4. **`SELF_TRANSFER` — standalone classification.** Not a flag on an
   existing classification. Keeps drill-down queries simple.
5. **Paired-transfer linking — v2.** No `paired_transaction_id` column in
   v1. Both legs of a self-transfer classify as `SELF_TRANSFER`; they
   already net to zero without pairing.
6. **Per-currency budgets — NO.** Single INR monthly limit per §6.
   Foreign-currency transactions convert to INR and count against the
   single limit.

## Migration posture

Room entity version starts at **1**. Every schema change ships with a
Room migration. No `fallbackToDestructiveMigration` — the user's data
is the point.

## Forward-compat fields present from day one

Explicitly listed here so they don't look like dead code in v1:

- `sms_log.source` (§10.1 statement import)
- `sms_log.reparsed_at_ms` (re-running parser after rule updates)
- `transactions.is_manual_entry` (§9 ruled out for v1 but reserved)
- `transactions.manually_adjusted` (§12.1 payback action, user overrides)
- `accounts.is_default_spend` (disambiguation when SMS lacks last4)
- `recipient_rules.source = LEARNED` — heuristic suggestions accepted by the user (#64 part 1, shipped)
- `recipient_rules.source = AI` — on-device LLM extraction accepted by the user (#64 part 2, `plans/ai-rules-spec.md`)
