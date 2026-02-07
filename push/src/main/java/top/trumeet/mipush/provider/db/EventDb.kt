package top.trumeet.mipush.provider.db

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import com.nihility.XMPushUtils
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmsf.push.utils.RegSecUtils
import com.xiaomi.xmsf.utils.ConvertUtils
import kotlinx.coroutines.runBlocking
import top.trumeet.common.utils.DatabaseUtils
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.DatabaseUtils.eventDao
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipush.provider.event.EventType

/**
 * @author Trumeet
 * @date 2017/12/23
 */
object EventDb {
    const val AUTHORITY = "top.trumeet.mipush.providers.EventProvider"
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

    @JvmStatic
    fun insertEvent(event: Event): Long = runBlocking {
        if (event.type == Event.Type.SendMessage) {
            Utils.setLastReceiveTime(event.pkg, event.date)
        }
        eventDao.insert(event)
    }

    @JvmStatic
    fun insertEvent(@Event.ResultType result: Int, type: EventType): Long {
        return insertEvent(createEvent(result, type))
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

    @JvmStatic
    fun queryById(
        lastId: Long?,
        size: Int,
        types: Set<Int>?,
        pkg: String?,
        text: String?
    ): List<Event> = runBlocking {
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

        eventDao.queryRaw(SimpleSQLiteQuery(queryBuilder.toString(), args.toTypedArray()))
    }

    @JvmStatic
    fun queryByPage(
        pageIndex: Int,
        pageSize: Int,
        types: Set<Int>?,
        pkg: String?,
        text: String?
    ): List<Event> {
        return query((pageIndex - 1) * pageSize, pageSize, types, pkg, text)
    }

    @JvmStatic
    fun query(
        skip: Int,
        limit: Int,
        types: Set<Int>?,
        pkg: String?,
        text: String?
    ): List<Event> = runBlocking {
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

        eventDao.queryRaw(SimpleSQLiteQuery(queryBuilder.toString(), args.toTypedArray()))
    }

    @JvmStatic
    fun deleteHistory() = runBlocking {
        val data = Utils.getUTC().time - 1000L * 3600L * 24 * 7
        eventDao.deleteHistory(data)
    }

    @JvmStatic
    fun queryRegistered(): RegistrationInfo = runBlocking {
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
        info
    }

    @JvmStatic
    fun getLastReceiveTime(packageName: String): Long = runBlocking {
        val time = Utils.getLastReceiveTime(packageName)
        if (time != null) {
            return@runBlocking time
        }

        val event = eventDao.getLastEventByType(packageName, Event.Type.SendMessage)
        val lastReceiveTime = event?.date ?: 0L
        Utils.setLastReceiveTime(packageName, lastReceiveTime)
        lastReceiveTime
    }
}
