plugins {
    alias(libs.plugins.android.application)
    // No org.jetbrains.kotlin.android: AGP 9 has built-in Kotlin support and applying the
    // old plugin alongside it is an error. The Compose compiler plugin is separate and its
    // version must match Kotlin exactly.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.careerseeker.dashboard"

    // compileSdk and targetSdk are deliberately different, and the difference matters:
    //
    //   compileSdk 37 -- which APIs the code may reference. Forced by the AndroidX
    //     dependencies: core-ktx 1.19.0 and lifecycle 2.11.0 refuse to be consumed by a
    //     project compiling against 36.
    //   targetSdk 36 -- which runtime behaviors the app opts into. This is Play's floor
    //     for new apps and updates from 2026-08-31 (verified 2026-07-22). Staying at 36
    //     avoids opting into Android 17 behavior changes nothing here has been tested
    //     against; raising it is a deliberate decision with a test pass behind it.
    //
    // The program spec guessed "assume 35+" and flagged it for re-verification. 35 would
    // be rejected by Play outright.
    compileSdk = 37

    defaultConfig {
        applicationId = "app.careerseeker.dashboard"
        minSdk = 26
        targetSdk = 36
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

    // Deliberately absent, and each absence is a commitment the store listing will repeat:
    // no Firebase, no Analytics, no Crashlytics, no ad SDK, no attribution SDK. The site
    // promises "nothing that watches you"; the dependency list is where that is either
    // true or not.
}
