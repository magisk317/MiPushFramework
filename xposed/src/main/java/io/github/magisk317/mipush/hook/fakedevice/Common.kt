package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.mipush.xposed.LoadParam
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.hookMethod
import miui.os.Build
import miui.external.SdkHelper

open class Common : IFakeDevice {
    companion object {
        private const val TAG = "Common"
    }

    override fun fake(lpparam: LoadParam): Boolean {
        XLog.d(TAG, "fake() called with: packageName = ${lpparam.packageName}")
        fakeAllBuildInProperties()
        enableAliMiPushBridge(lpparam)
        fakeClass(lpparam)
        return true
    }

    private fun enableAliMiPushBridge(lpparam: LoadParam) {
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

    private fun fakeClass(lpparam: LoadParam) {
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
}
