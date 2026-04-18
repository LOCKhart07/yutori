plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yutori.database"
    compileSdk = 34

    defaultConfig {
        minSdk = 28

        // Room's schema export — one JSON per DB version. Checked in so
        // migrations can be diffed and automatically verified.
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        // Include the schema export directory so Room can read existing
        // versions for migration validation in instrumentation tests.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    // Domain types that Room entities map to/from.
    implementation(project(":parser"))
    implementation(project(":classifier"))
    implementation(project(":budget"))
    implementation(project(":transactions"))

    // Room. Use KSP (not kapt) — faster build, Kotlin-native annotation
    // processing.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- Mapper unit tests (pure-JVM) ---
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core)

    // --- DAO instrumentation tests (emulator) ---
    androidTestImplementation(libs.androidx.test.ext.junit.db)
    androidTestImplementation(libs.androidx.test.runner.db)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
