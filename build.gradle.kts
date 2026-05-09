// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android) apply false
    id("magisk.maintenance")
}

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    configurations.all {
        resolutionStrategy {
            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)
            // END AUTO FORCED DEPENDENCIES (managed by workflow)
        }
    }
}

val versionNameOverride = providers.gradleProperty("versionName")
val versionNameProvider = versionNameOverride
    .orElse(libs.versions.versionName)
    .map { it.replace(Regex("^v"), "") }
val versionNameStr = try { versionNameProvider.get() } catch (e: Exception) { libs.versions.versionName.get() }
version = versionNameStr

val gitVersionCode = providers.exec {
    commandLine("git", "rev-list", "--first-parent", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: -1 }.orElse(-1)

extra["gitVersionCode"] = gitVersionCode
extra["gitVersionName"] = versionNameProvider
extra["APPLICATION_ID"] = "io.github.magisk317.mipush"

allprojects {
    configurations.configureEach {
        resolutionStrategy {
            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)
            // END AUTO FORCED DEPENDENCIES (managed by workflow)
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

// Maintenance task now automatically hooked via magisk.maintenance plugin

tasks.register("checkReadmeBuildRequirements") {
    group = "verification"
    description = "Fail if README platform and build requirements drift from the version catalog."
    val readme = layout.projectDirectory.file("README.md").asFile
    val minSdk = libs.versions.minSdk.get().toInt()
    val javaVersion = libs.versions.java.get()

    fun androidReleaseForApi(api: Int): String = when (api) {
        21 -> "5.0"
        22 -> "5.1"
        23 -> "6.0"
        24 -> "7.0"
        25 -> "7.1"
        26 -> "8.0"
        27 -> "8.1"
        28 -> "9.0"
        29 -> "10"
        30 -> "11"
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        else -> "API $api"
    }

    doLast {
        val content = readme.readText()
        val expectedAndroidBadge = "Android ${androidReleaseForApi(minSdk)}+"
        val expectedApiLine = "Android ${androidReleaseForApi(minSdk)}（API $minSdk）"
        val expectedJavaBadge = "Java ${javaVersion}+"
        val expectedJdkMarker = "JDK $javaVersion"
        val expectedGradleMarker = "Gradle 9.x"

        val missing = buildList {
            if (!content.contains(expectedAndroidBadge)) add(expectedAndroidBadge)
            if (!content.contains(expectedApiLine)) add(expectedApiLine)
            if (!content.contains(expectedJavaBadge)) add(expectedJavaBadge)
            if (!content.contains(expectedJdkMarker)) add(expectedJdkMarker)
            if (!content.contains(expectedGradleMarker)) add(expectedGradleMarker)
        }

        if (missing.isNotEmpty()) {
            val message = buildString {
                appendLine("README platform/build requirements drifted from gradle/libs.versions.toml.")
                appendLine("Missing expected markers:")
                missing.forEach { appendLine(" - $it") }
            }
            throw GradleException(message)
        }
    }
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn("checkReadmeBuildRequirements")
}

tasks.register("exportVersion") {
    doLast {
        file("$projectDir/version.txt").writeText(versionNameStr)
        file("$projectDir/gitTag.txt").writeText(versionNameStr)
    }
}
