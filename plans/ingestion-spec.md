# SpendWise — Ingestion Specification (v1)

How SMSes get from Android's telephony stack into `sms_log`. Covers
real-time receive, historical import, deduplication, retry, and
permission handling.

Companion docs: [spendwise-plan.md](./spendwise-plan.md) §4, §11.5,
[parser-spec.md](./parser-spec.md), [schema.md](./schema.md),
[business-logic-spec.md](./business-logic-spec.md).

---

## 1. Purpose

Ensure every financial-SMS-shaped message the device receives — live or
historical — lands exactly once in `sms_log` with its raw body intact.
Parser classification happens synchronously at insert time; downstream
transaction creation and budget updates are separate (see
[business-logic-spec.md](./business-logic-spec.md)).

## 2. Non-goals

- The ingestion layer does not decide classification semantics — the
  parser owns that.
- It does not update `transactions`, fire alerts, or compute budget
  state. Those are downstream of the `sms_log` insert.
- It does not fetch exchange rates, call any network API, or talk to
  the server (there is no server).
- It does not pre-filter non-financial senders aggressively. The
  principle is still "save everything." Filtering is a soft hint only
  (see §6).

## 3. Permissions

Android treats SMS permissions as **dangerous / runtime** + subject to
Play Store review:

- `android.permission.RECEIVE_SMS` — required for the `BroadcastReceiver`
  to fire on new SMS delivery. Real-time ingestion depends on this.
- `android.permission.READ_SMS` — required to query `content://sms` for
  historical import (§7).

### Permission lifecycle

1. **Install:** both declared in `AndroidManifest.xml`. Neither granted.
2. **First launch:** the onboarding screen explains what SMS access is
   used for, then prompts for both. User may grant one or both or
   neither.
3. **RECEIVE_SMS denied → no real-time ingestion.** App still functional
   via manual historical import (which itself needs READ_SMS). Display a
   persistent banner: "Real-time tracking is off. Tap to enable."
4. **READ_SMS denied → no historical import.** Settings screen disables
   the "Import past SMS" action. Real-time still works if RECEIVE_SMS is
   granted.
5. **Both denied → degraded.** App runs, shows previously-stored data,
   but cannot ingest new SMSes. Full-screen explainer with a retry CTA.
6. **Permission revoked mid-use (Settings → App permissions):** next
   time the app resumes, the onboarding banner reappears. Data already
   in `sms_log` is untouched.

### Play Store reality

Google restricts SMS permissions to apps with specific use cases. A
personal tracker that reads financial SMSes may not qualify. **Expected
distribution:** sideload APK or user-built debug builds. Play Store
submission is out of scope for v1.

## 4. Types

```kotlin
data class RawSms(
    val androidSmsId: Long,       // _id from content://sms
    val sender: String,
    val body: String,
    val receivedAtMs: Long,       // `date` from content://sms (delivery ts)
    val source: SmsSource,
)

enum class SmsSource {
    SMS_REALTIME,       // from BroadcastReceiver
    SMS_IMPORT,         // from historical import worker
    STATEMENT_PDF,      // §10.1 future scope
    STATEMENT_CSV,      // §10.1 future scope
    MANUAL,             // §9 ruled out v1, column reserved
}
```

## 5. Real-time ingestion

### 5.1 The receiver

```kotlin
class SpendWiseSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { ... }
}
```

Manifest-registered (not dynamic) with:
```xml
<receiver android:name=".SpendWiseSmsReceiver" android:exported="true">
    <intent-filter android:priority="999">
        <action android:name="android.provider.Telephony.SMS_RECEIVED"/>
    </intent-filter>
</receiver>
```

- `android:exported="true"` is mandatory — the system fires this intent.
- `android:priority="999"` keeps us high in the chain but not abusive;
  we do not abort the broadcast (we're not the default SMS app).

### 5.2 Handler contract

On each `SMS_RECEIVED` broadcast:

1. Extract SMS PDUs → `(sender, body, timestamp)` tuples (may be
   multiple for concatenated/multi-part SMSes; concatenate bodies in
   order).
2. Query `content://sms/inbox` with `_id` filter to recover the
   `androidSmsId` assigned by the system. **If the row hasn't been
   committed to the content provider yet** (race condition, ~100ms
   window), fall back to `androidSmsId = -1` and re-resolve on next
   launch. Ingestion must never block on this lookup.
3. Build `RawSms(source = SMS_REALTIME)`.
4. Hand off to `IngestionService` (§5.3).

The receiver itself does **no** parsing, DB I/O, or parser calls. Its
contract is: receive → dispatch → return in <5 ms.

### 5.3 `IngestionService`

A foreground-allowed (but not forced-foreground) service that processes
`RawSms` items from the receiver. Implementation can be a `Service`
with a bounded executor, or a `WorkManager` enqueue — either works
provided the contract below holds.

**Contract:**

1. **Dedup first.** If `androidSmsId != -1` and an `sms_log` row exists
   with that id → drop silently (no insert, no log line at INFO). The
   most common reason: historical import already ingested this row, now
   the real-time receiver is firing for the same message. Idempotent.
2. **Parse.** Call `Parser.parse(SmsInput(sender, body))`.
3. **Insert** into `sms_log` with:
   - `android_sms_id` = the id (nullable if -1 — requires the column to
     be `NULLABLE UNIQUE` where NULL is allowed multiple times).
   - `classification` = parser result's classification.
   - `pattern_matched` = parser result's pattern (null if UNMATCHED).
   - `source = SMS_REALTIME`.
4. **Downstream fanout.** Hand the new `sms_log.id` to the classifier
   layer (see [business-logic-spec.md](./business-logic-spec.md)). That
   layer decides whether to create/merge `transactions`, fire alerts, etc.
5. **Return** within 1 second of the original receive. Any work that
   could block longer is deferred to WorkManager.

### 5.4 Receiver + service race conditions

The receiver may fire before the system has written the SMS into
`content://sms`. The service must tolerate either ordering:

- **Receiver first, DB commit second:** service inserts with
  `androidSmsId = -1`. On next launch, a reconciler pass (§8) fills in
  the id by matching `(sender, body, received_at_ms ± 2s)`.
- **DB commit first (race resolved fast):** service reads the id
  immediately; standard path.

**Never** reject an SMS because we couldn't look up the id. The body is
what matters; the id is a convenience for dedup.

## 6. Sender filtering

**Default behavior: save everything** (no sender filter). This upholds
the plan's "save everything" principle and ensures no financial sender
format change silently causes us to lose data.

**Hard skip (non-negotiable):**
- SMSes sent *from* the device (outgoing) — `type=2` in
  `content://sms`. We only ingest inbox messages (`type=1`).

That's it for hard rules. Everything from the inbox passes ingestion.

### 6.1 Privacy escape hatch — purge in Settings

To address DB-size and privacy concerns, Settings offers:

**"Remove non-financial SMSes older than N days"** (user-initiated, not
automatic). Where "non-financial" = `sms_log.classification` is one of:
`NON_FINANCIAL`, `OTP`, `UNMATCHED`-from-a-non-financial-sender.

- Default N suggestion: 90 days.
- Action is confirmed with a preview count: "This will delete 3,421
  stored messages (47 MB). Your transaction history and budget data are
  unaffected."
- Deletes from `sms_log` (cascading to `transaction_sms_sources` via FK,
  though non-financials shouldn't have any source rows).
- Irreversible unless re-imported from the Android SMS inbox (which
  still has them).

### 6.2 Why no automatic purge

- Would contradict "save everything" as a default posture.
- A future parser improvement may reclassify a message: what was
  `UNMATCHED` today could be `CC_TRANSACTION` tomorrow. Auto-purging
  the raw body forfeits that recovery path.
- Storage is cheap; user attention is not — make deletion an
  intentional action.

### 6.3 Historical import matches this posture

Historical import (§7) uses the same "no filter" rule: it imports every
inbox SMS in the selected date range. The parser assigns classification
at insert time; the user can later purge via §6.1 if desired.

## 7. Historical import

### 7.1 Trigger

User-initiated from Settings → "Import past SMS." Never automatic, never
on first launch (per plan §11.5).

### 7.2 UX contract

1. User picks a start date (date picker; default: first day of current
   month).
2. App queries the SMS content provider with `date >= startMs AND type=1`
   and counts rows. Shown: "Found 1,000 messages since Jan 1, 2026."
3. User taps "Import." A `WorkManager` worker is enqueued.
4. Persistent foreground notification shows progress: "Importing: 340 /
   1,000 (27%)."
5. App remains fully usable during import. Dashboard reflects newly
   imported transactions as they arrive (Flow-driven UI per plan §3.3).
6. On completion, summary sheet:
   - Imported N messages.
   - X classified as spend / refund / credit.
   - Y dropped (bill payments, OTPs, admin).
   - Z unmatched — with a "Review unmatched" link that opens a list.

### 7.3 Worker contract

```kotlin
class HistoricalImportWorker : CoroutineWorker {
    suspend fun doWork(): Result { ... }
}
```

**Inputs:** `startMs` (earliest date to include), `endMs` (default =
current time).

**Algorithm:**

```
cursor = query content://sms/inbox
        where date >= startMs AND date <= endMs AND type = 1
        order by date ASC
checkpoint = readCheckpoint()    // last-processed android_sms_id for this job
batch = []
for each row in cursor:
    if row._id <= checkpoint: continue   // already processed
    batch.add(row)
    if batch.size >= 50:
        processBatch(batch)
        writeCheckpoint(batch.last._id)
        batch.clear()
processBatch(batch)
writeCheckpoint(latest)
```

**`processBatch`:**
- Start a single DB transaction.
- For each row: dedup by `android_sms_id`; if new, parse + insert into
  `sms_log`.
- Commit.
- Hand new `sms_log.id`s to the classifier layer for `transactions`
  creation. This happens **outside** the DB transaction — classifier
  errors must not roll back the raw capture.

**Failure modes:**

- Worker killed mid-batch → next run reads checkpoint, resumes.
- DB transaction fails → batch is lost; on resume, checkpoint is still
  the previous good one, so we retry.
- Classifier throws on some row → that row's `sms_log` is kept;
  `transactions` insert is retried in a deferred work item. Raw capture
  must never be lost.

### 7.4 Restartability

Checkpoint is stored in the `work_state` table (or `WorkManager`'s
input-data if easier to reason about), keyed by worker instance id.

Worker retry policy (`WorkManager`):
- Network: NOT_REQUIRED (no network use).
- Battery: expedited OK but not forced. Import is user-initiated, not
  urgent.
- Backoff: exponential, 30s initial, max 1hr.

### 7.5 Volume estimates

Rule of thumb: a heavy user's device receives ~**200 SMSes per
month**. A 2-year import = ~5,000 SMSes,
expected complete in well under 30s on a mid-range device. The worker
tiers (50-per-batch) are for 20K+ inboxes; small imports run in one
batch.

### 7.6 Re-import semantics

Re-running import over the same date range is safe and idempotent:

- `android_sms_id` unique constraint prevents double-insert.
- If the parser changed since the original import, previously-inserted
  rows still have their original `classification` — the importer doesn't
  re-parse existing rows.
- If the user previously purged non-financial SMSes (§6.1) and
  re-imports, those rows come back into `sms_log`. Purge is one-shot,
  not persistent state.
- To re-parse, the user triggers Settings → "Rerun parser on stored
  messages" (a separate, independent action; see §9).

## 8. Reconciliation pass

On every app launch, after a brief delay (3 s) to not block startup:

1. **Fill missing `android_sms_id`.** For each `sms_log` row with
   `android_sms_id IS NULL`, query `content://sms` for candidates
   matching `(sender, body, received_at_ms ± 2s)`. If one match → fill
   in the id. Zero or multiple matches → leave null, log a debug line.
2. **Drop orphaned `transaction_sms_sources`.** If any join-table row
   references a non-existent `sms_log.id` (possible after a botched
   migration or a restore), remove the join row and log at WARN.

Reconciliation is idempotent and cheap (<100 rows in normal use).

## 9. Reparse flow

When the user updates to a release with new parser rules, they can
optionally trigger a reparse from Settings:

1. Warning: "This will re-classify all imported SMSes using the new
   parser. Transactions may be re-categorized. This is reversible by
   reinstalling the previous app version."
2. User confirms.
3. A `ReparseWorker` runs:
   - For each `sms_log` row, re-call `Parser.parse`.
   - If classification changes → update `sms_log.classification` and
     `pattern_matched`.
   - Set `reparsed_at_ms = now`.
   - Trigger a transactions-rebuild step (hand off to classifier).
4. Summary sheet: "Reparsed N; M reclassified."

**Reparse never deletes transactions** — it may null out
`transaction_sms_sources` entries whose `sms_log` row no longer maps to
the same classification; the classifier then recomputes transactions
from scratch.

Reparse is opt-in. Upgrades don't auto-reparse: user data is the point.

## 10. Invariants

1. **`android_sms_id` uniqueness.** Never two rows with the same non-null
   id. Enforced at DB level.
2. **Raw body is never transformed before storage.** No trimming, no
   case-folding, no encoding normalization. The body stored must
   round-trip losslessly back to what Android delivered.
3. **One and only one `sms_log` row per actual SMS.** Regardless of how
   many times ingestion runs.
4. **`source` never changes once set.** An SMS imported via
   `SMS_IMPORT` keeps that source forever, even if the real-time
   receiver later sees the same message (dedup drops the duplicate
   before insert).
5. **Ingestion layer does not care what the parser returns.** Even
   UNMATCHED or NON_FINANCIAL messages are inserted.

## 11. Error handling

| Condition                            | Behavior                         |
|--------------------------------------|----------------------------------|
| `BroadcastReceiver` throws           | Logged at ERROR, broadcast swallowed. Android would otherwise kill the process. Receiver code stays minimal to reduce surface area. |
| `content://sms` query fails          | Import aborts, user shown "Unable to read SMS inbox — permission may have been revoked." |
| DB full                              | Import batches fail; retry backs off. User notified via persistent notification; remedy is to free storage. |
| Parser throws (should not happen)    | Caught at ingestion boundary; `sms_log` row inserted with `classification = UNMATCHED`, `pattern_matched = "parser_error"`. Stack trace in Logcat only. |
| Duplicate `android_sms_id` insert    | DB rejects via unique constraint; caught, dropped silently. Expected path. |
| Worker killed mid-batch              | Resumes from checkpoint on next run. |
| Worker fails 5× in a row             | WorkManager marks failed; user notified; import can be retried. Partial data stays in `sms_log`. |
| Permission revoked mid-import        | Next content://sms query fails; worker aborts with the permission-denied error path above. |

## 12. Security considerations

SMS is a **spoofable rail**. The ingestion layer does not authenticate
senders. Mitigations:

- The parser matches on body shape + sender prefix. A spoofed SMS
  pretending to be HDFC but with a non-HDFC body format → UNMATCHED.
- A spoofed SMS with correct body format fires a transaction. If the
  user notices an unexpected transaction, they can override
  classification (see `transactions.manually_adjusted`) and/or add a
  `recipient_rules` entry to ignore the sender.
- Stored SMS bodies may contain OTP codes, account numbers, amounts,
  personal UPI IDs. The DB file is in the app's private storage
  (standard Android); Room does not encrypt by default.

**Future consideration:** encrypt the `sms_log.body` column via
Android Keystore-backed SQLCipher. Deferred past v1.

## 13. Testing contract

### 13.1 Unit-testable components

Dedup logic is a pure function over `(newId, existingIds)` → `Boolean`.
Outgoing-type skip is a one-line predicate. Purge eligibility
(`isPurgeEligible(classification, sender)`) is a pure function.

These have full unit coverage:

```kotlin
@Test fun `outgoing SMS (type=2) is always skipped`()
@Test fun `incoming SMS (type=1) is always ingested`()
@Test fun `duplicate android_sms_id is rejected by dedup`()
@Test fun `null android_sms_id skips dedup check`()
@Test fun `NON_FINANCIAL is purge-eligible`()
@Test fun `OTP is purge-eligible`()
@Test fun `UNMATCHED from financial-looking sender is NOT purge-eligible`()
@Test fun `CC_TRANSACTION is never purge-eligible regardless of age`()
```

### 13.2 Integration-tested components

These require Android instrumentation (emulator or device):

- Receiver registration and broadcast dispatch
- `content://sms` query behavior
- Permission denial paths
- `WorkManager` scheduling and retry
- Full end-to-end: deliver a fake SMS via the Telephony test API,
  assert `sms_log` grows by one

Instrumentation tests live in `androidTest/`.

### 13.3 Feasibility-dataset regression

The historical import path is tested by:
1. Preload an empty Room DB.
2. Mock `content://sms` to return the 656 feasibility SMSes.
3. Run the import worker.
4. Assert `sms_log.count == 656`.
5. Assert the per-classification histogram matches the labeled
   distribution exactly.

### 13.4 Resume correctness test

1. Start import worker with the 656-SMS mock.
2. After 200 SMSes processed, forcibly cancel the worker.
3. Restart the worker.
4. Assert final count is still 656 (no duplicates, no missed).

### 13.5 Permission-state matrix

Each of the permission states (both granted, RECEIVE only, READ only,
neither) gets an integration test asserting the UI banner state and
the subset of features available.

## 14. Testability posture

| Behavior                                      | Category      |
|-----------------------------------------------|---------------|
| Sender filter predicate                       | Strict TDD    |
| Dedup predicate                               | Strict TDD    |
| Batch-processing algorithm                    | Strict TDD (with fakes) |
| Reconciler                                    | Strict TDD (with fakes) |
| `BroadcastReceiver` wiring                    | Integration   |
| `WorkManager` scheduling + retry              | Integration   |
| Permission flows                              | Integration   |
| Real device SMS delivery                      | Manual verify |
| Play Store review passage                     | N/A (won't submit) |

## 15. Coverage posture

- **Ingestion module line coverage: ≥95%.** The 5% gap is Android
  framework-touching code (receiver `onReceive`, content-provider
  queries) that can only be integration-tested.
- **Resume/checkpoint logic: 100% branch coverage.** Every combination
  of (empty-state, partial-state, completed-state) exercised by at
  least one test.

## 16. Decisions (resolved 2026-04-15)

- **Reconciler delay:** no special `EXACT_ALARM` handling needed. The
  reconciler is a coroutine, not an alarm. Confirm during implementation
  that Android 12+ doesn't surface a warning; if it does, revisit.
- **Transactions during import:** surface live as the worker progresses.
  The dashboard Flow emits on every `transactions` insert; no extra work
  needed beyond the already-specified Flow-backed UI.
- **Classifier rate-limiting:** no rate limit in v1. Profile only if a
  real import shows degradation.
