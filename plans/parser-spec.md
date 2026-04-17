# Yutori — Parser Specification (v1)

The parser turns one SMS into one `ParseResult`. This document is the
contract: regexes, rule ordering, edge behavior, and enumerated test cases
detailed enough that the Kotlin implementation is written test-first
against this spec.

Companion docs: [yutori-plan.md](./yutori-plan.md) §4,
[schema.md](./schema.md).

Reference implementation: `feasibility/scripts/parser.py` (Python, used
to validate the feasibility gate and generate expected outputs for
the Kotlin tests).

---

## 1. Purpose

Given an SMS (`sender`, `body`), produce a `ParseResult` that answers:

- What is this message? (classification)
- How much money, if any? (amount + currency)
- Who's the counterparty? (merchant / VPA / beneficiary)
- Which account of mine does it touch? (masked `last4`)
- What category does it belong to, if inferable? (category)

The parser is **deterministic** and **pure** — same input always produces
same output, no clock reads, no DB writes, no network. All DB-backed
enrichment (recipient_rules lookup, account resolution) happens in a
separate classification layer downstream — see §9.

## 2. Non-goals

- The parser does not decide whether to store the SMS. That's the
  ingestion layer's job ([ingestion-spec.md](./ingestion-spec.md)).
- The parser does not fetch exchange rates. It records the original
  currency and amount; conversion happens in the business-logic layer.
- The parser does not dedup multi-party SMSes (§12.3). It labels each SMS
  independently; the merge step lives downstream.
- The parser does not assign the final category for cross-cutting
  merchants. It extracts the merchant; the `merchant → category` mapping
  is a separate decision layer.

## 3. Types

```kotlin
data class SmsInput(
    val sender: String,      // raw from SMS, e.g. "VK-HDFCBK", "JD-KOTAKB-S"
    val body: String,        // verbatim
)

data class ParseResult(
    val classification: Classification,
    val amount: Double? = null,
    val currency: String = "INR",
    val merchant: String? = null,
    val last4: String? = null,
    val category: Category? = null,
    val pattern: String,     // name of the rule that fired, or "UNMATCHED"
)

enum class Classification {
    CC_TRANSACTION, CC_BILL_PAYMENT, UPI_PAYMENT, DEBIT_CARD,
    ATM_WITHDRAWAL, REFUND, CASHBACK, INCOMING_CREDIT,
    OTP, BALANCE_ALERT, NON_FINANCIAL,
    SELF_TRANSFER,      // reserved; only produced by downstream classifier,
                        // not the raw parser — see §9
    UNMATCHED,
}

enum class Category {
    FOOD_DINING, GROCERIES, TRAVEL_TRANSPORT, SHOPPING,
    BILLS_UTILITIES, HEALTH, ENTERTAINMENT, UPI_TRANSFER,
    CASH, UNCATEGORIZED, OTHER,
}
```

Kotlin serialization: `Classification` and `Category` survive the
round-trip to/from the DB as their `name` strings (via Room
TypeConverters). Never store ordinals — re-ordering the enum would
corrupt existing rows.

## 4. Pipeline

```
parse(sms: SmsInput): ParseResult

1. For each rule in RULES (ordered), call rule(sms).
2. If a rule returns non-null → return that result.
3. If all rules return null → return ParseResult(UNMATCHED, pattern="UNMATCHED").
```

**Ordering matters.** Money-movement rules come first so real spends are
never shadowed by an admin/promo pattern. Within each tier, the more
specific rule comes before the more general one.

### Rule order (canonical)

```
# Tier 1 — money-movement events (specific → general)
kotak_upi_debit          # may return CC_BILL_PAYMENT if recipient is a CC-bill middleman
kotak_cc_spend
axis_cc_spend

# Tier 2 — bill payments
axis_cc_bill_received
kotak_cc_bill_received
axis_savings_cc_bill_debit

# Tier 3 — incoming money
kotak_upi_credit
kotak_neft_credit
axis_savings_upi_credit
paytm_sebi_credit
vjsbnk_interest

# Tier 4 — bank adjustments
axis_cashback
blinkit_refund
icici_eazypay

# Tier 5 — notices (no money movement)
declined
cc_statement_generated
axis_cc_admin

# Tier 6 — catch-alls
otp
missed_calls
known_non_fin_sender
```

**Invariant:** if any Tier 1–4 rule matches, Tier 5–6 rules must not
change the outcome. Enforced by rule-ordering; no rule in Tier 5–6 has
access to amounts.

## 5. Rule specifications

Each rule is a `(SmsInput) -> ParseResult?` function. A rule's pattern
must:

1. Match the sender substring that identifies the bank/service.
2. Match a regex over the body.
3. Extract named capture groups: `amt`, `last4`, `merchant`, etc.

All numeric amounts are parsed as `Double`. Amounts with commas in the
source (`Rs.13,563.48`) have commas stripped before parsing.

### 5.1 `kotak_upi_debit` → `UPI_PAYMENT` | `CC_BILL_PAYMENT`

**Sender match:** `"KOTAKB" in sender`.

**Body regex:**
```
^Sent Rs\.(?P<amt>[\d.]+) from Kotak Bank AC X(?P<last4>\w+) to
  (?P<merchant>\S+) on
```

**Classification branch:**
- If `merchant` matches `CC_BILL_MIDDLEMAN_VPA` (see §7) → `CC_BILL_PAYMENT`.
- Else → `UPI_PAYMENT`, `category = UPI_TRANSFER`.

**Test cases:**

| id | body prefix | expected classification | amount | merchant | last4 |
|---|---|---|---|---|---|
| T1 | `Sent Rs.100.00 from Kotak Bank AC X0000 to 0000000000000000.bqr@kotak on 01-01-26` | UPI_PAYMENT | 100.00 | `0000000000000000.bqr@kotak` | 0000 |
| T2 | `Sent Rs.1000.00 from Kotak Bank AC X0000 to cred.club@axisb on 01-01-26` | **CC_BILL_PAYMENT** | 1000.00 | `cred.club@axisb` | 0000 |
| T3 | `Sent Rs.500.00 from Kotak Bank AC X0000 to cred.club¡axisb on 01-01-26` | **CC_BILL_PAYMENT** | 500.00 | `cred.club¡axisb` | 0000 |

T3 is the encoding-glitch variant (`¡` instead of `@`). The middleman
regex must accept both.

### 5.2 `kotak_cc_spend` → `CC_TRANSACTION`

**Sender match:** `"KOTAKB" in sender`.

**Body regex:**
```
^INR (?P<amt>[\d.]+) spent on Kotak Credit Card x(?P<last4>\w+) on
  .+? at (?P<merchant>.+?)\. Avl limit
```

**Output:** `category = null` (merchant-based categorization runs
separately).

**Test cases:**

| id | body prefix | amount | merchant | last4 |
|---|---|---|---|---|
| T10 | `INR 100 spent on Kotak Credit Card x3333 on 01-Jan-2026 at UPI-000000000000-KAWI. Avl limit …` | 100.00 | `UPI-000000000000-KAWI` | 3333 |
| T11 | `INR 50.00 spent on Kotak Credit Card x3333 on 01-JAN-2026 at UPI-000000000000-ZOMAT. Avl limit …` | 50.00 | `UPI-000000000000-ZOMAT` | 3333 |

The Kotak CC UPI-rail merchant is truncated to 5 chars. This is captured
verbatim; normalization (ZOMAT → Zomato) is a downstream merchant-key
concern.

### 5.3 `axis_cc_spend` → `CC_TRANSACTION`

**Sender match:** `"AXISBK" in sender`.

**Body regex (multiline):**
```
^Spent (?P<ccy>INR|USD|EUR|GBP|AED) (?P<amt>[\d.]+)\s*\n
  Axis Bank Card no\. XX(?P<last4>\w+)\s*\n
  \S+ \S+ IST\s*\n
  (?P<merchant>.+?)\s*\n
  Avl Limit
```

**Currency whitelist:** `INR|USD|EUR|GBP|AED`. Any other currency code
falls through to UNMATCHED (deliberate — new currencies should be a
caught gap, not silently misread). Adding a currency is one-line code +
test case.

**Test cases:**

| id | body | ccy | amount | merchant | last4 |
|---|---|---|---|---|---|
| T20 | `Spent INR 100.00\nAxis Bank Card no. XX1111\n01-01-26 12:00:00 IST\nBHARAT PETR\nAvl Limit: INR …` | INR | 100.00 | `BHARAT PETR` | 1111 |
| T21 | `Spent USD 10.00\nAxis Bank Card no. XX1111\n01-01-26 12:00:00 IST\nGITHUB, INC\nAvl Limit: INR …` | USD | 10.00 | `GITHUB, INC` | 1111 |

Forex: the `ccy` is captured raw. INR conversion happens in the business
layer, not here.

### 5.4 `axis_cc_bill_received` → `CC_BILL_PAYMENT`

**Sender match:** `"AXISBK" in sender`.

**Body regex:**
```
^Payment of INR (?P<amt>[\d.]+) has been received towards your
  Axis Bank Credit Card XX(?P<last4>\w+)
```

| id | body prefix | amount | last4 |
|---|---|---|---|
| T30 | `Payment of INR 1000 has been received towards your Axis Bank Credit Card XX1111 on 01-01-26` | 1000.00 | 1111 |
| T31 | `Payment of INR 500.00 has been received towards your Axis Bank Credit Card XX1111 on 01-01-26` | 500.00 | 1111 |

### 5.5 `kotak_cc_bill_received` → `CC_BILL_PAYMENT`

**Sender match:** `"KOTAKB" in sender`.

**Body regex:**
```
^Payment of INR (?P<amt>[\d.]+) is credited to your Kotak Bank
  Credit Card x(?P<last4>\w+)
```

### 5.6 `axis_savings_cc_bill_debit` → `CC_BILL_PAYMENT`

The "other side" of an Axis-CC bill paid from Axis savings. Matches
`Debit INR … Axis Bank A/c XX… <ts>\n CreditCard Payment XX…`. Both
sides are classified `CC_BILL_PAYMENT` so both drop from budget; the
§12.3 merge step will dedup them later.

**Body regex:**
```
^Debit INR (?P<amt>[\d.]+)\s*\n
  Axis Bank A/c XX(?P<last4>\w+)\s*\n
  \S+ \S+\s*\n
  CreditCard Payment XX
```

### 5.7 `kotak_upi_credit` → `INCOMING_CREDIT`

UPI credits into Kotak savings.

**Precondition:** body must start with `Received Rs.` (cheap pre-filter
before regex).

**Body regex:**
```
^Received Rs\.(?P<amt>[\d.]+) in your Kotak Bank AC X(?P<last4>\w+)
  from (?P<src>\S+) on
```

`src` is stored in `merchant`. It's a UPI VPA, not a merchant — but the
ParseResult field is reused for "counterparty" generally.

### 5.8 `kotak_neft_credit` → `INCOMING_CREDIT`

NEFT credits (typically salary, §12.1).

**Body regex:**
```
^Rs\.\s*(?P<amt>[\d.]+) credited to your Kotak Bank a/c XX(?P<last4>\w+)
  via NEFT from beneficiary (?P<src>.+?)\. UTR Ref
```

### 5.9 `axis_savings_upi_credit` → `INCOMING_CREDIT`

**Body regex:**
```
^INR (?P<amt>[\d.]+) credited\s*\n
  A/c no\. XX(?P<last4>\w+)\s*\n
```

### 5.10 `paytm_sebi_credit` → `INCOMING_CREDIT`

Paytm broker's quarterly/monthly return of unused funds (SEBI mandate).

**Sender match:** `"PAYTMM" in sender`.

**Body regex:**
```
^Rs\.\s*(?P<amt>[\d.]+) successfully transferred .*
  SEBI-mandated \w+ settlement
```

**Output merchant:** literal `"Paytm (SEBI settlement)"` (parser-assigned,
not captured — the SMS doesn't name the source).

### 5.11 `vjsbnk_interest` → `INCOMING_CREDIT`

Vasai Janata Bank quarterly savings interest.

**Sender match:** `"VJSBNK" in sender`.

**Body regex:**
```
^Rs\.(?P<amt>[\d.]+) is Credited By Trf in A/c (?P<last4>\d+) on
  .+? INT:
```

**Output merchant:** literal `"Savings interest (VJSBNK)"`.

### 5.12 `axis_cashback` → `CASHBACK`

**Sender match:** `"AXISBK" in sender`.

**Body regex:**
```
^Congratulations! Cashback of INR (?P<amt>[\d.]+) has been credited
  to your Axis Bank .*? Credit Card XX(?P<last4>\w+)
```

### 5.13 `blinkit_refund` → `REFUND`

**Sender match:** `"blnkit" in sender.lower()`.

**Body regex:**
```
^We have initiated a refund of Rs\.(?P<amt>[\d.]+) for the cancelled
  order (?P<order>\S+)
```

**Output:**
- `merchant = "Blinkit"` (literal)
- `category = Category.UNCATEGORIZED` (Blinkit is a cross-cutting
  platform per plan §5.1)

### 5.14 `icici_eazypay` → `UPI_PAYMENT`

ICICI eazypay is a gateway (not an ICICI account) — guest-checkout for
paying merchants. When no bank-side debit SMS arrives in the inbox,
this is the only record of the spend, so we classify it as spend.

**Body regex:**
```
^Dear Sir/Madam, you have made a payment of Rs\. (?P<amt>[\d.]+)
  to (?P<merchant>.+?) vide ICICI Bank eazypay
```

**Note:** the eazypay flow may duplicate against a bank-side debit (dedup
is §12.3's problem). The parser keeps both events distinct here.

### 5.15 `declined` → `NON_FINANCIAL`

**Body regex:** `\b(declined|Declined|DECLINED)\b` anywhere in body.

No amount/merchant/last4 extracted — the transaction didn't happen.

### 5.16 `cc_statement_generated` → `BALANCE_ALERT`

"Your statement is generated" / "Total Due: INR …" notices. Per plan
§4.2, these are analogous to balance alerts — announcing what's owed,
not moving money.

**Any of the following patterns (case-insensitive search, not anchor):**
```
statement for .*?Credit Card.*? is generated
statement for Credit Card X\w+ Total Due
Your credit card bill (?:for .+?)?has been generated
```

### 5.17 `axis_cc_admin` → `NON_FINANCIAL`

Axis CC admin notices: PIN generated, card dispatched, limit updated,
service request status, maintenance pause, feedback confirmations.

**Sender match:** `"AXISBK" in sender`.

**Any of these body patterns (search, not anchor):**
```
New PIN for Axis Bank Credit Card
replace .* Credit Card no\.\s*XX\w+ has been dispatched
usage & transaction limit options have been updated
SR no\. \w+ (?:for|related to) .*(?:Axis Bank)
txns\. with Axis Bank Credit Card .* will be paused
has been resolved - Axis Bank
Thank you for contacting Axis Bank
```

Adding new admin patterns is a one-line append.

### 5.18 `otp` → `OTP`

**Body regex:** `\b(?:OTP|verification code|one[- ]time\s+password)\b`
(case-insensitive).

**Guard:** reject if body contains "do not share", "never share", or
"beware" — these are safety-tip messages, not OTPs.

### 5.19 `missed_calls` → `NON_FINANCIAL`

**Sender regex:** `^\+?\d+$` (sender is a raw phone number).
**Body regex:** `missed call|is now available to take calls` (case-insensitive).

### 5.20 `known_non_fin_sender` → `NON_FINANCIAL`

Checks `sender` against a frozen list of non-financial sender
substrings. Maintained as a constant in code:

```
ISATHI, JIOINF, JIOFBR, JIOPAY, JIONET, -620016-, -620040-,
JioPay, BCCBnK, VIJAYS, MCLBLZ, GSTIND, VCPLNT, POLBAZ, TATALI,
TRAIND, MHACIS, MSEDCL, ELSRUN, TATAMO, SBIINB, BGBMST, REGINF,
SFLTRC, DOTMAH, DOTMUM, EKARTL, JIOVOC
```

Substring check, case-sensitive (these identifiers are always uppercase
in DLT headers).

## 6. UNMATCHED

An SMS that hits none of the rules returns:
```kotlin
ParseResult(classification = UNMATCHED, pattern = "UNMATCHED")
```

UNMATCHED is **not** an error. It's expected for novel non-financial
templates. The ingestion layer still stores these in `sms_log` — that's
the whole point of the raw log.

**Monitoring hook:** in debug builds, every UNMATCHED from a sender
containing one of `KOTAKB|AXISBK|ICICI|HDFC|SBI|UPI` is logged at WARN.
Financial-looking senders that fall through are the highest-priority
parser gap per plan §2.

## 7. Recipient-rule middleman detection

The `CC_BILL_MIDDLEMAN_VPA` check inside `kotak_upi_debit` (§5.1) uses a
hard-coded regex in v1 code — not the DB `recipient_rules` table.

Why: the parser is a pure function, no DB dependency. Pulling in
recipient_rules would make every parse call DB-bound, which we don't
want (every incoming SMS fires the parser on the UI thread when shown
as a preview).

The full recipient-rules flow lives in the **classification layer**
downstream (see [business-logic-spec.md](./business-logic-spec.md)):
```
raw ParseResult → classification layer
  - runs recipient_rules lookup
  - reclassifies UPI_PAYMENT → SELF_TRANSFER for own-account matches
  - optionally overrides to CC_BILL_PAYMENT for user-added middlemen
→ final Classification stored in transactions table
```

The parser's hard-coded middleman list is the "seed" — user-added entries
are additive on top of this in the classifier.

**Seed list (parser-level):**
```
cred\.club[@¡]axisb          # CRED
@paytm(cc|postpaid|ccbill)   # Paytm CC rails
@ybl.*creditcard             # PhonePe CC
@okhdfcbankcc                # HDFC CC via Google Pay
```

## 8. Forex handling

The parser captures `currency` and `amount` in original units. It does
not fetch exchange rates, convert, or call any API.

**Output shape for forex:**
```
ParseResult(
    classification = CC_TRANSACTION,
    amount = 10.00,
    currency = "USD",
    merchant = "GITHUB, INC",
    last4 = "1111",
)
```

The transactions-write layer (downstream) fetches the rate and fills
`inr_amount` + `exchange_rate` + `rate_source` on the `transactions` row.
If the rate fetch fails, the transaction is stored with
`rate_source = "pending"` and `inr_amount = null`; a background retry
fills it in later (see [business-logic-spec.md](./business-logic-spec.md)).

## 9. Raw parser vs. full classifier

The spec in this document describes the **raw parser** — pure function,
regex-only, no DB access.

The full `Classification` value stored in `transactions.classification`
is the output of a downstream step that:

1. Takes the raw `ParseResult`.
2. Looks up `recipient_rules` for the extracted merchant/VPA.
3. Looks up `accounts` for the extracted `last4` + issuer.
4. Possibly reclassifies:
   - `UPI_PAYMENT → SELF_TRANSFER` when the recipient is one of the user's
     own accounts.
   - `INCOMING_CREDIT → SELF_TRANSFER` when the source is one of the
     user's own accounts.
   - `UPI_PAYMENT → CC_BILL_PAYMENT` when the recipient is a user-added
     middleman not in the parser's seed list.
5. Resolves `account_id` to FK `accounts.id`.

`SELF_TRANSFER` never appears in raw `ParseResult`. It is produced only
by the classifier. This keeps the parser blind to user config — a
desirable separation for testing.

## 10. Edge cases and invariants

### 10.1 Input normalization

- `body` is **not** trimmed before matching. Whitespace in the source is
  meaningful (multi-line Axis CC templates depend on exact `\n` placement).
- `sender` is matched as-is.
- Unicode: bodies in non-English scripts (Marathi, Hindi) are passed
  through. The sender-substring check in `known_non_fin_sender` reliably
  catches Jio promos regardless of body language.

### 10.2 Amount parsing

- Strip commas before `Double` parse: `1,000.00` → `1000.00`.
- Reject if post-strip value is negative or NaN.
- Preserve decimal places in storage: `100.00` is stored as `100.0`,
  not coerced to an integer.

### 10.3 Amounts with whitespace or leading zeros

Observed in the dataset: `Rs.           0.00` (Axis lien notice) — many
spaces. The regex `Rs\.\s*(?P<amt>[\d.]+)` tolerates this.

### 10.4 Empty body

`body == ""` → returns UNMATCHED immediately (no rule matches the empty
string).

### 10.5 Missing sender

`sender == ""` → skip all sender-substring rules; rules with no sender
guard still try (e.g. `declined`, `otp`). If none fire → UNMATCHED.

### 10.6 Very long body

No length cap. Regex matches are anchored to `^` or use non-greedy
quantifiers so runtime stays linear.

### 10.7 Encoding glitches

Seen in the wild: `cred.club¡axisb` (should be `@`). Documented exceptions
go into the affected rule's regex as explicit alternates (`[@¡]`). New
glitches surface as UNMATCHED on financial senders → alert.

### 10.8 Invariant: rule uniqueness

No two rules may fire on the same `SmsInput`. If they could, the ordering
in §4 silently decides the outcome. The test suite enforces this (see §11).

## 11. Testing contract

### 11.1 Fixture-driven regression test — the primary test

The feasibility dataset is the backbone:
- `feasibility/data/sms_raw.json` — SMS corpus (not tracked).
- `feasibility/data/labels.json` — expected classification per SMS (not tracked).

The canonical parser test:

```kotlin
@Test fun `parser matches labels across the feasibility dataset`() {
    val rows = loadRawSmses()
    val labels = loadLabels()
    var correct = 0
    var wrong = 0
    val unmatchedFinancial = mutableListOf<Int>()
    val falsePositives = mutableListOf<Int>()

    for (row in rows) {
        val expected = labels[row.id]!!
        val actual = Parser.parse(SmsInput(row.sender, row.body))
        // ... compare classification, amount (for spend events), last4
    }

    // §2 feasibility gate invariants
    assertThat(financialAccuracy).isGreaterThanOrEqualTo(0.90)
    assertThat(nonFinancialFalsePositiveRate).isLessThan(0.02)
    assertThat(unmatchedFinancial).isEmpty()
}
```

**Required outcomes** (matching the current Python parser):
- Financial classification accuracy: 100%.
- Non-financial → spend false-positive rate: <1%.
- Unmatched financial: 0.

### 11.2 Per-rule unit tests

Every rule in §5 has at least the test cases listed under it. Each test
case is a separate `@Test` method (not parameterized) so failures name
the exact case.

### 11.3 Invariant tests

- **Rule uniqueness:** for every SMS in the dataset, at most one rule
  returns a non-null result. Implementation: feed each body through every
  rule individually, assert that ≤1 returns non-null.

- **Determinism:** `parse(x) == parse(x)` across 1,000 random runs over
  the dataset.

- **Purity:** parsing has no side effects. Enforced by code review and
  by the fact that `Parser` takes no constructor dependencies.

### 11.4 Edge-case tests

One test per item in §10.1–10.8. Small, targeted:

```kotlin
@Test fun `empty body returns UNMATCHED`()
@Test fun `amounts with embedded commas parse correctly`()
@Test fun `CRED middleman with encoding glitch still matches`()
@Test fun `non-whitelisted currency in Axis CC falls through to UNMATCHED`()
```

### 11.5 Python-Kotlin parity test

`feasibility/scripts/evaluate_parser.py` runs the Python parser over
the dataset and emits a JSON report. A CI check runs the Kotlin parser
over the same dataset and diffs. Any difference is a regression.

This is the migration-safety net: when we add a rule in Kotlin, we
back-port the regex to Python (or vice versa) and the parity test
ensures both stay in lockstep.

## 12. Testability posture

| Behavior                                  | Category      |
|-------------------------------------------|---------------|
| All rules in §5                           | Strict TDD    |
| UNMATCHED handling                        | Strict TDD    |
| Forex field capture                       | Strict TDD    |
| Edge cases §10                            | Strict TDD    |
| Parity with Python reference              | Strict TDD    |
| Actual SMS delivery from Android           | Integration   |
| Monitoring hook WARN logging              | Manual verify |

## 13. Coverage posture

- **Parser module line coverage: 100%.** The rules are the whole module;
  anything uncovered is dead code.
- **Branch coverage: 100%** for the middleman detection inside
  `kotak_upi_debit` (two branches: matched / not matched).
- Every rule in §5 has at minimum 2 test cases (one positive match, one
  negative near-miss against a similar-shaped SMS).

## 14. Change discipline

- Adding a new rule = new section in §5 + test cases + line in §4
  ordering + line in `RULES` in code + parity update in Python reference.
- Modifying an existing rule's regex = new test case demonstrating the
  widening/narrowing + run parity test + run §11.1 regression test.
- Deleting a rule = deprecate first (emit WARN log on match for 1
  release), then delete. Any existing `transactions.pattern_matched`
  values referencing the deleted name stay as historical record.

No code change to the parser ships without the regression test on the
full feasibility dataset passing.
