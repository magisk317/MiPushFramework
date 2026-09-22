package io.github.magisk317.mipush.runtime.store.kmp

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/** Room KMP database for the runtime store shared by the xmsf production path. */
@Database(
    entities = [
        RuntimeEventRow::class,
        RuntimeDeletedEventRow::class,
        RuntimeRegisteredApplicationRow::class,
    ],
    version = 10,
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
