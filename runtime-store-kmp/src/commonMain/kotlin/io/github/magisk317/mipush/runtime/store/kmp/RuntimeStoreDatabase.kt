package io.github.magisk317.mipush.runtime.store.kmp

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * Room KMP shadow database for schema and generated-code validation only.
 *
 * The production owner remains xmsf's AppDatabase. This database deliberately uses a separate
 * file name and is not wired into xmsf until the schema and adapter contract are proven.
 */
@Database(
    entities = [
        RuntimeEventRow::class,
        RuntimeDeletedEventRow::class,
        RuntimeRegisteredApplicationRow::class,
    ],
    version = 9,
    exportSchema = true,
)
@ConstructedBy(RuntimeStoreDatabaseConstructor::class)
abstract class RuntimeStoreDatabase : RoomDatabase() {
    abstract fun eventDao(): RuntimeEventDao

    abstract fun deletedEventDao(): RuntimeDeletedEventDao

    abstract fun registeredApplicationDao(): RuntimeRegisteredApplicationDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object RuntimeStoreDatabaseConstructor : RoomDatabaseConstructor<RuntimeStoreDatabase>
