# Instructions for GitHub Copilot coding agent

You are opening a PR for **Yutori**, an Android personal-spending tracker
that parses SMS from Indian banks / UPI apps on-device. `CLAUDE.md` at
the repo root is the full context; the most important parts are copied
below. Read `CLAUDE.md` yourself before making decisions — this file is
a Copilot-focused slice, not a replacement.

## 1. Spec before code — in the PR description

Before writing any code, write the PR description with these four
sections. Keep each under ~10 lines.

```
## Problem
<what the issue reports, in your own words>

## Approach
<one-paragraph plan — which modules/files and why>

## Files changed
<bulleted list of paths you expect to touch>

## Out of scope
<what a reader might expect you to also do, that you are NOT doing>
```

If you realise mid-implementation that the approach is wrong, stop,
edit the PR description to reflect the new plan, then continue. The
description is the spec — keep it honest.

## 2. Proof of work is mandatory — `## Proof` section

Add a `## Proof` section to the PR description. One of:

- **UI change (including colour / layout tweaks)** — a screenshot
  (drag-drop into the PR editor; the URL will be
  `github.com/user-attachments/…`) **or** a link to an HTML file under
  `mockups/` that shows the change.
- **Logic-only change** — a fenced code block with the output of the
  relevant test run (the actual command and its output, not paraphrased).

No `## Proof` section → the PR will be rejected on review. This is not
enforced by CI; the human reviewer enforces it.

## 3. Write unit tests wherever possible

If you add logic, add tests that exercise it. The test suite runs on
every PR (`.github/workflows/pr-checks.yml`) and must pass.

- Pure-JVM modules (`:parser`, `:classifier`, `:budget`, `:transactions`)
  — JUnit 5 + kotest. Tests are cheap and fast. No excuse to skip.
- Android library modules (`:ingestion`, `:database`) — JUnit 5, some
  Robolectric where Android types are touched.
- `:app` Compose UI — Robolectric JUnit4 tests run under `testDebug`
  alongside JUnit5. Add one for new composables if it's practical.

Exceptions (must be called out in the PR description):
- Build-system-only changes (e.g. `libs.versions.toml`, gradle file
  edits with no runtime consequence).
- Pure styling changes to existing composables where a test would only
  assert colour / padding values.

When in doubt: write the test.

## 4. Behavioural invariants — do not break these

Copied from `CLAUDE.md` → *Behavioural invariants that are easy to break*:

- **Save-everything**: every received SMS writes to `sms_log` regardless
  of classification. Dropping from the budget view ≠ deleting from
  `sms_log`. Never add filters that discard rows before insert.
- **Double-counting avoidance**: CC transactions count at swipe time;
  CC bill payments drop. UPI debits to known CC-bill middleman VPAs
  (CRED etc.) must be classified `CC_BILL_PAYMENT`, not `UPI_PAYMENT`
  (plan §12.4).
- **Self-transfers**: UPI debit + credit between the user's own
  registered accounts must both drop (§12.2).
- **Dedup (§4.2 / §12.5)**: `sms_log` → `transactions` is 1-to-N. The
  predicate requires merchant-token overlap when both sides have a
  merchant string, falling back to last4 only when one side's merchant
  is null. Regression test lives in `:classifier` — do not delete it.
- **Alert thresholds** fire once on the way up only; refunds below a
  threshold do not re-arm it. Fired thresholds are stamped in
  `budget_alert_state`.
- **Refunds** subtract from the month received, not the month of the
  original spend (§4.4). Foreign-currency conversion uses the rate at
  SMS-receipt time, stored alongside the tx (§4.5).
- **`Classification` enum is closed**: `UNMATCHED` means financial-sender
  SMS the parser failed on (a gap); `NON_FINANCIAL` is an explicit label
  for non-financial senders. Declined transactions are `NON_FINANCIAL`
  (§11.4).

## 5. Extra care in `:parser` / `:classifier` / `:budget`

These modules own the money-math invariants above. A silent miscategorise
here becomes wrong totals on the user's dashboard — we will not catch it
from the diff alone. Before editing any of these:

1. Read the relevant spec: `plans/parser-spec.md`,
   `plans/business-logic-spec.md`, and the §12 section of
   `plans/yutori-plan.md`.
2. State in the PR description which invariant you believe is still
   holding after your change.
3. Add a regression test.

If an issue was auto-assigned by the triage workflow, the gate already
excludes these modules. If an issue lands here anyway, flag it — do not
silently change logic in these modules.

## 6. Project conventions

- Money is stored as **paise** (`Long`), not rupees. Multiply / divide
  at the UI boundary only.
- Month keys are `YYYY-MM` strings.
- Masked card numbers from SMS (`XX1234`) are the cross-month card
  identifier — there is no separate card registry.
- Source roots are `src/main/kotlin` and `src/test/kotlin` (not
  `src/main/java`).
- Compose code uses theme colours — `SpendWiseTheme.colors.*` or
  `MaterialTheme.colorScheme.*`. Never inline `Color(0x…)` or named
  `Color.Red`.
- Icons come from Material core (already provided via `material3`).
  Do not add `material-icons-extended` and do not add custom vector
  drawables.

## 7. Commit messages — conventional commits

Use conventional-commit prefixes: `feat:`, `fix:`, `perf:`, `refactor:`,
`test:`, `docs:`, `ci:`, `chore:`, `style:`. The **path → prefix guide**
in `docs/CONTRIBUTING.md` is binding:

- 100 % `.github/**` changes → `ci:`.
- 100 % `docs/`, `plans/`, or `mockups/` changes → `docs:`.
- User-facing copy (button labels, help text, notification strings,
  About screen, onboarding) ships as `feat:` (new) or `fix:` (wrong) —
  **not** `docs:`. Repo-internal docs are `docs:`.

Mis-prefixing `feat:` on a non-app-code change leaks a wrong minor-bump
signal into semver and puts a false entry in the release body
(git-cliff reads these prefixes; see `cliff.toml`).

## 8. No PII in committed code

No real names, account last-4 digits, employer names, UPI handles, phone
numbers, or real SMS bodies in any committed file — including tests,
fixtures, plans, and mockups. Synthetic placeholders only. Existing
parser tests in `android/parser/src/test/kotlin/**/*Test.kt` show the
pattern.

## 9. How to run the tests

From `android/`:

```bash
# Fast — pure JVM modules.
./gradlew :parser:test :classifier:test :budget:test :transactions:test

# Android library + app unit tests (Robolectric under testDebug).
./gradlew :ingestion:test :database:test :app:testDebugUnitTest
```

The PR-check workflow runs exactly the above. If the workflow is red,
reproduce locally with one of these commands before pushing a fix.
