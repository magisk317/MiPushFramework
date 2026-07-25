package io.github.magisk317.mipush.service.runtime

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.xiaomi.xmsf.services.IMainProcBridge
import org.json.JSONObject
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Reduced, polling-based stock keep-alive runtime.
 *
 * Stock XMSF keeps a registry of signed strategy JSON and binds the target service while one of
 * its trigger processes is alive. This adapter preserves that useful behavior without importing
 * the stock process-observer implementation or treating the JSON as passive diagnostics data.
 * The `package`, `class`, `action`, `process`, and `app_list` keys come from stock 7.4.67-C
 * `com.xiaomi.xmsf.services.keepalive.strategy.a` and its `bc.b` binding path.
 */
object KeepAliveRuntimeAdapter {
    private const val TAG = "KeepAliveRuntime"
    private const val PREFS_NAME = "stock_keepalive_runtime"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ONETRACK_ENABLED = "onetrack_enabled"
    private const val STRATEGY_PREFIX = "strategy:"
    private const val RECONCILE_INTERVAL_MS = 60_000L
    // Stock 7.4.67-C za.f: OnetrackSwitch=140, KASwitch=142. The older pinned
    // ConfigKey snapshot intentionally does not yet contain these newer enum members.
    internal const val ONLINE_CONFIG_KEY_ONETRACK = 140
    internal const val ONLINE_CONFIG_KEY_KEEP_ALIVE = 142

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val lock = Any()
    private val strategies = linkedMapOf<String, Strategy>()
    private val connections = linkedMapOf<String, ServiceConnection>()
    private var appContext: Context? = null
    private var initialized = false
    private var active = false
    private var onlineConfigKnown = false
    private var enabled = true
    private var oneTrackEnabled = true
    private var pollScheduled = false

    private val reconcileRunnable = object : Runnable {
        override fun run() {
            synchronized(lock) { pollScheduled = false }
            reconcileNow()
            scheduleReconcile()
        }
    }

    fun refreshOnlineConfig(context: Context, bridge: IMainProcBridge?) {
        val connectedBridge = bridge ?: return
        val keepAlive = runCatching {
            connectedBridge.getOnlineBooleanConfig(ONLINE_CONFIG_KEY_KEEP_ALIVE, true)
        }.getOrElse {
            Log.w(TAG, "Keep-alive online config unavailable", it)
            return
        }
        val oneTrack = runCatching {
            connectedBridge.getOnlineBooleanConfig(ONLINE_CONFIG_KEY_ONETRACK, true)
        }.getOrElse {
            Log.w(TAG, "OneTrack online config unavailable", it)
            snapshot().oneTrackEnabled
        }
        // Preserve the stock subprocess state without overriding the product-wide
        // TelemetryDisabler policy in the main app process.
        updateOnlineConfig(context, keepAlive, oneTrack)
    }

    fun updateOnlineConfig(context: Context, keepAliveEnabled: Boolean, oneTrackEnabled: Boolean) {
        ensureInitialized(context)
        synchronized(lock) {
            // Stock creates its keep-alive manager only after ServiceBox has resolved KASwitch.
            // A strategy Binder call may arrive first, but it must not activate or bind anything.
            onlineConfigKnown = true
            active = keepAliveEnabled
            enabled = keepAliveEnabled
            this.oneTrackEnabled = oneTrackEnabled
            requireNotNull(appContext).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, keepAliveEnabled)
                .putBoolean(KEY_ONETRACK_ENABLED, oneTrackEnabled)
                .apply()
        }
        handler.post {
            if (keepAliveEnabled) {
                reconcileNow()
            } else {
                unbindAll()
            }
        }
        scheduleReconcile()
    }

    fun updateStrategy(context: Context, configJson: String?): Boolean {
        val strategy = parseStrategy(configJson) ?: return false
        ensureInitialized(context)
        val changedWhileBound = synchronized(lock) {
            val previous = if (strategy.supportedOnDevice) {
                strategies.put(strategy.targetPackage, strategy)
            } else {
                strategies.remove(strategy.targetPackage)
            }
            requireNotNull(appContext).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(STRATEGY_PREFIX + strategy.targetPackage, configJson)
                .apply()
            previous != null &&
                (!strategy.supportedOnDevice || previous != strategy) &&
                strategy.targetPackage in connections
        }
        handler.post {
            if (changedWhileBound) {
                unbindTarget(requireNotNull(appContext), strategy.targetPackage)
            }
            reconcileNow()
        }
        scheduleReconcile()
        MagiskOtel.event(
            name = "push.keepalive",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "bind",
                "reason" to "binding_trigger",
            ),
            statusOk = true,
        )
        return true
    }

    fun shutdown() {
        handler.removeCallbacks(reconcileRunnable)
        synchronized(lock) {
            active = false
            onlineConfigKnown = false
            pollScheduled = false
        }
        handler.post { unbindAll() }
        MagiskOtel.event(
            name = "push.keepalive",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "shutdown",
                "reason" to "adapter_shutdown",
            ),
            statusOk = true,
        )
    }

    internal fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            enabled = enabled,
            oneTrackEnabled = oneTrackEnabled,
            strategyPackages = strategies.keys.toSet(),
            boundTargetPackages = connections.keys.toSet(),
            active = active,
            onlineConfigKnown = onlineConfigKnown,
            pollScheduled = pollScheduled,
        )
    }

    private fun ensureInitialized(context: Context) {
        synchronized(lock) {
            if (initialized) return
            val applicationContext = context.applicationContext
            appContext = applicationContext
            val preferences = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            enabled = preferences.getBoolean(KEY_ENABLED, true)
            oneTrackEnabled = preferences.getBoolean(KEY_ONETRACK_ENABLED, true)
            preferences.all.forEach { (key, value) ->
                if (key.startsWith(STRATEGY_PREFIX) && value is String) {
                    parseStrategy(value)
                        ?.takeIf(Strategy::supportedOnDevice)
                        ?.let { strategies[it.targetPackage] = it }
                }
            }
            initialized = true
        }
    }

    private fun scheduleReconcile() {
        handler.removeCallbacks(reconcileRunnable)
        val shouldSchedule = synchronized(lock) {
            pollScheduled = false
            initialized && active && enabled && strategies.isNotEmpty()
        }
        if (shouldSchedule) {
            handler.postDelayed(reconcileRunnable, RECONCILE_INTERVAL_MS)
            synchronized(lock) { pollScheduled = true }
        }
    }

    private fun reconcileNow() {
        val context = synchronized(lock) { if (active) appContext else null } ?: return
        val state = synchronized(lock) { enabled to strategies.values.toList() }
        if (!state.first) {
            unbindAll()
            return
        }

        val runningProcesses = runningProcessNames(context)
        state.second.forEach { strategy ->
            val alreadyBound = strategy.targetPackage in synchronized(lock) { connections.keys }
            val trigger = bindingTrigger(strategy, runningProcesses, alreadyBound)
            if (trigger != null) {
                bindTarget(context, strategy, trigger)
            } else {
                unbindTarget(context, strategy.targetPackage)
            }
        }
    }

    private fun runningProcessNames(context: Context): Set<String> {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return runCatching {
            manager?.runningAppProcesses.orEmpty().mapNotNull { it.processName }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun bindTarget(context: Context, strategy: Strategy, triggerPackage: String) {
        if (strategy.targetPackage in synchronized(lock) { connections.keys }) return
        val intent = if (strategy.targetClass.isNotBlank()) {
            Intent().setComponent(ComponentName(strategy.targetPackage, strategy.targetClass))
        } else {
            Intent(strategy.targetAction)
        }.setPackage(strategy.targetPackage)
            .putExtra("trigger_pkg", triggerPackage)
            .putExtra("WakeUpSource", context.packageName)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) = Unit

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
            .onFailure { Log.w(TAG, "Keep-alive target binding failed", it) }
            .getOrDefault(false)
        if (bound) {
            synchronized(lock) { connections[strategy.targetPackage] = connection }
        }
    }

    private fun unbindTarget(context: Context, targetPackage: String) {
        val connection = synchronized(lock) { connections.remove(targetPackage) } ?: return
        runCatching { context.unbindService(connection) }
            .onFailure { Log.w(TAG, "Keep-alive target unbind failed", it) }
    }

    private fun unbindAll() {
        val context = synchronized(lock) { appContext } ?: return
        val packages = synchronized(lock) { connections.keys.toList() }
        packages.forEach { unbindTarget(context, it) }
    }

    internal data class Strategy(
        val targetPackage: String,
        val targetClass: String,
        val targetProcess: String,
        val targetAction: String,
        val triggerProcesses: Set<String>,
        val bindEvenAlive: Boolean,
        val supportedOnDevice: Boolean,
    )

    internal data class Snapshot(
        val enabled: Boolean,
        val oneTrackEnabled: Boolean,
        val strategyPackages: Set<String>,
        val boundTargetPackages: Set<String>,
        val active: Boolean,
        val onlineConfigKnown: Boolean,
        val pollScheduled: Boolean,
    )

    internal fun parseStrategy(configJson: String?): Strategy? {
        if (configJson.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(configJson)
            val targetPackage = root.optString("package").trim()
            val targetClass = root.optString("class").trim()
            val targetAction = root.optString("action").trim()
            if (!PACKAGE_PATTERN.matches(targetPackage) || (targetClass.isBlank() && targetAction.isBlank())) {
                return null
            }
            val triggers = buildSet {
                val array = root.optJSONArray("app_list")
                if (array != null) {
                    for (index in 0 until array.length()) {
                        array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
                    }
                }
            }
            val blockedDevices = buildSet {
                val array = root.optJSONArray("dev_black_list")
                if (array != null) {
                    for (index in 0 until array.length()) {
                        array.optString(index).trim().takeIf(String::isNotBlank)?.let { add(it.lowercase()) }
                    }
                }
            }
            Strategy(
                targetPackage = targetPackage,
                targetClass = targetClass,
                targetProcess = root.optString("process").trim(),
                targetAction = targetAction,
                triggerProcesses = triggers,
                bindEvenAlive = root.optBoolean("bind_even_alive", false),
                supportedOnDevice = Build.DEVICE.lowercase() !in blockedDevices,
            )
        }.onFailure {
            Log.w(TAG, "Rejected malformed keep-alive strategy")
        }.getOrNull()
    }

    internal fun bindingTrigger(
        strategy: Strategy,
        runningProcesses: Set<String>,
        alreadyBound: Boolean = false,
    ): String? {
        val trigger = strategy.triggerProcesses.firstOrNull(runningProcesses::contains) ?: return null
        if (alreadyBound) return trigger
        val targetProcess = strategy.targetProcess.ifBlank { strategy.targetPackage }
        return trigger.takeIf { strategy.bindEvenAlive || targetProcess !in runningProcesses }
    }

    private val PACKAGE_PATTERN = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
}
