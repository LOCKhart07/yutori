# Yutori — Testing Specification (v1)

Test infrastructure: how test fixtures are managed, what tools run
what, how the Python↔Kotlin parity harness works, and what the CI
contract is. The per-module test cases are in each module's own spec
([parser-spec.md](./parser-spec.md) §11,
[ingestion-spec.md](./ingestion-spec.md) §13,
[business-logic-spec.md](./business-logic-spec.md) §11,
[ui-spec.md](./ui-spec.md) §16,
[settings-spec.md](./settings-spec.md) §11,
[error-states-spec.md](./error-states-spec.md) §14). This doc owns the
plumbing.

---

## 1. Test layering

Four tiers, clear boundaries:

| Tier | Runs on | Speed | Tool | Example |
|---|---|---|---|---|
| Unit | JVM | <5ms / test | JUnit5 + Kotest + Mockk | Parser rule, classifier, state machine, validator |
| Integration (Android) | Emulator / device | 100ms–2s / test | AndroidX Test + Espresso / Compose UI | BroadcastReceiver, WorkManager, Room DAO with real SQLite, Compose screens |
| Snapshot | JVM (Paparazzi / Roborazzi) | 20–200ms / test | Paparazzi | Composable rendering |
| Manual verify | Human | n/a | Checklist | Animation feel, real SMS delivery, DB migration failure |

**Rules:**
- Unit tests never touch disk, network, or Android framework.
- Integration tests never assume specific timing beyond IdlingResource
  waits.
- Snapshot tests never cover behavior — only appearance.
- Manual verify items are enumerated per-spec and tracked as a release
  checklist.

## 2. Fixture management

### 2.1 The feasibility dataset is the primary fixture

```
feasibility/data/sms_raw.json       # SMS corpus (not tracked)
feasibility/data/labels.json        # ground-truth classifications (not tracked)
```

This is the backbone of:
- Parser regression (parser-spec §11.1)
- Full-pipeline smoke test (business-logic-spec §11.7)
- Historical import simulation (ingestion-spec §13.3)

These files are copied into the Android project at build time as test
resources:
```
app/src/test/resources/fixtures/sms_raw.json
app/src/test/resources/fixtures/labels.json
```

Build script (Gradle) copies from `../feasibility/data/` before tests
run. Keeps one source of truth outside the Android project.

### 2.2 Sanitization for fixtures in the public repo

If the repo becomes public or shared, raw SMSes must be sanitized per
plan §11.2:

- Amounts replaced with placeholder (`XXXX.XX`).
- Merchant names tokenized (`MERCHANT_A`, `MERCHANT_B`, …).
- UPI IDs tokenized (`VPA_1`, `VPA_2`, …).
- Phone numbers tokenized.
- Personal SMS bodies entirely replaced with `[PERSONAL]` (sender +
  timestamp preserved).
- Structural format (sender prefixes, keyword placement, newlines)
  preserved losslessly — the value of the fixture is the shape.

A `feasibility/scripts/sanitize.py` script runs once, writes to
`sms_raw.sanitized.json`. That sanitized copy is what's committed; the
original is gitignored (already is, per `feasibility/data` in
`.gitignore`).

Labels map 1:1 across sanitization — the label file is already
content-agnostic (classification, amount, merchant, last4), so only
the amount and merchant fields need corresponding placeholder updates.

### 2.3 Fixture factories

For tests that need synthetic data rather than the dataset:

```kotlin
object Fixtures {
    fun kotakUpiDebit(
        amount: Double = 100.0,
        recipient: String = "alice@oksbi",
        last4: String = "0000",
    ): RawSms = RawSms(
        androidSmsId = RandomIds.next(),
        sender = "JD-KOTAKB-S",
        body = "Sent Rs.$amount from Kotak Bank AC X$last4 to $recipient " +
               "on 01-01-26.UPI Ref 000000000000. Not you, URL",
        receivedAtMs = 1_700_000_000_000L,
        source = SmsSource.SMS_REALTIME,
    )

    fun axisCcSpend(...)
    fun kotakCcSpend(...)
    ...
}
```

One factory per rule in parser-spec §5. Keeps tests terse and makes
"what are we testing" obvious at a glance.

### 2.4 DB fixtures

In-memory Room:

```kotlin
fun testDb(): YutoriDatabase =
    Room.inMemoryDatabaseBuilder(
        context, YutoriDatabase::class.java
    )
    .allowMainThreadQueries()  // tests only; real code uses suspend DAOs
    .build()
```

One helper builds a "fully-seeded" DB (accounts, recipient_rules, a
couple budgets) for business-logic tests. Every test gets a fresh DB.

## 3. Python↔Kotlin parser parity

### 3.1 Why we keep the Python parser

Per parser-spec §11.5, the Python implementation
(`feasibility/scripts/parser.py`) stays alive as a reference. Every
rule change:
1. Update Kotlin.
2. Update Python to match.
3. Run parity test — CI-enforced.

### 3.2 Parity harness

Two artifacts, diffed:

```
python3 feasibility/scripts/evaluate_parser.py --json > /tmp/py.json
./gradlew :app:parserParityReport
# runs tests/ParserParityReporter.kt which writes /tmp/kt.json
diff /tmp/py.json /tmp/kt.json   # empty → pass
```

Both writers emit the same shape:
```json
{
  "ids": {
    "0001": {"classification": "NON_FINANCIAL", "amount": null, ...},
    "0002": {"classification": "INCOMING_CREDIT", "amount": 50000.00, ...},
    ...
  }
}
```

The diff is zero when parity holds.

### 3.3 CI integration

A GitHub Actions (or local `ci.sh`) step:

```yaml
- name: Parser parity
  run: |
    python3 feasibility/scripts/evaluate_parser.py --json > py.json
    ./gradlew parserParityReport
    diff py.json build/parser-parity.json
```

Non-zero exit → build red.

### 3.4 When to add a rule in only one language

**Never.** Every rule lives in both. If a rule can only be expressed
in one (e.g., needs a Kotlin-specific library), the parity test
documents the exception with `// PARITY_EXCEPTION: <reason>` and a
corresponding entry in a `parity_exceptions.json` file. Exceptions are
reviewed at every release.

## 4. Snapshot testing

### 4.1 Tool

**Paparazzi** (Square) — JVM-only, no emulator required. Runs on CI
in <30 s for the full screen set.

### 4.2 Golden storage

```
app/src/test/snapshots/
    DashboardScreen_ready_light.png
    DashboardScreen_ready_dark.png
    DashboardScreen_empty_light.png
    ...
```

Committed to repo. Updated via `./gradlew recordPaparazziDebug` (a
conscious opt-in) when a visual change is intentional.

### 4.3 Review discipline

When snapshots change:
- Each new/updated PNG must be eyeballed in the PR.
- If the change was unintentional, revert the code that caused it, not
  the snapshot.
- Snapshot-only PRs ("whitespace rendering tweak") are fine — they're
  cheap review.

### 4.4 Coverage

One snapshot per:
- Top-level screen (per ui-spec) × each state × (light, dark) × (phone
  width, if tablet ships later).

For v1 MVP phone-only:
- Dashboard × {Loading, Empty(NO_PERM), Empty(NO_DATA), Ready, Error}
  × {light, dark} = 10 snapshots.
- Category drill-down × {Empty, Ready} × 2 = 4 snapshots.
- Card drill-down × {Empty, Ready with filter-All, Ready with
  filter-Spend} × 2 = 6 snapshots.
- Transaction detail × {single-source, multi-source, forex} × 2 = 6.
- Budget setup × {fresh, existing} × 2 = 4.
- Settings hub × 1 × 2 = 2.
- Each settings sub-screen × empty × 2 = ~14.

Total ≈ 46 snapshots. Manageable.

## 5. HTTP mocking

### 5.1 Tool

**OkHttp's `MockWebServer`** — stands up a local server the real
`OkHttp` client hits.

### 5.2 Forex test harness

Tests exercising `ForexConversionWorker`:

```kotlin
@Test fun `forex worker fills inr_amount on success`() {
    server.enqueue(MockResponse().setBody(rateJson("USD", 83.5)))
    val worker = buildWorker(transactionId = 1L)
    val result = worker.doWork()
    assertEquals(Result.success(), result)
    val txn = db.transactionDao.byId(1L)
    assertEquals(83.5, txn.exchangeRate)
    assertEquals(1125.58, txn.inrAmount, 0.01)
}

@Test fun `forex worker leaves pending on HTTP 429`() { ... }
@Test fun `forex worker leaves pending on network failure`() { ... }
```

No test ever hits the real `exchangerate-api.com`.

## 6. Property-based testing

### 6.1 Tool

**Kotest property testing** (`kotest-property`).

### 6.2 Where used

For invariants from business-logic-spec §9:

```kotlin
"every transaction has effect consistent with classification" {
    forAll(classificationGen) { cls ->
        val effect = effectFor(cls)
        // insert a transaction with this classification
        // assert the stored budget_effect == effect
    }
}

"every classification maps to exactly one effect" {
    Classification.entries.forEach { c -> effectFor(c) }  // no throws
}

"dedup merge preserves amount-within-tolerance invariant" {
    forAll(transactionPairGen) { (a, b) ->
        val merged = tryMerge(a, b)
        if (merged != null) assert(|merged.amount - a.amount| < 0.5)
    }
}
```

A handful of these tests catch classes of bugs unit tests miss.

## 7. Coverage enforcement

### 7.1 Tool

**Kover** (Kotlin's coverage tool, Gradle-native).

### 7.2 Thresholds

Per-module minimums (build fails below):

| Module | Line | Branch |
|---|---|---|
| parser | 100% | 95% |
| classifier | 100% | 95% |
| business-logic | 100% | 95% |
| ingestion (JVM-side) | 95% | 90% |
| settings-viewmodels | 100% | 90% |
| ui-viewmodels | 100% | 90% |
| ui-composables | n/a (snapshot-covered) | n/a |

Generated reports live in `build/reports/kover/`. CI uploads them as
artifacts; no external service (no Codecov).

### 7.3 Exclusions

Legitimately non-testable code:
- Generated code (Room, Hilt).
- `BroadcastReceiver.onReceive` — covered by integration only.
- Application-class initialization.
- Logcat-only debug paths.

Exclusions are declared in `kover.gradle.kts`, not annotated inline —
keeps the test-write incentive honest.

## 8. Integration test harness

### 8.1 Tool

**AndroidX Test + Compose Test** running on an emulator.

### 8.2 Emulator config for CI

- API 34 (minimum version we support TBD; assume API 28+).
- 1080p.
- No hardware keyboard.
- Animations off (`setDurationScale(0f)`).
- Network disabled by default (tests that need it mock via
  MockWebServer).

### 8.3 SMS injection for receiver tests

Android's `TelephonyManager` can be invoked from test to inject a fake
inbound SMS:

```kotlin
@Test fun `receiver stores incoming SMS in sms_log`() = runTest {
    val latch = CountDownLatch(1)
    db.smsLogDao.count().observeOnce { latch.countDown() }

    injectFakeSms(sender = "JD-KOTAKB-S", body = "Sent Rs.100 ...")

    latch.await(2, SECONDS)
    val rows = db.smsLogDao.all()
    assertEquals(1, rows.size)
    assertEquals(Classification.UPI_PAYMENT, rows[0].classification)
}
```

Fake SMS injection requires a debug-only permission helper; documented
in the integration-test setup README.

### 8.4 WorkManager in tests

```kotlin
@get:Rule val workManagerRule = WorkManagerTestInitHelper.initializeTestWorkManager(...)
```

Drives synchronous execution; assertions on worker outputs without
real async waits.

## 9. Test data builders (composable)

For building elaborate scenarios without copy-pasting setup:

```kotlin
val scenario = TestScenario {
    accounts {
        +Account(issuer = "Kotak", last4 = "0000", kind = SAVINGS)
        +Account(issuer = "Axis", last4 = "1111", kind = CREDIT_CARD)
    }
    recipientRules {
        +SeedRules.CRED_AXIS
        +Rule(pattern = "myself-1@oksbi", reclassifyAs = SELF_TRANSFER,
              accountLast4 = "2222")
    }
    smsLog {
        +Fixtures.kotakUpiDebit(amount = 1000.0, recipient = "cred.club@axisb")
    }
    budgets {
        +Budget(monthKey = "2026-04", limit = 30000.0)
    }
}.seedInto(db)
```

Reusable across business-logic and integration tests. Lives in
`app/src/test/kotlin/fixtures/`.

## 10. CI pipeline

### 10.1 Shape

GitHub Actions workflow (assumption — swap for whatever CI runs
locally):

```yaml
on: [push, pull_request]
jobs:
  jvm:
    steps:
      - setup JDK 17
      - ./gradlew testDebugUnitTest
      - ./gradlew paparazziDebug   # snapshot compare
      - ./gradlew koverHtmlReport  # coverage
      - parser parity (§3.3)
      - upload kover report artifact

  android:
    runs-on: ubuntu-latest (with KVM for emulator)
    steps:
      - setup JDK + Android SDK
      - launch API-34 emulator
      - ./gradlew connectedDebugAndroidTest
```

Each job fails fast on any test failure. PRs require both green to
merge.

### 10.2 PR-level noise control

- Snapshot updates are flagged in the PR description as "⚠ snapshot
  updates — please eyeball."
- Coverage reports are commented on the PR only when thresholds drop.
- Flaky tests are quarantined (see §11).

### 10.3 Nightly job

- Full feasibility-dataset smoke (both Python parser and Kotlin parser,
  assert parity + regression against labels).
- Extended property-based runs (10× the default iterations).

## 11. Flakiness management

### 11.1 Quarantine

A test flagged `@Tag("flaky")` is excluded from the required CI
check. Appears in a separate report. Must be fixed or deleted within
7 days — PRs that add new `@Tag("flaky")` without a linked issue are
rejected.

### 11.2 Root-cause discipline

Flaky tests are bugs in the test or the code — never "just how it is."
Common fixes:
- Replace sleeps with `IdlingResource`.
- Use `TestDispatcher` for coroutines, not `Dispatchers.IO`.
- Seed random sources (no `System.currentTimeMillis()` in tests
  without `TestClock` injection).

### 11.3 No retry policies

A test that passes on retry is still failing. No `retryFailedTests`
Gradle flag.

## 12. Local developer experience

### 12.1 Fast feedback loop

```
./gradlew test                          # unit only, ~30s
./gradlew testDebugUnitTest --tests "*Parser*"   # parser only, ~5s
```

Unit tests must stay <30 s total for the whole app. If they creep
past, profile and fix — not raise the budget.

### 12.2 Pre-push hook

A git pre-push hook (opt-in, not enforced in the repo):
```
./gradlew testDebugUnitTest parserParityReport
```
Saves a broken CI round-trip for common mistakes.

### 12.3 Watch mode

```
./gradlew test -t    # Gradle continuous build; re-runs on file change
```

Works out of the box; no extra tooling.

## 13. Test naming

```kotlin
@Test fun `<subject> <behavior> when <condition>`()
```

Examples:
- `` `classifier reclassifies UPI_PAYMENT as SELF_TRANSFER when recipient matches registered account` ``
- `` `budget threshold fires exactly once when spend crosses 80pct` ``
- `` `forex worker leaves transaction pending when API returns 429` ``

Backticks instead of camelCase — test output reads like a spec.

## 14. Documentation-test link

Every `@Test` that maps to a spec invariant carries a comment:

```kotlin
// business-logic-spec §9.3 — exactly one primary
@Test fun `merge flips primary when more authoritative source arrives`() { ... }
```

No tooling enforces the link; code review catches mismatches. The spec
doc stays human-readable; the test code stays actionable.

## 15. What we are NOT testing

Explicit non-goals:

- **Gradle build script internals.** We trust Gradle.
- **Kotlin stdlib / Room internals.** Not our code.
- **Notification channel registration order across every OEM.**
  Manual-verify on representative OEMs (§16).
- **SMS spoofing.** Documented risk in
  [ingestion-spec.md](./ingestion-spec.md) §12; no automated test.
- **Upgrades from arbitrary prior versions.** v1 → v1.1 migration
  paths have tests; earlier trajectories don't exist.

## 16. Manual verify checklist (per release)

- [ ] Send a real SMS from another phone; dashboard reflects it in <5s.
- [ ] Revoke SMS permission in Settings; open app; banner appears.
- [ ] Re-grant; next inbound SMS is captured.
- [ ] Trigger historical import; notification shows progress; dashboard
      updates as rows land.
- [ ] Force-stop during import; re-launch; import resumes.
- [ ] Fill device storage to <100 MB free; try import; graceful fail
      notification.
- [ ] Dark mode renders correctly across all screens.
- [ ] TalkBack reads each screen reasonably.
- [ ] Rotate device (landscape) — phone-only v1 falls back gracefully,
      doesn't crash.
- [ ] Budget alerts appear silently in shade at default importance.
- [ ] CSV export opens in a spreadsheet without quoting corruption.
- [ ] Forex transaction: airplane-mode at insert, re-enable network,
      worker fills within 1 minute.
- [ ] Trigger purge; preview count matches; deletion count matches;
      transactions view unaffected.
- [ ] Install an older APK with prior DB version; upgrade; data survives.

Signed off per release.

## 17. Decisions (resolved 2026-04-15)

- **CI target: GitHub Actions.** Workflow shape in §10 applies as-is.
- **Minimum Android API: 28 (Android 9, 2018).** Covers >95% of
  in-market devices. Modern notification channels + runtime
  permissions available without back-compat shims.
- **Release cadence: monthly for v1.x.** Manual-verify checklist
  (§16) runs once per release.
- **Test tooling: Paparazzi (snapshots), Kover (coverage), Kotest
  (property-based + assertions), Mockk (mocking), JUnit5 (runner),
  AndroidX Test + Compose Test (instrumentation), OkHttp MockWebServer
  (HTTP).**
