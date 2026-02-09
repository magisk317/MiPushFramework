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

tasks.matching { it.name == "check" }.configureEach {
    dependsOn("checkNoLegacyNihilityImports")
}

tasks.register<Exec>("exportVersion") {
    commandLine("sh")
    doLast {
        file("$projectDir/version.txt").writeText(versionNameStr)
        file("$projectDir/gitTag.txt").writeText(versionNameStr)
    }
}
