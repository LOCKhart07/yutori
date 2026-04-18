# Yutori — Rule Suggestions Specification (v1)

Heuristic-driven surfacing of recipient-rule candidates mined from the
user's own transaction history. First half of issue **#64 ("AI-assisted
rule creation")** — the non-AI, no-model-required foundation. The LLM
path (free-form "anything from Swiggy is food") is explicitly deferred
to a follow-up and is out of scope for this document.

Companion docs: [schema.md](./schema.md) §`recipient_rules`,
[settings-spec.md](./settings-spec.md) §3 (Recipient rules),
[business-logic-spec.md](./business-logic-spec.md) §2.2 (classifier
pipeline), [ingestion-spec.md](./ingestion-spec.md) §7 (historical
import).

Reference mockup: `mockups/v14-suggested-rules.html`.

Related issues: **#64** (parent, this is "part 1"), **#28** (manual
recipient-rule edit form — reused for Edit fallback), **#29**
("Add a rule from this transaction" — complementary per-tx entry point),
**#30** (reparse pipeline — triggered on accept), **#32** (review
unmatched screen — secondary entry point, out of scope here).

---

## 1. Purpose

Writing a `recipient_rules` row today requires the user to (a) know that
rules exist, (b) navigate to Settings → Recipient rules, (c) pick a
pattern kind, (d) type the pattern correctly, and (e) pick a target
classification. Most users will never do this for rules that aren't
already shipped as `SEED`. The result: repeat merchants that should be
re-classifiable (a new CC-bill middleman, an unfamiliar UPI handle the
user treats as self-transfer, etc.) stay mis-classified forever and
silently distort the monthly budget.

The suggestion surface flips the default. The app mines the user's own
history for repeat merchants that aren't yet covered by an enabled rule,
infers a likely target where it can, and offers one-tap acceptance with
a retroactive match preview. The user's job collapses from "write a rule"
to "yes / no / show me the matches".

## 2. Data contract

### 2.1 New table: `rule_suggestions`

| column                      | type        | notes |
|-----------------------------|-------------|-------|
| `id`                        | INTEGER PK  | |
| `merchant_key`              | TEXT UNIQUE | grouping key; same normalization as `transactions.merchant_key` (lowercased, trimmed, punctuation-stripped) |
| `pattern`                   | TEXT        | what gets written to `recipient_rules.pattern` on accept |
| `pattern_kind`              | TEXT        | `LITERAL` / `PREFIX` / `REGEX`; inference never emits `REGEX` in v1 |
| `inferred_classification`   | TEXT?       | target classification, or `NULL` for "unsure" suggestions the user must resolve |
| `inferred_account_id`       | INTEGER?    | FK `accounts.id`; non-null only when `inferred_classification = SELF_TRANSFER` |
| `reason_code`               | TEXT        | `KEYWORD_MIDDLEMAN` / `OWN_HANDLE_SHAPE` / `REPEAT_NO_DEFAULT`; drives the "Why" line in the review sheet |
| `match_count`               | INTEGER     | number of matching txs at last scan |
| `total_paise`               | INTEGER     | sum of matching tx amounts at last scan; used for ranking |
| `first_seen_ms`             | INTEGER     | first time the miner flagged this merchant; drives the "new" badge |
| `last_scanned_ms`           | INTEGER     | updated every scan; lets us prune rows whose merchants fall out of the window |
| `dismissed_at_ms`           | INTEGER?    | `NULL` = active; non-null = dismissed by user, hidden from the list |

**Primary uniqueness:** `merchant_key`. Rescans upsert: they never insert
a second row for the same normalized merchant — they update counts,
bump `last_scanned_ms`, refresh `inferred_classification` (e.g. if the
user just registered an own-handle that now matches), and leave
`first_seen_ms` and `dismissed_at_ms` alone.

### 2.2 No new column on `recipient_rules`

The existing `source` column already reserves `LEARNED` (see
[schema.md](./schema.md) §`recipient_rules`, line 124 and the
forward-compat note at line 209). Accepted suggestions write a
`recipient_rules` row with `source = LEARNED`. No schema change to the
target table.

### 2.3 Migration posture

One Room version bump. Schema export to
`android/database/schemas/` per project convention. Migration is
additive — the new table is empty on first read, so there is no data
transformation step.

## 3. The miner

### 3.1 Module placement

| Piece                          | Module          | Rationale |
|--------------------------------|-----------------|-----------|
| `SuggestionInference`          | `:classifier`   | Pure function over a candidate + accounts + keyword table. Reuses the same keyword tables the classifier already uses for middleman detection (§2.2 of business-logic-spec). |
| `SuggestionMiner` (orchestrator) | `:transactions` | Reads `transactions`, filters by existing rules, calls `SuggestionInference`, upserts into `rule_suggestions`. |
| `RuleSuggestionEntity` + `RuleSuggestionDao` | `:database` | Room. Exposes `observeActive()` as a `Flow` for the UI. |
| `SuggestionRescanWorker`       | `:app`          | `CoroutineWorker`; pulls everything together under WorkManager. |
| `SuggestionsViewModel` + UI    | `:app`          | Observes the DAO, drives the rescan, routes accept/review/dismiss. |

Dependency direction stays downward — `:app` and `:ingestion` wire the
workers; the pure-JVM modules own the logic.

### 3.2 Candidate selection

The miner pulls candidates from `transactions` (not `sms_log` — we need
the final `classification` column, which only exists downstream). In
pseudo-SQL, with `:cutoff_ms = now - 60 days` and `:threshold = 3`:

```sql
SELECT
  merchant_key,
  COUNT(*)               AS match_count,
  SUM(amount_paise)      AS total_paise
FROM transactions
WHERE ts_ms >= :cutoff_ms
  AND merchant_key IS NOT NULL
  AND classification IN ('UNMATCHED', 'UPI_PAYMENT')
GROUP BY merchant_key
HAVING COUNT(*) >= :threshold
ORDER BY total_paise DESC
LIMIT 50
```

Why those two classifications:

- **`UPI_PAYMENT`** — the dominant source of mis-classification: new
  CC-bill middlemen, new own-handles, merchants the user treats as
  transfers rather than spend.
- **`UNMATCHED`** — a parser-miss that still has a resolvable
  `merchant_key`. These may want a recipient rule, though many will be
  better served by a parser-rule gap report; the surface is the same.

`CC_TRANSACTION`, `INCOMING_CREDIT`, `NON_FINANCIAL`, `SELF_TRANSFER`,
`CC_BILL_PAYMENT` are excluded from mining — they're either correctly
classified already or not rule-addressable.

### 3.3 Already-covered filter

Candidates whose `merchant_key` already matches an enabled
`recipient_rules` row are dropped. The filter runs in Kotlin against the
existing `RecipientRuleMatcher` (see business-logic-spec §2.2 step 4)
rather than attempting SQL-level matching — LITERAL/PREFIX/REGEX rules
don't all translate cleanly to SQL `LIKE`. The candidate list is small
(≤50 per scan) and the rule list is small (tens to low hundreds), so
O(candidates × rules) is fine.

### 3.4 Inference

For each surviving candidate, run the following checks **in order**;
first match wins:

1. **Own-handle shape.** The candidate's local-part (before `@`) matches
   the shape of any UPI handle registered on an `accounts` row — e.g.
   `jd-1234@oksbi` matches a user whose Kotak account has
   `jd-*@oksbi` among its associated UPI handles. Produces:
   - `inferred_classification = SELF_TRANSFER`
   - `inferred_account_id = <matching account>`
   - `reason_code = OWN_HANDLE_SHAPE`
   - `pattern_kind = LITERAL`, `pattern = merchant_key`
2. **Middleman keyword.** The `merchant_key` contains a substring from a
   small hard-coded keyword table: `cred`, `cheq`, `ccbill`, `cc-bill`,
   `creditcard`, `postpaid`. Produces:
   - `inferred_classification = CC_BILL_PAYMENT`
   - `inferred_account_id = null`
   - `reason_code = KEYWORD_MIDDLEMAN`
   - `pattern_kind = LITERAL`, `pattern = merchant_key`
3. **Fall through.** No heuristic fired. Produces:
   - `inferred_classification = null` — the UI renders this as an
     "unsure" card (info-blue border, "Pick type…" button).
   - `inferred_account_id = null`
   - `reason_code = REPEAT_NO_DEFAULT`
   - `pattern_kind = LITERAL`, `pattern = merchant_key`

The inference function is pure, lives in `:classifier`, and is unit-
testable without Android dependencies.

### 3.5 Triggers

Three code paths invoke `SuggestionMiner.runOnce(now)`. All are
idempotent — running the miner twice in a row produces the same DB
state.

- **Nightly `WorkManager` job.** `PeriodicWorkRequest`, 1 day interval,
  `ExistingPeriodicWorkPolicy.KEEP` so re-enqueue on every app start
  doesn't reset the cadence. No constraints beyond `NOT_REQUIRED`
  network (the miner is fully local).
- **Post-historical-import one-shot.** Chained onto the historical-
  import worker via `WorkManager.beginWith(import).then(rescan)`. A
  fresh install that just ingested 90 days of SMS gets suggestions
  immediately, not at next midnight.
- **Manual rescan.** Settings → Recipient rules has a Rescan affordance
  in the "Suggested" section header. Tapping it calls
  `SuggestionsRepository.rescanNow()` on `Dispatchers.IO`; the ViewModel
  exposes a `scanning: StateFlow<Boolean>` that the UI uses to animate
  the icon.

The live-SMS `BroadcastReceiver` does **not** trigger the miner — a
single new row rarely crosses the threshold, and mining on every
received SMS would be wasteful.

### 3.6 Pruning

Each rescan also deletes rows where:

- `last_scanned_ms < now - 90.days` AND `dismissed_at_ms IS NULL`, OR
- the merchant's recent `match_count` has dropped below threshold and
  hasn't crossed it again in 30+ days.

Dismissed rows are **not** pruned — they carry the user's intent
("don't show me this again") and must persist.

## 4. UI contract

The full rendered shape lives in `mockups/v14-suggested-rules.html`.
This section captures the behavioural contract the implementation must
match.

### 4.1 Placement

Settings → Recipient rules. A new **Suggested (N)** section renders
above the existing "Active rules" list when
`RuleSuggestionDao.observeActive().count() > 0` OR
`scanning == true` OR the user has tapped a dismissed-reveal affordance
(deferred — not in v1).

When the section is empty but the user has just run a rescan that
returned no new candidates, an inline "No repeat merchants crossed the
threshold yet" empty state renders under the section header for one
screen load, so the Rescan tap isn't silent.

### 4.2 Suggestion card

Each active row in `rule_suggestions` renders as one card with:

- **Row 1** — `pattern` in monospace; on the right, `{match_count} txs ·
  ₹{total_paise/100}` as a secondary line.
- **Row 2** — "Reclassify as → {inferred_classification}" label.
  - `SELF_TRANSFER` suggestions also show `· {account.display_name}`.
  - "Unsure" (null inference) shows "No default — pick a type" in info-
    blue italic.
- **Actions** — three buttons:
  - **Add rule** (accent) on confident suggestions / **Pick type…** (info-
    outlined) on unsure suggestions.
  - **Review** (ghost).
  - **Dismiss** (transparent).

Confident cards use an accent left border; unsure cards use info-blue.

### 4.3 Review sheet

Tapping **Review** opens a modal bottom sheet with:

- Header: "Review suggestion" + "Accepting this rule will reclassify N
  past transactions."
- Meta block: Pattern, Kind, Reclassify as, Why (human-readable text
  derived from `reason_code`).
- Match list: the actual matched txs from `SELECT ... FROM transactions
  WHERE merchant_key = :merchant_key ORDER BY ts_ms DESC` — date,
  amount, classification. Scrollable.
- Actions: **Edit…** (ghost, drops into #28's edit form with all fields
  pre-filled) and **Add rule & reclassify** (primary, same action as
  the card's Add rule).

The match preview list is intentionally identical in shape to the
"test match" surface specified in [settings-spec.md](./settings-spec.md)
§3.6 — the implementation should reuse the same composable.

### 4.4 Rescan affordance

Trailing item in the section header. Icon (Material circular arrows) +
label "Rescan". Tapping spins the icon and disables the tap target
until the coroutine completes. Visible both in populated and inline-
empty states so the user can always force a re-mine. Not shown when
the section is entirely absent (no suggestions, no scan in progress) —
but this case should be rare in practice once the nightly worker is
running.

## 5. Accept / Dismiss / Edit semantics

### 5.1 Accept (confident)

One database transaction:

1. Insert `recipient_rules(pattern, pattern_kind, reclassify_as,
   account_id, source = LEARNED, note = null, is_enabled = 1)`.
2. Delete the `rule_suggestions` row.
3. Enqueue a scoped reparse via the #30 pipeline for `sms_log` rows
   backing the matched txs only (not a full reparse — just the
   `merchant_key = :key` slice). The reparse flips their
   `classification`, updates `budget_effect`, and recalculates the
   affected months' budgets.

Confirmation UX: the Review sheet's primary button is labelled **Add
rule & reclassify** precisely so the retroactive consequence is
disclosed before the tap, not after.

### 5.2 Accept (unsure)

Tapping **Pick type…** opens #28's edit form pre-filled with the
suggestion's pattern, but with `reclassify_as` unselected. On save,
step through 5.1 from the form.

### 5.3 Dismiss

Single update: `UPDATE rule_suggestions SET dismissed_at_ms = :now
WHERE id = :id`. No DELETE — the row stays so rescans don't resurface
it.

Undo: a single snackbar with "Suggestion dismissed · Undo" for ~4s.
Undo clears `dismissed_at_ms`. After the snackbar times out the dismiss
is committed; there is no "show dismissed suggestions" affordance in
v1.

**Re-surfacing a dismissed merchant.** If a merchant's `match_count`
climbs to `≥ 2× count_at_dismissal` after dismissal, the next rescan
clears `dismissed_at_ms` and the card reappears. Prevents a permanent
hide for a merchant that becomes much more frequent (e.g. user starts
using a new food-delivery VPA aggressively). This re-surface rule is
implemented as a SQL `CASE` inside the upsert; no separate job.

### 5.4 Edit

**Edit…** in the Review sheet routes to `Screen.RecipientRuleEdit(
ruleId = null, prefill = RulePrefill(...))` — the same screen #29 uses.
On save, the Edit form owns the `rule_suggestions` deletion (via a
`onRuleCreatedFromSuggestion: (suggestionId) -> Unit` callback). User-
edits to the pattern before save are free — the link to the suggestion
is by `suggestion.id`, not by pattern equality.

## 6. Interactions

### 6.1 With the existing classifier

The miner reads `transactions.classification`, which is already written
by the classifier per business-logic-spec §2.2. Nothing in the
suggestion pipeline changes how the classifier runs on live SMS; it's
a pure read-side consumer. Accepted suggestions write a
`recipient_rules` row that the classifier picks up on subsequent
parses exactly like a SEED or USER rule — the `source` column is audit-
only, not behavioural.

### 6.2 With historical import

The post-import rescan runs once the import worker emits SUCCESS. It
operates on the `transactions` rows the import just created. If the
user cancels import partway, the miner still runs on whatever was
processed; suggestions simply reflect the partial history.

### 6.3 With #29 (per-tx "Add rule" entry point)

Complementary, not overlapping. #29 is reactive ("user noticed a
mis-classified tx"); #64 part 1 is proactive ("app noticed a repeat
mis-classification the user hasn't flagged yet"). Same destination —
both write `recipient_rules` rows. A suggestion should be auto-deleted
if the user manually creates a matching rule via any other path, not
just via accept; `SuggestionMiner.runOnce()` already handles this
because the already-covered filter (§3.3) will drop it on next scan.
In the interim (between manual create and next scan) the stale
suggestion card may briefly render — acceptable tradeoff, since the
card's own Accept button is a no-op safety net (inserting a duplicate
rule is prevented by the #28 validation rules).

### 6.4 With the LLM path (#64 part 2)

The LLM path, when it lands, produces inferred fields identical in
shape to what `SuggestionInference` produces today. The Review sheet
and accept/dismiss flow stay unchanged. The only new wiring: the
model's output passes through a validator (compile regex, non-empty
match set, classification ∈ enum) before populating a
`rule_suggestions` row. A row sourced from the LLM path can be
distinguished by `reason_code = AI_INFERRED` or similar — to be
specified alongside the LLM spec.

## 7. Non-goals (v1)

- **Cloud LLM path** — off the table for privacy reasons. The
  user must never be asked to paste an API key for suggestion
  generation.
- **"Show dismissed suggestions" toggle** — power-user feature; the
  auto-resurface rule (§5.3) covers the main regret case.
- **Inline suggestion banner on the unmatched-review screen (#32)** —
  separate issue, same data source (`rule_suggestions` table), same
  accept/review/dismiss semantics. Specified with #32, not here.
- **Per-tx preview during mining** — the miner does not show any UI
  during its run. It writes rows; the UI observes.
- **Suggestion ranking beyond `ORDER BY total_paise DESC`** — good
  enough for v1. Scoring by frequency × recency × amount can come
  later if the list gets noisy.

## 8. Testing contract

### 8.1 Inference (`:classifier`, pure JVM)

```kotlin
@Test fun `own-handle shape match infers SELF_TRANSFER with account id`()
@Test fun `middleman keyword match infers CC_BILL_PAYMENT`()
@Test fun `middleman keyword is case-insensitive`()
@Test fun `own-handle check wins over middleman keyword`()
@Test fun `no heuristic match leaves classification null`()
@Test fun `pattern is always the normalized merchant_key`()
@Test fun `pattern_kind is always LITERAL in v1 inference`()
```

### 8.2 Miner (`:transactions`, pure JVM with in-memory DAO)

```kotlin
@Test fun `groups transactions by merchant_key and thresholds at N=3`()
@Test fun `excludes classifications other than UNMATCHED and UPI_PAYMENT`()
@Test fun `excludes merchants already covered by enabled rules`()
@Test fun `includes merchants covered only by disabled rules`()
@Test fun `upsert updates counts without resetting first_seen_ms`()
@Test fun `upsert leaves dismissed_at_ms untouched on rescan`()
@Test fun `rescan resurfaces dismissed suggestion when match_count doubles`()
@Test fun `pruning removes stale non-dismissed rows older than 90 days`()
@Test fun `pruning keeps dismissed rows regardless of age`()
```

### 8.3 DAO (`:database`, instrumentation)

```kotlin
@Test fun `observeActive excludes dismissed rows`()
@Test fun `observeActive orders by total_paise DESC`()
@Test fun `unique merchant_key constraint rejects duplicate inserts`()
@Test fun `migration adds rule_suggestions table cleanly`()
```

### 8.4 Accept / Dismiss (`:app`, Robolectric)

```kotlin
@Test fun `accept writes recipient_rules with source=LEARNED`()
@Test fun `accept deletes the rule_suggestions row`()
@Test fun `accept kicks reparse scoped to matching sms_log rows`()
@Test fun `dismiss stamps dismissed_at_ms without deleting`()
@Test fun `undo snackbar clears dismissed_at_ms within window`()
```

### 8.5 UI (`:app`, Robolectric Compose)

```kotlin
@Test fun `Suggested section hidden when no active suggestions`()
@Test fun `confident suggestion renders Add rule button`()
@Test fun `unsure suggestion renders Pick type button`()
@Test fun `review sheet shows correct match count and list`()
@Test fun `rescan tap spins the icon and disables until done`()
@Test fun `accepting from review closes the sheet and removes the card`()
```

### 8.6 Regression: miner does not corrupt existing data paths

```kotlin
@Test fun `running the miner does not modify transactions table`()
@Test fun `running the miner does not modify recipient_rules table`()
@Test fun `running the miner during historical import does not deadlock`()
```

## 9. Implementation deviations (v1)

Noted here rather than silently shipping — future readers should know
what's different between this doc and the code.

1. **`SuggestionMiner` lives in `:app`, not `:transactions`.** The module
   graph has `:database` depending on `:transactions`, so `:transactions`
   can't see the DAOs. Pure inference (`SuggestionInference`) still lives
   in `:classifier`; only the orchestrator moved. Testability is
   preserved — the miner uses hand-rolled DAO fakes against the same
   interfaces.
2. **Accept does not trigger reparse.** Issue #30 (reparse pipeline)
   isn't implemented yet. Accepting a suggestion inserts the
   `recipient_rules` row so new transactions classify correctly going
   forward, but past transactions retain their original classification.
   The Review sheet copy reflects this ("would cover N past transactions
   going forward"). Retroactive reclassification follows once #30 lands.
3. **Undo snackbar on dismiss not implemented.** Dismiss writes
   `dismissed_at_ms` immediately; the auto-resurface rule (§5.3) is the
   only path back. Can add the snackbar later without schema changes.

## 10. Decisions (resolved)

1. **Storage** — dedicated `rule_suggestions` table, not in-memory or
   derived-on-read. Dismissals and "new" badges need persistence across
   app restarts and across rescans.
2. **Triggers** — nightly WorkManager job + post-historical-import one-
   shot + manual Rescan. Live-SMS receiver stays out of the loop.
3. **Threshold** — `N ≥ 3` matches over a 60-day window. Not user-
   exposed in v1; revisit if the list is consistently too chatty or too
   quiet.
4. **Classifications mined** — only `UNMATCHED` and `UPI_PAYMENT`.
   Everything else is either correctly classified or not rule-
   addressable.
5. **Pattern kind** — always `LITERAL` in v1 inference. `PREFIX` /
   `REGEX` are available if the user edits before accepting, but the
   miner never emits them. Keeps inference trivially testable.
6. **Accept is retroactive** — accepting a suggestion reparses past
   matching txs, not just future ones. The primary button in the
   Review sheet discloses this.
7. **Dismiss re-surfaces** — at `2× count_at_dismissal`. Prevents
   permanent hides while respecting the dismissal intent for
   low-growth merchants.
8. **Cloud LLM path is off the table.** On-device only. The LLM path
   (`#64` part 2), if and when it lands, ships a small local model, not
   an API-key setting.
