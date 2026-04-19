import io.gitlab.arturbosch.detekt.extensions.DetektExtension

// Plugin versions declared once for the whole build. Subprojects
// reference these plugins by id (no version) so there's a single
// source of truth and no classpath conflicts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<DetektExtension> {
        toolVersion = libs.versions.detekt.get()
        config.setFrom(rootProject.file("config/detekt.yml"))
        baseline = rootProject.file("config/detekt-baseline.xml")
        buildUponDefaultConfig = true
        allRules = false
    }
}
