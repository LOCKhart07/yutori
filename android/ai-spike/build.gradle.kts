plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Throwaway module: Stage A feasibility spike for issue #64 part 2
// (on-device LLM-assisted rule creation). This whole module —
// including the libs.litertlm* entries — is expected to either be
// promoted into :app or deleted after the go/no-go call.
//
// Installs as a separate app (applicationId = com.yutori.aispike) so it
// can live on the Nord alongside the real Yutori build without sharing
// signing keys or touching Yutori's data.
android {
    namespace = "com.yutori.aispike"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yutori.aispike"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "spike"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

detekt {
    // Throwaway code — don't fail CI on its style nits. The module is
    // behind its own applicationId and not shipped from :app:assembleRelease.
    ignoreFailures = true
}

dependencies {
    implementation(libs.litertlm.android)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
