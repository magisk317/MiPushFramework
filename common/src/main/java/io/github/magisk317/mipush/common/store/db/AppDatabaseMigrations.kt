package io.github.magisk317.mipush.common.store.db

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
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
