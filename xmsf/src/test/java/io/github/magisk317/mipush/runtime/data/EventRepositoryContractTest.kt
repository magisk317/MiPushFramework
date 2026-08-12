package io.github.magisk317.mipush.runtime.data

import android.content.Context
import com.xiaomi.push.service.XMPushServiceCore
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class EventRepositoryContractTest {

    @Test
    fun `mock replay bootstraps the private runtime core`() {
        val context: Context = RuntimeEnvironment.getApplication()

        val component = EventRepository.runtimeServiceIntent(context).component

        assertEquals(context.packageName, component?.packageName)
        assertEquals(XMPushServiceCore::class.java.name, component?.className)
    }

    @Test
    fun `restore only consumes a runtime owned deleted snapshot`() {
        val repository = readSource("io/github/magisk317/mipush/runtime/data/EventRepository.kt")
        val database = readSource("io/github/magisk317/mipush/runtime/store/db/EventDb.kt")
        val dao = readSource("io/github/magisk317/mipush/runtime/store/db/EventDao.kt")
        val adapter = readSource("io/github/magisk317/mipush/app/di/ManagerRuntimeAdapters.kt")

        assertTrue(repository.contains("EventDb.restoreDeletedEventAsync(preferredId, event.pkg, event.userId) ?: 0L"))
        assertTrue(repository.contains("EventDb.deleteByIdWithUndoSnapshotAsync(id, event.pkg, event.userId)"))
        assertTrue(database.contains("eventDao.deleteByIdWithUndoSnapshotForPackage(id, scopedUserId, packageName)"))
        assertTrue(database.contains("eventDao.restoreDeletedEventForPackage(id, userId.coerceAtLeast(0), packageName)"))
        assertTrue(dao.contains("val event = getById(id, userId) ?: return false"))
        assertTrue(dao.contains("if (event.pkg != packageName) return false"))
        assertTrue(dao.contains("if (deleted.pkg != packageName) return null"))
        assertTrue(dao.contains("insertDeletedEvent(DeletedEvent.fromEvent(event, System.currentTimeMillis()))"))
        assertTrue(adapter.contains("EventDebugJson.format(owned)"))
        assertFalse(adapter.contains("EventDebugJson.format(event)"))
        assertTrue(adapter.contains("resolveEventForMock(event) ?: return null"))
        assertTrue(adapter.contains("userId = userId,"))
        assertTrue(adapter.contains("userId = userId,\n            pkg = packageName,"))
        assertTrue(adapter.contains("it.userId = userId"))
        assertTrue(adapter.contains("RegSecUtils.getContainerWithRegSec(resolved.payload, resolved.regSec)\n            ?: return null"))
        assertFalse(repository.contains("EventDb.insertEventAsync(restored)"))
        assertFalse(repository.contains("EventDb.insertOrReplaceEventAsync(restored)"))
        assertTrue(adapter.contains("restoredId.takeIf { it > 0L }"))
    }

    private fun readSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("xmsf/src/main/java/$relativePath"),
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source not found: $relativePath from ${File(".").absolutePath}")
    }
}
