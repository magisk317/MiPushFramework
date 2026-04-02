package top.trumeet.mipushframework.config

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class ConfigSyncMergerTest {
    @Test
    fun mergeConfigEntries_classifiesStatuses() {
        val remoteFiles = listOf(
            RemoteConfigFile(
                path = "com.example.remote.json",
                name = "remote",
                sha = "remote-sha",
                size = 10,
                updatedAt = "2026-04-02T12:00:00Z",
            ),
            RemoteConfigFile(
                path = "com.example.same.json",
                name = "same",
                sha = "same-sha",
                size = 10,
                updatedAt = "2026-04-02T12:00:00Z",
            ),
            RemoteConfigFile(
                path = "com.example.changed.json",
                name = "changed",
                sha = "remote-new-sha",
                size = 10,
                updatedAt = "2026-04-02T12:00:00Z",
            ),
        )
        val localFiles = listOf(
            local(path = "com.example.local.json", sha = "local-sha"),
            local(path = "com.example.same.json", sha = "same-sha"),
            local(path = "com.example.changed.json", sha = "local-new-sha"),
            local(path = "com.example.invalid.json", sha = "bad-sha", isValid = false),
        )
        val syncRecords = mapOf(
            "com.example.changed.json" to ConfigSyncRecord(
                path = "com.example.changed.json",
                remoteSha = "remote-old-sha",
                localSha = "different-local-old-sha",
                syncedAt = 1L,
            ),
        )

        val merged = mergeConfigEntries(remoteFiles, localFiles, syncRecords).associateBy { it.path }

        assertEquals(ConfigSyncStatus.REMOTE_ONLY, merged.getValue("com.example.remote.json").status)
        assertEquals(ConfigSyncStatus.LOCAL_ONLY, merged.getValue("com.example.local.json").status)
        assertEquals(ConfigSyncStatus.IN_SYNC, merged.getValue("com.example.same.json").status)
        assertEquals(ConfigSyncStatus.MODIFIED_LOCAL, merged.getValue("com.example.changed.json").status)
        assertEquals(ConfigSyncStatus.INVALID_LOCAL, merged.getValue("com.example.invalid.json").status)
    }

    @Test
    fun guessPackageConfigPath_prefersPackagePrefixedFile() {
        val result = guessPackageConfigPath(
            "com.ss.android.lark",
            listOf(
                "0_基础配置_开关.json",
                "com.ss.android.lark_飞书.json",
                "com.tencent.mobileqq_QQ.json",
            ),
        )

        assertEquals("com.ss.android.lark_飞书.json", result)
    }

    private fun local(
        path: String,
        sha: String,
        isValid: Boolean = true,
    ): LocalConfigFile {
        return LocalConfigFile(
            path = path,
            name = path.removeSuffix(".json"),
            uri = mock(),
            sha = sha,
            size = 10L,
            lastModified = 1L,
            isValid = isValid,
            validationError = if (isValid) null else "bad json",
        )
    }
}
