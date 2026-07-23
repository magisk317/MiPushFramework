package io.github.magisk317.mipush.runtime.store.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import io.github.magisk317.mipush.runtime.store.entities.Event

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: Event): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(event: Event): Long

    @Query("SELECT * FROM EVENT WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Event?

    @Query("SELECT * FROM EVENT WHERE id < :lastId ORDER BY id DESC LIMIT :limit")
    suspend fun queryById(lastId: Long, limit: Int): List<Event>

    @Query("SELECT * FROM EVENT WHERE id < :lastId AND pkg = :pkg ORDER BY id DESC LIMIT :limit")
    suspend fun queryByIdAndPkg(lastId: Long, pkg: String, limit: Int): List<Event>

    @Query("SELECT * FROM EVENT ORDER BY date DESC LIMIT :limit OFFSET :skip")
    suspend fun query(skip: Int, limit: Int): List<Event>

    @Query("SELECT * FROM EVENT WHERE pkg = :pkg ORDER BY date DESC LIMIT :limit OFFSET :skip")
    suspend fun queryByPkg(pkg: String, skip: Int, limit: Int): List<Event>

    @RawQuery
    suspend fun queryRaw(query: SupportSQLiteQuery): List<Event>

    // 删除所有超期的瞬时事件;保留注册状态事件(20 UnRegistration / 21 RegistrationResult),
    // 它们是 queryRegisteredStatus 的数据来源,不能随保留期清掉,否则应用注册态会丢失。
    @Query("DELETE FROM EVENT WHERE type NOT IN (20, 21) AND date < :date")
    suspend fun deleteHistory(date: Long): Int

    @Query("DELETE FROM EVENT WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    // 按本地日历日聚合可清理事件的条数(排除注册态 20/21),供日历清理界面高亮与计数。
    // 用 SQLite 的 'localtime' 修饰符换算成设备时区的自然日,与用户看到的日期一致。
    @Query(
        "SELECT strftime('%Y-%m-%d', date / 1000, 'unixepoch', 'localtime') AS day, " +
            "COUNT(*) AS count FROM EVENT WHERE type NOT IN (20, 21) GROUP BY day"
    )
    suspend fun countEventsByDay(): List<DayCount>

    // 删除某个时间区间 [start, end) 内的瞬时事件(排除注册态 20/21),供"仅清理当天"使用。
    @Query("DELETE FROM EVENT WHERE type NOT IN (20, 21) AND date >= :start AND date < :end")
    suspend fun deleteHistoryInRange(start: Long, end: Long): Int

    @Query(
        "SELECT e.* FROM EVENT e " +
            "INNER JOIN (" +
            "SELECT pkg, MAX(date) AS max_date FROM EVENT WHERE type IN (21, 20) GROUP BY pkg" +
            ") latest ON e.pkg = latest.pkg AND e.date = latest.max_date " +
            "WHERE e.type IN (21, 20)"
    )
    suspend fun queryRegisteredStatus(): List<Event>

    @Query("SELECT * FROM EVENT WHERE pkg = :pkg AND type = :type ORDER BY date DESC LIMIT 1")
    suspend fun getLastEventByType(pkg: String, type: Int): Event?

    @Query("SELECT pkg, MAX(date) as date FROM EVENT WHERE type = 10 GROUP BY pkg")
    suspend fun getAllLastReceiveTimes(): List<PackageLastTime>
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
