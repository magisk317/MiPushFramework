package io.github.magisk317.mipush.runtime.store.kmp

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Integration test for the runtime-store-kmp module.
 *
 * Verifies that the KMP Room database and DAOs work correctly on the Android platform.
 * Uses Android's default SQLite (not BundledSQLiteDriver) for Robolectric compatibility.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class RuntimeStoreKmpIntegrationTest {
    private lateinit var db: RuntimeStoreDatabase

    @BeforeEach
    fun setUp() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(
            context = context,
            klass = RuntimeStoreDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun database_isCreatedWithAllDaos() {
        assertNotNull(db.eventDao())
        assertNotNull(db.deletedEventDao())
        assertNotNull(db.registeredApplicationDao())
    }

    @Test
    fun eventDao_insertAndQuery_returnsInsertedRow() = runBlocking {
        val dao = db.eventDao()
        val row = RuntimeEventRow(
            pkg = "com.example.test",
            userId = 0,
            type = 1,
            date = 1000L,
            result = 0,
        )
        val id = dao.insert(row)
        assertTrue(id > 0, "insert should return a positive row id")

        val results = dao.findByUser(0)
        assertEquals(1, results.size)
        assertEquals("com.example.test", results[0].pkg)
        assertEquals(1, results[0].type)
    }

    @Test
    fun eventDao_findByUser_filtersByUser() = runBlocking {
        val dao = db.eventDao()
        dao.insert(RuntimeEventRow(pkg = "com.user0.app", userId = 0, type = 1, date = 1000L, result = 0))
        dao.insert(RuntimeEventRow(pkg = "com.user1.app", userId = 1, type = 2, date = 2000L, result = 0))

        val user0Results = dao.findByUser(0)
        val user1Results = dao.findByUser(1)

        assertEquals(1, user0Results.size)
        assertEquals(1, user1Results.size)
        assertEquals("com.user0.app", user0Results[0].pkg)
        assertEquals("com.user1.app", user1Results[0].pkg)
    }

    @Test
    fun deletedEventDao_insertAndQuery_returnsInsertedRow() = runBlocking {
        val dao = db.deletedEventDao()
        val row = RuntimeDeletedEventRow(
            id = 42L,
            userId = 0,
            pkg = "com.deleted.app",
            type = 2,
            date = 2000L,
            result = 0,
            deletedAt = 5000L,
        )
        dao.insert(row)

        val results = dao.findByUser(0)
        assertEquals(1, results.size)
        assertEquals(42L, results[0].id)
        assertEquals("com.deleted.app", results[0].pkg)
    }

    @Test
    fun registeredApplicationDao_insertAndQuery_returnsInsertedRow() = runBlocking {
        val dao = db.registeredApplicationDao()
        val row = RuntimeRegisteredApplicationRow(
            packageName = "com.registered.app",
            userId = 0,
            type = 1,
            notificationOnRegister = true,
            blocked = false,
            islandEnabled = true,
            islandFocusNotification = false,
            registeredType = 1,
            appName = "Registered App",
        )
        val id = dao.insert(row)
        assertTrue(id > 0, "insert should return a positive row id")

        val results = dao.findByUser(0)
        assertEquals(1, results.size)
        assertEquals("com.registered.app", results[0].packageName)
        assertEquals("Registered App", results[0].appName)
    }

    @Test
    fun multiUser_dataIsIsolatedAcrossAllTables() = runBlocking {
        val eventDao = db.eventDao()
        val deletedDao = db.deletedEventDao()
        val registeredDao = db.registeredApplicationDao()

        // Insert data for user 0
        eventDao.insert(RuntimeEventRow(pkg = "com.u0.event", userId = 0, type = 1, date = 1000L, result = 0))
        deletedDao.insert(RuntimeDeletedEventRow(id = 1L, userId = 0, pkg = "com.u0.deleted", type = 1, date = 1000L, result = 0, deletedAt = 1000L))
        registeredDao.insert(RuntimeRegisteredApplicationRow(packageName = "com.u0.app", userId = 0, type = 1, notificationOnRegister = true, registeredType = 1, appName = "U0 App"))

        // Insert data for user 1
        eventDao.insert(RuntimeEventRow(pkg = "com.u1.event", userId = 1, type = 1, date = 2000L, result = 0))
        deletedDao.insert(RuntimeDeletedEventRow(id = 2L, userId = 1, pkg = "com.u1.deleted", type = 1, date = 2000L, result = 0, deletedAt = 2000L))
        registeredDao.insert(RuntimeRegisteredApplicationRow(packageName = "com.u1.app", userId = 1, type = 1, notificationOnRegister = false, registeredType = 1, appName = "U1 App"))

        // Verify isolation
        assertEquals(1, eventDao.findByUser(0).size)
        assertEquals(1, eventDao.findByUser(1).size)
        assertEquals(1, deletedDao.findByUser(0).size)
        assertEquals(1, deletedDao.findByUser(1).size)
        assertEquals(1, registeredDao.findByUser(0).size)
        assertEquals(1, registeredDao.findByUser(1).size)
    }
}
