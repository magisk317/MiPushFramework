package io.github.magisk317.mipush.hook.system
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import io.github.magisk317.mipush.common.ANDROID_PACKAGE_NAME
import io.github.magisk317.mipush.common.IS_SYSTEM_HOOK_READY
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_FAKE_CONDITION_PROVIDER_PATH
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.fakedevice.compat.ModuleCompatRegistry
import io.github.magisk317.mipush.hook.securitycore.SecurityCoreXSpacePackageInfoHook
import io.github.magisk317.xposed.callMethod
import io.github.magisk317.xposed.callStaticMethod
import io.github.magisk317.xposed.currentApplication
import io.github.magisk317.xposed.findHookClass
import io.github.magisk317.xposed.get
import io.github.magisk317.xposed.hookAllMethods
import io.github.magisk317.xposed.hookMethod
import io.github.magisk317.xposed.logging.MagiskOtel
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

internal object NmsHookInstallRetryPolicy {
    const val MAX_ATTEMPTS = 8
    const val RETRY_DELAY_MS = 1_000L

    fun shouldRetry(attempt: Int): Boolean = attempt in 0 until MAX_ATTEMPTS
}

private enum class NmsHookInstallState {
    NOT_INSTALLED,
    INSTALLING,
    INSTALLED,
    FAILED,
}

class HookSystemService : BaseHook() {
    companion object {
        private const val TAG = "HookSystemService"
        private const val MAX_VISIBILITY_LOGS_PER_KEY = 8

        private var _isSystemHookReady: Boolean? = null
        private val nmsHookInstallState = AtomicReference(NmsHookInstallState.NOT_INSTALLED)
        private val nmsRetryLock = Any()
        private var nmsRetryHandler: Handler? = null
        private var nmsRetryTask: Runnable? = null
        private var nmsRetryAttempt = 0
        private val explicitCompatPackages: Set<String> =
            ModuleCompatRegistry.allProfiles().mapTo(LinkedHashSet()) { it.packageName }
        private val visibilityLogCounts: MutableMap<String, Int> = Collections.synchronizedMap(HashMap())

        val isSystemHookReady: Boolean
            get() {
                if (_isSystemHookReady == true) return true
                return try {
                    val app = currentApplication() ?: return false
                    val nm = app.getSystemService(NotificationManager::class.java) ?: return false
                    val ready = nm.callMethod("isSystemConditionProviderEnabled", IS_SYSTEM_HOOK_READY) as? Boolean ?: false
                    if (ready) _isSystemHookReady = true
                    ready
                } catch (t: Throwable) {
                    false
                }
            }

        internal data class VisibilityDecision(
            val allow: Boolean,
            val reason: String,
            val caller: String? = null,
        )

        internal fun shouldAllowMiPushVisibility(
            callingPackages: Collection<String>,
            targetPackageName: String?,
        ): VisibilityDecision {
            val caller = callingPackages.firstOrNull { it in explicitCompatPackages }
                ?: return VisibilityDecision(allow = false, reason = "caller_not_profile")
            if (targetPackageName == XMSF_PACKAGE_NAME) {
                return VisibilityDecision(allow = true, reason = "xmsf_for_profile", caller = caller)
            }
            if (targetPackageName == caller) {
                return VisibilityDecision(allow = true, reason = "self_for_profile", caller = caller)
            }
            return VisibilityDecision(allow = false, reason = "target_not_mipush_visible", caller = caller)
        }

        private fun emitSystemService(result: String, reason: String, statusOk: Boolean = true) {
            MagiskOtel.event(
                name = "hook.load",
                attributes = mapOf(
                    "result" to result,
                    "duration_ms" to "0",
                    "process" to "system_server",
                    "stage" to "system_service",
                    "reason" to reason,
                ),
                statusOk = statusOk,
            )
        }

        private fun hookGlobalVisibility(classLoader: ClassLoader) {
            runCatching {
                val packageStateClass = findHookClass("com.android.server.pm.pkg.PackageState", classLoader)
                val getPackageNameMethod = packageStateClass.getMethod("getPackageName")
                val appsFilterClass = findHookClass("com.android.server.pm.AppsFilterBase", classLoader)
                appsFilterClass.hookAllMethods("shouldFilterApplication") {
                    doAfter {
                        if (result == false) return@doAfter
                        val targetPackageState = findPackageStateArg(args, getPackageNameMethod) ?: return@doAfter
                        val targetPackageName = packageNameFromState(targetPackageState, getPackageNameMethod) ?: return@doAfter
                        val callingUid = findCallingUid(args)
                        val callingPackages = resolveCallingPackages(args, callingUid)
                        val decision = shouldAllowMiPushVisibility(callingPackages, targetPackageName)
                        logVisibilityDecision(callingUid, callingPackages, targetPackageName, decision)
                        if (decision.allow) {
                            result = false
                        }
                    }
                }
                XLog.d(TAG, "installed scoped xmsf visibility hook")
                emitSystemService(result = "ok", reason = "visibility_installed")
            }.onFailure {
                XLog.e(TAG, "install xmsf global visibility hook failed", it)
                emitSystemService(result = "error", reason = it.javaClass.simpleName, statusOk = false)
            }
        }

        private fun hookForegroundServiceExemption(classLoader: ClassLoader) {
            runCatching {
                val amsClass = findHookClass("com.android.server.am.ActivityManagerService", classLoader)
                amsClass.hookAllMethods("setServiceForeground") {
                    doBefore {
                        val className = args.getOrNull(0) as? android.content.ComponentName ?: return@doBefore
                        if (className.packageName == XMSF_PACKAGE_NAME) {
                            val activeServices = thisObject?.get<Any?>("mServices") ?: return@doBefore
                            val serviceRecord = runCatching {
                                val getServiceByNameMethod = activeServices.javaClass.methods.firstOrNull {
                                    it.name.contains("getServiceByName")
                                }
                                val userId = (UserHandle::class.java.getMethod("getCallingUserId").invoke(null) as? Int) ?: 0
                                getServiceByNameMethod?.invoke(activeServices, className, userId)
                            }.getOrNull()
                            if (serviceRecord != null) {
                                runCatching {
                                    serviceRecord.javaClass.getField("mAllowWhileInUsePermissionInFgs").setBoolean(serviceRecord, true)
                                }
                            }
                        }
                    }
                }
                XLog.d(TAG, "installed xmsf foreground service exemption hook")
                emitSystemService(result = "ok", reason = "fgs_exemption_installed")
            }.onFailure {
                XLog.e(TAG, "install xmsf foreground service exemption hook failed", it)
                emitSystemService(result = "error", reason = it.javaClass.simpleName, statusOk = false)
            }
        }

        private fun findPackageStateArg(args: Array<Any?>, getPackageNameMethod: Method): Any? {
            return args.lastOrNull { arg ->
                arg != null && packageNameFromState(arg, getPackageNameMethod) != null
            }
        }

        private fun packageNameFromState(packageState: Any, getPackageNameMethod: Method): String? {
            return runCatching { getPackageNameMethod.invoke(packageState) as? String }.getOrNull()
        }

        private fun findCallingUid(args: Array<Any?>): Int {
            return args.firstOrNull { it is Int && it != Process.SYSTEM_UID } as? Int
                ?: Binder.getCallingUid()
        }

        private fun resolveCallingPackages(args: Array<Any?>, callingUid: Int): List<String> {
            val fromCallingSetting = args
                .asSequence()
                .drop(2)
                .firstNotNullOfOrNull { callingPackagesFromSetting(it) }
                .orEmpty()
            if (fromCallingSetting.isNotEmpty()) return fromCallingSetting

            val fromComputer = args.firstNotNullOfOrNull { computer ->
                runCatching {
                    val target = computer ?: return@runCatching null
                    val packages = target.callMethod("getPackagesForUid", callingUid) as? Array<*>
                    packages?.filterIsInstance<String>()
                }.getOrNull()
            }.orEmpty()
            if (fromComputer.isNotEmpty()) return fromComputer

            val app = currentApplication()
            val pm = app?.packageManager ?: return emptyList()
            return runCatching { pm.getPackagesForUid(callingUid)?.toList().orEmpty() }.getOrDefault(emptyList())
        }

        internal fun callingPackagesFromSetting(callingSetting: Any?): List<String>? {
            if (callingSetting == null) return null
            val directPackageName = packageNameFromSetting(callingSetting)
            if (!directPackageName.isNullOrBlank()) return listOf(directPackageName)

            val packageStates = runCatching {
                callingSetting.javaClass.methods
                    .firstOrNull { it.name == "getPackageStates" && it.parameterCount == 0 }
                    ?.invoke(callingSetting) as? Iterable<*>
            }.getOrNull()
            return packageStates
                ?.mapNotNull { state -> state?.let { packageNameFromSetting(it) } }
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
        }

        private fun packageNameFromSetting(setting: Any): String? {
            return runCatching {
                setting.javaClass.methods
                    .firstOrNull { it.name == "getPackageName" && it.parameterCount == 0 }
                    ?.invoke(setting) as? String
            }.getOrNull()
        }

        private fun logVisibilityDecision(
            callingUid: Int,
            callingPackages: Collection<String>,
            targetPackageName: String,
            decision: VisibilityDecision,
        ) {
            val message = visibilityDecisionLogMessage(
                callingUid = callingUid,
                callingPackages = callingPackages,
                targetPackageName = targetPackageName,
                decision = decision,
            ) ?: return
            XLog.i(
                TAG,
                message,
            )
        }

        internal fun visibilityDecisionLogMessage(
            callingUid: Int,
            callingPackages: Collection<String>,
            targetPackageName: String,
            decision: VisibilityDecision,
        ): String? {
            if (!decision.allow) return null
            val callers = callingPackages.joinToString(limit = 4)
            val caller = decision.caller ?: "unknown"
            val key = "${decision.reason}:$caller:$targetPackageName"
            if (!markVisibilityLogAllowed(key)) return null
            return "visibility allow uid=$callingUid callers=[$callers] target=$targetPackageName reason=${decision.reason}"
        }

        internal fun resetVisibilityLogLimiterForTest() {
            synchronized(visibilityLogCounts) {
                visibilityLogCounts.clear()
            }
        }

        private fun markVisibilityLogAllowed(key: String): Boolean {
            synchronized(visibilityLogCounts) {
                val count = visibilityLogCounts.getOrDefault(key, 0)
                if (count >= MAX_VISIBILITY_LOGS_PER_KEY) return false
                visibilityLogCounts[key] = count + 1
                return true
            }
        }
    }

    override fun onHotReloading() {
        // Cancel delayed retries while the old module ClassLoader is still reachable so a
        // retry cannot invoke hooks through a stale classloader after hot reload.
        stopNmsHookRetry()
        // system_server owns the XSpace package-sync receiver and its executor. Release them
        // while the old module ClassLoader is still reachable so hot reload can collect its DEX.
        XSpacePackageSyncHook.stop()
    }

    override fun onLoadPackage(param: LoadParam) {
        XLog.i(
            TAG,
            "onLoadPackage pkg=${param.packageName} proc=${param.processName}",
        )
        if (param.packageName != ANDROID_PACKAGE_NAME) {
            XLog.d(TAG, "skip system hook: package mismatch pkg=${param.packageName}")
            return
        }
        if (param.processName != ANDROID_PACKAGE_NAME &&
            param.processName != "system" &&
            param.processName != "system_server"
        ) {
            XLog.w(TAG, "skip system hook: process mismatch proc=${param.processName}")
            return
        }
        val classLoader = param.classLoader
        val classNotificationManagerService = findHookClass("com.android.server.notification.NotificationManagerService", classLoader)
        XLog.i(TAG, "installing system notification hooks")
        installXSpacePackageSyncReceiver(classLoader)

        classNotificationManagerService.hookMethod("onStart") {
            doAfter {
                XLog.d(TAG, "onStart invoked")
                val owner = thisObject ?: return@doAfter
                val context = owner.callMethod("getContext") as Context
                val service = owner.get<Any?>("mService")
                if (service == null) {
                    XLog.w(TAG, "skip system notification hook install because mService is null; scheduling retry")
                    emitSystemService(result = "skip", reason = "mservice_null")
                    scheduleNmsHookRetry(classLoader)
                    return@doAfter
                }
                installNotificationHooks(
                    context = context,
                    service = service,
                    source = "on_start",
                    allowRetry = true,
                    retryClassLoader = classLoader,
                )
            }
        }

        // onStart has already run when LSPosed hot-reloads the module into an existing
        // system_server. Resolve the local BinderService now; otherwise the onStart callback
        // above will never fire and the NMS permission hooks remain uninstalled.
        installRunningNotificationHooks(classLoader)

        //private boolean isPackageSuspendedForUser(String pkg, int uid)
        classNotificationManagerService.hookMethod("isPackageSuspendedForUser", String::class.java, Int::class.java) {
            doBefore {
                if (Binder.getCallingUid() == 1000) {
                    //suspend app can not show notification, fake its state
                    result = false
                }
            }
        }


        val classShortcutService = findHookClass("com.android.server.pm.ShortcutService", classLoader)
        ShortcutPermissionHooker.hook(classShortcutService)
        hookGlobalVisibility(classLoader)
        hookForegroundServiceExemption(classLoader)
        SecurityCoreXSpacePackageInfoHook.hook(classLoader)
    }

    private fun installRunningNotificationHooks(classLoader: ClassLoader, fromRetry: Boolean = false) {
        val context = currentSystemContext(classLoader)
        if (context == null) {
            XLog.w(TAG, "skip hot-reload NMS hook install because system context is unavailable")
            emitSystemService(result = "skip", reason = "hot_reload_context_unavailable")
            if (!fromRetry) scheduleNmsHookRetry(classLoader)
            return
        }
        val service = runCatching {
            findHookClass("android.os.ServiceManager", classLoader)
                .callStaticMethod("getService", Context.NOTIFICATION_SERVICE)
        }.onFailure {
            XLog.w(TAG, "skip hot-reload NMS hook install because notification service lookup failed: ${it.message}")
        }.getOrNull()
        if (service == null) {
            XLog.d(TAG, "notification service is not published yet; onStart will install NMS hooks")
            emitSystemService(result = "skip", reason = "hot_reload_service_unavailable")
            if (!fromRetry) scheduleNmsHookRetry(classLoader)
            return
        }
        installNotificationHooks(
            context = context,
            service = service,
            source = "hot_reload",
            allowRetry = !fromRetry,
            retryClassLoader = classLoader,
        )
    }

    private fun installNotificationHooks(
        context: Context,
        service: Any,
        source: String,
        allowRetry: Boolean,
        retryClassLoader: ClassLoader?,
    ) {
        val previousState = nmsHookInstallState.get()
        val acquired = nmsHookInstallState.compareAndSet(
            NmsHookInstallState.NOT_INSTALLED,
            NmsHookInstallState.INSTALLING,
        ) || nmsHookInstallState.compareAndSet(
            NmsHookInstallState.FAILED,
            NmsHookInstallState.INSTALLING,
        )
        if (!acquired) {
            XLog.d(TAG, "skip duplicate NMS hook install source=$source state=$previousState")
            return
        }
        val stubClass = service.javaClass
        XLog.i(TAG, "installing NMS permission hooks source=$source stub=${stubClass.name}")
        try {
            XSpacePackageSyncHook.install(context)
            hookPermission(stubClass)
            hookSystemReadyFlag(stubClass)
            nmsHookInstallState.set(NmsHookInstallState.INSTALLED)
            stopNmsHookRetry()
            XLog.i(TAG, "system notification hooks installed source=$source")
            emitSystemService(result = "ok", reason = "nms_installed")
        } catch (error: Throwable) {
            nmsHookInstallState.set(NmsHookInstallState.FAILED)
            XLog.e(TAG, "system notification hooks install failed source=$source", error)
            emitSystemService(result = "error", reason = error.javaClass.simpleName, statusOk = false)
            if (allowRetry) {
                scheduleNmsHookRetry(retryClassLoader)
            }
        }
    }

    private fun scheduleNmsHookRetry(classLoader: ClassLoader?) {
        if (classLoader == null) {
            XLog.w(TAG, "cannot schedule NMS hook retry because classloader is unavailable")
            return
        }
        synchronized(nmsRetryLock) {
            if (nmsRetryTask != null) return
            val handler = nmsRetryHandler ?: Handler(Looper.getMainLooper()).also {
                nmsRetryHandler = it
            }
            nmsRetryAttempt = 0
            val task = object : Runnable {
                override fun run() {
                    val attempt = synchronized(nmsRetryLock) { nmsRetryAttempt }
                    if (nmsHookInstallState.get() == NmsHookInstallState.INSTALLED) {
                        stopNmsHookRetry()
                        return
                    }
                    if (!NmsHookInstallRetryPolicy.shouldRetry(attempt)) {
                        XLog.w(TAG, "NMS hook retry exhausted; state=${nmsHookInstallState.get()}")
                        emitSystemService(result = "error", reason = "nms_retry_exhausted", statusOk = false)
                        stopNmsHookRetry()
                        return
                    }
                    synchronized(nmsRetryLock) {
                        nmsRetryAttempt = attempt + 1
                    }
                    XLog.i(TAG, "retrying NMS hook install attempt=${attempt + 1}/${NmsHookInstallRetryPolicy.MAX_ATTEMPTS}")
                    installRunningNotificationHooks(classLoader, fromRetry = true)
                    if (nmsHookInstallState.get() == NmsHookInstallState.INSTALLED) {
                        stopNmsHookRetry()
                        return
                    }
                    if (NmsHookInstallRetryPolicy.shouldRetry(attempt + 1)) {
                        handler.postDelayed(this, NmsHookInstallRetryPolicy.RETRY_DELAY_MS)
                    } else {
                        XLog.w(TAG, "NMS hook retry exhausted after attempt=${attempt + 1}")
                        emitSystemService(result = "error", reason = "nms_retry_exhausted", statusOk = false)
                        stopNmsHookRetry()
                    }
                }
            }
            nmsRetryTask = task
            handler.postDelayed(task, NmsHookInstallRetryPolicy.RETRY_DELAY_MS)
            emitSystemService(result = "skip", reason = "nms_retry_scheduled")
        }
    }

    private fun stopNmsHookRetry() {
        synchronized(nmsRetryLock) {
            val handler = nmsRetryHandler
            val task = nmsRetryTask
            if (handler != null && task != null) {
                handler.removeCallbacks(task)
            }
            nmsRetryTask = null
            nmsRetryAttempt = 0
            nmsRetryHandler = null
        }
    }

    private fun currentSystemContext(classLoader: ClassLoader): Context? {
        return runCatching {
            val activityThreadClass = findHookClass("android.app.ActivityThread", classLoader)
            val activityThread = activityThreadClass.callStaticMethod("currentActivityThread") ?: return@runCatching null
            activityThread.callMethod("getSystemContext") as? Context
        }.getOrNull()
    }

    private fun installXSpacePackageSyncReceiver(classLoader: ClassLoader) {
        runCatching {
            currentSystemContext(classLoader)?.let(XSpacePackageSyncHook::install)
        }.onFailure {
            XLog.d(TAG, "skip immediate XSpace package sync receiver install: ${it.message}")
        }
    }

    private fun hookSystemReadyFlag(stubClass: Class<Any>) {
        XLog.d(TAG, "install system ready flag hook on ${stubClass.name}")
        stubClass.hookMethod("isSystemConditionProviderEnabled", String::class.java) {
            doBefore {
                if (args[0] == IS_SYSTEM_HOOK_READY || args[0] == XMSF_FAKE_CONDITION_PROVIDER_PATH) {
                    XLog.d(TAG, "force system condition provider enabled: ${args[0]}")
                    result = true
                }
            }
        }
    }

    private fun hookPermission(stubClass: Class<Any>) {
        XLog.d(TAG, "install permission hook on ${stubClass.name}")
        NmsPermissionHooker.hook(stubClass)
    }
}
