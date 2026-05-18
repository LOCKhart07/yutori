# Yutori — In-app Autoupdater Specification (v1)

Tachiyomi-style in-app updater for the side-loaded APK distribution: on
every app cold-start, check GitHub Releases for a newer version, show a
dismissible dialog with release notes, and install via
`PackageInstaller` when the user confirms. The repo is public, so the
Releases API is hit **anonymously** — no embedded credential.

> **History.** This shipped while the repo was still private, behind a
> single embedded read-only fine-grained PAT isolated to one OkHttp
> interceptor. When the repo went public that interceptor, its
> `RELEASES_TOKEN` / `GITHUB_RELEASES_TOKEN` wiring and the repo secret
> were all removed and the PAT revoked. §5 records what that surface
> was; the spec body otherwise describes the current anonymous design.

Companion docs: [yutori-plan.md](./yutori-plan.md),
[settings-spec.md](./settings-spec.md),
[docs/RELEASING.md](../docs/RELEASING.md).

Tracks [#71](https://github.com/LOCKhart07/yutori/issues/71).
Pairs with the first-run mitigation from #57.

---

## 1. Scope

**In scope**
- Check latest release on app cold start, debounced to at most one
  check per 6 hours across cold starts.
- Manual *Check now* button in Settings.
- Update-available dialog rendering the GitHub release body as the
  in-app changelog.
- APK download to cache, install via `PackageInstaller.Session`.
- Anonymous Releases API access (public repo — no auth).
- Settings toggle to disable the on-open check (manual *Check now*
  always works).

**Out of scope**
- Periodic background polling (WorkManager). Cold-start is enough.
- Delta / patch updates.
- Silent install (requires device-owner privilege we don't have).
- Rollback.
- Pre-release / beta channel selector.
- Notification channel. No notification surface — dialog only.

## 2. Trigger and cadence

Single trigger: app cold start.

- Entry point: `ProcessLifecycleOwner.get().lifecycle` observer that
  fires on `ON_CREATE` once per process. Not `MainActivity.onCreate`
  — that fires on configuration changes and re-entries we don't care
  about.
- Debounce: SharedPrefs key `autoupdater_last_check_at` (millis).
  Skip if now − last < 6 h.
- Result lifetime: if a check finds an update and the user dismisses
  with *Later*, don't re-surface until the next cold-start check
  (i.e. after the 6 h debounce has elapsed and the process has been
  killed and restarted). No in-session nagging.
- Manual *Check now* bypasses the debounce and always hits the API.

Rationale for not using WorkManager: one user, side-loaded, killing
their own process regularly. Cold-start + debounce gives us multiple
checks per day at zero complexity cost and no background-execution
battery concerns.

## 3. Module placement

Lives in `:app` under `com.yutori.update/`:

```
:app/src/main/kotlin/com/yutori/update/
    UpdateModule.kt              # wiring (OkHttp + Moshi + repo)
    UpdateRepository.kt
    VersionComparator.kt
    UpdateDownloader.kt
    UpdateInstaller.kt
    UpdatePrefs.kt               # SharedPrefs wrapper
    UpdateCheckCoordinator.kt    # cold-start trigger + debounce
    model/
        LatestRelease.kt
        DownloadState.kt
```

Not a separate Gradle module. Needs Android `PackageInstaller`,
`PackageManager`, `SharedPreferences`, `ProcessLifecycleOwner`, and
has no consumers outside `:app`. A pure-JVM carve-out isn't worth the
module boilerplate.

`com.yutori.update.ui/` under `:app/src/main/kotlin/com/yutori/ui/`
holds the Compose surfaces (dialog + settings row) — standard location
for this project.

## 4. Components

### 4.1 `UpdateRepository`

```kotlin
class UpdateRepository(
    private val api: GithubApi,  // Retrofit
) {
    suspend fun latestRelease(): Result<LatestRelease?>
}
```

- Single endpoint:
  `GET https://api.github.com/repos/LOCKhart07/yutori/releases/latest`
- Headers (added by Retrofit annotations):
  `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2022-11-28`.
- No auth header — the request is anonymous (public repo).
- 404 handling: `Result.success(null)`. For a public repo a 404 from
  `/releases/latest` unambiguously means "no release published yet",
  so it is treated as *up to date*, never surfaced as an error.
- `Result.failure(...)` on any other non-2xx, timeout, or parse
  error. Caller logs and shows a user-facing state only for manual
  *Check now*.

### 4.2 `model.LatestRelease`

```kotlin
data class LatestRelease(
    val tagName: String,        // e.g. "v0.3.0"
    val name: String,           // release title
    val body: String,           // markdown release notes
    val asset: Asset?,          // first .apk asset, null if missing
) {
    data class Asset(
        val url: String,        // API url (NOT browser_download_url)
        val sizeBytes: Long,
        val name: String,
    )
}
```

`asset.url` is the `assets[].url` field from the Releases API
response (format: `https://api.github.com/repos/…/releases/assets/<id>`).
Called with `Accept: application/octet-stream` it returns 302 to a
presigned S3 URL. We use the API `url` rather than
`browser_download_url` for consistency with the JSON call (same host,
same anonymous client); both resolve fine for a public repo.

### 4.3 `VersionComparator`

Pure function, pure JVM. Easy to test.

```kotlin
object VersionComparator {
    fun hasUpdate(current: String, remoteTag: String): Boolean
}
```

- Strips a leading `v` from the remote tag.
- Splits both on `.` and compares as ints, left to right.
- Tags with non-numeric segments (`v0.3.0-beta`) → return `false`
  (treated as not-an-update). We don't ship prereleases on this
  channel; if we ever do, they get a separate channel.
- Example inputs handled:
  - `("0.2.0", "v0.3.0")` → true
  - `("0.2.0", "v0.2.0")` → false
  - `("0.3.0", "v0.2.0")` → false (no downgrade — covers the
    "delete the latest release" edge case in §10)
  - `("0.2.0", "v0.2.1")` → true
  - `("0.2.0", "v0.10.0")` → true (component-wise int, not
    lexicographic)

### 4.4 `UpdateDownloader`

```kotlin
class UpdateDownloader(
    private val client: OkHttpClient,
    private val cacheDir: File,
) {
    fun download(asset: LatestRelease.Asset): Flow<DownloadState>
}

sealed interface DownloadState {
    data class Progress(val bytes: Long, val total: Long) : DownloadState
    data class Done(val apk: File) : DownloadState
    data class Failed(val reason: Reason) : DownloadState

    enum class Reason { Network, Auth, NotFound, Disk, Cancelled, Unknown }
}
```

- Destination: `cacheDir/update/<tagName>.apk`. Purge any other
  `*.apk` under `cacheDir/update/` at the start of every download to
  avoid cache bloat from abandoned attempts.
- Headers on the request: `Accept: application/octet-stream`.
  Authorization header is added by the same interceptor used for the
  JSON API — not duplicated here.
- Redirect handling: OkHttp's default `followRedirects = true` is
  fine. OkHttp strips `Authorization` when the redirect target host
  differs from the origin host (see `RetryAndFollowUpInterceptor`), so
  the presigned S3 URL receives an unauthenticated request — correct,
  since the S3 URL is already signed. Verified in test (§12).
- Progress emitted every ~64 KB or 250 ms, whichever comes first.
- Errors map to `Reason` via OkHttp `Response.code` / `IOException`
  class.

### 4.5 `UpdateInstaller`

```kotlin
class UpdateInstaller(private val context: Context) {
    fun install(apk: File)
}
```

- Uses `PackageInstaller.Session`, `MODE_FULL_INSTALL`.
- Steps:
  1. `packageManager.packageInstaller.createSession(params)`
  2. `session.openWrite("base.apk", 0, apk.length())` → copy bytes
  3. `session.commit(pendingIntent)` — the `PendingIntent`
     fires once the system has shown the user its confirmation UI
     and they've tapped *Install*.
- `PendingIntent` targets an unexported `BroadcastReceiver`
  (`UpdateInstallResultReceiver`) that logs the
  `EXTRA_STATUS` result. Nothing in the UI reacts to it — the user
  either sees success (app restarts) or they see the system's own
  failure dialog.
- Requires `REQUEST_INSTALL_PACKAGES` in the manifest. If the user
  has revoked *Install unknown apps* for this app, the system shows
  its own permission prompt before the confirmation UI. We don't
  pre-check or pre-prompt — the system's flow is clearer than ours
  would be.

### 4.6 `UpdateCheckCoordinator`

```kotlin
class UpdateCheckCoordinator(
    private val repo: UpdateRepository,
    private val prefs: UpdatePrefs,
    private val clock: Clock,
    private val currentVersion: String,  // BuildConfig.VERSION_NAME
) {
    suspend fun checkIfDue(force: Boolean = false): CheckResult
}

sealed interface CheckResult {
    object Skipped : CheckResult               // debounced
    object UpToDate : CheckResult
    data class UpdateAvailable(val release: LatestRelease) : CheckResult
    data class Failed(val cause: Throwable) : CheckResult
}
```

- `force = true` for manual *Check now*; bypasses debounce.
- On success, writes `autoupdater_last_check_at = now` regardless of
  outcome (a successful "no update" is still a successful check).
- On failure, does not update the timestamp — so the next cold start
  retries.
- Invoked from:
  - `YutoriApplication.onCreate` via a `ProcessLifecycleOwner`
    observer, inside a `CoroutineScope(SupervisorJob + Dispatchers.IO)`
    scoped to the process.
  - Settings ViewModel (`force = true`).
- Result piped to a process-scoped `SharedFlow<CheckResult>` that the
  MainActivity observes; when it sees `UpdateAvailable`, it shows the
  dialog once.

### 4.7 `UpdatePrefs`

Thin wrapper over a dedicated `SharedPreferences` file,
`autoupdater_prefs`:

| Key | Type | Purpose |
|---|---|---|
| `last_check_at` | Long | millis since epoch, debounce |
| `check_on_open_enabled` | Boolean | default `true`; Settings toggle |
| `dismissed_tag` | String? | last tag the user tapped *Later* on |

`dismissed_tag` is only consulted for the on-open auto-surface — not
for *Check now*. This is so *Later* on v0.3.0 doesn't re-nag every 6 h
for the same release on the same installed version. Cleared when
`hasUpdate` returns false (i.e. once the user has actually updated,
the next on-open check will clear the dismissal).

## 5. Auth — none (anonymous public repo)

The repo is public, so the Releases API and the asset download are
both unauthenticated. `UpdateModule` builds one `OkHttpClient` with
no auth interceptor; the Retrofit JSON call and the asset download
share it. Anonymous GitHub reads are rate-limited to 60 req/h per IP
— a non-issue for one user on cold-start with a 6 h debounce.

```kotlin
// :app — UpdateModule.kt
val client = OkHttpClient.Builder()
    .connectTimeout(10, SECONDS)
    .readTimeout(30, SECONDS)
    .build()
```

No branching anywhere on auth or repo visibility, no `Authorization`
header, nothing to rotate or leak.

> **History — the removed PAT surface.** While the repo was private,
> `GET /releases/latest` 404'd anonymous callers, so the app shipped
> an embedded read-only fine-grained PAT. Everything private-specific
> was isolated to one class, `GithubAuthInterceptor` — it attached
> `Authorization: Bearer <token>` from
> `BuildConfig.GITHUB_RELEASES_TOKEN` (a no-op on an empty token) and
> was registered once in `UpdateModule`. The token was injected via
> Gradle (`GITHUB_RELEASES_TOKEN` gradle property / env var →
> `buildConfigField`), supplied in CI from the `RELEASES_TOKEN` repo
> secret. "Send feedback" (#113) had been the *other* embedded-PAT
> consumer (`ISSUES_TOKEN` → Issues API POST); it switched to a
> `mailto:` `Intent.ACTION_SENDTO` early, decoupled from the flip.
> When the repo went public the interceptor, the Gradle/CI wiring,
> the repo secret and the local `gradle.properties` line were all
> deleted and the fine-grained PAT revoked. See `docs/RELEASING.md`
> → *Autoupdater & feedback — no embedded PATs*.

## 6. Token wiring — removed

There is no token. The Gradle `buildConfigField`, the
`~/.gradle/gradle.properties` entry, the `release.yml` `env:` mapping
and the `RELEASES_TOKEN` repo secret described in earlier revisions of
this spec were all removed when the repo went public (see §5 History).
Anonymous calls have no expiry and no credential failure mode — only
the rate-limit / network / server cases in §12 remain.

## 7. UI surfaces — need mockup approval before code

Per `CLAUDE.md`: *"UI changes need a mockup first."* These three
surfaces are gated on HTML mockups in `mockups/`:

### 7.1 Update-available dialog

Modal bottom sheet (or `AlertDialog`, TBD in mockup) shown over the
dashboard when `UpdateCheckCoordinator` emits `UpdateAvailable` on
cold start.

- Title: the release `name` (e.g. "Yutori 0.3.0").
- Subtitle: `"Current: 0.2.0 → New: 0.3.0"`.
- Body: `release.body` rendered as markdown (GitHub-flavored enough
  to handle headings, bullets, links — a lightweight Compose
  markdown library or a minimal hand-rolled renderer, TBD).
- Primary action: *Download & install*. Switches the sheet body to a
  progress bar + cancel button, driven by `DownloadState`.
- Secondary action: *Later*. Persists `dismissed_tag = tagName` and
  closes.
- Failure state: error message + *Retry* + *Dismiss*.

### 7.2 Settings → App updates section

New section in `SettingsScreen`, likely above *About*:

```
App updates
  Current version            0.2.0
  Status                     Up to date            (or: Update available: 0.3.0)
  [✓] Check for updates on app open
  [ Check now ]
```

- Tapping *Status* when an update is available opens the same dialog
  as §7.1.
- Toggle persists to `UpdatePrefs.check_on_open_enabled`.
- *Check now* is always enabled. Disables while a check is in
  flight; re-enables on result.

### 7.3 First-run copy tweak

On the existing restricted-settings guidance screen
(`docs/RELEASING.md:150+` describes the flow), append one line
underneath the existing copy:

> "Once Yutori is installed, future updates arrive as an in-app
> prompt and install without another Play Protect warning."

Pairs with #57 mitigation (c). Not a new screen — single-line edit
to an existing one. Still wants a quick mockup pass for copy.

### 7.4 Dashboard / About / other surfaces

Deliberately none. *Check now* lives in Settings only for v1.
Revisit when About (#66) lands — mirror the *Check now* row there
if it feels natural.

## 8. Manifest

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

- No `INTERNET` needed — already declared.
- No FileProvider. `PackageInstaller.Session` streams bytes through
  `openWrite`; no `content://` URI crosses a process boundary.
- `UpdateInstallResultReceiver` registered in the manifest as
  `exported="false"`.

## 9. Error paths

| Condition | Behaviour |
|---|---|
| Offline at cold-start check | Silent — log only. No surface. |
| Offline on *Check now* | Snackbar "No connection". |
| 401 — token expired / revoked | Settings status shows *Updater offline*. *Check now* shows same. |
| 404 — no releases | Treat as *up to date*. No error surface. |
| 403 — rate limited | Log with remaining-quota header value. Treat UI-wise as offline for that check. |
| Malformed JSON | Fail the check, log to Crashlytics-style sink (we don't have one yet — just `Log.w`). |
| Asset list empty | Treat as up to date. Release without an APK is user error on our end, not a client problem. |
| Download interrupted | Dialog swaps to Retry / Dismiss. Partial file deleted. |
| APK signature mismatch | Android's installer rejects with its own dialog. We don't pre-verify. |
| *Install unknown apps* denied | System shows its own permission prompt; on deny we land back in the app with no install. Dialog stays open; user can retry. |
| User cancels confirmation UI | Session commit returns STATUS_FAILURE_ABORTED. Silent. |

## 10. Edge cases

- **Latest release deleted** — `/releases/latest` returns the
  previous release. `hasUpdate` returns false for users already on a
  version ≥ that one. No downgrade prompt. §4.3 covers the test.
- **Zero releases** — 404 → treat as up to date. No crash.
- **Re-tagging the same version** — *do not do this*. Clients on
  that tag won't see the re-release because `tagName` equality
  makes `hasUpdate` false. Always bump the version when re-cutting.
  Note lives in `docs/RELEASING.md` §11.
- **Release deleted mid-download** — asset URL 404s → `Failed(NotFound)`.
  Dialog shows error + Retry.
- **User installs a newer version manually (e.g. from a CI artifact)**
  — on next cold start, `hasUpdate` returns false and the dialog
  doesn't appear. Correct.
- **User is on a debug-signed APK and release is signed with the
  release keystore** — system rejects install because signatures
  differ. `docs/RELEASING.md:141` already documents this is a
  one-time uninstall-then-reinstall for the migration to
  release-signed. No autoupdater code.
- **User taps *Later* on v0.3.0, uninstalls, reinstalls v0.2.0** —
  `dismissed_tag = v0.3.0` survives in SharedPrefs only if they
  kept app data. On next cold start, `hasUpdate` is true and
  `dismissed_tag == remoteTag`, so dialog is suppressed. Fix:
  `dismissed_tag` is cleared whenever `currentVersion` changes
  (detected on Coordinator startup by stashing
  `last_seen_version_name` in prefs). One-liner.
- **System date set wrong** — 6 h debounce could misbehave. If
  `last_check_at > now` we treat it as "never checked" and run a
  check. Protects against clock going backwards.

## 11. Not in scope

- Background / periodic checks.
- Delta updates.
- Silent / unattended install.
- Rollback to previous version.
- Pre-release channel. If added later, it gets a separate
  *Include pre-releases* toggle and swaps to `/releases` instead of
  `/releases/latest`.
- Mirror-public-repo-for-releases approach (#71(c)). Was a fallback
  for private-repo PAT-rotation pain; moot now that reads are
  anonymous. Not needed.
- A notification when an update is available. Dialog on cold start
  is the only surface.

## 12. Testing

### 12.1 Unit — pure JVM

- `VersionComparatorTest` — equal, newer, older, multi-digit,
  leading-`v`, empty string (→ false), garbage (→ false).
- `UpdateRepositoryTest` with MockWebServer:
  - 200 OK with a full payload → parses `LatestRelease`.
  - 200 OK with no APK asset → `asset = null`.
  - 404 → `Result.success(null)` (public repo: no releases yet =
    up to date, never an error).
  - Malformed JSON → `Result.failure`.
  - Verifies no `Authorization` header is sent (anonymous).
- `UpdateDownloaderTest` with MockWebServer:
  - Happy path: small fake APK, progress emissions, `Done(file)`.
  - Redirect to a second MockWebServer on a different port is
    followed (the presigned-S3 hop) and the bytes still download.
  - 404 on asset → `Failed(NotFound)`.
  - Mid-stream close → `Failed(Network)`.
- `UpdateCheckCoordinatorTest` — debounce logic, `force = true`
  bypass, clock-moved-backwards handling, `dismissed_tag` vs
  `currentVersion` handling.

### 12.2 Unit — Android (Robolectric, under `:app`)

- `UpdatePrefsTest` — round-trip each key, default values.
- Settings ViewModel → Coordinator wiring (manual *Check now* path).

### 12.3 Not tested programmatically

- `UpdateInstaller`. `PackageInstaller.Session` is OS-owned; no
  viable fake. Manual QA covers it.
- Real GitHub API. Don't want CI to depend on an external service.

### 12.4 Manual QA (in `docs/RELEASING.md`)

Pre-release smoke:

1. Install v0.(N).0 on a real phone.
2. Tag v0.(N+1).0 via CI.
3. Cold-start the app (force-stop → open).
4. Update-available sheet appears within a few seconds.
5. Release notes render.
6. *Download & install* → system confirmation → install → app
   relaunches on v0.(N+1).0.
7. Settings → App updates → status reads *Up to date*.
8. *Check now* on v0.(N+1).0 → still *Up to date*.

## 13. Related `docs/RELEASING.md` content

These RELEASING.md additions landed alongside the feature and the
later public-flip cleanup:

1. **Versioning rule:** "Never re-use a version tag. If a release
   needs to be replaced, delete it and bump the patch number. In-app
   autoupdater clients compare by `tag_name` equality and will not
   pick up a re-tagged release."
2. **Autoupdater overview:** the flow and a pointer back to this spec.
3. **Post-public state** (*Autoupdater & feedback — no embedded
   PATs*): records that the interceptor, the Gradle/CI token wiring,
   the repo secret and the local `gradle.properties` line were
   deleted and the fine-grained PAT revoked when the repo went
   public — and that the feedback half (`ISSUES_TOKEN` / Issues API)
   had already gone earlier via the `mailto:` switch, decoupled from
   the flip.

## 14. Rollout order

Six PRs, each independently landable and reviewable:

1. **Token wiring + auth interceptor.** Gradle change + the
   interceptor class + the OkHttp client in a new `UpdateModule`.
   No runtime effect beyond an idle OkHttp client. Zero UI.
2. **`UpdateRepository` + `VersionComparator` + unit tests.** Still
   no UI, no trigger — the repo is instantiable and tested.
3. **`UpdateDownloader` + `UpdateInstaller` + `UpdatePrefs`.** Still
   no trigger. Hidden *Force update check* in a dev-menu (gated on
   `BuildConfig.DEBUG`) for dogfooding.
4. **Mockups for §7.1 + §7.2.** Merged to `mockups/` before any
   Compose code is written.
5. **Settings UI + update-available dialog.** Gates on PR #4.
   `Check now` path becomes real.
6. **`UpdateCheckCoordinator` + `ProcessLifecycleOwner` hook +
   first-run copy tweak (#57 pairing).** Cold-start trigger comes
   online. Release notes doc updates land with this PR.

The split kept each stage reviewable in isolation and confined the
(now-removed) embedded-PAT surface to stage 1.
