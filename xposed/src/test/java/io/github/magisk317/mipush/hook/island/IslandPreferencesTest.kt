package io.github.magisk317.mipush.hook.island

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class IslandPreferencesTest {
    @BeforeEach
    fun reset() {
        IslandPreferences.resetForTest()
    }

    @Test
    fun `default options keep focus payload injection disabled`() {
        val options = IslandPreferences.current()

        assertTrue(options.enabled)
        assertFalse(options.focusNotification)
        assertFalse(options.canInjectFocusPayload)
        assertEquals(5, options.timeoutSecs)
    }

    @Test
    fun `focus payload requires both total switch and focus switch`() {
        assertFalse(
            IslandOptions(enabled = false, focusNotification = true).canInjectFocusPayload
        )
        assertFalse(
            IslandOptions(enabled = true, focusNotification = false).canInjectFocusPayload
        )
        assertTrue(
            IslandOptions(enabled = true, focusNotification = true).canInjectFocusPayload
        )
    }

    @Test
    fun `refresh keeps stale package value until asynchronous replacement arrives`() {
        val packageName = "example.app"
        val packageOptions = IslandOptions(enabled = false, showNotification = false)
        IslandPreferences.cachePackageOptionsForTest(packageName, packageOptions, userId = 0)

        val refresh = IslandPreferences.prepareRefresh()

        assertEquals(packageOptions, IslandPreferences.current(packageName, userId = 0))
        assertTrue(IslandPreferences.PackageKey(0, packageName) in refresh.packageNames)
    }

    @Test
    fun `package settings cache is isolated by user`() {
        val packageName = "example.app"
        val ownerOptions = IslandOptions(enabled = false)
        val cloneOptions = IslandOptions(enabled = true)
        IslandPreferences.cachePackageOptionsForTest(packageName, ownerOptions, userId = 0)
        IslandPreferences.cachePackageOptionsForTest(packageName, cloneOptions, userId = 999)

        assertEquals(ownerOptions, IslandPreferences.current(packageName, userId = 0))
        assertEquals(cloneOptions, IslandPreferences.current(packageName, userId = 999))
    }

    @Test
    fun `unknown package user does not reuse the primary user cache`() {
        val packageName = "example.app"
        val ownerOptions = IslandOptions(enabled = true, focusNotification = true)
        IslandPreferences.cachePackageOptionsForTest(packageName, ownerOptions, userId = 0)

        val options = IslandPreferences.current(packageName)

        assertFalse(options.enabled)
        assertFalse(options.focusNotification)
    }

    @Test
    fun `uncached package does not inherit globally enabled focus mode`() {
        IslandPreferences.resetForTest(
            IslandOptions(enabled = true, enableFloat = true, focusNotification = true)
        )

        assertFalse(IslandPreferences.current("uncached.app").canInjectFocusPayload)
    }

    @Test
    fun `provider read does not convert missing results into default options`() {
        val source = sourceFile().readText()

        assertTrue(source.contains("} ?: error(\"island preference provider returned no cursor\")"))
        assertTrue(source.contains("check(values.isNotEmpty()) { \"island preference provider returned no values\" }"))
        assertFalse(source.contains("}.orEmpty()"))
    }

    private fun sourceFile(): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir is unavailable"))
        while (!File(directory, "settings.gradle.kts").isFile) {
            directory = directory.parentFile ?: error("repository root not found")
        }
        return File(
            directory,
            "xposed/src/main/java/io/github/magisk317/mipush/hook/island/IslandPreferences.kt",
        )
    }
}
