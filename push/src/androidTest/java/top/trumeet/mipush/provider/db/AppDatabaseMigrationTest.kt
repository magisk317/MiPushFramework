package top.trumeet.mipush.provider.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.trumeet.mipush.provider.entities.RegisteredApplication

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_preservesCoreRows() {
        val dbName = "migration-test-db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `EVENT` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT,
                    `pkg` TEXT NOT NULL,
                    `type` INTEGER NOT NULL,
                    `date` INTEGER NOT NULL,
                    `result` INTEGER NOT NULL,
                    `dev_info` TEXT,
                    `payload` BLOB,
                    `reg_sec` TEXT
                )
                """.trimIndent()
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_EVENT_pkg` ON `EVENT` (`pkg`)")
            execSQL("CREATE INDEX IF NOT EXISTS `index_EVENT_date` ON `EVENT` (`date`)")
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `REGISTERED_APPLICATION` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT,
                    `pkg` TEXT NOT NULL,
                    `type` INTEGER NOT NULL,
                    `notification_on_register` INTEGER NOT NULL,
                    `registered_type` INTEGER NOT NULL,
                    `app_name` TEXT NOT NULL
                )
                """.trimIndent()
            )
            execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_REGISTERED_APPLICATION_pkg` ON `REGISTERED_APPLICATION` (`pkg`)"
            )
            execSQL(
                """
                INSERT INTO `REGISTERED_APPLICATION`
                (`id`, `pkg`, `type`, `notification_on_register`, `registered_type`, `app_name`)
                VALUES (1, 'com.example.app', 2, 1, 1, 'Example App')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `EVENT`
                (`id`, `pkg`, `type`, `date`, `result`, `dev_info`, `payload`, `reg_sec`)
                VALUES (1, 'com.example.app', 21, 1700000000000, 0, '{"ok":true}', NULL, 'sec')
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 2, true, AppDatabaseMigrations.MIGRATION_1_2)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabaseMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val app = runBlocking { db.registeredApplicationDao().getByPackageName("com.example.app") }
            assertNotNull(app)
            assertEquals(RegisteredApplication.RegisteredType.Registered, app!!.registeredType)
            assertEquals("Example App", app.appName)

            val events = runBlocking {
                db.eventDao().queryRaw(
                    SimpleSQLiteQuery("SELECT * FROM EVENT WHERE pkg = ?", arrayOf("com.example.app"))
                )
            }
            assertTrue(events.isNotEmpty())
            assertEquals(21, events.first().type)
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }
}

