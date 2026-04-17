// Macrobenchmark test module — runs on a real device against the
// :app `benchmark` variant. Not part of the regular unit-test run;
// invoke explicitly with `:macrobenchmark:connectedBenchmarkAndroidTest`.
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yutori.macrobenchmark"
    compileSdk = 34

    defaultConfig {
        // Macrobenchmark itself requires API 23+. App minSdk is 28,
        // so 28 keeps the two aligned and avoids accidental forks.
        minSdk = 28
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // One variant — matches the `benchmark` build type on :app so
    // measurements run against a release-shaped (non-debuggable) APK.
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    // MacrobenchmarkRule tried to grant WRITE_EXTERNAL_STORAGE unconditionally
    // in 1.2.x–1.3.x, which fails on Android 14+ where the permission is no
    // longer a runtime permission. 1.4.x skips the grant on new SDKs.
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}
