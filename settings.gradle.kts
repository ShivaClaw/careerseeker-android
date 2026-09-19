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
    // :core pins jvmToolchain(17); this resolver lets Gradle download a JDK 17 when the
    // one running Gradle differs (e.g. Android Studio's bundled JBR is 21). Without it,
    // toolchain provisioning fails on any machine without a system JDK 17.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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
