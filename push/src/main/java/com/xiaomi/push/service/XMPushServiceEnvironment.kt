package com.xiaomi.push.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.android.Region
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ThreadUtils
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.xmpush.thrift.ConfigKey
import java.util.Date

object XMPushServiceEnvironment {
    private const val EXTREME_POWER_MODE = "EXTREME_POWER_MODE_ENABLE"
    private const val SUPER_POWER_MODE = "power_supersave_mode_open"

    @JvmStatic
    fun canOpenForegroundService(service: XMPushService): Boolean {
        if (TextUtils.equals(service.packageName, PushConstants.PUSH_SERVICE_PACKAGE_NAME)) {
            return false
        }
        return OnlineConfig.getInstance(service).getBooleanValue(ConfigKey.ForegroundServiceSwitch.value, false)
    }

    @JvmStatic
    fun ensureRegionAvailable(service: XMPushService): String? {
        ThreadUtils.checkNotUIThread()
        var countryCode: String? = null
        val start = SystemClock.elapsedRealtime()
        val monitor = Any()
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME == service.packageName) {
            val provision = PushProvision.getInstance(service)
            while (TextUtils.isEmpty(countryCode) || provision.getProvisioned() == 0) {
                if (TextUtils.isEmpty(countryCode)) {
                    countryCode = MIUIUtils.getProperty("ro.miui.region")
                    if (TextUtils.isEmpty(countryCode)) {
                        countryCode = MIUIUtils.getProperty("ro.product.locale.region")
                    }
                }
                try {
                    synchronized(monitor) {
                        (monitor as java.lang.Object).wait(100L)
                    }
                } catch (_: InterruptedException) {
                }
            }
        } else {
            countryCode = MIUIUtils.getCountryCode()
        }

        var regionName: String? = null
        if (!TextUtils.isEmpty(countryCode)) {
            AppRegionStorage.getInstance(service.applicationContext).setCountryCode(countryCode)
            regionName = MIUIUtils.getRegion(countryCode).name
        }
        MyLog.w("wait region :$regionName cost = ${SystemClock.elapsedRealtime() - start}")
        return regionName
    }

    @JvmStatic
    fun getFalldownTimeRange(service: XMPushService): IntArray? {
        val range = OnlineConfig.getInstance(service.applicationContext)
            .getStringValue(ConfigKey.FallDownTimeRange.value, "") ?: ""
        val parts = range.split(",")
        if (range.isEmpty() || parts.size < 2) {
            return null
        }
        return try {
            val start = parts[0].toInt()
            val end = parts[1].toInt()
            if (start !in 0..23 || end !in 0..23 || start == end) {
                null
            } else {
                intArrayOf(start, end)
            }
        } catch (e: NumberFormatException) {
            MyLog.e("parse falldown time range failure: $e")
            null
        }
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun getPushServiceNotification(context: Context): Notification {
        val intent = Intent(context, XMPushService::class.java)
        if (Build.VERSION.SDK_INT >= 11) {
            return Notification.Builder(context)
                .setSmallIcon(context.applicationInfo.icon)
                .setContentTitle("Push Service")
                .setContentText("Push Service")
                .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE))
                .notification
        }
        val notification = Notification()
        try {
            notification.javaClass.getMethod(
                "setLatestEventInfo",
                Context::class.java,
                CharSequence::class.java,
                CharSequence::class.java,
                PendingIntent::class.java,
            ).invoke(
                notification,
                context,
                "Push Service",
                "Push Service",
                PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } catch (e: Exception) {
            MyLog.e(e)
        }
        return notification
    }

    @JvmStatic
    fun isExtremePowerSaveMode(service: XMPushService): Boolean {
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME != service.packageName) {
            return false
        }
        return Settings.Secure.getInt(service.contentResolver, EXTREME_POWER_MODE, 0) == 1
    }

    @JvmStatic
    fun isSuperPowerModeEnable(service: XMPushService): Boolean {
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME != service.packageName) {
            return false
        }
        return Settings.System.getInt(service.contentResolver, SUPER_POWER_MODE, 0) == 1
    }

    @JvmStatic
    fun isInFalldownTimeRange(falldownStart: Int, falldownEnd: Int): Boolean {
        val currentHour = String.format("%tH", Date()).toInt()
        return if (falldownStart > falldownEnd) {
            currentHour >= falldownStart || currentHour < falldownEnd
        } else {
            falldownStart < falldownEnd && currentHour in falldownStart until falldownEnd
        }
    }

    @JvmStatic
    fun shouldFalldown(service: XMPushService, falldownStart: Int, falldownEnd: Int): Boolean {
        return service.applicationContext.packageName == PushConstants.PUSH_SERVICE_PACKAGE_NAME &&
            isInFalldownTimeRange(falldownStart, falldownEnd) &&
            !DeviceInfo.isScreenOn(service) &&
            !DeviceInfo.isCharging(service.applicationContext)
    }

    @JvmStatic
    fun resolveXmppRegionHost(regionName: String?): String {
        return when (regionName) {
            Region.Global.name -> ConnectionConfiguration.XMPP_SERVER_GLOBAL_HOST_P
            Region.Europe.name -> ConnectionConfiguration.XMPP_SERVER_EUROPE_HOST_P
            Region.Russia.name -> ConnectionConfiguration.XMPP_SERVER_RUSSIA_HOST_P
            Region.India.name -> ConnectionConfiguration.XMPP_SERVER_INDIA_HOST_P
            else -> ConnectionConfiguration.XMPP_SERVER_CHINA_HOST_P
        }
    }
}
