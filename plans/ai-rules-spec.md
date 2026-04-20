# Yutori — AI-Assisted Rule Creation Specification (v1)

On-device LLM-backed free-text rule extraction. Part 2 of issue **#64
("AI-assisted rule creation")** — the model-driven path that complements
the heuristic suggestions shipped in Part 1 (`plans/suggestions-spec.md`).

The user describes a rule in plain English ("treat anything from CRED as
a credit-card bill payment"); a small language model running entirely
on-device extracts a merchant pattern and a user-facing category label;
the user reviews + confirms in the existing `AddEditRecipientRule`
form — picking the internal classification enum themselves — and saves.
No cloud, no API key, no account.

Companion docs: [schema.md](./schema.md) §`recipient_rules`,
[settings-spec.md](./settings-spec.md) §3 (Recipient rules),
[suggestions-spec.md](./suggestions-spec.md) §6.4 (integration point),
[ui-spec.md](./ui-spec.md) (edit-form conventions).

Reference mockup: `mockups/v17-ai-rules.html` *(drafted alongside this
spec; user approval required before any Stage C code — see CLAUDE.md
workflow rules).*

Related issues: **#64** (parent), **#28** (manual edit form — target
surface for post-extraction review), **#29** ("Add a rule from this
transaction" — alternate entry point), **#30** (reparse pipeline —
triggered on accept), **#32** (review unmatched screen — secondary
entry point, out of scope for v1 here).

---

## 1. Purpose

Heuristic suggestions (Part 1) only surface merchants the user has
*already* transacted with at least three times. Two common cases it
cannot reach:

1. **First-encounter merchants the user wants to pre-classify.** "I'm
   about to start paying my electricity bill via CRED — reclassify all
   CRED payments as CC bill now." The heuristic miner has no data.
2. **Policy rules with no clear merchant anchor.** "Anything involving
   the word 'salary' from my employer should count as income." The
   heuristic path is strictly merchant-key-grouped; it can't infer from
   free-form intent.

Writing a rule manually via #28's form requires the user to (a) know
merchant substring matching exists, (b) know the `pattern_kind` options,
(c) pick an internal classification enum they've never seen labels for,
and (d) craft a note. For non-technical users this is a wall.

The AI path flips the default: **the user describes intent in plain
English; the app extracts what it can; the user confirms or edits the
rest in the existing manual form**. Success is a rule that saves in
~3 taps instead of 10, with meaningful fields already populated.

## 2. Scope and non-goals (v1)

### In scope

- On-device LLM runtime via **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android:0.10.2`) — Stage A feasibility-validated
- **Single model** shipped at launch: **`litert-community/gemma-4-E2B-it-litert-lm`** (2.58 GB, Apache-2.0, non-gated)
- Settings toggle: off by default, one-tap opt-in, opens a download sheet
- Model is **downloaded opt-in**; **no weights ship in the APK**
- "Describe this rule…" entry points on (a) unsure suggestion cards, (b) #29's per-tx context menu
- LLM extracts `{pattern, category}` as JSON; app parses with a tolerant extractor and runs a validator
- Post-extraction: user lands in `AddEditRecipientRule` with `pattern` pre-filled, `category` in the `note` field, classification enum **still to be picked manually from the dropdown**
- Accepted rules persist with `source = AI` (new enum value — §3.1)

### Explicitly out of scope for v1 — deferred to follow-ups

- **Multi-tier model picker** ("Quick / Balanced / Deep"). Infrastructure is designed tier-agnostic so Stage C can add more tiers without spec churn, but the Settings UI exposes only Deep at launch. Smaller tiers blocked by Gemma 3 license gating (see §11).
- **Auto-classification.** The model never tries to pick `UPI_PAYMENT` / `CC_BILL_PAYMENT` / `SELF_TRANSFER` / etc. — empirically unreliable at sub-2B scale (Stage A runs 1–4 produced near-random classifications). User picks from the dropdown.
- **Fine-tuning.** Not needed at the Deep tier; revisit only if we later ship smaller tiers that need accuracy recovery.
- **Automatic retroactive reparse.** Accepted AI rules match going forward only, same deviation already noted in `suggestions-spec.md` §9.2. Unblocks once #30 lands.
- **Non-English rule descriptions.** The model tolerates them but we don't test or claim support.
- **On-device fine-tune / personalization feedback loop.** Explicit non-goal; training on user data blurs the "everything stays local" story even if the compute is local.
- **Streaming token UI.** The extraction sheet shows a spinner + final result, not a token-by-token stream — simpler and ~10s is short enough that streaming is cosmetic.

## 3. Data contract

### 3.1 `recipient_rules.source` — new enum value

Existing values (schema.md §`recipient_rules` line 124): `SEED`, `USER`,
`LEARNED`. Part 1's heuristic suggestions write `LEARNED`. Part 2 adds a
fourth: **`AI`**.

| value | meaning |
|-------|---------|
| `SEED` | shipped with app (the initial `cred.club@axisb` etc.) |
| `USER` | user wrote it manually via #28 |
| `LEARNED` | user accepted a heuristic suggestion (Part 1) |
| **`AI`** | **user accepted an AI-extracted draft (this spec)** |

No migration — existing rows retain their current value. Downstream
consumers treating the column as a string continue to work; anything
switching on the enum must widen to the new value.

### 3.2 `recipient_rules.note` — carries the category

The LLM-extracted `category` string (e.g. `"food"`, `"credit card bill"`,
`"self-transfer"`) lands in the existing `note` column. The user can
freely edit it in the form before save. No new column.

Rationale for not introducing a dedicated `category` column: the note
field is already user-facing free text, already editable, and already
shown in suggestion cards per `suggestions-spec.md` §4.2. Adding a
semantic `category` column is a future enhancement (for e.g. "filter
transactions by user-assigned category") but is orthogonal to this
spec and would land on its own migration.

### 3.3 No new tables

Part 1's `rule_suggestions` table is not used by this path. AI-extracted
rules bypass the suggestions pipeline entirely — the user describes, the
model extracts, they go straight into the edit form, they save, they're
in `recipient_rules`. No "AI suggestion" queue.

### 3.4 Settings / preferences

One new entry in the existing Settings key-value storage (Preferences
DataStore in `:app`):

| key | type | default | notes |
|-----|------|---------|-------|
| `ai_rules_enabled` | Boolean | `false` | toggle state |
| `ai_model_installed_sha256` | String? | `null` | set once the downloaded model passes SHA-256 verify; `null` means no model on disk |
| `ai_model_install_time_ms` | Long? | `null` | for the "installed N days ago" settings caption |

Three keys, not a struct, because Preferences DataStore's Kotlin API is
per-key and there's no merge benefit.

## 4. Extractor pipeline

The extractor is a pure function `(userInput: String) -> ExtractionResult`
backed by a lazily-held LiteRT-LM Engine. Lives in `:app` under
`com.yutori.ai`.

### 4.1 Module placement

| Piece | Module | Rationale |
|---|---|---|
| `LlmEngineHolder` | `:app` | Singleton that owns the `Engine` lifetime. Lazy-init on first use; close on app termination. |
| `RuleExtractor` | `:app` | Takes a user prompt, runs it through the Engine, applies validator, returns `ExtractionResult`. |
| `StructuredOutputParser` | `:app` | Pure JVM; extracts `{pattern, category}` JSON from raw model output. Spike-validated (see `:ai-spike` module). |
| `ModelDownloader` | `:app` | OkHttp + WorkManager. Chunked download, SHA-256 verify, atomic rename. |
| `AiSettingsViewModel` + Compose screen | `:app` | Observes install state, drives download / delete actions. |
| `DescribeRuleSheet` + ViewModel | `:app` | The free-text input UI; calls `RuleExtractor` and routes to `AddEditRecipientRule`. |

All Android-coupled. No AI code in `:classifier`, `:parser`, `:transactions`,
`:database`, `:budget` — those stay pure-JVM.

### 4.2 Engine lifecycle

- **Cold load** on first `RuleExtractor.extract()` call after app start. Measured ~1.3 s for Gemma 4 E2B on Snapdragon 7+ Gen 3 (Nord 4) when the model file is page-cached; up to ~7 s cold-cold. Hidden behind a loading spinner in the Describe-this-rule sheet.
- **Kept warm** as a singleton for the app process lifetime. The Describe sheet can be opened repeatedly without paying init cost twice.
- **Torn down** when the user turns the toggle off (§6.1), when the model is deleted, or when the process dies. Not on sheet dismiss — warm re-use is the point.
- **Per-prompt fresh `Conversation`.** Stage A proved this was needed to avoid cross-prompt state leakage (run 3 → 4 idx 7 hallucinating "netflix"). The Engine is shared; the Conversation is scoped to a single extraction.
- **Sampler config**: `SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0)` — deterministic decode. Reproducibility matters more than diversity for rule extraction.

### 4.3 System prompt (locked from Stage A)

```
Extract the merchant pattern and category from the user's instruction.
Respond with ONLY a JSON object, no markdown fencing, no prose, no code blocks:
{"pattern": "<merchant name, UPI handle, or substring>", "category": "<category>"}

User: anything from swiggy is food
{"pattern": "swiggy", "category": "food"}

User: treat cred as a credit card bill
{"pattern": "cred", "category": "credit card bill"}

User: netflix is entertainment
{"pattern": "netflix", "category": "entertainment"}
```

Three few-shot examples. Verbatim from Stage A's JSON prompt that scored
10/10 parse and 8–10/10 semantic correctness on Deep.

### 4.4 Structured output parser

Scans the model's raw output for the first balanced `{...}` block
(respecting string escapes) and parses it via `org.json.JSONObject`.
Tolerates markdown code fencing (` ```json … ``` `), trailing prose, or
leading preamble — anything around the JSON is ignored.

Extracts two string fields: `pattern` (required) and `category`
(optional — may be null or empty). Returns `null` if no JSON object can
be found or if `pattern` is missing/empty.

Implementation already exists and is validated in the Stage A spike
module, preserved on the `ai-rules-stage-a` branch (not on `main` —
the throwaway harness is kept isolated). Stage C lifts the parser
directly from there.

### 4.5 Validator

Runs after parse, before returning to UI. Rejects the extraction if any
of the following fail:

1. **`pattern.isNotBlank()`** — trivial.
2. **`pattern.length >= 3`** — guards against "1234"-style over-generic matches (Stage A idx 7 failure mode on Deep).
3. **`pattern.length <= 80`** — guards against the model echoing the user's entire sentence as pattern (observed occasionally on 1B; the 80-char cap is conservative — real merchant substrings are 2–20 chars).
4. **`userInput.contains(pattern, ignoreCase = true)`** — the extracted pattern must appear verbatim in the user's text. Catches the "netflix hallucinated from few-shot example" failure mode when prompts don't fit the schema (Stage A 1B idx 7).
5. **`category == null || category.length <= 40`** — cap category length; empty/null is fine (user can fill in).

If validation fails, the Describe sheet shows:

> Couldn't extract a rule from that. Try rephrasing — e.g. "treat
> payments to cred as a credit card bill".

with a **Retry** button that refocuses the text input. No silent
acceptance of bad extractions.

## 5. Model supply chain

### 5.1 The single model (v1)

| field | value |
|---|---|
| Name | `gemma-4-E2B-it` |
| Source | `litert-community/gemma-4-E2B-it-litert-lm` |
| File | `gemma-4-E2B-it.litertlm` |
| Size | 2.58 GB (exact: 2 583 085 056 bytes) |
| License | Apache-2.0 (non-gated) |
| SHA-256 | **TBD — pin in Stage C after re-downloading from a known-good tag** |
| Direct URL | `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm` |

User-facing name in Settings: **"Deep"**. The technical identity shows
as a secondary caption ("Deep · gemma-4-E2B · 2.58 GB") so a curious
user can match it to the HF page but the primary affordance is the
friendly label.

### 5.2 Download flow

Triggered when the user taps **Download model** in the Settings AI
section after toggling the feature on.

1. Foreground `WorkManager` CoroutineWorker with notification: "Downloading Yutori AI (Deep) · 2.58 GB".
2. OkHttp call with `Range` header support → writes chunks to `filesDir/models/gemma-4-E2B-it.litertlm.tmp`.
3. Progress emits every ~256 KiB to the notification and to a Settings-screen progress bar.
4. On completion: compute SHA-256, compare to the pinned hash, then atomic rename `.tmp` → final path.
5. Write `ai_model_installed_sha256` + `ai_model_install_time_ms` to DataStore. Toggle state transitions from "downloading" to "ready".
6. Cancellation: tap the progress notification → work is cancelled, `.tmp` is deleted, toggle reverts to "off".

**Network constraint**: `NetworkType.UNMETERED` preferred but not required — the user may explicitly opt into a mobile-data download via a "Download anyway" button in the sheet. No silent mobile-data use.

**Resume**: on app restart during a download, the `.tmp` file is detected; the worker resumes from the last byte via `Range`. No guarantee — if the server refuses, start over.

### 5.3 Verify

SHA-256 check is mandatory. A mismatch deletes the `.tmp` and fails the
worker with "Downloaded file didn't match the expected checksum. Retry."
No partial activation. This matters because we're executing downloaded
code-ish (weights that the inference engine follows deterministically);
a corrupted or swapped model is a correctness hazard.

### 5.4 Delete

Settings → AI → "Delete model". Confirmation dialog ("This frees 2.58 GB.
You can re-download later."), then:

1. Close the Engine (`LlmEngineHolder.close()`).
2. Delete the file.
3. Clear `ai_model_installed_sha256` + `ai_model_install_time_ms`.
4. Toggle drops back to "on but no model" state — the settings row shows "Model deleted · Download again".

Toggling the feature OFF does **not** delete the model (the user may
want to toggle it back on without paying 2.58 GB again). Deletion is
an explicit action.

### 5.5 Model replacement (future-proofing for tiers)

The file path and name-on-disk are keyed by the model's canonical name,
not by a fixed `model.litertlm`. Stage C can add a "Switch to Quick"
action that downloads a new file alongside the existing one, verifies,
swaps the "active" pointer, then deletes the old file. Out of scope for
v1 UI but the storage layout supports it.

## 6. UI contract

Full rendered shape lives in `mockups/v15-ai-rules.html` (to be drafted
alongside this spec, approved before any Stage C code). This section
captures the behavioural contract.

### 6.1 Settings → AI-assisted rules

New section on the Settings screen, below "Recipient rules" (#28 surface).

**When `ai_rules_enabled == false`**:
- Toggle row: "AI-assisted rules · off".
- Single caption line: "Describe a rule in plain English; AI extracts the merchant pattern. Runs entirely on your phone."
- Tap toggle → confirmation sheet: "Enable AI-assisted rules?" with "~2.58 GB download · Wi-Fi recommended · On-device only, no data leaves your phone" and a **Continue** button. This is the one place we tell the user *explicitly* what the opt-in costs.

**When `ai_rules_enabled == true && ai_model_installed_sha256 == null`**:
- Toggle shows "on".
- Card below: "Model: Deep (not downloaded)" + Download button + a smaller caption "Deep · gemma-4-E2B · 2.58 GB".

**When downloading**:
- Card shows filename, linear progress bar, bytes transferred / total, Cancel button.

**When installed**:
- Card shows "Model: Deep · Installed {relative date}" + Delete button + secondary "Deep · gemma-4-E2B · 2.58 GB" caption.
- The feature is now usable from its entry points (§6.2).

### 6.2 Entry points

**(a) Unsure suggestion cards** (from Part 1, `suggestions-spec.md` §4.2,
`reason_code = REPEAT_NO_DEFAULT`). These show "No default — pick a type"
today. Add a **Describe this rule…** ghost button next to "Pick type…".
Tapping it opens the Describe sheet (§6.3) pre-populated with a sentence
seed derived from the suggestion's merchant key (e.g. "Anything from
{merchant_key} is…"). User edits or replaces, taps Extract.

**(b) #29's per-tx entry point** — same Describe sheet, unseeded
(empty text field). Routing is identical post-extraction.

**(c) Fallback when feature is off**. If the user taps either
Describe-this-rule button while `ai_rules_enabled == false`, we open a
bottom sheet that says "AI-assisted rules isn't enabled. Turn it on in
Settings?" with a **Go to Settings** primary action. No nag loop —
one tap deep-links them.

### 6.3 The Describe-this-rule sheet

Modal bottom sheet. Single-purpose.

- **Header**: "Describe a rule".
- **Body**:
  - Multi-line text field. Placeholder: "anything from cred is a credit-card bill". Auto-focus on open.
  - **Hint line** below, dimmed: "The AI will suggest a merchant pattern and a category. You'll still pick the classification yourself."
- **Actions**:
  - **Extract** (primary, disabled while text is empty or busy).
  - **Cancel** (ghost, closes the sheet).

When the user taps Extract:

1. Button disables, primary swaps to a progress-spinner state. Text field stays editable so they can see what they typed while the ~10 s extraction runs.
2. On validator success: sheet closes, navigate to `Screen.AddEditRecipientRule(ruleId = null, prefill = RulePrefill(pattern = …, note = category, patternKind = LITERAL))`.
3. On validator failure: show inline error banner above the actions: "Couldn't extract a rule from that — try rephrasing." with a **Retry** button (same as Extract). Text stays.
4. On extractor exception (e.g. Engine failed to init, OOM, device unsupported): show "AI is unavailable on this device" error and a **Tell me more** expandable caption with the error class. Disable the toggle (the feature genuinely doesn't work on this hardware).

### 6.4 AddEditRecipientRule post-extraction behaviour

The existing form (#28) takes a new optional `prefill: RulePrefill?`
parameter on its nav arg. When non-null, form fields are initialized
from it:

| field | pre-filled from prefill? | editable? |
|---|---|---|
| `pattern` | yes | yes |
| `pattern_kind` | forced to `LITERAL` | yes (user can switch to PREFIX / REGEX) |
| `reclassify_as` | **NO — blank dropdown, user must pick** | yes |
| `note` | yes (LLM-extracted `category` string) | yes |
| `account_id` | null | yes |
| `is_enabled` | `true` | n/a |

On save, the resulting row writes `source = AI`.

No "are you sure?" confirmation — the form itself IS the review step.
The user taps Save when they're ready.

## 7. Interactions

### 7.1 With the heuristic path (Part 1)

Orthogonal. The heuristic miner produces `rule_suggestions`; the AI path
does not read or write that table. Both paths write to `recipient_rules`
with distinct `source` values (`LEARNED` vs `AI`), so downstream code
that wants to know which path a rule came from can switch cleanly.

If a user manually uses Describe-this-rule for a merchant that already
has an active heuristic suggestion, both rules end up in
`recipient_rules`. #28's existing duplicate-prevention (same pattern +
same reclassify_as + enabled) catches actual collisions at save time.
Minor duplication between LEARNED and AI rules with identical patterns
but different notes is acceptable; the user can see and delete either.

### 7.2 With the manual form (#28)

The AI path is strictly "smart pre-fill" for #28. Every classification
decision, every regex escape, every save action goes through code that
already exists and is already tested. We are not duplicating the form
or its validation.

### 7.3 With the classifier

No change. Saved AI rules enter the same `RecipientRuleMatcher` the
classifier already uses (business-logic-spec §2.2). `source = AI` is
audit-only; it does not affect match precedence.

### 7.4 With historical import

No coupling. Historical import runs entirely without LLM involvement.
AI rules saved after an import apply to future matching transactions
only, same as USER / LEARNED rules today (retroactive reclassification
waits on #30).

### 7.5 With the unmatched-review screen (#32)

#32 specifies its own surface; integration of Describe-this-rule with
#32's flow belongs in that spec. We mark the integration as a future
touchpoint and move on.

## 8. Testing contract

### 8.1 StructuredOutputParser (`:app`, pure JVM)

```kotlin
@Test fun `extracts pattern and category from bare JSON object`()
@Test fun `extracts from JSON wrapped in markdown code fence`()
@Test fun `extracts from JSON with leading preamble`()
@Test fun `returns null when no JSON object present`()
@Test fun `returns null when pattern field is missing`()
@Test fun `returns null when pattern field is empty string`()
@Test fun `tolerates escaped quotes inside string values`()
@Test fun `tolerates nested objects (picks outermost)`()
@Test fun `category null when field absent`()
@Test fun `category null when field value is empty`()
```

### 8.2 Validator (`:app`, pure JVM)

```kotlin
@Test fun `accepts pattern that appears in user input`()
@Test fun `rejects pattern shorter than 3 chars`()
@Test fun `rejects pattern longer than 80 chars`()
@Test fun `rejects pattern that does not appear in user input`()
@Test fun `substring check is case-insensitive`()
@Test fun `rejects empty category over 40 chars`()  // well-formed rejection
@Test fun `accepts null category`()
@Test fun `accepts valid short category`()
```

### 8.3 ModelDownloader (`:app`, unit-tested with MockWebServer)

```kotlin
@Test fun `downloads full file to tmp and atomic renames`()
@Test fun `resumes from partial tmp via Range header`()
@Test fun `deletes tmp and fails worker on SHA-256 mismatch`()
@Test fun `cancellation mid-download deletes tmp`()
@Test fun `fails cleanly on 403 (gated model — defence in depth)`()
```

### 8.4 RuleExtractor integration (`:app`, Robolectric — Engine mocked)

```kotlin
@Test fun `successful extraction routes to AddEditRecipientRule with prefill`()
@Test fun `validator failure surfaces Couldn't extract banner`()
@Test fun `engine init failure surfaces AI unavailable error`()
@Test fun `fresh Conversation is created per prompt`()
```

### 8.5 UI (`:app`, Robolectric Compose)

```kotlin
@Test fun `Describe sheet disables Extract while text empty`()
@Test fun `Describe sheet shows spinner during extraction`()
@Test fun `Describe-this-rule button routes to Settings when feature off`()
@Test fun `Settings shows download card when toggle on but no model`()
@Test fun `Settings shows Installed state after successful download`()
@Test fun `Settings delete action confirms before wiping the file`()
```

### 8.6 Regression

```kotlin
@Test fun `heuristic suggestions pipeline is not affected by AI toggle`()
@Test fun `existing USER-source rules are unchanged after a migration touch`()
```

## 9. Implementation deviations (v1)

To be filled in during Stage C as the spec meets reality. Kept here as
a forward declaration so future readers know where to look for
spec-vs-code diffs.

## 10. Decisions (resolved)

1. **Single tier at launch: Deep only.** Multi-tier picker is a future enhancement; its infrastructure (model swap flow, tier metadata) is designed into Stage C but not exposed.
2. **No weights ship in the APK.** Opt-in download only. APK bloat from the LiteRT-LM runtime native libraries (~75 MiB in debug; Stage C to measure release) is the cost of entry for the feature being *possible* at all — accepted as a v1 tradeoff; revisit with dynamic feature delivery if Yutori ever goes to Play Store.
3. **Gemma 4 E2B is the chosen model.** Apache-2.0, non-gated, 2.58 GB, 80–90% joint field accuracy on Stage A's 10-prompt fixture. Runner-up (Gemma 3 1B) was 584 MB but only 55% accurate and gated; the storage/accuracy trade at the smaller tier isn't worth it without fine-tuning.
4. **LLM extracts (pattern, category) only — never classification.** Classification enum stays with the user's manual dropdown pick. Empirical: small on-device IT models produce near-random classification choices (Stage A runs 1–4). Keeping scope narrow is what makes the Deep tier reliable.
5. **JSON output format, three few-shot examples.** Ad-hoc `KEY: value` scaffolding scored 4/10 at Deep; JSON scored 10/10. Standard-format output is a hard dependency on accuracy, not a preference.
6. **Fresh `Conversation` per extraction.** Engine kept warm; Conversation closed after every prompt. Stage A confirmed cross-prompt state bleed even with temperature=0.
7. **Validator has 5 rules including substring check.** The substring check (pattern ∈ userInput) is what makes "out-of-schema prompt → hallucinated famous-example-from-the-prompt" failures impossible to ship. Non-negotiable.
8. **`source = AI` on saved rules.** New enum value, additive migration, no existing row touched.
9. **Category lives in `note`.** No new column in v1. Future categorization feature gets its own column and migration when it arrives.
10. **User-facing model name is "Deep".** Technical caption visible but secondary. `Quick` / `Balanced` names are reserved for the multi-tier future — don't use them in v1 strings.

## 11. Open questions for Stage C

1. **SHA-256 pin.** §5.1 leaves the exact hash as TBD. Stage C downloads the model once from a known-good commit (the current HEAD at `main` as of Stage A was `2a101e00c47f`), records the hash, and embeds it as a `BuildConfig` constant.
2. **Exact APK size impact of the LiteRT-LM runtime in release build.** Stage A's debug APK was ~75 MiB. Release post-minify / post-R8 may differ. If the release hit is >100 MiB we revisit (maybe a separate "Yutori AI Extension" APK; maybe the feature is gated on an install check).
3. **Gated-model fallback for smaller tiers.** Tracked as the blocker on Quick / Balanced. Revisit when either (a) a non-gated 270M or 1B Gemma 4 variant publishes, or (b) we conclude the licensing terms of Gemma 3 allow us to mirror.
4. **The describe-sheet seed text copy.** For entry-point (a), what's the exact template? "Anything from {merchant}…"? Copy review in the mockup stage.
5. **What happens when the user tries to extract while the model file is missing?** We should never reach this — toggle state should guard — but belt-and-suspenders: if we do, the sheet should offer "Download model first" as the action, not silently fail.
6. **Error surface for genuinely AI-unusable devices** (e.g. a hypothetical Android build that refuses JNI loads). §6.3 step 4 handles it at the sheet; Stage C decides whether the toggle itself should auto-disable or stay off-but-available.
