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
import io.github.magisk317.mipush.runtime.store.kmp.DayCount
import io.github.magisk317.mipush.runtime.store.kmp.EventRetentionPolicy
import io.github.magisk317.mipush.runtime.store.kmp.EventRowType
import io.github.magisk317.mipush.runtime.store.kmp.EventRowResultType
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegistrationStatePolicy
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.runtime.store.event.EventSearchTextBuilder
import io.github.magisk317.mipush.runtime.store.event.EventType
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventQuery
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventQueryPolicy
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeQueryArgument
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventDeletionRepository
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventDeletionStore

/**
 * @author Trumeet
 * @date 2017/12/23
 */
object EventDb {
    /** 事件记录默认保留天数(与既有硬编码行为保持一致)。 */
    const val DEFAULT_RETENTION_DAYS = EventRetentionPolicy.DEFAULT_RETENTION_DAYS

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
        val query = RuntimeEventQueryPolicy.byId(
            lastId = lastId,
            size = size,
            types = types,
            packageName = pkg,
            text = text,
            userId = userId,
        )
        return eventDao.queryRaw(roomRawQuery(query))
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
        val query = RuntimeEventQueryPolicy.page(
            offset = skip,
            limit = limit,
            types = types,
            packageName = pkg,
            text = text,
            userId = currentUserId(),
        )
        return eventDao.queryRaw(roomRawQuery(query))
    }

    /**
     * 按保留天数清理事件记录。
     * @param retentionDays 保留最近多少天;小于 1 时按 1 天兜底,避免误传 0 清空全表。
     * 注册状态事件(type 20/21)由 [RuntimeEventDao.deleteHistory] 的 SQL 永久保留。
     */
    suspend fun deleteHistoryAsync(retentionDays: Int = DEFAULT_RETENTION_DAYS) {
        deletionRepository().deleteHistory(retentionDays)
    }

    suspend fun deleteByIdAsync(id: Long): Boolean =
        deletionRepository().deleteById(id)

    suspend fun deleteByIdWithUndoSnapshotAsync(
        id: Long,
        packageName: String,
        userId: Int = currentUserId(),
    ): Boolean = deletionRepository(userId).deleteByIdWithUndoSnapshot(
        id = id,
        packageName = packageName,
        requestedUserId = userId,
    )

    suspend fun restoreDeletedEventAsync(
        id: Long,
        packageName: String,
        userId: Int = currentUserId(),
    ): Long? = deletionRepository(userId).restoreDeletedEvent(
        id = id,
        packageName = packageName,
        requestedUserId = userId,
    )

    /** 按本地日历日聚合可清理事件的条数(排除注册态),供日历清理界面高亮与计数。 */
    suspend fun countEventsByDayAsync(): List<DayCount> =
        deletionRepository().countEventsByDay()

    /**
     * 删除某个时间区间 [start, end) 内的可清理事件(排除注册态),供"仅清理当天"使用。
     * @return 实际删除条数。
     */
    suspend fun deleteHistoryInRangeAsync(start: Long, end: Long): Int =
        deletionRepository().deleteHistoryInRange(start, end)

    /**
     * 清理某个时间点之前的可清理事件(排除注册态),供"清理此日期及之前"使用。
     * @return 实际删除条数。
     */
    suspend fun deleteHistoryBeforeAsync(cutoff: Long): Int =
        deletionRepository().deleteHistoryBefore(cutoff)

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
            val registeredType = RuntimeRegistrationStatePolicy.resolveRegisteredType(
                eventType = event.type,
                errorCode = data?.errorCode,
            )
            if (registeredType == RegisteredAppRegisteredType.Registered) {
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

    private fun roomRawQuery(query: RuntimeEventQuery): RoomRawQuery = RoomRawQuery(query.sql) { statement ->
        query.arguments.forEachIndexed { index, argument ->
            when (argument) {
                is RuntimeQueryArgument.IntValue -> statement.bindLong(index + 1, argument.value.toLong())
                is RuntimeQueryArgument.LongValue -> statement.bindLong(index + 1, argument.value)
                is RuntimeQueryArgument.TextValue -> statement.bindText(index + 1, argument.value)
            }
        }
    }

    private fun deletionRepository(userId: Int = currentUserId()): RuntimeEventDeletionRepository =
        RuntimeEventDeletionRepository(
            store = EventDeletionDaoAdapter,
            userId = userId,
            nowMillis = { System.currentTimeMillis() },
        )

    private object EventDeletionDaoAdapter : RuntimeEventDeletionStore {
        override suspend fun deleteHistory(cutoffMillis: Long, userId: Int): Int =
            eventDao.deleteHistory(cutoffMillis, userId)

        override suspend fun deleteById(id: Long, userId: Int): Int =
            eventDao.deleteById(id, userId)

        override suspend fun deleteByIdWithUndoSnapshotForPackage(
            id: Long,
            userId: Int,
            packageName: String,
        ): Boolean = eventDao.deleteByIdWithUndoSnapshotForPackage(id, userId, packageName)

        override suspend fun pruneDeletedEvents(cutoffMillis: Long, maxCount: Int, userId: Int) {
            eventDao.pruneDeletedEvents(cutoffMillis, maxCount, userId)
        }

        override suspend fun restoreDeletedEventForPackage(
            id: Long,
            userId: Int,
            packageName: String,
        ): Long? = eventDao.restoreDeletedEventForPackage(id, userId, packageName)

        override suspend fun countEventsByDay(userId: Int): List<DayCount> =
            eventDao.countEventsByDay(userId)

        override suspend fun deleteHistoryInRange(
            startMillis: Long,
            endMillis: Long,
            userId: Int,
        ): Int = eventDao.deleteHistoryInRange(startMillis, endMillis, userId)
    }

    private fun currentUserId(): Int = Utils.myUserId().coerceAtLeast(0)
}
