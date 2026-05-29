package io.github.magisk317.mipush.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConfigSyncMergerTest {

    @Test
    fun `merge produces IN_SYNC when sha matches`() {
        val remote = listOf(remoteFile("app.json", sha = "abc123"))
        val local = listOf(localSummary("app.json", sha = "abc123"))

        val result = mergeConfigEntries(remote, local, emptyMap())

        assertEquals(1, result.size)
        assertEquals(ConfigSyncStatus.IN_SYNC, result[0].status)
    }

    @Test
    fun `merge produces REMOTE_ONLY when no local file`() {
        val remote = listOf(remoteFile("app.json", sha = "abc123"))

        val result = mergeConfigEntries(remote, emptyList(), emptyMap())

        assertEquals(1, result.size)
        assertEquals(ConfigSyncStatus.REMOTE_ONLY, result[0].status)
    }

    @Test
    fun `merge produces LOCAL_ONLY when no remote file`() {
        val local = listOf(localSummary("app.json", sha = "abc123"))

        val result = mergeConfigEntries(emptyList(), local, emptyMap())

        assertEquals(1, result.size)
        assertEquals(ConfigSyncStatus.LOCAL_ONLY, result[0].status)
    }

    @Test
    fun `merge produces OUTDATED_LOCAL when sha differs and no sync record`() {
        val remote = listOf(remoteFile("app.json", sha = "remote-sha"))
        val local = listOf(localSummary("app.json", sha = "local-sha"))

        val result = mergeConfigEntries(remote, local, emptyMap())

        assertEquals(1, result.size)
        assertEquals(ConfigSyncStatus.OUTDATED_LOCAL, result[0].status)
    }

    @Test
    fun `merge produces MODIFIED_LOCAL when local sha differs from sync record`() {
        val remote = listOf(remoteFile("app.json", sha = "remote-sha"))
        val local = listOf(localSummary("app.json", sha = "edited-sha"))
        val records = mapOf("app.json" to ConfigSyncRecord("app.json", remoteSha = "remote-sha", localSha = "original-sha"))

        val result = mergeConfigEntries(remote, local, records)

        assertEquals(1, result.size)
        assertEquals(ConfigSyncStatus.MODIFIED_LOCAL, result[0].status)
    }

    @Test
    fun `merge produces INVALID_LOCAL when local file is invalid`() {
        val remote = listOf(remoteFile("app.json", sha = "abc123"))
        val local = listOf(localSummary("app.json", sha = "abc123", isValid = false))

        val result = mergeConfigEntries(remote, local, emptyMap())

        assertEquals(1, result.size)
        assertEquals(ConfigSyncStatus.INVALID_LOCAL, result[0].status)
    }

    @Test
    fun `merge combines and sorts entries from both sources`() {
        val remote = listOf(
            remoteFile("b_app.json", sha = "b"),
            remoteFile("a_app.json", sha = "a"),
        )
        val local = listOf(
            localSummary("c_app.json", sha = "c"),
            localSummary("a_app.json", sha = "a"),
        )

        val result = mergeConfigEntries(remote, local, emptyMap())

        assertEquals(3, result.size)
        assertEquals(listOf("a_app.json", "b_app.json", "c_app.json"), result.map { it.path })
        assertEquals(ConfigSyncStatus.IN_SYNC, result[0].status)
        assertEquals(ConfigSyncStatus.REMOTE_ONLY, result[1].status)
        assertEquals(ConfigSyncStatus.LOCAL_ONLY, result[2].status)
    }

    @Test
    fun `guessPackageConfigPath finds exact match`() {
        val paths = setOf("com.example.app.json", "com.example.other.json")
        assertEquals("com.example.app.json", guessPackageConfigPath("com.example.app", paths))
    }

    @Test
    fun `guessPackageConfigPath finds prefix match`() {
        val paths = setOf("com.example.app_custom.json")
        assertEquals("com.example.app_custom.json", guessPackageConfigPath("com.example.app", paths))
    }

    @Test
    fun `guessPackageConfigPath returns null when no match`() {
        val paths = setOf("com.other.app.json")
        assertNull(guessPackageConfigPath("com.example.app", paths))
    }

    // --- helpers ---

    private fun remoteFile(path: String, sha: String) = RemoteConfigFile(
        path = path,
        name = path.removeSuffix(".json"),
        sha = sha,
        size = 100,
        updatedAt = "2026-01-01T00:00:00Z",
    )

    private fun localSummary(path: String, sha: String, isValid: Boolean = true) = LocalConfigSummary(
        path = path,
        name = path.removeSuffix(".json"),
        sha = sha,
        size = 100L,
        lastModified = 0L,
        isValid = isValid,
    )
}
