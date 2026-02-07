package top.trumeet.mipush.provider.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import top.trumeet.mipush.provider.db.converters.DateConverter
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipush.provider.entities.RegisteredApplication

@Database(entities = [Event::class, RegisteredApplication::class], version = 1, exportSchema = false)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun registeredApplicationDao(): RegisteredApplicationDao
}
