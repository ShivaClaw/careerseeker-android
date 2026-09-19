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

    // Captured as plain Files at configuration time. Referencing `project`, `projectDir`,
    // or the script `logger` from inside doLast captures a Gradle script object, which the
    // configuration cache cannot serialize.
    val coreSourceDir = layout.projectDirectory.dir("core/src").asFile
    val coreBuildFile = layout.projectDirectory.file("core/build.gradle.kts").asFile
    val repoRoot = layout.projectDirectory.asFile

    inputs.dir(coreSourceDir)
    inputs.file(coreBuildFile)

    doLast {
        val offenders = mutableListOf<String>()

        coreSourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { ix, line ->
                    val code = line.substringBefore("//").trim()
                    if (code.startsWith("import android.") || code.startsWith("import androidx.")) {
                        offenders += "${file.relativeTo(repoRoot)}:${ix + 1}: $code"
                    }
                }
            }

        // Scan the plugins {} block with comments stripped, not the whole file. Matching
        // raw text flags core/build.gradle.kts for the comment explaining WHY it must not
        // apply com.android.library -- a check that fails on its own documentation gets
        // deleted rather than fixed.
        val script = coreBuildFile.readText()
            .lines().joinToString("\n") { it.substringBefore("//") }
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")

        val pluginsBlock = Regex("""plugins\s*\{(.*?)}""", RegexOption.DOT_MATCHES_ALL)
            .find(script)?.groupValues?.get(1).orEmpty()

        if (pluginsBlock.contains("com.android") || pluginsBlock.contains("plugins.android")) {
            offenders += "core/build.gradle.kts applies an Android Gradle plugin"
        }

        if (offenders.isNotEmpty()) {
            throw GradleException(
                ":core must stay free of Android dependencies. Found:\n  " + offenders.joinToString("\n  "),
            )
        }

        println(":core is Android-free.")
    }
}

tasks.register("checkAll") {
    group = "verification"
    description = "Everything CI runs, in one entry point."
    dependsOn(checkCoreIsAndroidFree, ":core:test", ":app:assembleDebug", ":app:lintDebug")
}
