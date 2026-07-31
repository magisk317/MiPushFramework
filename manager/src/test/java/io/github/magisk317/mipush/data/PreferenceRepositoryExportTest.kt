package io.github.magisk317.mipush.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PreferenceRepositoryExportTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `runtime export includes defaults and persisted overrides`() = runBlocking {
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
                tempDirectory.resolve("runtime.preferences_pb").toFile()
            }
            val repository = PreferenceRepository(dataStore)

            val defaults = repository.exportOwnedPreferences(PreferenceOwner.RUNTIME).associateBy { it.key }
            assertEquals(PreferenceOwnership.runtimeKeys(), defaults.keys)
            assertEquals(
                OwnedPreferenceValue("xmpp_server", "string", "", PreferenceOwner.RUNTIME),
                defaults["xmpp_server"],
            )
            assertEquals(
                OwnedPreferenceValue("start_foreground", "boolean", "true", PreferenceOwner.RUNTIME),
                defaults["start_foreground"],
            )
            assertEquals(
                OwnedPreferenceValue("runtime_log_retention_days", "int", "2", PreferenceOwner.RUNTIME),
                defaults["runtime_log_retention_days"],
            )

            repository.setDebugMode(true)
            repository.setRuntimeLogRetentionDays(9)
            repository.setXmppServer("example.test")

            val updated = repository.exportOwnedPreferences(PreferenceOwner.RUNTIME).associateBy { it.key }
            assertEquals("true", updated.getValue("debug_mode").value)
            assertEquals("9", updated.getValue("runtime_log_retention_days").value)
            assertEquals("example.test", updated.getValue("xmpp_server").value)
        } finally {
            dataStoreScope.cancel()
        }
    }
}
