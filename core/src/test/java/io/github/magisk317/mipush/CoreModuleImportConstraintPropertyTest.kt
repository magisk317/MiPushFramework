package io.github.magisk317.mipush

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Property-based test for core module import constraints.
 *
 * **Validates: Requirements 1.2, 7.7**
 *
 * Property 1: Core module import constraints —
 * For any `.kt` source file in `core/src/main/`, all import statements SHALL reference
 * only classes from the allowed set.
 */
class CoreModuleImportConstraintPropertyTest {

    companion object {
        /**
         * Forbidden import prefixes — packages that must NOT appear in core module sources.
         * These represent xmsf-specific packages, Xiaomi SDK, Xposed API, etc.
         */
        private val FORBIDDEN_IMPORT_PREFIXES = listOf(
            // xmsf-specific packages
            "io.github.magisk317.mipush.push.hook",
            "io.github.magisk317.mipush.push.pipeline",
            "io.github.magisk317.mipush.config",
            "io.github.magisk317.mipush.hook",
            "io.github.magisk317.mipush.service",
            "io.github.magisk317.mipush.receiver",
            "io.github.magisk317.mipush.app",
            // Xiaomi push SDK
            "com.xiaomi.xmpush",
            "com.xiaomi.push",
            "com.xiaomi.mipush.sdk",
            "com.xiaomi.channel",
            // Xposed API
            "de.robv.android.xposed",
            // Room database (should not be in core)
            "androidx.room",
        )

        /**
         * Allowed import prefixes — packages that ARE permitted in core module sources.
         * Based on core/build.gradle.kts dependencies and Requirement 1.2.
         */
        private val ALLOWED_IMPORT_PREFIXES = listOf(
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
            // :common module
            "io.github.magisk317.mipush.common",
            // Libraries declared in core/build.gradle.kts
            "io.github.aakira.napier",
            "androidx.core",
            "androidx.annotation",
            // Android framework classes allowed via androidx.core.ktx dependency
            "android.content.",
            "android.os.",
        )

        private val IMPORT_REGEX = Regex("""^\s*import\s+(.+)\s*$""")
    }

    /**
     * Property 1: Scan all `.kt` files in `core/src/main/` and assert no forbidden imports exist.
     *
     * **Validates: Requirements 1.2**
     */
    @Test
    fun `core module source files contain no forbidden imports`() {
        val coreMainDir = findCoreMainDir()
        val ktFiles = coreMainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertTrue(ktFiles.isNotEmpty(), "Expected to find .kt files in core/src/main/")

        val violations = mutableListOf<String>()

        for (file in ktFiles) {
            val relativePath = file.relativeTo(coreMainDir).path
            val lines = file.readLines()

            for ((lineNum, line) in lines.withIndex()) {
                val match = IMPORT_REGEX.matchEntire(line) ?: continue
                val importPath = match.groupValues[1].trim()

                for (forbidden in FORBIDDEN_IMPORT_PREFIXES) {
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
            "Core module contains ${violations.size} forbidden import(s):\n${violations.joinToString("\n")}"
        )
    }

    /**
     * Property 1 (extended): All imports in core module resolve to allowed packages.
     * This is a stricter check that verifies imports are from the known-good set.
     *
     * **Validates: Requirements 1.2, 7.7**
     */
    @Test
    fun `core module source files only contain allowed imports`() {
        val coreMainDir = findCoreMainDir()
        val ktFiles = coreMainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertTrue(ktFiles.isNotEmpty(), "Expected to find .kt files in core/src/main/")

        val unknownImports = mutableListOf<String>()

        for (file in ktFiles) {
            val relativePath = file.relativeTo(coreMainDir).path
            val lines = file.readLines()

            for ((lineNum, line) in lines.withIndex()) {
                val match = IMPORT_REGEX.matchEntire(line) ?: continue
                val importPath = match.groupValues[1].trim()

                val isAllowed = ALLOWED_IMPORT_PREFIXES.any { importPath.startsWith(it) }
                if (!isAllowed) {
                    unknownImports.add(
                        "$relativePath:${lineNum + 1} — unrecognized import: $importPath"
                    )
                }
            }
        }

        assertTrue(
            unknownImports.isEmpty(),
            "Core module contains ${unknownImports.size} import(s) not in the allowed set:\n${unknownImports.joinToString("\n")}"
        )
    }

    private fun findCoreMainDir(): File {
        // Try to locate core/src/main/ relative to the project root
        // The test runs from the project root or module root
        val candidates = listOf(
            File("core/src/main/java"),
            File("../core/src/main/java"),
            File(System.getProperty("user.dir"), "core/src/main/java"),
            // Absolute fallback for this project
            File("/home/lzc/wqk/MiPushFramework/core/src/main/java"),
        )

        val dir = candidates.firstOrNull { it.isDirectory }
            ?: error(
                "Cannot locate core/src/main/java directory. " +
                    "Tried: ${candidates.map { it.absolutePath }}"
            )

        return dir
    }
}
