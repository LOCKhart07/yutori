# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Yutori (was **SpendWise** until 2026-04-17 — fully renamed, including `com.yutori.*` packages; see issue #8) is an Android-only personal spending tracker that reads SMS from Indian banks and UPI apps, classifies each message, and derives a monthly budget view. All data stays on-device (Room). Ships as a side-loaded APK via GitHub Releases (no Play Store); `docs/RELEASING.md` covers signing. The project is planning-heavy — the `plans/` directory is authoritative for behaviour; read the relevant spec before changing logic:

- `plans/yutori-plan.md` — top-level product and design doc. Read §4 (classification & double-counting), §11–§12 (resolved decisions and open questions) before touching parser/classifier logic.
- `plans/business-logic-spec.md` — dedup rules (§4.2 in particular), budget/alert semantics, reparse pipeline.
- `plans/parser-spec.md`, `plans/ingestion-spec.md`, `plans/schema.md`, `plans/ui-spec.md`, `plans/settings-spec.md`, `plans/error-states-spec.md`, `plans/testing-spec.md` — domain-specific specs.

Pending work lives in [GitHub issues](https://github.com/LOCKhart07/yutori/issues) — filter by `tier/` or `status/ready` labels (set by the auto-triage bot, see `docs/issue-triage.md`). The old `plans/pending.md` and `plans/backlog.md` side-ledgers were retired (2026-04-16 and 2026-04-19 respectively); cross-issue dependencies now live in the issue bodies themselves (`Follows #X`, `Blocks #Y`).

## Build and test

All Gradle commands run from `android/`. Kotlin 2.3.20, JDK 17, AGP 9.1.1, Gradle 9.4.1, `compileSdk=36`, `minSdk=28`. Library / plugin versions live in `android/gradle/libs.versions.toml` (added #96 stage 0). The Compose compiler is no longer a manual pin — `org.jetbrains.kotlin.plugin.compose` tracks it against the Kotlin version automatically.

```bash
cd android

# Pure-JVM module tests (JUnit 5 + kotest, fast)
./gradlew :parser:test :classifier:test :budget:test :transactions:test

# Android library / app tests (includes Robolectric UI tests in :app under testDebug)
./gradlew :ingestion:test :database:test :app:testDebugUnitTest

# Single test class / method (JUnit 5 filter)
./gradlew :parser:test --tests "com.yutori.parser.rules.KotakRuleTest"
./gradlew :classifier:test --tests "*DedupMatcher*.matchesByLast4*"

# Instrumentation (DAO + migrations on emulator/device)
./gradlew :database:connectedAndroidTest

# Release APK (debug-signed unless SIGNING_* env vars set — see docs/RELEASING.md)
./gradlew :app:assembleRelease
```

Tests use JUnit Platform (`useJUnitPlatform()`) across all modules. `:app` additionally wires JUnit Vintage so Robolectric JUnit4 Compose UI tests run alongside JUnit5 unit tests under `testDebug`. Kotlin lint: `./gradlew detekt` runs per-module. Config at `config/detekt.yml`, baseline at `config/detekt-baseline.xml`. CI enforces `detekt` on release workflow.

## Module graph

The build is split into six pure-JVM / Android-library modules plus the `:app` shell. Dependency direction is strictly downward — do not introduce Android deps into the pure-JVM modules.

```
app ──► ingestion ──► database (Room, KSP) ──► transactions ──► classifier ──► parser
        │                                      │                │
        └─► budget ─────────────────────────────┴────────────────┘
```

- **`:parser`** (pure JVM) — regex rules per bank/issuer, `SmsInput` → `ParseResult`. Highest-risk component; validated against real SMS corpus before app logic. See `plan §2` (feasibility gate) and §11.2 (dataset).
- **`:classifier`** (pure JVM) — assigns `Classification` enum (`CC_TRANSACTION`, `UPI_PAYMENT`, `CC_BILL_PAYMENT`, `INCOMING_CREDIT`, `SELF_TRANSFER`, `NON_FINANCIAL`, `UNMATCHED`, …). Also owns dedup (`DedupMatcher`) and recipient-rule matching for self-transfers / CC-bill middlemen (CRED etc.).
- **`:budget`** (pure JVM) — monthly budget math, carry-over, alert thresholds (50% / user-configurable warn / 100% / +10% steps).
- **`:transactions`** (pure JVM) — `sms_log` → `transactions` mapping, forex conversion coordination, coroutine-based pipeline.
- **`:database`** (Android library + KSP Room) — Room entities, DAOs, mappers, `YutoriDatabase`. Schemas exported to `android/database/schemas/` and checked in — Room migrations diff against them.
- **`:ingestion`** (Android library) — `BroadcastReceiver` for live SMS + `WorkManager` worker for historical import. Uses the same parser+classifier path as real-time; `sms_log` keyed on Android SMS ID to dedupe.
- **`:app`** (Android app) — Compose UI, ViewModels, notifications, forex API client, backup, importing flow. `Room.databaseBuilder()` lives here.

Package layout in each module is `com.yutori.<module>`; `:app` uses `com.yutori.{ui,notifications,forex,importing,backup}`.

## Behavioural invariants that are easy to break

- **Save-everything principle**: every received SMS is written to `sms_log` regardless of classification. Dropping from budget ≠ deleting. Don't add filters that discard rows before insert.
- **Double-counting avoidance**: CC transactions count at swipe time; CC bill payments drop. UPI debits to known CC-bill middleman VPAs (CRED etc.) must be reclassified `CC_BILL_PAYMENT`, not `UPI_PAYMENT` (§12.4).
- **Self-transfers**: UPI debit + credit between the user's own registered accounts must both drop (§12.2). Account registry lives in Settings; classifier falls back gracefully when not registered.
- **Dedup (§4.2 / §12.5)**: `sms_log` → `transactions` is 1-to-N. The refined predicate requires merchant-token overlap when both sides have a merchant string, falling back to last4 only when one side's merchant is null. Regression test lives in `:classifier`.
- **Alert thresholds** fire once on the way up only; refunds bringing spend below a threshold do not re-arm it. Fired thresholds are stamped in `budget_alert_state`.
- **Refunds** subtract from the month received, not the month of original spend (§4.4). Foreign-currency conversion uses the rate at SMS-receipt time, stored alongside the tx (§4.5).
- **Classification enum is closed**: `UNMATCHED` means financial-sender SMS the parser failed on (a gap); `NON_FINANCIAL` is an explicit label for non-financial senders. Declined transactions are `NON_FINANCIAL` (§11.4).

## Room schema changes

`:database` exports schemas via KSP to `android/database/schemas/` — commit those JSONs with every version bump and write a migration. Instrumentation tests under `:database:connectedAndroidTest` read the exported schemas from assets for automated migration verification.

## Workflow with the user

- **New issues are auto-triaged.** `.github/workflows/triage-issue.yml`
  runs Copilot CLI on every opened issue and posts a structured
  comment + labels (`kind/`, `module/`, `tier/`, `blocker/…`). Spec:
  `docs/issue-triage.md`. When picking up an issue, *read the existing
  triage comment + labels first* — don't redo the classification by
  hand. Disagreeing is fine; just note it.
- **Eligible bugs are auto-assigned to Copilot.** The triage workflow
  assigns the Copilot coding agent when the validated triage says bug
  + ready + P1/P2 + modules ∉ {parser, classifier, budget}. Copilot
  opens a PR gated by `.github/workflows/pr-checks.yml`. Spec:
  `docs/copilot-coding-agent.md`. Per-PR rules (spec-first, proof,
  tests) live in `.github/copilot-instructions.md`.
- **UI changes need a mockup first.** Any visible-to-user UI change — new screen, new banner, new layout, restyle of an existing surface — should be presented as a mockup (HTML in `mockups/`, screenshot, or annotated description) and approved by the user *before* code is written. This applies to the dashboard, drill-downs, settings, and any new screens. Pure-logic changes (DAO, classifier rules, ViewModel internals) don't need mockup approval.
- **Bugs take priority over features.** When the user asks "what's next," surface real bugs (broken behavior, data loss, wrong totals) ahead of features.
- **Verify audit / hypothesis claims before coding.** Two of the audit subagent's "missing" findings (alerts wired, FX banner) turned out to already exist. Always grep / read the alleged code path before claiming it's a gap.
- **PII**: real names, account last-4s, employer names, UPI handles, phone numbers, real SMS bodies must never be in committed source (tests, fixtures, plans, mockups). Synthetic placeholders only — see existing patterns in `android/parser/src/test/kotlin/.../*Test.kt`.
- **Commit policy**: don't `git add -A` (a hook blocks it). Stage by path. The user's name + email is fine on commits going forward.
- **Commit messages**: use conventional-commit prefixes — `feat:` / `fix:` / `perf:` / `refactor:` / `test:` / `docs:` / `ci:` / `chore:` / `style:`. The **path → prefix guide** in `docs/CONTRIBUTING.md` is binding: if a commit touches 100% `.github/**` it's `ci:`, 100% docs/plans/mockups it's `docs:`, etc. Mis-prefixing `feat:` on a non-app-code change leaks a wrong minor-bump signal into semver and a false entry into the release body. See `docs/CONTRIBUTING.md` for the full table, `docs/RELEASING.md` for the bump rules. Git-cliff reads these prefixes — `cliff.toml`.
- **`docs:` is repo-internal only.** User-facing copy (help text, button labels, error messages, notification strings, About screen, onboarding prompts) is product surface and ships as `feat:` (new) or `fix:` (wrong). Full rule in `docs/CONTRIBUTING.md`.

## Conventions picked up from existing code

- Source roots are `src/main/kotlin` (and `src/test/kotlin`), not `src/main/java`.
- Money is stored as paise (Long), not rupees.
- Month keys are `YYYY-MM` strings.
- Masked card numbers from SMS (e.g. `XX1234`) are the cross-month card identifier — no separate card registry.
