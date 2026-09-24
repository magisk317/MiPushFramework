package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.hookMethod
import miui.os.Build
import miui.external.SdkHelper

open class Common : IFakeDevice {
    companion object {
        private const val TAG = "Common"
    }

    private var skipPropertySpoofing: Boolean = false

    override fun fake(lpparam: LoadParam): Boolean {
        XLog.d(TAG, "fake() called with: packageName = ${lpparam.packageName}")
        var anyStepSucceeded = false
        if (skipPropertySpoofing) {
            XLog.i(TAG, "skipping property spoofing for Xiaomi identity: ${lpparam.packageName}")
        } else {
            anyStepSucceeded = runStep("build properties") { fakeBuildProperties() }
        }
        anyStepSucceeded = runStep("Ali MiPush bridge") { enableAliMiPushBridge(lpparam) } || anyStepSucceeded
        anyStepSucceeded = runStep("MIUI class bridge") { fakeClass(lpparam) } || anyStepSucceeded
        return anyStepSucceeded
    }

    /** Runs this pipeline while retaining class and vendor gates but omitting property spoofing. */
    internal fun fakeWithoutPropertySpoofing(lpparam: LoadParam): Boolean {
        val previousValue = skipPropertySpoofing
        skipPropertySpoofing = true
        return try {
            fake(lpparam)
        } finally {
            skipPropertySpoofing = previousValue
        }
    }

    protected open fun fakeBuildProperties() {
        fakeAllBuildInProperties()
    }

    protected open fun enableAliMiPushBridge(lpparam: LoadParam) {
        runCatching {
            lpparam.classLoader.findClass("com.alibaba.sdk.android.push.channel.XiaomiPushUtils")
                .hookMethod("isMiui") {
                    replace { true }
                }
        }
        runCatching {
            lpparam.classLoader.findClass("com.alipay.pushsdk.thirdparty.xiaomi.XiaoMIPushWorker")
                .hookMethod("isSupport") {
                    replace { true }
                }
        }
    }

    protected open fun fakeClass(lpparam: LoadParam) {
        var isMIUI = false
        try {
            // check MIUI environment
            Class.forName("miui.os.Build", false, lpparam.classLoader)
            isMIUI = true
        } catch (_: Throwable) {
        }
        if (isMIUI) {
            return
        }

        val classMap: Map<String, Class<out Any>> = mapOf(
            Build::class.java.name to Build::class.java,
            SdkHelper::class.java.name to SdkHelper::class.java,
        )
        Class::class.java.hookMethod(
            "forName",
            String::class.java,
            Boolean::class.java,
            ClassLoader::class.java
        ) {
            doBefore {
                var requestClass = args[0]
                val returnClass = classMap[requestClass]
                if (returnClass != null) {
                    XLog.d(TAG, "forHook $requestClass")
                    result = returnClass
                } else {
                    XLog.t(TAG, "forName $requestClass")
                }
            }
        }
    }

    private inline fun runStep(stepName: String, action: () -> Unit): Boolean {
        return runCatching(action).fold(
            onSuccess = { true },
            onFailure = { throwable ->
                XLog.w(
                    TAG,
                    "$stepName failed: ${throwable.javaClass.simpleName}: ${throwable.message}",
                )
                false
            },
        )
    }
}
