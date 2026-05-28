package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Property-based test for FocusNotificationCache circular dependency constraint.
 *
 * **Validates: Requirements 7.6, 8.4**
 *
 * Property 6: No intra-batch circular dependency —
 * Verify `FocusNotificationCache` has no import back to xmsf packages.
 * This ensures the extracted notification logic in `:core` does not depend
 * on the `:xmsf` module, maintaining the unidirectional dependency flow.
 */
class FocusNotificationCacheCircularDependencyPropertyTest {

    companion object {
        /**
         * Forbidden import prefixes — xmsf-specific packages that must NOT appear
         * in FocusNotificationCache or any notification package file in core.
         */
        private val XMSF_IMPORT_PREFIXES = listOf(
            "io.github.magisk317.mipush.push",
            "io.github.magisk317.mipush.hook",
            "io.github.magisk317.mipush.service",
            "io.github.magisk317.mipush.receiver",
            "io.github.magisk317.mipush.app",
            "io.github.magisk317.mipush.config",
            // Xiaomi push SDK (xmsf-level dependency)
            "com.xiaomi.xmpush",
            "com.xiaomi.push.service",
            "com.xiaomi.mipush.sdk",
            "com.xiaomi.channel",
            // Xposed API
            "de.robv.android.xposed",
            // Room database
            "androidx.room",
        )

        private val IMPORT_REGEX = Regex("""^\s*import\s+(.+)\s*$""")
    }

    /**
     * Property 6: FocusNotificationCache.kt has no imports from xmsf packages.
     * This directly verifies the no-circular-dependency property for this specific file.
     *
     * **Validates: Requirements 8.4**
     */
    @Test
    fun `FocusNotificationCache has no imports from xmsf packages`() {
        val cacheFile = findFocusNotificationCacheFile()
        val lines = cacheFile.readLines()
        val violations = mutableListOf<String>()

        for ((lineNum, line) in lines.withIndex()) {
            val match = IMPORT_REGEX.matchEntire(line) ?: continue
            val importPath = match.groupValues[1].trim()

            for (forbidden in XMSF_IMPORT_PREFIXES) {
                if (importPath.startsWith(forbidden)) {
                    violations.add(
                        "Line ${lineNum + 1}: forbidden import '$importPath' (matches xmsf prefix '$forbidden')"
                    )
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "FocusNotificationCache.kt contains ${violations.size} circular dependency import(s):\n" +
                violations.joinToString("\n")
        )
    }

    /**
     * Property 6 (extended): All files in core's notification package have no imports
     * back to xmsf. This verifies the entire batch has no circular dependencies.
     *
     * **Validates: Requirements 8.4**
     */
    @Test
    fun `core notification package has no imports from xmsf packages`() {
        val notificationDir = findCoreNotificationDir()
        val ktFiles = notificationDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertTrue(
            ktFiles.isNotEmpty(),
            "Expected to find .kt files in core notification package"
        )

        val violations = mutableListOf<String>()

        for (file in ktFiles) {
            val relativePath = file.relativeTo(notificationDir).path
            val lines = file.readLines()

            for ((lineNum, line) in lines.withIndex()) {
                val match = IMPORT_REGEX.matchEntire(line) ?: continue
                val importPath = match.groupValues[1].trim()

                for (forbidden in XMSF_IMPORT_PREFIXES) {
                    if (importPath.startsWith(forbidden)) {
                        violations.add(
                            "$relativePath:${lineNum + 1} — forbidden import: $importPath (matches prefix '$forbidden')"
                        )
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Core notification package contains ${violations.size} circular dependency import(s):\n" +
                violations.joinToString("\n")
        )
    }

    /**
     * Property 6 (structural): FocusNotificationCache only imports from allowed packages.
     * Verifies all imports are from the known-good set for core module.
     *
     * **Validates: Requirements 7.6**
     */
    @Test
    fun `FocusNotificationCache only imports from allowed packages`() {
        val allowedPrefixes = listOf(
            // Kotlin stdlib
            "kotlin.",
            "kotlinx.",
            // Java stdlib
            "java.",
            "javax.",
            // Core module's own packages
            "io.github.magisk317.mipush.runtime.core",
            "io.github.magisk317.mipush.diagnostics",
            "io.github.magisk317.mipush.utils",
            "io.github.magisk317.mipush.notification",
            "io.github.magisk317.mipush.platform.support",
            "io.github.magisk317.mipush.common",
            // Libraries declared in core/build.gradle.kts
            "io.github.aakira.napier",
            "androidx.core",
            "androidx.annotation",
        )

        val cacheFile = findFocusNotificationCacheFile()
        val lines = cacheFile.readLines()
        val unknownImports = mutableListOf<String>()

        for ((lineNum, line) in lines.withIndex()) {
            val match = IMPORT_REGEX.matchEntire(line) ?: continue
            val importPath = match.groupValues[1].trim()

            val isAllowed = allowedPrefixes.any { importPath.startsWith(it) }
            if (!isAllowed) {
                unknownImports.add(
                    "Line ${lineNum + 1}: unrecognized import '$importPath'"
                )
            }
        }

        assertTrue(
            unknownImports.isEmpty(),
            "FocusNotificationCache.kt contains ${unknownImports.size} import(s) not in the allowed set:\n" +
                unknownImports.joinToString("\n")
        )
    }

    // --- Helper functions ---

    private fun findFocusNotificationCacheFile(): File {
        val candidates = listOf(
            File("core/src/main/java/io/github/magisk317/mipush/notification/FocusNotificationCache.kt"),
            File("../core/src/main/java/io/github/magisk317/mipush/notification/FocusNotificationCache.kt"),
            File(System.getProperty("user.dir"), "core/src/main/java/io/github/magisk317/mipush/notification/FocusNotificationCache.kt"),
            File("/home/lzc/wqk/MiPushFramework/core/src/main/java/io/github/magisk317/mipush/notification/FocusNotificationCache.kt"),
        )

        return candidates.firstOrNull { it.isFile }
            ?: error(
                "Cannot locate FocusNotificationCache.kt. " +
                    "Tried: ${candidates.map { it.absolutePath }}"
            )
    }

    private fun findCoreNotificationDir(): File {
        val candidates = listOf(
            File("core/src/main/java/io/github/magisk317/mipush/notification"),
            File("../core/src/main/java/io/github/magisk317/mipush/notification"),
            File(System.getProperty("user.dir"), "core/src/main/java/io/github/magisk317/mipush/notification"),
            File("/home/lzc/wqk/MiPushFramework/core/src/main/java/io/github/magisk317/mipush/notification"),
        )

        return candidates.firstOrNull { it.isDirectory }
            ?: error(
                "Cannot locate core notification directory. " +
                    "Tried: ${candidates.map { it.absolutePath }}"
            )
    }
}
