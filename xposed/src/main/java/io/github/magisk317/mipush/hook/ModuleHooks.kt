package io.github.magisk317.mipush.hook

import android.app.Application
import android.content.Context
import io.github.magisk317.mipush.common.ANDROID_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PROCESS_NAME
import io.github.magisk317.mipush.common.doOnce
import io.github.magisk317.mipush.hook.fakedevice.FakeDevice
import io.github.magisk317.mipush.hook.fakedevice.ForceMiPushRegister
import io.github.magisk317.mipush.hook.fakedevice.fakeAllBuildInProperties
import io.github.magisk317.mipush.hook.keepalive.KeepAliveHook
import io.github.magisk317.mipush.hook.system.HookSystemService
import io.github.magisk317.mipush.hook.systemui.HookNotificationSettingsManager
import io.github.magisk317.mipush.hook.systemui.HookSystemUI
import io.github.magisk317.mipush.hook.systemui.HookSystemUIPlugin
import io.github.magisk317.mipush.hook.xmsf.HookXmsf
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

class LibXposedEntry : XposedModule {
    @Suppress("unused", "UnusedParameter")
    constructor(xposed: XposedInterface, loadedParam: ModuleLoadedParam) : super()

    constructor() : super()

    private var processName: String = "unknown"

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        XposedRuntime.install(this)
        processName = if (param.isSystemServer) "android" else param.processName
        XLog.i(TAG, "onModuleLoaded api=$apiVersion process=$processName framework=$frameworkName($frameworkVersionCode)")
        installTaxAttachFallbackHook()
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        dispatchLoadOnce(LoadParam("android", "android", param.classLoader))
    }

    override fun onPackageReady(param: PackageReadyParam) {
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
            removeHyperOSFocusNotificationPackageLimit(loadParam)
            return
        }

        if (loadParam.packageName == XMSF_PACKAGE_NAME) {
            if (loadParam.processName == XMSF_PROCESS_NAME) {
                HookXmsf().hook(loadParam)
            } else if (loadParam.processName == XMSF_PACKAGE_NAME) {
                HookSystemUI().hook(loadParam.classLoader)
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

    private companion object {
        private const val TAG = "LibXposedEntry"
        private const val TAX_PACKAGE_NAME = "cn.gov.tax.its"

        @Volatile
        private var taxAttachFallbackInstalled = false

        @Volatile
        private var taxBindFallbackInstalled = false
    }
}
