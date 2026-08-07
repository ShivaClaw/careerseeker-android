plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM on purpose. Applying com.android.library here would put the Android
// SDK on the classpath and quietly end the KMP-ready property -- the root project's
// checkCoreIsAndroidFree task fails the build if that happens.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
