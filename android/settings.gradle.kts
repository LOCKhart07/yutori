pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

rootProject.name = "yutori"

include(":parser")
include(":classifier")
include(":budget")
include(":transactions")
include(":database")
include(":ingestion")
include(":app")
include(":macrobenchmark")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}
