package io.github.magisk317.mipush.runtime.store.db

import android.app.Application
import androidx.room.Room
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

/**
 * Validates Room database backup and recovery.
 * Ensures the v9 schema is backup-compatible and data integrity survives file-level copy.
 *
 * Phase B-3 validation: database recovery path has JVM-level coverage.
 *
 * Uses Robolectric's default SQLite driver (not BundledSQLiteDriver, which is only
 * available in androidTest/main classpaths).
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class AppDatabaseBackupRestoreTest {
    private lateinit var dbFile: File
    private lateinit var backupFile: File
    private lateinit var db: AppDatabase

    @BeforeEach
    fun setUp() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        dbFile = File(context.cacheDir, "test-backup-restore.db")
        backupFile = File(context.cacheDir, "test-backup-restore-backup.db")
        listOf(dbFile, backupFile).forEach { if (it.exists()) it.delete() }

        db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbFile.absolutePath,
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @AfterEach
    fun tearDown() {
        db.close()
        listOf(dbFile, backupFile).forEach { if (it.exists()) it.delete() }
    }

    @Test
    fun `database file is created and can be opened`() = runBlocking {
        // Force Room to initialize the database by running a query
        db.eventDao().query(0, 0, 1)
        assertTrue(dbFile.exists())
        assertTrue(dbFile.length() > 0)
    }

    @Test
    fun `write and read event roundtrip`() = runBlocking {
        val event = Event().apply {
            pkg = "com.example.test"
            userId = 0
            type = 1
            date = 1700000000L
            result = 0
        }
        db.eventDao().insert(event)

        val events = db.eventDao().query(0, 0, 100)
        assertEquals(1, events.size)
        assertEquals("com.example.test", events[0].pkg)
    }

    @Test
    fun `database can be backed up via file copy`() = runBlocking {
        val event = Event().apply {
            pkg = "com.example.backup"
            userId = 0
            type = 2
            date = 1800000000L
            result = 1
        }
        db.eventDao().insert(event)
        db.close()

        dbFile.copyTo(backupFile, overwrite = true)
        assertTrue(backupFile.exists())
        assertEquals(dbFile.length(), backupFile.length())

        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val restoredDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            backupFile.absolutePath,
        )
            .fallbackToDestructiveMigration()
            .build()

        val events = restoredDb.eventDao().query(0, 0, 100)
        assertEquals(1, events.size)
        assertEquals("com.example.backup", events[0].pkg)
        assertEquals(2, events[0].type)
        restoredDb.close()
    }

    @Test
    fun `database survives multi-user data isolation after backup`() = runBlocking {
        db.eventDao().insert(Event().apply {
            pkg = "com.user0.app"
            userId = 0
            type = 1
            date = 1000L
            result = 0
        })
        db.eventDao().insert(Event().apply {
            pkg = "com.user999.app"
            userId = 999
            type = 2
            date = 2000L
            result = 0
        })
        db.close()

        dbFile.copyTo(backupFile, overwrite = true)
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val restoredDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            backupFile.absolutePath,
        )
            .fallbackToDestructiveMigration()
            .build()

        val user0Events = restoredDb.eventDao().query(0, 0, 100)
        val user999Events = restoredDb.eventDao().query(0, 999, 100)

        assertEquals(1, user0Events.size)
        assertEquals(1, user999Events.size)
        assertEquals("com.user0.app", user0Events[0].pkg)
        assertEquals("com.user999.app", user999Events[0].pkg)
        restoredDb.close()
    }

    @Test
    fun `registered application data survives backup`() = runBlocking {
        val app = RegisteredApplication().apply {
            packageName = "com.example.registered"
            userId = 0
            type = 1
            notificationOnRegister = true
            blocked = false
            islandEnabled = true
            islandFocusNotification = true
            registeredType = 2
            appName = "TestApp"
        }
        db.registeredApplicationDao().insert(app)
        db.close()

        dbFile.copyTo(backupFile, overwrite = true)
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val restoredDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            backupFile.absolutePath,
        )
            .fallbackToDestructiveMigration()
            .build()

        val apps = restoredDb.registeredApplicationDao().getAll(0)
        assertEquals(1, apps.size)
        assertEquals("com.example.registered", apps[0].packageName)
        assertEquals("TestApp", apps[0].appName)
        restoredDb.close()
    }
}
