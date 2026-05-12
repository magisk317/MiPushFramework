package io.github.magisk317.mipush.hook.fakedevice

import android.app.Application
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.hook
import io.github.magisk317.mipush.xposed.hookMethod
import java.lang.reflect.Method
import java.util.Collections

internal data class VendorHookSpec(
    val id: String,
    val classNames: List<String>,
    val forceRules: List<VendorForceRule>,
    val diagnosticRules: List<VendorDiagnosticRule>,
)

internal data class VendorForceRule(
    val methodNames: Set<String>,
    val action: VendorForceAction,
    val reason: String,
)

internal data class VendorDiagnosticRule(
    val methodNames: Set<String>,
    val methodKeywords: Set<String> = emptySet(),
    val reason: String,
)

internal sealed class VendorForceAction {
    data object BooleanFalse : VendorForceAction()
    data object BooleanTrue : VendorForceAction()
    data class IntValue(val value: Int) : VendorForceAction()
}

internal object VendorPushHookHelper {
    private const val TAG = "VendorPushCompat"
    private const val MAX_LOGS_PER_KEY = 8

    private val hookedMethods: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val hookedRuntimeCallbacks: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val hookedLoadClassCallbacks: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val logCounts: MutableMap<String, Int> = Collections.synchronizedMap(HashMap())

    fun install(lpparam: XC_LoadPackage.LoadPackageParam, spec: VendorHookSpec): Boolean {
        val packageName = lpparam.packageName.orEmpty()
        val processName = lpparam.processName.orEmpty()
        val classLoader = lpparam.classLoader ?: return false
        val context = VendorHookContext(
            packageName = packageName,
            processName = processName,
            classLoaderId = System.identityHashCode(classLoader),
            spec = spec,
        )

        var installed = installAvailableClasses(classLoader, context)
        installAfterApplicationCreate(lpparam, spec, context)
        installLoadClassProbe(lpparam, spec, context)
        if (installed) {
            rateLimitedLog(
                context,
                "installed",
                "installed ${spec.id} vendor hooks for pkg=$packageName proc=$processName"
            )
        } else {
            XLog.d(TAG, "deferred ${spec.id} vendor hooks for pkg=$packageName proc=$processName")
        }
        return installed
    }

    private fun installAfterApplicationCreate(
        lpparam: XC_LoadPackage.LoadPackageParam,
        spec: VendorHookSpec,
        context: VendorHookContext,
    ) {
        val key = "${context.packageName}@${context.processName}#${spec.id}#application"
        if (!hookedRuntimeCallbacks.add(key)) return
        Application::class.java.hookMethod("onCreate") {
            doAfter {
                val app = thisObject as? Application ?: return@doAfter
                val runtimeLoader = app.classLoader ?: lpparam.classLoader ?: return@doAfter
                val runtimeContext = context.copy(
                    classLoaderId = System.identityHashCode(runtimeLoader),
                )
                installAvailableClasses(runtimeLoader, runtimeContext)
            }
        }
    }

    private fun installLoadClassProbe(
        lpparam: XC_LoadPackage.LoadPackageParam,
        spec: VendorHookSpec,
        context: VendorHookContext,
    ) {
        val key = "${context.packageName}@${context.processName}#${spec.id}#loadClass"
        if (!hookedLoadClassCallbacks.add(key)) return
        val targetClasses = spec.classNames.toSet()
        ClassLoader::class.java.hookMethod("loadClass", String::class.java) {
            doAfter {
                val className = args.getOrNull(0) as? String ?: return@doAfter
                if (className !in targetClasses) return@doAfter
                val loadedClass = result as? Class<*> ?: return@doAfter
                val expectedLoader = lpparam.classLoader
                val loadedByTarget = expectedLoader == null ||
                    loadedClass.classLoader === expectedLoader ||
                    thisObject === expectedLoader
                if (!loadedByTarget) return@doAfter

                val runtimeContext = context.copy(
                    classLoaderId = System.identityHashCode(loadedClass.classLoader ?: thisObject),
                )
                installRulesForClass(loadedClass, runtimeContext)
            }
        }
    }

    private fun installAvailableClasses(classLoader: ClassLoader, context: VendorHookContext): Boolean {
        var installedAny = false
        context.spec.classNames.forEach { className ->
            val clazz = runCatching { classLoader.findClass(className) }.getOrNull() ?: return@forEach
            installedAny = installRulesForClass(clazz, context) || installedAny
        }
        return installedAny
    }

    private fun installRulesForClass(clazz: Class<*>, context: VendorHookContext): Boolean {
        val methods = runCatching { clazz.declaredMethods.toList() }.getOrDefault(emptyList())
        var installedAny = false

        context.spec.forceRules.forEach { rule ->
            methods
                .filter { method -> method.name in rule.methodNames && canForce(method.returnType, rule.action) }
                .forEach { method ->
                    installedAny = hookForceMethod(method, context, rule) || installedAny
                }
        }

        context.spec.diagnosticRules.forEach { rule ->
            methods
                .filter { method -> shouldDiagnose(method.name, rule) }
                .forEach { method ->
                    installedAny = hookDiagnosticMethod(method, context, rule) || installedAny
                }
        }

        return installedAny
    }

    private fun hookForceMethod(
        method: Method,
        context: VendorHookContext,
        rule: VendorForceRule,
    ): Boolean {
        val key = methodHookKey(context, method, "force:${rule.reason}")
        if (!markHooked(key)) return false
        method.hook {
            replace {
                val forced = forcedValue(method.returnType, rule.action)
                rateLimitedLog(
                    context,
                    "${method.declaringClass.name}.${method.name}",
                    "force ${context.spec.id} unavailable at ${method.declaringClass.name}.${method.name} " +
                        "pkg=${context.packageName} proc=${context.processName} result=${sanitizeForLog(forced)}"
                )
                forced
            }
        }
        XLog.d(TAG, "hooked ${context.spec.id} force ${method.declaringClass.name}.${method.name}")
        return true
    }

    private fun hookDiagnosticMethod(
        method: Method,
        context: VendorHookContext,
        rule: VendorDiagnosticRule,
    ): Boolean {
        val key = methodHookKey(context, method, "diagnostic:${rule.reason}")
        if (!markHooked(key)) return false
        method.hook {
            doBefore {
                rateLimitedLog(
                    context,
                    "${method.declaringClass.name}.${method.name}#before",
                    "${context.spec.id} before ${method.declaringClass.name}.${method.name} " +
                        "pkg=${context.packageName} proc=${context.processName} args=${sanitizeArgs(args)}"
                )
            }
            doAfter {
                if (throwable != null) {
                    XLog.e(
                        TAG,
                        "${context.spec.id} throwable ${method.declaringClass.name}.${method.name} " +
                            "pkg=${context.packageName} proc=${context.processName}",
                        throwable
                    )
                } else {
                    rateLimitedLog(
                        context,
                        "${method.declaringClass.name}.${method.name}#after",
                        "${context.spec.id} after ${method.declaringClass.name}.${method.name} " +
                            "pkg=${context.packageName} proc=${context.processName} result=${sanitizeForLog(result)}"
                    )
                }
            }
        }
        XLog.d(TAG, "hooked ${context.spec.id} diagnostic ${method.declaringClass.name}.${method.name}")
        return true
    }

    internal fun canForce(returnType: Class<*>, action: VendorForceAction): Boolean {
        return when (action) {
            VendorForceAction.BooleanFalse,
            VendorForceAction.BooleanTrue ->
                returnType == Boolean::class.javaPrimitiveType || returnType == Boolean::class.javaObjectType
            is VendorForceAction.IntValue ->
                returnType == Int::class.javaPrimitiveType || returnType == Int::class.javaObjectType
        }
    }

    internal fun forcedValue(returnType: Class<*>, action: VendorForceAction): Any? {
        return when (action) {
            VendorForceAction.BooleanFalse -> false
            VendorForceAction.BooleanTrue -> true
            is VendorForceAction.IntValue -> action.value
        }.takeIf { canForce(returnType, action) }
    }

    internal fun shouldDiagnose(methodName: String, rule: VendorDiagnosticRule): Boolean {
        val lowerName = methodName.lowercase()
        return methodName in rule.methodNames || rule.methodKeywords.any { lowerName.contains(it.lowercase()) }
    }

    internal fun sanitizeArgs(args: Array<Any?>): String {
        return args.joinToString(prefix = "[", postfix = "]") { sanitizeForLog(it) }
    }

    internal fun sanitizeForLog(value: Any?): String {
        if (value == null) return "null"
        val raw = value.toString()
        val sanitized = raw
            .replace(Regex("""(?i)(token|regid|reg_id|account|aid|appId|app_id|appKey|app_key|accessid|access_id)=([^,}\]\s]+)""")) {
                "${it.groupValues[1]}=${redactMiddle(it.groupValues[2])}"
            }
            .replace("\n", " ")
            .replace("\r", " ")
        val compact = if (sanitized.length > 240) sanitized.take(240) + "..." else sanitized
        return if (shouldRedactWholeValue(value, compact)) redactMiddle(compact) else compact
    }

    internal fun markHooked(key: String): Boolean = hookedMethods.add(key)

    internal fun resetForTest() {
        hookedMethods.clear()
        hookedRuntimeCallbacks.clear()
        hookedLoadClassCallbacks.clear()
        logCounts.clear()
    }

    private fun methodHookKey(context: VendorHookContext, method: Method, action: String): String {
        return "${context.packageName}@${context.processName}#${context.spec.id}#" +
            "${context.classLoaderId}#${method.toGenericString()}#$action"
    }

    private fun rateLimitedLog(context: VendorHookContext, key: String, message: String) {
        val countKey = "${context.packageName}@${context.processName}#${context.spec.id}#$key"
        val next = ((logCounts[countKey] ?: 0) + 1).also { logCounts[countKey] = it }
        if (next <= MAX_LOGS_PER_KEY) {
            XLog.i(TAG, message)
        } else if (next == MAX_LOGS_PER_KEY + 1) {
            XLog.i(TAG, "suppress further ${context.spec.id} logs for pkg=${context.packageName} proc=${context.processName} key=$key")
        }
    }

    private fun redactMiddle(value: String): String {
        if (value.length <= 8) return "<redacted>"
        return value.take(4) + "..." + value.takeLast(4)
    }

    private fun shouldRedactWholeValue(value: Any, text: String): Boolean {
        if (value !is CharSequence) return false
        val lower = text.lowercase()
        if (lower in setOf("true", "false", "null")) return false
        return text.length >= 16 && text.none { it.isWhitespace() }
    }

    private data class VendorHookContext(
        val packageName: String,
        val processName: String,
        val classLoaderId: Int,
        val spec: VendorHookSpec,
    )
}
