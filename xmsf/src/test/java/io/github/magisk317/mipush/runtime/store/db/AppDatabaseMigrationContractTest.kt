package io.github.magisk317.mipush.runtime.store.db

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppDatabaseMigrationContractTest {

    @Test
    fun `migration 1 to 2 creates required indexes`() {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqlSlot = slot<String>()
        every { db.execSQL(capture(sqlSlot)) } answers {
            statements += sqlSlot.captured
        }

        AppDatabaseMigrations.MIGRATION_1_2.migrate(db)

        assertEquals(
            listOf(
                "CREATE INDEX IF NOT EXISTS `index_EVENT_pkg` ON `EVENT` (`pkg`)",
                "CREATE INDEX IF NOT EXISTS `index_EVENT_date` ON `EVENT` (`date`)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_REGISTERED_APPLICATION_pkg` ON `REGISTERED_APPLICATION` (`pkg`)",
            ),
            statements,
        )
    }

    @Test
    fun `migration registry exposes all migrations`() {
        assertEquals(8, AppDatabaseMigrations.ALL.size)
        assertEquals(1, AppDatabaseMigrations.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabaseMigrations.MIGRATION_1_2.endVersion)

        assertEquals(2, AppDatabaseMigrations.MIGRATION_2_3.startVersion)
        assertEquals(3, AppDatabaseMigrations.MIGRATION_2_3.endVersion)

        assertEquals(3, AppDatabaseMigrations.MIGRATION_3_4.startVersion)
        assertEquals(4, AppDatabaseMigrations.MIGRATION_3_4.endVersion)

        assertEquals(4, AppDatabaseMigrations.MIGRATION_4_5.startVersion)
        assertEquals(5, AppDatabaseMigrations.MIGRATION_4_5.endVersion)

        assertEquals(5, AppDatabaseMigrations.MIGRATION_5_6.startVersion)
        assertEquals(6, AppDatabaseMigrations.MIGRATION_5_6.endVersion)
        assertEquals(6, AppDatabaseMigrations.MIGRATION_6_7.startVersion)
        assertEquals(7, AppDatabaseMigrations.MIGRATION_6_7.endVersion)
        assertEquals(7, AppDatabaseMigrations.MIGRATION_7_8.startVersion)
        assertEquals(8, AppDatabaseMigrations.MIGRATION_7_8.endVersion)
        assertEquals(8, AppDatabaseMigrations.MIGRATION_8_9.startVersion)
        assertEquals(9, AppDatabaseMigrations.MIGRATION_8_9.endVersion)
    }

    @Test
    fun `migration 5 to 6 adds search_text column`() {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqlSlot = slot<String>()
        every { db.execSQL(capture(sqlSlot)) } answers {
            statements += sqlSlot.captured
        }

        AppDatabaseMigrations.MIGRATION_5_6.migrate(db)

        assertEquals(
            listOf(
                "ALTER TABLE `EVENT` ADD COLUMN `search_text` TEXT",
            ),
            statements,
        )
    }

    @Test
    fun `migration 6 to 7 adds user scope and composite application identity`() {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqlSlot = slot<String>()
        every { db.execSQL(capture(sqlSlot)) } answers {
            statements += sqlSlot.captured
        }

        AppDatabaseMigrations.MIGRATION_6_7.migrate(db)

        assertEquals(
            listOf(
                "ALTER TABLE `EVENT` ADD COLUMN `user_id` INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE `REGISTERED_APPLICATION` ADD COLUMN `user_id` INTEGER NOT NULL DEFAULT 0",
                "DROP INDEX IF EXISTS `index_EVENT_pkg`",
                "DROP INDEX IF EXISTS `index_EVENT_date`",
                "DROP INDEX IF EXISTS `index_REGISTERED_APPLICATION_pkg`",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_REGISTERED_APPLICATION_user_id_pkg` ON `REGISTERED_APPLICATION` (`user_id`, `pkg`)",
                "CREATE INDEX IF NOT EXISTS `index_EVENT_user_id_pkg` ON `EVENT` (`user_id`, `pkg`)",
                "CREATE INDEX IF NOT EXISTS `index_EVENT_user_id_date` ON `EVENT` (`user_id`, `date`)",
            ),
            statements,
        )
    }

    @Test
    fun `migration 3 to 4 adds per-app island controls`() {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqlSlot = slot<String>()
        every { db.execSQL(capture(sqlSlot)) } answers {
            statements += sqlSlot.captured
        }

        AppDatabaseMigrations.MIGRATION_3_4.migrate(db)

        assertEquals(
            listOf(
                "ALTER TABLE REGISTERED_APPLICATION ADD COLUMN island_enabled INTEGER NOT NULL DEFAULT 1",
                "ALTER TABLE REGISTERED_APPLICATION ADD COLUMN island_focus_notification INTEGER NOT NULL DEFAULT 0",
            ),
            statements,
        )
    }

    @Test
    fun `migration 7 to 8 creates runtime event tombstones`() {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqlSlot = slot<String>()
        every { db.execSQL(capture(sqlSlot)) } answers {
            statements += sqlSlot.captured
        }

        AppDatabaseMigrations.MIGRATION_7_8.migrate(db)

        assertEquals(2, statements.size)
        assertTrue(statements[0].contains("CREATE TABLE IF NOT EXISTS `DELETED_EVENT`"))
        assertTrue(statements[0].contains("PRIMARY KEY(`id`, `user_id`)"))
        assertTrue(statements[1].contains("index_DELETED_EVENT_user_id_date"))
    }

    @Test
    fun `migration 8 to 9 adds tombstone deletion time`() {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqlSlot = slot<String>()
        every { db.execSQL(capture(sqlSlot)) } answers {
            statements += sqlSlot.captured
        }

        AppDatabaseMigrations.MIGRATION_8_9.migrate(db)

        assertEquals(
            listOf("ALTER TABLE `DELETED_EVENT` ADD COLUMN `deleted_at` INTEGER NOT NULL DEFAULT 0"),
            statements,
        )
    }

    @Test
    fun `migration 4 to 5 rebuilds registered applications with opt-in focus default`() {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqlSlot = slot<String>()
        every { db.execSQL(capture(sqlSlot)) } answers {
            statements += sqlSlot.captured
        }

        AppDatabaseMigrations.MIGRATION_4_5.migrate(db)

        assertEquals(
            listOf(
                """
                CREATE TABLE IF NOT EXISTS `REGISTERED_APPLICATION_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT,
                    `pkg` TEXT NOT NULL,
                    `type` INTEGER NOT NULL,
                    `notification_on_register` INTEGER NOT NULL,
                    `blocked` INTEGER NOT NULL DEFAULT 0,
                    `island_enabled` INTEGER NOT NULL DEFAULT 1,
                    `island_focus_notification` INTEGER NOT NULL DEFAULT 0,
                    `registered_type` INTEGER NOT NULL,
                    `app_name` TEXT NOT NULL
                )
                """.trimIndent(),
                """
                INSERT INTO `REGISTERED_APPLICATION_new` (
                    `id`,
                    `pkg`,
                    `type`,
                    `notification_on_register`,
                    `blocked`,
                    `island_enabled`,
                    `island_focus_notification`,
                    `registered_type`,
                    `app_name`
                )
                SELECT
                    `id`,
                    `pkg`,
                    `type`,
                    `notification_on_register`,
                    `blocked`,
                    `island_enabled`,
                    `island_focus_notification`,
                    `registered_type`,
                    `app_name`
                FROM `REGISTERED_APPLICATION`
                """.trimIndent(),
                "DROP TABLE `REGISTERED_APPLICATION`",
                "ALTER TABLE `REGISTERED_APPLICATION_new` RENAME TO `REGISTERED_APPLICATION`",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_REGISTERED_APPLICATION_pkg` ON `REGISTERED_APPLICATION` (`pkg`)",
            ),
            statements,
        )
    }
}
