// Top-level build file where you can add configuration options common to all sub-projects/modules.

import org.gradle.api.tasks.Exec

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

val versionNameOverride = providers.gradleProperty("versionName")
val versionNameProvider = versionNameOverride
    .orElse(libs.versions.versionName)
    .map { it.replace(Regex("^v"), "") }
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

tasks.register<Exec>("cleanupGradleCaches") {
    group = "maintenance"
    description = "Remove stale Gradle version caches under the project-local .gradle directory."
    workingDir = rootProject.projectDir
    commandLine("bash", "${rootProject.projectDir}/scripts/cleanup_gradle_caches.sh")
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

tasks.register("checkNoLegacyFeaturePackages") {
    group = "verification"
    description = "Fail if migrated owned feature packages still use legacy top.trumeet package declarations."
    val sourceRoot = layout.projectDirectory.dir("push/src/main/java").asFile
    val forbiddenPackages = setOf(
        "package top.trumeet.ui.theme",
        "package top.trumeet.mipushframework.component",
        "package top.trumeet.mipushframework.navigation",
        "package top.trumeet.mipushframework.data",
        "package top.trumeet.mipushframework.di",
        "package top.trumeet.mipushframework.config",
        "package top.trumeet.mipushframework.utils",
        "package top.trumeet.mipushframework.main.subpage",
        "package top.trumeet.mipushframework.wizard.permission",
        "package top.trumeet.mipush.provider",
    )

    doLast {
        if (!sourceRoot.exists()) return@doLast
        val violations = mutableListOf<String>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (forbiddenPackages.any { pkg -> line.contains(pkg) }) {
                            val rel = file.relativeTo(sourceRoot).invariantSeparatorsPath
                            violations += "$rel:${index + 1}"
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Found legacy package declarations in migrated feature namespaces:")
                violations.sorted().forEach { appendLine(" - $it") }
            }
            throw GradleException(message)
        }
    }
}

tasks.register("checkNoLegacyImportsInMigratedPackages") {
    group = "verification"
    description = "Fail if migrated io.github.magisk317.mipush packages import migrated legacy top.trumeet namespaces."
    val sourceRoot = layout.projectDirectory.dir("push/src/main/java/io/github/magisk317/mipush").asFile
    val projectRoot = layout.projectDirectory.asFile
    val allowedCompatPaths = setOf(
        "push/src/main/java/io/github/magisk317/mipush/platform/support/LegacyUiEntryPoints.kt",
    )
    val forbiddenImports = setOf(
        "top.trumeet.ui.theme",
        "top.trumeet.mipushframework.component",
        "top.trumeet.mipushframework.navigation",
        "top.trumeet.mipushframework.data",
        "top.trumeet.mipushframework.di",
        "top.trumeet.mipushframework.config",
        "top.trumeet.mipushframework.utils",
        "top.trumeet.mipushframework.main.subpage",
        "top.trumeet.mipushframework.wizard.permission",
        "top.trumeet.mipush.provider",
        "top.trumeet.mipushframework.MainActivityUtils",
        "top.trumeet.mipushframework.MainActivityOperation",
        "top.trumeet.mipushframework.main.AppConfigurationUtils",
        "top.trumeet.mipushframework.main.AppRegistrationDiagnostics",
        "top.trumeet.mipushframework.main.ApplicationIconCache",
        "top.trumeet.mipushframework.main.RegistrationStateStyle",
    )

    doLast {
        if (!sourceRoot.exists()) return@doLast
        val violations = mutableListOf<String>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .forEach { file ->
                val rel = file.relativeTo(projectRoot).invariantSeparatorsPath
                if (rel in allowedCompatPaths) return@forEach
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (forbiddenImports.any { pkg -> line.contains(pkg) }) {
                            violations += "$rel:${index + 1}"
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Found legacy migrated imports inside io.github.magisk317.mipush packages:")
                violations.sorted().forEach { appendLine(" - $it") }
            }
            throw GradleException(message)
        }
    }
}

tasks.register("checkNoLegacyCommonPackages") {
    group = "verification"
    description = "Fail if migrated common/platform code still uses legacy top.trumeet.common package declarations or imports, excluding compat shims and generated namespace references."
    val commonSourceRoot = layout.projectDirectory.dir("common/src/main/java").asFile
    val projectRoot = layout.projectDirectory.asFile
    val otherSourceRoots = listOf(
        projectRoot.resolve("push/src/main/java"),
        projectRoot.resolve("push/src/test"),
        projectRoot.resolve("runtime-core/src/main/java"),
        projectRoot.resolve("runtime-core/src/test"),
    )
    val allowedLegacyReferencePaths = setOf(
        "push/src/main/java/io/github/magisk317/mipush/platform/support/LegacyComponentNames.kt",
    )

    doLast {
        val violations = mutableListOf<String>()

        if (commonSourceRoot.exists()) {
            commonSourceRoot.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    val rel = file.relativeTo(commonSourceRoot).invariantSeparatorsPath
                    if (rel == "top/trumeet/common/ita/DetectionService.kt") return@forEach

                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            if (line.contains("package top.trumeet.common")) {
                                violations += "common/src/main/java/$rel:${index + 1}"
                            }
                        }
                    }
                }
        }

        otherSourceRoots.forEach { root ->
            if (!root.exists()) return@forEach
            root.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    val rel = file.relativeTo(projectRoot).invariantSeparatorsPath
                    if (rel in allowedLegacyReferencePaths) return@forEach
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val hasLegacyImport = line.contains("top.trumeet.common")
                            val allowedImport = line.contains("top.trumeet.common.R") || line.contains("top.trumeet.common.BuildConfig")
                            if (hasLegacyImport && !allowedImport) {
                                violations += "$rel:${index + 1}"
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Found legacy top.trumeet.common references outside allowed compat cases:")
                violations.sorted().forEach { appendLine(" - $it") }
            }
            throw GradleException(message)
        }
    }
}

tasks.register("checkLegacyCompatEntryPointsOnly") {
    group = "verification"
    description = "Fail if legacy top.trumeet source roots contain files beyond approved compatibility entrypoints."
    val projectRoot = layout.projectDirectory.asFile
    val legacyRoots = listOf(
        projectRoot.resolve("push/src/main/java/top/trumeet/mipushframework"),
        projectRoot.resolve("push/src/main/java/top/trumeet/mipush"),
        projectRoot.resolve("push/src/test/java/top/trumeet/mipush"),
        projectRoot.resolve("common/src/main/java/top/trumeet/common"),
    )
    val allowedFiles = setOf(
        "push/src/main/java/top/trumeet/mipushframework/main/MainActivity.kt",
        "push/src/main/java/top/trumeet/mipushframework/main/ApplicationInfoPage.kt",
        "push/src/main/java/top/trumeet/mipushframework/main/HelpPage.kt",
        "push/src/main/java/top/trumeet/mipushframework/main/RecentEventListPage.kt",
        "push/src/main/java/top/trumeet/mipushframework/wizard/RequestPermissionPage.kt",
        "push/src/main/java/top/trumeet/mipushframework/wizard/WelcomeActivity.kt",
        "common/src/main/java/top/trumeet/common/ita/DetectionService.kt",
    )

    doLast {
        val actualLegacyFiles = legacyRoots.asSequence()
            .filter { it.exists() }
            .flatMap { root ->
                root.walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                    .map { it.relativeTo(projectRoot).invariantSeparatorsPath }
            }
            .toSortedSet()

        val unexpected = actualLegacyFiles - allowedFiles
        if (unexpected.isNotEmpty()) {
            val message = buildString {
                appendLine("Found unexpected legacy top.trumeet source files. Keep only approved compatibility entrypoints:")
                unexpected.forEach { appendLine(" - $it") }
            }
            throw GradleException(message)
        }
    }
}

tasks.register("checkNoDirectDataStoreManagerUsage") {
    group = "verification"
    description = "Fail if DataStoreManager is referenced outside the compatibility facade."
    val projectRoot = layout.projectDirectory.asFile
    val sourceRoots = listOf(
        projectRoot.resolve("push/src/main/java"),
        projectRoot.resolve("common/src/main/java"),
    )
    val allowedPaths = setOf(
        "push/src/main/java/com/magisk317/data/DataStoreManager.kt",
    )

    doLast {
        val violations = mutableListOf<String>()
        sourceRoots.forEach { root ->
            if (!root.exists()) return@forEach
            root.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    val rel = file.relativeTo(projectRoot).invariantSeparatorsPath
                    if (rel in allowedPaths) return@forEach
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            if (line.contains("DataStoreManager")) {
                                violations += "$rel:${index + 1}"
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Found direct DataStoreManager references outside approved compatibility paths:")
                violations.sorted().forEach { appendLine(" - $it") }
            }
            throw GradleException(message)
        }
    }
}

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
    dependsOn("checkNoLegacyNihilityImports")
    dependsOn("checkNoLegacyDialogActionButtons")
    dependsOn("checkNoLegacyFeaturePackages")
    dependsOn("checkNoLegacyImportsInMigratedPackages")
    dependsOn("checkNoLegacyCommonPackages")
    dependsOn("checkLegacyCompatEntryPointsOnly")
    dependsOn("checkNoDirectDataStoreManagerUsage")
    dependsOn("checkReadmeBuildRequirements")
}

tasks.register<Exec>("exportVersion") {
    commandLine("sh")
    doLast {
        file("$projectDir/version.txt").writeText(versionNameStr)
        file("$projectDir/gitTag.txt").writeText(versionNameStr)
    }
}
