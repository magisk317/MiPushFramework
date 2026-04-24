package io.github.magisk317.mipush.runtime.core.store

import android.content.Context
import androidx.room.Room
import io.github.magisk317.mipush.runtime.core.store.db.AppDatabase
import io.github.magisk317.mipush.runtime.core.store.db.AppDatabaseMigrations
import io.github.magisk317.mipush.runtime.core.store.db.EventDao
import io.github.magisk317.mipush.runtime.core.store.db.RegisteredApplicationDao

/**
 * Created by Trumeet on 2017/12/23.
 */
object DatabaseUtils {
    @Volatile
    private var database: AppDatabase? = null

    val eventDao: EventDao
        get() = requireNotNull(database) { "DatabaseUtils.init(context) must be called first" }.eventDao()

    val registeredApplicationDao: RegisteredApplicationDao
        get() = requireNotNull(database) { "DatabaseUtils.init(context) must be called first" }.registeredApplicationDao()

    @JvmStatic
    fun init(context: Context) {
        if (database != null) {
            return
        }
        synchronized(this) {
            if (database == null) {
                database = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "db"
                )
                    .addMigrations(*AppDatabaseMigrations.ALL)
                    .build()
            }
        }
    }
}
