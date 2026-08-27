import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import dev.detekt.gradle.extensions.DetektExtension
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    id("magisk.maintenance")
}

val enableKover = providers.gradleProperty("enableKover")
    .map { it.toBooleanStrictOrNull() ?: false }
    .getOrElse(false) ||
    gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("kover", ignoreCase = true)
    }

fun KoverProjectExtension.configureProjectKoverVerification() {
    reports {
        verify {
            rule {
                minBound(10)
            }
        }
    }
}

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val versionNameOverride = providers.gradleProperty("versionName")
val versionNameProvider = versionNameOverride
    .orElse(libs.versions.versionName)
    .map { it.replace(Regex("^v"), "") }
val versionNameStr = try { versionNameProvider.get() } catch (e: Exception) { libs.versions.versionName.get() }
version = versionNameStr

val gitVersionCode = providers.gradleProperty("versionCode")
    .map { requireNotNull(it.toIntOrNull()) { "Invalid -PversionCode=$it" } }
    .orElse(
        providers.exec {
            commandLine("git", "rev-list", "--first-parent", "--count", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim().toIntOrNull() ?: -1 }.orElse(-1),
    )

val gitCommit = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }.orElse("unknown")

extra["gitVersionCode"] = gitVersionCode
extra["gitVersionName"] = versionNameProvider
extra["gitCommit"] = gitCommit
extra["APPLICATION_ID"] = "io.github.magisk317.mipush"

val catalog = libs
val detektBlockingProjects = setOf(
    ":app",
    ":common",
    ":core",
    ":diagnostics",
    ":magisk-ui-kit",
    ":magisk-xposed-kit",
    ":magisk-xposed-kit:diagnostics",
    ":magisk-xposed-kit:logging",
    ":manager:ui",
    ":manager:contract",
    ":manager:client",
    ":mipush",
    ":pinned",
    ":settings",
    ":vendor",
    ":xmsf:runtime",
    ":xmsf:shell",
    ":xposed",
)
val qualityGateKoverModules = listOf("common", "core", "xposed", "xmsf")

subprojects {
    fun Project.configureDetekt() {
        apply(plugin = "dev.detekt")
        val blocksNewViolations = path in detektBlockingProjects
        val detektBaselineFile = rootProject.layout.projectDirectory.file("config/detekt/baselines/${name}.xml")
        val hasDetektBaseline = detektBaselineFile.asFile.isFile
        extensions.configure<DetektExtension> {
            autoCorrect = false
            parallel = true
            buildUponDefaultConfig = false
            config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
            if (blocksNewViolations && hasDetektBaseline) {
                baseline.set(detektBaselineFile)
            }
        }
        dependencies {
            "detektPlugins"(catalog.detekt.rules.ktlint)
        }
        tasks.withType<DetektCreateBaselineTask>().configureEach {
            if (blocksNewViolations && hasDetektBaseline) {
                baseline.set(detektBaselineFile)
            }
        }
        tasks.withType<Detekt>().configureEach {
            if (blocksNewViolations && hasDetektBaseline) {
                baseline.set(detektBaselineFile)
            }
            // Every project in detektBlockingProjects is strict; only existing baselines are honored.
            ignoreFailures = !blocksNewViolations
            reports {
                html.required.set(true)
                checkstyle.required.set(true)
                sarif.required.set(true)
                markdown.required.set(false)
            }
        }
    }

    if (enableKover) {
        apply(plugin = "org.jetbrains.kotlinx.kover")
        extensions.configure<KoverProjectExtension>("kover") {
            configureProjectKoverVerification()
        }
    }

    pluginManager.withPlugin("com.android.application") {
        configureDetekt()
    }
    pluginManager.withPlugin("com.android.library") {
        configureDetekt()
    }
}

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

tasks.register("qualityGateDetekt") {
    group = "verification"
    description = "Runs Detekt checks that fail on new violations."
    dependsOn(detektBlockingProjects.map { "$it:detekt" })
}

tasks.register("qualityGateKoverReports") {
    group = "verification"
    description = "Generates XML and HTML Kover reports for the Phase 4 target modules."
    dependsOn(qualityGateKoverModules.flatMap { module ->
        listOf(":$module:koverXmlReport", ":$module:koverHtmlReport")
    })
}

tasks.register("qualityGateKoverVerify") {
    group = "verification"
    description = "Runs Kover verification for the Phase 4 target modules."
    dependsOn(qualityGateKoverModules.map { module -> ":$module:koverVerify" })
}

tasks.register("qualityGateKover") {
    group = "verification"
    description = "Runs Phase 4 Kover report generation and verification."
    dependsOn("qualityGateKoverReports", "qualityGateKoverVerify")
}

// Maintenance task now automatically hooked via magisk.maintenance plugin

tasks.register<Exec>("verifyModuleBoundaries") {
    group = "verification"
    description = "Fail when UI/settings code adds new direct imports of deep Xiaomi runtime/protocol types."
    commandLine("bash", "scripts/verify_module_boundaries.sh")
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn("qualityGateDetekt")
    if (enableKover) {
        dependsOn("qualityGateKoverVerify")
    }
    dependsOn("verifyModuleBoundaries")
}

tasks.register("exportVersion") {
    doLast {
        file("$projectDir/version.txt").writeText(versionNameStr)
        file("$projectDir/gitTag.txt").writeText(versionNameStr)
    }
}
