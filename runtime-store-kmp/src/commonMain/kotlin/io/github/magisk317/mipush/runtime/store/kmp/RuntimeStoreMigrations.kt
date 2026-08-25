package io.github.magisk317.mipush.runtime.store.kmp

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema migrations for the production runtime database.
 *
 * The SQL is intentionally kept in the KMP storage module so the database definition,
 * driver, and migration contract share one owner. The schema is unchanged from xmsf's
 * former Android Room database (v9), which allows existing `db` files to be opened in
 * place without a copy or destructive fallback.
 */
object RuntimeStoreMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_EVENT_pkg` ON `EVENT` (`pkg`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_EVENT_date` ON `EVENT` (`date`)")
            connection.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_REGISTERED_APPLICATION_pkg` ON `REGISTERED_APPLICATION` (`pkg`)"
            )
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE REGISTERED_APPLICATION ADD COLUMN blocked INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE REGISTERED_APPLICATION ADD COLUMN island_enabled INTEGER NOT NULL DEFAULT 1")
            connection.execSQL(
                "ALTER TABLE REGISTERED_APPLICATION ADD COLUMN island_focus_notification INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
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
            connection.execSQL(
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
            connection.execSQL("DROP TABLE `REGISTERED_APPLICATION`")
            connection.execSQL("ALTER TABLE `REGISTERED_APPLICATION_new` RENAME TO `REGISTERED_APPLICATION`")
            connection.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_REGISTERED_APPLICATION_pkg` ON `REGISTERED_APPLICATION` (`pkg`)"
            )
        }
    }

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `EVENT` ADD COLUMN `search_text` TEXT")
        }
    }

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `EVENT` ADD COLUMN `user_id` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL(
                "ALTER TABLE `REGISTERED_APPLICATION` " +
                    "ADD COLUMN `user_id` INTEGER NOT NULL DEFAULT 0"
            )
            connection.execSQL("DROP INDEX IF EXISTS `index_EVENT_pkg`")
            connection.execSQL("DROP INDEX IF EXISTS `index_EVENT_date`")
            connection.execSQL("DROP INDEX IF EXISTS `index_REGISTERED_APPLICATION_pkg`")
            connection.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_REGISTERED_APPLICATION_user_id_pkg` " +
                    "ON `REGISTERED_APPLICATION` (`user_id`, `pkg`)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_EVENT_user_id_pkg` ON `EVENT` (`user_id`, `pkg`)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_EVENT_user_id_date` ON `EVENT` (`user_id`, `date`)"
            )
        }
    }

    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `DELETED_EVENT` (
                    `id` INTEGER NOT NULL,
                    `user_id` INTEGER NOT NULL,
                    `pkg` TEXT NOT NULL,
                    `type` INTEGER NOT NULL,
                    `date` INTEGER NOT NULL,
                    `result` INTEGER NOT NULL,
                    `dev_info` TEXT,
                    `search_text` TEXT,
                    `payload` BLOB,
                    `reg_sec` TEXT,
                    PRIMARY KEY(`id`, `user_id`)
                )
                """.trimIndent()
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_DELETED_EVENT_user_id_date` " +
                    "ON `DELETED_EVENT` (`user_id`, `date`)"
            )
        }
    }

    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE `DELETED_EVENT` ADD COLUMN `deleted_at` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
    )
}
