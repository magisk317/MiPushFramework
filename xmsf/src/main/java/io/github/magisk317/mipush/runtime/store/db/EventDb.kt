package io.github.magisk317.mipush.runtime.store.db

import co.touchlab.kermit.Logger
import androidx.room.RoomRawQuery
import io.github.magisk317.mipush.platform.support.XMPushUtils
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import io.github.magisk317.mipush.utils.RegSecUtils
import io.github.magisk317.mipush.utils.ConvertUtils
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.DatabaseUtils.eventDao
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventRow
import io.github.magisk317.mipush.runtime.store.kmp.EventRowType
import io.github.magisk317.mipush.runtime.store.kmp.EventRowResultType
import io.github.magisk317.mipush.runtime.store.event.EventSearchTextBuilder
import io.github.magisk317.mipush.runtime.store.event.EventType
import io.github.magisk317.mipush.runtime.store.kmp.DayCount

/**
 * @author Trumeet
 * @date 2017/12/23
 */
object EventDb {
    /** 事件记录默认保留天数(与既有硬编码行为保持一致)。 */
    const val DEFAULT_RETENTION_DAYS = 7
    private const val UNDO_RETENTION_MS = 24L * 60L * 60L * 1000L
    private const val MAX_UNDO_EVENTS = 64

    class RegistrationInfo {
        @JvmField
        var registered: MutableSet<String> = HashSet()

        @JvmField
        var unregistered: MutableSet<String> = HashSet()
    }

    suspend fun insertEventAsync(event: RuntimeEventRow): Long {
        Logger.withTag("EventDb").d { "insertEvent() called with: $event" }
        val scoped = event.copy(userId = currentUserId())
        if (scoped.type == EventRowType.SendMessage) {
            Utils.setLastReceiveTime(scoped.pkg, scoped.date, scoped.userId)
        }
        val id = eventDao.insert(scoped)
        // 入库咽喉节流触发按天清理,避免事件表无上界增长(内部有时间间隔节流)。
        EventRetentionManager.maybePruneAfterInsert()
        return id
    }

    suspend fun getByIdAsync(id: Long, userId: Int = currentUserId()): RuntimeEventRow? =
        eventDao.getById(id, userId.coerceAtLeast(0))

    suspend fun insertOrReplaceEventAsync(event: RuntimeEventRow): Long {
        Logger.withTag("EventDb").d { "insertOrReplaceEvent() called with: $event" }
        val scoped = event.copy(userId = currentUserId())
        if (scoped.type == EventRowType.SendMessage) {
            Utils.setLastReceiveTime(scoped.pkg, scoped.date, scoped.userId)
        }
        val id = eventDao.insertOrReplace(scoped)
        EventRetentionManager.maybePruneAfterInsert()
        return if (id > 0L) id else (scoped.id ?: id)
    }

    suspend fun insertEventAsync(result: Int, type: EventType): Long {
        return insertEventAsync(createEvent(result, type))
    }

    @JvmStatic
    fun createEvent(result: Int, type: EventType): RuntimeEventRow {
        return RuntimeEventRow(
            id = null,
            pkg = type.pkg ?: "",
            type = type.type,
            date = System.currentTimeMillis(),
            result = result,
            info = type.info,
            payload = type.payload,
            regSec = Utils.getRegSec(type.pkg ?: ""),
            searchText = EventSearchTextBuilder.build(type),
            userId = currentUserId(),
        )
    }

    suspend fun queryByIdAsync(
        lastId: Long?,
        size: Int,
        types: Set<Int>?,
        pkg: String?,
        text: String?,
        userId: Int = currentUserId(),
    ): List<RuntimeEventRow> {
        val queryBuilder = StringBuilder("SELECT * FROM EVENT WHERE user_id = ?")
        val args = mutableListOf<Any>(userId.coerceAtLeast(0))
        if (lastId != null) {
            queryBuilder.append(" AND id < ?")
            args.add(lastId)
        }
        if (!pkg.isNullOrBlank()) {
            queryBuilder.append(" AND pkg = ?")
            args.add(pkg)
        }
        if (!types.isNullOrEmpty()) {
            queryBuilder.append(" AND type IN (")
            queryBuilder.append(types.joinToString(",") { "?" })
            queryBuilder.append(")")
            args.addAll(types)
        }
        if (!text.isNullOrBlank()) {
            // 搜索只打 UI 对齐的快照列;search_text 为空时回退 dev_info 兼容极少数无快照的旧记录。
            queryBuilder.append(" AND (search_text LIKE ? OR (search_text IS NULL AND dev_info LIKE ?))")
            args.add("%$text%")
            args.add("%$text%")
        }
        queryBuilder.append(" ORDER BY id DESC LIMIT ?")
        args.add(size)

        return eventDao.queryRaw(roomRawQuery(queryBuilder.toString(), args))
    }

    @JvmStatic
    fun queryByPage(
        pageIndex: Int,
        pageSize: Int,
        types: Set<Int>?,
        pkg: String?,
        text: String?
    ): List<RuntimeEventRow> {
        return runBlocking { queryAsync((pageIndex - 1) * pageSize, pageSize, types, pkg, text) }
    }

    suspend fun queryAsync(
        skip: Int,
        limit: Int,
        types: Set<Int>?,
        pkg: String?,
        text: String?
    ): List<RuntimeEventRow> {
        val queryBuilder = StringBuilder("SELECT * FROM EVENT WHERE user_id = ?")
        val args = mutableListOf<Any>(currentUserId())
        if (!pkg.isNullOrBlank()) {
            queryBuilder.append(" AND pkg = ?")
            args.add(pkg)
        }
        if (!types.isNullOrEmpty()) {
            queryBuilder.append(" AND type IN (")
            queryBuilder.append(types.joinToString(",") { "?" })
            queryBuilder.append(")")
            args.addAll(types)
        }
        if (!text.isNullOrBlank()) {
            // 搜索打 UI 对齐的 search_text 快照;兼容极少数仅有旧 dev_info 的残留行。
            queryBuilder.append(" AND (search_text LIKE ? OR dev_info LIKE ?)")
            args.add("%$text%")
            args.add("%$text%")
        }
        queryBuilder.append(" ORDER BY date DESC LIMIT ? OFFSET ?")
        args.add(limit)
        args.add(skip)

        return eventDao.queryRaw(roomRawQuery(queryBuilder.toString(), args))
    }

    /**
     * 按保留天数清理事件记录。
     * @param retentionDays 保留最近多少天;小于 1 时按 1 天兜底,避免误传 0 清空全表。
     * 注册状态事件(type 20/21)由 [RuntimeEventDao.deleteHistory] 的 SQL 永久保留。
     */
    suspend fun deleteHistoryAsync(retentionDays: Int = DEFAULT_RETENTION_DAYS) {
        val days = retentionDays.coerceAtLeast(1)
        val cutoff = System.currentTimeMillis() - 1000L * 3600L * 24 * days
        eventDao.deleteHistory(cutoff, currentUserId())
    }

    suspend fun deleteByIdAsync(id: Long): Boolean {
        return eventDao.deleteById(id, currentUserId()) > 0
    }

    suspend fun deleteByIdWithUndoSnapshotAsync(
        id: Long,
        packageName: String,
        userId: Int = currentUserId(),
    ): Boolean {
        val scopedUserId = userId.coerceAtLeast(0)
        val deleted = eventDao.deleteByIdWithUndoSnapshotForPackage(id, scopedUserId, packageName)
        if (deleted) {
            eventDao.pruneDeletedEvents(
                cutoff = System.currentTimeMillis() - UNDO_RETENTION_MS,
                maxCount = MAX_UNDO_EVENTS,
                userId = scopedUserId,
            )
        }
        return deleted
    }

    suspend fun restoreDeletedEventAsync(
        id: Long,
        packageName: String,
        userId: Int = currentUserId(),
    ): Long? {
        return eventDao.restoreDeletedEventForPackage(id, userId.coerceAtLeast(0), packageName)
    }

    /** 按本地日历日聚合可清理事件的条数(排除注册态),供日历清理界面高亮与计数。 */
    suspend fun countEventsByDayAsync(): List<DayCount> {
        return eventDao.countEventsByDay(currentUserId())
    }

    /**
     * 删除某个时间区间 [start, end) 内的可清理事件(排除注册态),供"仅清理当天"使用。
     * @return 实际删除条数。
     */
    suspend fun deleteHistoryInRangeAsync(start: Long, end: Long): Int {
        return eventDao.deleteHistoryInRange(start, end, currentUserId())
    }

    /**
     * 清理某个时间点之前的可清理事件(排除注册态),供"清理此日期及之前"使用。
     * @return 实际删除条数。
     */
    suspend fun deleteHistoryBeforeAsync(cutoff: Long): Int {
        return eventDao.deleteHistory(cutoff, currentUserId())
    }

    suspend fun queryRegisteredAsync(): RegistrationInfo {
        val events = eventDao.queryRegisteredStatus(currentUserId())
        val info = RegistrationInfo()
        for (event in events) {
            val container = XMPushUtils.packToContainer(event.payload)
            var data: XmPushActionRegistrationResult? = null
            try {
                data = ConvertUtils.getResponseMessageBodyFromContainer(
                    container,
                    RegSecUtils.getRegSec(container, event.userId)
                ) as XmPushActionRegistrationResult
            } catch (_: Exception) {
            }
            if (event.type == EventRowType.RegistrationResult && data?.errorCode?.toInt() == 0) {
                info.registered.add(event.pkg)
            } else {
                info.unregistered.add(event.pkg)
            }
        }
        return info
    }

    suspend fun getLastReceiveTimeAsync(packageName: String): Long {
        val userId = currentUserId()
        val time = Utils.getLastReceiveTime(packageName, userId)
        if (time != null) {
            return time
        }

        val event = eventDao.getLastEventByType(packageName, EventRowType.SendMessage, userId)
        val lastReceiveTime = event?.date ?: 0L
        Utils.setLastReceiveTime(packageName, lastReceiveTime, userId)
        return lastReceiveTime
    }

    suspend fun getAllLastReceiveTimesAsync(): Map<String, Long> {
        return eventDao.getAllLastReceiveTimes(currentUserId()).associate { it.pkg to it.date }
    }

    private fun roomRawQuery(sql: String, args: List<Any>): RoomRawQuery = RoomRawQuery(sql) { statement ->
        args.forEachIndexed { index, value ->
            when (value) {
                is Int -> statement.bindLong(index + 1, value.toLong())
                is Long -> statement.bindLong(index + 1, value)
                is String -> statement.bindText(index + 1, value)
                else -> error("Unsupported runtime event query argument: ${value::class.java.name}")
            }
        }
    }

    private fun currentUserId(): Int = Utils.myUserId().coerceAtLeast(0)
}
