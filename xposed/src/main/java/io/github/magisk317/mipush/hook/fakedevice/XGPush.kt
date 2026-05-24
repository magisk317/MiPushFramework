package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.mipush.xposed.LoadParam
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.hook
import io.github.magisk317.mipush.xposed.hookAllMethods
import io.github.magisk317.mipush.xposed.hookMethod
import io.github.magisk317.mipush.xposed.setHookStaticBooleanField
import java.lang.reflect.Method
import java.util.Collections

open class XGPush : IFakeDevice {
    companion object {
        private const val TAG = "FakeForXGPush"
        private const val OTHER_PUSH_SUCCESS = "errCode : 0 , errMsg : success"
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
        private val stateHookedMethods: MutableSet<String> =
            Collections.synchronizedSet(HashSet())
        private val runtimeStates: MutableMap<String, XgRuntimeState> =
            Collections.synchronizedMap(HashMap())
    }

    override fun fake(lpparam: LoadParam): Boolean {
        val classLoader = lpparam.classLoader
        val packageName = lpparam.packageName.orEmpty()
        val processName = lpparam.processName.orEmpty()

        XLog.d(TAG, "fake() called with: classLoader = $classLoader")

        return try {
            val classChannelUtils = try {
                classLoader.findClass("com.tencent.tpns.baseapi.base.util.ChannelUtils")
            } catch (_: Throwable) {
                null
            }
            if (classChannelUtils != null) {
                fakeChannels(classChannelUtils)
                installXgRuntimeStateHooks(packageName, processName, classLoader)
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
                installXgRuntimeStateHooks(packageName, processName, classLoader)
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

    private fun installXgRuntimeStateHooks(
        packageName: String,
        processName: String,
        classLoader: ClassLoader
    ) {
        val stateKey = "$packageName@$processName#${System.identityHashCode(classLoader)}"
        val context = XgHookContext(
            packageName = packageName,
            processName = processName,
            classLoader = classLoader,
            classLoaderId = System.identityHashCode(classLoader),
            state = runtimeStateFor(stateKey)
        )
        hookXgRuntimeClass(context, "com.tencent.android.tpush.XGPushManager")
        hookXgRuntimeClass(context, "com.tencent.android.tpush.XGPushConfig")
        hookXgRuntimeClass(context, "com.tencent.tpns.baseapi.XGApiConfig")
    }

    private fun runtimeStateFor(stateKey: String): XgRuntimeState {
        return synchronized(runtimeStates) {
            runtimeStates.getOrPut(stateKey) { XgRuntimeState() }
        }
    }

    private fun hookXgRuntimeClass(context: XgHookContext, className: String) {
        val clazz = runCatching { context.classLoader.findClass(className) }.getOrNull() ?: return
        val methods = runCatching { clazz.declaredMethods.toList() }.getOrDefault(emptyList())
        methods.forEach { method ->
            when (method.name) {
                "registerPush" -> hookRegisterPush(method, context)
                "getToken" -> hookGetToken(method, context)
                "isRegistered" -> hookIsRegistered(method, context)
                "loadOtherPushToken" -> hookLoadOtherPushToken(method, context)
                "getOtherPushErrCode" -> hookGetOtherPushErrCode(method, context)
                "setRegisterSuccess" -> hookSetRegisterSuccess(method, context)
                "clearRegistered" -> hookClearRegistered(method, context)
            }
        }
    }

    private fun hookRegisterPush(method: Method, context: XgHookContext) {
        if (!markStateHooked(context, method, "registerPush")) return
        method.hook {
            doBefore {
                if (context.state.isReady()) {
                    result = defaultReturnValue(method.returnType)
                    XLog.i(
                        TAG,
                        "short-circuit duplicate registerPush pkg=${context.packageName} " +
                            "proc=${context.processName} token=${context.state.tokenForLog()}"
                    )
                }
            }
        }
        XLog.d(TAG, "hooked TPNS registerPush state guard ${method.declaringClass.name}.${method.name}")
    }

    private fun hookGetToken(method: Method, context: XgHookContext) {
        if (!String::class.java.isAssignableFrom(method.returnType)) return
        if (!markStateHooked(context, method, "getToken")) return
        method.hook {
            doBefore {
                val cachedToken = context.state.cachedToken()
                if (cachedToken.isNotBlank()) {
                    result = cachedToken
                    XLog.i(
                        TAG,
                        "return cached TPNS token pkg=${context.packageName} " +
                            "proc=${context.processName} token=${context.state.tokenForLog()}"
                    )
                }
            }
            doAfter {
                val token = (result as? CharSequence)?.toString().orEmpty()
                if (token.isNotBlank()) {
                    if (context.state.markToken(token)) {
                        XLog.i(
                            TAG,
                            "cache TPNS token pkg=${context.packageName} " +
                                "proc=${context.processName} token=${context.state.tokenForLog()}"
                        )
                    }
                    return@doAfter
                }
                val cachedToken = context.state.cachedToken()
                if (cachedToken.isNotBlank()) {
                    result = cachedToken
                }
            }
        }
        XLog.d(TAG, "hooked TPNS getToken state bridge ${method.declaringClass.name}.${method.name}")
    }

    private fun hookIsRegistered(method: Method, context: XgHookContext) {
        if (!isBooleanReturn(method)) return
        if (!markStateHooked(context, method, "isRegistered")) return
        method.hook {
            doBefore {
                if (context.state.isReady()) {
                    result = true
                }
            }
            doAfter {
                if (result == true) {
                    if (context.state.markRegistered()) {
                        XLog.i(
                            TAG,
                            "mark TPNS registered pkg=${context.packageName} proc=${context.processName}"
                        )
                    }
                } else if (context.state.isReady()) {
                    result = true
                }
            }
        }
        XLog.d(TAG, "hooked TPNS isRegistered state bridge ${method.declaringClass.name}.${method.name}")
    }

    private fun hookLoadOtherPushToken(method: Method, context: XgHookContext) {
        if (!markStateHooked(context, method, "loadOtherPushToken")) return
        method.hook {
            doBefore {
                if (context.state.isReady()) {
                    context.state.markOtherPushSatisfied()
                    result = defaultReturnValue(method.returnType)
                    XLog.i(
                        TAG,
                        "short-circuit loadOtherPushToken after TPNS ready pkg=${context.packageName} " +
                            "proc=${context.processName}"
                    )
                }
            }
        }
        XLog.d(TAG, "hooked TPNS loadOtherPushToken state guard ${method.declaringClass.name}.${method.name}")
    }

    private fun hookGetOtherPushErrCode(method: Method, context: XgHookContext) {
        if (!markStateHooked(context, method, "getOtherPushErrCode")) return
        method.hook {
            doBefore {
                if (context.state.isReady()) {
                    result = successValueFor(method.returnType)
                    XLog.i(
                        TAG,
                        "force other-push success pkg=${context.packageName} proc=${context.processName}"
                    )
                }
            }
            doAfter {
                if (context.state.isReady()) {
                    result = successValueFor(method.returnType)
                }
            }
        }
        XLog.d(TAG, "hooked TPNS getOtherPushErrCode state bridge ${method.declaringClass.name}.${method.name}")
    }

    private fun hookSetRegisterSuccess(method: Method, context: XgHookContext) {
        if (!markStateHooked(context, method, "setRegisterSuccess")) return
        method.hook {
            doAfter {
                if (throwable == null && context.state.markRegistered()) {
                    XLog.i(
                        TAG,
                        "observe setRegisterSuccess pkg=${context.packageName} proc=${context.processName}"
                    )
                }
            }
        }
        XLog.d(TAG, "hooked TPNS setRegisterSuccess observer ${method.declaringClass.name}.${method.name}")
    }

    private fun hookClearRegistered(method: Method, context: XgHookContext) {
        if (!markStateHooked(context, method, "clearRegistered")) return
        method.hook {
            doAfter {
                if (throwable == null) {
                    context.state.clear()
                    XLog.i(TAG, "observe clearRegistered pkg=${context.packageName} proc=${context.processName}")
                }
            }
        }
        XLog.d(TAG, "hooked TPNS clearRegistered observer ${method.declaringClass.name}.${method.name}")
    }

    private fun markStateHooked(context: XgHookContext, method: Method, reason: String): Boolean {
        val key = "${context.packageName}@${context.processName}#${context.classLoaderId}#" +
            "${method.toGenericString()}#$reason"
        return stateHookedMethods.add(key)
    }

    private fun isBooleanReturn(method: Method): Boolean {
        return method.returnType == Boolean::class.javaPrimitiveType ||
            method.returnType == Boolean::class.javaObjectType
    }

    private fun successValueFor(returnType: Class<*>): Any? {
        return when {
            String::class.java.isAssignableFrom(returnType) -> OTHER_PUSH_SUCCESS
            returnType == Int::class.javaPrimitiveType || returnType == Int::class.javaObjectType -> 0
            returnType == Boolean::class.javaPrimitiveType || returnType == Boolean::class.javaObjectType -> true
            else -> defaultReturnValue(returnType)
        }
    }

    private fun defaultReturnValue(returnType: Class<*>): Any? {
        return when (returnType) {
            Boolean::class.javaPrimitiveType -> false
            Byte::class.javaPrimitiveType -> 0.toByte()
            Char::class.javaPrimitiveType -> 0.toChar()
            Double::class.javaPrimitiveType -> 0.0
            Float::class.javaPrimitiveType -> 0f
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Short::class.javaPrimitiveType -> 0.toShort()
            else -> null
        }
    }

    private fun fakeXGPushConfig(classXGPushConfig: Class<*>) {
        XLog.d(TAG, "fakeXGPushConfig() called")
        setHookStaticBooleanField(classXGPushConfig, "isForcedIsMiui", true)
        // Some versions might have these methods
        classXGPushConfig.hookMethod("isSamsungDevice") { replace { false } }
        classXGPushConfig.hookMethod("isOppoDevice") { replace { false } }
        classXGPushConfig.hookMethod("isVivoDevice") { replace { false } }
    }

    private fun fakeChannels(classChannelUtils: Class<*>): Boolean {
        XLog.d(TAG, "fakeChannels() called")

        classChannelUtils.declaredMethods.forEach { method ->
            method.hook {
                doBefore {
                    if (method.name == "getMiuiVersionCode") {
                        result = "13"
                    } else if (method.name == "getMiuiVersionName") {
                        result = "V130"
                    } else if (method.name == "isBrandXiaoMi") {
                        result = true
                    } else if (method.returnType == Boolean::class.java) {
                        result = false
                    } else if (method.returnType == String::class.java) {
                        result = ""
                    }
                }
            }
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

    private data class XgHookContext(
        val packageName: String,
        val processName: String,
        val classLoader: ClassLoader,
        val classLoaderId: Int,
        val state: XgRuntimeState
    )

    private class XgRuntimeState {
        @Volatile
        private var token: String = ""
        @Volatile
        private var registered: Boolean = false
        @Volatile
        private var otherPushSatisfied: Boolean = false

        @Synchronized
        fun cachedToken(): String = token

        @Synchronized
        fun isReady(): Boolean = registered || token.isNotBlank()

        @Synchronized
        fun markToken(value: String): Boolean {
            val normalized = value.trim()
            if (normalized.isBlank()) return false
            val changed = token != normalized || !registered
            token = normalized
            registered = true
            otherPushSatisfied = true
            return changed
        }

        @Synchronized
        fun markRegistered(): Boolean {
            val changed = !registered
            registered = true
            otherPushSatisfied = true
            return changed
        }

        @Synchronized
        fun markOtherPushSatisfied() {
            otherPushSatisfied = true
        }

        @Synchronized
        fun clear() {
            token = ""
            registered = false
            otherPushSatisfied = false
        }

        @Synchronized
        fun tokenForLog(): String {
            if (token.isBlank()) return "blank"
            return if (token.length <= 8) "<redacted>" else token.take(4) + "..." + token.takeLast(4)
        }
    }


}
