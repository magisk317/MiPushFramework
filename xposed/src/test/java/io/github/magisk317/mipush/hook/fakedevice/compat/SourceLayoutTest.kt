package io.github.magisk317.mipush.hook.fakedevice.compat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceLayoutTest {
    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: error("user.dir is unavailable")).absoluteFile
        while (true) {
            if (File(dir, "settings.gradle.kts").exists() && File(dir, "xposed").isDirectory) {
                return dir
            }
            val parent = dir.parentFile
                ?: error("Unable to locate MiPush repo root from ${dir.absolutePath}")
            dir = parent
        }
    }

    @Test
    fun `package declaration matches source path except explicit fake shims`() {
        val root = repoRoot()
        val sourceRoots = listOf(
            File(root, "common/src/main/java"),
            File(root, "xposed/src/main/java"),
        )
        val allowedExceptions = setOf(
            "xposed/src/main/java/io/github/magisk317/mipush/hook/fakedevice/fakeclass/SdkHelper.kt",
        )

        sourceRoots.forEach { sourceRoot ->
            sourceRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val relativePath = file.relativeTo(root).invariantSeparatorsPath
                    if (relativePath in allowedExceptions) {
                        return@forEach
                    }
                    val packageName = file.readLines().firstOrNull { it.startsWith("package ") }
                        ?.removePrefix("package ")
                        ?.trim()
                        ?: error("Missing package declaration in $relativePath")
                    val expectedSuffix = packageName.replace('.', '/') + "/" + file.name
                    assertTrue(
                        "Expected $relativePath to end with $expectedSuffix",
                        relativePath.endsWith(expectedSuffix),
                    )
                }
        }
    }

    @Test
    fun `source tree no longer carries old hms runtime naming`() {
        val root = repoRoot()
        val sourceRoots = listOf(
            File(root, "common/src/main/java"),
            File(root, "xposed/src/main/java"),
        )
        val allowedHuaweiInteropFiles = setOf(
            "common/src/main/java/io/github/magisk317/mipush/common/Constant.kt",
            "xposed/src/main/java/io/github/magisk317/mipush/hook/compat/legacyhuawei/LegacyHuaweiSignatureCompat.kt",
            "xposed/src/main/java/io/github/magisk317/mipush/hook/fakedevice/PinDuoDuo.kt",
        )

        sourceRoots.forEach { sourceRoot ->
            sourceRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val relativePath = file.relativeTo(root).invariantSeparatorsPath
                    val text = file.readText()
                    assertFalse("Found legacy HookHMS symbol in $relativePath", text.contains("HookHMS"))
                    assertFalse("Found legacy FakeHmsSignature symbol in $relativePath", text.contains("FakeHmsSignature"))
                    if (relativePath !in allowedHuaweiInteropFiles) {
                        assertFalse("Unexpected Huawei interop reference in $relativePath", text.contains("com.huawei"))
                        assertFalse("Unexpected HwPushReceiver reference in $relativePath", text.contains("HwPushReceiver"))
                    }
                }
        }
    }
}
