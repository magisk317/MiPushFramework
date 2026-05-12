package io.github.magisk317.mipush.hook.fakedevice

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.hookAllMethods
import io.github.magisk317.mipush.xposed.hookMethod
import java.lang.reflect.Method

open class XGPush : IFakeDevice {
    companion object {
        private const val TAG = "FakeForXGPush"
        private val XG_DIAGNOSTIC_CLASS_NAMES = listOf(
            "com.tencent.android.tpush.XGPushManager",
            "com.tencent.android.tpush.XGPushConfig",
            "com.tencent.tpns.baseapi.XGApiConfig",
            "com.tencent.tpns.baseapi.XGApiManager",
            "com.tencent.tpns.baseapi.core.impl.TPNSRegisterManager",
            "com.tencent.tpns.baseapi.core.impl.TPNSPushManager",
        )
        private val XG_DIAGNOSTIC_METHOD_KEYWORDS = listOf(
            "register",
            "unregister",
            "token",
            "account",
            "tag",
            "push",
            "channel",
            "miui",
            "xiaomi",
            "vendor",
        )
        private val missingClassLogged: MutableSet<String> =
            java.util.Collections.synchronizedSet(HashSet())
        private val diagnosticHookedClasses: MutableSet<String> =
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
                installXgDiagnostics(packageName, classLoader)
                return true
            }

            val classXGPushConfig = try {
                classLoader.findClass("com.tencent.android.tpush.XGPushConfig")
            } catch (_: Throwable) {
                null
            }
            if (classXGPushConfig != null) {
                fakeXGPushConfig(classXGPushConfig)
                installXgDiagnostics(packageName, classLoader)
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

    private fun installXgDiagnostics(packageName: String, classLoader: ClassLoader) {
        XG_DIAGNOSTIC_CLASS_NAMES.forEach { className ->
            val clazz = runCatching { classLoader.findClass(className) }.getOrNull() ?: return@forEach
            val classKey = "$packageName#$className"
            if (!diagnosticHookedClasses.add(classKey)) return@forEach

            val methodNames = runCatching {
                clazz.declaredMethods
                    .map { it.name }
                    .filter(::isDiagnosticMethod)
                    .distinct()
            }.getOrDefault(emptyList())
            methodNames.forEach { methodName ->
                runCatching {
                    clazz.hookAllMethods(methodName) {
                        doBefore {
                            XLog.i(
                                TAG,
                                "TPNS bridge before $className.$methodName " +
                                    "pkg=$packageName args=${safeArgs(args)}"
                            )
                        }
                        doAfter {
                            if (throwable != null) {
                                XLog.e(
                                    TAG,
                                    "TPNS bridge throwable $className.$methodName pkg=$packageName",
                                    throwable
                                )
                            } else {
                                XLog.i(
                                    TAG,
                                    "TPNS bridge after $className.$methodName " +
                                        "pkg=$packageName result=${safeValue(result)}"
                                )
                            }
                        }
                    }
                }.onFailure {
                    XLog.d(TAG, "skip TPNS diagnostic hook $className.$methodName: ${it.javaClass.simpleName}")
                }
            }
            if (methodNames.isEmpty()) {
                XLog.d(TAG, "TPNS diagnostic class found without matching methods: $className")
            } else {
                XLog.d(TAG, "TPNS diagnostic hooked $className methods=${methodNames.joinToString()}")
            }
        }
    }

    private fun isDiagnosticMethod(methodName: String): Boolean {
        val lowerName = methodName.lowercase()
        return XG_DIAGNOSTIC_METHOD_KEYWORDS.any { lowerName.contains(it) }
    }

    private fun safeArgs(args: Array<Any?>): String {
        return args.joinToString(prefix = "[", postfix = "]") { safeValue(it) }
    }

    private fun safeValue(value: Any?): String {
        if (value == null) return "null"
        val raw = value.toString()
        val sanitized = raw
            .replace(Regex("""(?i)(token|regid|reg_id|account|aid|accessid|access_id)=([^,}\]\s]+)""")) {
                "${it.groupValues[1]}=${redactMiddle(it.groupValues[2])}"
            }
            .replace("\n", " ")
            .replace("\r", " ")
        val compact = if (sanitized.length > 240) sanitized.take(240) + "..." else sanitized
        return if (shouldRedactWholeValue(value, compact)) redactMiddle(compact) else compact
    }

    private fun redactMiddle(value: String): String {
        if (value.length <= 8) return "<redacted>"
        return value.take(4) + "..." + value.takeLast(4)
    }

    private fun shouldRedactWholeValue(value: Any, text: String): Boolean {
        if (value !is CharSequence) return false
        return text.length >= 16 && text.none { it.isWhitespace() }
    }


}
