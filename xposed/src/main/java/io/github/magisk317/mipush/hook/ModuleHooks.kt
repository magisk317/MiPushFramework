package io.github.magisk317.mipush.hook

import android.app.Application
import android.content.Context
import android.os.Bundle
import io.github.magisk317.mipush.common.ANDROID_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PROCESS_NAME
import io.github.magisk317.mipush.common.doOnce
import io.github.magisk317.mipush.hook.documentsui.DocumentsUiXSpaceHook
import io.github.magisk317.mipush.hook.fakedevice.FakeDevice
import io.github.magisk317.mipush.hook.fakedevice.ForceMiPushRegister
import io.github.magisk317.mipush.hook.fakedevice.fakeAllBuildInProperties
import io.github.magisk317.mipush.hook.keepalive.KeepAliveHook
import io.github.magisk317.mipush.hook.securitycore.SecurityCoreXSpaceMiPushHook
import io.github.magisk317.mipush.hook.system.HookSystemService
import io.github.magisk317.mipush.hook.systemui.HookNotificationSettingsManager
import io.github.magisk317.mipush.hook.systemui.MiPushIslandHook
import io.github.magisk317.mipush.hook.systemui.HookSystemUI
import io.github.magisk317.mipush.hook.systemui.HookSystemUIPlugin
import io.github.magisk317.mipush.hook.xmsf.HookXmsf
import io.github.magisk317.mipush.hook.xmsf.UnlockFocusAuthHook
import io.github.magisk317.mipush.xposed.LoadParam
import io.github.magisk317.mipush.xposed.XposedRuntime
import io.github.magisk317.mipush.xposed.callStaticMethod
import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.findHookClass
import io.github.magisk317.mipush.xposed.getHookObjectField
import io.github.magisk317.mipush.xposed.hook
import io.github.magisk317.mipush.xposed.hookAllMethods
import io.github.magisk317.mipush.xposed.hookMethod
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap

class LibXposedEntry : XposedModule {
    @Suppress("unused", "UnusedParameter")
    constructor(xposed: XposedInterface, loadedParam: ModuleLoadedParam) : super()

    constructor() : super()

    private var processName: String = "unknown"
    private var moduleActive: Boolean = false
    private val loadedPackages = ConcurrentHashMap<String, ClassLoader>()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        val api = apiVersion
        if (api < MIN_LIBXPOSED_API_VERSION) {
            XLog.w(TAG, "skipped: apiVersion=$api < $MIN_LIBXPOSED_API_VERSION")
            moduleActive = false
            return
        }
        if (api < PREFERRED_LIBXPOSED_API_VERSION) {
            XLog.w(TAG, "running API 101 fallback: apiVersion=$api")
        } else {
            XLog.i(TAG, "running API 102 path: apiVersion=$api")
        }

        XposedRuntime.install(this, apiVersion = api)
        moduleActive = true
        processName = if (param.isSystemServer) "android" else param.processName
        XLog.i(TAG, "onModuleLoaded api=$apiVersion process=$processName framework=$frameworkName($frameworkVersionCode)")
        installTaxAttachFallbackHook()
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        if (!moduleActive) return
        loadedPackages["android"] = param.classLoader
        dispatchLoadOnce(LoadParam("android", "android", param.classLoader))
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!moduleActive) return
        loadedPackages[param.packageName] = param.classLoader
        dispatchLoadOnce(LoadParam(param.packageName, processName, param.classLoader))
    }

    private fun dispatchLoadOnce(loadParam: LoadParam) {
        loadParam.classLoader.doOnce("${loadParam.packageName}#${loadParam.processName}") {
            dispatchLoad(loadParam)
        }
    }

    private fun dispatchLoad(loadParam: LoadParam) {
        XLog.d(TAG, "package ready pkg=${loadParam.packageName} process=${loadParam.processName}")
        if (loadParam.packageName == ANDROID_PACKAGE_NAME || loadParam.packageName == "system") {
            XLog.w(TAG, "Android/system package loaded: pkg=${loadParam.packageName} process=${loadParam.processName}")
        }

        if (loadParam.packageName == ANDROID_PACKAGE_NAME) {
            if (loadParam.processName == ANDROID_PACKAGE_NAME) {
                HookSystemService().hook(loadParam.classLoader)
                KeepAliveHook().hook(loadParam.classLoader)
            }
            return
        }

        if (loadParam.packageName == "com.android.systemui") {
            HookSystemUI().hook(loadParam.classLoader)
            hookSystemUiIsland(loadParam)
            return
        }

        if (loadParam.packageName == SECURITY_CORE_PACKAGE_NAME) {
            SecurityCoreXSpaceMiPushHook().hook(loadParam.classLoader)
            return
        }

        if (loadParam.processName == DOCUMENTS_UI_PACKAGE_NAME) {
            if (loadParam.packageName == DOCUMENTS_UI_PACKAGE_NAME) {
                DocumentsUiXSpaceHook().hook(loadParam.classLoader)
            } else {
                XLog.d(TAG, "skip non-documents package in DocumentsUI process pkg=${loadParam.packageName}")
            }
            return
        }

        if (loadParam.packageName == XMSF_PACKAGE_NAME) {
            if (loadParam.processName == XMSF_PROCESS_NAME) {
                HookXmsf().hook(loadParam)
                hookXmsfFocusAuth(loadParam)
            } else if (loadParam.processName == XMSF_PACKAGE_NAME) {
                // HookSystemUI should NOT be called here
            }
            return
        }

        if (loadParam.processName.isBlank()) {
            XLog.w(TAG, "skip fake device for package without processName pkg=${loadParam.packageName}")
            return
        }

        try {
            FakeDevice.fake(loadParam)
        } catch (e: Throwable) {
            XLog.e(TAG, "fake device error for ${loadParam.packageName}", e)
        }
    }

    private fun removeHyperOSFocusNotificationPackageLimit(loadParam: LoadParam) {
        if (isHyperIslandInstalled(loadParam.classLoader)) {
            XLog.i(TAG, "skip focus unlock hooks because HyperIsland is installed")
            return
        }

        HookSystemUIPlugin(
            "miui.systemui.plugin",
            HookNotificationSettingsManager()
        ).hook(loadParam.classLoader)

        HookSystemUIPlugin("miui.systemui.plugin") { pluginLoader ->
            val tag = "HookFocusNotifUtils"
            try {
                val classFocusNotifUtils = findClass(
                    "miui.systemui.notification.focus.FocusNotifUtils",
                    pluginLoader
                )

                XLog.d(tag, "hooking canShowFocus method")
                classFocusNotifUtils.declaredMethods.find { it.name == "canShowFocus" }!!
                    .hook {
                        replace {
                            true
                        }
                    }
            } catch (e: Throwable) {
                XLog.e(tag, "hook failure: ${e.message}", e)
            }
        }.hook(loadParam.classLoader)
    }

    private fun hookSystemUiIsland(loadParam: LoadParam) {
        if (isHyperIslandInstalled(loadParam.classLoader)) {
            XLog.i(TAG, "skip systemui island hooks because HyperIsland is installed")
            return
        }
        removeHyperOSFocusNotificationPackageLimit(loadParam)
        MiPushIslandHook().hook(loadParam.classLoader)
    }

    private fun hookXmsfFocusAuth(loadParam: LoadParam) {
        if (isHyperIslandInstalled(loadParam.classLoader)) {
            XLog.i(TAG, "skip xmsf focus auth hook because HyperIsland is installed")
            return
        }
        UnlockFocusAuthHook().hook(loadParam.classLoader)
    }

    private fun isHyperIslandInstalled(classLoader: ClassLoader): Boolean {
        return runCatching {
            val appGlobals = findHookClass("android.app.AppGlobals", classLoader)
            val initialApplication = appGlobals.callStaticMethod("getInitialApplication") as? Application
            val packageManager = initialApplication?.packageManager ?: return@runCatching false
            packageManager.getPackageInfo(HYPERISLAND_PACKAGE_NAME, 0)
            true
        }.getOrDefault(false)
    }

    private fun installTaxAttachFallbackHook() {
        if (taxAttachFallbackInstalled) return
        synchronized(LibXposedEntry::class.java) {
            if (taxAttachFallbackInstalled) return
            runCatching {
                Application::class.java.hookMethod("attach", Context::class.java) {
                    doAfter {
                        val app = thisObject as? Application ?: return@doAfter
                        val context = args[0] as? Context ?: return@doAfter
                        val classLoader = context.classLoader ?: return@doAfter
                        val runtimeProcess = runCatching { Application.getProcessName() }.getOrNull().orEmpty()
                        val isTaxProcess = runtimeProcess == TAX_PACKAGE_NAME ||
                            runtimeProcess.startsWith("$TAX_PACKAGE_NAME:")
                        if (!isTaxProcess && context.packageName != TAX_PACKAGE_NAME) {
                            return@doAfter
                        }
                        XLog.i(TAG, "tax attach observed pkg=${context.packageName} proc=$runtimeProcess")
                        classLoader.doOnce("$TAX_PACKAGE_NAME#attachFallback#$runtimeProcess") {
                            applyTaxFallbackHooks(
                                packageName = TAX_PACKAGE_NAME,
                                processName = runtimeProcess.ifBlank { TAX_PACKAGE_NAME },
                                classLoader = classLoader,
                                application = app
                            )
                        }
                    }
                }
                taxAttachFallbackInstalled = true
            }.onFailure {
                XLog.e(TAG, "install tax attach fallback failed: ${it.message}", it)
            }
        }
        installTaxBindFallbackHook()
    }

    private fun installTaxBindFallbackHook() {
        if (taxBindFallbackInstalled) return
        synchronized(LibXposedEntry::class.java) {
            if (taxBindFallbackInstalled) return
            runCatching {
                val activityThreadClass = findHookClass("android.app.ActivityThread", null)
                activityThreadClass.hookAllMethods("handleBindApplication") {
                    doAfter {
                        val bindData = args.firstOrNull() ?: return@doAfter
                        val runtimeProcess = runCatching {
                            getHookObjectField(bindData, "processName") as? String
                        }.getOrNull().orEmpty()
                        val appInfo = runCatching {
                            getHookObjectField(bindData, "appInfo")
                        }.getOrNull()
                        val packageName = runCatching {
                            getHookObjectField(appInfo, "packageName") as? String
                        }.getOrNull().orEmpty()
                        if (packageName != TAX_PACKAGE_NAME && !runtimeProcess.startsWith("$TAX_PACKAGE_NAME:")) {
                            return@doAfter
                        }
                        val app = runCatching {
                            activityThreadClass.callStaticMethod("currentApplication") as? Application
                        }.getOrNull() ?: return@doAfter
                        val classLoader = app.classLoader ?: return@doAfter
                        XLog.i(TAG, "tax bind fallback fired pkg=$packageName proc=$runtimeProcess")
                        classLoader.doOnce("$TAX_PACKAGE_NAME#bindFallback#$runtimeProcess") {
                            applyTaxFallbackHooks(
                                packageName = TAX_PACKAGE_NAME,
                                processName = runtimeProcess.ifBlank { TAX_PACKAGE_NAME },
                                classLoader = classLoader,
                                application = app
                            )
                        }
                    }
                }

                Application::class.java.hookMethod("onCreate") {
                    doAfter {
                        val app = thisObject as? Application ?: return@doAfter
                        val runtimeProcess = runCatching { Application.getProcessName() }.getOrNull().orEmpty()
                        if (app.packageName != TAX_PACKAGE_NAME && !runtimeProcess.startsWith("$TAX_PACKAGE_NAME:")) {
                            return@doAfter
                        }
                        val classLoader = app.classLoader ?: return@doAfter
                        XLog.i(TAG, "tax onCreate fallback fired pkg=${app.packageName} proc=$runtimeProcess")
                        classLoader.doOnce("$TAX_PACKAGE_NAME#onCreateFallback#$runtimeProcess") {
                            applyTaxFallbackHooks(
                                packageName = TAX_PACKAGE_NAME,
                                processName = runtimeProcess.ifBlank { TAX_PACKAGE_NAME },
                                classLoader = classLoader,
                                application = app
                            )
                        }
                    }
                }
                taxBindFallbackInstalled = true
            }.onFailure {
                XLog.e(TAG, "install tax bind fallback failed: ${it.message}", it)
            }
        }
    }

    private fun applyTaxFallbackHooks(
        packageName: String,
        processName: String,
        classLoader: ClassLoader,
        application: Application
    ) {
        runCatching { fakeAllBuildInProperties() }
            .onFailure { XLog.e(TAG, "tax fallback fake properties failed: ${it.message}", it) }

        runCatching {
            classLoader.findClass("com.alipay.pushsdk.thirdparty.xiaomi.XiaoMIPushWorker")
                .hookMethod("isSupport") { replace { true } }
        }
        runCatching {
            classLoader.findClass("com.alibaba.sdk.android.push.channel.XiaomiPushUtils")
                .hookMethod("isMiui") { replace { true } }
        }

        runCatching {
            ForceMiPushRegister.hookFromRuntime(
                packageName = packageName,
                processName = processName,
                classLoader = classLoader,
                application = application
            )
        }.onFailure {
            XLog.e(TAG, "tax fallback register hook failed: ${it.message}", it)
        }
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        return runCatching {
            val state = createHotReloadState()
            param.setSavedInstanceState(state)
            XLog.i(TAG, "onHotReloading accepted process=$processName packages=${state.getStringArrayList(STATE_LOADED_PACKAGES).orEmpty()}")
            true
        }.getOrElse { t ->
            XLog.e(TAG, "hot reload rejected: ${t.message}", t)
            false
        }
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        val api = apiVersion
        XposedRuntime.install(this, apiVersion = api)
        val hookApi = XposedRuntime.hookApi ?: return
        moduleActive = true
        val oldHookHandles = param.oldHookHandles.toList()
        hookApi.beginHotReload(oldHookHandles)
        val removed = try {
            restoreHotReloadState(param.savedInstanceState, oldHookHandles)
            val currentTargets = resolveCurrentProcessTargets(param, oldHookHandles)
            XLog.i(TAG, "onHotReloaded replay process=$processName oldHooks=${oldHookHandles.size} currentTargets=${currentTargets.keys} packages=${loadedPackages.keys}")
            currentTargets.forEach { (pkg, cl) ->
                loadedPackages.putIfAbsent(pkg, cl)
            }
            loadedPackages.forEach { (pkg, cl) ->
                dispatchLoadOnce(LoadParam(pkg, processName, cl))
            }
            hookApi.finishHotReload()
        } catch (t: Throwable) {
            hookApi.abortHotReload()
            XLog.e(TAG, "hot reload failed", t)
            throw t
        }
        XLog.i(TAG, "onHotReloaded: replaced hooks, removed $removed stale hooks")
    }

    private fun createHotReloadState(): Bundle {
        return Bundle().apply {
            putString(STATE_PROCESS_NAME, processName)
            putStringArrayList(STATE_LOADED_PACKAGES, ArrayList(loadedPackages.keys.sorted()))
        }
    }

    private fun restoreHotReloadState(
        savedState: Any?,
        oldHookHandles: Iterable<XposedInterface.HookHandle>,
    ) {
        val state = savedState as? Bundle ?: return
        processName = state.getString(STATE_PROCESS_NAME) ?: processName
        val packages = state.getStringArrayList(STATE_LOADED_PACKAGES) ?: return
        loadedPackages.clear()
        packages.forEach { pkg ->
            val classLoader = resolveLoadedPackageClassLoader(pkg, oldHookHandles)
            if (classLoader == null) {
                XLog.w(TAG, "hot reload skipped package without classloader: $pkg")
            } else {
                loadedPackages[pkg] = classLoader
            }
        }
    }

    private fun resolveCurrentProcessTargets(
        param: ModuleLoadedParam,
        oldHookHandles: Iterable<XposedInterface.HookHandle>,
    ): Map<String, ClassLoader> {
        val process = if (param.isSystemServer) ANDROID_PACKAGE_NAME else param.processName
        return when (process) {
            ANDROID_PACKAGE_NAME, "system", "system_server" -> {
                resolveSystemServerClassLoader(oldHookHandles)
                    ?.let { mapOf(ANDROID_PACKAGE_NAME to it) }
                    ?: emptyMap()
            }
            "com.android.systemui" -> {
                val classLoader = resolveLoadedPackageClassLoader("com.android.systemui") ?: resolveContextClassLoader()
                mapOf("com.android.systemui" to classLoader)
            }
            XMSF_PACKAGE_NAME, XMSF_PROCESS_NAME -> {
                val classLoader = resolveLoadedPackageClassLoader(XMSF_PACKAGE_NAME) ?: resolveContextClassLoader()
                mapOf(XMSF_PACKAGE_NAME to classLoader)
            }
            DOCUMENTS_UI_PACKAGE_NAME -> {
                val classLoader = resolveLoadedPackageClassLoader(DOCUMENTS_UI_PACKAGE_NAME) ?: resolveContextClassLoader()
                mapOf(DOCUMENTS_UI_PACKAGE_NAME to classLoader)
            }
            SECURITY_CORE_PACKAGE_NAME -> {
                val classLoader = resolveLoadedPackageClassLoader(SECURITY_CORE_PACKAGE_NAME) ?: resolveContextClassLoader()
                mapOf(SECURITY_CORE_PACKAGE_NAME to classLoader)
            }
            else -> emptyMap()
        }
    }

    private fun resolveLoadedPackageClassLoader(
        packageName: String,
        oldHookHandles: Iterable<XposedInterface.HookHandle> = emptyList(),
    ): ClassLoader? {
        if (packageName == ANDROID_PACKAGE_NAME || packageName == "system") {
            return resolveSystemServerClassLoader(oldHookHandles)
        }
        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val activityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null) ?: return null
            listOf("mPackages", "mResourcePackages").firstNotNullOfOrNull { fieldName ->
                val field = activityThreadClass.getDeclaredField(fieldName).apply { isAccessible = true }
                val packages = field.get(activityThread) as? Map<*, *> ?: return@firstNotNullOfOrNull null
                val loadedApkRef = packages[packageName] ?: return@firstNotNullOfOrNull null
                val loadedApk = if (loadedApkRef is WeakReference<*>) {
                    loadedApkRef.get() ?: return@firstNotNullOfOrNull null
                } else {
                    loadedApkRef
                }
                loadedApk.javaClass
                    .getDeclaredMethod("getClassLoader")
                    .apply { isAccessible = true }
                    .invoke(loadedApk) as? ClassLoader
            }
        }.getOrElse { t ->
            XLog.w(TAG, "hot reload classloader resolve failed for $packageName: ${t.message}")
            null
        }
    }

    private fun resolveSystemServerClassLoader(
        oldHookHandles: Iterable<XposedInterface.HookHandle> = emptyList(),
    ): ClassLoader? {
        val handleLoader = oldHookHandles.asSequence()
            .mapNotNull { handle ->
                runCatching { handle.executable.declaringClass.classLoader }.getOrNull()
            }
            .firstOrNull(::canLoadSystemServerHooks)
        if (handleLoader != null) return handleLoader

        val contextLoader = resolveContextClassLoader()
        if (canLoadSystemServerHooks(contextLoader)) return contextLoader

        XLog.w(TAG, "hot reload skipped system_server without a valid system classloader")
        return null
    }

    private fun canLoadSystemServerHooks(classLoader: ClassLoader): Boolean {
        return SYSTEM_SERVER_SENTINEL_CLASSES.any { className ->
            runCatching {
                Class.forName(className, false, classLoader)
            }.isSuccess
        }
    }

    private fun resolveContextClassLoader(): ClassLoader {
        return Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
    }

    private companion object {
        private const val TAG = "LibXposedEntry"
        private const val MIN_LIBXPOSED_API_VERSION = 102
        private const val PREFERRED_LIBXPOSED_API_VERSION = 102
        private const val STATE_PROCESS_NAME = "processName"
        private const val STATE_LOADED_PACKAGES = "loadedPackages"
        private const val TAX_PACKAGE_NAME = "cn.gov.tax.its"
        private const val HYPERISLAND_PACKAGE_NAME = "io.github.hyperisland"
        private const val SECURITY_CORE_PACKAGE_NAME = "com.miui.securitycore"
        private const val DOCUMENTS_UI_PACKAGE_NAME = "com.google.android.documentsui"
        private val SYSTEM_SERVER_SENTINEL_CLASSES = arrayOf(
            "com.android.server.notification.NotificationManagerService",
            "com.android.server.am.ActivityManagerService",
            "com.android.server.SystemServer",
        )

        @Volatile
        private var taxAttachFallbackInstalled = false

        @Volatile
        private var taxBindFallbackInstalled = false
    }
}
