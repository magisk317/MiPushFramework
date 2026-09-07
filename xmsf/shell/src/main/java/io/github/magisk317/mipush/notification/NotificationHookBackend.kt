package io.github.magisk317.mipush.notification

import android.annotation.SuppressLint
import android.app.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.common.ANDROID_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.utils.ImgUtils
import io.github.magisk317.mipush.common.notification.ChannelNameEnricher
import co.touchlab.kermit.Logger
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.platform.support.NotificationManagerReflection
import io.github.magisk317.mipush.platform.support.NotificationVendorAdapter
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.InvocationTargetException

private object BackendLog {
    fun d(tag: String, message: String?) = Logger.withTag(tag).d { message.orEmpty() }
    fun w(tag: String, message: String?) = Logger.withTag(tag).w { message.orEmpty() }
    fun e(tag: String, message: String?, throwable: Throwable?) =
        if (throwable == null) Logger.withTag(tag).e { message.orEmpty() }
        else Logger.withTag(tag).e(throwable) { message.orEmpty() }
}

object NotificationHookBackend {
    private const val TAG = "SystemNotificationManager"
    private val missingPackageWarnings = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    // 某些系统 API（如 getNotificationChannelsForPackage）需要 STATUS_BAR_SERVICE 权限，
    // 本进程 uid 稳定拿不到，每次调用都必然抛 SecurityException 再回退 root。
    // 记录列表逐行查询时这会造成大量无谓的失败反射调用；这里记住"该操作已被系统拒绝"，
    // 之后直接走 fallback，不再每行重试。按操作名区分（权限门槛与包无关）。
    private val blockedSystemOps = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    private lateinit var appContext: android.content.Context

    @JvmStatic
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    private sealed class UidResolution {
        data class Found(val uid: Int) : UidResolution()
        object MissingPackage : UidResolution()
        object Unavailable : UidResolution()
    }

    init {
        HiddenApiBypass.addHiddenApiExemptions("")
    }

    private val notificationManager: Any? by lazy {
        runCatching {
            JavaCalls.callStaticMethodOrThrow("android.app.NotificationManager", "getService")
        }.getOrNull()
    }

    private fun requireNotificationManager(): Any {
        return notificationManager ?: throw IllegalStateException("NotificationManager service not available")
    }

    private fun invokeNotificationManager(
        name: String,
        parameterTypes: Array<Class<*>>,
        vararg args: Any?,
    ): Any? {
        val service = requireNotificationManager()
        return NotificationManagerReflection.findMethod(
            service.javaClass,
            name,
            *parameterTypes,
        ).invoke(service, *args)
    }

    private fun getUid(packageName: String): Int {
        return appContext.packageManager.getPackageUid(packageName, 0)
    }

    private fun resolveUidState(packageName: String, operation: String): UidResolution {
        return try {
            UidResolution.Found(getUid(packageName))
        } catch (e: PackageManager.NameNotFoundException) {
            if (missingPackageWarnings.add("$operation|$packageName")) {
                BackendLog.w(TAG, "$operation: target package not installed, skip target system API for $packageName (${e.message})")
            }
            UidResolution.MissingPackage
        } catch (e: SecurityException) {
            BackendLog.e(TAG, "$operation: system API blocked for $packageName, will use root fallback", e)
            UidResolution.Unavailable
        }
    }

    private fun resolveUid(packageName: String, operation: String): Int? {
        return (resolveUidState(packageName, operation) as? UidResolution.Found)?.uid
    }

    private fun getUserId(): Int = Utils.myUserId().takeIf { it >= 0 }
        ?: throw IllegalStateException("Invalid current user id")

    private fun Throwable.unwrapSystemCallFailure(): Throwable {
        return when (this) {
            is InvocationTargetException -> targetException ?: cause ?: this
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
        return runCatching { block() }.fold(
            onSuccess = { it },
            onFailure = { throwable ->
                val cause = throwable.unwrapSystemCallFailure()
                if (cause is SecurityException) {
                    if (blockedSystemOps.add(operation)) {
                        BackendLog.w(TAG, "$operation: system API blocked for $packageName (memoized, will skip retries): ${cause.message}")
                    } else {
                        BackendLog.w(TAG, "$operation: system API blocked for $packageName: ${cause.message}")
                    }
                } else {
                    BackendLog.e(TAG, "$operation: system API failed for $packageName", cause)
                }
                fallback(cause)
            },
        )
    }

    private fun localNotificationManager(): NotificationManager? {
        return runCatching {
            appContext.getSystemService(NotificationManager::class.java)
        }.getOrNull()
    }

    private fun isCurrentPackage(packageName: String): Boolean {
        return runCatching {
            appContext.packageName == packageName
        }.getOrDefault(false)
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
            BackendLog.e(TAG, "Failed to create extras bundle", it)
        }.getOrNull()
    }

    private fun injectAppIcons(packageName: String, notification: Notification) {
        val colorMode = MiPushIslandPreferences.read(appContext).colorStatusBarIcon
        NotificationIconRenderingSupport.injectTargetAppIcons(
            appContext, packageName, notification, colorMode,
        )
    }

    private fun notifyLocally(tag: String?, id: Int, notification: Notification): Boolean {
        return runCatching {
            localNotificationManager()?.notify(tag, id, notification)
            true
        }.onFailure {
            BackendLog.e(TAG, "notify: local fallback failed", it)
        }.getOrDefault(false)
    }

    private fun cancelLocally(tag: String?, id: Int) {
        runCatching {
            localNotificationManager()?.cancel(tag, id)
        }.onFailure {
            BackendLog.e(TAG, "cancel: local fallback failed", it)
        }
    }

    private fun createChannelsLocally(channels: List<NotificationChannel>) {
        runCatching {
            localNotificationManager()?.createNotificationChannels(channels)
        }.onFailure {
            BackendLog.e(TAG, "createNotificationChannels: local fallback failed", it)
        }
    }

    private fun createGroupsLocally(groups: List<NotificationChannelGroup>) {
        runCatching {
            localNotificationManager()?.createNotificationChannelGroups(groups)
        }.onFailure {
            BackendLog.e(TAG, "createNotificationChannelGroups: local fallback failed", it)
        }
    }

    fun notify(
        packageName: String,
        tag: String?, id: Int, notification: Notification
    ): Boolean {
        BackendLog.d(TAG, "notify() pkg=$packageName tag=$tag id=$id channel=${notification.channelId} group=${notification.group}")

        try {
            ensureExtras(notification)

            val field = notification.javaClass.getDeclaredField("extraNotification")
            field.isAccessible = true
            val extraNotification = field.get(notification)
            if (extraNotification != null) {
                try {
                    val methodSetCustomizedIcon = extraNotification.javaClass.getDeclaredMethod(
                        "setCustomizedIcon",
                        Boolean::class.javaPrimitiveType,
                    )
                    methodSetCustomizedIcon.isAccessible = true
                    val colorStatusBarIcon = MiPushIslandPreferences.read(appContext).colorStatusBarIcon
                    methodSetCustomizedIcon.invoke(extraNotification, colorStatusBarIcon)
                    BackendLog.d(TAG, "Successfully set miui customized icon=$colorStatusBarIcon")
                } catch (_: NoSuchMethodException) {
                    BackendLog.d(TAG, "MIUI customized icon setter unavailable")
                } catch (error: ReflectiveOperationException) {
                    BackendLog.e(TAG, "Failed to set miui customized icon", error)
                } catch (error: SecurityException) {
                    BackendLog.e(TAG, "MIUI customized icon setter blocked", error)
                } catch (error: IllegalArgumentException) {
                    BackendLog.e(TAG, "MIUI customized icon setter rejected arguments", error)
                }

                try {
                    val methodSetTargetPkg = extraNotification.javaClass.getDeclaredMethod(
                        "setTargetPkg",
                        CharSequence::class.java,
                    )
                    methodSetTargetPkg.isAccessible = true
                    methodSetTargetPkg.invoke(extraNotification, packageName as CharSequence)
                    BackendLog.d(TAG, "Successfully set miui targetPkg to $packageName")
                } catch (_: NoSuchMethodException) {
                    BackendLog.d(TAG, "MIUI targetPkg setter unavailable")
                } catch (error: ReflectiveOperationException) {
                    BackendLog.e(TAG, "Failed to set targetPkg", error)
                } catch (error: SecurityException) {
                    BackendLog.e(TAG, "MIUI targetPkg setter blocked", error)
                } catch (error: IllegalArgumentException) {
                    BackendLog.e(TAG, "MIUI targetPkg setter rejected arguments", error)
                }
            } else {
                BackendLog.d(TAG, "MIUI extraNotification unavailable")
            }
        } catch (_: NoSuchFieldException) {
            BackendLog.d(TAG, "MIUI extraNotification field unavailable")
        } catch (error: ReflectiveOperationException) {
            BackendLog.e(TAG, "Failed to access MIUI notification extras", error)
        } catch (error: SecurityException) {
            BackendLog.e(TAG, "MIUI notification extras access blocked", error)
        } catch (error: IllegalArgumentException) {
            BackendLog.e(TAG, "MIUI notification extras rejected access", error)
        }

        injectAppIcons(packageName, notification)

        if (!isCurrentPackage(packageName)) {
            when (resolveUidState(packageName, "notify")) {
                is UidResolution.Found -> Unit
                UidResolution.MissingPackage -> {
                    BackendLog.d(TAG, "notify() package not installed, drop: $packageName")
                    return false
                }
                UidResolution.Unavailable -> Unit
            }
        }
        return runSystemCall("notify", packageName, fallback = {
            BackendLog.d(TAG, "notify() system call failed, falling back to local: $packageName id=$id")
            notifyLocally(tag, id, notification)
        }) {
            val methodEnqueueNotificationWithTag = NotificationManagerReflection.findMethod(
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
            BackendLog.d(TAG, "notify() enqueue OK pkg=$packageName id=$id")
            true
        }
    }

    fun cancel(
        packageName: String,
        tag: String?, id: Int
    ) {
        BackendLog.d(TAG, "cancel() pkg=$packageName tag=$tag id=$id")
        runSystemCall("cancel", packageName, fallback = {
            BackendLog.d(TAG, "cancel() system call failed, falling back to local: $packageName id=$id")
            cancelLocally(tag, id)
        }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val methodCancelNotificationWithTag = NotificationManagerReflection.findMethod(
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
                val methodCancelNotificationWithTag = NotificationManagerReflection.findMethod(
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
    ): Boolean {
        BackendLog.d(TAG, "createNotificationChannels() called with: packageName = $packageName, channels = $channels")
        val uid = when (val resolution = resolveUidState(packageName, "createNotificationChannels")) {
            is UidResolution.Found -> resolution.uid
            UidResolution.MissingPackage -> {
                BackendLog.d(TAG, "createNotificationChannels() package not installed, drop: $packageName")
                return false
            }
            UidResolution.Unavailable -> {
                createChannelsLocally(channels)
                return false
            }
        }
        return runSystemCall("createNotificationChannels", packageName, fallback = {
            createChannelsLocally(channels)
            false
        }) {
            val channelsList = NotificationManagerReflection.newParceledListSlice(channels)
            invokeNotificationManager(
                "createNotificationChannelsForPackage",
                arrayOf(String::class.java, Int::class.java, channelsList.javaClass),
                packageName,
                uid,
                channelsList,
            )
            val missing = channels.filter { getNotificationChannel(packageName, it.id) == null }
            if (missing.isNotEmpty()) {
                BackendLog.w(
                    TAG,
                    "createNotificationChannels() target verification failed pkg=$packageName " +
                        "missing=${missing.joinToString { it.id }}",
                )
                false
            } else {
                true
            }
        }
    }

    fun getNotificationChannel(
        packageName: String,
        channelId: String?
    ): NotificationChannel? {
        val uid = resolveUid(packageName, "getNotificationChannel")
        if (uid == null) {
            return NotificationRootFallback.getNotificationChannel(packageName, channelId)
                ?: if (isCurrentPackage(packageName) && !channelId.isNullOrEmpty()) {
                    localNotificationManager()?.getNotificationChannel(channelId)
                } else {
                    null
                }
        }
        return runSystemCall("getNotificationChannel", packageName, fallback = {
            NotificationRootFallback.getNotificationChannel(packageName, channelId, uid)
                ?: if (isCurrentPackage(packageName) && !channelId.isNullOrEmpty()) {
                    localNotificationManager()?.getNotificationChannel(channelId)
                } else {
                    null
                }
        }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                NotificationManagerReflection.findMethod(
                    requireNotificationManager().javaClass,
                    "getNotificationChannelForPackage",
                    String::class.java,
                    Int::class.java,
                    String::class.java,
                    String::class.java,
                    Boolean::class.java,
                ).invoke(requireNotificationManager(), packageName, uid, channelId, null, false) as NotificationChannel?
            } else {
                NotificationManagerReflection.findMethod(
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
        BackendLog.d(TAG, "getNotificationChannels() called with: packageName = $packageName hasNms=${notificationManager != null}")
        val uid = resolveUid(packageName, "getNotificationChannels")
        if (uid == null) {
            val root = NotificationRootFallback.getNotificationChannels(packageName, uid)
            BackendLog.d(TAG, "getNotificationChannels uid-null pkg=$packageName rootCount=${root?.size}")
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
                BackendLog.d(TAG, "getNotificationChannels fallback cached-block pkg=$packageName uid=$uid")
            } else {
                BackendLog.w(TAG, "getNotificationChannels fallback pkg=$packageName uid=$uid err=${error.message}")
            }
            val root = NotificationRootFallback.getNotificationChannels(packageName, uid)
            BackendLog.d(TAG, "getNotificationChannels root fallback count=${root?.size} pkg=$packageName")
            root
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.notificationChannels
                } else {
                    null
                }
        }) {
            val method = NotificationManagerReflection.findMethod(
                requireNotificationManager().javaClass,
                "getNotificationChannelsForPackage",
                String::class.java,
                Int::class.java,
                Boolean::class.java,
            )
            val parceledListSlice = method.invoke(requireNotificationManager(), packageName, uid, false)
            @Suppress("UNCHECKED_CAST")
            val list = JavaCalls.callMethodOrThrow(parceledListSlice, "getList") as List<NotificationChannel?>?
            BackendLog.d(TAG, "getNotificationChannels nms count=${list?.size} pkg=$packageName uid=$uid")
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
            NotificationRootFallback.getNotificationChannels(packageName, packageUid)
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
            BackendLog.d(TAG, "enrichChannelNamesFromRoot pkg=$packageName enriched=$enriched/${channels.size}")
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
        BackendLog.d(TAG, "deleteNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        runSystemCall("deleteNotificationChannel", packageName, fallback = {
            runCatching {
                if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.deleteNotificationChannel(channelId)
                }
            }.onFailure {
                BackendLog.e(TAG, "deleteNotificationChannel: local fallback failed", it)
            }
        }) {
            invokeNotificationManager(
                "deleteNotificationChannel",
                arrayOf(String::class.java, String::class.java),
                packageName,
                channelId,
            )
        }
    }


    fun createNotificationChannelGroups(
        packageName: String,
        groups: List<NotificationChannelGroup>
    ) {
        BackendLog.d(TAG, "createNotificationChannelGroups() called with: packageName = $packageName, groups = $groups")
        val uid = when (val resolution = resolveUidState(packageName, "createNotificationChannelGroups")) {
            is UidResolution.Found -> resolution.uid
            UidResolution.MissingPackage -> {
                BackendLog.d(TAG, "createNotificationChannelGroups() package not installed, drop: $packageName")
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
        // JavaCalls.callMethodOrThrow(requireNotificationManager(), "createNotificationChannelGroups", packageName, list)

        runSystemCall("createNotificationChannelGroups", packageName, fallback = {
            createGroupsLocally(groups)
        }) {
            groups.forEach {
                JavaCalls.setField(it, "mName", "Mi Push")

                // 无法 hook
                // void createNotificationChannelGroup(String pkg, int uid, NotificationChannelGroup group, boolean fromApp, boolean fromListener)
                // JavaCalls.callMethodOrThrow(requireNotificationManager(), "createNotificationChannelGroup", packageName, getUid(packageName), it, true, false)
                runCatching {
                    // void updateNotificationChannelGroupForPackage(String pkg, int uid, in NotificationChannelGroup group);
                    // 因 createNotificationChannelGroup 的 fromApp 为 false，首次创建会产生 NullPointerException
                    invokeNotificationManager(
                        "updateNotificationChannelGroupForPackage",
                        arrayOf(String::class.java, Int::class.java, NotificationChannelGroup::class.java),
                        packageName,
                        uid,
                        it,
                    )
                }.onFailure { throwable ->
                    val cause = throwable.unwrapSystemCallFailure()
                    if (cause is SecurityException) {
                        throw throwable
                    }
                    BackendLog.d(TAG, "Ignoring ROM-side channel group update failure: ${cause.message}")
                }
            }
        }
    }

    fun getNotificationChannelGroup(
        packageName: String,
        groupId: String
    ): NotificationChannelGroup? {
        BackendLog.d(TAG, "getNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        val uid = resolveUid(packageName, "getNotificationChannelGroup")
        if (uid == null) {
            return NotificationRootFallback.getNotificationChannelGroup(packageName, groupId)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.getNotificationChannelGroup(groupId)
                } else {
                    null
                }
        }
        return runSystemCall("getNotificationChannelGroup", packageName, fallback = {
            NotificationRootFallback.getNotificationChannelGroup(packageName, groupId, uid)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.getNotificationChannelGroup(groupId)
                } else {
                    null
                }
        }) {
            invokeNotificationManager(
                "getNotificationChannelGroupForPackage",
                arrayOf(String::class.java, String::class.java, Int::class.java),
                groupId,
                packageName,
                uid,
            ) as NotificationChannelGroup?
        }
    }

    fun getNotificationChannelGroups(
        packageName: String
    ): List<NotificationChannelGroup?>? {
        BackendLog.d(TAG, "getNotificationChannelGroups() called with: packageName = $packageName")
        val uid = resolveUid(packageName, "getNotificationChannelGroups")
        if (uid == null) {
            return NotificationRootFallback.getNotificationChannelGroups(packageName)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.notificationChannelGroups
                } else {
                    null
                }
        }
        return runSystemCall("getNotificationChannelGroups", packageName, fallback = {
            NotificationRootFallback.getNotificationChannelGroups(packageName, uid)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.notificationChannelGroups
                } else {
                    null
                }
        }) {
            val method = NotificationManagerReflection.findMethod(
                requireNotificationManager().javaClass,
                "getNotificationChannelGroupsForPackage",
                String::class.java,
                Int::class.java,
                Boolean::class.java,
            )
            val parceledListSlice = method.invoke(requireNotificationManager(), packageName, uid, false)
            @Suppress("UNCHECKED_CAST")
            JavaCalls.callMethodOrThrow(parceledListSlice, "getList") as List<NotificationChannelGroup?>?
        }
    }

    fun deleteNotificationChannelGroup(
        packageName: String,
        groupId: String
    ) {
        BackendLog.d(TAG, "deleteNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        runSystemCall("deleteNotificationChannelGroup", packageName, fallback = {
            runCatching {
                if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.deleteNotificationChannelGroup(groupId)
                }
            }.onFailure {
                BackendLog.e(TAG, "deleteNotificationChannelGroup: local fallback failed", it)
            }
        }) {
            invokeNotificationManager(
                "deleteNotificationChannelGroup",
                arrayOf(String::class.java, String::class.java),
                packageName,
                groupId,
            )
        }
    }

    fun areNotificationsEnabled(
        packageName: String
    ): Boolean {
        BackendLog.d(TAG, "areNotificationsEnabled() called with: packageName = $packageName")
        val uid = resolveUid(packageName, "areNotificationsEnabled")
        if (uid == null) {
            return NotificationRootFallback.areNotificationsEnabled(packageName)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.areNotificationsEnabled() ?: true
                } else {
                    true
                }
        }
        return runSystemCall("areNotificationsEnabled", packageName, fallback = {
            NotificationRootFallback.areNotificationsEnabled(packageName)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.areNotificationsEnabled() ?: true
                } else {
                    true
                }
        }) {
            invokeNotificationManager(
                "areNotificationsEnabledForPackage",
                arrayOf(String::class.java, Int::class.java),
                packageName,
                uid,
            ) as Boolean
        }
    }

    @Suppress("SwallowedException")
    fun getActiveNotifications(
        packageName: String
    ): Array<StatusBarNotification?>? {
        return runSystemCall("getActiveNotifications", packageName, fallback = {
            BackendLog.d(TAG, "getActiveNotifications() system call failed for $packageName, returning null")
            null
        }) {
            val userId = getUserId()
            val parceledListSlice = try {
                invokeNotificationManager(
                    "getAppActiveNotifications",
                    arrayOf(String::class.java, Int::class.java),
                    packageName,
                    userId,
                )
            } catch (error: NoSuchMethodException) {
                // Android 17/ROM variants may remove the hidden package-scoped method. Use the
                // host API only for this capability absence; keep both target marker and user
                // scope fences to prevent records from another delegated app or user leaking.
                val localNotifications = localNotificationManager()?.activeNotifications
                val filtered = if (localNotifications == null) {
                    emptyList()
                } else {
                    NotificationVendorAdapter.filterLocalActiveNotifications(
                        packageName,
                        localNotifications,
                        userId,
                    )
                }
                BackendLog.d(
                    TAG,
                    "getActiveNotifications() pkg=$packageName hidden API unavailable, " +
                        "using local fallback count=${filtered.size}",
                )
                return@runSystemCall filtered.map { it as StatusBarNotification? }.toTypedArray()
            }
            if (parceledListSlice == null) {
                BackendLog.d(TAG, "getActiveNotifications() pkg=$packageName returned null slice")
                return@runSystemCall null
            }
            val list = JavaCalls.callMethodOrThrow(parceledListSlice, "getList") as? List<*>
                ?: return@runSystemCall null
            val notifications = list.filterIsInstance<StatusBarNotification>()
            val ids = notifications.map { "${it.id}" }.joinToString(",")
            BackendLog.d(TAG, "getActiveNotifications() pkg=$packageName count=${notifications.size} ids=[$ids]")
            notifications.map { it as StatusBarNotification? }.toTypedArray()
        }
    }

}
