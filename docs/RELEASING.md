# Releasing Yutori

Yutori ships as a side-loaded APK. There is no Play Store listing. A
GitHub Actions workflow (`.github/workflows/release.yml`) builds a
release APK and attaches it to a GitHub Release whenever a `v*` tag is
pushed.

## Cutting a release

Version numbers come from the tag — `android/app/build.gradle.kts`
derives `versionCode` from the commit count and `versionName` from
`GITHUB_REF_NAME` when CI sees a `v*.*.*` tag. Nothing to hand-edit
in `build.gradle.kts`.

1. Pick the next version — see *Picking the version number* below.
2. Push main, then tag and push the tag:

   ```bash
   git push origin main
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```

3. GitHub Actions runs `:app:assembleRelease`, creates a release named
   after the tag, and uploads `yutori-<tag>.apk` as an asset.

The app's in-app autoupdater (Tachiyomi-style) polls the GitHub
Releases API for new tags and offers to download the APK.

## Picking the version number

**Always anchor on the last release tag**, not on recency. Run:

```bash
git log $(git describe --tags --abbrev=0)..HEAD --oneline
```

Eyeballing `git log -5` or the "Recent commits" block in your tooling
is a trap — commits that look recent are often already shipped.

Map conventional-commit prefixes in that diff to a semver bump:

| Commits since last tag contain … | Bump |
| --- | --- |
| Only `fix:` / `ci:` / `test:` / `docs:` / `chore:` / `style:` / `perf:` | **patch** (0.5.0 → 0.5.1) |
| Any `feat:`, or `refactor:` that changes `applicationId` / schema / a user-visible surface | **minor** (0.5.x → 0.6.0) |
| A breaking change to behavior users depend on (post-1.0 only) | **major** |

Notes:

- `docs:` is repo-internal only (README, CLAUDE.md, `plans/`, `docs/`)
  — it never drives a version bump on its own. User-facing copy
  changes ship as `feat:` or `fix:`.
- `refactor:` is usually patch-worthy, but a package rename
  (`com.spendwise` → `com.yutori`) or a DB migration counts as minor
  because it changes something the user experiences (reinstall
  prompt, migration screen).
- Pre-1.0, breaking changes don't force a major bump. Bump minor.

## Signing modes

The workflow supports two modes:

### Release-signed (recommended once you have a keystore)

Requires these four repo secrets (Settings → Secrets and variables →
Actions → New repository secret):

| Secret | Contents |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64`   | base64-encoded JKS keystore |
| `SIGNING_KEYSTORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS`         | alias of the key inside the keystore |
| `SIGNING_KEY_PASSWORD`      | password of that key |

When all four are present, the workflow decodes the keystore into the
runner's temp dir and passes the other three through as environment
variables. `android/app/build.gradle.kts` picks them up and uses them
for the `release` signingConfig.

### Debug-signed (default until a keystore exists)

If any of the four secrets are missing, the workflow still produces a
release APK but falls back to Android's auto-generated debug
signingConfig. The APK is installable via side-load, but:

- Android tags it as a debug build.
- Every developer machine produces a different debug key, so updates
  built on a new machine will fail to install over an older
  debug-signed APK (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- **Do not distribute debug-signed builds to anyone but yourself.**

The workflow emits a GitHub Actions warning on every debug-signed
release so you can't miss it.

## Autoupdater & feedback — no embedded PATs

Neither shipping path embeds a GitHub token:

| Feature | How it talks to GitHub |
| --- | --- |
| Autoupdater | `GET /repos/LOCKhart07/yutori/releases/latest`, **anonymous** — the repo is public, so unauthenticated reads succeed. |
| Send feedback | No API call. Opens the user's mail client via `Intent.ACTION_SENDTO` (`mailto:`). |

This was not always so. While the repo was private the autoupdater
needed a fine-grained `RELEASES_TOKEN` (GitHub 404s anonymous reads of
a private repo). When the repo went public that PAT, its
`GITHUB_RELEASES_TOKEN` BuildConfig field, the workflow `env:` mapping
and the repo secret were all removed, and the fine-grained token
revoked. See `plans/autoupdater-spec.md` §5. There is now no embedded
credential to rotate or leak.

### Updater status codes

The Settings → App updates section surfaces errors as
`Couldn't check (<tag>)`. The tag is either the HTTP status from the
GitHub API or the literal word `offline`. Since the call is anonymous
there is no auth failure mode (`401`); a `404` is **not** an error —
for a public repo it just means "no release published yet", so the
updater treats it as up-to-date and never surfaces it. What remains:

| Tag | What it means | What to do |
| --- | --- | --- |
| `403` | Anonymous GitHub rate limit hit (60 req/hr per IP). | Back off; it clears on its own. Not actionable on the client. |
| `5xx` | GitHub server-side. | Try again later. Not actionable on the client. |
| `offline` | Network, DNS, or a malformed response from GitHub. | Retry when connectivity is back. |

Send feedback never hits the GitHub API, so these codes don't apply to
it — it hands off to the mail client (`mailto:`) and the only failure
mode is "no email app installed", surfaced inline on the sheet.

## CI tokens

Separate from the embedded PATs above — these never ship inside the
APK, they only unlock automation on the Actions runner. Same yearly
rotation discipline applies, so rotate them on the same calendar.

| Secret | Permission | Used by |
| --- | --- | --- |
| `COPILOT_GITHUB_TOKEN` | Fine-grained PAT on `LOCKhart07`'s account with **Copilot Requests** enabled (Repository access: *Public repositories* — this is UI gating, not a functional limit; see `docs/issue-triage.md` → *Permissions & secrets*) | `.github/workflows/triage-issue.yml` — authenticates Copilot CLI when auto-triaging newly opened issues. |

`COPILOT_GITHUB_TOKEN` is the only PAT the project still uses — the
embedded `RELEASES_TOKEN` / `ISSUES_TOKEN` were removed when the repo
went public (autoupdater is now anonymous, feedback is `mailto:`).
Copilot CLI still needs to authenticate to its backend on the user's
behalf, so this one stays. Rotate it annually.

When it expires, auto-triage fails silently: the workflow posts a
"Automated triage failed" comment on every new issue until the
secret is rotated. That fallback comment is defined in
`.github/workflows/triage-issue.yml`. To rotate, generate a fresh
PAT (same permissions as the table above) and re-run:

```bash
printf '%s' "$TOKEN" | gh secret set COPILOT_GITHUB_TOKEN --repo LOCKhart07/yutori
```

Same *"do not use `--body -`"* caveat as every other token here —
`gh` reads stdin automatically when it isn't a TTY; the `-` flag
stores a literal hyphen and the piped token is silently dropped.

## Generating a keystore

One-time setup on your local machine (not in the repo — the keystore
itself must never be committed; `*.jks` and `*.keystore` are in
`.gitignore`):

```bash
keytool -genkey -v \
    -keystore yutori-release.jks \
    -alias yutori \
    -keyalg RSA -keysize 4096 \
    -validity 10000
```

Pick a strong keystore password and key password. Store them in a
password manager — **if you lose these, you cannot ship updates** to
anyone who installed a previous release-signed APK (Android will
refuse to replace the app because the signatures won't match).

## Adding the keystore to GitHub Actions

```bash
# 1. base64-encode the keystore (macOS/Linux):
base64 -w0 yutori-release.jks > yutori-release.jks.b64
# (on macOS, `base64 -i yutori-release.jks` produces the same thing
#  without the -w0 flag.)

# 2. Copy the contents of yutori-release.jks.b64 into the
#    SIGNING_KEYSTORE_BASE64 secret in the GitHub repo settings.

# 3. Add SIGNING_KEYSTORE_PASSWORD, SIGNING_KEY_ALIAS, and
#    SIGNING_KEY_PASSWORD as separate secrets.

# 4. Delete the .b64 file once it's in GitHub:
rm yutori-release.jks.b64
```

The keystore itself (`yutori-release.jks`) should live in your
personal backup — **not** in the repo.

## Local builds (Android Studio + CLI)

You don't normally need to build a release APK locally. But if you have
the release keystore on your machine, Gradle will pick it up for **both**
debug and release builds — meaning AS-built debug APKs and CI-built
release APKs are signed with the same certificate, and Android will let
one replace the other on your phone without an uninstall prompt.

The recommended setup is `~/.gradle/gradle.properties` (outside the
repo, in your user home — Gradle reads this regardless of how it was
invoked, so Android Studio picks it up automatically):

```properties
# ~/.gradle/gradle.properties
SIGNING_KEYSTORE_PATH=/absolute/path/to/yutori-release.jks
SIGNING_KEYSTORE_PASSWORD=...
SIGNING_KEY_ALIAS=yutori
SIGNING_KEY_PASSWORD=...
```

Lock it down: `chmod 600 ~/.gradle/gradle.properties`.

Env vars also work (take precedence over gradle.properties):

```bash
cd android
export SIGNING_KEYSTORE_PATH=/path/to/yutori-release.jks
export SIGNING_KEYSTORE_PASSWORD=...
export SIGNING_KEY_ALIAS=yutori
export SIGNING_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

Without any of the above set, the build falls back to Android Studio's
auto-generated debug keystore (per-machine, different on every
developer's box). Debug builds still work; release builds fall through
to the debug key too and the CI workflow emits a warning.

### One-time migration from a debug-signed install

If your phone currently has v0.1.0 installed (debug-signed under AS's
auto-generated key), you'll need to **uninstall it once** before
installing the first release-signed build — Android refuses to replace
an APK with one signed by a different cert. After that one uninstall,
every subsequent debug or release build signs with the same release
keystore, and the reinstall prompts go away.

## First-install friction on Android 13+ (restricted settings)

Sideloaded APKs on Android 13+ can't request SMS permissions until the
user flips **Allow restricted settings** on the app's App info page.
On first launch, tapping *Grant permissions* produces no visible
dialog — the OS silently denies the request. The app detects this
dead-end (denied permission + `shouldShowRequestPermissionRationale`
false) and surfaces a guidance screen with a deep-link to App info
and a *Try again* retry path. Same screen also covers the
*Don't-ask-again* case for users who denied the runtime prompt twice.

No action required on the release side — just be aware the first
install will hit this, and the first-run path is:

1. Install APK.
2. Tap *Grant permissions* — dialog never appears.
3. Yutori shows the restricted-settings guidance.
4. Tap *Open app info* → system App info opens.
5. Tap three-dot menu → *Allow restricted settings* → confirm.
6. Back to Yutori → tap *Try again* → the real permission dialog
   now appears → Allow.
