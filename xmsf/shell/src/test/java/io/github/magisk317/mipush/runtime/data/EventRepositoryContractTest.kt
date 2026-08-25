package io.github.magisk317.mipush.runtime.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class EventRepositoryContractTest {
    @Test
    fun `restore only consumes a runtime owned deleted snapshot`() {
        val repository = readSource("io/github/magisk317/mipush/runtime/data/EventRepository.kt")
        val database = readSource("io/github/magisk317/mipush/runtime/store/db/EventDb.kt")
        val dao = readSource(
            "runtime/store/src/commonMain/kotlin/io/github/magisk317/mipush/runtime/store/kmp/RuntimeStoreDaos.kt"
        )
        val adapter = readSource("io/github/magisk317/mipush/app/di/ManagerRuntimeAdapters.kt")

        assertTrue(repository.contains("EventDb.restoreDeletedEventAsync(preferredId, event.pkg, event.userId) ?: 0L"))
        assertTrue(repository.contains("EventDb.deleteByIdWithUndoSnapshotAsync(id, event.pkg, event.userId)"))
        assertTrue(database.contains("eventDao.deleteByIdWithUndoSnapshotForPackage(id, userId, packageName)"))
        assertTrue(database.contains("eventDao.restoreDeletedEventForPackage(id, userId, packageName)"))
        assertTrue(dao.contains("val event = getById(id, userId) ?: return false"))
        assertTrue(dao.contains("if (event.pkg != packageName) return false"))
        assertTrue(dao.contains("if (deleted.pkg != packageName) return null"))
        assertTrue(dao.contains("insertDeletedEvent(event.toDeletedEvent(System.currentTimeMillis()))"))
        assertTrue(adapter.contains("EventDebugJson.format(owned)"))
        assertFalse(adapter.contains("EventDebugJson.format(event)"))
        assertTrue(adapter.contains("resolveEventForMock(event) ?: return null"))
        assertTrue(adapter.contains("userId = userId,"))
        assertTrue(adapter.contains("userId = userId,\n            pkg = packageName,"))
        assertTrue(adapter.contains("userId = userId"))
        assertTrue(adapter.contains("RegSecUtils.getContainerWithRegSec(resolved.payload, resolved.regSec)\n            ?: return null"))
        assertFalse(repository.contains("EventDb.insertEventAsync(restored)"))
        assertFalse(repository.contains("EventDb.insertOrReplaceEventAsync(restored)"))
        assertTrue(adapter.contains("restoredId.takeIf { it > 0L }"))
    }

    private fun readSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("../runtime/src/main/java/$relativePath"),
            File("../$relativePath"),
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source not found: $relativePath from ${File(".").absolutePath}")
    }
}
