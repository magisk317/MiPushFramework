package com.xiaomi.xmsf.stock

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import androidx.core.content.edit
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.push.service.NotificationManagerHelper
import io.github.magisk317.mipush.platform.support.NotificationVendorAdapter
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventRow
import io.github.magisk317.mipush.runtime.store.kmp.EventRowType
import io.github.magisk317.mipush.runtime.store.adapter.container
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationIntentSupport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.lang.reflect.InvocationTargetException

/** Stock-compatible delegates for com.xiaomi.push.provider.PushSupportProvider. */
internal object StockPushSupport {
    private const val CODE_OK = 0
    private const val CODE_NOT_SUPPORTED = 1
    private const val CODE_INVALID_ARGUMENT = 2
    private const val CODE_UNEXPECTED = 3
    private const val CODE_ACCESS_DENIED = 4
    private const val CODE_ROM_NOT_SUPPORTED = 6
    private const val CODE_DATA_SYNCING = 7

    private const val PREFS_NAME = "stock_surface"
    private const val KEY_PRIVACY_STATUS = "privacy_status"
    private const val KEY_READ_EVENT_IDS = "read_event_ids"
    private const val KEY_DELETED_EVENT_IDS = "deleted_event_ids"
    private const val KEY_CHANNEL_STATUS_PREFIX = "box_channel_status:"
    private const val CHANNEL_TYPE_EXTRA = "channel_type"
    private const val CHANNEL_ID_EXTRA = "channel_id"
    private const val ALLOW_BOX_EXTRA = "allow_box"
    private const val MAX_PAGE_SIZE = 100
    private const val MAX_MESSAGE_IDS_PER_CALL = 512
    private const val MAX_CHANNEL_TYPES_PER_CALL = 128
    private const val MAX_IDENTIFIER_LENGTH = 255
    private const val STOCK_NOTIFICATION_BROKER = "com.miui.systemAdSolution"

    internal data class BoxMessageRecord(
        val localEventId: Long?,
        val messageId: String,
        val channelTypeId: String?,
        val packageName: String,
        val title: String?,
        val content: String?,
        val receivedTime: Long,
        val exposed: Boolean,
        val intentData: ByteArray?,
    )

    internal data class BoxMessagePage(
        val records: List<BoxMessageRecord>,
        val hasMore: Boolean,
    )

    fun handle(
        context: Context,
        callingPackage: String?,
        method: String?,
        extras: Bundle?,
    ): Bundle {
        return try {
            when (method) {
                "getPushApps" -> success(Bundle().apply {
                    putParcelableArrayList("data", ArrayList(getPushApps(context)))
                })

                "getPushMsgs" -> getPushMessagesResult(context, extras)

                "getUnreadMsgCount" -> success(Bundle().apply {
                    putInt("count", getBoxMessageRecords(context).count { !it.exposed })
                })

                "getAppPushStatusByPkg" -> getAppPushStatusResult(context, extras)
                "setAppPushStatus" -> setAppPushStatusResult(context, extras)

                "getPrivacyStatus" -> success(Bundle().apply {
                    putBoolean("privacyStatus", prefs(context).getBoolean(KEY_PRIVACY_STATUS, false))
                })

                "setPrivacyStatus" -> {
                    if (extras == null || !extras.containsKey("agreedPrivacyPolicy")) {
                        code(CODE_INVALID_ARGUMENT)
                    } else {
                        prefs(context).edit {
                            putBoolean(KEY_PRIVACY_STATUS, extras.getBoolean("agreedPrivacyPolicy"))
                        }
                        code(CODE_OK)
                    }
                }

                "markMsgAsRead" -> updateMessages(context, extras, delete = false)
                "deleteMsgs" -> updateMessages(context, extras, delete = true)

                "post_notification" -> postNotification(context, callingPackage, extras)
                "cancel_notification" -> cancelNotification(context, callingPackage, extras)
                "check_onepush_status" -> checkOnePushStatus(extras)
                "check_callkit_whitelist" -> checkCallKitWhitelist(extras)
                "writeCallkitMsgCallback" -> writeCallKitCallback(extras)
                else -> code(CODE_NOT_SUPPORTED)
            }
        } catch (_: Throwable) {
            code(CODE_UNEXPECTED)
        }
    }

    internal fun paginate(
        records: List<BoxMessageRecord>,
        requestedCount: Int,
        cursorMessageId: String?,
        cursorReceiveTime: Long,
    ): BoxMessagePage {
        val count = requestedCount.coerceIn(1, MAX_PAGE_SIZE)
        val sorted = records.sortedWith(
            compareByDescending<BoxMessageRecord> { it.receivedTime }
                .thenByDescending { it.messageId },
        )
        val afterCursor = if (!cursorMessageId.isNullOrEmpty() && cursorReceiveTime > 0L) {
            sorted.filter {
                it.receivedTime < cursorReceiveTime ||
                    (it.receivedTime == cursorReceiveTime && it.messageId < cursorMessageId)
            }
        } else {
            sorted
        }
        val withSentinel = afterCursor.take(count + 1)
        return BoxMessagePage(
            records = withSentinel.take(count),
            hasMore = withSentinel.size > count,
        )
    }

    internal fun isNotificationBrokerAllowed(callingPackage: String?): Boolean =
        callingPackage == STOCK_NOTIFICATION_BROKER

    private fun getPushMessagesResult(context: Context, extras: Bundle?): Bundle {
        if (extras == null) return code(CODE_INVALID_ARGUMENT)
        val requestedCount = extras.getInt("msgCount", -1)
        if (requestedCount <= 0) return code(CODE_INVALID_ARGUMENT)
        val page = paginate(
            records = getBoxMessageRecords(context),
            requestedCount = requestedCount,
            cursorMessageId = extras.getString("msgId"),
            cursorReceiveTime = extras.getLong("receiveTime", -1L),
        )
        return success(Bundle().apply {
            putBoolean("hasMore", page.hasMore)
            putParcelableArrayList("data", ArrayList(page.records.map(::toBundle)))
        })
    }

    private fun getPushApps(context: Context): List<Bundle> {
        val packages = linkedSetOf<String>()
        prefs(context).all.keys
            .filter { it.startsWith(KEY_CHANNEL_STATUS_PREFIX) }
            .mapTo(packages) { it.removePrefix(KEY_CHANNEL_STATUS_PREFIX) }
        getBoxMessageRecords(context).mapTo(packages) { it.packageName }
        return packages.filter(String::isNotBlank).sorted().map { packageName ->
            Bundle().apply { putString("packageName", packageName) }
        }
    }

    private fun getAppPushStatusResult(context: Context, extras: Bundle?): Bundle {
        val packageName = extras?.getString("packageName")?.takeIf(::isBoundedIdentifier)
            ?: return code(CODE_INVALID_ARGUMENT)
        val stored = readChannelStatus(context, packageName)
        val channelTypes = linkedSetOf<String>()
        channelTypes += stored.keys
        getBoxMessageRecords(context)
            .asSequence()
            .filter { it.packageName == packageName }
            .mapNotNull(BoxMessageRecord::channelTypeId)
            .filter(::isBoundedIdentifier)
            .toCollection(channelTypes)
        val data = channelTypes.sorted().map { channelTypeId ->
            Bundle().apply {
                putString("channelTypeId", channelTypeId)
                putBoolean("enabled", stored[channelTypeId] ?: true)
            }
        }
        return success(Bundle().apply { putParcelableArrayList("data", ArrayList(data)) })
    }

    @Suppress("DEPRECATION")
    private fun setAppPushStatusResult(context: Context, extras: Bundle?): Bundle {
        val packageName = extras?.getString("packageName")?.takeIf(::isBoundedIdentifier)
            ?: return code(CODE_INVALID_ARGUMENT)
        val requested = extras.getParcelableArrayList<Bundle>("channelTypeIds")
            ?: return code(CODE_INVALID_ARGUMENT)
        if (requested.isEmpty() || requested.size > MAX_CHANNEL_TYPES_PER_CALL) {
            return code(CODE_INVALID_ARGUMENT)
        }
        val status = readChannelStatus(context, packageName).toMutableMap()
        requested.forEach { item ->
            item.getString("channelTypeId")
                ?.takeIf(::isBoundedIdentifier)
                ?.let { status[it] = item.getBoolean("enabled", true) }
        }
        writeChannelStatus(context, packageName, status)
        return code(CODE_OK)
    }

    private fun updateMessages(context: Context, extras: Bundle?, delete: Boolean): Bundle {
        val rawIds = extras?.getString("msgIds")?.takeIf(String::isNotBlank)
            ?: return code(CODE_INVALID_ARGUMENT)
        val records = getBoxMessageRecords(context)
        val targetRecords = if (rawIds.equals("all", ignoreCase = true)) {
            records
        } else {
            val ids = rawIds.split(',')
                .map(String::trim)
                .filter(::isBoundedIdentifier)
                .distinct()
            if (ids.isEmpty() || ids.size > MAX_MESSAGE_IDS_PER_CALL) {
                return code(CODE_INVALID_ARGUMENT)
            }
            val selected = ids.toHashSet()
            records.filter { it.messageId in selected }
        }

        if (delete) {
            val targetEventIds = targetRecords.mapNotNullTo(mutableSetOf(), BoxMessageRecord::localEventId)
            val deletedEventIds = readEventIds(context, KEY_DELETED_EVENT_IDS).apply {
                addAll(targetEventIds)
            }
            writeEventIds(context, KEY_DELETED_EVENT_IDS, deletedEventIds)
            val remainingRead = readEventIds(context, KEY_READ_EVENT_IDS).apply {
                removeAll(targetEventIds)
            }
            writeEventIds(context, KEY_READ_EVENT_IDS, remainingRead)
        } else {
            val readEventIds = readEventIds(context, KEY_READ_EVENT_IDS)
            targetRecords.mapNotNullTo(readEventIds, BoxMessageRecord::localEventId)
            writeEventIds(context, KEY_READ_EVENT_IDS, readEventIds)
        }
        return code(CODE_OK)
    }

    private fun getBoxMessageRecords(context: Context): List<BoxMessageRecord> {
        if (!prefs(context).getBoolean(KEY_PRIVACY_STATUS, false)) return emptyList()
        val events = runBlocking {
            EventDb.queryByIdAsync(
                lastId = null,
                size = Int.MAX_VALUE,
                types = setOf(EventRowType.SendMessage, EventRowType.Notification),
                pkg = null,
                text = null,
            )
        }
        val existingEventIds = events.mapNotNullTo(hashSetOf(), RuntimeEventRow::id)
        val readEventIds = readEventIds(context, KEY_READ_EVENT_IDS)
        val deletedEventIds = readEventIds(context, KEY_DELETED_EVENT_IDS)
        if (readEventIds.retainAll(existingEventIds)) {
            writeEventIds(context, KEY_READ_EVENT_IDS, readEventIds)
        }
        if (deletedEventIds.retainAll(existingEventIds)) {
            writeEventIds(context, KEY_DELETED_EVENT_IDS, deletedEventIds)
        }
        return events.asSequence()
            .filterNot { it.id in deletedEventIds }
            .mapNotNull { event ->
            val container = runCatching { event.container() }.getOrNull()
            val metadata = container?.metaInfo ?: return@mapNotNull null
            val channelTypeId = metadata.extra
                ?.takeIf { it[ALLOW_BOX_EXTRA] == "true" }
                ?.get(CHANNEL_TYPE_EXTRA)
                ?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val sourceChannelId = metadata.extra
                ?.get(CHANNEL_ID_EXTRA)
                ?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            if (!isBoxChannelEnabled(context, event.pkg, channelTypeId, sourceChannelId)) {
                return@mapNotNull null
            }
            val payload = event.payload ?: return@mapNotNull null
            val activityIntent = MyMIPushNotificationIntentSupport.buildBoxActivityIntent(
                context,
                container,
                payload,
            ) ?: return@mapNotNull null
            val messageId = metadata.id
                ?.takeIf(String::isNotBlank)
                ?: event.id?.toString()
                ?: return@mapNotNull null
            BoxMessageRecord(
                localEventId = event.id,
                messageId = messageId,
                channelTypeId = channelTypeId,
                packageName = event.pkg,
                title = metadata.title,
                content = metadata.description,
                receivedTime = event.date,
                exposed = event.id in readEventIds,
                intentData = marshallIntent(activityIntent),
            )
        }.toList()
    }

    private fun isBoxChannelEnabled(
        context: Context,
        packageName: String,
        channelTypeId: String,
        sourceChannelId: String,
    ): Boolean {
        if (readChannelStatus(context, packageName)[channelTypeId] == false) return false
        if (!Utils.isAppInstalled(context, packageName)) return false
        if (AppInfoUtils.getAppNotificationOp(context, packageName, true) ==
            AppInfoUtils.AppNotificationOp.NOT_ALLOWED
        ) {
            return false
        }
        val manager = NotificationManagerHelper.from(context.applicationContext, packageName)
        val appChannelId = manager.getMipushChannelId(sourceChannelId)
        val channel = manager.getNotificationChannel(appChannelId)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun marshallIntent(intent: android.content.Intent): ByteArray {
        val parcel = Parcel.obtain()
        return try {
            intent.writeToParcel(parcel, 0)
            parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }

    private fun toBundle(record: BoxMessageRecord) = Bundle().apply {
        putString("msgId", record.messageId)
        putString("channelTypeId", record.channelTypeId)
        putString("packageName", record.packageName)
        putString("title", record.title)
        putString("content", record.content)
        putLong("receivedTime", record.receivedTime)
        putBoolean("exposed", record.exposed)
        putByteArray("intentData", record.intentData)
    }

    private fun checkOnePushStatus(extras: Bundle?): Bundle {
        if (extras?.getString("pkg_name")?.takeIf(::isBoundedIdentifier) == null) {
            return code(CODE_INVALID_ARGUMENT)
        }
        // There is no OnePush synchronization database in this runtime. Code 7 is the stock
        // fail-closed response used while that data is unavailable; returning false with code 0
        // would incorrectly claim that a synchronized answer had been produced.
        return code(CODE_DATA_SYNCING)
    }

    private fun checkCallKitWhitelist(extras: Bundle?): Bundle {
        extras?.getString("pkg_name")?.takeIf(::isBoundedIdentifier)
            ?: return code(CODE_INVALID_ARGUMENT)
        // The stock whitelist source is not shipped. False is a safe, explicit deny result.
        return success(Bundle().apply { putBoolean("isInCallKitWhitelist", false) })
    }

    private fun writeCallKitCallback(extras: Bundle?): Bundle {
        if (extras == null || !extras.containsKey("phoneState")) return code(CODE_INVALID_ARGUMENT)
        if (extras.getString("msgId")?.takeIf(::isBoundedIdentifier) == null) {
            return code(CODE_INVALID_ARGUMENT)
        }
        if (extras.getString("packageName")?.takeIf(::isBoundedIdentifier) == null) {
            return code(CODE_INVALID_ARGUMENT)
        }
        // Stock uploads this callback through its CallKit telemetry plugin. Do not report a local
        // write as a successful server upload when that plugin is absent.
        return code(CODE_NOT_SUPPORTED)
    }

    private fun postNotification(
        context: Context,
        callingPackage: String?,
        extras: Bundle?,
    ): Bundle {
        if (extras == null) return code(CODE_INVALID_ARGUMENT)
        if (!NotificationVendorAdapter.isRomNotificationBelongToAppSupported(context)) {
            return code(CODE_ROM_NOT_SUPPORTED)
        }
        if (!isNotificationBrokerAllowed(callingPackage)) return code(CODE_ACCESS_DENIED)
        if (!extras.containsKey("notify_id")) return code(CODE_INVALID_ARGUMENT)
        val packageName = extras.getString("pkg_name")?.takeIf(::isBoundedIdentifier)
            ?: return code(CODE_INVALID_ARGUMENT)
        val notification = getNotification(extras) ?: return code(CODE_INVALID_ARGUMENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val icon = notification.smallIcon
            if (icon == null || icon.type == android.graphics.drawable.Icon.TYPE_RESOURCE) {
                return code(CODE_INVALID_ARGUMENT)
            }
        }
        notification.extras?.putString("target_package", packageName)
        return invokeTargetNotificationMethod(
            context = context,
            methodName = "notifyAsPackage",
            parameterTypes = arrayOf(
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!,
                Notification::class.java,
            ),
            arguments = arrayOf<Any?>(packageName, null, extras.getInt("notify_id"), notification),
        )
    }

    private fun cancelNotification(
        context: Context,
        callingPackage: String?,
        extras: Bundle?,
    ): Bundle {
        if (extras == null) return code(CODE_INVALID_ARGUMENT)
        if (!NotificationVendorAdapter.isRomNotificationBelongToAppSupported(context)) {
            return code(CODE_ROM_NOT_SUPPORTED)
        }
        if (!isNotificationBrokerAllowed(callingPackage)) return code(CODE_ACCESS_DENIED)
        if (!extras.containsKey("notify_id")) return code(CODE_INVALID_ARGUMENT)
        val packageName = extras.getString("pkg_name")?.takeIf(::isBoundedIdentifier)
            ?: return code(CODE_INVALID_ARGUMENT)
        return invokeTargetNotificationMethod(
            context = context,
            methodName = "cancelAsPackage",
            parameterTypes = arrayOf(
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!,
            ),
            arguments = arrayOf<Any?>(packageName, null, extras.getInt("notify_id")),
        )
    }

    private fun invokeTargetNotificationMethod(
        context: Context,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        arguments: Array<out Any?>,
    ): Bundle {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return code(CODE_ROM_NOT_SUPPORTED)
        return try {
            NotificationManager::class.java
                .getMethod(methodName, *parameterTypes)
                .invoke(manager, *arguments)
            code(CODE_OK)
        } catch (_: NoSuchMethodException) {
            code(CODE_ROM_NOT_SUPPORTED)
        } catch (exception: InvocationTargetException) {
            if (exception.cause is UnsupportedOperationException) {
                code(CODE_ROM_NOT_SUPPORTED)
            } else {
                code(CODE_UNEXPECTED)
            }
        } catch (_: ReflectiveOperationException) {
            code(CODE_UNEXPECTED)
        } catch (_: SecurityException) {
            code(CODE_ACCESS_DENIED)
        }
    }

    @Suppress("DEPRECATION")
    private fun getNotification(extras: Bundle): Notification? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable("notification", Notification::class.java)
        } else {
            extras.getParcelable("notification")
        }
    }

    private fun readChannelStatus(context: Context, packageName: String): Map<String, Boolean> {
        val encoded = prefs(context).getString(KEY_CHANNEL_STATUS_PREFIX + packageName, null)
            ?: return emptyMap()
        return runCatching {
            val json = Json.parseToJsonElement(encoded).jsonObject
            buildMap {
                for ((key, element) in json) {
                    if (isBoundedIdentifier(key)) {
                        put(key, element.jsonPrimitive.booleanOrNull ?: true)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun writeChannelStatus(context: Context, packageName: String, status: Map<String, Boolean>) {
        val json = buildJsonObject {
            status.forEach { (channelTypeId, enabled) -> put(channelTypeId, enabled) }
        }
        prefs(context).edit { putString(KEY_CHANNEL_STATUS_PREFIX + packageName, json.toString()) }
    }

    private fun readEventIds(context: Context, key: String): MutableSet<Long> =
        prefs(context).getStringSet(key, emptySet())
            .orEmpty()
            .mapNotNullTo(mutableSetOf(), String::toLongOrNull)

    private fun writeEventIds(context: Context, key: String, eventIds: Set<Long>) {
        prefs(context).edit { putStringSet(key, eventIds.mapTo(mutableSetOf(), Long::toString)) }
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun isBoundedIdentifier(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_IDENTIFIER_LENGTH && value != "null"

    private fun success(data: Bundle): Bundle = StockSurfaceSupport.pushSupportResult(CODE_OK, data)

    private fun code(value: Int): Bundle = StockSurfaceSupport.pushSupportResult(value)
}
