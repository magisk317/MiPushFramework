// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android) apply false
}

val securityOverrides: Map<String, String> = run {
    val file = rootProject.file("gradle/security-overrides.properties")
    if (!file.exists()) {
        emptyMap()
    } else {
        val props = java.util.Properties()
        file.reader().use { props.load(it) }
        props.stringPropertyNames().associateWith { props.getProperty(it) }
    }
}

val gitVersionNameFromGitProvider = providers.exec {
    commandLine("git", "describe", "--tags", "--dirty", "--exclude", "v*-*")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().takeIf { it.isNotEmpty() } ?: libs.versions.versionName.get() }
    .orElse(libs.versions.versionName.get())

val versionNameOverride = providers.gradleProperty("versionName")
val snapshotEnabled = providers.gradleProperty("snapshot")
    .map { value -> value.isBlank() || value.equals("true", ignoreCase = true) }
    .orElse(false)
val versionBaseProvider = versionNameOverride
    .orElse(gitVersionNameFromGitProvider)
    .map { it.replace(Regex("^v"), "") }
val versionNameProvider = versionBaseProvider.zip(snapshotEnabled) { base, snapshot ->
    if (snapshot) "$base-SNAPSHOT" else base
}
val versionNameStr = try { versionNameProvider.get() } catch (e: Exception) { libs.versions.versionName.get() }
version = versionNameStr

allprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val key = "${requested.group}:${requested.name}"
            val forcedVersion = securityOverrides[key]
            if (!forcedVersion.isNullOrBlank() && requested.version != forcedVersion) {
                useVersion(forcedVersion)
                because("Security override from gradle/security-overrides.properties")
            }
        }
    }

    gradle.taskGraph.whenReady {
        allTasks.forEach { task ->
            if (task.name == "mockableAndroidJar") {
                task.enabled = false
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

tasks.named<Wrapper>("wrapper") {
    val gradlewFile = layout.projectDirectory.file("gradlew")
    doLast {
        val file = gradlewFile.asFile
        if (file.exists()) {
            val content = file.readText()
            val cleanupScript = """
# Cleanup old Gradle caches
if [ -d "${"$"}APP_HOME/.gradle" ]; then
    (
        cd "${"$"}APP_HOME/.gradle" || exit
        # Find all version-like directories starting with a digit
        versions=$(ls -d [0-9]* 2>/dev/null)
        if [ -n "${"$"}versions" ]; then
            # Sort versions and keep the last one (latest)
            # Standard sort works fine for timestamped versions
            latest=$(echo "${"$"}versions" | sort | tail -n 1)

            # Iterate and remove non-latest versions
            for d in ${"$"}versions; do
                if [ "${"$"}d" != "${"$"}latest" ]; then
                    echo "Cleaning up old Gradle cache: ${"$"}d"
                    rm -rf "${"$"}d"
                fi
            done
        fi
    )
fi

"""
            if (!content.contains("Cleaning up old Gradle cache")) {
                val execCommand = "exec \"\$JAVACMD\" \"\$@\""
                if (content.contains(execCommand)) {
                    val replacement = """
"${"$"}JAVACMD" "${"$"}@"
EXIT_CODE=${"$"}?

$cleanupScript
exit ${"$"}EXIT_CODE
"""
                    val finalContent = content.replace(execCommand, replacement.trim())
                    file.writeText(finalContent)
                    println("Injected cleanup script into gradlew")
                }
            }
        }
    }
}

tasks.register("checkNoLegacyNihilityImports") {
    group = "verification"
    description = "Fail if com.nihility is imported outside compatibility shims."
    val sourceRoot = layout.projectDirectory.dir("push/src/main/java").asFile
    doLast {
        if (!sourceRoot.exists()) return@doLast

        val allowedPaths = setOf(
            "com/nihility/",
            "com/xiaomi/xmsf/push/notification/NotificationManagerEx.kt",
            "com/magisk317/hook/LegacyHookApi.kt",
            "com/magisk317/utils/Singleton.kt",
            "com/magisk317/service/XMPushServiceListener.kt"
        )

        val violations = mutableListOf<String>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .forEach { file ->
                val rel = file.relativeTo(sourceRoot).invariantSeparatorsPath
                val allowed = allowedPaths.any { marker ->
                    if (marker.endsWith("/")) rel.startsWith(marker) else rel == marker
                }
                if (allowed) return@forEach

                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (line.contains("com.nihility.")) {
                            violations += "$rel:${index + 1}"
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Found forbidden com.nihility references outside compatibility shims:")
                violations.sorted().forEach { appendLine(" - $it") }
            }
            throw GradleException(message)
        }
    }
}

tasks.register("checkNoLegacyDialogActionButtons") {
    group = "verification"
    description = "Fail if AlertDialog confirm/dismiss slots use direct TextButton rows instead of DialogActionRow."
    val sourceRoot = layout.projectDirectory.dir("push/src/main/java").asFile
    doLast {
        if (!sourceRoot.exists()) return@doLast

        val violations = mutableListOf<String>()
        val buttonSlotPattern = Regex("""\b(confirmButton|dismissButton)\s*=\s*\{""")

        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val lines = file.readLines()
                for (index in lines.indices) {
                    if (!buttonSlotPattern.containsMatchIn(lines[index])) continue

                    val end = minOf(lines.lastIndex, index + 40)
                    val block = lines.subList(index, end + 1).joinToString("\n")
                    val hasLegacyTextButton = block.contains("TextButton(")
                    val hasDialogActionRow = block.contains("DialogActionRow(")
                    if (hasLegacyTextButton && !hasDialogActionRow) {
                        val rel = file.relativeTo(sourceRoot).invariantSeparatorsPath
                        violations += "$rel:${index + 1}"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Found legacy AlertDialog action buttons. Use DialogActionRow instead:")
                violations.sorted().forEach { appendLine(" - $it") }
            }
            throw GradleException(message)
        }
    }
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn("checkNoLegacyNihilityImports")
    dependsOn("checkNoLegacyDialogActionButtons")
}

tasks.register<Exec>("exportVersion") {
    commandLine("sh")
    doLast {
        file("$projectDir/version.txt").writeText(versionNameStr)
        file("$projectDir/gitTag.txt").writeText(versionNameStr)
    }
}
