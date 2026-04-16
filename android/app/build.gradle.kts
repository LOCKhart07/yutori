plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.spendwise"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.spendwise"
        minSdk = 28          // decision 2026-04-15: API 28 floor
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing config.
    //
    // Reads SPENDWISE_* creds from env vars first (CI), then from Gradle
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

    val keystorePath: String? = signingProp("SPENDWISE_KEYSTORE_PATH")
    val keystorePassword: String? = signingProp("SPENDWISE_KEYSTORE_PASSWORD")
    val keyAlias: String? = signingProp("SPENDWISE_KEY_ALIAS")
    val keyPassword: String? = signingProp("SPENDWISE_KEY_PASSWORD")
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
