package com.xiaomi.xmsf.stock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.core.content.edit
import android.database.MatrixCursor
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmsf.account.DefaultAccountCloudBridge
import io.github.magisk317.mipush.notification.NotificationChannelManager
import com.xiaomi.xmsf.push.service.XMAccountManager
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.store.DatabaseUtils
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication
import kotlinx.coroutines.runBlocking

object StockSurfaceSupport {
    const val KEY_CODE = "code"
    const val KEY_DATA = "data"
    const val KEY_RESULT = "result"
    const val KEY_MSG = "msg"

    private const val PREFS_NAME = "stock_surface"
    private const val KEY_PRIVACY_STATUS = "privacy_status"
    private const val KEY_READ_MESSAGE_IDS = "read_message_ids"
    private const val KEY_KEEPALIVE_CONFIG = "keepalive_config"
    private const val KEY_STAT_EVENTS = "stat_events"
    private const val KEY_PROFILE_PREFIX = "profile:"
    private const val MAX_STAT_EVENTS = 32

    private const val SUPPORT_FLAG_PROFILE_ID = 4
    private const val SUPPORT_FLAG_EXTENSION_NOTIFICATION = 262144
    private const val SUPPORT_FLAG_CALLKIT = 524288

    private const val CODE_OK = 0
    private const val CODE_NOT_SUPPORTED = 1
    private const val CODE_UNKNOWN_RESULT = 2
    private const val CODE_UNEXPECTED = 3
    private const val CODE_ACCESS_DENIED = 4
    private const val CODE_NOT_FOUND = 5
    private const val CODE_UNKNOWN_METHOD = 6

    data class PushControlSnapshot(
        val controlMode: Int,
        val keyWords: String,
        val specialPackageNames: String,
        val controlSwitch: Boolean,
    )

    @JvmStatic
    fun pushSupportResult(code: Int, data: Bundle? = null, message: String? = null): Bundle {
        return Bundle().apply {
            putInt(KEY_CODE, code)
            if (data != null) {
                putBundle(KEY_RESULT, data)
                putBundle(KEY_DATA, data)
            }
            if (!message.isNullOrBlank()) {
                putString(KEY_MSG, message)
            }
        }
    }

    @JvmStatic
    fun handlePushCommonCall(context: Context, method: String?, extras: Bundle?): Bundle {
        return when (method) {
            "is_push_support" -> Bundle().apply {
                putBoolean("is_supported", isPushSupport(extras?.getInt("push_support_flag") ?: 0, context))
            }
            else -> pushSupportResult(CODE_UNKNOWN_METHOD, message = "unknown_method:$method")
        }
    }

    @JvmStatic
    fun handlePushSupportCall(context: Context, method: String?, extras: Bundle?): Bundle {
        ensureInitialized(context)
        return runCatching {
            when (method) {
                "getPushApps" -> pushSupportResult(CODE_OK, Bundle().apply {
                    putParcelableArrayList("data", ArrayList(getPushApps(context)))
                })
                "getPushMsgs" -> pushSupportResult(CODE_OK, Bundle().apply {
                    putParcelableArrayList("data", ArrayList(getPushMessages(extras)))
                })
                "getUnreadMsgCount" -> pushSupportResult(CODE_OK, Bundle().apply {
                    putInt("count", getUnreadPushMessageCount(extras?.getString("packageName")))
                })
                "getAppPushStatusByPkg" -> {
                    val packageName = extras?.getString("packageName").orEmpty()
                    if (packageName.isBlank()) {
                        pushSupportResult(CODE_UNEXPECTED, message = "packageName_required")
                    } else {
                        pushSupportResult(CODE_OK, getAppPushStatus(packageName))
                    }
                }
                "setAppPushStatus" -> {
                    val packageName = extras?.getString("packageName").orEmpty()
                    if (packageName.isBlank() || extras == null || !extras.containsKey("pushStatus")) {
                        pushSupportResult(CODE_UNEXPECTED, message = "packageName_and_pushStatus_required")
                    } else {
                        val status = extras.getInt("pushStatus", RegisteredApplication.Type.ASK)
                        pushSupportResult(CODE_OK, setAppPushStatus(packageName, status))
                    }
                }
                "getPrivacyStatus" -> pushSupportResult(CODE_OK, Bundle().apply {
                    putBoolean("agreedPrivacyPolicy", prefs(context).getBoolean(KEY_PRIVACY_STATUS, false))
                })
                "setPrivacyStatus" -> {
                    if (extras == null || !extras.containsKey("agreedPrivacyPolicy")) {
                        pushSupportResult(CODE_UNEXPECTED, message = "agreedPrivacyPolicy_required")
                    } else {
                        prefs(context).edit { putBoolean(KEY_PRIVACY_STATUS, extras.getBoolean("agreedPrivacyPolicy")) }
                        pushSupportResult(CODE_OK)
                    }
                }
                "markMsgAsRead" -> {
                    markMessageIdsAsRead(extras?.getString("msgIds"))
                    pushSupportResult(CODE_OK)
                }
                "deleteMsgs" -> {
                    markMessageIdsAsRead(extras?.getString("msgIds"))
                    pushSupportResult(CODE_OK, message = "delete_is_filtered_via_read_state")
                }
                else -> pushSupportResult(CODE_UNKNOWN_METHOD, message = "unknown_method:$method")
            }
        }.getOrElse {
            pushSupportResult(CODE_UNEXPECTED, message = it.message ?: it::class.java.simpleName)
        }
    }

    @JvmStatic
    fun handleProfileCall(context: Context, method: String?, extras: Bundle?): Bundle {
        val packageName = extras?.getString("packageName").orEmpty()
        return when (method) {
            "getProfileIds", "getProfileId" -> Bundle().apply {
                putStringArrayList("profileIds", ArrayList(readProfileIds(context, packageName)))
            }
            "setProfileId", "addProfileId", "putProfileId" -> {
                val ids = readIncomingProfileIds(extras)
                writeProfileIds(context, packageName, readProfileIds(context, packageName) + ids)
                Bundle().apply {
                    putStringArrayList("profileIds", ArrayList(readProfileIds(context, packageName)))
                }
            }
            "removeProfileId" -> {
                val ids = readIncomingProfileIds(extras)
                writeProfileIds(context, packageName, readProfileIds(context, packageName) - ids.toSet())
                Bundle().apply {
                    putStringArrayList("profileIds", ArrayList(readProfileIds(context, packageName)))
                }
            }
            "clearProfileIds" -> {
                writeProfileIds(context, packageName, emptySet())
                Bundle()
            }
            "hasProfileId" -> Bundle().apply {
                putBoolean("contains", readIncomingProfileIds(extras).any(readProfileIds(context, packageName)::contains))
            }
            else -> Bundle().apply {
                putString(KEY_MSG, "unknown_method:$method")
            }
        }
    }

    @JvmStatic
    fun createChannel(context: Context, extras: Bundle?): Bundle {
        ensureInitialized(context)
        if (extras == null) {
            return pushSupportResult(CODE_UNEXPECTED, message = "extras_required")
        }
        val packageName = extras.getString("pkgName").orEmpty()
        val channelName = extras.getString("channelName").orEmpty()
        val channelDescription = extras.getString("channelDesc")
        val channelId = extras.getString("channelId").orEmpty()
        val importance = extras.getInt("channelImportance", NotificationManager.IMPORTANCE_DEFAULT)
        if (packageName.isBlank() || channelName.isBlank() || channelId.isBlank()) {
            return pushSupportResult(CODE_UNEXPECTED, message = "pkgName_channelName_channelId_required")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManagerEx.createNotificationChannels(
                packageName,
                listOf(NotificationChannel(channelId, channelName, importance).apply {
                    description = channelDescription
                }),
            )
        }
        return pushSupportResult(CODE_OK, Bundle().apply {
            putString("pkgName", packageName)
            putString("channelId", channelId)
        })
    }

    @JvmStatic
    fun queryChannelState(context: Context, extras: Bundle?): Bundle {
        ensureInitialized(context)
        if (extras == null) {
            return pushSupportResult(CODE_UNEXPECTED, message = "extras_required")
        }
        val packageName = extras.getString("pkgName").orEmpty()
        val channelId = extras.getString("channelId").orEmpty()
        if (packageName.isBlank() || channelId.isBlank()) {
            return pushSupportResult(CODE_UNEXPECTED, message = "pkgName_channelId_required")
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return pushSupportResult(CODE_NOT_SUPPORTED, message = "notification_channels_unsupported")
        }
        val channel = NotificationChannelManager.getNotificationManagerEx().getNotificationChannel(packageName, channelId)
            ?: return pushSupportResult(CODE_NOT_FOUND, message = "channel_missing")
        val permissions = computeChannelPermissions(channel)
        return pushSupportResult(CODE_OK, Bundle().apply {
            putInt("state", if (channel.importance == NotificationManager.IMPORTANCE_NONE) 0 else 1)
            putString("channelPermissions", permissions.toString())
            putString("pkgName", packageName)
            putString("channelId", channelId)
            putString("appChannelId", channel.id)
        })
    }

    @JvmStatic
    fun pushControlCursor(context: Context): MatrixCursor {
        val snapshot = readPushControlSnapshot(context)
        return MatrixCursor(arrayOf("control_mode", "key_words", "special_pkg_names", "control_switch")).apply {
            addRow(
                arrayOf<Any>(
                    snapshot.controlMode,
                    snapshot.keyWords,
                    snapshot.specialPackageNames,
                    if (snapshot.controlSwitch) 1 else 0,
                ),
            )
        }
    }

    @JvmStatic
    fun readPushControlSnapshot(context: Context): PushControlSnapshot {
        val onlineConfig = OnlineConfig.getInstance(context)
        return PushControlSnapshot(
            controlMode = onlineConfig.getIntValue(1002, -1),
            keyWords = onlineConfig.getStringValue(1003, "") ?: "",
            specialPackageNames = onlineConfig.getStringValue(1004, "") ?: "",
            controlSwitch = onlineConfig.getBooleanValue(1005, false),
        )
    }

    @JvmStatic
    fun getOnlineBooleanConfig(context: Context, key: Int, defaultValue: Boolean): Boolean {
        return OnlineConfig.getInstance(context).getBooleanValue(key, defaultValue)
    }

    @JvmStatic
    fun getOnlineIntConfig(context: Context, key: Int, defaultValue: Int): Int {
        return OnlineConfig.getInstance(context).getIntValue(key, defaultValue)
    }

    @JvmStatic
    fun getOnlineStringConfig(context: Context, key: Int, defaultValue: String): String {
        return OnlineConfig.getInstance(context).getStringValue(key, defaultValue) ?: defaultValue
    }

    @JvmStatic
    fun updateKeepAliveConfig(context: Context, configJson: String?) {
        prefs(context).edit { putString(KEY_KEEPALIVE_CONFIG, configJson) }
        PushRuntime.observeChannelEvent(
            packageName = context.packageName,
            action = "keepalive_config_updated",
            source = "StockSurfaceSupport.updateKeepAliveConfig",
        )
    }

    @JvmStatic
    fun getKeepAliveConfig(context: Context): String? {
        return prefs(context).getString(KEY_KEEPALIVE_CONFIG, null)
    }

    @JvmStatic
    fun recordStatEvent(context: Context, event: String?) {
        val normalized = event?.takeIf { it.isNotBlank() } ?: return
        val existing = prefs(context).getString(KEY_STAT_EVENTS, "").orEmpty()
            .lineSequence()
            .filter(String::isNotBlank)
            .toMutableList()
        existing += "${System.currentTimeMillis()}:$normalized"
        while (existing.size > MAX_STAT_EVENTS) {
            existing.removeAt(0)
        }
        prefs(context).edit { putString(KEY_STAT_EVENTS, existing.joinToString("\n")) }
    }

    @JvmStatic
    fun recordNotificationEvent(context: Context, action: String, packageName: String?) {
        PushRuntime.observeNotificationEvent(
            packageName = packageName,
            action = action,
            source = "StockSurfaceSupport.recordNotificationEvent",
        )
        recordStatEvent(context, "notification:$action:${packageName.orEmpty()}")
    }

    @JvmStatic
    fun accountAvailabilityBundle(context: Context): Bundle {
        val availability = DefaultAccountCloudBridge.getInstance(context).availability()
        return Bundle().apply {
            putBoolean("xiaomiAccountPresent", availability.xiaomiAccountPresent)
            putBoolean("accountPackagePresent", availability.accountPackagePresent)
            putBoolean("cloudServicePackagePresent", availability.cloudServicePackagePresent)
        }
    }

    @JvmStatic
    fun serviceTokenBundle(context: Context, sid: String): Bundle {
        val result = XMAccountManager.getInstance(context).getServiceToken(sid)
        return DefaultAccountCloudBridge.toBundle(result)
    }

    @JvmStatic
    fun ensureInitialized(context: Context) {
        DatabaseUtils.init(context.applicationContext)
        NotificationManagerEx.init(context.applicationContext)
        UtilsContextHolder.context = context.applicationContext
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun isPushSupport(flag: Int, context: Context): Boolean {
        return when (flag) {
            SUPPORT_FLAG_PROFILE_ID -> true
            SUPPORT_FLAG_EXTENSION_NOTIFICATION -> false
            SUPPORT_FLAG_CALLKIT -> isPackageInstalled(context, "com.os.callservice")
            else -> false
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    private fun getPushApps(context: Context): List<Bundle> {
        return RegisteredApplicationDb.getList(null).map { application ->
            Bundle().apply {
                putString("packageName", application.packageName)
                putString("appName", application.appName)
            }
        }
    }

    private fun getPushMessages(extras: Bundle?): List<Bundle> {
        val packageName = extras?.getString("packageName")
        val requestedCount = extras?.getInt("msgCount", 20) ?: 20
        val maxCount = requestedCount.coerceIn(1, 50)
        val beforeId = extras?.getLong("msgId", -1L)?.takeIf { it > 0 }
        val events = runBlocking {
            EventDb.queryByIdAsync(
                lastId = beforeId,
                size = maxCount,
                types = setOf(Event.Type.SendMessage, Event.Type.Notification),
                pkg = packageName,
                text = null,
            )
        }
        return events.map { event ->
            val container = event.container
            Bundle().apply {
                putString("msgId", container?.metaInfo?.id ?: event.id?.toString())
                putString("channelTypeId", container?.metaInfo?.topic)
                putString("packageName", event.pkg)
                putString("title", container?.metaInfo?.title)
                putString("content", container?.metaInfo?.description)
                putLong("receivedTime", event.date)
                putBoolean("exposed", isMessageRead(container?.metaInfo?.id ?: event.id?.toString()))
                putByteArray("intentData", event.payload)
            }
        }
    }

    private fun getUnreadPushMessageCount(packageName: String?): Int {
        val events = runBlocking {
            EventDb.queryByIdAsync(
                lastId = null,
                size = 50,
                types = setOf(Event.Type.SendMessage, Event.Type.Notification),
                pkg = packageName,
                text = null,
            )
        }
        return events.count { event ->
            !isMessageRead(event.container?.metaInfo?.id ?: event.id?.toString())
        }
    }

    private fun markMessageIdsAsRead(msgIds: String?) {
        val context = UtilsContextHolder.context
        val current = prefs(context).getStringSet(KEY_READ_MESSAGE_IDS, emptySet()).orEmpty().toMutableSet()
        if (msgIds.isNullOrBlank() || msgIds.equals("all", ignoreCase = true)) {
            val allIds = runBlocking {
                EventDb.queryByIdAsync(
                    lastId = null,
                    size = 50,
                    types = setOf(Event.Type.SendMessage, Event.Type.Notification),
                    pkg = null,
                    text = null,
                )
            }.mapNotNull { it.container?.metaInfo?.id ?: it.id?.toString() }
            current += allIds
        } else {
            current += msgIds.split(',').map(String::trim).filter(String::isNotBlank)
        }
        prefs(context).edit { putStringSet(KEY_READ_MESSAGE_IDS, current) }
    }

    private fun isMessageRead(messageId: String?): Boolean {
        if (messageId.isNullOrBlank()) {
            return false
        }
        return prefs(UtilsContextHolder.context).getStringSet(KEY_READ_MESSAGE_IDS, emptySet()).orEmpty().contains(messageId)
    }

    private fun getAppPushStatus(packageName: String): Bundle {
        val application = RegisteredApplicationDb.registerApplication(packageName)
        return Bundle().apply {
            putString("packageName", application.packageName)
            putInt("pushStatus", application.type)
            putBoolean("notificationOnRegister", application.notificationOnRegister)
            putInt("registeredType", application.registeredType)
        }
    }

    private fun setAppPushStatus(packageName: String, status: Int): Bundle {
        val application = RegisteredApplicationDb.registerApplication(packageName)
        application.type = status
        RegisteredApplicationDb.update(application)
        return getAppPushStatus(packageName)
    }

    private fun readProfileIds(context: Context, packageName: String): Set<String> {
        if (packageName.isBlank()) {
            return emptySet()
        }
        val stored = prefs(context).getString(KEY_PROFILE_PREFIX + packageName, "").orEmpty()
        return stored.split(";#;").map(String::trim).filter(String::isNotBlank).toSet()
    }

    private fun writeProfileIds(context: Context, packageName: String, ids: Set<String>) {
        if (packageName.isBlank()) {
            return
        }
        prefs(context).edit {
            putString(KEY_PROFILE_PREFIX + packageName, ids.filter(String::isNotBlank).sorted().joinToString(";#;"))
        }
    }

    private fun readIncomingProfileIds(extras: Bundle?): Set<String> {
        if (extras == null) {
            return emptySet()
        }
        val ids = mutableSetOf<String>()
        extras.getString("profileId")?.takeIf { it.isNotBlank() }?.let(ids::add)
        extras.getStringArrayList("profileIds")?.filter(String::isNotBlank)?.let(ids::addAll)
        extras.getString("profileIds")
            ?.split(',', ';')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.let(ids::addAll)
        return ids
    }

    private fun computeChannelPermissions(channel: NotificationChannel): Int {
        var mask = 0
        if (channel.sound != null) mask = mask or 1
        if (channel.shouldVibrate()) mask = mask or 2
        if (channel.shouldShowLights()) mask = mask or 4
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && channel.canBypassDnd()) mask = mask or 8
        if (channel.canShowBadge()) mask = mask or 16
        return mask
    }

    private object UtilsContextHolder {
        lateinit var context: Context
    }
}
