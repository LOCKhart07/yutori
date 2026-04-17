# Yutori — Error States Specification (v1)

The canonical list of every degraded path and failure mode, with the
detection, behavior, UX, and recovery for each. Other specs reference
this doc instead of repeating the handling inline.

Companion docs: [ingestion-spec.md](./ingestion-spec.md),
[business-logic-spec.md](./business-logic-spec.md),
[ui-spec.md](./ui-spec.md),
[settings-spec.md](./settings-spec.md).

---

## 1. Philosophy

1. **Fail loudly on budget-affecting errors.** A silent miscount is
   worse than a crash. Users who can see something is wrong can work
   around it; users who can't see it trust the wrong number.

2. **Fail silently on audit-only errors.** A reconciler can't fill a
   missing `android_sms_id` — that's a Logcat line, not a toast.

3. **No analytics / crash telemetry.** Everything stays on-device.
   Errors are logged to Logcat at appropriate levels and surfaced in
   the UI when the user needs to act.

4. **Recovery paths are documented, not automated.** The user is the
   last line of defense; give them a clear remedy, don't try to
   self-heal opaquely.

5. **Invariants from other specs are assertable.** Every invariant in
   [business-logic-spec.md](./business-logic-spec.md) §9 and
   [ingestion-spec.md](./ingestion-spec.md) §10 has a check that can
   run in debug builds; a violation is a developer-visible crash, not
   a user-facing error.

## 2. Permission errors

### 2.1 RECEIVE_SMS denied

- **Detection:** `ContextCompat.checkSelfPermission` on resume.
- **Behavior:** No `BroadcastReceiver` ever fires. App otherwise
  functional.
- **UX:** Persistent top banner on dashboard: "Real-time tracking is
  off." Tap → runtime permission request.
- **Recovery:** User grants. Banner disappears on next resume.

### 2.2 READ_SMS denied

- **Detection:** Same.
- **Behavior:** Historical import screen is disabled.
- **UX:** Settings → "Import past SMS" row shows "Permission required"
  subtitle; tap shows full-screen CTA that requests READ_SMS. No
  alternative flow.
- **Recovery:** User grants. Screen is functional on return.

### 2.3 Both denied

- **Behavior:** New SMSes don't ingest; no historical import.
- **UX:** Full-screen explainer on first launch (onboarding §4.1).
  Dashboard accessible but banner persists.
- **Recovery:** User grants at least one from Settings → Permissions
  shortcut.

### 2.4 Permission revoked mid-session

- **Detection:** `onResume` re-checks.
- **Behavior:** Any SMSes received during the revocation window are
  lost to us — Android never delivered them.
- **UX:** Banner reappears on next dashboard resume; no disruptive
  dialog. Ongoing worker (if any) fails its next `content://sms`
  query and aborts gracefully (see §5.2).

### 2.5 Permission denied permanently ("Don't ask again")

- **Detection:** `shouldShowRequestPermissionRationale` returns false
  after a denial.
- **Behavior:** Runtime prompt will no longer appear from in-app.
- **UX:** CTA becomes "Open Settings" (system settings), not "Grant."
  User toggles it there and returns.

### 2.6 Notification permission (Android 13+) denied

- **Detection:** on first alert attempt.
- **Behavior:** `NotificationCompat.notify` silently no-ops.
- **UX:** Next dashboard resume surfaces a banner: "Budget alerts are
  off. Tap to enable." No surprises — the user sees current spend
  regardless.

## 3. Parser errors

### 3.1 Parser throws (should not happen)

- **Detection:** `try/catch` at the ingestion boundary.
- **Behavior:** The SMS is still inserted into `sms_log` with
  `classification = UNMATCHED` and `pattern_matched = "parser_error"`.
  Stack trace logged at ERROR to Logcat only.
- **UX:** None. The SMS behaves like any other UNMATCHED row.
- **Recovery:** Next app release with a fixed parser + rerun parser
  (Settings §5) reclassifies.
- **Test:** `@Test fun parser throws is caught and does not drop the SMS`.

### 3.2 UNMATCHED from a financial-looking sender

- **Detection:** Ingestion-time check — `classification == UNMATCHED`
  AND `sender matches KOTAKB|AXISBK|ICICI|HDFC|SBI|UPI`.
- **Behavior:** Normal insert. WARN log in debug builds.
- **UX:** Post-import summary surfaces the count. Dashboard itself
  does not highlight individual cases (would be noisy).
- **Recovery:** User can report via "Review unmatched" list
  ([settings-spec.md](./settings-spec.md) §4.5).

### 3.3 Parser-Python-Kotlin parity drift (dev-only)

- **Detection:** CI parity test.
- **Behavior:** Build fails.
- **UX:** n/a (caught before release).

## 4. Classifier errors

### 4.1 Classifier throws on a single row

- **Detection:** `try/catch` per row in the batch loop.
- **Behavior:** The offending `sms_log` row stays with its parser-
  assigned classification. No `transactions` row created. Batch
  continues with the next row. Stack trace to Logcat.
- **UX:** None. In debug builds, a WARN-level log helps diagnosis.
- **Recovery:** User-triggered reparse after a fix ships.

### 4.2 Account resolver finds ambiguous `last4`

- **Detection:** Multiple `accounts` rows share the same `last4`
  (possible if issuer differs — e.g., both Axis savings and Axis CC
  have a coincidentally overlapping last4, or user entered a duplicate
  across issuers).
- **Behavior:** Prefer the account whose `kind` matches the
  classification type (CC_TRANSACTION → CREDIT_CARD account; UPI_PAYMENT
  → SAVINGS / INVESTMENT). If still ambiguous, fall back to
  `is_default_spend = true`. If no match, leave `account_id = null`.
- **UX:** None in v1 MVP. v1.1 could surface an "ambiguous account"
  list in Settings.
- **Recovery:** User edits accounts to disambiguate, reparses.

### 4.3 Recipient rule regex fails to compile

- **Detection:** At rule-save time in settings (§3.5). Also at
  classifier load time, as a defense in depth.
- **Behavior:** Save rejected with inline error at save time. If a
  previously-saved rule becomes invalid (e.g., an engine change), the
  classifier skips it and WARN-logs; other rules still apply.
- **UX:** Save-time inline error. Classifier-time silent.
- **Recovery:** User edits the rule.

## 5. Database errors

### 5.1 Room migration failure on app upgrade

- **Detection:** Room's `onCreate` / `onUpgrade` throws.
- **Behavior:** App refuses to start normally. No data access.
- **UX:** Full-screen error: "Yutori couldn't upgrade your data.
  This shouldn't happen. Please reinstall from backup, or if that's
  not an option, clear app data to start fresh (your Android SMS inbox
  is untouched)." Two buttons: "Copy error details" and "Clear app
  data."
- **Recovery:** Clearing data removes the DB; next launch is
  onboarding. All historical transactions lost — user can re-import
  from SMS inbox (§4 settings-spec).
- **Prevention:** Every migration has a test. `fallbackToDestructiveMigration`
  is **not** enabled.

### 5.2 DB constraint violation (expected race)

- **Detection:** `SQLiteConstraintException` on insert.
- **Behavior:** Caught, treated as idempotent dedup success. The
  duplicate path in [ingestion-spec.md](./ingestion-spec.md) §5.3 is
  the canonical case.
- **UX:** None.
- **Recovery:** n/a — the "error" is the feature.

### 5.3 DB constraint violation (unexpected)

- **Detection:** `SQLiteConstraintException` thrown where dedup
  shouldn't apply.
- **Behavior:** Row drop, ERROR log with full context.
- **UX:** Invisible. If it happens repeatedly (e.g., a schema bug),
  the count shows up in the post-import summary as a mismatch between
  "Scanned N" and "Imported M."
- **Recovery:** Developer sees the log, fixes in next release.

### 5.4 Disk full

- **Detection:** `SQLiteFullException` on any write.
- **Behavior:** The operation fails; the transaction rolls back. For
  worker operations, the worker retries with backoff; eventually
  WorkManager marks it failed.
- **UX:** Persistent notification: "Yutori can't save — device
  storage is full." Dashboard still shows existing data.
- **Recovery:** User frees storage. Ingestion / import resumes on next
  trigger.

### 5.5 DB corruption

- **Detection:** `SQLiteDatabaseCorruptException`.
- **Behavior:** Room throws; app enters the full-screen error state of
  §5.1. Auto-backup (Android default) may have a usable older copy.
- **UX:** Same error screen as migration failure. "Clear app data" is
  the universal recovery.

## 6. Worker errors

### 6.1 Worker killed mid-run

- **Detection:** `WorkManager` observes the crash; marks the work as
  failed and schedules retry per backoff policy.
- **Behavior:** Checkpoint (per [ingestion-spec.md](./ingestion-spec.md)
  §7.4) is preserved. Retry resumes from checkpoint.
- **UX:** Progress notification disappears briefly, reappears when
  retry starts. User sees no data loss.

### 6.2 Worker fails N consecutive times

- **Detection:** WorkManager backoff exhausts.
- **Behavior:** Work marked failed. User-triggered operations stop
  retrying.
- **UX:** For import: "Import couldn't complete. Tap to retry." For
  reparse: same. For forex: pending transactions stay pending; shown
  on dashboard banner.
- **Recovery:** User taps retry.

### 6.3 Worker cancelled by user

- **Detection:** `CoroutineWorker` respects cancellation.
- **Behavior:** Current batch completes (atomic DB transaction), then
  stops. Checkpoint is at the last completed batch.
- **UX:** Worker's progress notification is replaced by a "Cancelled"
  summary.
- **Recovery:** User may re-trigger the operation; it resumes from
  checkpoint.

### 6.4 Two workers tried to run simultaneously on overlapping data

- **Detection:** WorkManager's `ExistingWorkPolicy.KEEP` on enqueue;
  named-work uniqueness constraint.
- **Behavior:** Second enqueue no-ops. The first worker continues
  uninterrupted.
- **UX:** If the user triggered the second (e.g., tapped "Import"
  twice), a toast: "An import is already in progress."

## 7. Ingestion race conditions

### 7.1 Receiver fires before `content://sms` commits

- **Detection:** Receiver queries `content://sms` by the inbound
  `(sender, body, receivedAtMs)`; finds no row.
- **Behavior:** Insert with `android_sms_id = NULL`. Reconciler
  fills it on next launch.
- **UX:** None.
- **Test:** Integration — fake the race via mocked ContentProvider.

### 7.2 Historical import ingests a row the receiver is about to see

- **Detection:** Dedup on `android_sms_id` (already assigned by the
  content provider at historical-import time).
- **Behavior:** Receiver's insert attempt raises
  `SQLiteConstraintException`; handled per §5.2.
- **UX:** None.

### 7.3 Multiple real-time broadcasts for the same SMS

- **Detection:** Rare but observed on some OEMs.
- **Behavior:** Dedup on `android_sms_id`. Second insert fails silently.
- **UX:** None.

### 7.4 Concatenated multi-part SMS delivered out of order

- **Detection:** Android usually reassembles, but if our receiver sees
  both parts before Android's reassembly, we may see two "partial"
  bodies.
- **Behavior:** The PDU extraction code concatenates parts by reference
  number and sequence index before delivering to ingestion.
- **Recovery path:** If reassembly fails, we store each part
  separately. Parser will likely UNMATCH both. A later rerun with
  improved PDU handling recovers.

## 8. Time and timezone errors

### 8.1 Clock rolled back

- **Detection:** App startup compares `System.currentTimeMillis()` with
  `max(received_at_ms)` in `sms_log`. If current time is >1 hour
  earlier than the max, we suspect a clock issue.
- **Behavior:** Budget math unchanged (uses `occurred_at_ms` from
  SMS, not device clock). Alert state machine uses the SMS's
  `occurred_at_ms` for month-key determination.
- **UX:** Debug log; no user-visible change.

### 8.2 Timezone changed mid-month

- **Detection:** Each transaction is stamped with `month_key` at insert
  time, computed from the device's timezone then. That stamp is
  immutable.
- **Behavior:** Transactions inserted before the TZ change keep their
  old `month_key`. Transactions inserted after use the new TZ.
- **UX:** None. The dashboard may show a 1-off stat (e.g., a
  transaction near month boundary assigned to the "wrong" month from
  the new-TZ perspective).
- **Recovery:** None needed in v1. v2 could offer a "re-bucket by
  current TZ" settings action.

### 8.3 SMS timestamp in the future

- **Detection:** `received_at_ms > now() + 1 day` at ingestion.
- **Behavior:** Logged at WARN; the row is still stored with the
  future timestamp.
- **UX:** The transaction appears in a future month. Budget for that
  month will be affected when the date arrives.
- **Recovery:** User can manually correct via v1.1's edit UX. In v1
  MVP, they wait.

## 9. Forex errors

### 9.1 No network when forex conversion needed

- **Detection:** `IOException` from OkHttp/URLConnection.
- **Behavior:** Transaction stays pending. Worker retries per
  [business-logic-spec.md](./business-logic-spec.md) §5.2.
- **UX:** Pending-FX banner on dashboard.
- **Recovery:** Automatic on next network availability.

### 9.2 API quota exhausted (HTTP 429)

- **Detection:** Response code.
- **Behavior:** Back off progressively: 1 h → 6 h → 24 h.
- **UX:** Pending-FX banner. After 24h, a low-importance
  notification ("forex conversion delayed — free-tier quota reached").
- **Recovery:** Quota resets on the calendar month boundary for
  exchangerate-api.com free tier.

### 9.3 API returns invalid rate (0 / NaN / negative)

- **Detection:** Sanity check post-parse.
- **Behavior:** Treated as failure; retry per §9.1.
- **UX:** Pending-FX banner.

### 9.4 API returns unknown currency

- **Detection:** Rate not present in response for our requested currency.
- **Behavior:** Transaction stays pending. WARN log.
- **UX:** Pending-FX banner persists beyond usual retries.
  v1.1: user can enter rate manually from the transaction detail
  screen.

## 10. User-triggered error paths

### 10.1 User deletes an account referenced by many transactions

- **Detection:** Count of transactions with `account_id = this.id`.
- **Behavior:** Cascade-delete only the account's `recipient_rules`
  entries. Transactions keep their `last4` and `issuer`; their
  `account_id` is NULLed. No classification change.
- **UX:** Confirm dialog mentions transaction count: "Delete account?
  47 transactions currently reference this account; they'll stay in
  your history."
- **Recovery:** User adds the account back (manually re-enters). Past
  transactions stay NULLed; a reparse could re-link them.

### 10.2 User disables the default spend account

- **Detection:** Settings save time.
- **Behavior:** Save succeeds. If no account is marked default, the
  classifier's ambiguity fallback (§4.2) reverts to "leave null."
- **UX:** Inline hint on the toggle: "Marking another account as
  default will unmark this one."

### 10.3 User triggers purge while import is running

- **Detection:** Settings purge screen checks for active import worker.
- **Behavior:** Purge CTA disabled with tooltip: "Can't purge while
  import is in progress."
- **UX:** Tooltip on hover / tap.

### 10.4 User changes budget limit mid-month to below current spend

- **Detection:** Save time.
- **Behavior:** Save succeeds. Alert state machine sees existing
  threshold-crossings already fired (§7.4 of business-logic-spec); it
  does not re-fire them. Dashboard gauge re-renders over 100%.
- **UX:** Dashboard shows immediate overage. No notification.

## 11. Data integrity violations

### 11.1 Orphaned `transaction_sms_sources` row

- **Detection:** Reconciler pass or debug assertion (§9 of
  business-logic-spec).
- **Behavior:** Orphaned row removed, WARN log.
- **UX:** None.

### 11.2 Transaction row with no sources

- **Detection:** Debug assertion.
- **Behavior:** In debug builds, CRASH. In release builds, WARN log
  and the transaction is silently deleted.
- **UX:** Likely invisible (release path); budget recomputes.
- **Recovery:** Any missing data would surface as a mismatch between
  `sms_log` history and `transactions`; a reparse resyncs.

### 11.3 Inconsistent `budget_effect`

- **Detection:** `budget_effect != mapClassificationToEffect(classification)`.
- **Behavior:** Debug crash. Release log + overwrite to correct value.
- **UX:** Invisible in release. Next dashboard refresh reflects the
  fix.

### 11.4 Negative `inr_amount`

- **Detection:** Transaction insert validation.
- **Behavior:** Rejected; ERROR log.
- **UX:** In the rare case it comes from classifier drift, the
  contributing SMS goes un-transactioned. UNMATCHED-like behavior.

## 12. Manifest-level errors

### 12.1 App updated; new permission declared

- **Detection:** Android itself.
- **Behavior:** New permissions require user re-grant on update — the
  banner reappears.
- **UX:** Banner + onboarding §4.1 pattern.

### 12.2 Notifications channel removed

Users may have deleted the "Budget alerts" channel in system settings.
- **Detection:** `notificationManager.getNotificationChannel(id) == null`
  on alert time.
- **Behavior:** Re-create the channel before notifying.
- **UX:** None if the channel's importance is restored; may re-notify
  at default importance.

## 13. Logging and observability

### 13.1 Log levels

- ERROR — data-affecting unexpected errors (parser throw, DB constraint
  violation outside the dedup path, classifier throw).
- WARN — budget-neutral anomalies (UNMATCHED from financial sender,
  orphaned join row, forex rate NaN).
- INFO — normal operation milestones (worker start/end, permission
  state change).
- DEBUG — rule fires, reconciler matches, individual dedup decisions.

Release builds strip DEBUG and below.

### 13.2 No crash reporting service

No Firebase, no Crashlytics, no Sentry. If the app crashes, the user
sees the system "Stopped" dialog; logs exist in `adb logcat` only.

### 13.3 Debug-build assertions

Invariants from business-logic-spec §9 and ingestion-spec §10 are
checked via `check(condition) { message }` in debug builds. Release
builds strip these.

## 14. Testability posture

| Behavior | Category |
|---|---|
| Error catches at ingestion boundary | Strict TDD |
| Dedup-on-constraint-violation | Strict TDD |
| Classifier error swallow + log | Strict TDD |
| Worker retry / backoff | Integration |
| Permission-state reaction on resume | Integration |
| Forex API error paths | Strict TDD (with mocked HTTP) |
| DB migration failure screen | Manual verify |
| Disk-full behavior | Manual verify |
| Timezone / clock change effects | Strict TDD (with fake clock) |

## 15. Coverage posture

- **Error paths for every external interaction: 100% branch coverage.**
  Every `try/catch`, every `when` on error type, every retry branch.
- **UI error states: snapshot-tested** for layout; behavior tested in
  integration (permission flows, revocation).

## 16. Decisions (resolved 2026-04-15)

- **Invariant violations: `check()` in debug, `Log.e` in release.** Fast
  developer feedback during dev; safe silent correction in prod.
- **Permission revoked mid-use: banner only.** No notification. App
  config states shouldn't compete with actual user activity for
  notification attention.
- **Unmatched financial count: summary screen only.** Shown in the
  post-import summary with a "Review unmatched" link. No persistent
  dashboard indicator — nags are worse than invisible gaps the user
  can acknowledge once.
