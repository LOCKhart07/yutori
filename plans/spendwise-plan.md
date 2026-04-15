# SpendWise — Planning Document
> Android spending tracker. Last updated: April 2026.

---

## 1. Goals

Build a personal Android app that:
- Automatically tracks spending by reading SMS from banks and UPI apps
- Sets a monthly budget and alerts when approaching or exceeding it
- Carries over surplus or deficit from previous months
- Breaks down spending by category and by card/account
- Exports data for external analysis

The guiding principle for data storage is: **save everything, use what's needed now, analyse more later.** No SMS from a financial sender should be silently discarded.

---

## 2. Implementation Order and Feasibility Gate

**The SMS parser is the highest-risk component of this app.** Everything else — the database schema, the budget logic, the UI — is standard Android development with well-understood patterns. The parser is not. It depends entirely on whether real bank SMS formats are regular enough to be reliably parsed with regex or simple heuristics. This must be validated before any other work begins.

### Step 1 — Extract last month's SMS inbox (do this first, before writing any app code)

Extract the complete SMS inbox for last calendar month from the developer's device. Unfiltered — all senders, all content. See §11.2 for the full extraction and sanitization process.

**This is a feasibility gate, not just a development task.** The outcome of this step determines whether the app is viable as designed:

- If financial SMS formats are regular and parseable → proceed with full implementation as planned.
- If formats are too varied or irregular for reliable regex-based parsing → the parser strategy must change before any app code is written. Options at that point: LLM-based parsing (send SMS body to a model, extract structured fields), a hybrid approach (regex for known banks, LLM fallback for unmatched), or a narrower scope (only support specific banks whose formats are regular).

### Step 2 — Label the dataset and build the parser in isolation

Before building the Android app, build and validate the parser as a standalone component — a simple Kotlin script or even a Python script — against the labeled dataset. Measure accuracy. Identify gaps. Iterate until accuracy is acceptable.

**Do not start building the Android app until the parser hits an acceptable accuracy threshold on the real dataset.** Suggested minimum bar: >90% correct classification on financial SMSes, <2% false positive rate on non-financial SMSes.

### Step 3 — Build the Android app around the validated parser

Once the parser is validated, port it into the Android project. The rest of the implementation can proceed in parallel: Room schema, BroadcastReceiver, budget logic, UI.

## 3. Stack Decision

**Chosen: Native Android (Kotlin) + Jetpack Compose + Room**

### Why native Android
SMS reading on Android requires `RECEIVE_SMS` and `READ_SMS` permissions and a `BroadcastReceiver`. This is a first-class native API. React Native and Flutter can wrap it, but the added bridge overhead and dependency on third-party SMS plugins introduce unnecessary complexity and potential reliability issues for something as core as real-time SMS interception.

### Why Jetpack Compose
Modern Android UI toolkit. Avoids XML layout boilerplate. Better state management through Compose's reactive model, which pairs naturally with Room's Flow-based queries.

### Why Room
Structured local storage with type safety, Flow support for reactive UI updates, and easy schema migrations. All data stays on-device — no cloud dependency, no privacy concerns.

---

## 4. SMS Processing Design

### 4.1 The Core Problem: Credit Card Double-Counting

Most spending happens on a credit card. This creates a double-counting risk:

1. **CC transaction SMS** arrives when you swipe/tap — this is the actual spend.
2. **CC bill payment SMS** arrives when you pay the bill from your savings account — this is settling the debt, not new spending.

If both are counted, every rupee spent on a CC is counted twice. The SMS pre-classification layer exists specifically to prevent this.

### 4.2 SMS Classification

Every incoming SMS from a financial sender is classified before any budget logic runs:

| Classification | Budget Action | Reasoning |
|---|---|---|
| CC transaction | Count as spend | Actual consumption event |
| UPI payment | Count as spend | Direct debit, actual consumption |
| Debit card purchase | Count as spend | Direct debit, actual consumption |
| ATM / cash withdrawal | Count as spend (Cash category) | See §4.3 |
| CC bill payment | Drop from budget | Already counted at transaction time |
| Bank credit / refund | Subtract from spend | See §4.4 |
| Cashback / reward points | Drop from budget | Not real income, distorts tracking |
| OTP SMS | Drop from budget | Not a transaction |
| Available limit / balance alert | Drop from budget | Not a transaction |

**Important:** "Drop from budget" does not mean delete. Every SMS is stored in the raw log regardless of classification. See §6.

### 4.3 Cash Withdrawals

ATM withdrawals are counted as spent at the time of withdrawal, under a dedicated **Cash** category.

**Limitation:** Cash is then spent in smaller, SMS-less transactions (street food, auto, etc.) that the app cannot see. Counting at withdrawal time is an approximation — it assumes withdrawn = spent.

**Why accept this:** The alternative is manual entry, which was explicitly ruled out. The approximation is acceptable; what matters is that it's clearly communicated.

**UI implication:** Cash withdrawals are displayed distinctly in the category breakdown — not mixed into the spending ring chart — so the approximate nature is always visible. A "Cash (estimated)" label may be appropriate.

**What is not done:** Letting users split or relabel a withdrawal into sub-categories after the fact. This would require manual input, which is out of scope.

### 4.4 Refunds

Refunds are subtracted from the month in which they are **received**, not the month the original spend occurred.

**Reasoning:** The goal of this app is to know how much you can spend right now without guilt. A refund received this month increases this month's effective budget regardless of when the purchase was made. Attributing it to the original month would require the user to mentally track "I have a pending refund from last month" — defeating the purpose.

### 4.5 Foreign Currency Transactions

CC transactions abroad appear in the SMS in the original currency (USD, EUR, etc.). These are converted to INR using the exchange rate at the time the SMS is received.

**Limitation:** The rate used will differ from the rate at which the bank actually settles the transaction (typically T+1 or T+2). The converted figure is therefore an approximation.

**What is stored:** Original currency code, original amount, INR-converted amount, and the exchange rate used. This allows recalculation later if needed.

**Rate source:** A lightweight public exchange rate API fetched on demand when a foreign currency SMS arrives. No continuous polling.

---

## 5. Categories

| Category | What it covers | Notes |
|---|---|---|
| Food & Dining | Restaurants, cafes, Swiggy (food orders), Zomato (food orders) | Only when merchant is unambiguously food-only |
| Groceries | Unambiguously grocery-only stores (local supermarkets, dedicated grocery chains) | See §5.1 |
| Travel & Transport | Uber, Ola, Rapido, metro, IRCTC, fuel stations | |
| Shopping | Myntra, Ajio, Nykaa and other single-category retail | Explicitly excludes cross-cutting platforms |
| Bills & Utilities | Electricity, water, gas, broadband, Airtel, Jio, mobile recharge | |
| Health | Pharmacies, hospitals, clinics, Apollo, Netmeds | |
| Entertainment | Netflix, Hotstar, Spotify, cinemas, PVR, INOX | |
| UPI Transfer | Peer-to-peer UPI payments to individuals | |
| Cash | ATM withdrawals (approximate, see §4.3) | |
| Uncategorized | Cross-cutting platforms, ambiguous merchants | See §5.1 |
| Other | Parser matched but merchant doesn't fit any above category | See §5.1 |

Categories are assigned by keyword matching on the merchant name extracted from the SMS. The matched keywords, the category assigned, and the confidence level are stored alongside the transaction so the logic can be improved retroactively.

### 5.1 Uncategorized vs Other — An Important Distinction

These are two separate categories serving different purposes:

**Uncategorized** — the parser successfully extracted a merchant name, but that merchant is known to be cross-cutting and cannot be reliably assigned a category from the SMS alone. This is an explicit, intentional classification. Examples:
- Amazon — could be electronics, clothing, groceries, household items, or all at once
- Flipkart — same problem
- Blinkit, Zepto, Swiggy Instamart — quick commerce, ordered items are not reliably one category
- BigBasket — primarily groceries but also household items and personal care
- DMart — groceries, clothing, household, electronics all under one roof

**Uncategorized is a signal, not a failure.** High Uncategorized volume surfaces prominently in the UI, making it clear what the parser cannot resolve. Over time this drives improvements — either through parser enhancements, or through the future-scope receipt/screenshot categorization feature (§10).

**Other** — the parser matched and extracted a merchant, but it is a genuinely miscellaneous merchant that doesn't fit any defined category. A one-off spend at an obscure local vendor, for example. This is a true fallback.

**Why Groceries stays as a category despite the ambiguity of platforms like Blinkit:** There are legitimate grocery-only merchants — a standalone supermarket, a local chain with a specific name — where the category is unambiguous from the merchant name alone. The category exists for those cases. The rule is: assign Groceries only when confident; assign Uncategorized when the merchant is a known cross-cutting platform. Never guess.

---

## 6. Budget & Alerts

### Monthly Budget
A single INR limit set per month. Can be set in advance or updated mid-month. All budget calculations use the **effective budget** — the set limit adjusted for carry-over from the previous month.

### Carry-Over
At the end of each month, the surplus (underspent) or deficit (overspent) rolls into the next month's effective budget.

- **Surplus:** Next month's effective budget = set limit + surplus. You can spend more without guilt.
- **Deficit:** Next month's effective budget = set limit − deficit. You overspent last month, so you start behind.

**Reasoning:** The user explicitly framed the goal as "spending down without guilt." Carry-over directly serves this — it means the monthly number always reflects your true financial position, not just an arbitrary reset.

### Alerts

Budget alerts use **progressive thresholds** — multiple notifications as spending increases, not just a single alert at the limit. This gives early warning rather than a surprise at 100%.

**Default thresholds:**
- **50%** — informational. "You've used half your budget with X days remaining."
- **80%** — warning. "You're approaching your budget limit."
- **100%** — critical. "You've hit your budget for this month."
- **>100%** — each subsequent 10% over budget triggers another alert (110%, 120%, etc.) so overspending doesn't go unnoticed.

**Configurability:**
- The 80% warning threshold is user-adjustable (e.g. move it to 70% or 90%).
- The 50% and 100% thresholds are fixed.
- All thresholds are configurable per month in the budget screen.

**Notification behavior:**
- Each threshold fires at most once per month — no repeated nagging if the user doesn't spend further.
- Notifications are dismissible and do not reappear unless the next threshold is crossed.
- Notifications include the current spend amount, effective budget, and amount remaining.
- Tapping a notification opens the dashboard directly.
- If a refund brings spend back below a previously triggered threshold, the threshold is not re-armed. The threshold fires once on the way up, not on the way down.

**Daily burn rate (informational, not an alert):**
- The dashboard surfaces a "daily burn rate" — average daily spend so far this month vs the daily rate needed to stay within budget. This is passive information, not a notification, but helps the user self-correct before hitting a threshold.

---

## 7. Data Storage Strategy

**Principle: Save everything. Use what's needed now. Analyse more later.**

Concrete Room schema proposal: [schema.md](./schema.md).

### Two-table design

**`sms_log` table — raw capture layer**
Stores every SMS from a financial sender, regardless of classification. Fields:
- Sender ID (e.g. `VK-HDFCBK`)
- Raw SMS body
- Timestamp (epoch milliseconds — not just date, to enable time-of-day and day-of-week analysis later)
- Classification assigned (e.g. `CC_TRANSACTION`, `CC_BILL_PAYMENT`, `OTP`, etc.)
- Parser pattern that matched, or `UNMATCHED` if none did
- Whether it was included in budget calculations

Unmatched SMSes from financial senders are particularly valuable — they reveal parser gaps and can be re-processed after parser improvements.

**`transactions` table — derived budget layer**
Only rows that affect the budget. Fields:
- Amount (INR)
- Original amount + original currency (if foreign)
- Exchange rate used (if foreign)
- Merchant name (extracted)
- Category (assigned)
- Card/account identifier — the masked number from the SMS (e.g. `XX1234`), not just the bank name
- Bank/issuer name
- Month key (e.g. `2026-04`) for grouping
- Timestamp
- Classification (CC transaction, UPI, cash withdrawal, refund, etc.)
- Reference to `sms_log` row
- Is manual entry flag (for future use, even if manual entry is not currently in scope)

### Why store the masked card number
The masked number (XX1234) allows correlating transactions to a specific card across months. Combined with the bank name derived from the sender ID, this gives you the card/account breakdown drill-down (§7) without requiring the user to register their cards manually.

---

## 8. UI Structure

### Primary view — Dashboard
- Current month spend vs effective budget (accounting for carry-over)
- Progress bar / gauge
- Days remaining in month
- Quick category breakdown (ring chart)
- Cash withdrawal called out separately

### Drill-down — Category view
- Spend per category for the selected month
- Transaction list filterable by category

### Drill-down — Card/Account view
- Same breakdown split by masked card/account identifier
- Accessible from the dashboard, not the primary navigation

**Reasoning for making card breakdown a drill-down:** Most of the time you want to know "how much have I spent total." The card split is for periodic review, not daily use. Surfacing it in primary navigation would clutter the main experience.

### Budget screen
- Set monthly limit
- Set alert threshold
- View carry-over balance from previous month

### Export screen
- CSV export of transactions for a selected month range
- Written to device Downloads folder

---

## 9. What Was Explicitly Ruled Out

| Feature | Reason |
|---|---|
| Manual transaction entry | App is SMS-driven. Manual entry contradicts the core design. |
| Splitting/relabelling cash withdrawals manually | Requires manual input, contradicts the no-manual-entry goal. |
| iOS support | SMS interception is not possible on iOS without special entitlements unavailable to regular apps. Android only. Not a priority. |
| CC outstanding balance tracking | User tracks debt position through other means. Out of scope. |
| Multiple budget profiles | Not requested. Single monthly budget is sufficient. |

---

## 10. Future Scope

Features that are explicitly acknowledged but deferred. Documenting them here ensures architectural decisions in v1 don't accidentally close the door on them.

### 10.1 Bank and CC Statement Import (PDF/CSV)
Users can upload monthly bank or credit card statements (PDF or CSV format) as an alternative or supplement to SMS parsing. This would give complete transaction history for months before app install, and would resolve cases where SMS was not delivered or was deleted.

**Architectural consideration for v1:** The `sms_log` and `transactions` tables should have a `source` field from the start (`SMS_REALTIME`, `SMS_IMPORT`, `STATEMENT_PDF`, `STATEMENT_CSV`, `MANUAL`) so that statement-imported transactions can be stored in the same schema without a migration.

### 10.2 Cloud Sync and Backup
Back up the database to a user-controlled cloud destination (Google Drive, iCloud not applicable). Useful for device switches and as a safety net against data loss.

**Architectural consideration for v1:** No cloud dependency should be introduced in v1. Local Room DB is the single source of truth. Cloud sync should be additive, not structural.

### 10.3 Receipt and Screenshot Categorization
User attaches a screenshot of an order confirmation, receipt, or invoice for a transaction that was classified as Uncategorized. A vision model reads the screenshot and assigns a category (and optionally sub-items). This resolves the fundamental limitation of SMS-only parsing for cross-cutting merchants like Amazon, Blinkit, and DMart.

**Why this is future scope and not v1:** Requires either an on-device vision model or a cloud API call, adding complexity and potential cost. The Uncategorized category in v1 is specifically designed to surface these transactions so this feature has a clear queue to work from.

### 10.4 Weekly and Yearly Budgets
Budget periods other than monthly. Weekly budgets would be useful for more granular control; yearly budgets for annual planning.

### 10.5 Spend Pattern Analysis
Using the timestamp precision stored in `sms_log` (epoch milliseconds), derive insights like: peak spending day of week, peak spending time of day, month-over-month category trends. The data is collected from day one; the analysis layer is future scope.

---

## 11. Resolved Decisions (Previously Open)

### 11.1 Exchange Rate API

**Decision: `exchangerate-api.com` free tier, fetched on demand.**

- Free tier provides 1,500 requests/month with no account required for the basic endpoint.
- Rates are fetched only when a foreign currency SMS is detected — not on a polling schedule. Foreign currency transactions are infrequent enough that free tier limits will not be hit.
- The rate fetched is the mid-market rate at the time of SMS receipt. This will differ slightly from the bank's actual settlement rate (typically T+1 or T+2 and includes a spread). This approximation is acceptable and is documented in §4.5.
- The fetched rate is stored alongside the transaction (see §6, `transactions` table). If a better rate is known later, the transaction can be manually corrected or the conversion recalculated.
- If the API call fails (no network, quota exceeded), the transaction should still be stored — with the original currency and amount — and flagged as "pending conversion." A retry should be attempted the next time the app is opened. The user should be notified that the INR value is not yet available for that transaction.
- Base URL: `https://v6.exchangerate-api.com/v6/latest/USD` (or the relevant base currency). No API key required for free tier basic endpoint.

### 11.2 Parser Development Dataset — SMS Extraction

**Decision: Extract all SMS from last month, completely unfiltered. The parser handles classification.**

**Rationale:** Pre-filtering to financial senders before extraction introduces human judgment into what should be a mechanical step. It risks excluding edge cases — unfamiliar sender IDs, new banks, UPI apps with unexpected prefixes. Extracting everything and letting the parser classify is more robust and produces a more honest picture of parser performance against real-world inbox noise.

**Extraction method:**
- Use ADB or a throwaway single-purpose Android app to dump the full SMS inbox.
- Filter only by date: last calendar month. No filtering by sender ID, content, or any other field.
- Output as structured JSON or CSV with fields: Android SMS ID, sender ID, timestamp (epoch ms), body.

**Volume expectation:** A full month of SMS including OTPs, promotional messages, delivery alerts, and personal messages will be large. This is intentional — the parser must be robust against non-financial SMS, and the dataset should reflect that.

**Sanitization before use as test fixtures:**
- Mask all amounts: replace numeric amounts with placeholder values (e.g. `XXXX.XX`) — preserves format, removes real financial data.
- Mask merchant names: replace with generic labels (e.g. `MERCHANT_A`, `MERCHANT_B`).
- Mask card/account numbers: already partially masked in SMS (XX1234), but verify no full numbers appear.
- Mask UPI IDs and phone numbers.
- Mask personal message content entirely — replace body with `[PERSONAL]` for non-financial SMSes, preserving only the sender ID and timestamp.
- The structural format — sender ID patterns, keyword placement, punctuation, field ordering — must be preserved intact for financial SMSes. That is the valuable part of the dataset.

**Labeling and use in development:**
- Each SMS in the dataset must be manually labeled before use as a test case: what classification it should receive (`CC_TRANSACTION`, `CC_BILL_PAYMENT`, `OTP`, `NON_FINANCIAL`, etc.), what amount should be extracted (if any), what merchant (if any), what category.
- Parser accuracy is measurable: correctly classified / total labeled SMSes.
- Two failure modes to track separately: (a) financial SMS not recognized — `UNMATCHED` on something that should have parsed; (b) non-financial SMS incorrectly parsed as a transaction — false positive. Both are high priority.
- Unmatched financial SMSes are the highest priority gap — each one is a real transaction the app would miss in production.

**Parser coverage target at launch:**
At minimum: HDFC, SBI, ICICI, Axis, Kotak, IndusInd for bank/CC transactions; GPay, PhonePe, Paytm, Amazon Pay for UPI. Coverage validated against the real dataset, not assumed.

### 11.3 Classification Enum — `NON_FINANCIAL`

**Decision: `NON_FINANCIAL` is an explicit classification.**

The §4.2 table enumerates types that are *financial but dropped from budget*
(OTPs, balance alerts, etc.). Real inboxes also contain promos, maintenance
notices, delivery alerts, personal SMSes — messages from non-financial senders
entirely. The parser needs a distinct label for these so the pipeline is
uniform: every SMS stored in `sms_log` has a classification; nothing is
"unclassified." `UNMATCHED` remains reserved for financial-sender messages
the parser failed to recognize (a parser gap, not a content category).

### 11.4 Declined Transactions

**Decision: declined transactions are classified as `NON_FINANCIAL`.**

Surfaced during the April 2026 feasibility dataset labeling. Examples:

> `Txn of INR 100.00 at WWW EXAMPLE COM on Kotak Credit Card x3333 declined as incorrect CVC entered...`
> `Transaction on Axis Bank Credit Card no. XX1111 has been declined due to incorrect CVV...`

No money moves; counting them would inflate spend. They are still stored in
`sms_log` (principle: save everything) with their classification so the record
is auditable, but they do not enter the `transactions` table.

### 11.5 Historical SMS Import

**Decision: Historical import is a user-triggered settings feature, not part of first-launch onboarding.**

**Rationale:** Doing a full inbox parse on first launch creates a poor onboarding experience — the user sees a loading screen before they've even set a budget. It also risks importing years of SMSes the user may not want. Making it an explicit, user-triggered action gives control and sets correct expectations.

**UX flow:**
1. User navigates to Settings → "Import past SMS."
2. User picks a start date (date picker). Default suggestion: start of current month.
3. App shows an estimate of how many SMSes will be scanned (can be computed quickly by querying the SMS content provider without parsing).
4. User confirms. Import begins.
5. A persistent notification shows progress (e.g. "Processed 340 / 1,200 messages"). The app remains fully usable during import.
6. On completion, a summary is shown: how many transactions were found, how many SMSes were unmatched, how many were dropped (bill payments, OTPs, etc.).

**Technical requirements:**
- Must run in a `WorkManager` background worker — not on the main thread, not in a foreground service unless the dataset is very large.
- For very large imports (e.g. importing 2+ years), the worker should process in batches and be restartable — if the app is killed mid-import, it should resume from where it left off, not restart from scratch. This requires storing a "last processed SMS ID" checkpoint.
- Duplicate detection is essential: if a user runs import twice, or if an SMS that was already captured by the real-time `BroadcastReceiver` is also in the import batch, it must not be double-inserted. The `sms_log` table should use the Android SMS ID (from the content provider) as a unique key to prevent duplicates.
- The real-time `BroadcastReceiver` (§3) and the historical import worker use the same underlying parser and classification logic — no separate code paths.

---

## 12. Open Questions (Surfaced During Feasibility, 2026-04)

### 12.1 Incoming UPI Credits — Salary vs. P2P Paybacks

Real inboxes contain a substantial stream of incoming-credit SMSes:

> `Received Rs.100.00 in your Kotak Bank AC X0000 from goog-payments@axisbank on 01-01-26. UPI Ref:...`
> `Received Rs.50.00 in your Kotak Bank AC X0000 from friendone-1@okicici on 01-01-26. UPI Ref:...`

These split semantically into two distinct cases:

- **Salary / employer deposits** — true income. Relevant for net-worth or
  cashflow views, not for monthly spend budget.
- **P2P paybacks** — someone reimbursing you for a shared bill, rent,
  dinner, etc. These are *not* income — they are reversing a prior spend.
  Effectively equivalent to a refund from a budget perspective (§4.4): they
  should reduce the effective spend in the month they are received.

**The classification problem:** from the SMS alone, the two cases are
indistinguishable — both are "Received Rs.X from {upi_id}". The sender's UPI
handle is sometimes a hint (employer UPI IDs are stable; `goog-payments@...`
is Google Pay which could be either) but not reliable.

**Handling decided:** Classification `INCOMING_CREDIT` is now part of the
enum. These messages are dropped from the spend-budget calculation (same
effect as the temporary `NON_FINANCIAL` handling) but retain their semantic
identity in `sms_log` and `transactions`, so the salary-vs-payback decision
becomes a downstream routing choice — not a re-labeling exercise.

**Still open — budget routing:** whether/how to return P2P-payback amounts
to the spend budget. Leading candidate: a lightweight user action ("mark as
payback for previous spend") that converts an `INCOMING_CREDIT` into a
synthetic refund against a chosen prior transaction. Salary/employer
deposits remain outside the budget view but are available for future
cashflow/net-worth analysis. Deferred until v2 because it requires UI work.

### 12.2 Self-Transfers Between Own Accounts

A transfer between the user's own accounts (e.g. Kotak savings → Axis
savings, or Kotak savings → Axis CC bill) produces two SMSes whose
individual contents are indistinguishable from real external transactions:

> `Sent Rs.100 from Kotak Bank AC X0000 to <upi>@axl on 01-01-26.UPI Ref ...`
> `INR 100.00 credited A/c no. XX2222 ... UPI/P2A/...`

The first matches `rule_kotak_upi` → classified `UPI_PAYMENT` → **counted
as spend**. The second matches `rule_axis_savings_upi_credit` →
`INCOMING_CREDIT` → dropped. Net: the budget is charged for money that
never left the user's total holdings.

The Axis CC bill payment flow is already a special case of this (§11.5):
the savings debit and the CC payment receipt are two sides of a self-move.
It only nets out cleanly because both sides happen to be
`CC_BILL_PAYMENT`-flavored and both drop from budget.

**Proposed v1 handling:**

- Add a Settings screen: "My Accounts." User registers their own accounts
  once — `(bank, masked_last4)` plus optional UPI VPAs and beneficiary
  names. Seed shape (placeholders): Kotak Bank `XX0000` (savings), Axis
  Bank `XX2222` (savings), Vasai Janata Bank `006666` (savings), Axis
  Bank CC `XX1111`, Kotak CC `x3333`, Paytm Money `X5555`
  (investments — inflows via IMPS are self-transfers, not spend).
  Own UPI handle pattern follows `examplename-*@oksbi`. Heuristic
  matching on a surname root is unsafe — family members often share it.
- Parser checks the recipient (debit side) and source (credit side) against
  this list.
- If either matches → new classification `SELF_TRANSFER`. Both sides drop
  from budget. Stored in `sms_log` with full fidelity, surfaced in the
  card/account drill-down so the user can see movements without them
  polluting the spend total.

**Why this is acceptable despite §9's "no manual transaction entry" rule:**
registering accounts is one-time setup, not per-transaction data entry.
The app is still SMS-driven for individual transactions — we're only
giving the parser the context it needs to interpret them correctly.

**Architectural note for v1:** reserve the `SELF_TRANSFER` enum value and
the `accounts` table in the Room schema even if the Settings UI ships
later. Users who miss the setup screen will have their self-transfers
labeled `UPI_PAYMENT` / `INCOMING_CREDIT`; retroactive re-classification
after they register accounts should be a simple query rewrite over
`sms_log`, not a migration.

### 12.3 Multi-Party Duplicate SMSes for a Single Payment

Gateway-routed payments routinely produce two or three SMSes from
different parties describing the same underlying money movement. Worked
example (Rs 100 property-tax payment):

> **Axis Bank (collect request, unfulfilled):** `VVCMC has requested money
> from you on Google Pay. On approving the request, INR 100.00 will be
> debited from your A/c - Axis Bank`
>
> **ICICI eazypay (the real payment):** `you have made a payment of Rs.
> 100.00 to VVCMC ONLINE PROPERTY TAX ACCOUNT vide ICICI Bank eazypay
> reference ID 000000000000000`
>
> **VVCMC (receipt ack):** `Thank you for payment of Rs. 100 towards
> your House Tax bill. It has been credited to your account on VV00/0000`

Only one represents actual money movement. A naive classifier that treats
each as a spend would book Rs 300 for a Rs 100 bill.

This is a **dedup problem, not a classification problem** — and it's
distinct from:

- §4.1 CC double-count (CC transaction + CC bill), solved by dropping
  `CC_BILL_PAYMENT` from budget.
- §12.2 self-transfer double-count (debit + credit of the same movement),
  solved by account ownership registration.

**Proposed v1 handling:**

- Accept the labeled events as-is in `sms_log` (principle: save everything).
- When inserting into `transactions`, run a dedup check: same amount ±1 min
  window + overlapping merchant/payee tokens → merge into a single
  transaction row, keeping all contributing `sms_log` IDs as references.
- Preference order for the "winning" source when merging:
  1. Bank-side debit SMS (most authoritative about money leaving)
  2. Gateway-side confirmation (eazypay, Google Pay, Razorpay)
  3. Merchant-side receipt ack
- If only the merchant/gateway SMS is available (as with the property-tax
  case above — no bank debit SMS arrived), keep it as the sole source.

**Edge cases to revisit:**

- Timing window: 1 min may be too tight for slow gateways; 5 min may merge
  legitimately distinct payments. Needs tuning against a larger dataset.
- Amount rounding/fees: gateway may show Rs 100 while bank debit shows
  Rs 100.50 due to convenience fee. Fuzzy match within ±5% needed.
- Collect-request SMSes (like the Axis one above) that go unfulfilled must
  *not* be merged into the real payment — they refer to it but no money
  moved. Detecting unfulfilled requests: no matching debit within the
  window.

Deferred past v1 MVP but must be designed in before the `transactions`
table schema is frozen. The `sms_log` → `transactions` mapping is 1-to-N,
not 1-to-1.

### 12.4 CC Bill Payments via UPI Middlemen (CRED, etc.)

**A specific escape hatch for the §4.1 double-count.** Users routinely pay
credit card bills via UPI through intermediaries like CRED, Paytm Postpaid,
PhonePe CC, or Google Pay CC. The money moves:

    savings account --UPI--> middleman --(internal)--> credit card

The bank only sees the first leg. Both sides fire SMSes:

- Savings bank: `Sent Rs.X from … AC … to cred.club@axisb on … UPI Ref …` →
  naive rule matches `UPI_PAYMENT` (counted as spend).
- Credit card bank (later): `Payment of INR X has been received towards your
  … Credit Card …` → `CC_BILL_PAYMENT` (dropped).

The CC transactions that make up this bill were *already* counted at
transaction time (§4.2). Counting the UPI leg too = same double-count that
§4.1 warns about, just via a different channel.

**Feasibility dataset evidence:** recurring UPI payments to `cred.club@axisb`
over several months. When reclassified correctly, reported spend drops
materially month-on-month — up to roughly a third of a month's spend.

**Fix:**

Maintain a list of known CC-bill-payment service VPAs. Any UPI debit
whose recipient matches is reclassified `CC_BILL_PAYMENT` instead of
`UPI_PAYMENT`. Current list:

- `cred.club@axisb` — CRED (also seen with `¡` in place of `@` due to an
  encoding artifact in some transport paths — match both).
- `*@paytmcc` / `*@paytmpostpaid` / `*@paytmccbill` — Paytm CC rails.
- `*@ybl` handles containing `creditcard` — PhonePe CC.
- `*@okhdfcbankcc` — HDFC CC via Google Pay.

The list is extensible; ship v1 with a static registry and let users
add entries in Settings ("Known CC bill payment services"). Adding a
service name post-hoc should trigger a re-classification over
`sms_log` — no migration needed.

**Architectural note:** this is structurally similar to the §12.2
self-transfer handling (both convert UPI debits into a dropped
classification based on recipient matching). The two registries can share
a single "recipient rules" table with columns `(pattern, classification,
note)`.

### 12.5 Dedup Over-Merge on Same-Account Same-Amount Payments

Observed on-device 2026-04-15 during the first end-to-end import test.
Two legitimately distinct UPI payments from the same savings account,
same INR amount, to different VPAs, within the 5-minute window, merged
into a single transaction by the §4.2 dedup rule.

**Example captured:**

> `Sent Rs.999.00 from Kotak Bank AC X0000 to issuertest@okaxis …`
> `Sent Rs.999.00 from Kotak Bank AC X0000 to freshtest@okaxis …`

§4.2 rule 4 ("same `last4` OR merchant-token overlap") was satisfied
by the shared `last4=0000`, so the second payment merged into the
first.

**Why it slipped through the §12.3 design:**

§12.3 was designed for the *multi-party* case (gateway + bank + merchant
ack for the same event) where the bank side often lacks a merchant
string. "Same `last4` is enough" was the deliberate concession. It
over-fires on the *same-account distinct-payment* case, where both
sides DO have merchants — just different ones.

**Proposed v1.1 refinement:** see
[business-logic-spec.md §4.2](./business-logic-spec.md#42-dedup-candidate-search)
for the full proposed predicate. In short: require merchant-token
overlap when both sides have a merchant string; fall back to last4-only
matching when one side's merchant is null.

**Not blocking v1 MVP** because:
- The over-merge collapses two real spends into one. The total amount
  in `transactions` shows ₹999 instead of ₹1998 — a real budget error.
- BUT the scenario is narrow: two UPIs for identical amounts to
  different people in the same 5-minute window.
- User impact in normal usage is small. Defer to v1.1 once we have a
  sample of real-world data to validate the refined predicate against.

**Resolved 2026-04-15.** Implemented the proposed refinement in
`DedupMatcher.matchesByLast4OrMerchantToken` per
[business-logic-spec.md §4.2](./business-logic-spec.md#42-dedup-candidate-search).
Regression test: `distinct UPI payments same account same amount
different VPAs do NOT merge`.
