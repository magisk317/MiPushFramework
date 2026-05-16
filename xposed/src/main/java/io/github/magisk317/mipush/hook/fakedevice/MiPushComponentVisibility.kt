package io.github.magisk317.mipush.hook.fakedevice

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageInfo
import android.content.pm.PermissionInfo
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Process
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.hook
import java.lang.reflect.Method
import java.util.Collections

class MiPushComponentVisibility : IFakeDevice {
    companion object {
        private const val TAG = "MiPushComponentVisibility"
        private const val MAX_LOGS_PER_KEY = 8
        private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
        private const val XMSF_SERVICE = "com.xiaomi.push.service.XMPushService"
        private const val XMSF_LEGACY_SERVICE = "com.xiaomi.xmsf.push.service.XMPushService"
        private const val XMSF_JOB_SERVICE = "com.xiaomi.push.service.XMJobService"
        private const val MIPUSH_RECEIVE_ACTION = "com.xiaomi.mipush.RECEIVE_MESSAGE"
        private const val MIPUSH_MIUI_RECEIVE_ACTION = "com.xiaomi.mipush.miui.RECEIVE_MESSAGE"
        private const val MIPUSH_MIUI_CLICK_ACTION = "com.xiaomi.mipush.miui.CLICK_MESSAGE"
        private const val MIPUSH_PING_ACTION = "com.xiaomi.push.PING_TIMER"
        private const val PUSH_SERVICE_RECEIVER = "com.xiaomi.mipush.sdk.PushServiceReceiver"
        private const val PING_RECEIVER = "com.xiaomi.push.service.receivers.PingReceiver"
        private const val MIPUSH_MESSAGE_RECEIVER = "com.xiaomi.push.service.receivers.MIPushMessageHandler"
        private const val PUSH_MESSAGE_HANDLER = "com.xiaomi.mipush.sdk.PushMessageHandler"
        private const val MESSAGE_HANDLE_SERVICE = "com.xiaomi.mipush.sdk.MessageHandleService"
        private const val MIPUSH_BRIDGE_ACTIVITY = "com.xiaomi.mipush.sdk.BridgeActivity"
        private const val MIPUSH_NOTIFICATION_CLICKED_ACTIVITY = "com.xiaomi.mipush.sdk.NotificationClickedActivity"
        private const val XMSF_MAIN_ACTIVITY = "top.trumeet.mipushframework.main.MainActivity"
        private const val CHANNEL_PROVIDER = "com.xiaomi.xmsf.provider.ChannelProvider"
        private const val PUSH_SUPPORT_PROVIDER = "com.xiaomi.push.provider.PushSupportProvider"
        private const val PUSH_COMMON_PROVIDER = "com.xiaomi.push.provider.PushCommonProvider"
        private const val PUSH_PROFILE_ID_PROVIDER = "com.xiaomi.xmsf.provider.PushProfileIdProvider"
        private const val CHANNEL_AUTHORITY = "com.xiaomi.xmsf.provider.CHANNEL"
        private const val PUSH_SUPPORT_AUTHORITY = "com.xiaomi.push.provider.PUSH_SUPPORT"
        private const val PUSH_COMMON_AUTHORITY = "com.xiaomi.push.provider.PUSH_COMMON"
        private const val PUSH_PROFILE_AUTHORITY = "com.xiaomi.push.provider.profile"
        private const val CHANNEL_PERMISSION = "com.xiaomi.xmsf.permission.CHANNEL"
        private const val PUSH_SUPPORT_PERMISSION = "com.xiaomi.push.permission.PUSH_SUPPORT"
        private const val GOOGLE_PLAY_PACKAGE = "com.android.vending"
        private const val XIAOMI_MARKET_PACKAGE = "com.xiaomi.market"
        private const val PACKAGE_SOURCE_STORE = 2

        private val packageInfoMethodNames = setOf("getPackageInfo", "getPackageInfoAsUser")
        private val applicationInfoMethodNames = setOf("getApplicationInfo", "getApplicationInfoAsUser")
        private val packageUidMethodNames = setOf("getPackageUid", "getPackageUidAsUser")
        private val receiverQueryMethodNames = setOf("queryBroadcastReceivers", "queryBroadcastReceiversAsUser")
        private val serviceQueryMethodNames = setOf("queryIntentServices", "queryIntentServicesAsUser")
        private val serviceResolveMethodNames = setOf("resolveService", "resolveServiceAsUser")
        private val activityQueryMethodNames = setOf("queryIntentActivities", "queryIntentActivitiesAsUser")
        private val activityResolveMethodNames = setOf("resolveActivity", "resolveActivityAsUser")
        private val providerQueryMethodNames = setOf("queryContentProviders")
        private val installedPackageMethodNames = setOf("getInstalledPackages", "getInstalledPackagesAsUser")
        private val installedApplicationMethodNames = setOf("getInstalledApplications", "getInstalledApplicationsAsUser")
        private val installerPackageMethodNames = setOf("getInstallerPackageName", "getInstallerForPackage")
        private val installSourceInfoMethodNames = setOf("getInstallSourceInfo")

        private val hookedMethods: MutableSet<String> = Collections.synchronizedSet(HashSet())
        private val logCounts: MutableMap<String, Int> = Collections.synchronizedMap(HashMap())

        internal fun isMiPushReceiverQuery(
            action: String?,
            targetPackage: String?,
            ownPackage: String,
        ): Boolean {
            if (action !in setOf(MIPUSH_RECEIVE_ACTION, MIPUSH_MIUI_RECEIVE_ACTION, MIPUSH_PING_ACTION)) {
                return false
            }
            return targetPackage == null || targetPackage == ownPackage
        }

        internal fun isMiPushServiceQuery(
            action: String?,
            targetPackage: String?,
            componentPackage: String?,
            ownPackage: String,
        ): Boolean {
            if (targetPackage == XMSF_PACKAGE || componentPackage == XMSF_PACKAGE) return true
            if (targetPackage != null && targetPackage != ownPackage) return false
            return action == MIPUSH_MIUI_CLICK_ACTION
        }

        internal fun isMiPushActivityQuery(
            action: String?,
            targetPackage: String?,
            ownPackage: String,
        ): Boolean {
            if (targetPackage != null && targetPackage != ownPackage && targetPackage != XMSF_PACKAGE) {
                return false
            }
            val normalizedAction = action.orEmpty()
            return normalizedAction.startsWith("com.xiaomi.mipush") ||
                normalizedAction.startsWith("com.xiaomi.push")
        }

        internal fun shouldPatchInstallerQuery(queryPackage: String?, ownPackage: String): Boolean {
            return queryPackage == XMSF_PACKAGE || queryPackage == ownPackage
        }

        internal fun shouldPatchProviderQuery(processName: String?): Boolean {
            return processName == null || processName == XMSF_PACKAGE
        }
    }

    override fun fake(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        val packageName = lpparam.packageName.orEmpty()
        val processName = lpparam.processName.orEmpty()
        val classLoader = lpparam.classLoader ?: return false
        val pmClass = runCatching {
            classLoader.findClass("android.app.ApplicationPackageManager")
        }.onFailure {
            XLog.d(TAG, "ApplicationPackageManager not found for pkg=$packageName proc=$processName")
        }.getOrNull() ?: return false

        val context = VisibilityContext(
            packageName = packageName,
            processName = processName,
            classLoader = classLoader,
        )
        val methods = runCatching { pmClass.declaredMethods.toList() }.getOrDefault(emptyList())
        var installed = false
        methods.forEach { method ->
            installed = installHook(method, context) || installed
        }
        if (installed) {
            XLog.i(TAG, "installed MiPush component visibility hooks for pkg=$packageName proc=$processName")
        }
        return installed
    }

    private fun installHook(method: Method, context: VisibilityContext): Boolean {
        return when (method.name) {
            in packageInfoMethodNames -> hookPackageInfo(method, context)
            in applicationInfoMethodNames -> hookApplicationInfo(method, context)
            in packageUidMethodNames -> hookPackageUid(method, context)
            in receiverQueryMethodNames -> hookReceiverQuery(method, context)
            in serviceQueryMethodNames -> hookServiceQuery(method, context)
            in serviceResolveMethodNames -> hookServiceResolve(method, context)
            in activityQueryMethodNames -> hookActivityQuery(method, context)
            in activityResolveMethodNames -> hookActivityResolve(method, context)
            in providerQueryMethodNames -> hookProviderQuery(method, context)
            in installedPackageMethodNames -> hookInstalledPackages(method, context)
            in installedApplicationMethodNames -> hookInstalledApplications(method, context)
            in installerPackageMethodNames -> hookInstallerPackage(method, context)
            in installSourceInfoMethodNames -> hookInstallSourceInfo(method, context)
            else -> false
        }
    }

    private fun hookPackageInfo(method: Method, context: VisibilityContext): Boolean {
        if (!markHooked(method, context)) return false
        method.hook {
            doAfter {
                val queryPackage = firstStringArg() ?: return@doAfter
                val original = result as? PackageInfo
                val patched = when (queryPackage) {
                    XMSF_PACKAGE -> (original ?: fakeXmsfPackageInfo()).also { ensureXmsfServices(it) }
                    context.packageName -> (original ?: fakeOwnPackageInfo(context)).also {
                        ensureOwnMiPushComponents(it, context)
                    }
                    else -> null
                } ?: return@doAfter
                applyPatchedResult(context, method.name, queryPackage, original, patched)
            }
        }
        return true
    }

    private fun hookApplicationInfo(method: Method, context: VisibilityContext): Boolean {
        if (!markHooked(method, context)) return false
        method.hook {
            doAfter {
                val queryPackage = firstStringArg() ?: return@doAfter
                if (throwable == null && result is ApplicationInfo) return@doAfter
                val patched = when (queryPackage) {
                    XMSF_PACKAGE -> fakeApplicationInfo(XMSF_PACKAGE, system = true)
                    context.packageName -> fakeApplicationInfo(context.packageName, system = false)
                    else -> null
                } ?: return@doAfter
                throwable = null
                result = patched
                rateLimitedLog(
                    context,
                    "${method.name}:$queryPackage",
                    "patched ${method.name} query=$queryPackage pkg=${context.packageName} proc=${context.processName}"
                )
            }
        }
        return true
    }

    private fun hookPackageUid(method: Method, context: VisibilityContext): Boolean {
        if (!markHooked(method, context)) return false
        method.hook {
            doAfter {
                val queryPackage = firstStringArg() ?: return@doAfter
                if (queryPackage != XMSF_PACKAGE) return@doAfter
                if (throwable == null && result is Int) return@doAfter
                throwable = null
                result = Process.SYSTEM_UID
                rateLimitedLog(
                    context,
                    "${method.name}:$queryPackage",
                    "patched ${method.name} query=$queryPackage uid=${Process.SYSTEM_UID} " +
                        "pkg=${context.packageName} proc=${context.processName}"
                )
            }
        }
        return true
    }

    private fun hookReceiverQuery(method: Method, context: VisibilityContext): Boolean {
        if (!markHooked(method, context)) return false
        method.hook {
            doAfter {
                val intent = firstIntentArg() ?: return@doAfter
                val original = result as? List<*>
                val count = original?.size ?: 0
                if (!isMiPushReceiverQuery(intent, context)) return@doAfter
                if (throwable == null && count > 0) {
                    logComponentQuery(context, method.name, intent, count, patched = false)
                    return@doAfter
                }

                val patched = fakeReceiverResolveInfos(intent, context)
                if (patched.isEmpty()) {
                    logComponentQuery(context, method.name, intent, count, patched = false)
                    return@doAfter
                }
                throwable = null
                result = patched
                logComponentQuery(context, method.name, intent, patched.size, patched = true)
            }
        }
        return true
    }

    private fun hookServiceQuery(method: Method, context: VisibilityContext): Boolean {
        if (!markHooked(method, context)) return false
        method.hook {
            doAfter {
                val intent = firstIntentArg() ?: return@doAfter
                val original = result as? List<*>
                val count = original?.size ?: 0
                if (!isMiPushServiceQuery(intent, context)) return@doAfter
                if (throwable == null && count > 0) {
                    logComponentQuery(context, method.name, intent, count, patched = false)
                    return@doAfter
                }

                val patched = fakeServiceResolveInfos(intent, context)
                if (patched.isEmpty()) {
                    logComponentQuery(context, method.name, intent, count, patched = false)
                    return@doAfter
                }
                throwable = null
                result = patched
                logComponentQuery(context, method.name, intent, patched.size, patched = true)
            }
        }
        return true
    }

    private fun hookServiceResolve(method: Method, context: VisibilityContext): Boolean {
        if (!markHooked(method, context)) return false
        method.hook {
            doAfter {
                val intent = firstIntentArg() ?: return@doAfter
                if (!isMiPushServiceQuery(intent, context)) return@doAfter
                if (throwable == null && result is ResolveInfo) {
                    logComponentQuery(context, method.name, intent, 1, patched = false)
                    return@doAfter
                }

                val patched = fakeServiceResolveInfos(intent, context).firstOrNull() ?: return@doAfter
                throwable = null
                result = patched
                logComponentQuery(context, method.name, intent, 1, patched = true)
            }
        }
        return true
    }

    private fun hookActivityQuery(method: Method, context: VisibilityContext): Boolean {
        if (!markHooked(method, context)) return false
        method.hook {
            doAfter {
                val intent = firstIntentArg() ?: return@doAfter
                val original = result as? List<*>
                val count = original?.size ?: 0
                if (!isMiPushActivityQuery(intent, context)) return@doAfter
                if (throwable == null && count > 0) {
                    logComponentQuery(context, method.name, intent, count, patched = false)
                    return@doAfter
                }

                val patched = fakeActivityResolveInfos(intent, context)
                if (patched.isEmpty()) {
                    logComponentQuery(context, method.name, intent, count, patched = false)
                    return@doAfter
                }
                throwable = null
                result = patched
                logComponentQuery(context, method.name, intent, patched.size, patched = true)
            }
        }
        return true
    }

    private fun hookActivityResolve(method: Method, context: VisibilityContext): Boolean {
        if (!markHooked(method, context)) return false
        method.hook {
            doAfter {
                val intent = firstIntentArg() ?: return@doAfter
                if (!isMiPushActivityQuery(intent, context)) return@doAfter
                if (throwable == null && result is ResolveInfo) {
                    logComponentQuery(context, method.name, intent, 1, patched = false)
                    return@doAfter
                }

                val patched = fakeActivityResolveInfos(intent, context).firstOrNull()
                if (patched == null) {
                    logComponentQuery(context, method.name, intent, resultCount(result), patched = false)
                    return@doAfter
                }
                throwable = null
                result = patched
                logComponentQuery(context, method.name, intent, 1, patched = true)
            }
        }
        return true
    }

    private fun hookProviderQuery(method: Method, context: VisibilityContext): Boolean {
        if (!markHooked(method, context)) return false
        method.hook {
            doAfter {
                val processName = args.firstOrNull { it is String? } as? String
                if (!shouldPatchProviderQuery(processName)) return@doAfter
                val original = result as? List<*>
                val originalProviders = original?.filterIsInstance<ProviderInfo>().orEmpty()
                val missingProviders = fakeXmsfProviderInfos().filter { provider ->
                    originalProviders.none { it.authority == provider.authority }
                }
                if (missingProviders.isNotEmpty()) {
                    val patched = ArrayList<Any?>(original.orEmpty().size + missingProviders.size)
                    patched.addAll(original.orEmpty())
                    patched.addAll(missingProviders)
                    throwable = null
                    result = patched
                    logProviderQuery(context, method.name, processName, patched.size, patched = true)
                    return@doAfter
                }

                val argsSummary = args.joinToString(prefix = "[", postfix = "]") { arg ->
                    when (arg) {
                        is String -> arg
                        else -> arg?.javaClass?.simpleName ?: "null"
                    }
                }
                logProviderQuery(context, method.name, processName, resultCount(result), patched = false)
                rateLimitedLog(
                    context,
                    "${method.name}:$argsSummary",
                    "diagnostic ${method.name} pkg=${context.packageName} proc=${context.processName} " +
                        "args=$argsSummary resultCount=${resultCount(result)} throwable=${throwable?.javaClass?.simpleName}"
                )
            }
        }
        return true
    }

    private fun hookInstallerPackage(method: Method, context: VisibilityContext): Boolean {
        if (!markHooked(method, context)) return false
        method.hook {
            doAfter {
                val queryPackage = firstStringArg() ?: return@doAfter
                if (!shouldPatchInstallerQuery(queryPackage, context.packageName)) return@doAfter
                val original = result as? String
                if (throwable == null && !original.isNullOrBlank()) {
                    logInstallerQuery(context, method.name, queryPackage, original, patched = false)
                    return@doAfter
                }

                val patched = installerPackageFor(queryPackage, context.packageName)
                throwable = null
                result = patched
                logInstallerQuery(context, method.name, queryPackage, original, patched = true)
            }
        }
        return true
    }

    private fun hookInstallSourceInfo(method: Method, context: VisibilityContext): Boolean {
        if (!markHooked(method, context)) return false
        method.hook {
            doAfter {
                val queryPackage = firstStringArg() ?: return@doAfter
                if (!shouldPatchInstallerQuery(queryPackage, context.packageName)) return@doAfter
                val original = result
                if (throwable == null && original != null) {
                    logInstallSourceQuery(context, method.name, queryPackage, original, patched = false)
                    return@doAfter
                }

                val patched = fakeInstallSourceInfo(method, queryPackage, context.packageName)
                if (patched == null) {
                    logInstallSourceQuery(context, method.name, queryPackage, original, patched = false)
                    return@doAfter
                }
                throwable = null
                result = patched
                logInstallSourceQuery(context, method.name, queryPackage, original, patched = true)
            }
        }
        return true
    }

    private fun hookInstalledPackages(method: Method, context: VisibilityContext): Boolean {
        if (!markHooked(method, context)) return false
        method.hook {
            doAfter {
                val original = result as? List<*> ?: return@doAfter
                if (original.any { (it as? PackageInfo)?.packageName == XMSF_PACKAGE }) return@doAfter
                val patched = ArrayList<Any?>(original.size + 1)
                patched.addAll(original)
                patched.add(fakeXmsfPackageInfo())
                result = patched
                rateLimitedLog(
                    context,
                    method.name,
                    "patched ${method.name} to include $XMSF_PACKAGE pkg=${context.packageName} " +
                        "proc=${context.processName} count=${patched.size}"
                )
            }
        }
        return true
    }

    private fun hookInstalledApplications(method: Method, context: VisibilityContext): Boolean {
        if (!markHooked(method, context)) return false
        method.hook {
            doAfter {
                val original = result as? List<*> ?: return@doAfter
                if (original.any { (it as? ApplicationInfo)?.packageName == XMSF_PACKAGE }) return@doAfter
                val patched = ArrayList<Any?>(original.size + 1)
                patched.addAll(original)
                patched.add(fakeApplicationInfo(XMSF_PACKAGE, system = true))
                result = patched
                rateLimitedLog(
                    context,
                    method.name,
                    "patched ${method.name} to include $XMSF_PACKAGE pkg=${context.packageName} " +
                        "proc=${context.processName} count=${patched.size}"
                )
            }
        }
        return true
    }

    private fun XC_MethodHook.MethodHookParam.applyPatchedResult(
        context: VisibilityContext,
        methodName: String,
        queryPackage: String,
        original: PackageInfo?,
        patched: PackageInfo,
    ) {
        throwable = null
        result = patched
        val services = patched.services?.size ?: 0
        val receivers = patched.receivers?.size ?: 0
        rateLimitedLog(
            context,
            "$methodName:$queryPackage",
            "patched $methodName query=$queryPackage pkg=${context.packageName} proc=${context.processName} " +
                "original=${original != null} services=$services receivers=$receivers"
        )
    }

    private fun fakeXmsfPackageInfo(): PackageInfo {
        return PackageInfo().apply {
            packageName = XMSF_PACKAGE
            versionName = "7.4.67-C"
            @Suppress("DEPRECATION")
            versionCode = 70004067
            runCatching { setLongVersionCode(70004067L) }
            applicationInfo = fakeApplicationInfo(XMSF_PACKAGE, system = true)
            activities = arrayOf(activityInfo(XMSF_PACKAGE, XMSF_MAIN_ACTIVITY, exported = true))
            services = arrayOf(
                serviceInfo(XMSF_PACKAGE, XMSF_SERVICE, exported = false),
                serviceInfo(XMSF_PACKAGE, XMSF_LEGACY_SERVICE, exported = false),
                serviceInfo(
                    XMSF_PACKAGE,
                    XMSF_JOB_SERVICE,
                    exported = false,
                    permission = "android.permission.BIND_JOB_SERVICE"
                ),
            )
        }
    }

    private fun fakeOwnPackageInfo(context: VisibilityContext): PackageInfo {
        return PackageInfo().apply {
            packageName = context.packageName
            applicationInfo = fakeApplicationInfo(context.packageName, system = false)
            permissions = arrayOf(
                PermissionInfo().apply {
                    packageName = context.packageName
                    name = "${context.packageName}.permission.MIPUSH_RECEIVE"
                }
            )
            requestedPermissions = arrayOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.ACCESS_WIFI_STATE",
                "android.permission.VIBRATE",
                "${context.packageName}.permission.MIPUSH_RECEIVE",
            )
            ensureOwnMiPushComponents(this, context)
        }
    }

    private fun ensureXmsfServices(packageInfo: PackageInfo) {
        packageInfo.services = mergeServices(
            packageInfo.services,
            listOf(
                serviceInfo(XMSF_PACKAGE, XMSF_SERVICE, exported = false),
                serviceInfo(XMSF_PACKAGE, XMSF_LEGACY_SERVICE, exported = false),
                serviceInfo(
                    XMSF_PACKAGE,
                    XMSF_JOB_SERVICE,
                    exported = false,
                    permission = "android.permission.BIND_JOB_SERVICE"
                ),
            )
        )
        if (packageInfo.applicationInfo == null) {
            packageInfo.applicationInfo = fakeApplicationInfo(XMSF_PACKAGE, system = true)
        }
    }

    private fun ensureOwnMiPushComponents(packageInfo: PackageInfo, context: VisibilityContext) {
        val services = buildList {
            if (context.hasClass(PUSH_MESSAGE_HANDLER)) {
                add(serviceInfo(context.packageName, PUSH_MESSAGE_HANDLER, exported = true))
            }
            if (context.hasClass(MESSAGE_HANDLE_SERVICE)) {
                add(serviceInfo(context.packageName, MESSAGE_HANDLE_SERVICE, exported = false))
            }
            if (context.hasClass(XMSF_SERVICE)) {
                add(serviceInfo(context.packageName, XMSF_SERVICE, exported = false))
            }
            if (context.hasClass(XMSF_JOB_SERVICE)) {
                add(
                    serviceInfo(
                        context.packageName,
                        XMSF_JOB_SERVICE,
                        exported = false,
                        permission = "android.permission.BIND_JOB_SERVICE"
                    )
                )
            }
        }
        val receivers = buildList {
            if (context.hasClass(PUSH_SERVICE_RECEIVER)) {
                add(activityInfo(context.packageName, PUSH_SERVICE_RECEIVER, exported = true))
            }
            if (context.hasClass(PING_RECEIVER)) {
                add(activityInfo(context.packageName, PING_RECEIVER, exported = false))
            }
            if (context.hasClass(MIPUSH_MESSAGE_RECEIVER)) {
                add(activityInfo(context.packageName, MIPUSH_MESSAGE_RECEIVER, exported = true))
            }
        }
        val activities = buildList {
            if (context.hasClass(MIPUSH_BRIDGE_ACTIVITY)) {
                add(activityInfo(context.packageName, MIPUSH_BRIDGE_ACTIVITY, exported = true))
            }
            if (context.hasClass(MIPUSH_NOTIFICATION_CLICKED_ACTIVITY)) {
                add(activityInfo(context.packageName, MIPUSH_NOTIFICATION_CLICKED_ACTIVITY, exported = true))
            }
        }
        packageInfo.services = mergeServices(packageInfo.services, services)
        packageInfo.receivers = mergeActivities(packageInfo.receivers, receivers)
        packageInfo.activities = mergeActivities(packageInfo.activities, activities)
        if (packageInfo.applicationInfo == null) {
            packageInfo.applicationInfo = fakeApplicationInfo(context.packageName, system = false)
        }
    }

    private fun fakeReceiverResolveInfos(intent: Intent, context: VisibilityContext): List<ResolveInfo> {
        val action = intent.action
        val className = when (action) {
            MIPUSH_RECEIVE_ACTION -> firstExistingClass(context, PUSH_SERVICE_RECEIVER, MIPUSH_MESSAGE_RECEIVER)
            MIPUSH_MIUI_RECEIVE_ACTION -> firstExistingClass(context, MIPUSH_MESSAGE_RECEIVER, PUSH_SERVICE_RECEIVER)
            MIPUSH_PING_ACTION -> firstExistingClass(context, PING_RECEIVER)
            else -> null
        } ?: return emptyList()
        return listOf(
            ResolveInfo().apply {
                activityInfo = activityInfo(
                    context.packageName,
                    className,
                    exported = action != MIPUSH_PING_ACTION
                )
            }
        )
    }

    private fun fakeServiceResolveInfos(intent: Intent, context: VisibilityContext): List<ResolveInfo> {
        val targetPackage = intent.targetPackage()
        val serviceInfo = when {
            targetPackage == XMSF_PACKAGE -> serviceInfo(XMSF_PACKAGE, XMSF_SERVICE, exported = false)
            intent.component?.packageName == XMSF_PACKAGE -> {
                serviceInfo(
                    XMSF_PACKAGE,
                    intent.component?.className ?: XMSF_SERVICE,
                    exported = false
                )
            }
            targetPackage == context.packageName || targetPackage == null -> {
                if (intent.action != MIPUSH_MIUI_CLICK_ACTION) return emptyList()
                val className = firstExistingClass(context, PUSH_MESSAGE_HANDLER, MESSAGE_HANDLE_SERVICE)
                    ?: return emptyList()
                serviceInfo(context.packageName, className, exported = true)
            }
            else -> null
        } ?: return emptyList()
        return listOf(ResolveInfo().apply { this.serviceInfo = serviceInfo })
    }

    private fun fakeActivityResolveInfos(intent: Intent, context: VisibilityContext): List<ResolveInfo> {
        val targetPackage = intent.targetPackage()
        val activityInfo = when {
            targetPackage == XMSF_PACKAGE || intent.component?.packageName == XMSF_PACKAGE -> {
                activityInfo(
                    XMSF_PACKAGE,
                    intent.component?.className ?: XMSF_MAIN_ACTIVITY,
                    exported = true
                )
            }
            targetPackage == context.packageName || targetPackage == null -> {
                val className = firstExistingClass(
                    context,
                    intent.component?.takeIf { it.packageName == context.packageName }?.className.orEmpty(),
                    MIPUSH_BRIDGE_ACTIVITY,
                    MIPUSH_NOTIFICATION_CLICKED_ACTIVITY,
                ) ?: return emptyList()
                activityInfo(context.packageName, className, exported = true)
            }
            else -> null
        } ?: return emptyList()
        return listOf(ResolveInfo().apply { this.activityInfo = activityInfo })
    }

    private fun fakeXmsfProviderInfos(): List<ProviderInfo> {
        return listOf(
            providerInfo(XMSF_PACKAGE, CHANNEL_PROVIDER, CHANNEL_AUTHORITY, CHANNEL_PERMISSION),
            providerInfo(XMSF_PACKAGE, PUSH_SUPPORT_PROVIDER, PUSH_SUPPORT_AUTHORITY, PUSH_SUPPORT_PERMISSION),
            providerInfo(XMSF_PACKAGE, PUSH_COMMON_PROVIDER, PUSH_COMMON_AUTHORITY),
            providerInfo(XMSF_PACKAGE, PUSH_PROFILE_ID_PROVIDER, PUSH_PROFILE_AUTHORITY),
        )
    }

    private fun isMiPushReceiverQuery(intent: Intent, context: VisibilityContext): Boolean {
        return isMiPushReceiverQuery(
            action = intent.action,
            targetPackage = intent.targetPackage(),
            ownPackage = context.packageName,
        )
    }

    private fun isMiPushServiceQuery(intent: Intent, context: VisibilityContext): Boolean {
        return isMiPushServiceQuery(
            action = intent.action,
            targetPackage = intent.targetPackage(),
            componentPackage = intent.component?.packageName,
            ownPackage = context.packageName,
        )
    }

    private fun isMiPushActivityQuery(intent: Intent, context: VisibilityContext): Boolean {
        return isMiPushActivityQuery(
            action = intent.action,
            targetPackage = intent.targetPackage(),
            ownPackage = context.packageName,
        )
    }

    private fun Intent.targetPackage(): String? {
        return component?.packageName ?: `package`
    }

    private fun firstExistingClass(context: VisibilityContext, vararg classNames: String): String? {
        return classNames.firstOrNull { it.isNotBlank() && context.hasClass(it) }
    }

    private fun fakeApplicationInfo(packageName: String, system: Boolean): ApplicationInfo {
        return ApplicationInfo().apply {
            this.packageName = packageName
            enabled = true
            flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
            processName = packageName
        }
    }

    private fun serviceInfo(
        packageName: String,
        className: String,
        exported: Boolean,
        permission: String? = null,
    ): ServiceInfo {
        return ServiceInfo().apply {
            this.packageName = packageName
            name = className
            enabled = true
            this.exported = exported
            this.permission = permission
            processName = packageName
        }
    }

    private fun activityInfo(packageName: String, className: String, exported: Boolean): ActivityInfo {
        return ActivityInfo().apply {
            this.packageName = packageName
            name = className
            enabled = true
            this.exported = exported
            processName = packageName
        }
    }

    private fun providerInfo(
        packageName: String,
        className: String,
        authority: String,
        permission: String? = null,
    ): ProviderInfo {
        return ProviderInfo().apply {
            this.packageName = packageName
            name = className
            this.authority = authority
            enabled = true
            exported = true
            readPermission = permission
            writePermission = permission
            processName = packageName
            applicationInfo = fakeApplicationInfo(packageName, system = true)
        }
    }

    private fun mergeServices(existing: Array<ServiceInfo>?, additions: List<ServiceInfo>): Array<ServiceInfo>? {
        if (additions.isEmpty()) return existing
        val merged = LinkedHashMap<String, ServiceInfo>()
        existing.orEmpty().forEach { service -> service.name?.let { merged[it] = service } }
        additions.forEach { service -> service.name?.let { merged.putIfAbsent(it, service) } }
        return merged.values.toTypedArray()
    }

    private fun mergeActivities(existing: Array<ActivityInfo>?, additions: List<ActivityInfo>): Array<ActivityInfo>? {
        if (additions.isEmpty()) return existing
        val merged = LinkedHashMap<String, ActivityInfo>()
        existing.orEmpty().forEach { activity -> activity.name?.let { merged[it] = activity } }
        additions.forEach { activity -> activity.name?.let { merged.putIfAbsent(it, activity) } }
        return merged.values.toTypedArray()
    }

    private fun installerPackageFor(queryPackage: String, ownPackage: String): String {
        return if (queryPackage == XMSF_PACKAGE) XIAOMI_MARKET_PACKAGE else GOOGLE_PLAY_PACKAGE
    }

    private fun fakeInstallSourceInfo(method: Method, queryPackage: String, ownPackage: String): InstallSourceInfo? {
        val installerPackage = installerPackageFor(queryPackage, ownPackage)
        val constructors = method.returnType.declaredConstructors
            .sortedByDescending { it.parameterTypes.size }
        constructors.forEach { constructor ->
            val args = constructor.parameterTypes.map { type ->
                installSourceConstructorArg(type, installerPackage)
            }.toTypedArray()
            runCatching {
                constructor.isAccessible = true
                return constructor.newInstance(*args) as? InstallSourceInfo
            }
        }
        return null
    }

    private fun installSourceConstructorArg(type: Class<*>, installerPackage: String): Any? {
        return when (type) {
            String::class.java -> installerPackage
            Int::class.javaPrimitiveType, Int::class.javaObjectType -> PACKAGE_SOURCE_STORE
            Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> false
            else -> null
        }
    }

    private fun XC_MethodHook.MethodHookParam.firstStringArg(): String? {
        return args.firstOrNull { it is String } as? String
    }

    private fun XC_MethodHook.MethodHookParam.firstIntentArg(): Intent? {
        return args.firstOrNull { it is Intent } as? Intent
    }

    private fun resultCount(value: Any?): Int {
        return when (value) {
            is Collection<*> -> value.size
            is Array<*> -> value.size
            is ResolveInfo -> 1
            null -> 0
            else -> -1
        }
    }

    private fun logComponentQuery(
        context: VisibilityContext,
        methodName: String,
        intent: Intent,
        count: Int,
        patched: Boolean,
    ) {
        val action = intent.action.orEmpty()
        val target = intent.targetPackage().orEmpty()
        rateLimitedLog(
            context,
            "$methodName:$action:$target:$patched",
            "component query method=$methodName pkg=${context.packageName} proc=${context.processName} " +
                "action=$action target=$target count=$count patched=$patched"
        )
    }

    private fun logInstallerQuery(
        context: VisibilityContext,
        methodName: String,
        queryPackage: String,
        original: String?,
        patched: Boolean,
    ) {
        rateLimitedLog(
            context,
            "$methodName:$queryPackage:$patched",
            "installer query method=$methodName pkg=${context.packageName} proc=${context.processName} " +
                "query=$queryPackage original=${original.orEmpty()} patched=$patched"
        )
    }

    private fun logInstallSourceQuery(
        context: VisibilityContext,
        methodName: String,
        queryPackage: String,
        original: Any?,
        patched: Boolean,
    ) {
        rateLimitedLog(
            context,
            "$methodName:$queryPackage:$patched",
            "install source query method=$methodName pkg=${context.packageName} proc=${context.processName} " +
                "query=$queryPackage originalType=${original?.javaClass?.name.orEmpty()} patched=$patched"
        )
    }

    private fun logProviderQuery(
        context: VisibilityContext,
        methodName: String,
        processName: String?,
        count: Int,
        patched: Boolean,
    ) {
        rateLimitedLog(
            context,
            "$methodName:${processName.orEmpty()}:$patched",
            "provider query method=$methodName pkg=${context.packageName} proc=${context.processName} " +
                "process=${processName.orEmpty()} count=$count patched=$patched"
        )
    }

    private fun markHooked(method: Method, context: VisibilityContext): Boolean {
        val key = "${context.packageName}@${context.processName}#${method.toGenericString()}"
        return hookedMethods.add(key)
    }

    private fun rateLimitedLog(context: VisibilityContext, key: String, message: String) {
        val fullKey = "${context.packageName}@${context.processName}#$key"
        val count = logCounts.getOrDefault(fullKey, 0)
        if (count >= MAX_LOGS_PER_KEY) return
        logCounts[fullKey] = count + 1
        XLog.i(TAG, message)
    }

    private data class VisibilityContext(
        val packageName: String,
        val processName: String,
        val classLoader: ClassLoader,
    ) {
        fun hasClass(className: String): Boolean {
            return runCatching { classLoader.findClass(className) }.isSuccess
        }
    }
}
