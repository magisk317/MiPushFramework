package io.github.magisk317.mipush.runtime.core.store.db

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class AppDatabaseMigrationContractTest {

    @Test
    fun `migration 1 to 2 creates required indexes`() {
        val recorder = SqlRecorder()

        AppDatabaseMigrations.MIGRATION_1_2.migrate(recorder.database)

        assertEquals(
            listOf(
                "CREATE INDEX IF NOT EXISTS `index_EVENT_pkg` ON `EVENT` (`pkg`)",
                "CREATE INDEX IF NOT EXISTS `index_EVENT_date` ON `EVENT` (`date`)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_REGISTERED_APPLICATION_pkg` ON `REGISTERED_APPLICATION` (`pkg`)",
            ),
            recorder.statements,
        )
    }

    @Test
    fun `migration registry currently exposes only baseline migration`() {
        assertEquals(1, AppDatabaseMigrations.ALL.size)
        assertEquals(1, AppDatabaseMigrations.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabaseMigrations.MIGRATION_1_2.endVersion)
    }

    private class SqlRecorder : InvocationHandler {
        val statements = mutableListOf<String>()

        val database: SupportSQLiteDatabase =
            Proxy.newProxyInstance(
                SupportSQLiteDatabase::class.java.classLoader,
                arrayOf(SupportSQLiteDatabase::class.java),
                this,
            ) as SupportSQLiteDatabase

        override fun invoke(proxy: Any?, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? {
            if (method.name == "execSQL" && !args.isNullOrEmpty()) {
                statements += args[0] as String
                return null
            }
            return defaultValue(method.returnType)
        }

        private fun defaultValue(returnType: Class<*>): Any? = when (returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            else -> null
        }
    }
}
