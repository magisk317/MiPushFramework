package com.xiaomi.xmsf.stock

import android.content.Context
import android.os.Bundle
import androidx.core.content.edit
import android.database.MatrixCursor
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ConfigKey
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.store.DatabaseUtils

object StockSurfaceSupport {
    const val KEY_CODE = "code"
    const val KEY_DATA = "data"
    const val KEY_MSG = "msg"

    private const val PREFS_NAME = "stock_surface"
    private const val KEY_STAT_EVENTS = "stat_events"
    private const val MAX_STAT_EVENTS = 32

    private const val SUPPORT_FLAG_PROFILE_ID = 4
    private const val SUPPORT_FLAG_EXTENSION_NOTIFICATION = 262144
    private const val SUPPORT_FLAG_CALLKIT = 524288

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
                putBundle(KEY_DATA, data)
            }
            if (!message.isNullOrBlank()) {
                putString(KEY_MSG, message)
            }
        }
    }

    @JvmStatic
    fun handlePushCommonCall(method: String?, extras: Bundle?): Bundle {
        return when (method) {
            "is_push_support" -> Bundle().apply {
                // Stock XMSF 7.4.67-C z9.b treats missing, invalid, and malformed flags as false.
                // The older provider rejected their whole call with an invented msg field.
                val flag = runCatching { extras?.getInt("push_support_flag", 0) ?: 0 }.getOrDefault(0)
                putBoolean("is_supported", isPushSupport(flag))
            }
            // Stock PushCommonProvider returns an empty Bundle for unknown methods.
            else -> Bundle()
        }
    }

    @JvmStatic
    fun handlePushSupportCall(
        context: Context,
        callingPackage: String?,
        method: String?,
        extras: Bundle?,
    ): Bundle {
        ensureInitialized(context)
        return StockPushSupport.handle(context, callingPackage, method, extras)
    }

    @JvmStatic
    fun handleProfileCall(
        context: Context,
        method: String?,
        extras: Bundle?,
        callingPackage: String?,
    ): Bundle = StockProfileIdStore.handle(context, method, extras, callingPackage)

    @JvmStatic
    fun isProfileAllowed(context: Context, container: XmPushActionContainer): Boolean =
        StockProfileIdStore.isAllowed(context.applicationContext, container)

    @JvmStatic
    fun handleChannelCall(
        context: Context,
        callingPackage: String?,
        method: String?,
        extras: Bundle?,
    ): Bundle {
        ensureInitialized(context)
        return StockChannelSupport.handle(context, callingPackage, method, extras)
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
            controlMode = onlineConfig.getIntValue(ConfigKey.LimitThridPushStrategyMode.value, -1),
            keyWords = onlineConfig.getStringValue(ConfigKey.ThirdPushComponentKeyWords.value, "") ?: "",
            specialPackageNames = onlineConfig.getStringValue(ConfigKey.ThirdPushWhiteList.value, "") ?: "",
            controlSwitch = onlineConfig.getBooleanValue(ConfigKey.ThirdPushControlSwitch.value, false),
        )
    }

    internal fun pushControlConfigKeys(): List<Int> = listOf(
        ConfigKey.LimitThridPushStrategyMode.value,
        ConfigKey.ThirdPushComponentKeyWords.value,
        ConfigKey.ThirdPushWhiteList.value,
        ConfigKey.ThirdPushControlSwitch.value,
    )

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
    fun ensureInitialized(context: Context) {
        DatabaseUtils.init(context.applicationContext)
        NotificationManagerEx.init(context.applicationContext)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun isPushSupport(flag: Int): Boolean {
        return when (flag) {
            SUPPORT_FLAG_PROFILE_ID -> true
            SUPPORT_FLAG_EXTENSION_NOTIFICATION -> false
            // The stock CallKit plugin/whitelist and callback uploader are not packaged. Do not
            // advertise the flag merely because a caller package happens to be installed.
            SUPPORT_FLAG_CALLKIT -> false
            else -> false
        }
    }

}
