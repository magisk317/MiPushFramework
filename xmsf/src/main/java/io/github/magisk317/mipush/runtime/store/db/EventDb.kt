package io.github.magisk317.mipush.runtime.store.db

import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import androidx.sqlite.db.SimpleSQLiteQuery
import io.github.magisk317.mipush.platform.support.XMPushUtils
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import io.github.magisk317.mipush.utils.RegSecUtils
import io.github.magisk317.mipush.utils.ConvertUtils
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.DatabaseUtils.eventDao
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.event.EventSearchTextBuilder
import io.github.magisk317.mipush.runtime.store.event.EventType

/**
 * @author Trumeet
 * @date 2017/12/23
 */
object EventDb {
    /** 事件记录默认保留天数(与既有硬编码行为保持一致)。 */
    const val DEFAULT_RETENTION_DAYS = 7

    class RegistrationInfo {
        @JvmField
        var registered: MutableSet<String> = HashSet()

        @JvmField
        var unregistered: MutableSet<String> = HashSet()
    }

    suspend fun insertEventAsync(event: Event): Long {
        Napier.d("insertEvent() called with: $event", tag = "EventDb")
        if (event.type == Event.Type.SendMessage) {
            Utils.setLastReceiveTime(event.pkg, event.date)
        }
        val id = eventDao.insert(event)
        // 入库咽喉节流触发按天清理,避免事件表无上界增长(内部有时间间隔节流)。
        EventRetentionManager.maybePruneAfterInsert()
        return id
    }

    suspend fun getByIdAsync(id: Long): Event? = eventDao.getById(id)

    suspend fun insertOrReplaceEventAsync(event: Event): Long {
        Napier.d("insertOrReplaceEvent() called with: $event", tag = "EventDb")
        if (event.type == Event.Type.SendMessage) {
            Utils.setLastReceiveTime(event.pkg, event.date)
        }
        val id = eventDao.insertOrReplace(event)
        EventRetentionManager.maybePruneAfterInsert()
        return if (id > 0L) id else (event.id ?: id)
    }

    suspend fun insertEventAsync(@Event.ResultType result: Int, type: EventType): Long {
        return insertEventAsync(createEvent(result, type))
    }

    @JvmStatic
    fun createEvent(@Event.ResultType result: Int, type: EventType): Event {
        return Event(
            id = null,
            pkg = type.pkg ?: "",
            type = type.type,
            date = System.currentTimeMillis(),
            result = result,
            info = type.info,
            payload = type.payload,
            regSec = Utils.getRegSec(type.pkg ?: ""),
            searchText = EventSearchTextBuilder.build(type),
        )
    }

    suspend fun queryByIdAsync(
        lastId: Long?,
        size: Int,
        types: Set<Int>?,
        pkg: String?,
        text: String?
    ): List<Event> {
        val queryBuilder = StringBuilder("SELECT * FROM EVENT WHERE 1=1")
        val args = mutableListOf<Any>()
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

        return eventDao.queryRaw(SimpleSQLiteQuery(queryBuilder.toString(), args.toTypedArray()))
    }

    @JvmStatic
    fun queryByPage(
        pageIndex: Int,
        pageSize: Int,
        types: Set<Int>?,
        pkg: String?,
        text: String?
    ): List<Event> {
        return runBlocking { queryAsync((pageIndex - 1) * pageSize, pageSize, types, pkg, text) }
    }

    suspend fun queryAsync(
        skip: Int,
        limit: Int,
        types: Set<Int>?,
        pkg: String?,
        text: String?
    ): List<Event> {
        val queryBuilder = StringBuilder("SELECT * FROM EVENT WHERE 1=1")
        val args = mutableListOf<Any>()
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

        return eventDao.queryRaw(SimpleSQLiteQuery(queryBuilder.toString(), args.toTypedArray()))
    }

    /**
     * 按保留天数清理事件记录。
     * @param retentionDays 保留最近多少天;小于 1 时按 1 天兜底,避免误传 0 清空全表。
     * 注册状态事件(type 20/21)由 [EventDao.deleteHistory] 的 SQL 永久保留。
     */
    suspend fun deleteHistoryAsync(retentionDays: Int = DEFAULT_RETENTION_DAYS) {
        val days = retentionDays.coerceAtLeast(1)
        val cutoff = System.currentTimeMillis() - 1000L * 3600L * 24 * days
        eventDao.deleteHistory(cutoff)
    }

    suspend fun deleteByIdAsync(id: Long): Boolean {
        return eventDao.deleteById(id) > 0
    }

    /** 按本地日历日聚合可清理事件的条数(排除注册态),供日历清理界面高亮与计数。 */
    suspend fun countEventsByDayAsync(): List<DayCount> {
        return eventDao.countEventsByDay()
    }

    /**
     * 删除某个时间区间 [start, end) 内的可清理事件(排除注册态),供"仅清理当天"使用。
     * @return 实际删除条数。
     */
    suspend fun deleteHistoryInRangeAsync(start: Long, end: Long): Int {
        return eventDao.deleteHistoryInRange(start, end)
    }

    /**
     * 清理某个时间点之前的可清理事件(排除注册态),供"清理此日期及之前"使用。
     * @return 实际删除条数。
     */
    suspend fun deleteHistoryBeforeAsync(cutoff: Long): Int {
        return eventDao.deleteHistory(cutoff)
    }

    suspend fun queryRegisteredAsync(): RegistrationInfo {
        val events = eventDao.queryRegisteredStatus()
        val info = RegistrationInfo()
        for (event in events) {
            val container = XMPushUtils.packToContainer(event.payload)
            var data: XmPushActionRegistrationResult? = null
            try {
                data = ConvertUtils.getResponseMessageBodyFromContainer(
                    container,
                    RegSecUtils.getRegSec(container)
                ) as XmPushActionRegistrationResult
            } catch (_: Exception) {
            }
            if (event.type == Event.Type.RegistrationResult && data?.errorCode?.toInt() == 0) {
                info.registered.add(event.pkg)
            } else {
                info.unregistered.add(event.pkg)
            }
        }
        return info
    }

    suspend fun getLastReceiveTimeAsync(packageName: String): Long {
        val time = Utils.getLastReceiveTime(packageName)
        if (time != null) {
            return time
        }

        val event = eventDao.getLastEventByType(packageName, Event.Type.SendMessage)
        val lastReceiveTime = event?.date ?: 0L
        Utils.setLastReceiveTime(packageName, lastReceiveTime)
        return lastReceiveTime
    }

    suspend fun getAllLastReceiveTimesAsync(): Map<String, Long> {
        return eventDao.getAllLastReceiveTimes().associate { it.pkg to it.date }
    }
}
