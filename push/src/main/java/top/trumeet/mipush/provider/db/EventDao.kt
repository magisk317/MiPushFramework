package top.trumeet.mipush.provider.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import top.trumeet.mipush.provider.entities.Event

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: Event): Long

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

    @Query("DELETE FROM EVENT WHERE type IN (0, 2, 10) AND date < :date")
    suspend fun deleteHistory(date: Long): Int

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
}
