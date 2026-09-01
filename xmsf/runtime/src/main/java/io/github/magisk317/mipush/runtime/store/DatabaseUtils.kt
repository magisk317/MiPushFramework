package io.github.magisk317.mipush.runtime.store

import android.annotation.SuppressLint
import android.content.Context
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventDao
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationDao
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeStoreDatabase
import io.github.magisk317.mipush.runtime.store.kmp.configureRuntimeStoreKmp
import io.github.magisk317.xposed.logging.MagiskOtel
import kotlinx.coroutines.runBlocking

/**
 * Created by Trumeet on 2017/12/23.
 */
object DatabaseUtils {
    private const val DATABASE_SCHEMA_VERSION = 9
    private const val USER_SCOPE_MIGRATION_PREFS = "database_identity_migration"
    private const val USER_SCOPE_MIGRATION_KEY = "v7_done"

    @Volatile
    private var database: RuntimeStoreDatabase? = null

    val eventDao: RuntimeEventDao
        get() = requireNotNull(database) { "DatabaseUtils.init(context) must be called first" }.eventDao()

    val registeredApplicationDao: RuntimeRegisteredApplicationDao
        get() = requireNotNull(database) { "DatabaseUtils.init(context) must be called first" }.registeredApplicationDao()

    @JvmStatic
    fun init(context: Context) {
        getDatabase(context)
    }

    @Suppress("TooGenericExceptionCaught")
    fun getDatabase(context: Context): RuntimeStoreDatabase {
        database?.let { return it }
        synchronized(this) {
            database?.let { return it }
            try {
                val appContext = context.applicationContext
                val db = configureRuntimeStoreKmp(appContext, databaseName = "db")
                // Migrate before publishing the singleton so no DAO can observe the default user.
                migrateLegacyUserScope(appContext, db)
                database = db
                emitStoreEvent(
                    reason = "open",
                    userId = currentUserId(),
                    userScopeMigrationComplete = appContext
                        .getSharedPreferences(USER_SCOPE_MIGRATION_PREFS, Context.MODE_PRIVATE)
                        .getBoolean(USER_SCOPE_MIGRATION_KEY, false),
                    statusOk = true,
                )
                return db
            } catch (error: RuntimeException) {
                emitStoreEvent(
                    reason = "open_failed",
                    userId = runCatching { currentUserId() }.getOrDefault(-1),
                    userScopeMigrationComplete = false,
                    statusOk = false,
                    error = error,
                )
                throw error
            }
        }
    }

    /**
     * v7 stores old rows with the migration default user 0. The database is scoped by Android
     * user, so assign those legacy rows to the actual owner once before enforcing user filters.
     */
    @SuppressLint("UseKtx")
    private fun migrateLegacyUserScope(context: Context, db: RuntimeStoreDatabase) {
        val preferences = context.getSharedPreferences(USER_SCOPE_MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (preferences.getBoolean(USER_SCOPE_MIGRATION_KEY, false)) return
        synchronized(this) {
            if (preferences.getBoolean(USER_SCOPE_MIGRATION_KEY, false)) return
            val userId = currentUserId()
            if (userId != 0) {
                runBlocking { db.eventDao().migrateLegacyUserScope(userId) }
            }
            check(preferences.edit().putBoolean(USER_SCOPE_MIGRATION_KEY, true).commit()) {
                "Failed to persist database identity migration state"
            }
        }
    }

    private fun emitStoreEvent(
        reason: String,
        userId: Int,
        userScopeMigrationComplete: Boolean,
        statusOk: Boolean,
        error: Exception? = null,
    ) {
        runCatching {
            val attributes = mutableMapOf(
                "result" to if (statusOk) "ok" else "error",
                "process" to "xmsf",
                "stage" to "runtime_store",
                "reason" to reason,
                "schema_version" to DATABASE_SCHEMA_VERSION.toString(),
                "user_id" to userId.toString(),
                "user_scope_migration" to if (userScopeMigrationComplete) "complete" else "pending",
            )
            error?.let { attributes["error_class"] = it.javaClass.simpleName }
            MagiskOtel.event(
                name = "push.control",
                attributes = attributes,
                statusOk = statusOk,
            )
        }
    }

    private fun currentUserId(): Int = runCatching { Utils.myUserId() }
        .getOrNull()
        ?.takeIf { it >= 0 }
        ?: error("Unable to resolve current Android user id")
}
