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

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CareerSeeker Dashboard"

// :core is a pure-Kotlin JVM module holding the sync protocol, crypto, and domain
// types -- deliberately no Android dependency, so the iOS port has a starting point
// and so protocol logic is testable without an emulator. The Android-free property
// is enforced by a check in build.gradle.kts, not by convention.
include(":core")
include(":app")
