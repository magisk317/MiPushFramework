package io.github.magisk317.mipush.runtime.db

import android.content.Context
import android.net.Uri
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import androidx.sqlite.db.SimpleSQLiteQuery
import io.github.magisk317.mipush.common.configurations.XMPushUtils
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import io.github.magisk317.mipush.common.configurations.RegSecUtils
import io.github.magisk317.mipush.common.configurations.ConvertUtils
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.common.utils.DatabaseUtils
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.core.store.DatabaseUtils.eventDao
import io.github.magisk317.mipush.runtime.core.store.entities.Event
import io.github.magisk317.mipush.runtime.event.EventType

/**
 * @author Trumeet
 * @date 2017/12/23
 */
object EventDb {
    const val AUTHORITY = "io.github.magisk317.mipush.runtime.core.stores.EventProvider"
    const val BASE_PATH = "EVENT"

    @JvmField
    val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$BASE_PATH")

    @JvmStatic
    private fun getInstance(context: Context): DatabaseUtils {
        return DatabaseUtils(CONTENT_URI, context.contentResolver)
    }

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
        return eventDao.insert(event)
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
            date = Utils.getUTC().time,
            result = result,
            info = type.info,
            payload = type.payload,
            regSec = Utils.getRegSec(type.pkg ?: "")
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
            queryBuilder.append(" AND dev_info LIKE ?")
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
            queryBuilder.append(" AND dev_info LIKE ?")
            args.add("%$text%")
        }
        queryBuilder.append(" ORDER BY date DESC LIMIT ? OFFSET ?")
        args.add(limit)
        args.add(skip)

        return eventDao.queryRaw(SimpleSQLiteQuery(queryBuilder.toString(), args.toTypedArray()))
    }

    suspend fun deleteHistoryAsync() {
        val data = Utils.getUTC().time - 1000L * 3600L * 24 * 7
        eventDao.deleteHistory(data)
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
            if (event.type == Event.Type.RegistrationResult && (data == null || data.errorCode.toInt() == 0)) {
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
