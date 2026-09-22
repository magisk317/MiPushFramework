package io.github.magisk317.mipush.runtime.store.kmp

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeRegisteredApplicationRepositoryTest {
    @Test
    fun `register creates a scoped default row and reuses it`() = runBlocking {
        val store = FakeStore()
        val repository = repository(store)

        val created = repository.registerApplicationOutcome("com.example.app")
        val existing = repository.registerApplicationOutcome("com.example.app")

        assertTrue(created.created)
        assertEquals(1, created.application.id)
        assertEquals(7, created.application.userId)
        assertEquals(RegisteredAppType.ASK, created.application.type)
        assertEquals(RegisteredAppRegisteredType.NotRegistered, created.application.registeredType)
        assertEquals("Example", created.application.appName)
        assertFalse(existing.created)
        assertEquals(created.application, existing.application)
        assertEquals(1, store.insertCount)
    }

    @Test
    fun `get list and update always use the repository user scope`() = runBlocking {
        val store = FakeStore(
            rows = mutableListOf(
                row(id = 2, packageName = "com.example.app", userId = 7),
                row(id = 3, packageName = "com.other.app", userId = 8),
            ),
        )
        val repository = repository(store)

        assertEquals(listOf("com.example.app"), repository.getList(null).map { it.packageName })
        assertEquals(listOf("com.example.app"), repository.getList("").map { it.packageName })
        assertEquals(listOf("com.example.app"), repository.getList("com.example.app").map { it.packageName })
        assertNull(repository.getRegisteredApplication("com.other.app"))

        val updatedId = repository.update(
            row(id = 999, packageName = "com.example.app", userId = 99).copy(blocked = true),
        )

        assertEquals(2, updatedId)
        assertEquals(7, store.require("com.example.app").userId)
        assertTrue(store.require("com.example.app").blocked)
    }

    @Test
    fun `blocked and island settings read from the scoped application`() = runBlocking {
        val store = FakeStore(
            rows = mutableListOf(
                row(id = 5, packageName = "com.example.app", userId = 7).copy(
                    blocked = true,
                    islandEnabled = false,
                    islandFocusNotification = true,
                ),
            ),
        )
        val repository = repository(store)

        assertTrue(repository.isBlocked("com.example.app"))
        assertEquals(
            RuntimeIslandSettings(enabled = false, focusNotification = true),
            repository.getIslandSettings("com.example.app"),
        )
        assertFalse(repository.isBlocked("com.missing.app"))
        assertNull(repository.getIslandSettings("com.missing.app"))
        assertEquals(1, repository.updateBlocked(5, false))
        assertFalse(repository.isBlocked("com.example.app"))
    }

    @Test
    fun `click fallback flag defaults off and updates through the scoped store`() = runBlocking {
        val store = FakeStore(
            rows = mutableListOf(
                row(id = 5, packageName = "com.example.app", userId = 7),
            ),
        )
        val repository = repository(store)

        assertFalse(repository.isClickFallbackEnabled("com.example.app"))
        assertFalse(repository.isClickFallbackEnabled("com.missing.app"))
        assertEquals(1, repository.updateClickFallbackEnabled(5, true))
        assertTrue(repository.isClickFallbackEnabled("com.example.app"))
        assertEquals(1, repository.updateClickFallbackEnabled(5, false))
        assertFalse(repository.isClickFallbackEnabled("com.example.app"))
        assertEquals(0, repository.updateClickFallbackEnabled(999, true))
    }

    @Test
    fun `unregistration reports missing, idempotent and updated transitions`() = runBlocking {
        val store = FakeStore(
            rows = mutableListOf(
                row(id = 5, packageName = "com.registered.app", userId = 7).copy(
                    registeredType = RegisteredAppRegisteredType.Registered,
                ),
                row(id = 6, packageName = "com.unregistered.app", userId = 7).copy(
                    registeredType = RegisteredAppRegisteredType.Unregistered,
                ),
            ),
        )
        val repository = repository(store)

        assertEquals(
            RuntimeUnregistrationResult.Missing,
            repository.markUnregistered("com.missing.app"),
        )
        assertEquals(
            RuntimeUnregistrationResult.AlreadyUnregistered,
            repository.markUnregistered("com.unregistered.app"),
        )
        assertEquals(
            RuntimeUnregistrationResult.Updated,
            repository.markUnregistered("com.registered.app"),
        )
        assertEquals(
            RegisteredAppRegisteredType.Unregistered,
            store.require("com.registered.app").registeredType,
        )
    }

    private fun repository(store: FakeStore): RuntimeRegisteredApplicationRepository =
        RuntimeRegisteredApplicationRepository(
            store = store,
            userId = 7,
            appNameForPackage = { "Example" },
        )

    private fun row(
        id: Long,
        packageName: String,
        userId: Int,
    ): RuntimeRegisteredApplicationRow = RuntimeRegisteredApplicationRow(
        id = id,
        packageName = packageName,
        userId = userId,
        type = RegisteredAppType.ASK,
        notificationOnRegister = true,
        registeredType = RegisteredAppRegisteredType.NotRegistered,
        appName = packageName,
    )

    private class FakeStore(
        val rows: MutableList<RuntimeRegisteredApplicationRow> = mutableListOf(),
    ) : RuntimeRegisteredApplicationStore {
        var insertCount: Int = 0
            private set

        override suspend fun getByPackageName(
            packageName: String,
            userId: Int,
        ): RuntimeRegisteredApplicationRow? = rows.firstOrNull {
            it.packageName == packageName && it.userId == userId
        }

        override suspend fun getAll(userId: Int): List<RuntimeRegisteredApplicationRow> =
            rows.filter { it.userId == userId }

        override suspend fun insert(application: RuntimeRegisteredApplicationRow): Long {
            insertCount++
            val id = application.id ?: ((rows.mapNotNull { it.id }.maxOrNull() ?: 0L) + 1L)
            rows += application.copy(id = id)
            return id
        }

        override suspend fun insertOrReplace(application: RuntimeRegisteredApplicationRow): Long {
            val id = application.id ?: ((rows.mapNotNull { it.id }.maxOrNull() ?: 0L) + 1L)
            rows.removeAll { it.id == id && it.userId == application.userId }
            rows.removeAll {
                it.packageName == application.packageName && it.userId == application.userId
            }
            rows += application.copy(id = id)
            return id
        }

        override suspend fun update(application: RuntimeRegisteredApplicationRow): Int {
            val index = rows.indexOfFirst {
                it.id == application.id && it.userId == application.userId
            }
            if (index < 0) return 0
            rows[index] = application
            return 1
        }

        override suspend fun updateBlocked(id: Long, blocked: Boolean, userId: Int): Int {
            val index = rows.indexOfFirst { it.id == id && it.userId == userId }
            if (index < 0) return 0
            rows[index] = rows[index].copy(blocked = blocked)
            return 1
        }

        override suspend fun isBlocked(packageName: String, userId: Int): Boolean? =
            getByPackageName(packageName, userId)?.blocked

        override suspend fun isClickFallbackEnabled(packageName: String, userId: Int): Boolean? =
            getByPackageName(packageName, userId)?.clickFallbackEnabled

        override suspend fun updateClickFallbackEnabled(id: Long, enabled: Boolean, userId: Int): Int {
            val index = rows.indexOfFirst { it.id == id && it.userId == userId }
            if (index < 0) return 0
            rows[index] = rows[index].copy(clickFallbackEnabled = enabled)
            return 1
        }

        fun require(packageName: String): RuntimeRegisteredApplicationRow =
            rows.first { it.packageName == packageName }
    }
}
