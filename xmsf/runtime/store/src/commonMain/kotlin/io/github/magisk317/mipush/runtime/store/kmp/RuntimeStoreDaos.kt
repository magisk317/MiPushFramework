package io.github.magisk317.mipush.runtime.store.kmp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.RoomRawQuery

// ───────────────────────────────────────────────────────────────────────────────
// Data classes used by aggregate queries
// ───────────────────────────────────────────────────────────────────────────────

/** Package-level last receive timestamp (type = 10 / Command). */
data class PackageLastTime(
    val pkg: String,
    val date: Long,
)

/** Per-calendar-day count of cleanable events (excludes registration state 20/21). */
data class DayCount(
    val day: String,
    val count: Int,
)

// ───────────────────────────────────────────────────────────────────────────────
// Conversion helpers between RuntimeEventRow <-> RuntimeDeletedEventRow
// ───────────────────────────────────────────────────────────────────────────────

fun RuntimeEventRow.toDeletedEvent(deletedAt: Long): RuntimeDeletedEventRow = RuntimeDeletedEventRow(
    id = requireNotNull(id),
    userId = userId,
    pkg = pkg,
    type = type,
    date = date,
    result = result,
    info = info,
    searchText = searchText,
    payload = payload?.copyOf(),
    regSec = regSec,
    deletedAt = deletedAt,
)

fun RuntimeDeletedEventRow.toEvent(): RuntimeEventRow = RuntimeEventRow(
    id = id,
    pkg = pkg,
    userId = userId,
    type = type,
    date = date,
    result = result,
    info = info,
    searchText = searchText,
    payload = payload?.copyOf(),
    regSec = regSec,
)

// ───────────────────────────────────────────────────────────────────────────────
// RuntimeEventDao — full port of xmsf EventDao
// ───────────────────────────────────────────────────────────────────────────────

@Dao
interface RuntimeEventDao {

    // -- basic CRUD -----------------------------------------------------------

    @Insert
    suspend fun insert(event: RuntimeEventRow): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(event: RuntimeEventRow): Long

    // -- single-row lookups ---------------------------------------------------

    @Query("SELECT * FROM EVENT WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getById(id: Long, userId: Int): RuntimeEventRow?

    @Query("SELECT * FROM EVENT WHERE pkg = :pkg AND type = :type AND user_id = :userId ORDER BY date DESC LIMIT 1")
    suspend fun getLastEventByType(pkg: String, type: Int, userId: Int): RuntimeEventRow?

    // -- paginated queries ----------------------------------------------------

    @Query("SELECT * FROM EVENT WHERE id < :lastId AND user_id = :userId ORDER BY id DESC LIMIT :limit")
    suspend fun queryById(lastId: Long, userId: Int, limit: Int): List<RuntimeEventRow>

    @Query("SELECT * FROM EVENT WHERE id < :lastId AND pkg = :pkg AND user_id = :userId ORDER BY id DESC LIMIT :limit")
    suspend fun queryByIdAndPkg(lastId: Long, pkg: String, userId: Int, limit: Int): List<RuntimeEventRow>

    @Query("SELECT * FROM EVENT WHERE user_id = :userId ORDER BY date DESC LIMIT :limit OFFSET :skip")
    suspend fun query(skip: Int, userId: Int, limit: Int): List<RuntimeEventRow>

    @Query("SELECT * FROM EVENT WHERE pkg = :pkg AND user_id = :userId ORDER BY date DESC LIMIT :limit OFFSET :skip")
    suspend fun queryByPkg(pkg: String, userId: Int, skip: Int, limit: Int): List<RuntimeEventRow>

    @RawQuery
    suspend fun queryRaw(query: RoomRawQuery): List<RuntimeEventRow>

    // -- existing simple query (preserved from original KMP DAO) ---------------

    @Query("SELECT * FROM EVENT WHERE user_id = :userId ORDER BY date DESC")
    suspend fun findByUser(userId: Int): List<RuntimeEventRow>

    /**
     * v7 added user_id with a default of zero. Existing databases are scoped by Android user,
     * so non-zero users need their legacy rows reassigned before normal scoped reads begin.
     */
    @Query("UPDATE EVENT SET user_id = :userId WHERE user_id = 0")
    suspend fun migrateLegacyEventUserScope(userId: Int): Int

    @Query("UPDATE REGISTERED_APPLICATION SET user_id = :userId WHERE user_id = 0")
    suspend fun migrateLegacyApplicationUserScope(userId: Int): Int

    @Transaction
    suspend fun migrateLegacyUserScope(userId: Int) {
        if (userId == 0) return
        migrateLegacyEventUserScope(userId)
        migrateLegacyApplicationUserScope(userId)
    }

    // -- deletion -------------------------------------------------------------

    // Delete expired transient events; preserve registration state (20/21)
    // which are the data source for queryRegisteredStatus.
    @Query("DELETE FROM EVENT WHERE user_id = :userId AND type NOT IN (20, 21) AND date < :date")
    suspend fun deleteHistory(date: Long, userId: Int): Int

    @Query("DELETE FROM EVENT WHERE id = :id AND user_id = :userId")
    suspend fun deleteById(id: Long, userId: Int): Int

    // Delete transient events in [start, end) range, preserving registration state (20/21).
    @Query("DELETE FROM EVENT WHERE user_id = :userId AND type NOT IN (20, 21) AND date >= :start AND date < :end")
    suspend fun deleteHistoryInRange(start: Long, end: Long, userId: Int): Int

    // -- aggregate queries ----------------------------------------------------

    @Query(
        "SELECT strftime('%Y-%m-%d', date / 1000, 'unixepoch', 'localtime') AS day, " +
            "COUNT(*) AS count FROM EVENT WHERE user_id = :userId AND type NOT IN (20, 21) GROUP BY day"
    )
    suspend fun countEventsByDay(userId: Int): List<DayCount>

    @Query(
        "SELECT e.* FROM EVENT e " +
            "INNER JOIN (" +
            "SELECT pkg, MAX(date) AS max_date FROM EVENT WHERE user_id = :userId AND type IN (21, 20) GROUP BY pkg" +
            ") latest ON e.pkg = latest.pkg AND e.date = latest.max_date AND e.user_id = :userId " +
            "WHERE e.user_id = :userId AND e.type IN (21, 20)"
    )
    suspend fun queryRegisteredStatus(userId: Int): List<RuntimeEventRow>

    @Query("SELECT pkg, MAX(date) as date FROM EVENT WHERE user_id = :userId AND type = 10 GROUP BY pkg")
    suspend fun getAllLastReceiveTimes(userId: Int): List<PackageLastTime>

    // -- deleted event management (undo snapshot) -----------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedEvent(event: RuntimeDeletedEventRow)

    @Query("SELECT * FROM DELETED_EVENT WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getDeletedEvent(id: Long, userId: Int): RuntimeDeletedEventRow?

    @Query("DELETE FROM DELETED_EVENT WHERE id = :id AND user_id = :userId")
    suspend fun deleteDeletedEvent(id: Long, userId: Int)

    @Query("DELETE FROM DELETED_EVENT WHERE user_id = :userId AND deleted_at < :cutoff")
    suspend fun deleteDeletedEventsBefore(cutoff: Long, userId: Int)

    @Query("SELECT COUNT(*) FROM DELETED_EVENT WHERE user_id = :userId")
    suspend fun countDeletedEvents(userId: Int): Int

    @Query(
        "DELETE FROM DELETED_EVENT WHERE user_id = :userId AND id IN (" +
            "SELECT id FROM DELETED_EVENT WHERE user_id = :userId " +
            "ORDER BY deleted_at ASC LIMIT :limit)"
    )
    suspend fun deleteOldestDeletedEvents(limit: Int, userId: Int)

    // -- @Transaction composite methods ---------------------------------------

    @Transaction
    suspend fun pruneDeletedEvents(cutoff: Long, maxCount: Int, userId: Int) {
        deleteDeletedEventsBefore(cutoff, userId)
        val excess = countDeletedEvents(userId) - maxCount
        if (excess > 0) deleteOldestDeletedEvents(excess, userId)
    }

    @Transaction
    suspend fun deleteByIdWithUndoSnapshotForPackage(
        id: Long,
        userId: Int,
        packageName: String,
    ): Boolean {
        val event = getById(id, userId) ?: return false
        if (event.pkg != packageName) return false
        insertDeletedEvent(event.toDeletedEvent(System.currentTimeMillis()))
        if (deleteById(id, userId) > 0) return true
        deleteDeletedEvent(id, userId)
        return false
    }

    @Transaction
    suspend fun restoreDeletedEventForPackage(
        id: Long,
        userId: Int,
        packageName: String,
    ): Long? {
        getById(id, userId)?.let { event ->
            return if (event.pkg == packageName) id else null
        }
        val deleted = getDeletedEvent(id, userId) ?: return null
        if (deleted.pkg != packageName) return null
        val restored = insertOrReplace(deleted.toEvent())
        if (restored <= 0L) return null
        deleteDeletedEvent(id, userId)
        return restored
    }
}

// ───────────────────────────────────────────────────────────────────────────────
// RuntimeDeletedEventDao — thin DAO, bulk logic lives in RuntimeEventDao
// ───────────────────────────────────────────────────────────────────────────────

@Dao
interface RuntimeDeletedEventDao {

    @Insert
    suspend fun insert(event: RuntimeDeletedEventRow)

    @Query("SELECT * FROM DELETED_EVENT WHERE user_id = :userId ORDER BY deleted_at DESC")
    suspend fun findByUser(userId: Int): List<RuntimeDeletedEventRow>
}

// ───────────────────────────────────────────────────────────────────────────────
// RuntimeRegisteredApplicationDao — full port of xmsf RegisteredApplicationDao
// ───────────────────────────────────────────────────────────────────────────────

@Dao
interface RuntimeRegisteredApplicationDao {

    @Query("SELECT * FROM REGISTERED_APPLICATION WHERE pkg = :pkg AND user_id = :userId LIMIT 1")
    suspend fun getByPackageName(pkg: String, userId: Int): RuntimeRegisteredApplicationRow?

    @Query("SELECT * FROM REGISTERED_APPLICATION WHERE user_id = :userId")
    suspend fun getAll(userId: Int): List<RuntimeRegisteredApplicationRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(application: RuntimeRegisteredApplicationRow): Long

    @Insert
    suspend fun insert(application: RuntimeRegisteredApplicationRow): Long

    @Update
    suspend fun update(application: RuntimeRegisteredApplicationRow): Int

    @Delete
    suspend fun delete(application: RuntimeRegisteredApplicationRow)

    @Query("UPDATE REGISTERED_APPLICATION SET blocked = :blocked WHERE id = :id AND user_id = :userId")
    suspend fun updateBlocked(id: Long, blocked: Boolean, userId: Int): Int

    @Query("SELECT blocked FROM REGISTERED_APPLICATION WHERE pkg = :pkg AND user_id = :userId LIMIT 1")
    suspend fun isBlocked(pkg: String, userId: Int): Boolean?

    @Query("SELECT island_enabled FROM REGISTERED_APPLICATION WHERE pkg = :pkg AND user_id = :userId LIMIT 1")
    suspend fun isIslandEnabled(pkg: String, userId: Int): Boolean?

    @Query("SELECT island_focus_notification FROM REGISTERED_APPLICATION WHERE pkg = :pkg AND user_id = :userId LIMIT 1")
    suspend fun isIslandFocusNotificationEnabled(pkg: String, userId: Int): Boolean?

    // -- existing simple query (preserved from original KMP DAO) ---------------

    @Query("SELECT * FROM REGISTERED_APPLICATION WHERE user_id = :userId ORDER BY pkg")
    suspend fun findByUser(userId: Int): List<RuntimeRegisteredApplicationRow>
}
