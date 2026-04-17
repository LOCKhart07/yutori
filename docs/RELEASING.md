# Releasing Yutori

Yutori ships as a side-loaded APK. There is no Play Store listing. A
GitHub Actions workflow (`.github/workflows/release.yml`) builds a
release APK and attaches it to a GitHub Release whenever a `v*` tag is
pushed.

## Cutting a release

1. Bump `versionCode` and `versionName` in
   `android/app/build.gradle.kts` and commit.
2. Tag and push:

   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   # or: git push --tags
   ```

3. GitHub Actions runs `:app:assembleRelease`, creates a release named
   after the tag, and uploads `yutori-<tag>.apk` as an asset.

The app's in-app autoupdater (Tachiyomi-style) polls the GitHub
Releases API for new tags and offers to download the APK.

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

## Autoupdater PAT (private repo only)

While `LOCKhart07/yutori` is private, the in-app autoupdater can't call
the GitHub Releases API anonymously — GitHub 404s private-repo reads,
and the app surfaces that as "Updater offline." The workflow bakes a
fine-grained PAT into the APK so installed builds can read releases.

Repo secret:

| Secret | Contents |
| --- | --- |
| `RELEASES_TOKEN` | fine-grained PAT, scoped to `LOCKhart07/yutori`, *Contents: Read* |

The secret is named `RELEASES_TOKEN` — GH rejects any secret name
starting with `GITHUB_`. The Gradle build still reads env var
`GITHUB_RELEASES_TOKEN` into `BuildConfig.GITHUB_RELEASES_TOKEN`; the
workflow maps `secrets.RELEASES_TOKEN` onto that env var.

Token setup:
1. github.com → Settings → Developer settings → Personal access tokens
   → Fine-grained tokens → Generate new token.
2. Resource owner: `LOCKhart07`. Repository access: only
   `LOCKhart07/yutori`. Repository permissions: **Contents — Read**.
   Expiry: 1 year.
3. Copy the token, add it as repo secret `RELEASES_TOKEN`.

When the token expires, "Updater offline" will return for any APK
built against the stale PAT. Rotate by creating a new PAT and
overwriting the secret; the next release embeds the fresh one. APKs
already on users' phones keep the old token until they're replaced.

Remove this whole section (and the corresponding `buildConfigField`
in `android/app/build.gradle.kts`) when the repo goes public — see
autoupdater-spec §11.2 and issue #71(a).

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
