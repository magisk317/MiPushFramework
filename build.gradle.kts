import java.util.Date
import java.util.Properties
import java.util.TimeZone

// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.aspectjx) apply false
}

val gitVersionCodeProvider = providers.exec {
    commandLine("git", "rev-list", "--first-parent", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: -1 }.orElse(-1)

val gitVersionNameFromGitProvider = providers.exec {
    commandLine("git", "describe", "--tags", "--dirty", "--exclude", "v*-*")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().takeIf { it.isNotEmpty() } ?: libs.versions.versionName.get() }
    .orElse(libs.versions.versionName.get())

fun isPreReleaseVersion(versionName: String): Boolean {
    return versionName.contains("-")
}

fun increaseVersionForPreRelease(versionName: String): String {
    if (isPreReleaseVersion(versionName)) {
        return versionName.replace(Regex("(\\d+\\.\\d+\\.)(\\d+)")) {
            it.groupValues[1] + (it.groupValues[2].toInt() + 1)
        }
    }
    return versionName
}

val gitVersionName = run {
    val name = (project.findProperty("versionName") as? String) 
        ?: (try { gitVersionNameFromGitProvider.get() } catch (e: Exception) { libs.versions.versionName.get() })
    increaseVersionForPreRelease(name).replace(Regex("^v"), "")
}

val gitVersionCode = try { gitVersionCodeProvider.get() } catch (e: Exception) { -1 }
val snapshot = false
val versionBase = gitVersionName
val versionNameStr = if (snapshot) "$versionBase-SNAPSHOT" else versionBase

allprojects {
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("com.github.promeg:tinypinyin"))
                .using(module("com.github.promeG:TinyPinyin:${libs.versions.tinypinyin.get()}"))
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

extra.apply {
    set("minSdkVersion", libs.versions.minSdk.get().toInt())
    set("compileSdkVersion", libs.versions.compileSdk.get().toInt())
    set("targetSdkVersion", libs.versions.targetSdk.get().toInt())
    set("pushVersionCode", libs.versions.versionCode.get())
    set("gitVersionCode", gitVersionCode)
    set("gitVersionName", gitVersionName)
    set("versionName", versionNameStr)
    set("gitTag", versionNameStr)
}

tasks.register<Exec>("exportVersion") {
    commandLine("sh")
    doLast {
        file("$projectDir/version.txt").writeText(versionNameStr)
        file("$projectDir/gitTag.txt").writeText(versionNameStr)
    }
}
