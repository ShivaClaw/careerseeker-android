plugins {
    alias(libs.plugins.android.application)
    // No org.jetbrains.kotlin.android: AGP 9 has built-in Kotlin support and applying the
    // old plugin alongside it is an error. The Compose compiler plugin is separate and its
    // version must match Kotlin exactly.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.careerseeker.dashboard"

    // compileSdk = which APIs the code may reference. 37 is forced by AndroidX: core-ktx
    // 1.19.0 and lifecycle 2.11.0 refuse to be consumed by a project compiling against 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "app.careerseeker.dashboard"
        minSdk = 26

        // targetSdk = which runtime behaviors the app opts into.
        //
        // Play's FLOOR is 36 for new apps and updates from 2026-08-31 (verified
        // 2026-07-22) -- the program spec's "assume 35+" would be rejected outright. 37
        // clears that floor.
        //
        // Set to 37 rather than the floor because lint's OldTargetApi treats anything
        // below the latest as an error, and it is right to: there is nothing in this
        // scaffold whose behavior could regress under Android 17, so the usual reason to
        // lag (untested behavior changes) does not apply yet. When real features land,
        // bumping targetSdk becomes a decision with a test pass behind it -- and
        // suppressing OldTargetApi now would silence the check that prompts exactly that.
        targetSdk = 37

        versionCode = 1
        versionName = "0.1.0-p0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    kotlin {
        jvmToolchain(17)
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
    }

    testOptions {
        unitTests {
            // Robolectric: real Android framework classes (including SQLite) on the JVM, so
            // the Room replica tests run in CI without an emulator (P2-Runbook §2.3).
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    // Committed schema snapshots make Room's schema an explicit, reviewable artifact and
    // unlock migration tests when version 2 ever exists.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    // The Room replica (P2-Runbook §2.3): the phone-side mirror the screens project.
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    // Payload JSON parsing in the applier — runtime tree API only, no codegen plugin.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.kotlinx.coroutines.test)
    // Compose screen tests under Robolectric (no emulator). The test-manifest artifact adds
    // the test ComponentActivity to the DEBUG manifest only; nothing ships in release.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Deliberately absent, and each absence is a commitment the store listing will repeat:
    // no Firebase, no Analytics, no Crashlytics, no ad SDK, no attribution SDK. The site
    // promises "nothing that watches you"; the dependency list is where that is either
    // true or not.
}
