plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

/**
 * Enforces the ":core has no Android dependency" rule from the program spec (section 4.2).
 *
 * Asserted rather than trusted. The rule is what makes protocol logic testable without an
 * emulator and gives an eventual iOS port a starting point, and it is the kind of thing a
 * single convenient import erodes without anyone noticing in review.
 *
 * The structural guarantee is already there -- :core applies the Kotlin JVM plugin, not an
 * Android one, so the Android SDK is not on its classpath. This catches the case where
 * somebody "fixes" that by switching :core to com.android.library.
 */
val checkCoreIsAndroidFree by tasks.registering {
    group = "verification"
    description = "Fails if :core references the Android SDK."

    val coreSources = layout.projectDirectory.dir("core/src")
    val coreBuildFile = layout.projectDirectory.file("core/build.gradle.kts")
    inputs.dir(coreSources)
    inputs.file(coreBuildFile)

    doLast {
        val offenders = mutableListOf<String>()

        coreSources.asFile.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { ix, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import android.") || trimmed.startsWith("import androidx.")) {
                        offenders += "${file.relativeTo(projectDir)}:${ix + 1}: $trimmed"
                    }
                }
            }

        val buildScript = coreBuildFile.asFile.readText()
        if (buildScript.contains("com.android.")) {
            offenders += "core/build.gradle.kts applies an Android Gradle plugin"
        }

        if (offenders.isNotEmpty()) {
            throw GradleException(
                ":core must stay free of Android dependencies. Found:\n  " + offenders.joinToString("\n  "),
            )
        }

        logger.lifecycle(":core is Android-free.")
    }
}

tasks.register("checkAll") {
    group = "verification"
    description = "Everything CI runs, in one entry point."
    dependsOn(checkCoreIsAndroidFree, ":core:test", ":app:assembleDebug", ":app:lintDebug")
}
