import java.time.Instant

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Run a git command from the repo root and return stdout trimmed.
// Returns `fallback` on any failure (non-git clone, shallow CI checkout
// without fetch-depth: 0, etc.) so builds never break on versioning.
fun git(fallback: String, vararg args: String): String = runCatching {
    val process = ProcessBuilder("git", *args)
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val out = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() == 0 && out.isNotEmpty()) out else fallback
}.getOrDefault(fallback)

// Version derivation.
//
// - versionCode = total commit count from HEAD. Monotonic per commit,
//   so installing a debug build over a release (or vice versa) never
//   trips Android's downgrade guard as long as the clone has full
//   history. CI must use `fetch-depth: 0` on checkout or the count
//   collapses to 1.
// - versionName, CI with a tag push: strip `v` from GITHUB_REF_NAME
//   (e.g. "v0.2.0" → "0.2.0").
// - versionName, everywhere else (local AS builds, CI on non-tag
//   pushes): "0.0.0-dev+<commitCount>" — clearly not a release.
val commitCount: Int = git("0", "rev-list", "--count", "HEAD").toIntOrNull() ?: 0
val commitSha: String = git("unknown", "rev-parse", "--short", "HEAD")

val refName: String? = System.getenv("GITHUB_REF_NAME")
val refType: String? = System.getenv("GITHUB_REF_TYPE")  // "tag" | "branch"
val isReleaseTag = refType == "tag" &&
    refName?.matches(Regex("v\\d+\\.\\d+\\.\\d+(?:-.+)?")) == true
val derivedVersionName = if (isReleaseTag) {
    refName!!.removePrefix("v")
} else {
    "0.0.0-dev+$commitCount"
}

// Dogfood-only overrides for testing the in-app autoupdater flow. Real
// CI builds should never set these — versionCode is meant to grow with
// commitCount so downgrade protection works. Locally we sometimes need
// to pretend the build is older than the latest GitHub release, so the
// updater finds an "upgrade" to offer. Pass as env vars or Gradle
// properties, e.g.:
//   YUTORI_VERSION_CODE_OVERRIDE=50 ./gradlew :app:assembleDebug
val versionCodeOverride: Int? =
    (providers.gradleProperty("YUTORI_VERSION_CODE_OVERRIDE")
        .orElse(providers.environmentVariable("YUTORI_VERSION_CODE_OVERRIDE"))
        .orNull)?.toIntOrNull()
val versionNameOverride: String? =
    providers.gradleProperty("YUTORI_VERSION_NAME_OVERRIDE")
        .orElse(providers.environmentVariable("YUTORI_VERSION_NAME_OVERRIDE"))
        .orNull

android {
    namespace = "com.yutori"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yutori"
        minSdk = 28          // decision 2026-04-15: API 28 floor
        targetSdk = 34
        versionCode = versionCodeOverride ?: commitCount
        versionName = versionNameOverride ?: derivedVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "COMMIT_SHA", "\"$commitSha\"")
        buildConfigField("String", "COMMIT_COUNT", "\"$commitCount\"")
        buildConfigField("String", "BUILD_TIME", "\"${Instant.now()}\"")
        buildConfigField("boolean", "IS_RELEASE_TAG", isReleaseTag.toString())

        // Fine-grained PAT for the in-app autoupdater's Releases API calls
        // while `LOCKhart07/yutori` is still a private repo. Empty by
        // default — unauthenticated builds still compile and the
        // interceptor no-ops. See plans/autoupdater-spec.md §6 and
        // docs/RELEASING.md. Remove at #71(a) when the repo goes public.
        val releasesToken: String = providers.gradleProperty("GITHUB_RELEASES_TOKEN")
            .orElse(providers.environmentVariable("GITHUB_RELEASES_TOKEN"))
            .orNull.orEmpty()
        buildConfigField("String", "GITHUB_RELEASES_TOKEN", "\"$releasesToken\"")

        // Fine-grained PAT for the in-app "Send feedback" flow — POSTs
        // user-typed bug reports to the Issues API on this repo. Scoped
        // Issues:Write. Separate from releasesToken so a leak of one
        // doesn't implicate the other. Empty default keeps local dev
        // builds compiling; the UI gracefully disables Send in that case.
        // See #113, docs/RELEASING.md.
        val issuesToken: String = providers.gradleProperty("GITHUB_ISSUES_TOKEN")
            .orElse(providers.environmentVariable("GITHUB_ISSUES_TOKEN"))
            .orNull.orEmpty()
        buildConfigField("String", "GITHUB_ISSUES_TOKEN", "\"$issuesToken\"")
    }

    // Release signing config.
    //
    // Reads SIGNING_* creds from env vars first (CI), then from Gradle
    // properties (~/.gradle/gradle.properties on a dev machine — lets
    // Android Studio pick them up without launching AS from a shell).
    // When all four are present and the keystore file exists, both debug
    // and release builds use the release signingConfig — same cert on
    // every APK means AS-built debug installs and CI-built release
    // installs can replace each other on your phone without uninstalling.
    // When anything is missing, release falls back to the auto-generated
    // debug keystore and the workflow emits a warning.
    fun signingProp(key: String): String? =
        System.getenv(key) ?: project.findProperty(key) as String?

    val keystorePath: String? = signingProp("SIGNING_KEYSTORE_PATH")
    val keystorePassword: String? = signingProp("SIGNING_KEYSTORE_PASSWORD")
    val keyAlias: String? = signingProp("SIGNING_KEY_ALIAS")
    val keyPassword: String? = signingProp("SIGNING_KEY_PASSWORD")
    val hasReleaseSigning = !keystorePath.isNullOrBlank() &&
        !keystorePassword.isNullOrBlank() &&
        !keyAlias.isNullOrBlank() &&
        !keyPassword.isNullOrBlank() &&
        file(keystorePath!!).exists()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {
            // When release-signing creds are present locally, sign debug
            // with them too so AS-built debug APKs and CI-built release
            // APKs can replace each other without an uninstall prompt.
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                // Fallback: debug key. Produces a side-loadable APK but
                // Android will tag it as a debug build. Not for public
                // distribution. See docs/RELEASING.md.
                signingConfigs.getByName("debug")
            }
        }
        // Macrobenchmark target. Shape matches release (non-debuggable,
        // same minify + signing config) so measurements reflect real
        // performance and the APK replaces the existing install cleanly
        // — inheriting release's signing means we reuse the release key
        // when creds are set and fall back to the debug key otherwise.
        // `matchingFallbacks` tells library modules with no `benchmark`
        // variant to use their `release` one.
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // Kotlin 1.9.23 ↔ Compose compiler 1.5.11 per
        // https://developer.android.com/jetpack/androidx/releases/compose-kotlin
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Pure-JVM domain modules — the real work.
    implementation(project(":parser"))
    implementation(project(":classifier"))
    implementation(project(":budget"))
    implementation(project(":transactions"))

    // Android library modules — storage + ingestion.
    implementation(project(":database"))
    implementation(project(":ingestion"))

    // Room — the :app module calls Room.databaseBuilder() directly.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // WorkManager — drives the historical-import worker.
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Pulled in transitively by other androidx libs at an older rev; pinned
    // here because Macrobenchmark on API 34+ requires >=1.4.0 to install
    // baseline/startup profiles at measurement time.
    implementation("androidx.profileinstaller:profileinstaller:1.4.0")

    // Compose bill-of-materials pins all androidx.compose.* artifacts.
    val composeBom = platform("androidx.compose:compose-bom:2024.04.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // In-app autoupdater networking. Retrofit for the single GitHub
    // Releases endpoint; Moshi with reflection-based Kotlin adapter for
    // parsing (no codegen needed for one small DTO).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Vintage bridges the JUnit 4 Robolectric tests under debug. Kept
    // at testImplementation scope so both variants can load the engine
    // safely (release just won't have JUnit4 tests to route to it).
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    testImplementation("io.kotest:kotest-assertions-core:5.8.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // Real org.json implementation for unit tests — Android's stub in
    // mockable-android.jar has no method bodies. This shadowing pattern
    // is the standard way to exercise JSONObject/JSONArray in :app
    // unit tests without Robolectric.
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // Robolectric-backed Compose UI tests — run on JVM, no emulator.
    // Avoids Espresso's API-37 incompatibility (InputManager.getInstance
    // signature change). runComposeUiTest from ui-test-junit4 works
    // under Robolectric when paired with ui-test-manifest.
    //
    // Scoped to `testDebug*` only — release's manifest is stripped of
    // the ComponentActivity declaration that ui-test-manifest provides,
    // and we have no reason to run UI tests against the release variant.
    testDebugImplementation("org.robolectric:robolectric:4.12.2")
    testDebugImplementation("androidx.compose.ui:ui-test-junit4")
    testDebugImplementation("androidx.compose.ui:ui-test-manifest")
    testDebugImplementation("androidx.test:runner:1.6.2")
    testDebugImplementation("androidx.test.ext:junit:1.2.1")
    testDebugImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
