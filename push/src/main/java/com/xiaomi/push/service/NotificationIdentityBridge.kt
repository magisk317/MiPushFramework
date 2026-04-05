package com.xiaomi.push.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.MIUIUtils

object NotificationIdentityBridge {
    enum class Strategy {
        FRAMEWORK,
        DELEGATED,
        UNSUPPORTED
    }

    private fun appContext(context: Context): Context = context.applicationContext

    private fun notificationManager(context: Context): NotificationManager =
        appContext(context).getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun callingUserId(context: Context): Int = runCatching {
        Context::class.java.getMethod("getUserId").invoke(appContext(context)) as? Int
    }.getOrNull() ?: 0

    private fun service(): Any? = runCatching {
        NotificationManager::class.java.getMethod("getService").invoke(null)
    }.getOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun <T> listFromParceledListSlice(slice: Any?): List<T> {
        if (slice == null) {
            return emptyList()
        }
        return runCatching {
            slice.javaClass.getMethod("getList").invoke(slice) as? List<T>
        }.getOrNull().orEmpty()
    }

    fun resolveStrategy(context: Context, packageName: String): Strategy {
        if (packageName == appContext(context).packageName) {
            return Strategy.UNSUPPORTED
        }
        if (isFrameworkIdentitySupported(context)) {
            return Strategy.FRAMEWORK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canNotifyAsPackage(context, packageName)) {
            return Strategy.DELEGATED
        }
        return Strategy.UNSUPPORTED
    }

    fun isFrameworkIdentitySupported(context: Context): Boolean = runCatching {
        NotificationManagerPlatformSupport.isRomSupportNotificationBelongToApp(appContext(context))
    }.getOrDefault(false)

    fun canNotifyAsPackage(context: Context, packageName: String): Boolean {
        if (packageName == appContext(context).packageName) {
            return true
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }
        return runCatching {
            notificationManager(context).javaClass
                .getMethod("canNotifyAsPackage", String::class.java)
                .invoke(notificationManager(context), packageName) as? Boolean ?: false
        }.getOrDefault(false)
    }

    fun canCreateTargetChannels(context: Context, packageName: String): Boolean {
        if (packageName == appContext(context).packageName) {
            return true
        }
        return resolveStrategy(context, packageName) == Strategy.FRAMEWORK
    }

    fun getTargetNotificationChannels(context: Context, packageName: String): List<NotificationChannel> {
        return when (resolveStrategy(context, packageName)) {
            Strategy.FRAMEWORK -> runCatching {
                NotificationManagerPlatformSupport.getNotificationChannels(packageName)
            }.getOrNull().orEmpty()

            Strategy.DELEGATED -> runCatching {
                val remoteService = service() ?: return@runCatching emptyList<NotificationChannel>()
                val channels = remoteService.javaClass.getMethod(
                    "getNotificationChannels",
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                ).invoke(remoteService, appContext(context).packageName, packageName, callingUserId(context))
                listFromParceledListSlice<NotificationChannel>(channels)
            }.getOrNull().orEmpty()

            Strategy.UNSUPPORTED -> emptyList()
        }
    }

    fun getTargetNotificationChannel(
        context: Context,
        packageName: String,
        channelId: String?
    ): NotificationChannel? {
        if (channelId.isNullOrEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null
        }
        return getTargetNotificationChannels(context, packageName).firstOrNull { it.id == channelId }
    }

    fun getPreferredTargetNotificationChannel(
        context: Context,
        packageName: String,
        preferredChannelId: String?
    ): NotificationChannel? {
        val channels = getTargetNotificationChannels(context, packageName)
            .filter { it.importance != NotificationManager.IMPORTANCE_NONE }
        if (channels.isEmpty()) {
            return null
        }
        if (!preferredChannelId.isNullOrEmpty()) {
            channels.firstOrNull { it.id == preferredChannelId }?.let { return it }
        }
        channels.firstOrNull { it.id == NotificationChannel.DEFAULT_CHANNEL_ID }?.let { return it }
        return channels.firstOrNull()
    }

    fun createTargetNotificationChannelGroups(
        context: Context,
        packageName: String,
        groups: List<NotificationChannelGroup?>
    ): Boolean {
        if (resolveStrategy(context, packageName) != Strategy.FRAMEWORK) {
            return false
        }
        return runCatching {
            groups.filterNotNull().forEach { group ->
                NotificationManagerPlatformSupport.createNotificationChannelGroup(packageName, group)
            }
            true
        }.getOrDefault(false)
    }

    fun createTargetNotificationChannels(
        context: Context,
        packageName: String,
        channels: List<NotificationChannel?>
    ): Boolean {
        if (resolveStrategy(context, packageName) != Strategy.FRAMEWORK) {
            return false
        }
        return runCatching {
            channels.filterNotNull().forEach { channel ->
                NotificationManagerPlatformSupport.createNotificationChannel(packageName, channel)
            }
            true
        }.getOrDefault(false)
    }

    fun notifyAsTargetPackage(
        context: Context,
        packageName: String,
        tag: String?,
        id: Int,
        notification: Notification
    ): Boolean {
        return when (resolveStrategy(context, packageName)) {
            Strategy.FRAMEWORK -> runCatching {
                NotificationManagerPlatformSupport.notify(packageName, id, notification)
                true
            }.getOrDefault(false)

            Strategy.DELEGATED -> runCatching {
                notificationManager(context).javaClass.getMethod(
                    "notifyAsPackage",
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Notification::class.java
                ).invoke(notificationManager(context), packageName, tag, id, notification)
                true
            }.getOrDefault(false)

            Strategy.UNSUPPORTED -> false
        }
    }

    fun cancelAsTargetPackage(
        context: Context,
        packageName: String,
        tag: String?,
        id: Int
    ): Boolean {
        return when (resolveStrategy(context, packageName)) {
            Strategy.FRAMEWORK -> runCatching {
                NotificationManagerPlatformSupport.cancel(packageName, id)
                true
            }.getOrDefault(false)

            Strategy.DELEGATED -> runCatching {
                notificationManager(context).javaClass.getMethod(
                    "cancelAsPackage",
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                ).invoke(notificationManager(context), packageName, tag, id)
                true
            }.getOrDefault(false)

            Strategy.UNSUPPORTED -> false
        }
    }

    fun dumpDiagnostics(
        context: Context,
        packageName: String,
        channelId: String?,
        groupId: String?
    ): String {
        val appContext = appContext(context)
        val localNotificationManager = notificationManager(appContext)
        val strategy = resolveStrategy(appContext, packageName)
        val targetChannels = runCatching { getTargetNotificationChannels(appContext, packageName).size }
            .getOrElse { -1 }
        val targetChannel = runCatching { getTargetNotificationChannel(appContext, packageName, channelId)?.id ?: "null" }
            .getOrElse { "${it.javaClass.simpleName}:${it.message}" }
        val canNotify = runCatching { canNotifyAsPackage(appContext, packageName) }
            .getOrElse { false }
        val packageContextProbe = describe {
            val packageContext = appContext.createPackageContext(packageName, 0)
            packageContext.packageName
        }
        val packageChannelProbe = describe {
            if (channelId.isNullOrEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                "n/a"
            } else {
                val packageContext = appContext.createPackageContext(packageName, 0)
                val packageManager = packageContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                packageManager.getNotificationChannel(channelId)?.id ?: "null"
            }
        }
        val localChannelProbe = describe {
            if (channelId.isNullOrEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                "n/a"
            } else {
                localNotificationManager.getNotificationChannel(channelId)?.id ?: "null"
            }
        }
        val frameworkSupport = describe { isFrameworkIdentitySupported(appContext) }
        val supportFwk = describe {
            NotificationManagerPlatformSupport.init(appContext)
            NotificationManagerPlatformSupport.isSupportFwk()
        }
        return buildString {
            append("identity-diagnostics")
            append(" strategy=").append(strategy)
            append(" sdk=").append(Build.VERSION.SDK_INT)
            append(" release=").append(Build.VERSION.RELEASE)
            append(" codename=").append(Build.VERSION.CODENAME)
            append(" brand=").append(Build.BRAND)
            append(" manufacturer=").append(Build.MANUFACTURER)
            append(" model=").append(Build.MODEL)
            append(" device=").append(Build.DEVICE)
            append(" product=").append(Build.PRODUCT)
            append(" fingerprint=").append(Build.FINGERPRINT)
            append(" appPkg=").append(appContext.packageName)
            append(" targetPkg=").append(packageName)
            append(" targetUid=").append(describe { NotificationManagerPlatformSupport.getPkgUid(packageName) })
            append(" userId=").append(callingUserId(appContext))
            append(" spaceId=").append(describe { DeviceInfo.getSpaceId() })
            append(" canNotifyAsPackage=").append(canNotify)
            append(" frameworkSupport=").append(frameworkSupport)
            append(" supportFwk=").append(supportFwk)
            append(" isMiui=").append(describe { MIUIUtils.isMIUI() })
            append(" miuiType=").append(describe { MIUIUtils.getMIUIType() })
            append(" miuiVersionCode=").append(describe { MIUIUtils.getMiuiVersionCode(appContext) })
            append(" miuiCountry=").append(describe { MIUIUtils.getCountryCode() })
            append(" isXms=").append(describe { MIUIUtils.isXMS() })
            append(" packageContext=").append(packageContextProbe)
            append(" packageChannel=").append(packageChannelProbe)
            append(" localChannel=").append(localChannelProbe)
            append(" targetChannels=").append(targetChannels)
            append(" targetChannel=").append(targetChannel)
            append(" channelId=").append(channelId)
            append(" groupId=").append(groupId)
        }
    }

    private fun describe(block: () -> Any?): String = try {
        block()?.toString() ?: "null"
    } catch (t: Throwable) {
        "${t.javaClass.simpleName}:${t.message}"
    }
}
