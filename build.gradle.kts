// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
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

tasks.register<Exec>("exportVersion") {
    commandLine("sh")
    doLast {
        file("$projectDir/version.txt").writeText(versionNameStr)
        file("$projectDir/gitTag.txt").writeText(versionNameStr)
    }
}
