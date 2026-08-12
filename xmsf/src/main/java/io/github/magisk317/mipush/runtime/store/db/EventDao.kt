package io.github.magisk317.mipush.runtime.store.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.entities.DeletedEvent

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: Event): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(event: Event): Long

    @Query("SELECT * FROM EVENT WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getById(id: Long, userId: Int): Event?

    @Query("SELECT * FROM EVENT WHERE id < :lastId AND user_id = :userId ORDER BY id DESC LIMIT :limit")
    suspend fun queryById(lastId: Long, userId: Int, limit: Int): List<Event>

    @Query("SELECT * FROM EVENT WHERE id < :lastId AND pkg = :pkg AND user_id = :userId ORDER BY id DESC LIMIT :limit")
    suspend fun queryByIdAndPkg(lastId: Long, pkg: String, userId: Int, limit: Int): List<Event>

    @Query("SELECT * FROM EVENT WHERE user_id = :userId ORDER BY date DESC LIMIT :limit OFFSET :skip")
    suspend fun query(skip: Int, userId: Int, limit: Int): List<Event>

    @Query("SELECT * FROM EVENT WHERE pkg = :pkg AND user_id = :userId ORDER BY date DESC LIMIT :limit OFFSET :skip")
    suspend fun queryByPkg(pkg: String, userId: Int, skip: Int, limit: Int): List<Event>

    @RawQuery
    suspend fun queryRaw(query: SupportSQLiteQuery): List<Event>

    // 删除所有超期的瞬时事件;保留注册状态事件(20 UnRegistration / 21 RegistrationResult),
    // 它们是 queryRegisteredStatus 的数据来源,不能随保留期清掉,否则应用注册态会丢失。
    @Query("DELETE FROM EVENT WHERE user_id = :userId AND type NOT IN (20, 21) AND date < :date")
    suspend fun deleteHistory(date: Long, userId: Int): Int

    @Query("DELETE FROM EVENT WHERE id = :id AND user_id = :userId")
    suspend fun deleteById(id: Long, userId: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedEvent(event: DeletedEvent)

    @Query("SELECT * FROM DELETED_EVENT WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getDeletedEvent(id: Long, userId: Int): DeletedEvent?

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
        insertDeletedEvent(DeletedEvent.fromEvent(event, System.currentTimeMillis()))
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

    // 按本地日历日聚合可清理事件的条数(排除注册态 20/21),供日历清理界面高亮与计数。
    // 用 SQLite 的 'localtime' 修饰符换算成设备时区的自然日,与用户看到的日期一致。
    @Query(
        "SELECT strftime('%Y-%m-%d', date / 1000, 'unixepoch', 'localtime') AS day, " +
            "COUNT(*) AS count FROM EVENT WHERE user_id = :userId AND type NOT IN (20, 21) GROUP BY day"
    )
    suspend fun countEventsByDay(userId: Int): List<DayCount>

    // 删除某个时间区间 [start, end) 内的瞬时事件(排除注册态 20/21),供"仅清理当天"使用。
    @Query("DELETE FROM EVENT WHERE user_id = :userId AND type NOT IN (20, 21) AND date >= :start AND date < :end")
    suspend fun deleteHistoryInRange(start: Long, end: Long, userId: Int): Int

    @Query(
            "SELECT e.* FROM EVENT e " +
            "INNER JOIN (" +
            "SELECT pkg, MAX(date) AS max_date FROM EVENT WHERE user_id = :userId AND type IN (21, 20) GROUP BY pkg" +
            ") latest ON e.pkg = latest.pkg AND e.date = latest.max_date AND e.user_id = :userId " +
            "WHERE e.user_id = :userId AND e.type IN (21, 20)"
    )
    suspend fun queryRegisteredStatus(userId: Int): List<Event>

    @Query("SELECT * FROM EVENT WHERE pkg = :pkg AND type = :type AND user_id = :userId ORDER BY date DESC LIMIT 1")
    suspend fun getLastEventByType(pkg: String, type: Int, userId: Int): Event?

    @Query("SELECT pkg, MAX(date) as date FROM EVENT WHERE user_id = :userId AND type = 10 GROUP BY pkg")
    suspend fun getAllLastReceiveTimes(userId: Int): List<PackageLastTime>
}

data class PackageLastTime(
    val pkg: String,
    val date: Long
)

// 单个本地日历日的可清理事件计数;day 形如 "2026-07-13"(设备时区)。
data class DayCount(
    val day: String,
    val count: Int
)
