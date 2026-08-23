package io.github.magisk317.mipush.utils

import android.app.Application
import android.content.Context
import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
@Execution(ExecutionMode.SAME_THREAD)
class ActiveConfigurationSnapshotStoreRobolectricTest {
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ActiveConfigurationSnapshotStore.directory(context).deleteRecursively()
    }

    @AfterEach
    fun tearDown() {
        ActiveConfigurationSnapshotStore.directory(context).deleteRecursively()
    }

    @Test
    fun `persist replaces a complete snapshot and removes staging files`() {
        val fileName = "active.json"
        val first = "{\"version\":1}".toByteArray()
        val replacement = ("{\"version\":2,\"payload\":\"" + "x".repeat(32_768) + "\"}").toByteArray()

        assertTrue(ActiveConfigurationSnapshotStore.persist(context, fileName, first))
        assertTrue(ActiveConfigurationSnapshotStore.persist(context, fileName, replacement))

        val directory = ActiveConfigurationSnapshotStore.directory(context)
        assertArrayEquals(replacement, File(directory, fileName).readBytes())
        assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `failed replacement keeps an existing target intact`() {
        val fileName = "blocked.json"
        val target = File(ActiveConfigurationSnapshotStore.directory(context), fileName).apply {
            mkdirs()
            File(this, "marker").writeText("keep")
        }

        assertFalse(ActiveConfigurationSnapshotStore.persist(context, fileName, "new".toByteArray()))
        assertTrue(target.isDirectory)
        assertTrue(File(target, "marker").readText() == "keep")
    }
}
