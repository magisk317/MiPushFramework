package io.github.magisk317.mipush.runtime.store.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.magisk317.mipush.runtime.store.db.converters.DateConverter
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication

@Database(entities = [Event::class, RegisteredApplication::class], version = 2, exportSchema = true)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun registeredApplicationDao(): RegisteredApplicationDao
}
