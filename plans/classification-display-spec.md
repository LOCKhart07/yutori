# Yutori — Classification display consolidation (v1)

Canonical on-device display name for the `Classification` enum, and a
pill-taxonomy fix on the Message log screen. Closes issue **#152**.

Reference mockup: `mockups/v19-classification-pills.html` *(approved
option A2 — real classification text, tone driven by `BudgetEffect`).*

Companion docs: [ui-spec.md](./ui-spec.md) §8 (Transaction detail —
consumer of `prettyClassification`), [parser-spec.md](./parser-spec.md)
(owns `Classification`), [business-logic-spec.md](./business-logic-spec.md)
§4 (owns `BudgetEffect` mapping).

Related commits / issues: **#146** (shipped the Message log screen with
the rollup pills this spec replaces), **#64** part 2 (rule picker, uses
`classificationLabel` today).

---

## 1. Purpose

The Message log screen (shipped in 7b614b6, part of #146) renders one of
four rollup pills per SMS row:

- **Affects budget** — any `Classification` with `BudgetEffect.SPEND` or `REFUND`
- **Tracked as income** — `INCOMING_CREDIT`
- **Ignored** — `BudgetEffect.DROP` (NON_FINANCIAL, SELF_TRANSFER, CC_BILL_PAYMENT, OTP, BALANCE_ALERT — five distinct outcomes collapsed into one label)
- **Needs review** — `UNMATCHED` or unknown string

The reporter in #152 calls out two problems:

1. **"Needs review" implies an action that doesn't exist.** The screen
   is read-only; there is no review affordance.
2. **The four-way rollup is a translation table over the real signal.**
   If the pill needs a lookup to be meaningful, show the thing the lookup
   resolves to instead. "Ignored" smears OTP and Self-transfer into the
   same bucket, which is exactly the information the screen exists to
   surface.

A second motivating finding, discovered while drafting: two private
helpers in the UI layer already do display-name conversion for
`Classification`, and they disagree on casing/punctuation — `NON_FINANCIAL`
renders as **"Non financial"** in `TransactionDetailScreen` and as
**"Non-financial (drop)"** in `AddEditRecipientRuleScreen`. The same
classification should not render two different ways depending on which
screen the user is on.

## 2. Scope and non-goals

### In scope

- New canonical `Classification.displayName: String` in `:parser`, exhaustive over all 13 enum values.
- Delete `prettyClassification` in `android/app/src/main/kotlin/com/yutori/ui/TransactionDetailScreen.kt` (and its `CLASSIFICATION_ACRONYMS` set).
- Delete `classificationLabel` in `android/app/src/main/kotlin/com/yutori/ui/AddEditRecipientRuleScreen.kt`.
- Rewire all three screens (Transaction detail, rule picker, Message log) to the canonical helper.
- Delete the `IngestedMessageOutcome` enum in `android/app/src/main/kotlin/com/yutori/ui/LatestIngestedMessage.kt` — it has exactly one caller and is replaced by `Classification?` on the model.
- Swap the Message log pill taxonomy: pill text = canonical display name; pill tone = `BudgetEffect` (with `UNMATCHED` getting its own warn tone since it has no budget effect).

### Non-goals

- No filter chips on the Message log.
- No tap-to-reclassify from the Message log.
- No changes to the rule-picker's filtering logic (still the same reclassify-addressable subset).
- No changes to the `Classification` enum itself — no added, removed, or renamed values.
- No changes to stored data — `sms_log.classification` remains the enum `name()` string, parsed on read.
- No changes to the notification copy, dashboard, or any other surface that doesn't already display a classification label.

## 3. Canonical display-name mapping

One extension on `Classification`, exhaustive `when`, lives in
`:parser` next to the enum.

```
// android/parser/src/main/kotlin/com/yutori/parser/Classification.kt

val Classification.displayName: String
    get() = when (this) {
        Classification.CC_TRANSACTION  -> "CC transaction"
        Classification.CC_BILL_PAYMENT -> "CC bill payment"
        Classification.UPI_PAYMENT     -> "UPI payment"
        Classification.DEBIT_CARD      -> "Debit card"
        Classification.ATM_WITHDRAWAL  -> "ATM withdrawal"
        Classification.REFUND          -> "Refund"
        Classification.CASHBACK        -> "Cashback"
        Classification.INCOMING_CREDIT -> "Incoming credit"
        Classification.OTP             -> "OTP"
        Classification.BALANCE_ALERT   -> "Balance alert"
        Classification.NON_FINANCIAL   -> "Non-financial"
        Classification.SELF_TRANSFER   -> "Self-transfer"
        Classification.UNMATCHED       -> "Unmatched"
    }
```

Rationale on the cases that change vs. existing code:

| Enum            | Today (TxDetail) | Today (RulePicker)     | Canonical        | Why                                                                 |
|-----------------|------------------|-------------------------|------------------|----------------------------------------------------------------------|
| `NON_FINANCIAL` | Non financial    | Non-financial (drop)    | **Non-financial** | Hyphen matches the English compound; drop `(drop)` (context-specific to rule picker). |
| `SELF_TRANSFER` | Self transfer    | Self-transfer           | **Self-transfer** | Hyphen matches the English compound.                                  |
| others          | *(unchanged)*    | *(unchanged)*           | *(unchanged)*    | Tokenizer and hand-map agree.                                        |

No value is a retokenization of the enum at runtime; every case is
literal. This is deliberate — the tokenizer in `prettyClassification`
gets fragile with acronyms (UPI/CC/ATM/OTP are hardcoded; adding a new
enum value with a new acronym silently produces a miscased label). An
exhaustive `when` forces an opt-in addition per new enum value.

## 4. Pill tone on the Message log

Pill tone is driven by `budgetEffectForClassification(c)` from
`:classifier`, with one special case for the unmatched path which has no
`BudgetEffect`.

| Classification's effect                 | Tone       | CSS class in v19 mockup |
|----------------------------------------|------------|-------------------------|
| `BudgetEffect.SPEND`, `BudgetEffect.REFUND` | positive   | `.pill.counted`         |
| `BudgetEffect.INCOME`                  | info       | `.pill.income`          |
| `BudgetEffect.DROP`                    | muted/grey | `.pill.ignored`         |
| *(UNMATCHED — no effect)*              | warn       | `.pill.review`          |

`REFUND` shares the spend tone (negative spend is still a budget-relevant
event, visually grouped with spends). `UNMATCHED` keeps the warn tone
because "the parser couldn't handle this" is the one case the user might
actually want to notice — it's a gap, not a decision.

No new colors. All four tones already exist in `YutoriTheme.colors`
(`positive`, `info`, `onMuted` + `surfaceElevated2`, `warn`).

## 5. Call-site migration

### 5.1 `TransactionDetailScreen`

- Delete `prettyClassification(name: String)` and `CLASSIFICATION_ACRONYMS`.
- Callers pass the DB string today (`tx.classification`); they now do
  `Classification.valueOf(tx.classification).displayName`. The single
  place where the raw string may not decode (legacy rows, out-of-band
  writes) already has a `runCatching { Classification.valueOf(...) }`
  guard — keep that guard, fall back to the raw string unchanged.
- `prettyRole` stays as-is; it operates on `MessageRole`, not
  `Classification`.

### 5.2 `AddEditRecipientRuleScreen`

- Delete `classificationLabel(c: Classification)`.
- `reclassifyOptionLabel(c: Classification?)` stays; its body becomes
  `c?.displayName ?: "Don't change"`.
- Filtering logic (`RECLASSIFY_OPTIONS`, `TRIGGER_CLASSIFICATIONS`) is
  untouched.
- Visual effect: `NON_FINANCIAL` row goes from "Non-financial (drop)" to
  "Non-financial". No behavioural change, no state migration.

### 5.3 `MessageLogScreen` + `LatestIngestedMessage`

- Delete `IngestedMessageOutcome` enum.
- `LatestIngestedMessage.outcome: IngestedMessageOutcome` →
  `LatestIngestedMessage.classification: Classification?`. Nullable
  because `SmsLogEntity.classification` is a `String` and may be a
  legacy value that no longer decodes; a `null` here renders identically
  to `UNMATCHED` (warn tone, "Unmatched" label). Existing
  `runCatching { Classification.valueOf(...) }.getOrNull()` stays.
- `MessageLogRow` replaces its 4-arm `when` with:
  1. Pill label: `classification?.displayName ?: "Unmatched"`
  2. Pill tone: map `null` or `UNMATCHED` → warn; else map
     `budgetEffectForClassification(classification)` to the tone
     table in §4.
- `toLatestIngestedMessage()` drops its two `.toXxxOutcome()` helpers;
  the `classification` field is set directly from the decoded enum (or
  null).

## 6. Testing

Additions and changes, one commit each:

**Commit 1 — `refactor: canonical Classification.displayName`**

- `:parser` — new test `ClassificationDisplayNameTest` covering every
  enum value (exhaustive; if a new enum value is added without a
  mapping, the `when` won't compile — but the test pins the strings so a
  silent rename is caught).
- `:app` (Robolectric) — existing `AddEditRecipientRuleScreenTest` and
  `TransactionDetailScreenTest` may have label assertions against the
  old strings. The one known-change is `NON_FINANCIAL` in the rule
  picker ("Non-financial (drop)" → "Non-financial"). Update any
  matching assertions; do not add new ones.

**Commit 2 — `feat: show real classification on Message log (#152)`**

- `:app` — `LatestIngestedMessageTest` today asserts the 4-way rollup
  (`outcome == NEEDS_REVIEW` for UNMATCHED, etc.). Rewrite to assert
  the `classification` field on the model (one assertion per enum
  value → expected `Classification` on the decoded side).
- `:app` (Robolectric) — `MessageLogScreenTest` (new, or extension of
  existing) rendering one row per tone bucket, asserting the pill label
  matches `displayName` and the pill tone matches the §4 table.

No new instrumentation tests. No schema migration. No changes to
`:database` or `:ingestion`.

## 7. Sequencing

Two commits, landed in order:

1. `refactor:` — promote `Classification.displayName`, delete both
   private helpers, rewire Transaction detail + rule picker, update
   tests. **No visual change on the Message log.** Tiny copy change on
   the rule picker (dropping "(drop)"). Ship independently — can be
   reviewed in isolation.
2. `feat:` — swap the Message log pill taxonomy, delete
   `IngestedMessageOutcome`. Closes #152.

Commit 1 carries no user-visible behavioural change outside the one
parenthetical drop on the rule picker, so it can merge quickly.
Commit 2 is the user-facing fix #152 is filed against.

## 8. Decisions (resolved in mockup review)

- **A2 over A3.** Keep `BudgetEffect`-driven tone on the pill; the
  user's complaint was about the label, not the color. Going monochrome
  (A3) would lose a cheap signal for no corresponding gain.
- **Exhaustive mapping, not a tokenizer.** `prettyClassification`'s
  acronym-preserving splitter is clever but brittle — future enum
  additions with new acronyms would silently miscase. An exhaustive
  `when` forces an intentional mapping per value.
- **Delete `IngestedMessageOutcome`.** It's a second taxonomy with one
  caller; replacing it with `Classification?` on the model is smaller
  than keeping a parallel hierarchy.
- **Tone for UNMATCHED stays warn, not drop.** It's a parser gap, not a
  decision — giving it a distinct tone keeps it scannable.

## 9. Out of scope (follow-ups if requested)

- Per-classification filter chips on the Message log.
- Tap-to-reclassify from the Message log.
- Exposing `displayName` in any other surface (notifications, CSV
  export, dashboard) — those use their own copy today and should be
  audited separately if we want consolidation there too.
- Localization — all strings are English-only, same as the rest of the
  app.
