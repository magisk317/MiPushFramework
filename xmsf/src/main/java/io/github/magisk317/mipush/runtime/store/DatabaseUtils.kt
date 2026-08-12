package io.github.magisk317.mipush.runtime.store

import android.content.Context
import androidx.room.Room
import io.github.magisk317.mipush.runtime.store.db.AppDatabase
import io.github.magisk317.mipush.runtime.store.db.AppDatabaseMigrations
import io.github.magisk317.mipush.runtime.store.db.EventDao
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDao
import io.github.magisk317.mipush.common.utils.Utils

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
        getDatabase(context)
    }

    fun getDatabase(context: Context): AppDatabase {
        database?.let { return it }
        synchronized(this) {
            database?.let { return it }
            val db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "db"
            )
                .addMigrations(*AppDatabaseMigrations.ALL)
                .build()
            // Migrate before publishing the singleton so no DAO can observe the default user.
            migrateLegacyUserScope(context.applicationContext, db)
            database = db
            return db
        }
    }

    /**
     * v7 stores old rows with the migration default user 0. The database is scoped by Android
     * user, so assign those legacy rows to the actual owner once before enforcing user filters.
     */
    private fun migrateLegacyUserScope(context: Context, db: AppDatabase) {
        val preferences = context.getSharedPreferences("database_identity_migration", Context.MODE_PRIVATE)
        if (preferences.getBoolean("v7_done", false)) return
        synchronized(this) {
            if (preferences.getBoolean("v7_done", false)) return
            val userId = Utils.myUserId().coerceAtLeast(0)
            if (userId != 0) {
                val writableDatabase = db.openHelper.writableDatabase
                writableDatabase.beginTransaction()
                try {
                    writableDatabase.execSQL(
                        "UPDATE EVENT SET user_id = ? WHERE user_id = 0",
                        arrayOf(userId),
                    )
                    writableDatabase.execSQL(
                        "UPDATE REGISTERED_APPLICATION SET user_id = ? WHERE user_id = 0",
                        arrayOf(userId),
                    )
                    writableDatabase.setTransactionSuccessful()
                } finally {
                    writableDatabase.endTransaction()
                }
            }
            check(preferences.edit().putBoolean("v7_done", true).commit()) {
                "Failed to persist database identity migration state"
            }
        }
    }
}
