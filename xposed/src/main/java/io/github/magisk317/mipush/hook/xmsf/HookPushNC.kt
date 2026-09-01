package io.github.magisk317.mipush.hook.xmsf

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.system.HookSystemService
import io.github.magisk317.xposed.HookClassNotFoundError
import io.github.magisk317.xposed.HookInvocationTargetError
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.getOrNull
import io.github.magisk317.xposed.hookMethod
import io.github.magisk317.xposed.set
import io.github.magisk317.xposed.logging.MagiskOtel
import java.lang.reflect.InvocationTargetException

object HookPushNC {
    private const val TAG = "HookPushNC"
    private const val ExpectedHookApiVersion = 2

    private const val TargetClass = "io.github.magisk317.mipush.notification.NotificationManagerEx"
    private const val RuntimeNotificationBridgeClass = "io.github.magisk317.mipush.notification.NotificationHookBridge"
    private const val IdentityBridgeClass = "com.xiaomi.push.service.NotificationIdentityBridge"
    private const val IdentityStrategyClass = "com.xiaomi.push.service.NotificationIdentityBridge\$Strategy"
    private const val READY_RETRY_DELAY_MS = 250L
    // XMSF may start before system_server publishes NotificationManagerService. Keep polling
    // long enough to cover slow boots instead of permanently pinning identity ownership false.
    private const val MAX_READY_RETRY_ATTEMPTS = 120

    private val readyRetryLock = Any()
    private var readyRetryHandler: Handler? = null
    private var readyRetryTask: Runnable? = null
    private var readyRetryAttempt = 0

    private val hookCheck = { HookSystemService.isSystemHookReady }

    fun canHook(classLoader: ClassLoader): Boolean {
        return try {
            classLoader.findClass(TargetClass)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun hook(classLoader: ClassLoader) {
        XLog.d(TAG, "hookPushNC() called with: classLoader = $classLoader")

        val classNotificationManager = classLoader.findClass(TargetClass)
        val runtimeBridge = RuntimeNotificationBridge.create(classLoader) ?: run {
            XLog.e(TAG, "XMSF notification bridge unavailable; skip notification hook installation", null)
            return
        }
        val managerHookApiVersion = runCatching {
            classNotificationManager.getOrNull<Int>("HOOK_API_VERSION") ?: 0
        }.getOrDefault(0)
        if (managerHookApiVersion != ExpectedHookApiVersion) {
            XLog.e(
                TAG,
                "NotificationManagerEx hook api mismatch: expected=$ExpectedHookApiVersion actual=$managerHookApiVersion",
                null
            )
            MagiskOtel.event(
                name = "notify.intercept",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "hook_install",
                    "reason" to "api_mismatch",
                ),
                statusOk = false,
            )
            return
        }

        //notify(
        //        packageName: String,
        //        tag: String?, id: Int, notification: Notification, userId: Int
        //    ): Boolean
        classNotificationManager.hookMethod(
            "notify",
            String::class.java,
            String::class.java,
            Int::class.java,
            Notification::class.java,
            Int::class.java,
        ) {
            replace(hookCheck) {
                tryInvoke {
                    runtimeBridge.notify(
                        args[0] as String,
                        args[1] as String?,
                        args[2] as Int,
                        args[3] as Notification
                    )
                }
            }
        }

        //cancel(
        //        packageName: String,
        //        tag: String?, id: Int, userId: Int
        //    )
        classNotificationManager.hookMethod(
            "cancel",
            String::class.java,
            String::class.java,
            Int::class.java,
            Int::class.java,
        ) {
            replace(hookCheck) {
                tryInvoke {
                    runtimeBridge.cancel(
                        args[0] as String,
                        args[1] as String?,
                        args[2] as Int
                    )
                }
            }
        }

        //createNotificationChannels(
        //        packageName: String,
        //        channels: List<NotificationChannel?>
        //    )
        classNotificationManager.hookMethod(
            "createNotificationChannels",
            String::class.java,
            List::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    @Suppress("UNCHECKED_CAST")
                    return@replace runtimeBridge.createNotificationChannels(
                        args[0] as String,
                        args[1] as List<NotificationChannel>
                    )
                }
            }
        }

        //getNotificationChannel(
        //        packageName: String,
        //        channelId: String?
        //    ): NotificationChannel?
        classNotificationManager.hookMethod(
            "getNotificationChannel",
            String::class.java,
            String::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    return@replace runtimeBridge.getNotificationChannel(
                        args[0] as String,
                        args[1] as String
                    )
                }
            }
        }

        //getNotificationChannels(
        //        packageName: String
        //    ): List<NotificationChannel?>?
        classNotificationManager.hookMethod("getNotificationChannels", String::class.java) {
            // Always take over listing: system-hook readiness is not required for NMS/root fallbacks.
            replace {
                tryInvoke {
                    val channels = runtimeBridge.getNotificationChannels(args[0] as String)
                    XLog.d(TAG, "hook getNotificationChannels pkg=${args[0]} count=${channels?.size}")
                    return@replace channels
                }
            }
        }

        //deleteNotificationChannel(
        //        packageName: String,
        //        channelId: String?
        //    )
        classNotificationManager.hookMethod(
            "deleteNotificationChannel",
            String::class.java,
            String::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    runtimeBridge.deleteNotificationChannel(
                        args[0] as String,
                        args[1] as String
                    )
                }
            }
        }

        //createNotificationChannelGroups(
        //        packageName: String,
        //        groups: List<NotificationChannelGroup?>
        //    )
        classNotificationManager.hookMethod(
            "createNotificationChannelGroups",
            String::class.java,
            List::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    @Suppress("UNCHECKED_CAST")
                    runtimeBridge.createNotificationChannelGroups(
                        args[0] as String,
                        args[1] as List<NotificationChannelGroup>
                    )
                }
            }
        }

        //getNotificationChannelGroup(
        //        packageName: String,
        //        groupId: String?
        //    ): NotificationChannelGroup?
        runCatching {
            classNotificationManager.hookMethod(
                "getNotificationChannelGroup",
                String::class.java,
                String::class.java
            ) {
                replace(hookCheck) {
                    tryInvoke {
                        return@replace runtimeBridge.getNotificationChannelGroup(
                            args[0] as String,
                            args[1] as String
                        )
                    }
                }
            }
        }.onFailure {
            XLog.e(TAG, "skip getNotificationChannelGroup hook", it)
        }

        //getNotificationChannelGroups(
        //        packageName: String
        //    ): List<NotificationChannelGroup?>?
        classNotificationManager.hookMethod("getNotificationChannelGroups", String::class.java) {
            replace {
                tryInvoke {
                    val groups = runtimeBridge.getNotificationChannelGroups(args[0] as String)
                    XLog.d(TAG, "hook getNotificationChannelGroups pkg=${args[0]} count=${groups?.size}")
                    return@replace groups
                }
            }
        }

        //deleteNotificationChannelGroup(
        //        packageName: String,
        //        groupId: String?
        //    )
        classNotificationManager.hookMethod(
            "deleteNotificationChannelGroup",
            String::class.java,
            String::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    runtimeBridge.deleteNotificationChannelGroup(
                        args[0] as String,
                        args[1] as String
                    )
                }
            }
        }

        //areNotificationsEnabled(
        //        packageName: String
        //    ): Boolean
        classNotificationManager.hookMethod("areNotificationsEnabled", String::class.java) {
            replace(hookCheck) {
                tryInvoke {
                    return@replace runtimeBridge.areNotificationsEnabled(args[0] as String)
                }
            }
        }

        //getActiveNotifications(
        //        packageName: String
        //    ): Array<StatusBarNotification?>?
        classNotificationManager.hookMethod("getActiveNotifications", String::class.java) {
            replace(hookCheck) {
                tryInvoke {
                    return@replace runtimeBridge.getActiveNotifications(args[0] as String)
                }
            }
        }

        classNotificationManager.hookMethod("supportsTargetChannelProvisioning", String::class.java) {
            replace(hookCheck) {
                true
            }
        }

        classNotificationManager.hookMethod(
            "findPreferredTargetChannel",
            String::class.java,
            String::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    return@replace runtimeBridge.findPreferredTargetChannel(
                        args[0] as String,
                        args[1] as String?
                    )
                }
            }
        }

        val identityBridgeClass = hookIdentityBridge(classLoader, runtimeBridge)
        if (identityBridgeClass != null) {
            if (HookSystemService.isSystemHookReady) {
                markIdentityBridgeReady(classNotificationManager, identityBridgeClass)
            } else {
                // The app-side hooks may install before system_server is ready. Do not advertise
                // target-identity ownership yet, but keep probing so a transient boot race does
                // not leave this process permanently on the unsafe local fallback path.
                scheduleIdentityBridgeReady(classNotificationManager, identityBridgeClass)
                XLog.w(TAG, "identity bridge installed before system_server ready; keeping bridge ownership disabled")
            }
        } else {
            XLog.w(TAG, "identity bridge hooks unavailable; keeping NotificationManagerEx.isHooked = false")
        }
        XLog.i(TAG, "host notification takeover hooks installed")
        MagiskOtel.event(
            name = "notify.intercept",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "hook_install",
                "reason" to "installed",
            ),
            statusOk = true,
        )
    }

    private fun hookIdentityBridge(classLoader: ClassLoader, runtimeBridge: RuntimeNotificationBridge): Class<*>? {
        val identityBridgeClass = runCatching { classLoader.findClass(IdentityBridgeClass) }
            .getOrElse {
                XLog.d(TAG, "identity bridge class not found, skip")
                return null
            }
        val bridgeHookApiVersion = runCatching {
            identityBridgeClass.getOrNull<Int>("HOOK_API_VERSION") ?: 0
        }.getOrDefault(0)
        if (bridgeHookApiVersion != ExpectedHookApiVersion) {
            XLog.e(
                TAG,
                "NotificationIdentityBridge hook api mismatch: expected=$ExpectedHookApiVersion actual=$bridgeHookApiVersion",
                null
            )
            return null
        }
        val identityStrategyClass = runCatching { classLoader.findClass(IdentityStrategyClass) }
            .getOrElse {
                XLog.d(TAG, "identity strategy enum not found, skip")
                return null
            }

        val frameworkStrategy = runCatching {
            @Suppress("UNCHECKED_CAST")
            java.lang.Enum.valueOf(identityStrategyClass as Class<out Enum<*>>, "FRAMEWORK")
        }.getOrElse {
            XLog.e(TAG, "resolve FRAMEWORK strategy failed", it)
            return null
        }
        XLog.i(TAG, "installing identity bridge hooks")

        identityBridgeClass.hookMethod("isFrameworkIdentitySupported", Context::class.java) {
            replace(hookCheck) { true }
        }

        identityBridgeClass.hookMethod("canNotifyAsPackage", Context::class.java, String::class.java) {
            replace(hookCheck) { true }
        }

        identityBridgeClass.hookMethod("canCreateTargetChannels", Context::class.java, String::class.java) {
            replace(hookCheck) { true }
        }

        identityBridgeClass.hookMethod("resolveStrategy", Context::class.java, String::class.java) {
            replace(hookCheck) { frameworkStrategy }
        }

        identityBridgeClass.hookMethod("getTargetNotificationChannels", Context::class.java, String::class.java) {
            replace {
                tryInvoke {
                    val channels = runtimeBridge.getNotificationChannels(args[1] as String)
                    XLog.d(TAG, "hook getTargetNotificationChannels pkg=${args[1]} count=${channels?.size}")
                    return@replace channels.orEmpty()
                }
            }
        }

        identityBridgeClass.hookMethod("getTargetNotificationChannelGroups", Context::class.java, String::class.java) {
            replace {
                tryInvoke {
                    val groups = runtimeBridge.getNotificationChannelGroups(args[1] as String)
                    XLog.d(TAG, "hook getTargetNotificationChannelGroups pkg=${args[1]} count=${groups?.size}")
                    return@replace groups.orEmpty()
                }
            }
        }

        identityBridgeClass.hookMethod("getTargetNotificationChannel", Context::class.java, String::class.java, String::class.java) {
            replace(hookCheck) {
                tryInvoke {
                    return@replace runtimeBridge.getNotificationChannel(
                        args[1] as String,
                        args[2] as String
                    )
                }
            }
        }

        identityBridgeClass.hookMethod(
            "getPreferredTargetNotificationChannel",
            Context::class.java,
            String::class.java,
            String::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    return@replace runtimeBridge.findPreferredTargetChannel(
                        args[1] as String,
                        args[2] as String?
                    )
                }
            }
        }

        identityBridgeClass.hookMethod("createTargetNotificationChannelGroups", Context::class.java, String::class.java, List::class.java) {
            replace(hookCheck) {
                tryInvoke {
                    @Suppress("UNCHECKED_CAST")
                    runtimeBridge.createNotificationChannelGroups(
                        args[1] as String,
                        args[2] as List<NotificationChannelGroup>
                    )
                    return@replace true
                }
            }
        }

        identityBridgeClass.hookMethod("createTargetNotificationChannels", Context::class.java, String::class.java, List::class.java) {
            replace(hookCheck) {
                tryInvoke {
                    @Suppress("UNCHECKED_CAST")
                    return@replace runtimeBridge.createNotificationChannels(
                        args[1] as String,
                        args[2] as List<NotificationChannel>
                    )
                }
            }
        }

        identityBridgeClass.hookMethod(
            "notifyAsTargetPackage",
            Context::class.java,
            String::class.java,
            String::class.java,
            Int::class.java,
            Notification::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    runtimeBridge.notify(
                        args[1] as String,
                        args[2] as String?,
                        args[3] as Int,
                        args[4] as Notification
                    )
                }
            }
        }

        identityBridgeClass.hookMethod(
            "cancelAsTargetPackage",
            Context::class.java,
            String::class.java,
            String::class.java,
            Int::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    runtimeBridge.cancel(
                        args[1] as String,
                        args[2] as String?,
                        args[3] as Int
                    )
                    return@replace true
                }
            }
        }
        XLog.i(TAG, "identity bridge hooks installed")
        return identityBridgeClass
    }

    private fun markIdentityBridgeReady(
        notificationManagerClass: Class<*>,
        identityBridgeClass: Class<*>,
    ): Boolean = runCatching {
        identityBridgeClass["isHooked"] = true
        notificationManagerClass["isHooked"] = true
        XLog.i(TAG, "marked identity bridge and NotificationManagerEx as ready")
        true
    }.onFailure {
        XLog.e(TAG, "failed to mark identity bridge ready", it)
    }.getOrDefault(false)

    private fun scheduleIdentityBridgeReady(
        notificationManagerClass: Class<*>,
        identityBridgeClass: Class<*>,
    ) {
        synchronized(readyRetryLock) {
            if (readyRetryTask != null) return
            val handler = readyRetryHandler ?: Handler(Looper.getMainLooper()).also {
                readyRetryHandler = it
            }
            readyRetryAttempt = 0
            val task = object : Runnable {
                override fun run() {
                    val attempt = synchronized(readyRetryLock) { readyRetryAttempt }
                    if (HookSystemService.isSystemHookReady) {
                        if (markIdentityBridgeReady(notificationManagerClass, identityBridgeClass)) {
                            synchronized(readyRetryLock) {
                                if (readyRetryTask === this) {
                                    readyRetryTask = null
                                    readyRetryAttempt = 0
                                }
                            }
                            return
                        }
                        XLog.w(TAG, "system_server ready probe succeeded but identity bridge mark failed; retrying")
                    }
                    if (!shouldRetrySystemHookReady(attempt)) {
                        synchronized(readyRetryLock) {
                            if (readyRetryTask === this) {
                                readyRetryTask = null
                                readyRetryAttempt = 0
                            }
                        }
                        XLog.w(TAG, "system_server ready probe exhausted; identity bridge remains disabled")
                        return
                    }
                    synchronized(readyRetryLock) {
                        readyRetryAttempt = attempt + 1
                    }
                    handler.postDelayed(this, READY_RETRY_DELAY_MS)
                }
            }
            readyRetryTask = task
            handler.postDelayed(task, READY_RETRY_DELAY_MS)
        }
    }

    internal fun shouldRetrySystemHookReady(attempt: Int): Boolean =
        attempt in 0 until MAX_READY_RETRY_ATTEMPTS

    fun stopReadyRetry() {
        synchronized(readyRetryLock) {
            val handler = readyRetryHandler
            val task = readyRetryTask
            if (handler != null && task != null) {
                handler.removeCallbacks(task)
            }
            readyRetryTask = null
            readyRetryAttempt = 0
            readyRetryHandler = null
        }
    }

    private class RuntimeNotificationBridge private constructor(private val bridgeClass: Class<*>) {
        companion object {
            fun create(classLoader: ClassLoader): RuntimeNotificationBridge? = runCatching {
                val bridge = classLoader.findClass(RuntimeNotificationBridgeClass)
                val version = bridge.getOrNull<Int>("HOOK_API_VERSION") ?: 0
                check(version == 1) { "notification bridge api mismatch: $version" }
                RuntimeNotificationBridge(bridge)
            }.onFailure {
                XLog.e(TAG, "failed to resolve XMSF notification bridge", it)
            }.getOrNull()
        }

        private fun invoke(name: String, parameterTypes: Array<Class<*>>, vararg args: Any?): Any? =
            bridgeClass.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }.invoke(null, *args)

        private inline fun <reified T : Any> castNullableList(value: Any?): List<T?>? {
            val values = value as? List<*> ?: return null
            return values.map { item ->
                check(item == null || item is T) { "unexpected reflected list item: ${item?.let { it::class.java.name } ?: "null"}" }
                item
            }
        }

        private inline fun <reified T : Any> castList(value: Any?): List<T> {
            val values = value as? List<*>
                ?: error("unexpected reflected list result: ${value?.let { it::class.java.name }}")
            return values.map { item ->
                check(item is T) { "unexpected reflected list item: ${item?.let { it::class.java.name }}" }
                item
            }
        }

        private inline fun <reified T : Any> castNullableArray(value: Any?): Array<T?>? {
            val values = value as? Array<*> ?: return null
            return values.map { item ->
                check(item == null || item is T) { "unexpected reflected array item: ${item?.let { it::class.java.name } ?: "null"}" }
                item
            }.toTypedArray()
        }

        fun notify(packageName: String, tag: String?, id: Int, notification: Notification): Boolean =
            invoke(
                "notify",
                arrayOf(
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    Notification::class.java,
                ),
                packageName,
                tag,
                id,
                notification,
            ) as Boolean

        fun cancel(packageName: String, tag: String?, id: Int) {
            invoke("cancel", arrayOf(String::class.java, String::class.java, Int::class.javaPrimitiveType!!), packageName, tag, id)
        }

        fun createNotificationChannels(packageName: String, channels: List<NotificationChannel>): Boolean =
            invoke("createNotificationChannels", arrayOf(String::class.java, List::class.java), packageName, channels) as Boolean

        fun getNotificationChannel(packageName: String, channelId: String?): NotificationChannel? =
            invoke("getNotificationChannel", arrayOf(String::class.java, String::class.java), packageName, channelId) as NotificationChannel?

        fun getNotificationChannels(packageName: String): List<NotificationChannel?>? =
            castNullableList(invoke("getNotificationChannels", arrayOf(String::class.java), packageName))

        fun deleteNotificationChannel(packageName: String, channelId: String?) {
            invoke("deleteNotificationChannel", arrayOf(String::class.java, String::class.java), packageName, channelId)
        }

        fun createNotificationChannelGroups(packageName: String, groups: List<NotificationChannelGroup>) {
            invoke("createNotificationChannelGroups", arrayOf(String::class.java, List::class.java), packageName, groups)
        }

        fun getNotificationChannelGroup(packageName: String, groupId: String?): NotificationChannelGroup? =
            invoke("getNotificationChannelGroup", arrayOf(String::class.java, String::class.java), packageName, groupId) as NotificationChannelGroup?

        fun getNotificationChannelGroups(packageName: String): List<NotificationChannelGroup?>? =
            castNullableList(invoke("getNotificationChannelGroups", arrayOf(String::class.java), packageName))

        fun deleteNotificationChannelGroup(packageName: String, groupId: String?) {
            invoke("deleteNotificationChannelGroup", arrayOf(String::class.java, String::class.java), packageName, groupId)
        }

        fun areNotificationsEnabled(packageName: String): Boolean =
            invoke("areNotificationsEnabled", arrayOf(String::class.java), packageName) as Boolean

        fun getActiveNotifications(packageName: String): Array<StatusBarNotification?>? =
            castNullableArray(invoke("getActiveNotifications", arrayOf(String::class.java), packageName))

        fun supportsTargetChannelProvisioning(packageName: String): Boolean =
            invoke("supportsTargetChannelProvisioning", arrayOf(String::class.java), packageName) as Boolean

        fun findPreferredTargetChannel(packageName: String, preferredChannelId: String?): NotificationChannel? =
            invoke("findPreferredTargetChannel", arrayOf(String::class.java, String::class.java), packageName, preferredChannelId) as NotificationChannel?

        fun getTargetNotificationChannels(packageName: String): List<NotificationChannel> =
            castList(invoke("getTargetNotificationChannels", arrayOf(String::class.java), packageName))

        fun getTargetNotificationChannelGroups(packageName: String): List<NotificationChannelGroup> =
            castList(invoke("getTargetNotificationChannelGroups", arrayOf(String::class.java), packageName))

        fun getTargetNotificationChannel(packageName: String, channelId: String?): NotificationChannel? =
            invoke("getTargetNotificationChannel", arrayOf(String::class.java, String::class.java), packageName, channelId) as NotificationChannel?

        fun getPreferredTargetNotificationChannel(packageName: String, preferredChannelId: String?): NotificationChannel? =
            invoke("getPreferredTargetNotificationChannel", arrayOf(String::class.java, String::class.java), packageName, preferredChannelId) as NotificationChannel?

        fun createTargetNotificationChannelGroups(packageName: String, groups: List<NotificationChannelGroup>): Boolean =
            invoke("createTargetNotificationChannelGroups", arrayOf(String::class.java, List::class.java), packageName, groups) as Boolean

        fun createTargetNotificationChannels(packageName: String, channels: List<NotificationChannel>): Boolean =
            invoke("createTargetNotificationChannels", arrayOf(String::class.java, List::class.java), packageName, channels) as Boolean

        fun notifyAsTargetPackage(packageName: String, tag: String?, id: Int, notification: Notification): Boolean =
            invoke(
                "notifyAsTargetPackage",
                arrayOf(
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    Notification::class.java,
                ),
                packageName,
                tag,
                id,
                notification,
            ) as Boolean

        fun cancelAsTargetPackage(packageName: String, tag: String?, id: Int): Boolean =
            invoke("cancelAsTargetPackage", arrayOf(String::class.java, String::class.java, Int::class.javaPrimitiveType!!), packageName, tag, id) as Boolean
    }

    private inline fun <R> tryInvoke(invoke: () -> R): R {
        try {
            return invoke()
        } catch (e: HookInvocationTargetError) {
            XLog.e(TAG, "tryInvoke: ", e)
            XLog.e(TAG, "tryInvoke targetException: ", e.cause)
            throw e.cause ?: e
        } catch (e: InvocationTargetException) {
            XLog.e(TAG, "tryInvoke: ", e)
            XLog.e(TAG, "tryInvoke targetException: ", e.targetException)
            throw e.targetException ?: e
        } catch (e: Throwable) {
            XLog.e(TAG, "tryInvoke: ", e)
            XLog.e(TAG, "tryInvoke cause: ", e.cause)
            throw e.cause ?: e
        }
    }
}
