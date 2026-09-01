package io.github.magisk317.mipush.runtime.store.db

import io.github.magisk317.mipush.runtime.store.kmp.RuntimeStoreMigrations
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RuntimeStoreMigrationContractTest {
    @Test
    fun `migration registry covers every production schema version`() {
        assertEquals(8, RuntimeStoreMigrations.ALL.size)
        RuntimeStoreMigrations.ALL.forEachIndexed { index, migration ->
            assertEquals(index + 1, migration.startVersion)
            assertEquals(index + 2, migration.endVersion)
        }
    }

    @Test
    fun `runtime store database declares the production v9 entities`() {
        val source = readSource(
            "../runtime/store/src/commonMain/kotlin/" +
                "io/github/magisk317/mipush/runtime/store/kmp/RuntimeStoreDatabase.kt",
        )

        assertTrue(source.contains("@Database("))
        assertTrue(source.contains("version = 9"))
        listOf(
            "RuntimeEventRow::class",
            "RuntimeDeletedEventRow::class",
            "RuntimeRegisteredApplicationRow::class",
        ).forEach { entity ->
            assertTrue(source.contains(entity), "Missing production entity: $entity")
        }
    }

    @Test
    fun `production database facade is wired to KMP db and preserves the db filename`() {
        val source = readSource(
            "../runtime/src/main/java/io/github/magisk317/mipush/runtime/store/DatabaseUtils.kt",
        )

        assertTrue(source.contains("RuntimeStoreDatabase"))
        assertTrue(source.contains("configureRuntimeStoreKmp(appContext, databaseName = \"db\")"))
        assertTrue(source.contains("RuntimeEventDao"))
        assertTrue(source.contains("RuntimeRegisteredApplicationDao"))
        assertFalse(source.contains("AppDatabase"))
        assertFalse(source.contains("Room.databaseBuilder"))
    }

    @Test
    fun `migration source preserves legacy schema operations`() {
        val source = readSource(
            "../runtime/store/src/commonMain/kotlin/" +
                "io/github/magisk317/mipush/runtime/store/kmp/RuntimeStoreMigrations.kt",
        )

        assertTrue(source.contains("override fun migrate(connection: SQLiteConnection)"))
        listOf(
            "CREATE INDEX IF NOT EXISTS `index_EVENT_pkg`",
            "ALTER TABLE REGISTERED_APPLICATION ADD COLUMN blocked",
            "ALTER TABLE REGISTERED_APPLICATION ADD COLUMN island_enabled",
            "CREATE TABLE IF NOT EXISTS `REGISTERED_APPLICATION_new`",
            "ALTER TABLE `EVENT` ADD COLUMN `search_text` TEXT",
            "ALTER TABLE `EVENT` ADD COLUMN `user_id` INTEGER NOT NULL DEFAULT 0",
            "CREATE TABLE IF NOT EXISTS `DELETED_EVENT`",
            "ALTER TABLE `DELETED_EVENT` ADD COLUMN `deleted_at` INTEGER NOT NULL DEFAULT 0",
        ).forEach { sqlFragment ->
            assertTrue(source.contains(sqlFragment), "Missing migration SQL: $sqlFragment")
        }
    }

    @Test
    fun `user scope migration is transactional and only publishes completion after updates`() {
        val dao = readSource(
            "../runtime/store/src/commonMain/kotlin/" +
                "io/github/magisk317/mipush/runtime/store/kmp/RuntimeStoreDaos.kt",
        )
        val database = readSource(
            "../runtime/src/main/java/io/github/magisk317/mipush/runtime/store/DatabaseUtils.kt",
        )

        assertTrue(dao.contains("@Transaction\n    suspend fun migrateLegacyUserScope"))
        assertTrue(dao.contains("UPDATE EVENT SET user_id = :userId WHERE user_id = 0"))
        assertTrue(dao.contains("UPDATE REGISTERED_APPLICATION SET user_id = :userId WHERE user_id = 0"))
        assertTrue(database.contains("runBlocking { db.eventDao().migrateLegacyUserScope(userId) }"))
        assertTrue(database.contains("USER_SCOPE_MIGRATION_KEY"))
        assertTrue(database.contains("putBoolean(USER_SCOPE_MIGRATION_KEY, true).commit()"))
    }

    @Test
    fun `database open emits low-cardinality store observation without payload data`() {
        val source = readSource(
            "../runtime/src/main/java/io/github/magisk317/mipush/runtime/store/DatabaseUtils.kt",
        )

        assertTrue(source.contains("reason = \"open\""))
        assertTrue(source.contains("reason = \"open_failed\""))
        assertTrue(source.contains("\"stage\" to \"runtime_store\""))
        assertTrue(source.contains("\"schema_version\""))
        assertTrue(source.contains("\"user_scope_migration\""))
        assertTrue(source.contains("\"error_class\""))
        assertFalse(source.contains("payload"))
        assertFalse(source.contains("regSec"))
    }

    @Test
    fun `all production event and application reads remain user scoped`() {
        val dao = readSource(
            "../runtime/store/src/commonMain/kotlin/" +
                "io/github/magisk317/mipush/runtime/store/kmp/RuntimeStoreDaos.kt",
        )

        listOf(
            "WHERE id = :id AND user_id = :userId",
            "WHERE pkg = :pkg AND type = :type AND user_id = :userId",
            "WHERE id < :lastId AND user_id = :userId",
            "WHERE user_id = :userId ORDER BY date DESC",
            "WHERE pkg = :pkg AND user_id = :userId",
            "WHERE pkg = :pkg AND user_id = :userId LIMIT 1",
            "WHERE user_id = :userId ORDER BY pkg",
            "SET blocked = :blocked WHERE id = :id AND user_id = :userId",
        ).forEach { queryFragment ->
            assertTrue(dao.contains(queryFragment), "Missing user-scoped DAO query: $queryFragment")
        }
    }

    @Test
    fun `event facade keeps undo restore and retention behavior on KMP DAOs`() {
        val dao = readSource(
            "../runtime/store/src/commonMain/kotlin/" +
                "io/github/magisk317/mipush/runtime/store/kmp/RuntimeStoreDaos.kt",
        )
        val eventDb = readSource(
            "src/main/java/io/github/magisk317/mipush/runtime/store/db/EventDb.kt",
        )
        val repository = readSource(
            "src/main/java/io/github/magisk317/mipush/runtime/data/EventRepository.kt",
        )

        assertTrue(dao.contains("deleteByIdWithUndoSnapshotForPackage"))
        assertTrue(dao.contains("restoreDeletedEventForPackage"))
        assertTrue(dao.contains("pruneDeletedEvents"))
        assertTrue(dao.contains("type NOT IN (20, 21)"))
        assertTrue(eventDb.contains("eventDao.deleteByIdWithUndoSnapshotForPackage"))
        assertTrue(eventDb.contains("eventDao.restoreDeletedEventForPackage"))
        assertTrue(repository.contains("EventDb.deleteByIdWithUndoSnapshotAsync(id, event.pkg, event.userId)"))
        assertTrue(repository.contains("EventDb.restoreDeletedEventAsync(preferredId, event.pkg, event.userId)"))
    }

    @Test
    fun `event database user resolution fails closed without changing legacy migration`() {
        val eventDb = readSource(
            "src/main/java/io/github/magisk317/mipush/runtime/store/db/EventDb.kt",
        )
        val database = readSource(
            "../runtime/src/main/java/io/github/magisk317/mipush/runtime/store/DatabaseUtils.kt",
        )
        val queryPolicy = readSource(
            "../runtime/store/src/commonMain/kotlin/" +
                "io/github/magisk317/mipush/runtime/store/kmp/RuntimeEventQueryPolicy.kt",
        )

        assertFalse(eventDb.contains("Utils.myUserId().coerceAtLeast(0)"))
        assertFalse(eventDb.contains("userId.coerceAtLeast(0)"))
        assertTrue(eventDb.contains("eventDao.getById(id, requireValidUserId(userId))"))
        assertTrue(eventDb.contains("Unable to resolve current Android user id"))
        assertTrue(eventDb.contains("Invalid Android user id: \$userId"))
        assertFalse(database.contains("Utils.myUserId().coerceAtLeast(0)"))
        assertTrue(database.contains("private fun currentUserId(): Int = runCatching"))
        assertTrue(database.contains("runBlocking { db.eventDao().migrateLegacyUserScope(userId) }"))
        assertFalse(queryPolicy.contains("userId.coerceAtLeast(0)"))
        assertTrue(queryPolicy.contains("RuntimeQueryArgument.IntValue(requireValidUserId(userId))"))
    }

    @Test
    fun `registered application facade rejects invalid requested users`() {
        val source = readSource(
            "../runtime/src/main/java/" +
                "io/github/magisk317/mipush/runtime/store/db/RegisteredApplicationDb.kt",
        )

        assertFalse(source.contains("requestedUserId?.takeIf { it >= 0 } ?: currentUserId()"))
        assertFalse(source.contains("Utils.myUserId().coerceAtLeast(0)"))
        assertTrue(source.contains("requestedUserId?.let(::requireValidUserId) ?: currentUserId()"))
        assertTrue(source.contains("private fun currentUserId(): Int = runCatching"))
        assertTrue(source.contains("private fun requireValidUserId(userId: Int): Int"))
        assertTrue(source.contains("Invalid Android user id: \$userId"))
    }

    private fun readSource(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source not found: $relativePath from ${File(".").absolutePath}")
    }
}
