package io.github.magisk317.mipush.hook.system

import android.app.Notification
import android.app.NotificationChannelGroup
import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Process
import io.github.magisk317.mipush.common.ANDROID_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.mipush.common.logging.LogRoute
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.logging.FixedWindowLogLimiter
import android.util.Log
import android.os.SystemClock
import io.github.magisk317.xposed.HookCallback
import io.github.magisk317.xposed.HookContext
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.currentApplication
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.findMethodExact
import io.github.magisk317.xposed.hook
import io.github.magisk317.xposed.hookAllMethods
import io.github.magisk317.xposed.hookMethod
import io.github.magisk317.xposed.logging.MagiskOtel

object NmsPermissionHooker {
    private val debugLimiter = FixedWindowLogLimiter(
        maxEvents = 12,
        windowMs = 30_000L,
        maxTrackedKeys = 64,
    )

    private const val TAG = "NmsPermissionHooker"

    @Volatile private var xmsfUid = -1
    private fun getXmsfUid(): Int {
        if (xmsfUid == -1) {
            runCatching {
                xmsfUid = getContext().packageManager.getPackageUid(XMSF_PACKAGE_NAME, 0)
            }.onFailure {
                XLog.w(TAG, "resolving the xmsf package uid failed: ${it.message}", it)
            }

        }
        return xmsfUid
    }

    internal fun isXmsfCallingIdentity(
        callingUid: Int,
        primaryXmsfUid: Int,
        callingPackages: Collection<String>,
    ): Boolean {
        if (primaryXmsfUid > 0 && callingUid == primaryXmsfUid) {
            return true
        }
        return XMSF_PACKAGE_NAME in callingPackages
    }

    private fun getCallingPackages(callingUid: Int): List<String> {
        return runCatching {
            getContext().packageManager.getPackagesForUid(callingUid)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    // 每步独立隔离的 hook 安装：issue #149 的设备（system_server 被 R8 混淆，NMS 类名形如
    // android.telephony.BoulDealBlistount）上，PreferencesHelper 的 findClass 抛 LinkageError
    // 曾让 try{} 包到底的旧实现把其后所有 hook（updateNotificationChannelGroupForPackage、
    // getNotificationChannelGroupsForPackage 等）静默丢掉——建组/组查询永远 SecurityException，
    // 而更早安装的 hook 看起来"一切正常"，极难定位。现在每步失败只影响自己，并留下审计记录。
    private val installedHooks = mutableListOf<String>()
    private val failedHooks = mutableMapOf<String, Throwable>()

    private fun installStep(name: String, block: () -> Unit) {
        try {
            block()
            installedHooks.add(name)
        } catch (throwable: Throwable) {
            failedHooks[name] = throwable
            XLog.w(
                tag = TAG,
                route = LogRoute.NMS_HOOK.id,
                message = "NMS hook install failed step=$name error=${throwable.javaClass.name}: ${throwable.message}",
            )
        }
    }

    private fun fromXmsf(): Boolean {
        val callingUid = Binder.getCallingUid()
        // uid 探测与包名探测是两条独立证据链，必须各自吞异常：任一 PackageManager 抖动
        // （混淆 ROM 上 getPackageUid / getPackagesForUid 会间歇失败）都不能单独阻断 bypass。
        // 旧实现整链包在一个 try 里，探测一旦失败就返回 false、不清 identity——hook 明明已装上，
        // 同一方法却在成功与 SecurityException("Disallowed call for uid 10456") 之间来回抖动
        // （issue #149 设备上 getNotificationChannelForPackage 栈实锤 hook 在跑但 identity 未清）。
        val uidProbe = runCatching {
            val primary = getXmsfUid()
            primary > 0 && callingUid == primary
        }.onFailure {
            XLog.d(TAG, "xmsf uid probe failed", it)
        }.getOrDefault(false)
        if (uidProbe) return true
        return runCatching {
            XMSF_PACKAGE_NAME in getCallingPackages(callingUid)
        }.onFailure {
            XLog.d(TAG, "xmsf package probe failed", it)
        }.getOrDefault(false)
    }

    private fun getPackageUid(packageName: String) = getContext().packageManager.getPackageUid(packageName, 0)

    private fun getContext(): Context = currentApplication()
        ?: throw IllegalStateException("currentApplication unavailable")

    private fun rewriteNativeFocusNotification(param: MethodHookParam) {
        val packageName = param.args.getOrNull(0) as? String ?: return
        val opPackageName = param.args.getOrNull(1) as? String ?: return
        val notification = param.args.getOrNull(4) as? Notification ?: return
        val userId = param.args.getOrNull(5) as? Int ?: return
        val callingUid = Binder.getCallingUid()
        val context = runCatching { getContext() }.getOrNull() ?: return
        val options = IslandPreferences.current(packageName, userId)
        val result = NativeFocusNotificationRewriter.rewrite(
            NativeFocusNotificationRewriter.RewriteInput(
                packageName = packageName,
                opPackageName = opPackageName,
                userId = userId,
                callingUid = callingUid,
                notification = notification,
                enabled = options.nativeLiveUpdateEnabled,
                isMiui = NativeFocusNotificationRewriter.isMiuiBuild(),
                supportsLiveUpdates = NativeFocusNotificationRewriter.supportsLiveUpdates(),
                context = context,
            ),
        )
        result.notification?.let { rewritten ->
            param.args[4] = rewritten
            // Rate limited: progress-style apps update the same id frequently. The message
            // carries no package name or notification content, only the decision reason.
            if (debugLimiter.tryAcquire("native_focus_rewritten", android.os.SystemClock.elapsedRealtime()).allowed) {
                XLog.i(
                    tag = "NativeFocusLiveUpdate",
                    route = LogRoute.NMS_HOOK.id,
                    message = "rewrote focus notification user=$userId reason=${result.reason} " +
                        "promotionToken=${result.promotionTokenIssued}",
                )
            }
        }
    }

    /**
     * Whether a caller identity bypass is required for an NMS permission hook whose target
     * package name sits at [targetPackageName].
     *
     * MIUI resolveNotificationUid binds callingUid to the uid owning opPkg, so clearing the
     * identity (xmsf uid -> 1000) breaks that binding when xmsf operates on its own package
     * and invokeOriginal throws SecurityException. xmsf acting on itself needs no bypass:
     * keep the real calling uid so the binding check passes.
     */
    internal fun shouldBypassCallingIdentity(targetPackageName: String?): Boolean =
        targetPackageName != XMSF_PACKAGE_NAME

    private fun hookPermission(targetPackageNameParamIndex: Int, hookExtra: (MethodHookParam.() -> Unit)? = null): HookCallback = {
        replace {
            var token: Long? = null
            if (fromXmsf()) {
                if (shouldBypassCallingIdentity(args[targetPackageNameParamIndex] as? String)) {
                    token = Binder.clearCallingIdentity()
                }
                hookExtra?.invoke(this)
            }
            try {
                invokeOriginal()
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.targetException ?: e.cause ?: e
            } finally {
                if (token != null) {
                    Binder.restoreCallingIdentity(token)
                }
            }
        }
    }

    private fun hookCanNotifyAsPackage(): HookCallback = {
        doBefore {
            if (fromXmsf()) {
                if (XLog.getLogLevel() <= Log.DEBUG &&
            debugLimiter.tryAcquire("force_can_notify_as_package", SystemClock.elapsedRealtime()).allowed
        ) {
            XLog.d(tag = TAG, message = "force canNotifyAsPackage(callingPkg=${args[0]}, targetPkg=${args[1]}, userId=${args[2]}) = true")
        }
                result = true
            }
        }
    }

    internal fun shouldPreserveXmsfDelegateIdentity(
        callingPackage: String?,
        callingUid: Int,
        primaryXmsfUid: Int,
        callingPackages: Collection<String>,
    ): Boolean {
        return callingPackage == XMSF_PACKAGE_NAME &&
            isXmsfCallingIdentity(callingUid, primaryXmsfUid, callingPackages)
    }

    internal fun cancelOpPackage(pkg: String?, currentOpPkg: String?): String {
        if (pkg == XMSF_PACKAGE_NAME) {
            return XMSF_PACKAGE_NAME
        }
        if (currentOpPkg == XMSF_PACKAGE_NAME) {
            return XMSF_PACKAGE_NAME
        }
        return ANDROID_PACKAGE_NAME
    }

    private fun hookNotificationEnqueue(preserveDelegateIdentity: Boolean): HookCallback = {
        replace {
            var token: Long? = null
            val xmsfCaller = fromXmsf()
            if (xmsfCaller) {
                if (preserveDelegateIdentity) {
                    // Keep the stock delegate identity. SystemUI and MIUI notification policy use
                    // opPkg=com.xiaomi.xmsf to distinguish a target-app MiPush notification from
                    // a notification posted by the Android system itself.
                    args[1] = XMSF_PACKAGE_NAME
                } else {
                    token = Binder.clearCallingIdentity()
                    args[1] = ANDROID_PACKAGE_NAME
                }
            } else {
                // A rewrite failure must keep the caller's original notification enqueued.
                runCatching { rewriteNativeFocusNotification(this) }.onFailure {
                    if (debugLimiter.tryAcquire("native_focus_rewrite_failed", android.os.SystemClock.elapsedRealtime()).allowed) {
                        XLog.w(TAG, "native focus rewrite failed; enqueuing original: ${it.javaClass.simpleName}")
                    }
                }
            }
            try {
                invokeOriginal()
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.targetException ?: e.cause ?: e
            } finally {
                if (token != null) {
                    Binder.restoreCallingIdentity(token)
                }
            }
        }
    }

    private fun installNotificationDelegateResolver(classLoader: ClassLoader?): Boolean {
        return runCatching {
            val notificationManagerService = findClass(
                "com.android.server.notification.NotificationManagerService",
                classLoader,
            )
            val getPackageUidAsUser = PackageManager::class.java.getMethod(
                "getPackageUidAsUser",
                String::class.java,
                Int::class.javaPrimitiveType,
            )
            notificationManagerService.hookMethod(
                "resolveNotificationUid",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            ) {
                doBefore {
                    val callingPackage = args[0] as? String
                    val targetPackage = args[1] as? String ?: return@doBefore
                    val callingUid = args[2] as? Int ?: return@doBefore
                    val userId = args[3] as? Int ?: return@doBefore
                    if (!shouldPreserveXmsfDelegateIdentity(
                            callingPackage = callingPackage,
                            callingUid = callingUid,
                            primaryXmsfUid = getXmsfUid(),
                            callingPackages = getCallingPackages(callingUid),
                        )
                    ) {
                        return@doBefore
                    }
                    val packageManager = getContext().packageManager
                    result = getPackageUidAsUser.invoke(packageManager, targetPackage, userId) as Int
                }
            }
            XLog.i(tag = TAG, route = LogRoute.NMS_HOOK.id, message = "notification delegate resolver hook installed; preserving XMSF opPkg")
            true
        }.getOrElse { throwable ->
            XLog.w(
                tag = TAG,
                route = LogRoute.NMS_HOOK.id,
                message = "notification delegate resolver unavailable; keep system-identity fallback: ${throwable.message}"
            )
            false
        }
    }

    fun hook(classINotificationManager: Class<*>) {
        XLog.i(tag = TAG, route = LogRoute.NMS_HOOK.id, message = "installing NMS permission hooks on ${classINotificationManager.name}")
        installedHooks.clear()
        failedHooks.clear()
        try {
        val preserveNotificationDelegateIdentity =
            installNotificationDelegateResolver(classINotificationManager.classLoader)
        //boolean canNotifyAsPackage(String callingPkg, String targetPkg, int userId);
        installStep("canNotifyAsPackage") {
            findMethodExact(classINotificationManager, "canNotifyAsPackage", String::class.java, String::class.java, Int::class.java)
                .hook(hookCanNotifyAsPackage())
        }

        //boolean areNotificationsEnabledForPackage(String pkg, int uid);
        installStep("areNotificationsEnabledForPackage") {
            findMethodExact(classINotificationManager, "areNotificationsEnabledForPackage", String::class.java, Int::class.java)
                .hook(hookPermission(0))
        }

        installStep("getNotificationChannelForPackage") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                //NotificationChannel getNotificationChannelForPackage(String pkg, int uid, String channelId, String conversationId, boolean includeDeleted);
                findMethodExact(
                    classINotificationManager,
                    "getNotificationChannelForPackage",
                    String::class.java, Int::class.java, String::class.java, String::class.java, Boolean::class.java,
                )
                    .hook(hookPermission(0))
            } else {
                //NotificationChannel getNotificationChannelForPackage(String pkg, int uid, String channelId, boolean includeDeleted);
                findMethodExact(
                    classINotificationManager,
                    "getNotificationChannelForPackage",
                    String::class.java, Int::class.java, String::class.java, Boolean::class.java,
                )
                    .hook(hookPermission(0))
            }
        }

        //ParceledListSlice getNotificationChannelsForPackage(String pkg, int uid, boolean includeDeleted);
        installStep("getNotificationChannelsForPackage") {
            findMethodExact(classINotificationManager, "getNotificationChannelsForPackage", String::class.java, Int::class.java, Boolean::class.java)
                .hook(hookPermission(0))
        }

        //void enqueueNotificationWithTag(String pkg, String opPkg, String tag, int id, Notification notification, int userId)
        installStep("enqueueNotificationWithTag") {
            findMethodExact(
                classINotificationManager,
                "enqueueNotificationWithTag",
                String::class.java, String::class.java, String::class.java, Int::class.java,
                Notification::class.java, Int::class.java,
            )
                .hook(hookNotificationEnqueue(preserveNotificationDelegateIdentity))
        }
        installStep("NativeFocusNotificationRewriter.installPromotionHook") {
            NativeFocusNotificationRewriter.installPromotionHook(classINotificationManager.classLoader)
        }

        //void createNotificationChannelsForPackage(String pkg, int uid, in ParceledListSlice channelsList);
        installStep("createNotificationChannelsForPackage") {
            findMethodExact(
                classINotificationManager,
                "createNotificationChannelsForPackage",
                String::class.java, Int::class.java,
                findClass("android.content.pm.ParceledListSlice", null),
            )
                .hook(hookPermission(0))
        }

        installStep("cancelNotificationWithTag") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                //void cancelNotificationWithTag(String pkg, String opPkg, String tag, int id, int userId);
                findMethodExact(
                    classINotificationManager,
                    "cancelNotificationWithTag",
                    String::class.java, String::class.java, String::class.java, Int::class.java, Int::class.java,
                )
                    .hook(hookPermission(0) {
                        val pkg = args[0] as? String
                        val currentOpPkg = args[1] as? String
                        val resolvedOpPkg = cancelOpPackage(pkg, currentOpPkg)
                        if (resolvedOpPkg != currentOpPkg) {
                            XLog.i(
                                tag = TAG,
                                route = LogRoute.NMS_HOOK.id,
                                message = "cancelNotificationWithTag rewrite opPkg pkg=$pkg from=$currentOpPkg to=$resolvedOpPkg"
                            )
                            args[1] = resolvedOpPkg
                        }
                    })
            } else {
                //void cancelNotificationWithTag(String pkg, String opPkg, String tag, int id, int userId);
                findMethodExact(classINotificationManager, "cancelNotificationWithTag", String::class.java, String::class.java, Int::class.java, Int::class.java)
                    .hook(hookPermission(0))
            }
        }

        //void deleteNotificationChannel(String pkg, String channelId);
        installStep("deleteNotificationChannel") {
            findMethodExact(classINotificationManager, "deleteNotificationChannel", String::class.java, String::class.java)
                .hook(hookPermission(0))
        }

        //ParceledListSlice getAppActiveNotifications(String callingPkg, int userId);
        installStep("getAppActiveNotifications") {
            findMethodExact(classINotificationManager, "getAppActiveNotifications", String::class.java, Int::class.java)
                .hook(hookPermission(0))
        }

        val deleteNotificationChannelHook: HookContext.() -> Unit = {
            doBefore {
                val packageName = args[0] as String
                if (Binder.getCallingUid() == Process.SYSTEM_UID) {
                    args[1] = getPackageUid(packageName)
                }
            }
        }
        installStep("PreferencesHelper.deleteNotificationChannel") {
            // 注意：这里必须捕获 Throwable 而不只是 NoSuchMethodError。混淆 ROM（issue #149 设备，
            // NMS 类被 R8 重命名）上 findClass("com.android.server.notification.PreferencesHelper")
            // 抛的是 ClassNotFoundError（LinkageError），旧实现只捕 NoSuchMethodError，会从这里
            // 冲出外层 try，把其后整段 hook 安装（updateNotificationChannelGroupForPackage 等）
            // 全部静默跳过。installStep 已兜住一切 Throwable；内部 catch 仅为保留 Samsung One UI 7
            // 的"方法被删"语义日志。
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    findClass("com.android.server.notification.PreferencesHelper", classINotificationManager.classLoader)
                        //public boolean deleteNotificationChannel(String pkg, int uid, String channelId, int callingUid, boolean fromSystemOrSystemUi)
                        .hookMethod(
                            "deleteNotificationChannel", String::class.java, Int::class.java, String::class.java, Int::class.java, Boolean::class.java,
                            callback = deleteNotificationChannelHook
                        )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    findClass("com.android.server.notification.PreferencesHelper", classINotificationManager.classLoader)
                        //public boolean deleteNotificationChannel(String pkg, int uid, String channelId)
                        .hookMethod("deleteNotificationChannel", String::class.java, Int::class.java, String::class.java,
                            callback = deleteNotificationChannelHook
                        )
                } else {
                    findClass("com.android.server.notification.RankingHelper", classINotificationManager.classLoader)
                        //public void deleteNotificationChannel(String pkg, int uid, String channelId)
                        .hookMethod("deleteNotificationChannel", String::class.java, Int::class.java, String::class.java,
                            callback = deleteNotificationChannelHook
                        )
                }
            } catch (e: NoSuchMethodError) {
                //Samsung One UI 7 delete this method
                XLog.d(tag = TAG, route = LogRoute.NMS_HOOK.id, message = "hook deleteNotificationChannel error, NoSuchMethodError", e)
            }
        }

        //void updateNotificationChannelGroupForPackage(String pkg, int uid, in NotificationChannelGroup group);
        installStep("updateNotificationChannelGroupForPackage") {
            findMethodExact(classINotificationManager, "updateNotificationChannelGroupForPackage", String::class.java, Int::class.java, NotificationChannelGroup::class.java)
                .hook(hookPermission(0))
        }

        //void createNotificationChannelGroup(String pkg, int uid, in NotificationChannelGroup group, boolean fromApp, boolean fromListener);
        // 旧实现注释"无法 hook"而跳过、只靠 updateNotificationChannelGroupForPackage 建组；但在
        // update 不会创建缺失组的 ROM 上（issue #149），组永远建不起来，后续建通道连带被
        // PreferencesHelper 以 "NotificationChannelGroup doesn't exist" 拒绝。这里补上权限绕过 hook，
        // NotificationHookBackend 在 update 回查失败后会走该 create 路径兜底。
        installStep("createNotificationChannelGroup") {
            findMethodExact(
                classINotificationManager,
                "createNotificationChannelGroup",
                String::class.java, Int::class.java, NotificationChannelGroup::class.java, Boolean::class.java, Boolean::class.java,
            )
                .hook(hookPermission(0))
        }

        //NotificationChannelGroup getNotificationChannelGroupForPackage(String groupId, String pkg, int uid);
        installStep("getNotificationChannelGroupForPackage") {
            findMethodExact(classINotificationManager, "getNotificationChannelGroupForPackage", String::class.java, String::class.java, Int::class.java)
                .hook(hookPermission(1))
        }

        //ParceledListSlice getNotificationChannelGroupsForPackage(String pkg, int uid, boolean includeDeleted);
        installStep("getNotificationChannelGroupsForPackage") {
            findMethodExact(classINotificationManager, "getNotificationChannelGroupsForPackage", String::class.java, Int::class.java, Boolean::class.java)
                .hook(hookPermission(0))
        }

        //void deleteNotificationChannelGroup(String pkg, String channelGroupId);
        installStep("deleteNotificationChannelGroup") {
            findMethodExact(classINotificationManager, "deleteNotificationChannelGroup", String::class.java, String::class.java)
                .hook(hookPermission(0))
        }

        runCatching {
            val classLoader = classINotificationManager.classLoader
            val nmsClass = findClass("com.android.server.notification.NotificationManagerService", classLoader)
            findMethodExact(nmsClass, "checkCallerIsSystem", *emptyArray<Any>())
                .hook {
                    replace {
                        if (fromXmsf()) {
                            XLog.d(tag = TAG, route = LogRoute.NMS_HOOK.id, message = "checkCallerIsSystem bypassed for xmsf")
                            return@replace null
                        }
                        // buzzBeepBlinkForNotification calls checkCallerIsSystem after
                        // binder identity is restored. Clear identity to avoid SecurityException.
                        val token = Binder.clearCallingIdentity()
                        try {
                            invokeOriginal()
                        } finally {
                            Binder.restoreCallingIdentity(token)
                        }
                    }
                }
            XLog.i(tag = TAG, route = LogRoute.NMS_HOOK.id, message = "checkCallerIsSystem hook installed")
        }.onFailure {
            XLog.e(tag = TAG, route = LogRoute.NMS_HOOK.id, message = "failed to hook checkCallerIsSystem", it)
        }

        // 安装审计：把实际装上/失败的步骤完整打出来。混淆 ROM 上曾经的失败模式是"早期 hook 生效、
        // 后期 hook 静默缺失"，从外部只能看到一半方法 SecurityException 一半正常，无法定位断点；
        // 这条清单 + otel 计数让断点在日志里一眼可见。注意 system_server 启动早期日志总线可能
        // 尚未就绪，清单可能随第一条 hook 日志才可见，所以 otel 计数同步承载结果。
        val installedSummary = installedHooks.joinToString(",")
        val failedSummary = failedHooks.keys.joinToString(",")
        XLog.i(
            tag = TAG,
            route = LogRoute.NMS_HOOK.id,
            message = "NMS hook install audit installed=${installedHooks.size} failed=${failedHooks.size} " +
                "installed=[$installedSummary] failed=[$failedSummary]",
        )
        MagiskOtel.event(
            name = "hook.load",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "system_server",
                "stage" to "nms_permission",
                "reason" to "installed",
                "installed" to installedHooks.size.toString(),
                "failed" to failedHooks.size.toString(),
            ),
            statusOk = failedHooks.isEmpty(),
        )
        } catch (error: Throwable) {
            XLog.e(tag = TAG, route = LogRoute.NMS_HOOK.id, message = "NMS permission hooks install failed", error)
            MagiskOtel.event(
                name = "hook.load",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "system_server",
                    "stage" to "nms_permission",
                    "reason" to error.javaClass.simpleName,
                ),
                statusOk = false,
            )
            throw error
        }
    }
}
