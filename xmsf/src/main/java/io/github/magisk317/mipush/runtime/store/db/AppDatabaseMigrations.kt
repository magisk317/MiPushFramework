package io.github.magisk317.mipush.runtime.store.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {
    @JvmField
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Baseline migration: explicitly keep critical indexes for old installs.
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_EVENT_pkg` ON `EVENT` (`pkg`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_EVENT_date` ON `EVENT` (`date`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_REGISTERED_APPLICATION_pkg` ON `REGISTERED_APPLICATION` (`pkg`)"
            )
        }
    }

    @JvmField
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE REGISTERED_APPLICATION ADD COLUMN blocked INTEGER NOT NULL DEFAULT 0")
        }
    }

    @JvmField
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE REGISTERED_APPLICATION ADD COLUMN island_enabled INTEGER NOT NULL DEFAULT 1")
            db.execSQL(
                "ALTER TABLE REGISTERED_APPLICATION ADD COLUMN island_focus_notification INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    @JvmField
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
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
                """.trimIndent()
            )
            db.execSQL(
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
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `REGISTERED_APPLICATION`")
            db.execSQL("ALTER TABLE `REGISTERED_APPLICATION_new` RENAME TO `REGISTERED_APPLICATION`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_REGISTERED_APPLICATION_pkg` ON `REGISTERED_APPLICATION` (`pkg`)"
            )
        }
    }

    @JvmField
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
