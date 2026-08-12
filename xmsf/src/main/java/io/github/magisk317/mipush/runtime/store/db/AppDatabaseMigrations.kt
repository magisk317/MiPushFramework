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
    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // v6: EVENT 表新增 search_text 列(UI 对齐的可搜索快照),老行留 NULL,
            // 查询侧以 `search_text IS NULL AND dev_info LIKE ?` 回退兼容,保留历史事件。
            db.execSQL("ALTER TABLE `EVENT` ADD COLUMN `search_text` TEXT")
        }
    }

    @JvmField
    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `EVENT` ADD COLUMN `user_id` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "ALTER TABLE `REGISTERED_APPLICATION` " +
                    "ADD COLUMN `user_id` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL("DROP INDEX IF EXISTS `index_EVENT_pkg`")
            db.execSQL("DROP INDEX IF EXISTS `index_EVENT_date`")
            db.execSQL("DROP INDEX IF EXISTS `index_REGISTERED_APPLICATION_pkg`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_REGISTERED_APPLICATION_user_id_pkg` " +
                    "ON `REGISTERED_APPLICATION` (`user_id`, `pkg`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_EVENT_user_id_pkg` ON `EVENT` (`user_id`, `pkg`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_EVENT_user_id_date` ON `EVENT` (`user_id`, `date`)"
            )
        }
    }

    @JvmField
    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
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
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_DELETED_EVENT_user_id_date` " +
                    "ON `DELETED_EVENT` (`user_id`, `date`)"
            )
        }
    }

    @JvmField
    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `DELETED_EVENT` ADD COLUMN `deleted_at` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    @JvmField
    val ALL: Array<Migration> =
        arrayOf(
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
