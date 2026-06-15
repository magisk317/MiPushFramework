import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import dev.detekt.gradle.extensions.DetektExtension
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.robolectric.junit5) apply false
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
                minBound(0)
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
    configurations.all {
        resolutionStrategy {
            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)
            force("io.netty:netty-codec:4.2.15.Final")
            force("io.netty:netty-codec-http:4.2.15.Final")
            force("io.netty:netty-codec-http2:4.2.15.Final")
            force("io.netty:netty-common:4.2.15.Final")
            force("io.netty:netty-handler:4.2.15.Final")
            force("io.netty:netty-handler-proxy:4.2.15.Final")
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

val catalog = libs
val forcedKotlinVersion = libs.versions.kotlin.get()
val forcedByteBuddyVersion = libs.versions.bytebuddy.get()
val detektBlockingProjects = setOf(":xposed")
val qualityGateKoverModules = listOf("common", "core", "xposed", "xmsf")

subprojects {
    fun Project.configureDetekt() {
        apply(plugin = "dev.detekt")
        val blocksNewViolations = path in detektBlockingProjects
        val detektBaselineFile = rootProject.layout.projectDirectory.file("config/detekt/baselines/${name}.xml")
        extensions.configure<DetektExtension> {
            autoCorrect = false
            parallel = true
            buildUponDefaultConfig = false
            config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
            if (blocksNewViolations) {
                baseline.set(detektBaselineFile)
            }
        }
        dependencies {
            "detektPlugins"(catalog.detekt.rules.ktlint)
        }
        tasks.withType<DetektCreateBaselineTask>().configureEach {
            if (blocksNewViolations) {
                baseline.set(detektBaselineFile)
            }
        }
        tasks.withType<Detekt>().configureEach {
            if (blocksNewViolations) {
                baseline.set(detektBaselineFile)
            }
            // Most modules stay report-only while xposed starts failing on findings outside its baseline.
            ignoreFailures = true
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
    configurations.configureEach {
        resolutionStrategy {
            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)
            force("io.netty:netty-codec:4.2.15.Final")
            force("io.netty:netty-codec-http:4.2.15.Final")
            force("io.netty:netty-codec-http2:4.2.15.Final")
            force("io.netty:netty-common:4.2.15.Final")
            force("io.netty:netty-handler:4.2.15.Final")
            force("io.netty:netty-handler-proxy:4.2.15.Final")
            force("org.apache.commons:commons-lang3:3.20.0")
            force("org.apache.httpcomponents:httpclient:4.5.14")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.bouncycastle:bcpkix-jdk18on:1.84")
            force("org.bouncycastle:bcprov-jdk18on:1.84")
            force("org.jdom:jdom2:2.0.6.1")
            // END AUTO FORCED DEPENDENCIES (managed by workflow)

            // Custom migration overrides for Java 26 compatibility
            force("org.jetbrains.kotlin:kotlin-metadata-jvm:$forcedKotlinVersion")
            force("org.ow2.asm:asm:9.10.1")
            force("org.ow2.asm:asm-commons:9.10.1")
            force("org.ow2.asm:asm-tree:9.10.1")
            force("org.ow2.asm:asm-analysis:9.10.1")
            force("org.ow2.asm:asm-util:9.10.1")
            force("net.bytebuddy:byte-buddy:$forcedByteBuddyVersion")
            force("net.bytebuddy:byte-buddy-agent:$forcedByteBuddyVersion")
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
