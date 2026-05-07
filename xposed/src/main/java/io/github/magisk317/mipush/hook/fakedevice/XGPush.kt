package io.github.magisk317.mipush.hook.fakedevice

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.hookMethod
import java.lang.reflect.Method

open class XGPush : IFakeDevice {
    companion object {
        private const val TAG = "FakeForXGPush"
        private val missingClassLogged: MutableSet<String> =
            java.util.Collections.synchronizedSet(HashSet())
    }

    override fun fake(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        val classLoader = lpparam.classLoader
        val packageName = lpparam.packageName

        XLog.d(TAG, "fake() called with: classLoader = $classLoader")

        return try {
            val classChannelUtils = try {
                classLoader.findClass("com.tencent.tpns.baseapi.base.util.ChannelUtils")
            } catch (_: Throwable) {
                null
            }
            if (classChannelUtils != null) {
                fakeChannels(classChannelUtils)
                return true
            }

            val classXGPushConfig = try {
                classLoader.findClass("com.tencent.android.tpush.XGPushConfig")
            } catch (_: Throwable) {
                null
            }
            if (classXGPushConfig != null) {
                fakeXGPushConfig(classXGPushConfig)
                return true
            }

            if (missingClassLogged.add(packageName)) {
                XLog.d(TAG, "Neither ChannelUtils nor XGPushConfig found for $packageName")
            }
            false
        } catch (e: Throwable) {
            XLog.e(TAG, "fake error: ", e)
            false
        }
    }

    private fun fakeXGPushConfig(classXGPushConfig: Class<*>) {
        XLog.d(TAG, "fakeXGPushConfig() called")
        XposedHelpers.setStaticBooleanField(classXGPushConfig, "isForcedIsMiui", true)
        // Some versions might have these methods
        classXGPushConfig.hookMethod("isSamsungDevice") { replace { false } }
        classXGPushConfig.hookMethod("isOppoDevice") { replace { false } }
        classXGPushConfig.hookMethod("isVivoDevice") { replace { false } }
    }

    private fun fakeChannels(classChannelUtils: Class<*>): Boolean {
        XLog.d(TAG, "fakeChannels() called")

        classChannelUtils.declaredMethods.forEach {
            XposedBridge.hookMethod(it, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val method = param.method as Method

                    if (method.name == "getMiuiVersionCode") {
                        param.result = "13"
                    } else if (method.name == "getMiuiVersionName") {
                        param.result = "V130"
                    } else if (method.name == "isBrandXiaoMi") {
                        param.result = true
                    } else if (method.returnType == Boolean::class.java) {
                        param.result = false
                    } else if (method.returnType == String::class.java) {
                        param.result = ""
                    }
                }
            })
        }
        return true
    }


}
