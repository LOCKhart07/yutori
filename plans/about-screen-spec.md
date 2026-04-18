# About screen + Open-source licenses

Spec for issue #66 (widened) — a Settings → About surface that covers
operational metadata (version, commit, check for updates, licenses,
repo link) *and* the story of the app (what "Yutori" means, logo
rationale, 5 principles). Plus a README `## Why "Yutori"` + `## Principles`
section carrying the same philosophy text so the in-app story and the
public-repo landing tell the same story.

Context on why this is a ship-blocker for going public: #66's body.
Companion mockup: `mockups/v12-about.html`.

## 1. Scope

**In scope**

- New screen `Settings → About Yutori` (below the existing Feedback
  section, in its own "About" section).
- New sub-screen `About → Open-source licenses`.
- New navigation routes `Screen.About` and `Screen.OpenSourceLicenses`
  in the existing `MainActivity` sealed hierarchy.
- New sections in `README.md`: `## Why "Yutori"` and `## Principles`.
- Hand-curated list of runtime dependencies + one canonical Apache 2.0
  license text (every runtime dep in Yutori is Apache 2.0 as of v0.6.2).

**Out of scope**

- A Gradle license-report plugin (`com.jaredsburrows.license` etc.) to
  auto-generate the list. Deferred: hand-curated is fine for v0.7.0
  and re-generation cost is low. Follow-up if the dep graph grows.
- An in-app changelog / "What's new" renderer — that's #77.
- Any user-editable preference on this screen. About is read-only.

## 2. Screens

### 2.1 About (`Screen.About`)

Entry point: `Settings → About Yutori`.

Scrollable single Column with these sections in order:

1. **Back row** — "Settings" with the AutoMirrored back chevron. Same
   `BackRow` composable the rest of the app uses (defined in
   `CategoryDrillDownScreen.kt:217`).
2. **Hero** — logo (88dp circular, `ic_launcher_round` res), wordmark
   "Yutori" (Inter semibold 28sp), tagline on two lines.
3. **"Why \"Yutori\""** — section head + single elevated card with the
   name + logo meaning paragraph (~40 words).
4. **"Principles"** — section head + five numbered slab cards. Each
   card: bold heading, one terse supporting line. Order matters and is
   load-bearing:
   1. SMS-first, human-assist second
   2. Margin, not micromanagement
   3. On-device by default
   4. Opinionated about scope
   5. Side-loaded, open source
5. **"Build"** — elevated card with three rows:
   - Version (reads `BuildConfig.VERSION_NAME`)
   - Commit (reads `BuildConfig.COMMIT_SHA`)
   - Check for updates (actionable row with trailing chevron — fires
     `UpdateViewModel.onCheckNow()`; result surfaces via the
     AppContent-level UpdateDialog overlay, same mechanism Settings
     already uses, so About doesn't duplicate update UI)
6. **"More"** — two elevated link rows:
   - Open-source licenses → navigates to `Screen.OpenSourceLicenses`
   - View on GitHub → fires `Intent.ACTION_VIEW` on
     `https://github.com/LOCKhart07/yutori` with `FLAG_ACTIVITY_NEW_TASK`

No bottom navigation. No ViewModel — the screen is stateless (all
inputs are passed by the caller via `BuildConfig` + the shared
`UpdateViewModel` state).

### 2.2 Open-source licenses (`Screen.OpenSourceLicenses`)

Entry point: About → "Open-source licenses" link row.

Scrollable single Column:

1. **Back row** — "About" with the AutoMirrored back chevron.
2. **Title** — "Open-source licenses" (display/headlineLarge).
3. **Intro paragraph** — one line explaining this is the list of
   libraries Yutori depends on and that they all ship under Apache 2.0.
4. **Library list** — one row per runtime dependency, elevated slab
   style. Each row: library name (left) + license tag "Apache 2.0"
   (right, muted, monospace). No tap action on rows.
5. **Apache 2.0 link card** — clickable row at the bottom that fires
   `Intent.ACTION_VIEW` on `https://www.apache.org/licenses/LICENSE-2.0`.
   Not bundled — the canonical text lives upstream, the dep list already
   tells you which license, and bundling ~11 KB of boilerplate for a
   text nobody reads in-app isn't worth the APK weight. Trailing
   external-link icon so the one-tap-to-leave-the-app is explicit.

The single-screen, dep-list-plus-link model is intentional. Simpler
than per-library sub-screens, simpler than bundling multiple license
texts, and every listed library really does ship under Apache 2.0.

## 3. Data contract

### 3.1 BuildConfig fields

Already defined in `android/app/build.gradle.kts`; no new fields:

| Field                          | Source                                   |
| ------------------------------ | ---------------------------------------- |
| `BuildConfig.VERSION_NAME`     | Derived from `GITHUB_REF_NAME` on a tag push, else `"0.0.0-dev+<commit-count>"` |
| `BuildConfig.COMMIT_SHA`       | `git rev-parse --short HEAD` at build time |

### 3.2 Licenses list

Lives in a new internal file:
`android/app/src/main/kotlin/com/yutori/ui/about/Licenses.kt`.

Shape:

```kotlin
internal data class LicenseEntry(
    val name: String,
    val license: String = "Apache 2.0",
)

internal val openSourceLibraries: List<LicenseEntry> = listOf(
    LicenseEntry("Jetpack Compose"),
    LicenseEntry("Material 3"),
    LicenseEntry("AndroidX Core / Activity / Lifecycle"),
    LicenseEntry("Room"),
    LicenseEntry("WorkManager"),
    LicenseEntry("Profile Installer"),
    LicenseEntry("Kotlin Coroutines"),
    LicenseEntry("OkHttp"),
    LicenseEntry("Retrofit"),
    LicenseEntry("Moshi"),
)
```

Grouped by logical "family" rather than 1-per-artifact. `"AndroidX Core
/ Activity / Lifecycle"` covers the ~5 closely-related androidx
artifacts we depend on, as one entry, so the list doesn't balloon
past ~10 rows.

Maintenance contract: any new runtime dependency (not
`testImplementation` / `androidTestImplementation`) added to any
module requires an update to this file. CI doesn't enforce this (out
of scope); it's a commit-review convention.

### 3.3 Apache 2.0 license text

Not bundled. The Open-source licenses sub-screen links out to
`https://www.apache.org/licenses/LICENSE-2.0` via `Intent.ACTION_VIEW`
instead of embedding ~11 KB of legal boilerplate in the APK. Dep names
+ license tag in-app, canonical text upstream.

## 4. Copy

Canonical copy — changes to anything below should update both the
in-app screen and `README.md`, and should be discussed in the PR that
changes it because the copy is the product's voice.

**Tagline:**

> You don't want to spend less. You want to spend confidently.

**Name + logo paragraph:**

> **Yutori** (余裕) is Japanese for "breathing room": financial
> margin, mental ease, room to spend without friction. The logo is
> the hiragana **ゆ** ("yu") rendered as negative space; the space
> itself is the design.

**Principle one-liners (in-app):**

1. **SMS-first, human-assist second.** Bank SMS is the source of
   truth. Manual entry stays light.
2. **Margin, not micromanagement.** Answers "how much room do I have?"
   without moralising spend.
3. **On-device by default.** You own your data.
4. **Opinionated about scope.** Personal monthly spend from SMS.
   Nothing more.
5. **Side-loaded, open source.** GitHub releases, in-app updates. Code
   is public, audit it.

**Principle paragraphs (README — slightly longer):** see
`mockups/v12-about.html` for the current canonical versions.

## 5. Layout notes

- Entire About screen scrolls as one `Column(verticalScroll)`. No
  `LazyColumn` — content is fixed-cardinality (5 principles, 3 build
  rows, 2 link rows). LazyColumn would over-engineer this.
- Hero uses `Icon(painter = painterResource(R.mipmap.ic_launcher_round))`
  if that's a valid painter source; otherwise falls back to rendering
  the PNG via a Drawable. Decide at implementation time — both compile.
- Section heads use the existing `YutoriTextStyles.Caps` and
  `YutoriTheme.colors.onFaint` so About matches the visual language of
  Settings.
- Principle-card number circles are Inter 11sp mono, 22dp diameter,
  surface-elevated-2 background. Matches the "numbered marker" style
  used in `MigrationErrorScreen`'s Step composable conceptually, but
  we don't share the composable because the contexts differ.

## 6. Module placement

New files, all inside `:app`:

```
android/app/src/main/kotlin/com/yutori/ui/about/
    AboutScreen.kt
    OpenSourceLicensesScreen.kt
    Licenses.kt
LICENSE                              (new at repo root — GPL-3.0)
```

Modified files:

```
android/app/src/main/kotlin/com/yutori/MainActivity.kt
    + Screen.About
    + Screen.OpenSourceLicenses
    + when-arms in the navigation dispatcher
android/app/src/main/kotlin/com/yutori/ui/SettingsScreen.kt
    + "About" section with one SettingsItem
    + onAbout callback parameter
README.md
    + ## Why "Yutori"
    + ## Principles
plans/backlog.md
    - remove the #66 entry (per
      memory/feedback_backlog_sync_with_feature)
```

No test code beyond what the normal Compose unit-test harness
would need; About is stateless and its inputs are trivially typed.

## 7. Rollout

Single commit, single release. `fix:` is wrong (this isn't bug work);
`feat:` is right — a new user-visible screen. Per `docs/RELEASING.md`
that drives a minor version bump: **v0.6.2 → v0.7.0**.

The About screen does *not* depend on the #71(a) public-flip
workstream (issue #126) — both can ship independently. The README
philosophy copy is worded such that "Read the code" (in principle 5)
is accurate once the repo is public; it becomes accurate when the
visibility flip happens, so the timing should be: land this PR →
walk the `docs/SECURITY.md` pre-public-flip checklist → flip the
repo visibility → land #126 (remove embedded PATs) as the next
release.
