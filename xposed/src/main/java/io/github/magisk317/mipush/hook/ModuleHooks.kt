package io.github.magisk317.mipush.hook

import io.github.magisk317.mipush.common.ANDROID_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PROCESS_NAME
import io.github.magisk317.mipush.hook.documentsui.DocumentsUiXSpaceHook
import io.github.magisk317.mipush.hook.fakedevice.FakeDeviceHook
import io.github.magisk317.mipush.hook.fakedevice.ForceMiPushRegister
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
import io.github.magisk317.mipush.hook.freeze.FreezeTaskRemovedHook
import io.github.magisk317.mipush.hook.xmsf.UnlockFocusAuthHook
import io.github.magisk317.mipush.hook.widgetcenter.PersonalAssistantPickerHook
import io.github.magisk317.mipush.hook.widgetcenter.PersonalAssistantDetailHook
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.BaseLibXposedEntry
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.LibXposedHookApi
import io.github.magisk317.xposed.XposedRuntime
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.hook
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
        HookXmsf(),
        FreezeTaskRemovedHook(),
        FakeDeviceHook(),
        PersonalAssistantPickerHook(),
        PersonalAssistantDetailHook(),
    )

    override val logTag: String = TAG

    override val hookIdPrefix: String = "mipush"

    override fun installModuleRuntime(module: XposedModule, hookApi: LibXposedHookApi) {
        XposedRuntime.install(module, hookApi)
        XLog.configure()
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
        XLog.resetForLifecycle()
        ForceMiPushRegister.resetForHotReload()
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
            "com.miui.personalassistant" -> {
                resolveLoadedPackageClassLoader("com.miui.personalassistant")
                    ?.let { mapOf("com.miui.personalassistant" to it) }
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

    private companion object {
        private const val TAG = "LibXposedEntry"
        private const val SECURITY_CORE_PACKAGE_NAME = "com.miui.securitycore"
        private const val DOCUMENTS_UI_PACKAGE_NAME = "com.google.android.documentsui"
    }
}
