package io.github.magisk317.mipush.hook.fakedevice

import android.app.Application
import android.os.Handler
import android.os.Looper
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.xposed.logging.LogSanitizerConfig
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.hook
import io.github.magisk317.xposed.hookMethod
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
    private const val LOG_VALUE_MAX_LENGTH = 240

    private val hookedMethods: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val logCounts: MutableMap<String, Int> = Collections.synchronizedMap(HashMap())

    /** Specs registered per target process, so one shared probe can serve them all. */
    private val processSpecs: MutableMap<String, MutableList<SpecRegistration>> =
        Collections.synchronizedMap(HashMap())

    /** Processes whose Application.onCreate probe is already installed. */
    private val installedApplicationProbes: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** Processes whose ClassLoader.loadClass probe is already installed (or scheduled). */
    private val installedLoadClassProbes: MutableSet<String> = Collections.synchronizedSet(HashSet())

    private data class SpecRegistration(
        val spec: VendorHookSpec,
        val classLoader: ClassLoader,
        val context: VendorHookContext,
    )

    fun install(lpparam: LoadParam, spec: VendorHookSpec): Boolean {
        val packageName = lpparam.packageName.orEmpty()
        val processName = lpparam.processName.orEmpty()
        val classLoader = lpparam.classLoader
        val context = VendorHookContext(
            packageName = packageName,
            processName = processName,
            classLoaderId = System.identityHashCode(classLoader),
            spec = spec,
        )

        var installed = installAvailableClasses(classLoader, context)
        registerProcessProbes(lpparam, spec, context)
        if (installed) {
            rateLimitedLog(
                context,
                "installed",
                "installed ${spec.id} vendor hooks for pkg=$packageName proc=$processName"
            )
        } else {
            XLog.d(TAG, "deferred ${spec.id} vendor hooks for pkg=$packageName proc=$processName")
        }
        MagiskOtel.event(
            name = "hook.load",
            attributes = mapOf(
                "result" to if (installed) "ok" else "skip",
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "vendor_hook",
                "reason" to if (installed) "installed" else "deferred",
                "target_package" to packageName,
                "source" to spec.id,
            ),
            statusOk = true,
        )
        return installed
    }

    /**
     * Registers a spec for the target process and installs the shared runtime probes once
     * per process. Every vendor spec used to install its own Application.onCreate and
     * ClassLoader.loadClass hooks, so an auto-detected package (e.g. com.xiaomi.xmsf with
     * seven vendor pipelines) re-hooked the same methods seven times during
     * handleBindApplication; repeated hook installs on a method the main thread is
     * executing deadlocked the process (observed as "timeout publishing content providers"
     * kill loops). Converge to a single probe set per process instead.
     */
    private fun registerProcessProbes(
        lpparam: LoadParam,
        spec: VendorHookSpec,
        context: VendorHookContext,
    ) {
        val processKey = "${context.packageName}@${context.processName}"
        val registrations = synchronized(processSpecs) {
            processSpecs.getOrPut(processKey) { mutableListOf() }
        }
        val alreadyRegistered = synchronized(registrations) {
            if (registrations.any { it.spec.id == spec.id }) {
                true
            } else {
                registrations += SpecRegistration(spec, lpparam.classLoader, context)
                false
            }
        }
        if (alreadyRegistered) return
        if (!installedApplicationProbes.add(processKey)) return
        installAfterApplicationCreate(lpparam, processKey)
    }

    private fun installAfterApplicationCreate(
        lpparam: LoadParam,
        processKey: String,
    ) {
        Application::class.java.hookMethod("onCreate") {
            doAfter {
                val app = thisObject as? Application ?: return@doAfter
                val runtimeLoader = app.classLoader ?: lpparam.classLoader
                // Hooking ClassLoader.loadClass while the main thread is still inside
                // handleBindApplication (content-provider publishing) deadlocks the
                // process: the repeated hook installs trigger deopt + thread suspend
                // while loadClass sits on the stack. Schedule the probe install for
                // after onCreate returns instead.
                Handler(Looper.getMainLooper()).post {
                    installLoadClassProbe(lpparam, processKey)
                }
                // loadClass probes may fire on any thread, so snapshot under the lock.
                val snapshot = processSpecs[processKey]?.let { list ->
                    synchronized(list) { list.toList() }
                }.orEmpty()
                snapshot.forEach { registration ->
                    val runtimeContext = registration.context.copy(
                        classLoaderId = System.identityHashCode(runtimeLoader),
                    )
                    installAvailableClasses(runtimeLoader, runtimeContext)
                }
            }
        }
    }

    private fun installLoadClassProbe(
        lpparam: LoadParam,
        processKey: String,
    ) {
        if (!installedLoadClassProbes.add(processKey)) return
        ClassLoader::class.java.hookMethod("loadClass", String::class.java) {
            doAfter {
                val className = args.getOrNull(0) as? String ?: return@doAfter
                val loadedClass = result as? Class<*> ?: return@doAfter
                // Snapshot per fire: registrations may still be appended by later specs.
                val registrations = processSpecs[processKey]?.let { list ->
                    synchronized(list) { list.toList() }
                }.orEmpty()
                registrations.forEach { registration ->
                    if (className !in registration.spec.classNames) return@forEach
                    val expectedLoader = registration.classLoader
                    val loadedByTarget =
                        loadedClass.classLoader === expectedLoader ||
                            thisObject === expectedLoader
                    if (!loadedByTarget) return@forEach

                    val runtimeContext = registration.context.copy(
                        classLoaderId = System.identityHashCode(loadedClass.classLoader ?: thisObject),
                    )
                    installRulesForClass(loadedClass, runtimeContext)
                }
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
            .replace("\n", " ")
            .replace("\r", " ")
        if (!LogSanitizerConfig.isEnabled()) {
            return truncateLogValue(raw)
        }
        // Field-aware sanitization first; bare high-entropy tokens fall back to redactArg.
        val fieldSanitized = DefaultLogSanitizer.sanitize(raw)
        val sanitized = if (fieldSanitized != raw) {
            fieldSanitized
        } else {
            DefaultLogSanitizer.redactArg(value)
        }.replace("\n", " ").replace("\r", " ")
        return truncateLogValue(sanitized)
    }

    private fun truncateLogValue(value: String): String {
        return if (value.length > LOG_VALUE_MAX_LENGTH) {
            value.take(LOG_VALUE_MAX_LENGTH) + "..."
        } else {
            value
        }
    }

    internal fun markHooked(key: String): Boolean = hookedMethods.add(key)

    internal fun resetForTest() {
        hookedMethods.clear()
        processSpecs.clear()
        installedApplicationProbes.clear()
        installedLoadClassProbes.clear()
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



    private data class VendorHookContext(
        val packageName: String,
        val processName: String,
        val classLoaderId: Int,
        val spec: VendorHookSpec,
    )
}
