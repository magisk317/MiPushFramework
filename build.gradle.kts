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
            force("io.netty:netty-codec:4.2.13.Final")
            force("io.netty:netty-codec-http:4.2.13.Final")
            force("io.netty:netty-codec-http2:4.2.13.Final")
            force("io.netty:netty-common:4.2.13.Final")
            force("io.netty:netty-handler:4.2.13.Final")
            force("io.netty:netty-handler-proxy:4.2.13.Final")
            force("org.apache.commons:commons-lang3:3.20.0")
            force("org.apache.httpcomponents:httpclient:4.5.14")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.bouncycastle:bcpkix-jdk18on:1.84")
            force("org.bouncycastle:bcprov-jdk18on:1.84")
            force("org.jdom:jdom2:2.0.6.1")
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
            force("io.netty:netty-codec:4.2.13.Final")
            force("io.netty:netty-codec-http:4.2.13.Final")
            force("io.netty:netty-codec-http2:4.2.13.Final")
            force("io.netty:netty-common:4.2.13.Final")
            force("io.netty:netty-handler:4.2.13.Final")
            force("io.netty:netty-handler-proxy:4.2.13.Final")
            force("org.apache.commons:commons-lang3:3.20.0")
            force("org.apache.httpcomponents:httpclient:4.5.14")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.bouncycastle:bcpkix-jdk18on:1.84")
            force("org.bouncycastle:bcprov-jdk18on:1.84")
            force("org.jdom:jdom2:2.0.6.1")
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
        28 -> "9.0"
        29 -> "10"
        30 -> "11"
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        37 -> "17"
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

tasks.register<Exec>("verifyModuleBoundaries") {
    group = "verification"
    description = "Fail when UI/settings code adds new direct imports of deep Xiaomi runtime/protocol types."
    commandLine("bash", "scripts/verify_module_boundaries.sh")
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn("checkReadmeBuildRequirements")
    dependsOn("verifyModuleBoundaries")
}

tasks.register("exportVersion") {
    doLast {
        file("$projectDir/version.txt").writeText(versionNameStr)
        file("$projectDir/gitTag.txt").writeText(versionNameStr)
    }
}
