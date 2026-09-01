package io.github.magisk317.mipush.hook

import android.app.Application
import android.content.Context
import io.github.magisk317.mipush.common.ANDROID_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PROCESS_NAME
import io.github.magisk317.mipush.common.doOnce
import io.github.magisk317.mipush.hook.amap.AmapNavigationLiveViewHook
import io.github.magisk317.mipush.hook.documentsui.DocumentsUiXSpaceHook
import io.github.magisk317.mipush.hook.fakedevice.FakeDeviceHook
import io.github.magisk317.mipush.hook.fakedevice.ForceMiPushRegister
import io.github.magisk317.mipush.hook.fakedevice.fakeAllBuildInProperties
import io.github.magisk317.mipush.hook.island.IslandDispatcherHook
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.mipush.hook.keepalive.KeepAliveHook
import io.github.magisk317.mipush.hook.securitycore.SecurityCoreXSpaceMiPushHook
import io.github.magisk317.mipush.hook.system.HookSystemService
import io.github.magisk317.mipush.hook.systemui.FocusNotificationPermissionPolicy
import io.github.magisk317.mipush.hook.systemui.HookFocusAuthorization
import io.github.magisk317.mipush.hook.systemui.HookNotificationSettingsManager
import io.github.magisk317.mipush.hook.systemui.MiPushIslandHook
import io.github.magisk317.mipush.hook.systemui.HookSystemUI
import io.github.magisk317.mipush.hook.systemui.HookSystemUIPlugin
import io.github.magisk317.mipush.hook.systemui.ISystemUIPluginHooker
import io.github.magisk317.mipush.hook.xmsf.HookXmsf
import io.github.magisk317.mipush.hook.xmsf.UnlockFocusAuthHook
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.BaseLibXposedEntry
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.LibXposedHookApi
import io.github.magisk317.xposed.findHookClass
import io.github.magisk317.xposed.callStaticMethod
import io.github.magisk317.xposed.XposedRuntime
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.getHookObjectField
import io.github.magisk317.xposed.hook
import io.github.magisk317.xposed.hookAllMethods
import io.github.magisk317.xposed.hookMethod
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.magisk317.xposed.logging.MagiskOtel

class LibXposedEntry : BaseLibXposedEntry {

    @Suppress("unused", "UnusedParameter")
    constructor(xposed: io.github.libxposed.api.XposedInterface, loadedParam: ModuleLoadedParam) : super(xposed, loadedParam)
    constructor() : super()

    override val hooks: List<BaseHook> = listOf(
        HookSystemService(),
        KeepAliveHook(),
        HookSystemUI(),
        SecurityCoreXSpaceMiPushHook(),
        DocumentsUiXSpaceHook(),
        AmapNavigationLiveViewHook(),
        HookXmsf(),
        FakeDeviceHook(),
    )

    override val logTag: String = TAG

    override val hookIdPrefix: String = "mipush"

    override fun installModuleRuntime(module: XposedModule, hookApi: LibXposedHookApi) {
        XposedRuntime.install(module, hookApi)
        XLog.configure()
        // These hooks are outside the BaseHook dispatch list. Install them for every runtime
        // generation so hot reload can replace their old handles instead of leaving them stale.
        installTaxAttachFallbackHook()
        // Pull sensitive-debug pref into LogSanitizerConfig for hook processes.
        IslandPreferences.startRefreshLoop()
        MagiskOtel.event(
            name = "hook.load",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "module_runtime",
                "reason" to "installed",
                "source" to "mipush",
            ),
            statusOk = true,
        )
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        // IslandPreferences is also started from installModuleRuntime, outside the BaseHook list.
        // Stop its threads, executor and registered receiver while this old module ClassLoader is
        // still reachable; otherwise autoHotReload retains every loaded module DEX generation.
        IslandPreferences.stopRefreshLoop()
        ForceMiPushRegister.resetForHotReload()
        synchronized(LibXposedEntry::class.java) {
            // These fallback hooks are installed outside the BaseHook list. Allow the next
            // runtime generation to install them again, even if the framework reuses this loader.
            taxAttachFallbackInstalled = false
            taxBindFallbackInstalled = false
        }
        return super.onHotReloading(param)
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        MagiskOtel.event(
            name = "hook.load",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to if (param.isSystemServer) "system_server" else "hook",
                "stage" to "module_loaded",
                "reason" to "loaded",
                "source" to param.processName.ifBlank { "unknown" },
            ),
            statusOk = true,
        )
    }

    override fun postDispatch(loadParam: LoadParam) {
        if (loadParam.packageName == "com.android.systemui") {
            hookSystemUiIsland(loadParam)
        }
        if (loadParam.packageName == XMSF_PACKAGE_NAME) {
            hookXmsfFocusAuth(loadParam)
        }
    }

    // -- MiPushFramework-specific: resolve targets for hot reload --
    override fun resolveCurrentProcessTargets(param: ModuleLoadedParam): Map<String, ClassLoader> {
        val process = if (param.isSystemServer) ANDROID_PACKAGE_NAME else param.processName
        return when (process) {
            ANDROID_PACKAGE_NAME, "system", "system_server" -> {
                resolveSystemServerClassLoader()
                    ?.let { mapOf(ANDROID_PACKAGE_NAME to it) }
                    ?: emptyMap()
            }
            "com.android.systemui" -> {
                resolveLoadedPackageClassLoader("com.android.systemui")
                    ?.let { mapOf("com.android.systemui" to it) }
                    ?: emptyMap()
            }
            XMSF_PACKAGE_NAME, XMSF_PROCESS_NAME -> {
                resolveLoadedPackageClassLoader(XMSF_PACKAGE_NAME)
                    ?.let { mapOf(XMSF_PACKAGE_NAME to it) }
                    ?: emptyMap()
            }
            DOCUMENTS_UI_PACKAGE_NAME -> {
                resolveLoadedPackageClassLoader(DOCUMENTS_UI_PACKAGE_NAME)
                    ?.let { mapOf(DOCUMENTS_UI_PACKAGE_NAME to it) }
                    ?: emptyMap()
            }
            SECURITY_CORE_PACKAGE_NAME -> {
                resolveLoadedPackageClassLoader(SECURITY_CORE_PACKAGE_NAME)
                    ?.let { mapOf(SECURITY_CORE_PACKAGE_NAME to it) }
                    ?: emptyMap()
            }
            AMAP_PACKAGE_NAME -> {
                resolveLoadedPackageClassLoader(AMAP_PACKAGE_NAME)
                    ?.let { mapOf(AMAP_PACKAGE_NAME to it) }
                    ?: emptyMap()
            }
            else -> emptyMap()
        }
    }

    // -- SystemUI island hooks --
    private fun installFocusAuthorizationBypass(loadParam: LoadParam) {
        HookNotificationSettingsManager().hook(loadParam.classLoader)

        val focusNotifUtilsHooker = ISystemUIPluginHooker { pluginLoader ->
            val tag = "HookFocusNotifUtils"
            val classFocusNotifUtils = runCatching {
                findClass(
                    "miui.systemui.notification.focus.FocusNotifUtils",
                    pluginLoader
                )
            }.onFailure {
                XLog.d(tag, "skip optional legacy FocusNotifUtils hook: ${it.message}")
            }.getOrNull() ?: return@ISystemUIPluginHooker

            val method = classFocusNotifUtils.declaredMethods.firstOrNull {
                it.name == "canShowFocus" &&
                    (it.returnType == Boolean::class.javaPrimitiveType || it.returnType == Boolean::class.java)
            } ?: run {
                XLog.d(tag, "skip optional legacy FocusNotifUtils hook: canShowFocus not found")
                return@ISystemUIPluginHooker
            }
            val paramTypes = method.parameterTypes
            // Identify which parameter index holds the package name (String).
            // Known signatures: canShowFocus(Context, String) or canShowFocus(String).
            val pkgArgIndex = paramTypes.indexOfFirst { it == String::class.java }

            XLog.d(tag, "hooking canShowFocus paramTypes=${paramTypes.map { it.simpleName }} pkgArgIndex=$pkgArgIndex")
            method.hook {
                doAfter {
                    val originalAllowed = result as? Boolean ?: return@doAfter
                    val packageName = if (pkgArgIndex >= 0) args[pkgArgIndex] as? String else null
                    val miPushAllowed = FocusNotificationPermissionPolicy.miPushPreferenceAllows(packageName)
                    val allowed = FocusNotificationPermissionPolicy.merge(
                        systemAllowed = originalAllowed,
                        miPushAllowed = miPushAllowed,
                    )
                    if (allowed != originalAllowed) {
                        XLog.d(tag, "canShowFocus pkg=$packageName system=$originalAllowed mipush=$miPushAllowed -> $allowed")
                        result = allowed
                    }
                }
            }
        }

        val pluginHookers = mutableListOf<ISystemUIPluginHooker>(
            HookNotificationSettingsManager(),
            HookFocusAuthorization(),
            focusNotifUtilsHooker,
        )
        HookSystemUIPlugin(
            "miui.systemui.plugin",
            *pluginHookers.toTypedArray(),
        ).hook(loadParam.classLoader)
    }

    private fun hookSystemUiIsland(loadParam: LoadParam) {
        // XMSF sends generated-focus requests to this project-private receiver in SystemUI.
        IslandDispatcherHook().hook()
        installFocusAuthorizationBypass(loadParam)
        MiPushIslandHook().onLoadPackage(loadParam)
    }

    private fun hookXmsfFocusAuth(loadParam: LoadParam) {
        UnlockFocusAuthHook().onLoadPackage(loadParam)
    }

    // -- Tax app fallback hooks --
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
        }.onFailure { XLog.d(TAG, "XiaoMIPushWorker hook skipped: ${it.message}") }
        runCatching {
            classLoader.findClass("com.alibaba.sdk.android.push.channel.XiaomiPushUtils")
                .hookMethod("isMiui") { replace { true } }
        }.onFailure { XLog.d(TAG, "XiaomiPushUtils hook skipped: ${it.message}") }

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

    private companion object {
        private const val TAG = "LibXposedEntry"
        private const val TAX_PACKAGE_NAME = "cn.gov.tax.its"
        private const val SECURITY_CORE_PACKAGE_NAME = "com.miui.securitycore"
        private const val DOCUMENTS_UI_PACKAGE_NAME = "com.google.android.documentsui"
        private const val AMAP_PACKAGE_NAME = "com.autonavi.minimap"

        @Volatile
        private var taxAttachFallbackInstalled = false

        @Volatile
        private var taxBindFallbackInstalled = false
    }
}
