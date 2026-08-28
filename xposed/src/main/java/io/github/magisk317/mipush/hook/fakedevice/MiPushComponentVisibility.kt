package io.github.magisk317.mipush.hook.fakedevice

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageInfo
import android.content.pm.PermissionInfo
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.os.Process
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.hook
import io.github.magisk317.xposed.logging.MagiskOtel
import java.lang.reflect.Method
import java.util.Collections

class MiPushComponentVisibility : IFakeDevice {
    companion object {
        private const val TAG = "MiPushComponentVisibility"
        private const val MAX_LOGS_PER_KEY = 8
        private const val XMSF_PACKAGE = MiPushComponentVisibilityQueryPolicy.XMSF_PACKAGE
        private const val XMSF_SERVICE = "com.xiaomi.push.service.XMPushService"
        private const val XMSF_LEGACY_SERVICE = "com.xiaomi.xmsf.push.service.XMPushService"
        private const val XMSF_JOB_SERVICE = "com.xiaomi.push.service.XMJobService"
        private const val MIPUSH_RECEIVE_ACTION = MiPushComponentVisibilityQueryPolicy.MIPUSH_RECEIVE_ACTION
        private const val MIPUSH_MIUI_RECEIVE_ACTION = MiPushComponentVisibilityQueryPolicy.MIPUSH_MIUI_RECEIVE_ACTION
        private const val MIPUSH_MIUI_CLICK_ACTION = MiPushComponentVisibilityQueryPolicy.MIPUSH_MIUI_CLICK_ACTION
        private const val MIPUSH_PING_ACTION = MiPushComponentVisibilityQueryPolicy.MIPUSH_PING_ACTION
        private const val PUSH_SERVICE_RECEIVER = "com.xiaomi.mipush.sdk.PushServiceReceiver"
        private const val PING_RECEIVER = "com.xiaomi.push.service.receivers.PingReceiver"
        private const val MIPUSH_MESSAGE_RECEIVER = "com.xiaomi.push.service.receivers.MIPushMessageHandler"
        private const val PUSH_MESSAGE_HANDLER = "com.xiaomi.mipush.sdk.PushMessageHandler"
        private const val MESSAGE_HANDLE_SERVICE = "com.xiaomi.mipush.sdk.MessageHandleService"
        private const val MIPUSH_BRIDGE_ACTIVITY = "com.xiaomi.mipush.sdk.BridgeActivity"
        private const val MIPUSH_NOTIFICATION_CLICKED_ACTIVITY = "com.xiaomi.mipush.sdk.NotificationClickedActivity"
        private const val XMSF_MAIN_ACTIVITY = "top.trumeet.mipushframework.main.MainActivity"
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
        private val providerResolveMethodNames = setOf("resolveContentProvider", "resolveContentProviderAsUser")
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
        ): Boolean = MiPushComponentVisibilityQueryPolicy.isMiPushReceiverQuery(
            action = action,
            targetPackage = targetPackage,
            ownPackage = ownPackage,
        )

        internal fun isMiPushServiceQuery(
            action: String?,
            targetPackage: String?,
            componentPackage: String?,
            ownPackage: String,
        ): Boolean = MiPushComponentVisibilityQueryPolicy.isMiPushServiceQuery(
            action = action,
            targetPackage = targetPackage,
            componentPackage = componentPackage,
            ownPackage = ownPackage,
        )

        internal fun isMiPushActivityQuery(
            action: String?,
            targetPackage: String?,
            ownPackage: String,
        ): Boolean = MiPushComponentVisibilityQueryPolicy.isMiPushActivityQuery(
            action = action,
            targetPackage = targetPackage,
            ownPackage = ownPackage,
        )

        internal fun shouldPatchInstallerQuery(queryPackage: String?, ownPackage: String): Boolean =
            MiPushComponentVisibilityQueryPolicy.shouldPatchInstallerQuery(queryPackage, ownPackage)

        internal fun shouldPatchProviderQuery(processName: String?): Boolean =
            MiPushComponentVisibilityQueryPolicy.shouldPatchProviderQuery(processName)



        internal fun isMiPushProviderAuthority(authority: String?): Boolean =
            MiPushComponentVisibilityQueryPolicy.isMiPushProviderAuthority(authority)
    }

    override fun fake(lpparam: LoadParam): Boolean {
        val packageName = lpparam.packageName.orEmpty()
        val processName = lpparam.processName.orEmpty()
        val classLoader = lpparam.classLoader
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
        MagiskOtel.event(
            name = "hook.load",
            attributes = mapOf(
                "result" to if (installed) "ok" else "skip",
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "component_visibility",
                "reason" to if (installed) "installed" else "no_hook",
                "target_package" to packageName.ifBlank { "unknown" },
            ),
            statusOk = true,
        )
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
            in providerResolveMethodNames -> hookProviderResolve(method, context)
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
                    XMSF_PACKAGE -> (original ?: MiPushComponentVisibilityModelFactory.fakeXmsfPackageInfo()).also { ensureXmsfServices(it) }
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
                    XMSF_PACKAGE -> MiPushComponentVisibilityModelFactory.fakeApplicationInfo(XMSF_PACKAGE, system = true)
                    context.packageName -> MiPushComponentVisibilityModelFactory.fakeApplicationInfo(context.packageName, system = false)
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
                val missingProviders = MiPushComponentVisibilityModelFactory.fakeXmsfProviderInfos().filter { provider ->
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

    private fun hookProviderResolve(method: Method, context: VisibilityContext): Boolean {
        if (!markHooked(method, context)) return false
        method.hook {
            doAfter {
                val authority = firstStringArg() ?: return@doAfter
                if (!isMiPushProviderAuthority(authority)) return@doAfter
                if (throwable == null && result is ProviderInfo) {
                    rateLimitedLog(
                        context,
                        "${method.name}:$authority:false",
                        "resolveContentProvider authority=$authority pkg=${context.packageName} " +
                            "proc=${context.processName} patched=false"
                    )
                    return@doAfter
                }
                val provider = MiPushComponentVisibilityModelFactory.fakeXmsfProviderInfos().firstOrNull { it.authority == authority }
                    ?: return@doAfter
                throwable = null
                result = provider
                rateLimitedLog(
                    context,
                    "${method.name}:$authority:true",
                    "resolveContentProvider authority=$authority pkg=${context.packageName} " +
                        "proc=${context.processName} patched=true"
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
                patched.add(MiPushComponentVisibilityModelFactory.fakeXmsfPackageInfo())
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
                patched.add(MiPushComponentVisibilityModelFactory.fakeApplicationInfo(XMSF_PACKAGE, system = true))
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

    private fun MethodHookParam.applyPatchedResult(
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

    @Suppress("DEPRECATION")
    private fun fakeOwnPackageInfo(context: VisibilityContext): PackageInfo {
        return PackageInfo().apply {
            packageName = context.packageName
            applicationInfo = MiPushComponentVisibilityModelFactory.fakeApplicationInfo(context.packageName, system = false)
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
        packageInfo.services = MiPushComponentVisibilityModelFactory.mergeServices(
            packageInfo.services,
            listOf(
                MiPushComponentVisibilityModelFactory.serviceInfo(XMSF_PACKAGE, XMSF_SERVICE, exported = false),
                MiPushComponentVisibilityModelFactory.serviceInfo(XMSF_PACKAGE, XMSF_LEGACY_SERVICE, exported = false),
                MiPushComponentVisibilityModelFactory.serviceInfo(
                    XMSF_PACKAGE,
                    XMSF_JOB_SERVICE,
                    exported = false,
                    permission = "android.permission.BIND_JOB_SERVICE"
                ),
            )
        )
        if (packageInfo.applicationInfo == null) {
            packageInfo.applicationInfo = MiPushComponentVisibilityModelFactory.fakeApplicationInfo(XMSF_PACKAGE, system = true)
        }
    }

    private fun ensureOwnMiPushComponents(packageInfo: PackageInfo, context: VisibilityContext) {
        val services = buildList {
            if (context.hasClass(PUSH_MESSAGE_HANDLER)) {
                add(MiPushComponentVisibilityModelFactory.serviceInfo(context.packageName, PUSH_MESSAGE_HANDLER, exported = true))
            }
            if (context.hasClass(MESSAGE_HANDLE_SERVICE)) {
                add(MiPushComponentVisibilityModelFactory.serviceInfo(context.packageName, MESSAGE_HANDLE_SERVICE, exported = false))
            }
            if (context.hasClass(XMSF_SERVICE)) {
                add(MiPushComponentVisibilityModelFactory.serviceInfo(context.packageName, XMSF_SERVICE, exported = false))
            }
            if (context.hasClass(XMSF_JOB_SERVICE)) {
                add(
                    MiPushComponentVisibilityModelFactory.serviceInfo(
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
                add(MiPushComponentVisibilityModelFactory.activityInfo(context.packageName, PUSH_SERVICE_RECEIVER, exported = true))
            }
            if (context.hasClass(PING_RECEIVER)) {
                add(MiPushComponentVisibilityModelFactory.activityInfo(context.packageName, PING_RECEIVER, exported = false))
            }
            if (context.hasClass(MIPUSH_MESSAGE_RECEIVER)) {
                add(MiPushComponentVisibilityModelFactory.activityInfo(context.packageName, MIPUSH_MESSAGE_RECEIVER, exported = true))
            }
        }
        val activities = buildList {
            if (context.hasClass(MIPUSH_BRIDGE_ACTIVITY)) {
                add(MiPushComponentVisibilityModelFactory.activityInfo(context.packageName, MIPUSH_BRIDGE_ACTIVITY, exported = true))
            }
            if (context.hasClass(MIPUSH_NOTIFICATION_CLICKED_ACTIVITY)) {
                add(MiPushComponentVisibilityModelFactory.activityInfo(context.packageName, MIPUSH_NOTIFICATION_CLICKED_ACTIVITY, exported = true))
            }
        }
        packageInfo.services = MiPushComponentVisibilityModelFactory.mergeServices(packageInfo.services, services)
        packageInfo.receivers = MiPushComponentVisibilityModelFactory.mergeActivities(packageInfo.receivers, receivers)
        packageInfo.activities = MiPushComponentVisibilityModelFactory.mergeActivities(packageInfo.activities, activities)
        if (packageInfo.applicationInfo == null) {
            packageInfo.applicationInfo = MiPushComponentVisibilityModelFactory.fakeApplicationInfo(context.packageName, system = false)
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
                activityInfo = MiPushComponentVisibilityModelFactory.activityInfo(
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
            targetPackage == XMSF_PACKAGE -> MiPushComponentVisibilityModelFactory.serviceInfo(XMSF_PACKAGE, XMSF_SERVICE, exported = false)
            intent.component?.packageName == XMSF_PACKAGE -> {
                MiPushComponentVisibilityModelFactory.serviceInfo(
                    XMSF_PACKAGE,
                    intent.component?.className ?: XMSF_SERVICE,
                    exported = false
                )
            }
            targetPackage == context.packageName || targetPackage == null -> {
                if (intent.action != MIPUSH_MIUI_CLICK_ACTION) return emptyList()
                val className = firstExistingClass(context, PUSH_MESSAGE_HANDLER, MESSAGE_HANDLE_SERVICE)
                    ?: return emptyList()
                MiPushComponentVisibilityModelFactory.serviceInfo(context.packageName, className, exported = true)
            }
            else -> null
        } ?: return emptyList()
        return listOf(ResolveInfo().apply { this.serviceInfo = serviceInfo })
    }

    private fun fakeActivityResolveInfos(intent: Intent, context: VisibilityContext): List<ResolveInfo> {
        val targetPackage = intent.targetPackage()
        val activityInfo = when {
            targetPackage == XMSF_PACKAGE || intent.component?.packageName == XMSF_PACKAGE -> {
                MiPushComponentVisibilityModelFactory.activityInfo(
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
                MiPushComponentVisibilityModelFactory.activityInfo(context.packageName, className, exported = true)
            }
            else -> null
        } ?: return emptyList()
        return listOf(ResolveInfo().apply { this.activityInfo = activityInfo })
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

    private fun installerPackageFor(queryPackage: String, ownPackage: String): String {
        return if (queryPackage == XMSF_PACKAGE) XIAOMI_MARKET_PACKAGE else GOOGLE_PLAY_PACKAGE
    }

    @SuppressLint("NewApi")
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

    private fun MethodHookParam.firstStringArg(): String? {
        return args.firstOrNull { it is String } as? String
    }

    private fun MethodHookParam.firstIntentArg(): Intent? {
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
