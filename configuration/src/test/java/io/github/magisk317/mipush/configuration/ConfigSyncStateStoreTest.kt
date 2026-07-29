package io.github.magisk317.mipush.configuration

import android.app.Application
import io.github.magisk317.mipush.utils.ConfigSyncRecord
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ConfigSyncStateStoreTest {
    private lateinit var application: Application
    private lateinit var store: ConfigSyncStateStore

    @BeforeEach
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        File(application.filesDir, "config-sync").deleteRecursively()
        store = ConfigSyncStateStore(application)
    }

    @Test
    fun `concurrent upserts preserve every record`() = runBlocking {
        coroutineScope {
            (0 until 50).map { index ->
                async {
                    store.upsert(
                        directoryUri = "content://configs",
                        record = ConfigSyncRecord(path = "config-$index.json", remoteSha = "sha-$index"),
                    )
                }
            }.awaitAll()
        }

        val records = store.getDirectoryRecords("content://configs")
        assertEquals(50, records.size)
        assertEquals("sha-49", records["config-49.json"]?.remoteSha)
    }

    @Test
    fun `corrupt snapshot fails closed to empty state`() = runBlocking {
        val stateFile = File(application.filesDir, "config-sync/state.json")
        stateFile.parentFile!!.mkdirs()
        stateFile.writeText("{not-json")

        assertEquals(emptyMap<String, ConfigSyncRecord>(), store.getDirectoryRecords("content://configs"))
    }
}
