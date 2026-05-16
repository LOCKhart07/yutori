# Yutori — In-app Autoupdater Specification (v1)

Tachiyomi-style in-app updater for the side-loaded APK distribution: on
every app cold-start, check GitHub Releases for a newer version, show a
dismissible dialog with release notes, and install via
`PackageInstaller` when the user confirms. Ships while the repo is
still private — a fine-grained PAT embedded at build time unblocks the
Releases API, and the design isolates that workaround to a single
interceptor so removal at #71(a) is a two-minute chore.

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
- Private-repo auth via an embedded read-only PAT, isolated behind
  one interceptor.
- Settings toggle to disable the on-open check (manual *Check now*
  always works).

**Out of scope**
- Periodic background polling (WorkManager). Cold-start is enough.
- Delta / patch updates.
- Silent install (requires device-owner privilege we don't have).
- Rollback.
- Pre-release / beta channel selector.
- Notification channel. No notification surface — dialog only.
- Public-repo variant. The interceptor is already a no-op on empty
  tokens; #71(a) is removal-only, not a second code path.

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
    GithubAuthInterceptor.kt     # #71(a) removal target
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
- Auth header is added by the interceptor, not by the repo.
- 404 handling is token-aware while the repo is still private:
  - With token: `Result.success(null)` — repo is reachable and has
    no releases yet. Treat as *up to date*.
  - Without token: `Result.failure(...)` — GitHub returns 404 to
    anonymous callers on private repos to hide their existence, so
    "no token + 404" is the shape of a missing PAT, not a real
    absence of releases. Surface as *Updater offline*. This branch
    is part of the #71(a) removal surface — after the repo goes
    public, every 404 is legit and the token-aware parameter
    disappears.
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
This URL returns 302 to a presigned S3 URL when called with
`Accept: application/octet-stream`, and it *does* work for private
repos when the right Authorization header is present on the initial
request. `browser_download_url` does not — it's the public CDN path
and returns 404 for private repos regardless of token.

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

## 5. Auth isolation — the #71(a) removal surface

Everything private-repo-specific lives in exactly one class:

```kotlin
// Remove when #71(a) lands — see docs/RELEASING.md "Going public".
class GithubAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = BuildConfig.GITHUB_RELEASES_TOKEN
        val request = if (token.isEmpty()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
```

- No branching in repo/downloader/installer code based on
  public-vs-private.
- Empty token ⇒ unauthenticated request ⇒ works for public repos at
  the anonymous rate limit (60 req/h per IP — fine for one user on
  cold-start with a 6 h debounce).
- Wired in one place:

```kotlin
// :app — UpdateModule.kt
val client = OkHttpClient.Builder()
    .addInterceptor(GithubAuthInterceptor())   // Remove at #71(a)
    .connectTimeout(10, SECONDS)
    .readTimeout(30, SECONDS)
    .build()
```

Both the JSON API (Retrofit-on-OkHttp) and the asset download use the
same `OkHttpClient`, so one interceptor registration covers both.

> **Feedback flow note (#71(a) decoupling).** "Send feedback" (#113)
> used to be the *other* embedded-PAT consumer (`ISSUES_TOKEN` →
> `IssueReporter` → Issues API POST). It was decoupled from the public
> flip and its removal shipped early: it now opens the mail client via
> `Intent.ACTION_SENDTO` (`mailto:`), which needs no token regardless
> of repo visibility. So this interceptor and its `RELEASES_TOKEN` are
> the **sole remaining #71(a) surface**, and that removal is the only
> part still gated on the repo going public.

## 6. Token wiring

### 6.1 Gradle

`:app/build.gradle.kts`, under `defaultConfig`:

```kotlin
// Remove when #71(a) lands — see docs/RELEASING.md "Going public".
val releasesToken = providers.gradleProperty("GITHUB_RELEASES_TOKEN")
    .orElse(providers.environmentVariable("GITHUB_RELEASES_TOKEN"))
    .orNull.orEmpty()

buildConfigField("String", "GITHUB_RELEASES_TOKEN", "\"$releasesToken\"")
```

- `buildFeatures { buildConfig = true }` must be enabled (already is
  for `VERSION_NAME`).
- Empty token is a valid build: BuildConfig field becomes `""`, the
  interceptor no-ops, and the feature silently runs at anonymous
  rate limits. No build-time warning — dev builds without the token
  should just work.

### 6.2 Developer machine

`~/.gradle/gradle.properties` (outside the repo):

```
GITHUB_RELEASES_TOKEN=github_pat_...
```

`chmod 600 ~/.gradle/gradle.properties`. Same file already used for
signing secrets (see `docs/RELEASING.md:112`), so no new file hygiene
to establish.

### 6.3 CI

`.github/workflows/release.yml`:

```yaml
env:
  GITHUB_RELEASES_TOKEN: ${{ secrets.GITHUB_RELEASES_TOKEN }}
```

New repo secret `GITHUB_RELEASES_TOKEN`. Mirror the naming and the
location of the signing secrets block.

### 6.4 PAT properties

- Type: fine-grained personal access token.
- Resource owner: `LOCKhart07`.
- Repository access: `LOCKhart07/yutori` only.
- Permission: `Contents: Read`. Nothing else.
- Expiry: 90 days.
- Calendar reminder to rotate 7 days before expiry. When the repo
  goes public (#71(a)), revoke early instead of rotating.

### 6.5 What happens when the token expires

- API call returns 401.
- `UpdateCheckCoordinator` emits `Failed`.
- Settings status row shows *Updater offline — check logs*. Manual
  *Check now* shows the same. Everything else in the app is
  unaffected.
- Fix: rotate the PAT, replace the gradle.properties value, rebuild.

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
- Mirror-public-repo-for-releases approach (#71(c)). Revisit only if
  PAT rotation becomes chronically painful before #71(a) lands.
- A notification when an update is available. Dialog on cold start
  is the only surface.

## 12. Testing

### 12.1 Unit — pure JVM

- `VersionComparatorTest` — equal, newer, older, multi-digit,
  leading-`v`, empty string (→ false), garbage (→ false).
- `UpdateRepositoryTest` with MockWebServer:
  - 200 OK with a full payload → parses `LatestRelease`.
  - 200 OK with no APK asset → `asset = null`.
  - 404 → `Result.success(null)`.
  - 401 → `Result.failure`.
  - Malformed JSON → `Result.failure`.
  - Verifies the `Authorization: Bearer test-token` header is
    present when the interceptor is installed, absent when not.
- `UpdateDownloaderTest` with MockWebServer:
  - Happy path: small fake APK, progress emissions, `Done(file)`.
  - Redirect to a second MockWebServer on a different port to
    verify `Authorization` header is **not** forwarded.
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
- Real GitHub API. Don't want CI to depend on an external service
  or to smuggle the PAT into test runners.

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

## 13. Changes to `docs/RELEASING.md`

Three additions:

1. **Versioning rule (§11 new):** "Never re-use a version tag. If a
   release needs to be replaced, delete it and bump the patch
   number. In-app autoupdater clients compare by `tag_name` equality
   and will not pick up a re-tagged release."
2. **Autoupdater section (§12 new):** brief overview of the flow,
   pointer to `plans/autoupdater-spec.md`, and the PAT-rotation
   reminder.
3. **"Going public" subsection (§13 new):** the removal checklist
   for #71(a):
   1. Delete `GithubAuthInterceptor.kt`.
   2. Remove the `.addInterceptor(GithubAuthInterceptor())` line
      from `UpdateModule.kt`.
   3. Remove the `buildConfigField("GITHUB_RELEASES_TOKEN", …)` and
      the `providers.gradleProperty(...)` block from
      `:app/build.gradle.kts`.
   4. Remove `GITHUB_RELEASES_TOKEN` from the `env:` block of
      `release.yml` and delete the repo secret.
   5. Remove the `GITHUB_RELEASES_TOKEN` line from local
      `gradle.properties` files.
   6. Revoke the PAT at
      github.com/settings/tokens and delete the calendar reminder.
   7. `git grep '#71(a)'` returns no matches.

   Note: the feedback-flow half of the old #71(a) surface
   (`ISSUES_TOKEN` / `IssueReporter` / Issues API) already shipped its
   removal independently via the `mailto:` switch — it was never tied
   to the flip. Only the autoupdater steps 1–6 above remain, so the
   step-7 `git grep` only goes fully clean once those land post-flip.

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

Split keeps each PR reviewable in isolation and keeps the #71(a)
removal surface confined to PR #1.
