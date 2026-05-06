package com.xiaomi.push.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.service.notification.StatusBarNotification
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.MIUIUtils
import io.github.aakira.napier.Napier

object NotificationIdentityBridge {
    private const val TAG = "NotificationIdentityBridge"
    private val logger = object {
        fun d(message: String) = Napier.d(message, tag = TAG)
        fun e(message: String, throwable: Throwable? = null) = Napier.e(message, throwable, tag = TAG)
    }

    @JvmField
    var isHooked = false

    enum class Strategy {
        FRAMEWORK,
        DELEGATED,
        UNSUPPORTED
    }

    private fun appContext(context: Context): Context = context.applicationContext

    private fun notificationManager(context: Context): NotificationManager =
        appContext(context).getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun isMiuiXmsfCompatEligible(context: Context, packageName: String): Boolean {
        val appContext = appContext(context)
        if (packageName == appContext.packageName) {
            return false
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            MIUIUtils.isMIUI() &&
            MIUIUtils.isXMSF(appContext) &&
            !MIUIUtils.isXMS()
    }

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
        if (isHooked) {
            return Strategy.FRAMEWORK
        }
        if (packageName == appContext(context).packageName) {
            return Strategy.UNSUPPORTED
        }
        if (isFrameworkIdentitySupported(context)) {
            return Strategy.FRAMEWORK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Be optimistic: if we are on Android 10+, try DELEGATED even if initial check fails.
            // This accommodates cases where LSPosed/Root might allow notifyAsPackage but not affect our probe.
            return Strategy.DELEGATED
        }
        return Strategy.UNSUPPORTED
    }

    fun shouldAttemptCompatTargetPost(context: Context, packageName: String): Boolean {
        return resolveStrategy(context, packageName) != Strategy.UNSUPPORTED ||
            isMiuiXmsfCompatEligible(context, packageName)
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
        val strategy = resolveStrategy(context, packageName)
        val channelId = notification.channelId
        val compatEligible = isMiuiXmsfCompatEligible(context, packageName)
        logger.d(
            "notifyAsTargetPackage attempt strategy=$strategy compatEligible=$compatEligible " +
                "pkg=$packageName tag=$tag id=$id channelId=$channelId"
        )
        return when (strategy) {
            Strategy.FRAMEWORK -> runCatching {
                NotificationManagerPlatformSupport.notify(packageName, id, notification)
                logger.d("notifyAsTargetPackage success strategy=$strategy pkg=$packageName id=$id channelId=$channelId")
                true
            }.onFailure {
                logger.e("notifyAsTargetPackage failed strategy=$strategy pkg=$packageName id=$id channelId=$channelId", it)
            }.getOrDefault(false)

            Strategy.DELEGATED -> runCatching {
                notificationManager(context).javaClass.getMethod(
                    "notifyAsPackage",
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Notification::class.java
                ).invoke(notificationManager(context), packageName, tag, id, notification)
                logger.d("notifyAsTargetPackage success strategy=$strategy pkg=$packageName id=$id channelId=$channelId")
                true
            }.onFailure {
                logger.e("notifyAsTargetPackage failed strategy=$strategy pkg=$packageName id=$id channelId=$channelId", it)
            }.getOrDefault(false)

            Strategy.UNSUPPORTED -> {
                if (!compatEligible) {
                    logger.d(
                        "notifyAsTargetPackage skipped strategy=$strategy compatEligible=$compatEligible " +
                            "pkg=$packageName id=$id channelId=$channelId"
                    )
                    false
                } else {
                    runCatching {
                        notificationManager(context).javaClass.getMethod(
                            "notifyAsPackage",
                            String::class.java,
                            String::class.java,
                            Int::class.javaPrimitiveType,
                            Notification::class.java
                        ).invoke(notificationManager(context), packageName, tag, id, notification)
                        logger.d("notifyAsTargetPackage success strategy=COMPAT pkg=$packageName id=$id channelId=$channelId")
                        true
                    }.onFailure {
                        logger.e("notifyAsTargetPackage failed strategy=COMPAT pkg=$packageName id=$id channelId=$channelId", it)
                    }.getOrDefault(false)
                }
            }
        }
    }

    fun dumpPostedNotificationSnapshot(
        context: Context,
        packageName: String,
        tag: String?,
        id: Int
    ): String {
        fun StatusBarNotification.describe(): String {
            val channelId = runCatching { notification.channelId }.getOrNull()
            val group = runCatching { notification.group }.getOrNull()
            val targetPkg = runCatching { NotificationUtils.getTargetPackage(notification) }.getOrNull()
            val xmsfTarget = runCatching { notification.extras?.getString("xmsf_target_package") }.getOrNull()
            val miuiTarget = runCatching { notification.extras?.getString("miui.targetPkg") }.getOrNull()
            return "pkg=${this.packageName} opPkg=${this.opPkg} id=${this.id} tag=${this.tag} channel=$channelId group=$group target=$targetPkg xmsfTarget=$xmsfTarget miuiTarget=$miuiTarget"
        }

        fun StatusBarNotification.matchesExactly(): Boolean {
            if (this.id != id) return false
            if (tag != this.tag) return false
            return true
        }

        fun StatusBarNotification.matchesRelatedTarget(): Boolean {
            return this.packageName == packageName ||
                this.opPkg == packageName ||
                NotificationUtils.getTargetPackage(this.notification) == packageName ||
                this.notification.extras?.getString("xmsf_target_package") == packageName
        }

        fun List<StatusBarNotification>.toSnapshotString(): String {
            return if (isEmpty()) "[]" else joinToString(prefix = "[", postfix = "]") { it.describe() }
        }

        val globalActive = runCatching {
            notificationManager(context).activeNotifications?.filterNotNull().orEmpty()
        }.getOrElse { emptyList() }

        val globalMatches = runCatching {
            globalActive.filter { it.matchesExactly() }.toSnapshotString()
        }.getOrElse { "${it.javaClass.simpleName}:${it.message}" }

        val relatedGlobal = runCatching {
            globalActive.filter { it.matchesRelatedTarget() }.take(8).toSnapshotString()
        }.getOrElse { "${it.javaClass.simpleName}:${it.message}" }

        return "global=$globalMatches relatedGlobal=$relatedGlobal"
    }

    fun cancelAsTargetPackage(
        context: Context,
        packageName: String,
        tag: String?,
        id: Int
    ): Boolean {
        val strategy = resolveStrategy(context, packageName)
        val compatEligible = isMiuiXmsfCompatEligible(context, packageName)
        logger.d(
            "cancelAsTargetPackage attempt strategy=$strategy compatEligible=$compatEligible " +
                "pkg=$packageName tag=$tag id=$id"
        )
        return when (strategy) {
            Strategy.FRAMEWORK -> runCatching {
                NotificationManagerPlatformSupport.cancel(packageName, id)
                logger.d("cancelAsTargetPackage success strategy=$strategy pkg=$packageName id=$id")
                true
            }.onFailure {
                logger.e("cancelAsTargetPackage failed strategy=$strategy pkg=$packageName id=$id", it)
            }.getOrDefault(false)

            Strategy.DELEGATED -> runCatching {
                notificationManager(context).javaClass.getMethod(
                    "cancelAsPackage",
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                ).invoke(notificationManager(context), packageName, tag, id)
                logger.d("cancelAsTargetPackage success strategy=$strategy pkg=$packageName id=$id")
                true
            }.onFailure {
                logger.e("cancelAsTargetPackage failed strategy=$strategy pkg=$packageName id=$id", it)
            }.getOrDefault(false)

            Strategy.UNSUPPORTED -> {
                if (!compatEligible) {
                    logger.d(
                        "cancelAsTargetPackage skipped strategy=$strategy compatEligible=$compatEligible " +
                            "pkg=$packageName id=$id"
                    )
                    false
                } else {
                    runCatching {
                        notificationManager(context).javaClass.getMethod(
                            "cancelAsPackage",
                            String::class.java,
                            String::class.java,
                            Int::class.javaPrimitiveType
                        ).invoke(notificationManager(context), packageName, tag, id)
                        logger.d("cancelAsTargetPackage success strategy=COMPAT pkg=$packageName id=$id")
                        true
                    }.onFailure {
                        logger.e("cancelAsTargetPackage failed strategy=COMPAT pkg=$packageName id=$id", it)
                    }.getOrDefault(false)
                }
            }
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
            append(" isHooked=").append(isHooked)
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
            append(" compatEligible=").append(describe { isMiuiXmsfCompatEligible(appContext, packageName) })
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
