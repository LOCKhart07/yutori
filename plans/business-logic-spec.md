# SpendWise — Business Logic Specification (v1)

The layer that converts labeled `sms_log` rows into `transactions`,
maintains budget state, fires alerts, and handles all the
reclassification logic the raw parser deliberately avoids.

This is where the plan's hard-earned design decisions live in code:
§4.1 (CC double-count), §4.4 (refunds), §4.5 (forex), §6 (budget +
alerts), §12.1–§12.4 (open-question handling).

Companion docs: [spendwise-plan.md](./spendwise-plan.md),
[schema.md](./schema.md), [parser-spec.md](./parser-spec.md),
[ingestion-spec.md](./ingestion-spec.md).

---

## 1. Layer boundaries

Upstream inputs:
- A new `sms_log` row (from [ingestion-spec.md](./ingestion-spec.md) §5 or §7).
- Its raw parser result (classification, amount, merchant, last4, etc.).

Downstream outputs:
- Zero, one, or more `transactions` rows (plus `transaction_sms_sources`
  join entries).
- Updated `budget_alert_state` (if a threshold is crossed).
- A notification dispatch (if an alert fires).

This layer **owns these invariants**:

1. Every spend-like `sms_log` row either contributes to a transaction,
   or has a recorded reason for not doing so.
2. Budget math is always self-consistent: `sum(transactions WHERE
   budget_effect=SPEND) - sum(REFUND) + carry_over == effective_spend`.
3. Alert thresholds fire at most once per month per threshold.
4. No dedup flow produces a `transactions` row with `inr_amount` that
   doesn't match at least one contributing `sms_log`.

## 2. Classifier — from `ParseResult` to final `Classification`

The parser's output is intentionally blind to user config. The
classifier reads the raw result and consults DB state (`accounts`,
`recipient_rules`) to possibly override.

### 2.1 Types

```kotlin
data class SmsLogRow(
    val id: Long,
    val sender: String,
    val body: String,
    val receivedAtMs: Long,
    val parserResult: ParseResult,
)

data class ClassifiedSms(
    val smsLogId: Long,
    val finalClassification: Classification,
    val budgetEffect: BudgetEffect,
    val amount: Double?,            // INR for domestic, original for forex
    val currency: String,
    val merchant: String?,
    val merchantKey: String?,       // normalized for category lookup
    val last4: String?,
    val accountId: Long?,           // resolved FK to `accounts.id` if found
    val category: Category,
    val classificationOriginal: Classification?,  // parser's verdict if overridden
)

enum class BudgetEffect {
    SPEND,          // counts against the month's spend total
    REFUND,         // offsets spend in the month it was received (§4.4)
    INCOME,         // shown separately; does not affect spend budget
    DROP,           // explicitly excluded from budget (OTP, alert, etc.)
}
```

### 2.2 Classifier pipeline

```
classify(row: SmsLogRow): ClassifiedSms

1. Start with `raw = row.parserResult.classification`.
2. If raw == UNMATCHED → emit classification=UNMATCHED, effect=DROP. Return.
3. Run `accountResolver` — try to resolve `last4 → accounts.id`.
4. Run `recipientRuleMatcher` — check if merchant/VPA matches any
   `recipient_rules` entry. If so, apply its `reclassify_as`.
5. Apply self-transfer heuristic (§2.3) — may upgrade to SELF_TRANSFER.
6. Compute `budgetEffect` from final classification (§2.4).
7. Compute `merchantKey` (lowercased, trimmed, punctuation-stripped).
8. Compute `category` via `categoryFor(merchantKey, classification)` (§3).
9. If final classification != raw → stamp `classificationOriginal = raw`.
10. Emit ClassifiedSms.
```

### 2.3 Self-transfer heuristic

A transaction becomes `SELF_TRANSFER` if **either**:

- The raw classification is `UPI_PAYMENT` AND the recipient `merchant`
  (a VPA) matches an entry in `recipient_rules` with
  `reclassify_as = SELF_TRANSFER`.
- The raw classification is `INCOMING_CREDIT` AND the source
  `merchant` matches an entry the same way.

The `recipient_rules` table is seeded at install with known patterns
(e.g. `examplename-*@oksbi` from the feasibility dataset, if the
user confirms during onboarding). Users add entries in Settings as
they discover their own accounts.

**Not used in v1:** matching by `last4 → accounts.id` on both sides
simultaneously (i.e. "debit from my account X AND credit to my account Y
within T seconds = self-transfer"). That's the paired-transfer linking
deferred to v2 per schema.md Decision 5.

### 2.4 Classification → budget_effect table

| Final classification | Budget effect | Notes                                       |
|----------------------|--------------|---------------------------------------------|
| CC_TRANSACTION       | SPEND        | §4.1 — the actual consumption event        |
| UPI_PAYMENT          | SPEND        |                                             |
| DEBIT_CARD           | SPEND        |                                             |
| ATM_WITHDRAWAL       | SPEND        | §4.3, Cash category, shown distinctly      |
| CC_BILL_PAYMENT      | DROP         | §4.1 — already counted at txn time         |
| REFUND               | REFUND       | §4.4 — applied to month received           |
| CASHBACK             | DROP         | §4.2 — "not real income, distorts tracking" |
| INCOMING_CREDIT      | INCOME       | Shown separately; does not offset spend    |
| SELF_TRANSFER        | DROP         | §12.2                                       |
| OTP                  | DROP         |                                             |
| BALANCE_ALERT        | DROP         |                                             |
| NON_FINANCIAL        | DROP         |                                             |
| UNMATCHED            | DROP         | Visible in debug view only                 |

This mapping is **derived, not configurable**. The classification is the
semantic fact; the effect is a consequence of plan policy.

## 3. Category assignment

### 3.1 Sources of truth (ordered)

1. **Parser-assigned category**, if present. Currently only
   `UPI_PAYMENT` (Kotak) and `REFUND` (Blinkit) get a default category
   from the parser. Respect it.
2. **User-defined merchant mapping.** A future `merchant_categories`
   table (reserved in schema.md forward-compat but not created in v1
   MVP — see §3.4). Until then: no user overrides.
3. **Keyword matching on `merchantKey`** per plan §5. A static map in
   code, seeded with the merchants observed in the feasibility dataset.
4. **Fallback:** `UNCATEGORIZED` if merchant is a known cross-cutting
   platform (Amazon, Flipkart, Blinkit, Zepto, Swiggy Instamart,
   BigBasket, DMart); `OTHER` otherwise; `null` if no merchant extracted.

### 3.2 Seed keyword map

Derived from the feasibility dataset. One merchant keyword per line,
matched case-insensitively against `merchantKey`:

```
FOOD_DINING:      zomato, swiggy (restaurants), dominos, mcdonalds,
                  starbucks, subway, kfc, pizza hut, faasos, kawi,
                  café, cafe, restaurant, bakery, eatclub
GROCERIES:        reliance fresh, big bazaar, spencers
TRAVEL_TRANSPORT: uber, ola, rapido, irctc, redbus, bharat petr,
                  hp petrol, indian oil, metro, autorickshaw
SHOPPING:         myntra, ajio, nykaa, vijay sales, croma, swarovski,
                  max fashion, reliance digital, tanishq
BILLS_UTILITIES:  airtel, jio, vi, bsnl, mseb, electricity, water,
                  gas bill, broadband, tata power, adani, mahadiscom
HEALTH:           apollo, netmeds, 1mg, pharmeasy, practo, medplus
ENTERTAINMENT:    netflix, hotstar, prime video, spotify, pvr, inox,
                  bookmyshow, claude, github, real-debrid, f1
UPI_TRANSFER:     (assigned by parser for Kotak UPI; handled there)
CASH:             (assigned by ATM_WITHDRAWAL classification)
UNCATEGORIZED:    amazon, flipkart, blinkit, zepto, swiggy instamart,
                  bigbasket, dmart
```

**Match semantics:**
- `contains(merchantKey, keyword.lowercased())`.
- First match wins.
- Kotak CC 5-char tags (`VINOD`, `ZOMAT`, `DOMIN`, etc.) are expanded
  before matching via a small lookup:
  ```
  ZOMAT → zomato
  DOMIN → dominos
  STATI → (unknown, stays as STATI → OTHER)
  KAWI  → kawi (food)
  VINOD → (unknown, stays as VINOD → OTHER)
  ```
  The lookup is shipped with the app and extended as new truncations
  are observed. It's keyed off the exact 5-char tag.

### 3.3 Conflict policy

If the raw classification is a bill-payment or admin-like thing
(CC_BILL_PAYMENT, BALANCE_ALERT, etc.), we do NOT assign a category —
category is `null` for DROP-effect rows. Category only applies to
money-moving events (SPEND, REFUND, INCOME).

### 3.4 User override (v1.1 scope, not v1 MVP)

v1 MVP omits the "change category" UX entirely. Categories shown in the
dashboard are parser/classifier output, period. If the user disagrees,
they live with it or wait for v1.1.

Justification: building "edit transaction" requires thinking about audit
trails, multi-row edit, category-color assignment, and re-aggregation
of the month's totals. All real work that distracts from getting the
core loop right. Ship without it, see how painful it actually is, add
in v1.1.

**Forward-compat reserved in schema:** `transactions.manually_adjusted`,
`transactions.classification_original`. These exist in v1 but are only
written by the future-v1.1 override UX.

## 4. Transaction creation (§12.3 merge flow)

Most `sms_log` rows map 1:1 to `transactions` rows. Some do not.

### 4.1 Single-SMS flow (common case)

On receiving a `ClassifiedSms`:

```
if classified.budgetEffect == DROP:
    return  # no transaction row
if classified.budgetEffect == SPEND, REFUND, or INCOME:
    candidate = findDedupCandidate(classified)  // §4.2
    if candidate != null:
        mergeInto(candidate, classified)
    else:
        createTransaction(classified)
```

### 4.2 Dedup candidate search

Multi-party duplicates (§12.3 in the plan): the same Rs 100 property
tax payment appears as an Axis collect request, an ICICI eazypay
confirmation, and a VVCMC receipt. We store all three in `sms_log`, but
only one `transactions` row.

**Candidate rule:** a `sms_log` row matches an existing `transactions`
row if **all** of:

1. `|inr_amount - existing.inr_amount| < 0.5` (absolute, not percent —
   avoids false matches at large amounts).
2. `|receivedAtMs - existing.occurredAtMs| < 300_000` (5 minutes).
3. `classified.budgetEffect == existing.budget_effect` (can't merge a
   REFUND with a SPEND).
4. At least one of:
   - Same `last4`, OR
   - `merchantKey` overlap (≥ 1 token in common, e.g.
     `VVCMC ONLINE PROPERTY TAX` and `VVCMC` share `vvcmc`).

If exactly one candidate matches → merge. If multiple match → pick the
one with matching `last4` first, then earliest `occurredAtMs`. If none
match → create new.

**Why 5 minutes, not 1:**
- Gateway→merchant ack can lag by 2–3 minutes (observed in dataset).
- Real distinct transactions within 5 minutes with the same amount AND
  overlapping merchant tokens are vanishingly rare for personal spending.

**Why not fuzzy amount match:**
- A gateway may add a convenience fee: bank shows Rs 100.50, gateway
  shows Rs 100.00. A 0.5 absolute tolerance handles fee rounding while
  rejecting distinct same-approximate-amount spends.

**Known over-merge edge case (observed 2026-04-15):**
Two legitimately distinct UPI payments from the same account for the
same amount, to different VPAs but within the 5-minute window, can get
merged into a single transaction. Example observed on-device:

- `Sent Rs.999.00 from Kotak Bank AC X0000 to issuertest@okaxis …`
- `Sent Rs.999.00 from Kotak Bank AC X0000 to freshtest@okaxis …`

Both merged into one transaction because rule 4 was satisfied by
matching `last4 = 0000`. Rule 4's OR is too permissive when `last4` is
shared across many UPIs from the same account.

**Proposed refinement (v1.1):** tighten rule 4 to require
`same last4 AND merchant-token overlap`, OR `same last4 AND one of the
merchants is null`, OR `merchant-token overlap AND both non-null`. I.e.
`last4` alone isn't sufficient when both merchants are present and
differ. Testing notes to keep in mind when refining:

- The §12.3 VVCMC case still needs to pass: gateway has full
  "VVCMC ONLINE PROPERTY TAX ACCOUNT" but the bank-side debit has
  merchant = null (the savings-debit SMS only names the counterparty
  as a VPA, not a merchant). So "`last4` alone OR merchant overlap"
  remains necessary for that case — refinement needs to handle
  "one side null" explicitly.
- Lowering the time window from 5 min to 2 min would also help but
  risks missing real-world gateway lag (Paytm seen at ~3 min tail).

Defer this refinement to v1.1 and track it in the plan.

### 4.3 Merge semantics

When an existing transaction absorbs a new SMS:

1. Add a row to `transaction_sms_sources` with `role` computed per §4.4.
2. Determine if the new SMS is "more authoritative" (per §4.5). If yes:
   flip the existing `is_primary` flag off, mark the new source as
   primary.
3. Rewrite `transactions.merchant` + `merchantKey` if the new source
   is primary AND its merchant is non-null AND longer/more descriptive
   than the current one.
4. Never change `inr_amount`, `occurred_at_ms`, or `classification`
   during a merge — those follow the primary source only, and are
   already set at creation time.

### 4.4 Role assignment

| Source classification + sender pattern                  | Role                |
|---------------------------------------------------------|---------------------|
| CC_TRANSACTION from a CC-issuing bank sender            | BANK_DEBIT         |
| UPI_PAYMENT from a savings bank sender                  | BANK_DEBIT         |
| UPI_PAYMENT from a gateway (ICICI eazypay, Razorpay)    | GATEWAY            |
| CC_BILL_PAYMENT from CC issuer (`Payment received…`)    | CC_PAYMENT_RECEIPT |
| CC_BILL_PAYMENT from savings bank (`Debit … CC Payment`)| BANK_DEBIT         |
| Any SMS from the payee (merchant ack)                   | MERCHANT_ACK       |
| Anything else                                           | DUPLICATE_NOTIF    |

### 4.5 Primary-source preference order (per §12.3)

1. BANK_DEBIT (most authoritative about money leaving)
2. GATEWAY (knows the payment succeeded but doesn't see the funding account)
3. CC_PAYMENT_RECEIPT (for bill-payment transactions)
4. MERCHANT_ACK (post-facto receipt)
5. DUPLICATE_NOTIF (lowest — a duplicate notification from the same
   bank, e.g. SBI notifying about an Axis-routed UPI credit)

The primary source's `sms_log.id` is what `transactions` canonically
references; its `inr_amount` and `occurred_at_ms` are what appear on
the row.

### 4.6 CC bill payment double-count

Already handled by the classifier: any SMS whose final classification
is `CC_BILL_PAYMENT` has `budgetEffect = DROP`, so it never adds to the
month's spend. The underlying CC transactions were counted at spend
time (§4.1).

**Double-safety:** if a merge accidentally flips a `SPEND` transaction
into a `CC_BILL_PAYMENT` (by absorbing an incoming CC_BILL_PAYMENT
SMS), that's a bug — merge rules only combine same-effect rows (§4.2
rule 3). This is asserted in tests.

## 5. Forex handling

For a `ClassifiedSms` where `currency != "INR"`:

1. Create the `transactions` row with:
   - `original_amount = classified.amount`
   - `original_currency = classified.currency`
   - `inr_amount = null` (cannot commit without rate)
   - `exchange_rate = null`
   - `rate_source = "pending"`
2. Enqueue a `ForexConversionWorker` with the transaction id.
3. Worker:
   - Calls `exchangerate-api.com` free tier for the `currency → INR`
     rate (§11.1).
   - On success: writes `inr_amount`, `exchange_rate`,
     `rate_source = "exchangerate-api.com"`, and touches
     `updated_at_ms`.
   - On failure: leaves the row pending. Retries on next app launch
     and at app-resume. Shows a UI badge ("1 transaction pending
     conversion") linking to the affected rows.
4. Budget calculations **exclude** transactions with `inr_amount = null`
   from spend totals — they show separately in the dashboard as
   "pending FX."

### 5.1 Rate caching

Don't call the API per transaction. Cache rates per (date, currency)
for 24 hours in a small table or shared-preferences JSON blob. Forex
rates change by <0.5% intra-day for major currencies; the cache keeps
us well under the 1,500/month free-tier limit.

### 5.2 Retry and backoff

- On HTTP 429 (quota): back off 1 hour, then 6 hours, then daily.
  Until then, pending conversions stay pending.
- On transient network error: exponential 30 s → 5 min → 1 hr.
- On API returning a rate of 0 or NaN: treat as failure, don't write.

### 5.3 Manual override

v1.1 scope — user can enter a specific rate for a specific transaction.
Stored as `exchange_rate` with `rate_source = "manual"`.

## 6. Budget computation

### 6.1 Effective budget for month M

```
effectiveBudget(M)
  = budgets[M].limit_inr + carryOver(M)
```

where

```
carryOver(M)
  = sum over all months P < M of (
        budgets[P].limit_inr
      - monthSpend(P)
      + monthRefunds(P)
    )
```

Computed on read, not cached (per schema.md Decision 2). Acceptable
performance even for 5+ years of data with proper `month_key` indexing.

### 6.2 Monthly spend

```
monthSpend(M)
  = sum of transactions.inr_amount
    where month_key = M
      and budget_effect = SPEND
      and inr_amount IS NOT NULL    // exclude pending FX
```

### 6.3 Monthly refunds

```
monthRefunds(M)
  = sum of transactions.inr_amount
    where month_key = M
      and budget_effect = REFUND
      and inr_amount IS NOT NULL
```

### 6.4 Carry-over sign convention

A **positive** carry-over means you underspent previous months
collectively — you can spend more this month without guilt. A **negative**
carry-over means prior overspending; your effective budget is lower.

The dashboard should display both additions and subtractions
prominently. No absolute-value tricks.

### 6.5 Month boundaries

- `month_key = YYYY-MM` computed from `occurred_at_ms` in **device
  local time**. Rationale: a user in IST at 23:55 on the last of the
  month spending Rs 500 considers that spend "this month"; using UTC
  would push it into next month.
- DST transitions are not a concern in India. If the app is ever used
  in a DST-observing timezone, reconsider.
- Changing device timezone mid-month does not re-bucket transactions.
  `month_key` is stored at insert time and immutable.

### 6.6 First month edge case

If no prior months have `budgets` rows, `carryOver = 0` (not `null`).

## 7. Alert threshold state machine

### 7.1 Thresholds

Per plan §6:
- 50% (fixed, informational)
- 80% default warning (user-adjustable via `budgets.threshold_warn_pct`)
- 100% critical (fixed)
- Every subsequent +10% over 100% (110%, 120%, …) — dynamic

### 7.2 Firing rules

Per plan §6:
- Each threshold fires at most once per month.
- Fires on transitions *upward through* the threshold, not when reached
  exactly.
- Does **not** re-arm if spend falls below after a refund.

### 7.3 Algorithm

After any transaction insert or update that changes a month's
`monthSpend(M)`:

```
effective = effectiveBudget(M)
spend     = monthSpend(M)
percent   = (spend / effective) * 100

for threshold in [50, warnPct, 100, 110, 120, 130, ...]:
    if percent >= threshold AND not alreadyFired(M, threshold):
        fireAlert(M, threshold, spend, effective)
        recordFired(M, threshold, now())
```

Upward thresholds beyond 100% are generated on demand: the loop
iterates through `110, 120, …` until `threshold > percent`.

### 7.4 Fire semantics

1. Insert into `budget_alert_state (month_key, threshold_pct,
   fired_at_ms)`.
2. Dispatch a `Notification`:
   - Title: "Budget: 50% used" / "Budget: Approaching limit" /
     "Budget: Over limit" / "Budget: 120% of budget"
   - Body: current spend, effective budget, remaining or overage.
   - Tap action: open the dashboard.
3. Notifications are `INTERRUPTION_FILTER_PRIORITY` low. They don't
   make sound; they appear silently in the shade.

### 7.5 Recompute on refund

A refund received mid-month reduces `monthSpend`. A threshold that
already fired stays fired (per plan §6 "threshold fires once on the way
up, not on the way down"). No action needed — the loop in §7.3 will
simply not re-fire.

### 7.6 Recompute on late-arriving transactions

A historical import or reparse may add transactions to a past month.
If the past month's spend now crosses a threshold that never fired:

- The current month's alerts are unaffected.
- We do not fire alerts retroactively. Showing "You were over budget
  in February 2026" when you're reading the app in April 2026 is
  noise. The dashboard reflects the correct numbers, which is enough.
- `budget_alert_state` for past months is updated only to mark
  `fired_at_ms = now()` without dispatching a notification, so a
  subsequent spend change won't fire either.

## 8. Reparse — downstream of ingestion §9

When the ingestion layer's reparse worker updates `sms_log`
classifications in bulk, the business-logic layer must reconcile
`transactions`:

1. For each `sms_log` row whose classification changed:
   - Find its `transaction_sms_sources` entry (if any).
   - If the new classification's `budgetEffect` is DROP and the old
     was SPEND/REFUND/INCOME: remove this source from the transaction.
     If it was the only source, delete the transaction.
   - If the new classification's `budgetEffect` is SPEND/REFUND/INCOME
     and the old was DROP: create a transaction (may trigger dedup
     merge per §4.2).
   - If both old and new are SPEND/REFUND/INCOME but classification
     differs: update `transactions.classification`, recompute
     `budget_effect` and `category`. The transaction's existence doesn't
     change, only its labels.
2. Recompute `budget_alert_state` per §7.6 (silent reconciliation, no
   notifications).

Reparse is a potentially large operation. It must:
- Run in a single DB write transaction per month or per 500 rows,
  whichever is smaller.
- Show progress to the user (toast + notification).
- Be interruptible and resumable (checkpoint on `sms_log.id`).

## 9. Invariants (enforced by tests)

1. **Effect consistency.** For every row in `transactions`:
   `budget_effect == mapClassificationToEffect(classification)`.
2. **Source non-empty.** Every row in `transactions` has ≥1 row in
   `transaction_sms_sources`. Orphans are a bug.
3. **Exactly one primary.** For every `transactions.id`, exactly one
   `transaction_sms_sources.is_primary = 1`.
4. **Forex completeness.** If `currency != 'INR'`, then
   `original_amount IS NOT NULL AND original_currency IS NOT NULL`.
5. **INR completeness.** If `rate_source != 'pending'`, then
   `inr_amount IS NOT NULL AND exchange_rate IS NOT NULL`.
6. **Threshold idempotency.** `(month_key, threshold_pct)` is unique in
   `budget_alert_state`. Inserting a duplicate is a no-op (or an error;
   calling code checks `alreadyFired` first).
7. **Month-key immutability.** Once set on a transaction, never rewritten.
8. **Monotonic reparse.** Reparse of a `sms_log` row never produces a
   classification that's more ambiguous than the current one (e.g.
   `CC_TRANSACTION → UNMATCHED` is forbidden — that's a parser
   regression, not a valid classification update).

## 10. Worked examples

### 10.1 CRED bill payment (single SMS, straightforward)

Input: `Sent Rs.1000.00 from Kotak Bank AC X0000 to cred.club@axisb
on 01-01-26`.

- Parser: `ParseResult(CC_BILL_PAYMENT, 1000, merchant="cred.club@axisb",
  last4="0000")` — the parser's middleman detection fires.
- Classifier: no further override; `budgetEffect = DROP`.
- Transaction layer: `budgetEffect = DROP`, no transaction row created.
- Budget: unaffected.

### 10.2 Axis CC bill payment pair (two SMSes, merge)

Two SMSes, same event:
1. Axis savings debit: `Debit INR 1000.00\nAxis Bank A/c XX2222\n…\n
   CreditCard Payment XX 1111` — parser → `CC_BILL_PAYMENT`.
2. Axis CC: `Payment of INR 1000 has been received towards your Axis
   Bank Credit Card XX1111 on 01-01-26` — parser → `CC_BILL_PAYMENT`.

- Both have `budgetEffect = DROP` → no transaction rows, no merge,
  no budget effect. Both stored in `sms_log`.

### 10.3 VVCMC property tax (multi-party merge)

Three SMSes within 10 minutes:
1. `JK-AXISBK-S`: VVCMC collect request (parser → UPI-request-like
   pattern → not matched → UNMATCHED → DROP).
2. `JD-ICICIT-S`: eazypay confirmation (parser → UPI_PAYMENT,
   `Rs. 100, merchant=VVCMC ONLINE PROPERTY TAX`).
3. `VM-VVMCDM-S`: VVCMC "Thank you for payment" (parser → UNMATCHED →
   DROP).

Transaction flow:
- #2 creates a new transaction: SPEND, 100, VVCMC PROPERTY TAX.
- #1 and #3 are UNMATCHED → no transaction; they stay in sms_log.

(If we later add a rule that matches VVCMC ack → NON_FINANCIAL, the
flow stays the same. If we add a rule that matches VVCMC ack → REFUND,
the dedup candidate in §4.2 rule 3 prevents merging it with the
SPEND transaction. New transaction row with REFUND effect.)

### 10.4 Salary + P2P payback (§12.1)

- ACME CORP NEFT credit: `Rs. 50000.00 credited … from beneficiary
  ACME CORP` → parser → INCOMING_CREDIT → classifier →
  `budgetEffect = INCOME` → transaction created, does not affect spend
  budget.
- Friend pays back Rs 100 via UPI: `Received Rs.100.00 in your Kotak
  Bank AC X0000 from 9999999999@yescred` → parser → INCOMING_CREDIT →
  classifier → `budgetEffect = INCOME` → transaction created.

Per plan §12.1, both are currently bucketed into INCOME. Distinguishing
them requires the salary-sender whitelist / payback UX — not in v1.

### 10.5 Self-transfer Kotak→Axis (registered)

- Kotak UPI debit: `Sent Rs.100 from Kotak Bank AC X0000 to
  examplename-4@oksbi on 01-01-26` → parser → UPI_PAYMENT →
  classifier consults `recipient_rules`, finds
  `examplename-*@oksbi → SELF_TRANSFER` → final classification:
  SELF_TRANSFER → `budgetEffect = DROP` → no transaction for budget.
- Axis A/c credit (same event, minutes later): matched similarly as
  SELF_TRANSFER → DROP.

Both land in `sms_log` for audit / card drill-down, neither affects
the budget.

### 10.6 Foreign currency subscription

- Axis CC: `Spent USD 10.00\nAxis Bank Card no. XX1111\n…\nGITHUB, INC`
  → parser → CC_TRANSACTION, amount=10.00, currency=USD, last4=1111.
- Classifier → `budgetEffect = SPEND`.
- Transaction row created with `inr_amount = null`, `rate_source =
  pending`.
- `ForexConversionWorker` runs, fetches rate (USD→INR ≈ 83.5 on
  2026-03-03), writes `inr_amount = 835.00`,
  `exchange_rate = 83.50`, `rate_source =
  exchangerate-api.com`.
- Threshold check runs with the new spend total; may or may not fire.

## 11. Testing contract

### 11.1 Classifier tests

One test per row in the §2.4 classification→effect table. Each verifies
the effect derivation is correct.

```kotlin
@Test fun `CC_TRANSACTION yields SPEND effect`()
@Test fun `CC_BILL_PAYMENT yields DROP effect`()
...
```

Plus tests for the classifier pipeline:

```kotlin
@Test fun `UPI_PAYMENT to registered own VPA becomes SELF_TRANSFER`()
@Test fun `UPI_PAYMENT to CRED middleman already returned as CC_BILL_PAYMENT by parser`()
@Test fun `user-added recipient rule reclassifies UPI_PAYMENT to CC_BILL_PAYMENT`()
@Test fun `INCOMING_CREDIT from registered self source becomes SELF_TRANSFER`()
```

### 11.2 Dedup merge tests

Each worked example in §10 is a test. Plus edge cases:

```kotlin
@Test fun `same amount within 5 minutes but different last4 does not merge`()
@Test fun `convenience fee of 50 paise still merges`()
@Test fun `5-minute-and-1-second gap does not merge`()
@Test fun `two real distinct spends of identical amount 10 minutes apart do not merge`()
@Test fun `REFUND never merges with SPEND even if everything else matches`()
@Test fun `merge updates primary when more authoritative source arrives`()
```

### 11.3 Budget computation tests

Table-driven: set up a DB with known transactions and `budgets` rows,
assert computed outputs.

```kotlin
@Test fun `carry-over with single prior month of surplus`()
@Test fun `carry-over with alternating surplus and deficit`()
@Test fun `carry-over is zero when no prior budgets exist`()
@Test fun `month spend excludes CC_BILL_PAYMENT`()
@Test fun `month spend excludes pending-FX transactions`()
@Test fun `month refunds are counted in the month received`()
```

### 11.4 Alert state machine tests

```kotlin
@Test fun `50pct threshold fires when spend crosses half`()
@Test fun `threshold does not re-fire when refund drops below`()
@Test fun `110pct threshold fires exactly once even after further spending`()
@Test fun `warn threshold respects user setting`()
@Test fun `late-arriving historical transaction does not fire current-month alerts`()
```

### 11.5 Forex tests

```kotlin
@Test fun `forex transaction is created pending when offline`()
@Test fun `forex transaction is filled when worker runs`()
@Test fun `forex pending transaction is excluded from spend total`()
@Test fun `manual rate override preserved across reparse`()   // v1.1
@Test fun `rate cache avoids duplicate API call within 24h`()
```

### 11.6 Invariant property tests

Generate random synthetic operations (inserts, updates, reparses) over
a small schema and assert §9's invariants hold after each operation.
Tool: Kotest property-based testing.

### 11.7 Full-pipeline integration tests

Against a fresh in-memory Room DB:

1. Preload the feasibility dataset.
2. Register known accounts + recipient rules (seeded per accounts memo, local-only).
3. Run the full ingestion → classifier → transactions pipeline.
4. Assert: monthly spend totals match the expected post-CRED-fix
   numbers from the feasibility analysis.

This is the "does the real-world math come out right" smoke test.

## 12. Testability posture

| Behavior                                                 | Category      |
|----------------------------------------------------------|---------------|
| Classifier pipeline                                      | Strict TDD    |
| Dedup merge algorithm                                    | Strict TDD    |
| Budget computation (carry-over, spend, refunds)          | Strict TDD    |
| Alert state machine                                      | Strict TDD    |
| Category assignment                                      | Strict TDD    |
| Role assignment for `transaction_sms_sources`            | Strict TDD    |
| Reparse reconciliation                                   | Strict TDD    |
| Forex worker (rate fetch + retry)                        | Integration (with mocked HTTP) |
| Notification dispatch                                    | Integration   |
| End-to-end feasibility-dataset math                      | Integration   |
| Realtime ingestion → budget update flow on device        | Manual verify |

## 13. Coverage posture

- **Classifier + dedup merge: 100% line coverage.** No excuses.
- **Budget computation: 100% branch coverage.** Every
  carry-over-sign × threshold-crossed × refund-present combo.
- **Alert state machine: 100% branch coverage.**
- **Forex worker: ≥90%.** 10% gap acceptable for actual HTTP path which
  is integration-tested.

## 14. Decisions (resolved 2026-04-15)

- **Dedup window: 5 minutes** between a candidate and the existing
  transaction's `occurred_at_ms`. Wider risks false merges; tighter
  risks missing the observed 3-min VVCMC span.
- **Late-arriving past-month alerts: silent-fired.** A transaction
  inserted by historical import that would have crossed a threshold
  receives an entry in `budget_alert_state` (monotonic-history), but
  no notification is dispatched. See §7.6.
- **Month-key timezone: device-local time.** Stored immutably at
  transaction insert time. Revisit if international-travel edge cases
  produce visible mis-bucketing.
