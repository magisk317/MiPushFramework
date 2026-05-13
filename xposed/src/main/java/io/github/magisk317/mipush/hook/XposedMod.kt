package io.github.magisk317.mipush.hook

import android.app.Application
import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.magisk317.mipush.common.ANDROID_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PROCESS_NAME
import io.github.magisk317.mipush.common.doOnce
import io.github.magisk317.mipush.hook.fakedevice.FakeDevice
import io.github.magisk317.mipush.hook.fakedevice.ForceMiPushRegister
import io.github.magisk317.mipush.hook.fakedevice.fakeAllBuildInProperties
import io.github.magisk317.mipush.hook.xmsf.HookXmsf
import io.github.magisk317.mipush.hook.system.HookSystemService
import io.github.magisk317.mipush.hook.systemui.HookNotificationSettingsManager
import io.github.magisk317.mipush.hook.systemui.HookSystemUIPlugin
import io.github.magisk317.mipush.hook.keepalive.KeepAliveHook

import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.hook
import io.github.magisk317.mipush.xposed.hookAllMethods
import io.github.magisk317.mipush.xposed.hookMethod


class XposedMod : IXposedHookLoadPackage, IXposedHookZygoteInit {
    init {
        XLog.i(TAG, "XposedMod instance created")
        installTaxAttachFallbackHook()
    }

    companion object {
        private const val TAG = "XposedMod"
        private const val TAX_PACKAGE_NAME = "cn.gov.tax.its"
        @Volatile
        private var taxAttachFallbackInstalled = false
        @Volatile
        private var taxBindFallbackInstalled = false
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        try {
            val packageName = safeLoadPackageString(lpparam, "packageName")
            val processName = safeLoadPackageString(lpparam, "processName")
            if (packageName == TAX_PACKAGE_NAME || processName.startsWith("$TAX_PACKAGE_NAME:")) {
                XLog.i(TAG, "enter handleLoadPackage pkg=$packageName proc=$processName cl=${lpparam.classLoader}")
            }
            val classLoader = lpparam.classLoader
            if (classLoader == null) {
                XLog.w(TAG, "skip package without classLoader pkg=$packageName proc=$processName")
                return
            }
            classLoader.doOnce("$packageName#$processName") {
                hook(lpparam, packageName, processName, classLoader)
            }
        } catch (e: Throwable) {
            XLog.e(TAG, "critical error in handleLoadPackage: ${e.message}", e)
        }
    }

    private fun safeLoadPackageString(lpparam: LoadPackageParam, fieldName: String): String {
        return runCatching {
            lpparam.javaClass.getField(fieldName).get(lpparam) as? String
        }.getOrNull().orEmpty()
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        XLog.i(TAG, "initZygote called")
        installTaxAttachFallbackHook()
    }

    private fun installTaxAttachFallbackHook() {
        if (taxAttachFallbackInstalled) return
        synchronized(XposedMod::class.java) {
            if (taxAttachFallbackInstalled) return
            runCatching {
                Application::class.java.hookMethod("attach", Context::class.java) {
                    doAfter {
                        val app = thisObject as? Application ?: return@doAfter
                        val context = args[0] as? Context ?: return@doAfter
                        val classLoader = context.classLoader ?: return@doAfter
                        val processName = runCatching { Application.getProcessName() }.getOrNull() ?: ""
                        val isTaxProcess = processName == TAX_PACKAGE_NAME ||
                            processName.startsWith("$TAX_PACKAGE_NAME:")
                        if (!isTaxProcess && context.packageName != TAX_PACKAGE_NAME) {
                            return@doAfter
                        }
                        XLog.i(TAG, "attach observed pkg=${context.packageName} proc=$processName")
                        classLoader.doOnce("$TAX_PACKAGE_NAME#attachFallback#$processName") {
                            XLog.i(TAG, "tax attach fallback fired pkg=${context.packageName} proc=$processName")
                            applyTaxFallbackHooks(
                                packageName = TAX_PACKAGE_NAME,
                                processName = processName,
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
        synchronized(XposedMod::class.java) {
            if (taxBindFallbackInstalled) return
            runCatching {
                // Some packed apps skip normal load-package callbacks. Bind-time hook is a fallback.
                val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null)
                activityThreadClass.hookAllMethods("handleBindApplication") {
                    doAfter {
                        val bindData = args.firstOrNull() ?: return@doAfter
                        val processName = runCatching {
                            XposedHelpers.getObjectField(bindData, "processName") as? String
                        }.getOrNull().orEmpty()
                        val appInfo = runCatching {
                            XposedHelpers.getObjectField(bindData, "appInfo")
                        }.getOrNull()
                        val packageName = runCatching {
                            XposedHelpers.getObjectField(appInfo, "packageName") as? String
                        }.getOrNull().orEmpty()
                        if (packageName != TAX_PACKAGE_NAME && !processName.startsWith("$TAX_PACKAGE_NAME:")) {
                            return@doAfter
                        }
                        val app = runCatching {
                            XposedHelpers.callStaticMethod(activityThreadClass, "currentApplication") as? Application
                        }.getOrNull() ?: return@doAfter
                        val classLoader = app.classLoader ?: return@doAfter
                        XLog.i(TAG, "bind fallback fired pkg=$packageName proc=$processName")
                        classLoader.doOnce("$TAX_PACKAGE_NAME#bindFallback#$processName") {
                            applyTaxFallbackHooks(
                                packageName = TAX_PACKAGE_NAME,
                                processName = processName.ifBlank { TAX_PACKAGE_NAME },
                                classLoader = classLoader,
                                application = app
                            )
                        }
                    }
                }

                Application::class.java.hookMethod("onCreate") {
                    doAfter {
                        val app = thisObject as? Application ?: return@doAfter
                        val processName = runCatching { Application.getProcessName() }.getOrNull() ?: ""
                        if (app.packageName != TAX_PACKAGE_NAME && !processName.startsWith("$TAX_PACKAGE_NAME:")) {
                            return@doAfter
                        }
                        val classLoader = app.classLoader ?: return@doAfter
                        XLog.i(TAG, "onCreate fallback fired pkg=${app.packageName} proc=$processName")
                        classLoader.doOnce("$TAX_PACKAGE_NAME#onCreateFallback#$processName") {
                            applyTaxFallbackHooks(
                                packageName = TAX_PACKAGE_NAME,
                                processName = processName.ifBlank { TAX_PACKAGE_NAME },
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

    private fun hook(
        lpparam: LoadPackageParam,
        packageName: String,
        processName: String,
        classLoader: ClassLoader
    ) {
        if (packageName == TAX_PACKAGE_NAME || processName.startsWith("$TAX_PACKAGE_NAME:")) {
            XLog.i(TAG, "enter hook pkg=$packageName proc=$processName")
        }
        if (
            packageName == ANDROID_PACKAGE_NAME ||
            packageName == "com.android.systemui" ||
            packageName == XMSF_PACKAGE_NAME ||
            packageName == TAX_PACKAGE_NAME ||
            processName.startsWith("$TAX_PACKAGE_NAME:")
        ) {
            XLog.d(TAG, "Loaded app: $packageName process:$processName")
        }

        if (processName == ANDROID_PACKAGE_NAME) {
            if (packageName == ANDROID_PACKAGE_NAME) {
                HookSystemService().hook(classLoader)
                KeepAliveHook().hook(classLoader)

            }
            return
        }

        if (packageName == "com.android.systemui") {
            removeHyperOSFocusNotificationPackageLimit(lpparam)
            return
        }

        if (packageName == XMSF_PACKAGE_NAME) {
            if (processName == XMSF_PROCESS_NAME) {
                HookXmsf().hook(lpparam)
            }
            return
        }

        if (processName.isBlank()) {
            XLog.w(TAG, "skip fake device for package without processName pkg=$packageName")
            return
        }

        try {
            FakeDevice.fake(lpparam)
        } catch (e: Throwable) {
            XLog.e(TAG, "fake device error for $packageName", e)
        }
    }

    private fun removeHyperOSFocusNotificationPackageLimit(lpparam: LoadPackageParam) {
        HookSystemUIPlugin(
            "miui.systemui.plugin",
            HookNotificationSettingsManager()
        ).hook(lpparam.classLoader)

        HookSystemUIPlugin("miui.systemui.plugin") { pluginLoader ->
            val tag = "HookFocusNotifUtils"
            try {
                val classFocusNotifUtils = XposedHelpers.findClass(
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
                XLog.e(
                    tag,
                    "hook failure: " + e.message,
                    e
                )
            }
        }.hook(lpparam.classLoader)
    }
}
