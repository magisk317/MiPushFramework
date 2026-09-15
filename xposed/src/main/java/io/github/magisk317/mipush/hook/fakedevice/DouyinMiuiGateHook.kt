package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.hook
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections

/**
 * ByteDance's MiPush provider selection gates the Xiaomi channel behind
 * `com.ss.android.message.util.ToolUtils.isMiui()`, which resolves the MIUI framework class
 * `miui.os.Build` and caches the negative result. System properties cannot provide a Java
 * class, and the downloader-era MIUIUtils hooks cover a different SDK path, so on stock-like
 * Xiaomi ROMs (and on non-Xiaomi devices spoofed to Xiaomi brand, where the FakeDevice guard
 * skips the whole pipeline) the provider is never initialized and normal registration never
 * starts.
 *
 * This narrowly scoped compatibility hook overrides exactly that predicate in the target
 * process: the original method still runs first (its own logging/caching side effects stay
 * intact) and only the call result is forced, mirroring the verified manual experiment. A
 * missing class, an absent method or an ambiguous signature fails closed with a diagnostic;
 * there is no blind replacement.
 */
internal object DouyinMiuiGateHook {
    private const val TAG = "DouyinMiuiGate"
    private const val GATE_CLASS = "com.ss.android.message.util.ToolUtils"
    private const val GATE_METHOD = "isMiui"

    private val installedPackages: MutableSet<String> = Collections.synchronizedSet(HashSet())

    fun install(lpparam: LoadParam) {
        val packageName = lpparam.packageName.orEmpty()
        if (packageName.isEmpty() || !installedPackages.add(packageName)) return

        val clazz = runCatching { lpparam.classLoader.loadClass(GATE_CLASS) }.getOrNull()
        if (clazz == null) {
            XLog.i(TAG, "provider gate absent pkg=$packageName class=$GATE_CLASS")
            return
        }
        val methods = runCatching { clazz.declaredMethods }
            .getOrDefault(emptyArray())
            .filter { it.matchesProviderGate() }
        when (methods.size) {
            0 -> XLog.i(TAG, "provider gate method absent pkg=$packageName class=$GATE_CLASS")
            1 -> {
                methods.single().hook {
                    doAfter { result = true }
                }
                XLog.i(TAG, "provider gate forced pkg=$packageName class=$GATE_CLASS method=$GATE_METHOD")
            }
            else -> XLog.w(TAG, "provider gate ambiguous pkg=$packageName count=${methods.size}")
        }
    }

    /** Static, no-argument, primitive-boolean `isMiui` — matches the verified gate exactly. */
    internal fun Method.matchesProviderGate(): Boolean =
        name == GATE_METHOD &&
            Modifier.isStatic(modifiers) &&
            parameterCount == 0 &&
            returnType == Boolean::class.javaPrimitiveType
}
