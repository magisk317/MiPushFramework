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
        assertEquals(3, AppDatabaseMigrations.ALL.size)
        assertEquals(1, AppDatabaseMigrations.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabaseMigrations.MIGRATION_1_2.endVersion)
        
        assertEquals(2, AppDatabaseMigrations.MIGRATION_2_3.startVersion)
        assertEquals(3, AppDatabaseMigrations.MIGRATION_2_3.endVersion)

        assertEquals(3, AppDatabaseMigrations.MIGRATION_3_4.startVersion)
        assertEquals(4, AppDatabaseMigrations.MIGRATION_3_4.endVersion)
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
                "ALTER TABLE REGISTERED_APPLICATION ADD COLUMN island_focus_notification INTEGER NOT NULL DEFAULT 1",
            ),
            statements,
        )
    }
}
