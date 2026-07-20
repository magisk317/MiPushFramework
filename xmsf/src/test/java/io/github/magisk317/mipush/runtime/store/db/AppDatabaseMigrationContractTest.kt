package io.github.magisk317.mipush.runtime.store.db

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
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
        assertEquals(5, AppDatabaseMigrations.ALL.size)
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
