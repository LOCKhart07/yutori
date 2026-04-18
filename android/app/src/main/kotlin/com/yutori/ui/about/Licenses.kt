package com.yutori.ui.about

/**
 * Hand-curated list of runtime open-source dependencies for the
 * Settings → About → Open-source licenses surface. See
 * `plans/about-screen-spec.md` §3.2.
 *
 * Any new `implementation(...)` or `api(...)` dependency added to any
 * module (`:app`, `:database`, `:ingestion`, etc.) should add a line
 * here. Test-only deps don't ship and don't belong.
 *
 * Entries are grouped by logical family rather than per-artifact —
 * `AndroidX Core / Activity / Lifecycle` covers the five
 * closely-related androidx artifacts as one line so the list stays
 * scannable.
 *
 * Every entry ships under Apache 2.0 as of v0.7.0. The Open-source
 * licenses sub-screen links out to the canonical Apache 2.0 text
 * online rather than bundling it.
 */
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
