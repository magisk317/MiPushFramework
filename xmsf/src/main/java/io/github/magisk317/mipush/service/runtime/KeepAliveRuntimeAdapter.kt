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
import android.os.SystemClock
import co.touchlab.kermit.Logger
import com.xiaomi.xmpush.thrift.ConfigKey
import com.xiaomi.xmsf.services.IMainProcBridge
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.xposed.logging.MagiskOtel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Stock-compatible keep-alive binding runtime with a polling process source.
 *
 * Stock XMSF keeps a registry of signed strategy JSON and binds the target service while one of
 * its trigger processes owns a foreground activity. The `package`, `class`, `action`, `process`,
 * `app_list`, `bind_even_alive`, and `calm_down_period` keys come from stock 7.4.67-C
 * `com.xiaomi.xmsf.services.keepalive.strategy.a`; owner transfer, delayed bind/unbind, and retry
 * behavior follow its `bc.b` binding path. Process-observer registration is layered on separately;
 * this class keeps polling as the compatibility source when observer registration is unavailable.
 */
object KeepAliveRuntimeAdapter {
    private const val TAG = "KeepAliveRuntime"
    private const val PREFS_NAME = "stock_keepalive_runtime"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ONETRACK_ENABLED = "onetrack_enabled"
    private const val STRATEGY_PREFIX = "strategy:"
    private const val RECONCILE_INTERVAL_MS = 60_000L
    internal const val BIND_RETRY_INTERVAL_MS = 5_000L
    internal const val STOCK_DEFAULT_CALM_DOWN_MS = 5_000L
    internal const val STOCK_MIN_CALM_DOWN_MS = 2_000L
    internal const val MAX_BIND_RETRY_COUNT = 3
    // Stock 7.4.67-C za.f: OnetrackSwitch=140 and KASwitch=142. These now come
    // from the updated pinned wire enum instead of duplicating raw IDs here.
    internal val ONLINE_CONFIG_KEY_ONETRACK = ConfigKey.OnetrackSwitch.value
    internal val ONLINE_CONFIG_KEY_KEEP_ALIVE = ConfigKey.KASwitch.value

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val lock = Any()
    private val strategies = linkedMapOf<String, Strategy>()
    private val bindings = linkedMapOf<String, BindingRecord>()
    private val pendingBinds = linkedMapOf<String, PendingAction>()
    private val pendingUnbinds = linkedMapOf<String, PendingAction>()
    private val pendingRetries = linkedMapOf<String, PendingAction>()
    private val observerForegroundProcesses = linkedMapOf<Int, String>()
    private val observerProcessNames = linkedMapOf<Int, String>()
    private var appContext: Context? = null
    private var processObserver: ProcessObserverCompat? = null
    private var initialized = false
    private var active = false
    private var onlineConfigKnown = false
    private var enabled = true
    private var oneTrackEnabled = true
    private var pollScheduled = false
    private var observerRegistered = false
    private var observerFallbackLogged = false
    private var foregroundActivityProcesses = emptySet<String>()
    private var lifecycleGeneration = 0L

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
            Logger.withTag(TAG).w(it) { "Keep-alive online config unavailable" }
            return
        }
        val oneTrack = runCatching {
            connectedBridge.getOnlineBooleanConfig(ONLINE_CONFIG_KEY_ONETRACK, true)
        }.getOrElse {
            Logger.withTag(TAG).w(it) { "OneTrack online config unavailable" }
            snapshot().oneTrackEnabled
        }
        // Preserve the stock subprocess state without overriding the product-wide
        // TelemetryDisabler policy in the main app process.
        updateOnlineConfig(context, keepAlive, oneTrack)
    }

    fun updateOnlineConfig(context: Context, keepAliveEnabled: Boolean, oneTrackEnabled: Boolean) {
        ensureInitialized(context)
        synchronized(lock) {
            // A new ServiceBox configuration invalidates any queued shutdown cleanup from the
            // previous service instance.
            lifecycleGeneration++
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
        val parsedStrategy = parseStrategy(configJson) ?: return false
        val strategy = parsedStrategy.withDeviceSupport(context.applicationContext)
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
                hasTargetStateLocked(strategy.targetPackage)
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
        val shutdownGeneration = synchronized(lock) {
            lifecycleGeneration++
            active = false
            onlineConfigKnown = false
            pollScheduled = false
            observerRegistered = false
            observerFallbackLogged = false
            lifecycleGeneration
        }
        handler.post {
            val shouldCleanup = synchronized(lock) { lifecycleGeneration == shutdownGeneration }
            if (shouldCleanup) {
                stopProcessObserver()
                unbindAll()
            }
        }
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

    /** Removes stale keep-alive state after the target package clears its data. */
    fun clearPackageState(
        context: Context,
        targetPackage: String,
        userId: Int = currentUserId(),
    ) {
        if (targetPackage.isBlank()) return
        val normalizedUserId = userId.coerceAtLeast(0)
        val processUserId = currentUserId()
        // KeepAlive state is process-local. A package-data callback resolved for another user
        // must not clear a same-named target in this process.
        if (normalizedUserId != processUserId) {
            Logger.withTag(TAG).w { "skip keep-alive cleanup for foreign user=$normalizedUserId currentUser=$processUserId package=$targetPackage" }
            return
        }
        ensureInitialized(context)
        val removed = synchronized(lock) {
            strategies.remove(targetPackage) != null ||
                bindings.containsKey(targetPackage) ||
                pendingBinds.containsKey(targetPackage) ||
                pendingUnbinds.containsKey(targetPackage) ||
                pendingRetries.containsKey(targetPackage)
        }
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove(STRATEGY_PREFIX + targetPackage)
            ?.apply()
        if (removed) {
            handler.post { unbindTarget(context.applicationContext, targetPackage) }
        }
        MagiskOtel.event(
            name = "push.keepalive",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "clear",
                "reason" to "package_data_cleared",
                "target_package" to targetPackage,
            ),
            statusOk = true,
        )
    }

    internal fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            enabled = enabled,
            oneTrackEnabled = oneTrackEnabled,
            strategyPackages = strategies.keys.toSet(),
            boundTargetPackages = bindings.filterValues { it.connected }.keys,
            bindingOwners = bindings
                .filterValues { it.connected }
                .mapValues { it.value.ownerProcess },
            pendingBindTargetPackages = pendingBinds.keys.toSet(),
            pendingRetryTargetPackages = pendingRetries.keys.toSet(),
            active = active,
            onlineConfigKnown = onlineConfigKnown,
            pollScheduled = pollScheduled,
            observerRegistered = observerRegistered,
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
            val supportEnvironment = KeepAliveEnvironment.snapshot(applicationContext)
            preferences.all.forEach { (key, value) ->
                if (key.startsWith(STRATEGY_PREFIX) && value is String) {
                    parseStrategy(value)
                        ?.let { strategy ->
                            strategy.copy(
                                supportedOnDevice = strategy.supportedOnDevice &&
                                    KeepAliveEnvironment.deviceSupportBlockReason(
                                        strategy,
                                        supportEnvironment,
                                    ) == null,
                            )
                        }
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
            shouldReconcile(
                onlineConfigKnown = onlineConfigKnown,
                active = active,
                enabled = enabled,
                strategyCount = strategies.size,
            )
        }
        if (!shouldSchedule) {
            stopProcessObserver()
            return
        }
        val context = synchronized(lock) { appContext } ?: return
        if (ensureProcessObserver(context)) {
            synchronized(lock) { observerFallbackLogged = false }
            return
        }
        val shouldLogFallback = synchronized(lock) {
            if (observerFallbackLogged) false else {
                observerFallbackLogged = true
                true
            }
        }
        if (shouldLogFallback) {
            Logger.withTag(TAG).w { "Process observer unavailable; using ${RECONCILE_INTERVAL_MS}ms polling fallback" }
            MagiskOtel.event(
                name = "push.keepalive",
                attributes = mapOf(
                    "result" to "fallback",
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "process_observer",
                    "reason" to "polling_fallback",
                    "poll_interval_ms" to RECONCILE_INTERVAL_MS.toString(),
                ),
                statusOk = true,
            )
        }
        handler.postDelayed(reconcileRunnable, RECONCILE_INTERVAL_MS)
        synchronized(lock) { pollScheduled = true }
    }

    private fun reconcileNow() {
        val context = synchronized(lock) {
            if (onlineConfigKnown && active) appContext else null
        } ?: return
        val state = synchronized(lock) { enabled to strategies.values.toList() }
        if (!state.first) {
            unbindAll()
            return
        }

        reconcileSnapshot(context, processSnapshot(context), state.second)
    }

    private fun processSnapshot(context: Context): ProcessSnapshot {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return runCatching {
            val processes = manager?.runningAppProcesses.orEmpty()
            ProcessSnapshot(
                allProcessNames = processes.mapNotNull { it.processName }.toSet(),
                foregroundActivityProcessNames = processes
                    .asSequence()
                    .filter { it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
                    .mapNotNull { it.processName }
                    .toSet(),
                processNamesByPid = processes
                    .mapNotNull { process ->
                        process.processName?.let { process.pid to it }
                    }
                    .toMap(),
                foregroundActivityPids = processes
                    .asSequence()
                    .filter { it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
                    .map { it.pid }
                    .toSet(),
            )
        }.getOrDefault(ProcessSnapshot(emptySet(), emptySet()))
    }

    private fun ensureProcessObserver(context: Context): Boolean {
        if (synchronized(lock) { observerRegistered }) return true
        val observer = synchronized(lock) {
            processObserver ?: ProcessObserverCompat(
                onProcessStarted = { pid, processName ->
                    handler.post { handleObserverProcessStarted(pid, processName) }
                },
                onForegroundActivitiesChanged = { pid, _, foreground ->
                    handler.post { handleObserverForegroundChange(context, pid, foreground) }
                },
                onProcessDied = { pid, _ ->
                    handler.post { handleObserverForegroundChange(context, pid, foreground = false) }
                },
            ).also { processObserver = it }
        }
        if (!observer.register()) return false

        val initialSnapshot = processSnapshot(context)
        synchronized(lock) {
            observerRegistered = true
            observerFallbackLogged = false
            pollScheduled = false
            observerForegroundProcesses.clear()
            observerProcessNames.clear()
            initialSnapshot.foregroundActivityPids.forEach { pid ->
                initialSnapshot.processNamesByPid[pid]?.let {
                    observerProcessNames[pid] = it
                    observerForegroundProcesses[pid] = it
                }
            }
        }
        Logger.withTag(TAG).i { "Process observer registered; polling fallback disabled" }
        MagiskOtel.event(
            name = "push.keepalive",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "process_observer",
                "reason" to "registered",
            ),
            statusOk = true,
        )
        reconcileSnapshot(
            context,
            initialSnapshot.copy(
                foregroundActivityProcessNames = synchronized(lock) {
                    observerForegroundProcesses.values.toSet()
                },
            ),
            synchronized(lock) { strategies.values.toList() },
        )
        return true
    }

    private fun handleObserverProcessStarted(pid: Int, processName: String) {
        if (processName.isBlank()) return
        synchronized(lock) {
            if (active && observerRegistered) {
                observerProcessNames[pid] = processName
            }
        }
    }

    private fun handleObserverForegroundChange(context: Context, pid: Int, foreground: Boolean) {
        val currentState = synchronized(lock) {
            if (!active || !observerRegistered) return
            strategies.values.toList()
        }
        val snapshot = processSnapshot(context)
        val processName = snapshot.processNamesByPid[pid]
            ?: synchronized(lock) {
                observerProcessNames[pid] ?: observerForegroundProcesses[pid]
            }
        synchronized(lock) {
            if (foreground && processName != null) {
                observerForegroundProcesses[pid] = processName
            } else {
                observerForegroundProcesses.remove(pid)
            }
            if (!foreground) observerProcessNames.remove(pid)
        }
        reconcileSnapshot(
            context,
            snapshot.copy(
                foregroundActivityProcessNames = synchronized(lock) {
                    observerForegroundProcesses.values.toSet()
                },
                foregroundActivityPids = synchronized(lock) {
                    observerForegroundProcesses.keys.toSet()
                },
            ),
            currentState,
        )
    }

    private fun stopProcessObserver() {
        val observer = synchronized(lock) {
            observerRegistered = false
            observerForegroundProcesses.clear()
            observerProcessNames.clear()
            processObserver
        }
        observer?.unregister()
    }

    private fun reconcileSnapshot(
        context: Context,
        snapshot: ProcessSnapshot,
        currentStrategies: List<Strategy>,
    ) {
        val previousForeground = synchronized(lock) {
            foregroundActivityProcesses.also {
                foregroundActivityProcesses = snapshot.foregroundActivityProcessNames
            }
        }
        (previousForeground - snapshot.foregroundActivityProcessNames).forEach { processName ->
            handleForegroundChange(context, processName, foreground = false, currentStrategies)
        }
        (snapshot.foregroundActivityProcessNames - previousForeground).forEach { processName ->
            handleForegroundChange(context, processName, foreground = true, currentStrategies)
        }

        // A skipped bind (for example because the target process was alive) has no stock binding
        // record. Polling must reconsider it even when the foreground set itself did not change.
        currentStrategies.forEach { strategy ->
            val hasTargetState = synchronized(lock) { hasTargetStateLocked(strategy.targetPackage) }
            if (!hasTargetState) {
                bindingTrigger(strategy, snapshot)?.let { trigger ->
                    scheduleBind(context, strategy, trigger)
                }
            }
        }
    }

    private fun handleForegroundChange(
        context: Context,
        processName: String,
        foreground: Boolean,
        currentStrategies: List<Strategy>,
    ) {
        currentStrategies
            .asSequence()
            .filter { processName in it.triggerProcesses }
            .forEach { strategy ->
                if (foreground) {
                    scheduleBind(context, strategy, processName)
                } else {
                    scheduleUnbind(context, strategy, processName)
                }
            }
    }

    private fun scheduleBind(context: Context, strategy: Strategy, triggerProcess: String) {
        val targetPackage = strategy.targetPackage
        cancelTargetCallbacks(targetPackage)
        val existing = synchronized(lock) { bindings[targetPackage] }
        if (existing?.connected == true) {
            // Stock 7.4.67-C bc.b updates the binding owner when another configured trigger enters
            // the foreground. Only the current owner is then allowed to schedule the unbind.
            synchronized(lock) { bindings[targetPackage]?.ownerProcess = triggerProcess }
            return
        }
        if (existing != null) {
            synchronized(lock) { bindings.remove(targetPackage) }
            runCatching { context.unbindService(existing.connection) }
        }

        lateinit var runnable: Runnable
        runnable = Runnable {
            val shouldRun = synchronized(lock) {
                pendingBinds[targetPackage]?.runnable === runnable &&
                    pendingBinds.remove(targetPackage) != null
            }
            if (shouldRun) attemptBind(context, strategy, triggerProcess, retryCount = 0)
        }
        synchronized(lock) {
            pendingBinds[targetPackage] = PendingAction(triggerProcess, runnable)
        }
        handler.postDelayed(runnable, effectiveCalmDownMs(strategy.calmDownPeriodMs))
    }

    private fun scheduleUnbind(context: Context, strategy: Strategy, triggerProcess: String) {
        val targetPackage = strategy.targetPackage
        cancelTargetCallbacks(targetPackage)
        val binding = synchronized(lock) { bindings[targetPackage] } ?: return
        if (binding.ownerProcess != triggerProcess) return
        if (!binding.connected) {
            unbindTarget(context, targetPackage)
            return
        }

        lateinit var runnable: Runnable
        runnable = Runnable {
            val shouldRun = synchronized(lock) {
                pendingUnbinds[targetPackage]?.runnable === runnable &&
                    pendingUnbinds.remove(targetPackage) != null
            }
            if (shouldRun) unbindTarget(context, targetPackage)
        }
        synchronized(lock) {
            pendingUnbinds[targetPackage] = PendingAction(triggerProcess, runnable)
        }
        handler.postDelayed(runnable, effectiveCalmDownMs(strategy.calmDownPeriodMs))
    }

    private fun attemptBind(
        context: Context,
        strategy: Strategy,
        triggerProcess: String,
        retryCount: Int,
    ) {
        if (retryCount > MAX_BIND_RETRY_COUNT) {
            Logger.withTag(TAG).w { "Keep-alive binding stopped after $MAX_BIND_RETRY_COUNT retries: ${strategy.targetPackage}" }
            return
        }
        val mayBind = synchronized(lock) {
            active && enabled && strategies[strategy.targetPackage] == strategy &&
                triggerProcess in foregroundActivityProcesses
        }
        if (!mayBind) return

        val environmentBlock = KeepAliveEnvironment.bindBlockReason(
            strategy,
            KeepAliveEnvironment.snapshot(context),
        )
        if (environmentBlock != null) {
            Logger.withTag(TAG).i { "Keep-alive binding blocked for ${strategy.targetPackage}: $environmentBlock" }
            return
        }

        val allProcesses = processSnapshot(context).allProcessNames
        val targetProcess = strategy.targetProcess.ifBlank { strategy.targetPackage }
        if (!strategy.bindEvenAlive && targetProcess in allProcesses) return

        synchronized(lock) { bindings.remove(strategy.targetPackage) }?.let { stale ->
            runCatching { context.unbindService(stale.connection) }
        }
        val intent = buildBindIntent(strategy, triggerProcess)
        lateinit var record: BindingRecord
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val isCurrent = synchronized(lock) {
                    (bindings[strategy.targetPackage] === record).also {
                        if (it) record.connected = true
                    }
                }
                if (isCurrent) {
                    cancelPendingRetry(strategy.targetPackage)
                    Logger.withTag(TAG).i { "Keep-alive target connected package=${strategy.targetPackage} trigger=$triggerProcess" }
                    MagiskOtel.event(
                        name = "push.keepalive",
                        attributes = mapOf(
                            "result" to "ok",
                            "duration_ms" to "0",
                            "process" to "main",
                            "stage" to "bind",
                            "reason" to "service_connected",
                            "target_package" to strategy.targetPackage,
                        ),
                        statusOk = true,
                    )
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                handleBindingLost(context, strategy, triggerProcess, record, "service_disconnected")
            }

            override fun onBindingDied(name: ComponentName?) {
                handleBindingLost(context, strategy, triggerProcess, record, "binding_died")
            }

            override fun onNullBinding(name: ComponentName?) {
                handleBindingLost(context, strategy, triggerProcess, record, "null_binding")
            }
        }
        record = BindingRecord(triggerProcess, connection)
        synchronized(lock) { bindings[strategy.targetPackage] = record }

        scheduleBindRetry(context, strategy, triggerProcess, retryCount + 1)
        val bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
            .onFailure { Logger.withTag(TAG).w(it) { "Keep-alive target binding failed" } }
            .getOrDefault(false)
        if (!bound) {
            Logger.withTag(TAG).w { "Keep-alive target bindService returned false package=${strategy.targetPackage}" }
            synchronized(lock) {
                if (bindings[strategy.targetPackage] === record) {
                    bindings.remove(strategy.targetPackage)
                }
            }
        }
    }

    private fun handleBindingLost(
        context: Context,
        strategy: Strategy,
        triggerProcess: String,
        record: BindingRecord,
        reason: String,
    ) {
        val shouldRetry = synchronized(lock) {
            if (bindings[strategy.targetPackage] !== record) {
                false
            } else {
                record.connected = false
                active && enabled &&
                    strategies[strategy.targetPackage] == strategy &&
                    triggerProcess in foregroundActivityProcesses
            }
        }
        if (shouldRetry) {
            Logger.withTag(TAG).w { "Keep-alive target binding lost package=${strategy.targetPackage} reason=$reason; retry scheduled" }
            MagiskOtel.event(
                name = "push.keepalive",
                attributes = mapOf(
                    "result" to "retry",
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "bind",
                    "reason" to reason,
                    "target_package" to strategy.targetPackage,
                ),
                statusOk = true,
            )
            scheduleBindRetry(context, strategy, triggerProcess, retryCount = 1)
        }
    }

    private fun scheduleBindRetry(
        context: Context,
        strategy: Strategy,
        triggerProcess: String,
        retryCount: Int,
    ) {
        lateinit var runnable: Runnable
        runnable = Runnable {
            val shouldRetry = synchronized(lock) {
                pendingRetries[strategy.targetPackage]?.runnable === runnable &&
                    pendingRetries.remove(strategy.targetPackage) != null &&
                    bindings[strategy.targetPackage]?.connected != true
            }
            if (shouldRetry) attemptBind(context, strategy, triggerProcess, retryCount)
        }
        synchronized(lock) {
            pendingRetries[strategy.targetPackage] = PendingAction(triggerProcess, runnable)
        }
        handler.postDelayed(runnable, BIND_RETRY_INTERVAL_MS)
    }

    private fun cancelTargetCallbacks(targetPackage: String) {
        val callbacks = synchronized(lock) {
            listOfNotNull(
                pendingBinds.remove(targetPackage)?.runnable,
                pendingUnbinds.remove(targetPackage)?.runnable,
                pendingRetries.remove(targetPackage)?.runnable,
            )
        }
        callbacks.forEach(handler::removeCallbacks)
    }

    private fun cancelPendingRetry(targetPackage: String) {
        val runnable = synchronized(lock) { pendingRetries.remove(targetPackage)?.runnable }
        if (runnable != null) handler.removeCallbacks(runnable)
    }

    private fun unbindTarget(context: Context, targetPackage: String) {
        cancelTargetCallbacks(targetPackage)
        val binding = synchronized(lock) { bindings.remove(targetPackage) } ?: return
        runCatching { context.unbindService(binding.connection) }
            .onFailure { Logger.withTag(TAG).w(it) { "Keep-alive target unbind failed" } }
    }

    private fun unbindAll() {
        val context = synchronized(lock) { appContext } ?: return
        val packages = synchronized(lock) {
            (bindings.keys + pendingBinds.keys + pendingUnbinds.keys + pendingRetries.keys).toSet()
        }
        packages.forEach { unbindTarget(context, it) }
        synchronized(lock) { foregroundActivityProcesses = emptySet() }
    }

    internal data class Strategy(
        val targetPackage: String,
        val targetClass: String,
        val targetProcess: String,
        val targetAction: String,
        val triggerProcesses: Set<String>,
        val bindEvenAlive: Boolean,
        val memoryStandardMb: Int,
        val memoryUsageRate: Int,
        val batteryLowRate: Int,
        val maxTemperatureCelsius: Float,
        val deviceBlackList: Set<String>,
        val needStat: Boolean,
        val ignoreMiuiLite: Boolean,
        val calmDownPeriodMs: Int,
        val supportedOnDevice: Boolean,
    )

    internal data class ProcessSnapshot(
        val allProcessNames: Set<String>,
        val foregroundActivityProcessNames: Set<String>,
        val processNamesByPid: Map<Int, String> = emptyMap(),
        val foregroundActivityPids: Set<Int> = emptySet(),
    )

    internal data class Snapshot(
        val enabled: Boolean,
        val oneTrackEnabled: Boolean,
        val strategyPackages: Set<String>,
        val boundTargetPackages: Set<String>,
        val bindingOwners: Map<String, String>,
        val pendingBindTargetPackages: Set<String>,
        val pendingRetryTargetPackages: Set<String>,
        val active: Boolean,
        val onlineConfigKnown: Boolean,
        val pollScheduled: Boolean,
        val observerRegistered: Boolean,
    )

    internal fun parseStrategy(configJson: String?): Strategy? {
        if (configJson.isNullOrBlank()) return null
        return runCatching {
            val root = Json.parseToJsonElement(configJson).jsonObject
            val targetPackage = root["package"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val targetClass = root["class"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val targetAction = root["action"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (!PACKAGE_PATTERN.matches(targetPackage) || (targetClass.isBlank() && targetAction.isBlank())) {
                return null
            }
            val triggers = buildSet {
                val array = root["app_list"]?.jsonArray
                if (array != null) {
                    for (element in array) {
                        element.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank)?.let(::add)
                    }
                }
            }
            val blockedDevices = buildSet {
                val array = root["dev_black_list"]?.jsonArray
                if (array != null) {
                    for (element in array) {
                        element.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank)?.let { add(it.lowercase()) }
                    }
                }
            }
            Strategy(
                targetPackage = targetPackage,
                targetClass = targetClass,
                targetProcess = root["process"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                targetAction = targetAction,
                triggerProcesses = triggers,
                bindEvenAlive = root["bind_even_alive"]?.jsonPrimitive?.booleanOrNull ?: false,
                memoryStandardMb = root["men_std"]?.jsonPrimitive?.intOrNull ?: 0,
                memoryUsageRate = root["mem_usage_rate"]?.jsonPrimitive?.intOrNull ?: 0,
                batteryLowRate = root["battery_low_rate"]?.jsonPrimitive?.intOrNull ?: 0,
                maxTemperatureCelsius = (root["max_temperature"]?.jsonPrimitive?.doubleOrNull ?: 0.0).toFloat(),
                deviceBlackList = blockedDevices,
                // Stock 7.4.67-C uses need_stat only for keep-alive statistics. Preserve the
                // wire value for contract parity without re-enabling OneTrack uploads.
                needStat = root["need_stat"]?.jsonPrimitive?.booleanOrNull ?: true,
                ignoreMiuiLite = root["ignore_miui_lite"]?.jsonPrimitive?.booleanOrNull ?: false,
                calmDownPeriodMs = root["calm_down_period"]?.jsonPrimitive?.intOrNull ?: 0,
                supportedOnDevice = Build.DEVICE.lowercase() !in blockedDevices,
            )
        }.onFailure {
            Logger.withTag(TAG).w { "Rejected malformed keep-alive strategy" }
        }.getOrNull()
    }

    internal fun bindingTrigger(
        strategy: Strategy,
        snapshot: ProcessSnapshot,
        alreadyBound: Boolean = false,
    ): String? {
        val trigger = strategy.triggerProcesses
            .firstOrNull(snapshot.foregroundActivityProcessNames::contains)
            ?: return null
        if (alreadyBound) return trigger
        val targetProcess = strategy.targetProcess.ifBlank { strategy.targetPackage }
        return trigger.takeIf { strategy.bindEvenAlive || targetProcess !in snapshot.allProcessNames }
    }

    internal fun shouldReconcile(
        onlineConfigKnown: Boolean,
        active: Boolean,
        enabled: Boolean,
        strategyCount: Int,
    ): Boolean = onlineConfigKnown && active && enabled && strategyCount > 0

    internal fun buildBindIntent(strategy: Strategy, triggerProcess: String): Intent {
        // Stock 7.4.67-C bc.b selects the explicit action whenever it is non-empty, even when the
        // strategy also carries a class. This matters for targets whose action is resolved to a
        // ROM-specific service implementation rather than the fallback component.
        return if (strategy.targetAction.isNotBlank()) {
            Intent(strategy.targetAction)
        } else {
            Intent().setComponent(ComponentName(strategy.targetPackage, strategy.targetClass))
        }.setPackage(strategy.targetPackage)
            .putExtra("trigger_pkg", triggerProcess)
            .putExtra("WakeUpSource", STOCK_WAKE_UP_SOURCE)
    }

    internal fun effectiveCalmDownMs(configuredMs: Int): Long {
        // Stock 7.4.67-C bc.b uses 0x1388 (5000 ms) when calm_down_period is below
        // 2000 ms. JADX renders the constant as Level.TRACE_INT, so keep the DEX-confirmed
        // numeric behavior explicit here.
        return if (configuredMs < STOCK_MIN_CALM_DOWN_MS) {
            STOCK_DEFAULT_CALM_DOWN_MS
        } else {
            configuredMs.toLong()
        }
    }

    private fun Strategy.withDeviceSupport(context: Context): Strategy {
        if (!supportedOnDevice) return this
        return copy(
            supportedOnDevice = KeepAliveEnvironment.deviceSupportBlockReason(
                this,
                KeepAliveEnvironment.snapshot(context),
            ) == null,
        )
    }

    private fun hasTargetStateLocked(targetPackage: String): Boolean {
        return targetPackage in bindings || targetPackage in pendingBinds ||
            targetPackage in pendingUnbinds || targetPackage in pendingRetries
    }

    private fun currentUserId(): Int = runCatching { Utils.myUserId() }
        .getOrDefault(0)
        .coerceAtLeast(0)

    private data class PendingAction(
        val ownerProcess: String,
        val runnable: Runnable,
    )

    private data class BindingRecord(
        var ownerProcess: String,
        val connection: ServiceConnection,
        var connected: Boolean = false,
    )

    private const val STOCK_WAKE_UP_SOURCE = "com.xiaomi.xmsf"
    private val PACKAGE_PATTERN = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
}
