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
    // Promoted from testImplementation to implementation in A2, deliberately.
    //
    // :core previously advertised "zero dependencies". Holding that line was costing
    // correctness: the receiver was locating the payload `kind` by scanning the decrypted
    // bytes for the first `"kind"` substring, and the decrypted bytes are exactly where
    // untrusted job and recruiter text lives (Sync-Protocol.md §8.6). Carried text
    // containing `"kind":"..."` ahead of the real field would have misrouted the envelope.
    // Parsing it properly is worth more than the slogan.
    //
    // The trade is smaller than it looks: kotlinx-serialization-json is already an
    // implementation dependency of :app, so it is ALREADY in the shipped APK -- this adds
    // no new artifact and no new attack surface. It is pure Kotlin, multiplatform, and
    // Android-free, so both the checkCoreIsAndroidFree rule and the eventual iOS target
    // are unaffected. The dependency-light posture that matters is the one about
    // third-party CRYPTO (Tink was dropped for it); that still holds -- crypto is JCA-only.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
