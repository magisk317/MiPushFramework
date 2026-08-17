package io.github.magisk317.mipush.hook.xmsf.nm

import android.annotation.SuppressLint
import android.app.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.common.ANDROID_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.utils.ImgUtils
import io.github.magisk317.mipush.common.notification.ChannelNameEnricher
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.xposed.callMethod
import io.github.magisk317.xposed.callStaticMethod
import io.github.magisk317.xposed.currentApplication
import io.github.magisk317.xposed.HookInvocationTargetError
import io.github.magisk317.xposed.findHookConstructorExact
import io.github.magisk317.xposed.findHookMethodExact
import io.github.magisk317.xposed.setHookObjectField
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.InvocationTargetException

object SystemNotificationManager {
    private const val TAG = "SystemNotificationManager"
    private const val EXTRA_LARGE_ICON = "android.largeIcon"
    private const val EXTRA_MIUI_APP_ICON = "miui.appIcon"
    private const val EXTRA_MIUI_OP_PKG = "miui.opPkg"
    private val missingPackageWarnings = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    // 某些系统 API（如 getNotificationChannelsForPackage）需要 STATUS_BAR_SERVICE 权限，
    // 本进程 uid 稳定拿不到，每次调用都必然抛 SecurityException 再回退 root。
    // 记录列表逐行查询时这会造成大量无谓的失败反射调用；这里记住"该操作已被系统拒绝"，
    // 之后直接走 fallback，不再每行重试。按操作名区分（权限门槛与包无关）。
    private val blockedSystemOps = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    private sealed class UidResolution {
        data class Found(val uid: Int) : UidResolution()
        object MissingPackage : UidResolution()
        object Unavailable : UidResolution()
    }

    init {
        HiddenApiBypass.addHiddenApiExemptions("")
    }

    private val notificationManager: Any? by lazy {
        runCatching { NotificationManager::class.java.callStaticMethod("getService") }.getOrNull()
    }

    private fun requireNotificationManager(): Any {
        return notificationManager ?: throw IllegalStateException("NotificationManager service not available")
    }

    private fun getUid(packageName: String): Int {
        val app = currentApplication() ?: throw PackageManager.NameNotFoundException("application not available")
        return app.packageManager.getPackageUid(packageName, 0)
    }

    private fun resolveUidState(packageName: String, operation: String): UidResolution {
        return try {
            UidResolution.Found(getUid(packageName))
        } catch (e: PackageManager.NameNotFoundException) {
            if (missingPackageWarnings.add("$operation|$packageName")) {
                XLog.w(TAG, "$operation: target package not installed, skip target system API for $packageName")
            }
            UidResolution.MissingPackage
        } catch (e: SecurityException) {
            XLog.e(TAG, "$operation: system API blocked for $packageName, will use root fallback", e)
            UidResolution.Unavailable
        }
    }

    private fun resolveUid(packageName: String, operation: String): Int? {
        return (resolveUidState(packageName, operation) as? UidResolution.Found)?.uid
    }

    private fun getUserId(): Int {
        return currentApplication()?.callMethod("getUserId") as Int? ?: 0
    }

    private fun Throwable.unwrapSystemCallFailure(): Throwable {
        return when (this) {
            is InvocationTargetException -> targetException ?: cause ?: this
            is HookInvocationTargetError -> cause ?: this
            else -> this
        }
    }

    private inline fun <T> runSystemCall(
        operation: String,
        packageName: String,
        fallback: (Throwable) -> T,
        block: () -> T
    ): T {
        // 该操作此前已被系统权限拒绝：直接走 fallback，不再发起注定失败的反射调用。
        if (blockedSystemOps.contains(operation)) {
            return fallback(SecurityException("$operation previously blocked, using fallback"))
        }
        return try {
            block()
        } catch (t: Throwable) {
            val cause = t.unwrapSystemCallFailure()
            if (cause is SecurityException) {
                if (blockedSystemOps.add(operation)) {
                    XLog.w(TAG, "$operation: system API blocked for $packageName (memoized, will skip retries): ${cause.message}")
                } else {
                    XLog.w(TAG, "$operation: system API blocked for $packageName: ${cause.message}")
                }
            } else {
                XLog.e(TAG, "$operation: system API failed for $packageName", cause)
            }
            fallback(cause)
        }
    }

    private fun localNotificationManager(): NotificationManager? {
        return runCatching {
            currentApplication()?.getSystemService(NotificationManager::class.java)
        }.getOrNull()
    }

    private fun isCurrentPackage(packageName: String): Boolean {
        return runCatching {
            currentApplication()?.packageName == packageName
        }.getOrDefault(false)
    }

    private fun createAppIconBitmap(
        packageManager: PackageManager,
        appInfo: ApplicationInfo,
    ): Bitmap? {
        return runCatching {
            ImgUtils.drawableToBitmap(appInfo.loadIcon(packageManager))
        }.onFailure {
            XLog.e(TAG, "Failed to create app icon bitmap", it)
        }.getOrNull()
    }

    private fun createUserBadgedAppIconBitmap(
        packageManager: PackageManager,
        appInfo: ApplicationInfo,
    ): Bitmap? {
        return runCatching {
            val rawIcon = appInfo.loadIcon(packageManager)
            val userHandle = resolveUserHandle(getUserId())
            val iconForUser = if (userHandle != null) {
                packageManager.getUserBadgedIcon(rawIcon, userHandle)
            } else {
                rawIcon
            }
            ImgUtils.drawableToBitmap(iconForUser)
        }.onFailure {
            XLog.e(TAG, "Failed to create user-badged app icon", it)
        }.getOrNull()
    }

    private fun resolveUserHandle(userId: Int): UserHandle? {
        return runCatching {
            val method = UserHandle::class.java.getDeclaredMethod("of", Integer.TYPE)
            method.isAccessible = true
            method.invoke(null, userId) as UserHandle
        }.onFailure {
            XLog.e(TAG, "Failed to resolve UserHandle for userId=$userId", it)
        }.getOrNull()
    }

    private fun hasLargeIcon(notification: Notification): Boolean {
        val reflectedLargeIcon = runCatching { notification.getLargeIcon() }.getOrNull()
        if (reflectedLargeIcon != null) return true
        return notification.extras?.containsKey(EXTRA_LARGE_ICON) == true
    }

    private fun ensureExtras(notification: Notification): Bundle? {
        notification.extras?.let { return it }
        return runCatching {
            val extras = Bundle()
            val field = Notification::class.java.getDeclaredField("extras")
            field.isAccessible = true
            field.set(notification, extras)
            extras
        }.onFailure {
            XLog.e(TAG, "Failed to create extras bundle", it)
        }.getOrNull()
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun injectAppIcons(packageName: String, notification: Notification) {
        val colorMode = IslandPreferences.current().colorStatusBarIcon
        XLog.d(TAG, "injectAppIcons pkg=$packageName colorStatusBarIcon=$colorMode")
        try {
            val pm = currentApplication()?.packageManager ?: return
            val appInfo = pm.getApplicationInfo(packageName, 0)
            if (appInfo.icon == 0) return

            val appIconBitmap = createAppIconBitmap(pm, appInfo)
            if (appIconBitmap != null) {
                notification.extras?.putParcelable(EXTRA_MIUI_APP_ICON, Icon.createWithBitmap(appIconBitmap))
                notification.extras?.putString(EXTRA_MIUI_OP_PKG, XMSF_PACKAGE_NAME)
                XLog.d(TAG, "Successfully injected MIUI custom app icon extras userId=${getUserId()}")
            }

            // Android 17 SystemUI accepts XMSF in config_canCustomNotificationAppIcon and uses
            // these extras for the header icon. In monochrome mode only publish the custom header
            // source; keep Notification.smallIcon for the status-bar monochrome hook.
            if (!colorMode) {
                XLog.d(TAG, "Kept original smallIcon and retained MIUI custom app icon extras userId=${getUserId()}")
                return
            }

            val fieldSmallIcon = Notification::class.java.getDeclaredField("mSmallIcon")
            fieldSmallIcon.isAccessible = true

            val badgedBitmap = createUserBadgedAppIconBitmap(pm, appInfo)
            if (badgedBitmap != null) {
                fieldSmallIcon.set(notification, Icon.createWithBitmap(badgedBitmap))
                XLog.d(TAG, "Successfully injected mSmallIcon with user-badged app icon userId=${getUserId()}")
                if (!hasLargeIcon(notification)) {
                    @Suppress("DEPRECATION")
                    notification.largeIcon = badgedBitmap
                    notification.extras?.putParcelable(EXTRA_LARGE_ICON, badgedBitmap)
                    XLog.d(TAG, "Successfully injected fallback largeIcon with user-badged app icon userId=${getUserId()}")
                }
            } else {
                fieldSmallIcon.set(notification, Icon.createWithResource(packageName, appInfo.icon))
                XLog.d(TAG, "Successfully injected mSmallIcon with app launcher icon")
            }
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to inject app icons", e)
        }
    }

    private fun notifyLocally(tag: String?, id: Int, notification: Notification): Boolean {
        return runCatching {
            localNotificationManager()?.notify(tag, id, notification)
            true
        }.onFailure {
            XLog.e(TAG, "notify: local fallback failed", it)
        }.getOrDefault(false)
    }

    private fun cancelLocally(tag: String?, id: Int) {
        runCatching {
            localNotificationManager()?.cancel(tag, id)
        }.onFailure {
            XLog.e(TAG, "cancel: local fallback failed", it)
        }
    }

    private fun createChannelsLocally(channels: List<NotificationChannel>) {
        runCatching {
            localNotificationManager()?.createNotificationChannels(channels)
        }.onFailure {
            XLog.e(TAG, "createNotificationChannels: local fallback failed", it)
        }
    }

    private fun createGroupsLocally(groups: List<NotificationChannelGroup>) {
        runCatching {
            localNotificationManager()?.createNotificationChannelGroups(groups)
        }.onFailure {
            XLog.e(TAG, "createNotificationChannelGroups: local fallback failed", it)
        }
    }

    fun notify(
        packageName: String,
        tag: String?, id: Int, notification: Notification
    ): Boolean {
        XLog.d(TAG, "notify() pkg=$packageName tag=$tag id=$id channel=${notification.channelId} group=${notification.group}")

        runCatching {
            ensureExtras(notification)

            val field = notification.javaClass.getDeclaredField("extraNotification")
            field.isAccessible = true
            val extraNotification = field.get(notification)
            if (extraNotification != null) {
                val methodSetCustomizedIcon = extraNotification.javaClass.getDeclaredMethod("setCustomizedIcon", Boolean::class.javaPrimitiveType)
                methodSetCustomizedIcon.isAccessible = true
                val colorStatusBarIcon = IslandPreferences.current().colorStatusBarIcon
                methodSetCustomizedIcon.invoke(extraNotification, colorStatusBarIcon)
                XLog.d(TAG, "Successfully set miui customized icon=$colorStatusBarIcon")

                try {
                    val methodSetTargetPkg = extraNotification.javaClass.getDeclaredMethod("setTargetPkg", CharSequence::class.java)
                    methodSetTargetPkg.isAccessible = true
                    methodSetTargetPkg.invoke(extraNotification, packageName as CharSequence)
                    XLog.d(TAG, "Successfully set miui targetPkg to $packageName")
                } catch (e: Exception) {
                    XLog.e(TAG, "Failed to set targetPkg", e)
                }
            } else {
                XLog.w(TAG, "extraNotification is null!")
            }
        }.onFailure {
            XLog.e(TAG, "Failed to set miui customized icon", it)
        }

        injectAppIcons(packageName, notification)

        if (!isCurrentPackage(packageName)) {
            when (resolveUidState(packageName, "notify")) {
                is UidResolution.Found -> Unit
                UidResolution.MissingPackage -> {
                    XLog.d(TAG, "notify() package not installed, drop: $packageName")
                    return false
                }
                UidResolution.Unavailable -> Unit
            }
        }
        return runSystemCall("notify", packageName, fallback = {
            XLog.d(TAG, "notify() system call failed, falling back to local: $packageName id=$id")
            notifyLocally(tag, id, notification)
        }) {
            val methodEnqueueNotificationWithTag = findHookMethodExact(
                requireNotificationManager().javaClass,
                "enqueueNotificationWithTag",
                String::class.java,
                String::class.java,
                String::class.java,
                Int::class.java,
                Notification::class.java,
                Int::class.java,
            )
            // Stock XMSF posts as a delegate: pkg is the target app while opPkg remains XMSF.
            // The system-server hook authorizes this pair without rewriting it to "android".
            val opPkg = XMSF_PACKAGE_NAME
            methodEnqueueNotificationWithTag.invoke(requireNotificationManager(), packageName, opPkg, tag, id, notification, getUserId())
            XLog.d(TAG, "notify() enqueue OK pkg=$packageName id=$id")
            true
        }
    }

    fun cancel(
        packageName: String,
        tag: String?, id: Int
    ) {
        XLog.d(TAG, "cancel() pkg=$packageName tag=$tag id=$id")
        runSystemCall("cancel", packageName, fallback = {
            XLog.d(TAG, "cancel() system call failed, falling back to local: $packageName id=$id")
            cancelLocally(tag, id)
        }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val methodCancelNotificationWithTag = findHookMethodExact(
                    requireNotificationManager().javaClass,
                    "cancelNotificationWithTag",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    Int::class.java,
                    Int::class.java,
                )
                methodCancelNotificationWithTag.invoke(requireNotificationManager(), packageName, ANDROID_PACKAGE_NAME, tag, id, getUserId())
            } else {
                val methodCancelNotificationWithTag = findHookMethodExact(
                    requireNotificationManager().javaClass,
                    "cancelNotificationWithTag",
                    String::class.java,
                    String::class.java,
                    Int::class.java,
                    Int::class.java,
                )
                methodCancelNotificationWithTag.invoke(requireNotificationManager(), packageName, tag, id, getUserId())
            }
        }
    }

    fun createNotificationChannels(
        packageName: String,
        channels: List<NotificationChannel>
    ) {
        XLog.d(TAG, "createNotificationChannels() called with: packageName = $packageName, channels = $channels")
        val uid = when (val resolution = resolveUidState(packageName, "createNotificationChannels")) {
            is UidResolution.Found -> resolution.uid
            UidResolution.MissingPackage -> {
                XLog.d(TAG, "createNotificationChannels() package not installed, drop: $packageName")
                return
            }
            UidResolution.Unavailable -> {
                createChannelsLocally(channels)
                return
            }
        }
        runSystemCall("createNotificationChannels", packageName, fallback = {
            createChannelsLocally(channels)
        }) {
            val channelsList = findHookConstructorExact("android.content.pm.ParceledListSlice", null, List::class.java)
                .newInstance(channels)
            requireNotificationManager().callMethod("createNotificationChannelsForPackage", packageName, uid, channelsList)
        }
    }

    fun getNotificationChannel(
        packageName: String,
        channelId: String?
    ): NotificationChannel? {
        XLog.d(TAG, "getNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        val uid = resolveUid(packageName, "getNotificationChannel")
        if (uid == null) {
            return RootNotificationHelper.getNotificationChannel(packageName, channelId)
                ?: if (isCurrentPackage(packageName) && !channelId.isNullOrEmpty()) {
                    localNotificationManager()?.getNotificationChannel(channelId)
                } else {
                    null
                }
        }
        return runSystemCall("getNotificationChannel", packageName, fallback = {
            RootNotificationHelper.getNotificationChannel(packageName, channelId, uid)
                ?: if (isCurrentPackage(packageName) && !channelId.isNullOrEmpty()) {
                    localNotificationManager()?.getNotificationChannel(channelId)
                } else {
                    null
                }
        }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                findHookMethodExact(
                    requireNotificationManager().javaClass,
                    "getNotificationChannelForPackage",
                    String::class.java,
                    Int::class.java,
                    String::class.java,
                    String::class.java,
                    Boolean::class.java,
                ).invoke(requireNotificationManager(), packageName, uid, channelId, null, false) as NotificationChannel?
            } else {
                findHookMethodExact(
                    requireNotificationManager().javaClass,
                    "getNotificationChannelForPackage",
                    String::class.java,
                    Int::class.java,
                    String::class.java,
                    Boolean::class.java,
                ).invoke(requireNotificationManager(), packageName, uid, channelId, false) as NotificationChannel?
            }
        }
    }

    fun getNotificationChannels(
        packageName: String
    ): List<NotificationChannel?>? {
        XLog.d(TAG, "getNotificationChannels() called with: packageName = $packageName hasNms=${notificationManager != null}")
        val uid = resolveUid(packageName, "getNotificationChannels")
        if (uid == null) {
            val root = RootNotificationHelper.getNotificationChannels(packageName, uid)
            XLog.d(TAG, "getNotificationChannels uid-null pkg=$packageName rootCount=${root?.size}")
            return root
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.notificationChannels
                } else {
                    null
                }
        }
        return runSystemCall("getNotificationChannels", packageName, fallback = { error ->
            // Previously-blocked path is expected on HyperOS without STATUS_BAR_SERVICE; keep it quiet.
            val previouslyBlocked = error.message?.contains("previously blocked") == true
            if (previouslyBlocked) {
                XLog.d(TAG, "getNotificationChannels fallback cached-block pkg=$packageName uid=$uid")
            } else {
                XLog.w(TAG, "getNotificationChannels fallback pkg=$packageName uid=$uid err=${error.message}")
            }
            val root = RootNotificationHelper.getNotificationChannels(packageName, uid)
            XLog.d(TAG, "getNotificationChannels root fallback count=${root?.size} pkg=$packageName")
            root
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.notificationChannels
                } else {
                    null
                }
        }) {
            val parceledListSlice = findHookMethodExact(requireNotificationManager().javaClass, "getNotificationChannelsForPackage", String::class.java, Int::class.java, Boolean::class.java)
                .invoke(requireNotificationManager(), packageName, uid, false)
            @Suppress("UNCHECKED_CAST")
            val list = parceledListSlice?.callMethod("getList") as List<NotificationChannel?>?
            XLog.d(TAG, "getNotificationChannels nms count=${list?.size} pkg=$packageName uid=$uid")
            // HyperOS may redact foreign-package channel names via NMS ("订阅今...").
            // Prefer full names from dumpsys when available.
            enrichChannelNamesFromRoot(packageName, uid, list)
        }
    }

    /**
     * HyperOS/NMS may return redacted channel names for cross-package queries (e.g. "订阅今...").
     * Merge full names parsed from root dumpsys by channel id when they look better.
     */
    private fun enrichChannelNamesFromRoot(
        packageName: String,
        packageUid: Int,
        channels: List<NotificationChannel?>?,
    ): List<NotificationChannel?>? {
        if (channels.isNullOrEmpty()) return channels
        val rootNames = runCatching {
            RootNotificationHelper.getNotificationChannels(packageName, packageUid)
                ?.filterNotNull()
                ?.associate { it.id to it.name?.toString().orEmpty() }
                .orEmpty()
        }.getOrDefault(emptyMap())
        if (rootNames.isEmpty()) return channels

        var enriched = 0
        val result = channels.map { channel ->
            if (channel == null) return@map null
            val fullName = ChannelNameEnricher.resolveName(packageName, channel.id, rootNames).orEmpty()
            if (fullName.isBlank()) return@map channel
            val current = channel.name?.toString().orEmpty()
            if (!shouldPreferRootChannelName(current, fullName)) return@map channel
            enriched++
            NotificationChannel(channel.id, fullName, channel.importance).apply {
                description = channel.description
                if (!channel.group.isNullOrBlank()) {
                    group = channel.group
                }
            }
        }
        if (enriched > 0) {
            XLog.d(TAG, "enrichChannelNamesFromRoot pkg=$packageName enriched=$enriched/${channels.size}")
        }
        return result
    }

    private fun shouldPreferRootChannelName(current: String, rootName: String): Boolean {
        if (rootName.isBlank() || rootName == current) return false
        if (looksEllipsizedChannelName(current)) return true
        return rootName.length > current.length
    }

    private fun looksEllipsizedChannelName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.endsWith("...") || trimmed.endsWith("…")
    }

    fun findPreferredTargetChannel(
        packageName: String,
        preferredChannelId: String?
    ): NotificationChannel? {
        if (preferredChannelId.isNullOrBlank()) {
            return null
        }
        val channels = getNotificationChannels(packageName)
            .orEmpty()
            .filterNotNull()
            .filter { it.importance != NotificationManager.IMPORTANCE_NONE }
        if (channels.isEmpty()) {
            return null
        }
        return channels.firstOrNull { it.id == preferredChannelId }
    }

    fun deleteNotificationChannel(
        packageName: String,
        channelId: String
    ) {
        XLog.d(TAG, "deleteNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        runSystemCall("deleteNotificationChannel", packageName, fallback = {
            runCatching {
                if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.deleteNotificationChannel(channelId)
                }
            }.onFailure {
                XLog.e(TAG, "deleteNotificationChannel: local fallback failed", it)
            }
        }) {
            requireNotificationManager().callMethod("deleteNotificationChannel", packageName, channelId)
        }
    }


    fun createNotificationChannelGroups(
        packageName: String,
        groups: List<NotificationChannelGroup>
    ) {
        XLog.d(TAG, "createNotificationChannelGroups() called with: packageName = $packageName, groups = $groups")
        val uid = when (val resolution = resolveUidState(packageName, "createNotificationChannelGroups")) {
            is UidResolution.Found -> resolution.uid
            UidResolution.MissingPackage -> {
                XLog.d(TAG, "createNotificationChannelGroups() package not installed, drop: $packageName")
                return
            }
            UidResolution.Unavailable -> {
                createGroupsLocally(groups)
                return
            }
        }

        // 无法指定 uid，调用成功也不会生效
        // void createNotificationChannelGroups(String pkg, in ParceledListSlice channelGroupList);
        // val list = findHookConstructorExact("android.content.pm.ParceledListSlice", null, List::class.java)
        //     .newInstance(groups)
        // requireNotificationManager().callMethod("createNotificationChannelGroups", packageName, list)

        runSystemCall("createNotificationChannelGroups", packageName, fallback = {
            createGroupsLocally(groups)
        }) {
            groups.forEach {
                io.github.magisk317.xposed.setHookObjectField(it, "mName", "Mi Push")

                // 无法 hook
                // void createNotificationChannelGroup(String pkg, int uid, NotificationChannelGroup group, boolean fromApp, boolean fromListener)
                // requireNotificationManager().callMethod("createNotificationChannelGroup", packageName, getUid(packageName), it, true, false)
                try {
                    // void updateNotificationChannelGroupForPackage(String pkg, int uid, in NotificationChannelGroup group);
                    // 因 createNotificationChannelGroup 的 fromApp 为 false，首次创建会产生 NullPointerException
                    requireNotificationManager().callMethod(
                        "updateNotificationChannelGroupForPackage",
                        packageName,
                        uid,
                        it
                    )
                } catch (e: Throwable) {
                    val cause = e.unwrapSystemCallFailure()
                    if (cause is SecurityException) {
                        throw e
                    }
                    // ignore
                    // Attempt to invoke virtual method 'boolean android.app.NotificationChannelGroup.isBlocked()' on a null object reference
                }
            }
        }
    }

    fun getNotificationChannelGroup(
        packageName: String,
        groupId: String
    ): NotificationChannelGroup? {
        XLog.d(TAG, "getNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        val uid = resolveUid(packageName, "getNotificationChannelGroup")
        if (uid == null) {
            return RootNotificationHelper.getNotificationChannelGroup(packageName, groupId)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.getNotificationChannelGroup(groupId)
                } else {
                    null
                }
        }
        return runSystemCall("getNotificationChannelGroup", packageName, fallback = {
            RootNotificationHelper.getNotificationChannelGroup(packageName, groupId, uid)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.getNotificationChannelGroup(groupId)
                } else {
                    null
                }
        }) {
            requireNotificationManager().callMethod("getNotificationChannelGroupForPackage", groupId, packageName, uid) as NotificationChannelGroup?
        }
    }

    fun getNotificationChannelGroups(
        packageName: String
    ): List<NotificationChannelGroup?>? {
        XLog.d(TAG, "getNotificationChannelGroups() called with: packageName = $packageName")
        val uid = resolveUid(packageName, "getNotificationChannelGroups")
        if (uid == null) {
            return RootNotificationHelper.getNotificationChannelGroups(packageName)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.notificationChannelGroups
                } else {
                    null
                }
        }
        return runSystemCall("getNotificationChannelGroups", packageName, fallback = {
            RootNotificationHelper.getNotificationChannelGroups(packageName, uid)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.notificationChannelGroups
                } else {
                    null
                }
        }) {
            val parceledListSlice = findHookMethodExact(requireNotificationManager().javaClass, "getNotificationChannelGroupsForPackage", String::class.java, Int::class.java, Boolean::class.java)
                .invoke(requireNotificationManager(), packageName, uid, false)
            @Suppress("UNCHECKED_CAST")
            parceledListSlice?.callMethod("getList") as List<NotificationChannelGroup?>?
        }
    }

    fun deleteNotificationChannelGroup(
        packageName: String,
        groupId: String
    ) {
        XLog.d(TAG, "deleteNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        runSystemCall("deleteNotificationChannelGroup", packageName, fallback = {
            runCatching {
                if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.deleteNotificationChannelGroup(groupId)
                }
            }.onFailure {
                XLog.e(TAG, "deleteNotificationChannelGroup: local fallback failed", it)
            }
        }) {
            requireNotificationManager().callMethod("deleteNotificationChannelGroup", packageName, groupId)
        }
    }

    fun areNotificationsEnabled(
        packageName: String
    ): Boolean {
        XLog.d(TAG, "areNotificationsEnabled() called with: packageName = $packageName")
        val uid = resolveUid(packageName, "areNotificationsEnabled")
        if (uid == null) {
            return RootNotificationHelper.areNotificationsEnabled(packageName)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.areNotificationsEnabled() ?: true
                } else {
                    true
                }
        }
        return runSystemCall("areNotificationsEnabled", packageName, fallback = {
            RootNotificationHelper.areNotificationsEnabled(packageName)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.areNotificationsEnabled() ?: true
                } else {
                    true
                }
        }) {
            requireNotificationManager().callMethod("areNotificationsEnabledForPackage", packageName, uid) as Boolean
        }
    }

    fun getActiveNotifications(
        packageName: String
    ): Array<StatusBarNotification?>? {
        return runSystemCall("getActiveNotifications", packageName, fallback = {
            XLog.d(TAG, "getActiveNotifications() system call failed for $packageName, returning null")
            null
        }) {
            val parceledListSlice = requireNotificationManager().callMethod("getAppActiveNotifications", packageName, getUserId())
            @Suppress("UNCHECKED_CAST")
            val list = parceledListSlice?.callMethod("getList") as List<StatusBarNotification>
            val ids = list.map { "${it.id}" }.joinToString(",")
            XLog.d(TAG, "getActiveNotifications() pkg=$packageName count=${list.size} ids=[$ids]")
            list.toTypedArray()
        }
    }

}
