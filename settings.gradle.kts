pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "erhu"
include(":app")
include(":third-party:jaudiotagger")
include(":core:common")
include(":core:designsystem")
include(":core:playback")
include(":core:model")
include(":core:datastore")
include(":core:datastore-proto")
include(":core:database")
include(":core:sound")
include(":core:network")
include(":core:vybe")
include(":core:vybe-mediapipe")
include(":feature:library")
include(":feature:settings")
include(":feature:transfer")
include(":benchmark")
