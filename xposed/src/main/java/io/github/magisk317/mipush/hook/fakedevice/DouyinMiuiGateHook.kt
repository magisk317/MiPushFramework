package io.github.magisk317.mipush.hook.fakedevice

import io.github.libxposed.api.XposedInterface
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.hook
import io.github.magisk317.xposed.logging.XLog
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
 *
 * Issue #143 (Douyin on a Lunaris-AOSP ROM registers forever and never receives a push, while a
 * standalone `isMiui()` module works) showed the cost of the original install bookkeeping: the
 * package was marked handled *before* the class lookup, so the first loader that could not see
 * `ToolUtils` locked out every later attempt in that process. That throws away the recovery the
 * kit deliberately provides -- `BaseLibXposedEntry` dedups deferred hooks by
 * `loaderKey#hookIdentity` and re-dispatches when an authoritative loader replaces a provisional
 * one, precisely because a hook installed against the wrong loader "silently finds nothing".
 * Claims are therefore keyed per class loader now and recorded only on success.
 *
 * Two more blind spots from the same report are closed here: the hook never said whether it was
 * ever *hit*, and it never deoptimized the target even though the gate caches a negative value
 * (the working standalone module calls `deoptimize` before hooking). Both were unfalsifiable from
 * the manager's own export because the export window can start after process launch, so the
 * install outcome alone cannot distinguish "not installed" from "installed, never called".
 */
internal object DouyinMiuiGateHook {
    private const val TAG = "DouyinMiuiGate"
    private const val GATE_CLASS = "com.ss.android.message.util.ToolUtils"
    private const val GATE_METHOD = "isMiui"

    /**
     * Distinct loaders one package may claim. The kit tops out at MAX_DISPATCHES_PER_PACKAGE, so
     * this only guards against a pathological loader churn in a long-lived process.
     */
    internal const val MAX_LOADERS_PER_PACKAGE = 4

    /** Log the first hits in full, then thin out: `isMiui()` is called on every provider poll. */
    internal const val VERBOSE_HIT_LIMIT = 8L
    internal const val SPARSE_HIT_MODULO = 64L

    /** package -> identity hashes of the class loaders whose gate this object has taken over. */
    private val claimedLoaders = ConcurrentHashMap<String, MutableSet<Int>>()
    /** Calls that reached the hook, per process; the counter is what makes "never hit" visible. */
    private val hits = AtomicLong()

    @Volatile
    private var deoptimizeTarget: ((Executable) -> Boolean)? = null

    /**
     * Binds the framework's deoptimization entry point so the gate can be discarded from compiled
     * form before hooking. Called from the module entry with the [XposedInterface] it was given;
     * left unbound on a JVM without one (unit tests), where the hook simply installs as before.
     */
    fun bindDeoptimizer(xposed: XposedInterface?) {
        deoptimizeTarget = xposed?.let { xposedInterface ->
            { executable -> runCatching { xposedInterface.deoptimize(executable) }.getOrDefault(false) }
        }
    }

    /**
     * Whether [loaderId] may try to take over the gate yet.
     *
     * A loader that already owns the gate is never hooked twice, and a loader that is merely new
     * gets its own attempt even if an earlier loader failed -- the opposite of the old
     * package-level latch, which is what made a transient "class not visible yet" permanent.
     */
    internal fun shouldAttempt(
        loaderId: Int,
        claimed: Set<Int>,
        maxLoaders: Int = MAX_LOADERS_PER_PACKAGE,
    ): Boolean = loaderId !in claimed && claimed.size < maxLoaders

    /** Whether hit number [count] deserves a log line. */
    internal fun shouldLogHit(count: Long): Boolean =
        count in 1..VERBOSE_HIT_LIMIT ||
            (count > VERBOSE_HIT_LIMIT && count % SPARSE_HIT_MODULO == 0L)

    fun install(lpparam: LoadParam) {
        val packageName = lpparam.packageName.orEmpty()
        if (packageName.isEmpty()) return
        val classLoader = lpparam.classLoader
        val loaderId = System.identityHashCode(classLoader)
        // A concurrent set, because onLoadPackage can be dispatched from more than one thread and a
        // plain mutableSet behind a ConcurrentHashMap would race.
        val claimed = claimedLoaders.computeIfAbsent(packageName) { ConcurrentHashMap.newKeySet() }
        if (!shouldAttempt(loaderId, claimed)) return
        // Reserve before the lookup so two racing dispatches cannot both hook the same method;
        // released below whenever the lookup shows there is nothing to hook.
        if (!claimed.add(loaderId)) return

        val clazz = runCatching { classLoader.loadClass(GATE_CLASS) }.getOrNull()
        if (clazz == null) {
            // Released, not kept: a later dispatch carrying a different (authoritative) loader must
            // still get a chance, which is exactly what the old package-level latch destroyed.
            claimed.remove(loaderId)
            XLog.i(
                TAG,
                "provider gate absent pkg=$packageName class=$GATE_CLASS loader=$loaderId " +
                    "claimed=${claimed.size}",
            )
            return
        }
        val methods = runCatching { clazz.declaredMethods }
            .getOrDefault(emptyArray())
            .filter { it.matchesProviderGate() }
        when (methods.size) {
            0 -> {
                claimed.remove(loaderId)
                XLog.i(TAG, "provider gate method absent pkg=$packageName class=$GATE_CLASS")
            }
            1 -> {
                val gate = methods.single()
                // The gate caches its negative answer, so a compiled/inlined copy of the original
                // body can keep answering false even after the hook exists. Discard it first; a
                // runtime without deoptimization just reports false and the hook still installs.
                val deoptimized = deoptimizeTarget?.invoke(gate)
                gate.hook {
                    doAfter {
                        val original = result
                        val count = hits.incrementAndGet()
                        if (shouldLogHit(count)) {
                            XLog.i(
                                TAG,
                                "gate hit pkg=$packageName original=$original forced=true hits=$count",
                            )
                        }
                        result = true
                    }
                }
                // Claimed above by the reserve; the hook is now live, so the claim stands.
                XLog.i(
                    TAG,
                    "provider gate forced pkg=$packageName class=$GATE_CLASS method=$GATE_METHOD " +
                        "loader=$loaderId deoptimized=$deoptimized",
                )
            }
            else -> {
                claimed.remove(loaderId)
                XLog.w(TAG, "provider gate ambiguous pkg=$packageName count=${methods.size}")
            }
        }
    }

    /** Test seam: forget every claim, as a fresh process would. */
    internal fun resetForTest() {
        claimedLoaders.clear()
        hits.set(0)
        deoptimizeTarget = null
    }

    internal fun claimedLoaderIds(packageName: String): Set<Int> =
        claimedLoaders[packageName]?.toSet().orEmpty()

    /** Static, no-argument, primitive-boolean `isMiui` — matches the verified gate exactly. */
    internal fun Method.matchesProviderGate(): Boolean =
        name == GATE_METHOD &&
            Modifier.isStatic(modifiers) &&
            parameterCount == 0 &&
            returnType == Boolean::class.javaPrimitiveType
}
