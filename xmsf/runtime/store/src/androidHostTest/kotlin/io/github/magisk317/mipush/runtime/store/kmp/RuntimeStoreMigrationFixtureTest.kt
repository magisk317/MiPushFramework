package io.github.magisk317.mipush.runtime.store.kmp

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeStoreMigrationFixtureTest {
    @Test
    fun `every legacy schema version migrates to v10 and preserves runtime data`() {
        for (version in 1..10) {
            val connection = createFixture(version)
            try {
                migrateToLatest(connection, version)

                assertEquals(10L, scalarLong(connection, "PRAGMA user_version"), "user_version for v$version")
                assertEquals(
                    listOf("id", "pkg", "type", "date", "result", "dev_info", "payload", "reg_sec", "search_text", "user_id"),
                    columnNames(connection, "EVENT"),
                    "EVENT columns for v$version",
                )
                assertEquals(
                    listOf("id", "pkg", "type", "notification_on_register", "blocked", "island_enabled", "island_focus_notification", "registered_type", "app_name", "user_id", "click_fallback_enabled"),
                    columnNames(connection, "REGISTERED_APPLICATION"),
                    "REGISTERED_APPLICATION columns for v$version",
                )
                assertEquals(
                    listOf("id", "user_id", "pkg", "type", "date", "result", "dev_info", "search_text", "payload", "reg_sec", "deleted_at"),
                    columnNames(connection, "DELETED_EVENT"),
                    "DELETED_EVENT columns for v$version",
                )

                val expectedUserId = if (version >= 7) 999L else 0L
                assertEquals(expectedUserId, scalarLong(connection, "SELECT user_id FROM EVENT WHERE id = 1"))
                assertEquals(expectedUserId, scalarLong(connection, "SELECT user_id FROM REGISTERED_APPLICATION WHERE id = 1"))
                assertEquals(
                    if (version >= 10) 1L else 0L,
                    scalarLong(connection, "SELECT click_fallback_enabled FROM REGISTERED_APPLICATION WHERE id = 1"),
                    "click_fallback_enabled for v$version",
                )
                assertArrayEquals(
                    byteArrayOf(1, 2, 3),
                    scalarBlob(connection, "SELECT payload FROM EVENT WHERE id = 1"),
                )
                if (version >= 8) {
                    assertEquals(
                        if (version >= 9) 1234L else 0L,
                        scalarLong(connection, "SELECT deleted_at FROM DELETED_EVENT WHERE id = 2"),
                    )
                } else {
                    assertEquals(0L, scalarLong(connection, "SELECT COUNT(*) FROM DELETED_EVENT"))
                }

                assertTrue(indexNames(connection, "EVENT").contains("index_EVENT_user_id_pkg"))
                assertTrue(indexNames(connection, "EVENT").contains("index_EVENT_user_id_date"))
                assertTrue(indexNames(connection, "REGISTERED_APPLICATION").contains("index_REGISTERED_APPLICATION_user_id_pkg"))
                assertTrue(indexNames(connection, "DELETED_EVENT").contains("index_DELETED_EVENT_user_id_date"))
            } finally {
                connection.close()
            }
        }
    }

    @Test
    fun `migration registry is contiguous and ends at production schema`() {
        assertEquals(9, RuntimeStoreMigrations.ALL.size)
        assertEquals(1, RuntimeStoreMigrations.ALL.first().startVersion)
        assertEquals(10, RuntimeStoreMigrations.ALL.last().endVersion)
        RuntimeStoreMigrations.ALL.toList().zipWithNext().forEach { (current, next) ->
            assertEquals(current.endVersion, next.startVersion)
        }
    }

    private fun migrateToLatest(connection: SQLiteConnection, version: Int) {
        RuntimeStoreMigrations.ALL
            .filter { it.startVersion >= version }
            .forEach { migration ->
                migration.migrate(connection)
                connection.execSQL("PRAGMA user_version = ${migration.endVersion}")
            }
    }

    private fun createFixture(version: Int): SQLiteConnection {
        val connection = BundledSQLiteDriver().open(":memory:")
        connection.execSQL(
            """
            CREATE TABLE EVENT (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pkg TEXT NOT NULL,
                type INTEGER NOT NULL,
                date INTEGER NOT NULL,
                result INTEGER NOT NULL,
                dev_info TEXT,
                payload BLOB,
                reg_sec TEXT${if (version >= 6) ", search_text TEXT" else ""}${if (version >= 7) ", user_id INTEGER NOT NULL DEFAULT 0" else ""}
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE REGISTERED_APPLICATION (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pkg TEXT NOT NULL,
                type INTEGER NOT NULL,
                notification_on_register INTEGER NOT NULL,
                ${if (version >= 3) "blocked INTEGER NOT NULL DEFAULT 0," else ""}
                ${if (version >= 4) "island_enabled INTEGER NOT NULL DEFAULT 1, island_focus_notification INTEGER NOT NULL DEFAULT 0," else ""}
                registered_type INTEGER NOT NULL,
                app_name TEXT NOT NULL${if (version >= 7) ", user_id INTEGER NOT NULL DEFAULT 0" else ""}${if (version >= 10) ", click_fallback_enabled INTEGER NOT NULL DEFAULT 0" else ""}
            )
            """.trimIndent(),
        )
        if (version >= 2) {
            connection.execSQL("CREATE INDEX index_EVENT_pkg ON EVENT (pkg)")
            connection.execSQL("CREATE INDEX index_EVENT_date ON EVENT (date)")
            connection.execSQL("CREATE UNIQUE INDEX index_REGISTERED_APPLICATION_pkg ON REGISTERED_APPLICATION (pkg)")
        }
        if (version >= 7) {
            connection.execSQL("CREATE UNIQUE INDEX index_REGISTERED_APPLICATION_user_id_pkg ON REGISTERED_APPLICATION (user_id, pkg)")
            connection.execSQL("CREATE INDEX index_EVENT_user_id_pkg ON EVENT (user_id, pkg)")
            connection.execSQL("CREATE INDEX index_EVENT_user_id_date ON EVENT (user_id, date)")
        }
        if (version >= 8) {
            connection.execSQL(
                """
                CREATE TABLE DELETED_EVENT (
                    id INTEGER NOT NULL,
                    user_id INTEGER NOT NULL,
                    pkg TEXT NOT NULL,
                    type INTEGER NOT NULL,
                    date INTEGER NOT NULL,
                    result INTEGER NOT NULL,
                    dev_info TEXT,
                    search_text TEXT,
                    payload BLOB,
                    reg_sec TEXT${if (version >= 9) ", deleted_at INTEGER NOT NULL DEFAULT 0" else ""},
                    PRIMARY KEY(id, user_id)
                )
                """.trimIndent(),
            )
            connection.execSQL("CREATE INDEX index_DELETED_EVENT_user_id_date ON DELETED_EVENT (user_id, date)")
        }

        val eventUserId = if (version >= 7) 999 else 0
        val eventColumns = if (version >= 7) {
            "id, pkg, type, date, result, dev_info, payload, reg_sec, search_text, user_id"
        } else if (version >= 6) {
            "id, pkg, type, date, result, dev_info, payload, reg_sec, search_text"
        } else {
            "id, pkg, type, date, result, dev_info, payload, reg_sec"
        }
        val eventValues = if (version >= 7) {
            "1, 'com.example.app', 10, 1000, 0, 'info', X'010203', 'secret', 'search', $eventUserId"
        } else if (version >= 6) {
            "1, 'com.example.app', 10, 1000, 0, 'info', X'010203', 'secret', 'search'"
        } else {
            "1, 'com.example.app', 10, 1000, 0, 'info', X'010203', 'secret'"
        }
        connection.execSQL("INSERT INTO EVENT ($eventColumns) VALUES ($eventValues)")

        val applicationColumns = if (version >= 10) {
            "id, pkg, type, notification_on_register, blocked, island_enabled, island_focus_notification, registered_type, app_name, user_id, click_fallback_enabled"
        } else if (version >= 7) {
            "id, pkg, type, notification_on_register, blocked, island_enabled, island_focus_notification, registered_type, app_name, user_id"
        } else if (version >= 4) {
            "id, pkg, type, notification_on_register, blocked, island_enabled, island_focus_notification, registered_type, app_name"
        } else if (version >= 3) {
            "id, pkg, type, notification_on_register, blocked, registered_type, app_name"
        } else {
            "id, pkg, type, notification_on_register, registered_type, app_name"
        }
        val applicationValues = if (version >= 10) {
            "1, 'com.example.app', 0, 1, 0, 1, 0, 0, 'Example', $eventUserId, 1"
        } else if (version >= 7) {
            "1, 'com.example.app', 0, 1, 0, 1, 0, 0, 'Example', $eventUserId"
        } else if (version >= 4) {
            "1, 'com.example.app', 0, 1, 0, 1, 0, 0, 'Example'"
        } else if (version >= 3) {
            "1, 'com.example.app', 0, 1, 0, 0, 'Example'"
        } else {
            "1, 'com.example.app', 0, 1, 0, 'Example'"
        }
        connection.execSQL("INSERT INTO REGISTERED_APPLICATION ($applicationColumns) VALUES ($applicationValues)")

        if (version >= 8) {
            val deletedColumns = if (version >= 9) {
                "id, user_id, pkg, type, date, result, dev_info, search_text, payload, reg_sec, deleted_at"
            } else {
                "id, user_id, pkg, type, date, result, dev_info, search_text, payload, reg_sec"
            }
            val deletedValues = if (version >= 9) {
                "2, $eventUserId, 'com.example.app', 10, 1000, 0, 'info', 'search', X'010203', 'secret', 1234"
            } else {
                "2, $eventUserId, 'com.example.app', 10, 1000, 0, 'info', 'search', X'010203', 'secret'"
            }
            connection.execSQL("INSERT INTO DELETED_EVENT ($deletedColumns) VALUES ($deletedValues)")
        }
        connection.execSQL("PRAGMA user_version = $version")
        return connection
    }

    private fun columnNames(connection: SQLiteConnection, table: String): List<String> =
        queryRows(connection, "PRAGMA table_info('$table')") { statement -> statement.getText(1) }

    private fun indexNames(connection: SQLiteConnection, table: String): List<String> =
        queryRows(connection, "PRAGMA index_list('$table')") { statement -> statement.getText(1) }

    private fun scalarLong(connection: SQLiteConnection, sql: String): Long =
        connection.prepare(sql).use { statement ->
            check(statement.step()) { "Expected one row for $sql" }
            statement.getLong(0)
        }

    private fun scalarBlob(connection: SQLiteConnection, sql: String): ByteArray =
        connection.prepare(sql).use { statement ->
            check(statement.step()) { "Expected one row for $sql" }
            statement.getBlob(0)
        }

    private fun <T> queryRows(
        connection: SQLiteConnection,
        sql: String,
        mapper: (androidx.sqlite.SQLiteStatement) -> T,
    ): List<T> = buildList {
        connection.prepare(sql).use { statement ->
            while (statement.step()) add(mapper(statement))
        }
    }
}
