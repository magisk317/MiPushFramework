package io.github.magisk317.mipush.hook.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.mipush.common.ANDROID_PACKAGE_NAME
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_ANTI_KILL
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_AUTHORITY
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_COLUMN_ENABLED
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_COLUMN_KEY
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_DOZE_BYPASS
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_OOM_ADJ
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_PATH_FLAGS
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_READ_PERMISSION
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_STANDBY_BYPASS
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.HookCallback
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.currentApplication
import io.github.magisk317.xposed.findHookClass
import io.github.magisk317.xposed.hook
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class KeepAliveHook : BaseHook() {
    companion object {
        private const val TAG = "KeepAliveHook"
        private const val PREF_REFRESH_INTERVAL_MS = 60_000L
        private const val LOG_INTERVAL_MS = 60_000L
        private val PREF_URI = Uri.parse("content://$KEEPALIVE_PREF_AUTHORITY/$KEEPALIVE_PREF_PATH_FLAGS")
        private val PREF_KEYS = arrayOf(
            KEEPALIVE_PREF_OOM_ADJ,
            KEEPALIVE_PREF_ANTI_KILL,
            KEEPALIVE_PREF_STANDBY_BYPASS,
            KEEPALIVE_PREF_DOZE_BYPASS,
        )

        @Volatile
        private var flags = KeepAliveFlags()

        private val lastLogAt = ConcurrentHashMap<String, Long>()
    }

    private val platform = KeepAlivePlatformAdapter()
    private val refreshLock = Any()
    private val refreshGeneration = AtomicLong()

    @Volatile
    private var refreshLoopStarted = false
    private var refreshExecutor: ExecutorService? = null
    private var refreshPollThread: Thread? = null
    private var refreshReceiverThread: Thread? = null
    private var preferenceReceiverContext: Context? = null
    private var preferenceReceiver: BroadcastReceiver? = null

    override fun onLoadPackage(param: LoadParam) {
        if (param.packageName != ANDROID_PACKAGE_NAME || param.processName != ANDROID_PACKAGE_NAME) return

        XLog.i(TAG, "loading in system_server")
        startPreferenceRefreshLoop()
        hookOomAdjuster(param.classLoader)
        hookKillProcess(param.classLoader)
        hookPackageKill(param.classLoader)
        hookAppStandbyController(param.classLoader)
        XLog.i(
            TAG,
            "doze bypass requires the real DeviceIdle whitelist; no DeviceIdle query result is overridden",
        )
    }

    private fun startPreferenceRefreshLoop() {
        if (refreshLoopStarted) return
        synchronized(refreshLock) {
            if (refreshLoopStarted) return
            refreshExecutor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "MiPushKeepAlivePrefs").apply { isDaemon = true }
            }
            refreshLoopStarted = true
            val generation = refreshGeneration.incrementAndGet()
            refreshFlagsAsync()
            refreshReceiverThread = Thread({
                registerPreferenceReceiverWhenReady(generation)
            }, "MiPushKeepAlivePrefReceiver").apply {
                isDaemon = true
                start()
            }
            refreshPollThread = Thread({
                while (isRefreshActive(generation)) {
                    try {
                        Thread.sleep(PREF_REFRESH_INTERVAL_MS)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                    if (isRefreshActive(generation)) {
                        refreshFlagsAsync()
                    }
                }
            }, "MiPushKeepAlivePrefPoll").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun registerPreferenceReceiverWhenReady(generation: Long) {
        while (isRefreshActive(generation)) {
            val app = currentApplication()
            if (app != null) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        refreshFlagsAsync()
                    }
                }
                val registered = runCatching {
                    val filter = IntentFilter(ACTION_PREF_CHANGED).apply {
                        addAction(Intent.ACTION_USER_UNLOCKED)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        app.registerReceiver(
                            receiver,
                            filter,
                            KEEPALIVE_PREF_READ_PERMISSION,
                            null,
                            Context.RECEIVER_EXPORTED,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        app.registerReceiver(receiver, filter, KEEPALIVE_PREF_READ_PERMISSION, null)
                    }
                }.onFailure {
                    logRateLimited("pref_receiver", "failed to register keepalive preference receiver: ${it.message}")
                }.isSuccess
                if (registered) {
                    val keepRegistration = synchronized(refreshLock) {
                        if (isRefreshActive(generation)) {
                            preferenceReceiverContext = app
                            preferenceReceiver = receiver
                            true
                        } else {
                            false
                        }
                    }
                    if (keepRegistration) {
                        refreshFlagsAsync()
                        return
                    }
                    runCatching { app.unregisterReceiver(receiver) }
                    return
                }
            }
            try {
                Thread.sleep(1_000L)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    private fun refreshFlagsAsync() {
        val executor = refreshExecutor ?: return
        if (executor.isShutdown) return
        runCatching { executor.execute(::refreshFlags) }
    }

    override fun onHotReloading() {
        stopPreferenceRefreshLoop()
    }

    private fun stopPreferenceRefreshLoop() {
        val receiverContext: Context?
        val receiver: BroadcastReceiver?
        val receiverThread: Thread?
        val pollThread: Thread?
        val executor: ExecutorService?
        synchronized(refreshLock) {
            refreshGeneration.incrementAndGet()
            refreshLoopStarted = false
            receiverContext = preferenceReceiverContext
            receiver = preferenceReceiver
            receiverThread = refreshReceiverThread
            pollThread = refreshPollThread
            executor = refreshExecutor
            preferenceReceiverContext = null
            preferenceReceiver = null
            refreshReceiverThread = null
            refreshPollThread = null
            refreshExecutor = null
        }
        receiverThread?.interrupt()
        pollThread?.interrupt()
        executor?.shutdownNow()
        if (receiverContext != null && receiver != null) {
            runCatching { receiverContext.unregisterReceiver(receiver) }
        }
    }

    private fun isRefreshActive(generation: Long): Boolean =
        refreshLoopStarted && refreshGeneration.get() == generation

    private fun refreshFlags() {
        val app = currentApplication() ?: return
        runCatching {
            val values = app.contentResolver.query(PREF_URI, null, null, PREF_KEYS, null)?.use { cursor ->
                val keyIndex = cursor.getColumnIndex(KEEPALIVE_PREF_COLUMN_KEY)
                val enabledIndex = cursor.getColumnIndex(KEEPALIVE_PREF_COLUMN_ENABLED)
                check(keyIndex >= 0 && enabledIndex >= 0) { "keepalive preference columns missing" }
                buildMap<String, Boolean> {
                    while (cursor.moveToNext()) {
                        put(cursor.getString(keyIndex), cursor.getInt(enabledIndex) != 0)
                    }
                }
            } ?: error("keepalive preference provider returned no cursor")
            check(PREF_KEYS.all(values::containsKey)) { "keepalive preference snapshot is incomplete" }
            KeepAliveFlags(
                ready = true,
                oomAdj = values.getValue(KEEPALIVE_PREF_OOM_ADJ),
                antiKill = values.getValue(KEEPALIVE_PREF_ANTI_KILL),
                standbyBypass = values.getValue(KEEPALIVE_PREF_STANDBY_BYPASS),
                dozeBypass = values.getValue(KEEPALIVE_PREF_DOZE_BYPASS),
            )
        }.onSuccess { updated ->
            val previous = flags
            flags = updated
            if (previous != updated) {
                XLog.i(
                    TAG,
                    "keepalive preferences ready=true oom=${updated.oomAdj} antiKill=${updated.antiKill} " +
                        "standby=${updated.standbyBypass} doze=${updated.dozeBypass}",
                )
            }
        }.onFailure {
            val previous = flags
            // A provider failure invalidates the complete snapshot. Retaining ready=true here
            // would keep old anti-kill and resource-bypass decisions active after a revoked or
            // unavailable configuration.
            flags = KeepAliveFlags()
            if (previous.ready) {
                XLog.w(TAG, "keepalive preferences unavailable; disabled stale runtime flags")
            }
            logRateLimited("pref_refresh", "failed to refresh keepalive prefs: ${it.message}")
        }
    }

    private fun hookOomAdjuster(classLoader: ClassLoader) {
        runCatching {
            val owner = findHookClass("com.android.server.am.OomAdjuster", classLoader)
            val resolved = resolveTarget("oom_apply", owner, KeepAliveHookTargets.oomApply) ?: return
            resolved.method.hook {
                doBefore {
                    val record = args.getOrNull(0) ?: return@doBefore
                    if (platform.adjustOomAdj(record, flags)) {
                        logRateLimited("oom_adjust", "updated ProcessStateRecord.mCurAdj for XMSF")
                    }
                }
            }
        }.onFailure {
            XLog.e(TAG, "failed to hook OomAdjuster", it)
        }
    }

    private fun hookKillProcess(classLoader: ClassLoader) {
        runCatching {
            val owner = findHookClass("com.android.server.am.ProcessRecord", classLoader)
            val resolved = resolveTarget("kill_guard", owner, KeepAliveHookTargets.killLocked) ?: return
            val reasonIndex = requireNotNull(resolved.target.valueIndex)
            val subReasonIndex = requireNotNull(resolved.target.secondaryValueIndex)
            resolved.method.hook {
                doBefore {
                    val record = thisObject ?: return@doBefore
                    val mappings = platform.activeRecordMappings(record)
                    val reason = args.getOrNull(reasonIndex) as? Int
                    val subReason = args.getOrNull(subReasonIndex) as? Int
                    if (
                        KeepAlivePolicy.shouldSuppressKill(
                            flags = flags,
                            processName = mappings.processName,
                            reason = reason,
                            subReason = subReason,
                            currentNameMapping = mappings.nameCurrent,
                            currentPidMapping = mappings.pidCurrent,
                        )
                    ) {
                        result = null
                        logRateLimited("kill_guard", "suppressed current XMSF automatic kill reason=$reason subReason=$subReason")
                    }
                }
            }
        }.onFailure {
            XLog.e(TAG, "failed to hook ProcessRecord.killLocked", it)
        }
    }

    private fun hookPackageKill(classLoader: ClassLoader) {
        runCatching {
            val owner = findHookClass("com.android.server.am.ProcessList", classLoader)
            val resolved = resolveTarget("package_kill_guard", owner, KeepAliveHookTargets.packageKill) ?: return
            val packageIndex = requireNotNull(resolved.target.packageIndex)
            val reasonIndex = requireNotNull(resolved.target.valueIndex)
            val subReasonIndex = requireNotNull(resolved.target.secondaryValueIndex)
            resolved.method.hook {
                doBefore {
                    val packageName = args.getOrNull(packageIndex) as? String
                    val reason = args.getOrNull(reasonIndex) as? Int
                    val subReason = args.getOrNull(subReasonIndex) as? Int
                    // These indexes are part of the exact API 33 descriptor above:
                    // callerWillRestart=4, doit=6, evenPersistent=7, setRemoved=8,
                    // uninstalling=9. Unknown or malformed values fail open.
                    if (
                        KeepAlivePolicy.shouldSuppressPackageKill(
                            flags = flags,
                            packageName = packageName,
                            reason = reason,
                            subReason = subReason,
                            callerWillRestart = args.getOrNull(4) as? Boolean,
                            doit = args.getOrNull(6) as? Boolean,
                            evenPersistent = args.getOrNull(7) as? Boolean,
                            setRemoved = args.getOrNull(8) as? Boolean,
                            uninstalling = args.getOrNull(9) as? Boolean,
                        )
                    ) {
                        result = false
                        logRateLimited(
                            "package_kill_guard",
                            "suppressed current XMSF package automatic kill reason=$reason subReason=$subReason",
                        )
                    }
                }
            }
        }.onFailure {
            XLog.e(TAG, "failed to hook ProcessList.killPackageProcessesLSP", it)
        }
    }

    private fun hookAppStandbyController(classLoader: ClassLoader) {
        runCatching {
            val owner = findHookClass("com.android.server.usage.AppStandbyController", classLoader)
            hookStandbyBucket(owner)
            hookIdleEntry(owner, KeepAliveHookTargets.appIdle)
            hookIdleEntry(owner, KeepAliveHookTargets.forceIdle)
        }.onFailure {
            XLog.e(TAG, "failed to hook AppStandbyController", it)
        }
    }

    private fun hookStandbyBucket(owner: Class<*>) {
        KeepAliveHookTargets.standbyBucket.forEach { target ->
            hookStandbyBucketTarget(owner, target)
        }
    }

    private fun hookStandbyBucketTarget(owner: Class<*>, target: IndexedHookTarget) {
        val resolved = resolveTarget("standby_bucket", owner, listOf(target)) ?: return
        val packageIndex = requireNotNull(resolved.target.packageIndex)
        val bucketIndex = requireNotNull(resolved.target.valueIndex)
        resolved.method.hook {
            doBefore {
                val packageName = args.getOrNull(packageIndex) as? String ?: return@doBefore
                val currentBucket = args.getOrNull(bucketIndex) as? Int ?: return@doBefore
                val targetBucket = KeepAlivePolicy.desiredStandbyBucket(flags, packageName, currentBucket) ?: return@doBefore
                args[bucketIndex] = targetBucket
                logRateLimited("standby_bucket", "forced XMSF standby bucket ACTIVE from $currentBucket")
            }
        }
    }

    private fun hookIdleEntry(owner: Class<*>, targets: List<IndexedHookTarget>) {
        val resolved = resolveTarget(targets.single().capability, owner, targets) ?: return
        val packageIndex = requireNotNull(resolved.target.packageIndex)
        val idleIndex = requireNotNull(resolved.target.valueIndex)
        resolved.method.hook {
            doBefore {
                val packageName = args.getOrNull(packageIndex) as? String ?: return@doBefore
                val idle = args.getOrNull(idleIndex) as? Boolean ?: return@doBefore
                val targetIdle = KeepAlivePolicy.desiredIdleState(flags, packageName, idle) ?: return@doBefore
                args[idleIndex] = targetIdle
                logRateLimited("${resolved.target.capability}_idle", "kept XMSF out of forced app idle")
            }
        }
    }

    private fun resolveTarget(
        capability: String,
        owner: Class<*>,
        targets: List<IndexedHookTarget>,
    ): MethodResolution.Resolved? {
        return when (val resolution = KeepAliveHookTargets.resolve(owner, targets)) {
            is MethodResolution.Resolved -> {
                resolution.method.isAccessible = true
                XLog.i(TAG, "hook $capability installed ${KeepAliveHookTargets.describe(resolution.target)}")
                resolution
            }
            MethodResolution.Missing -> {
                XLog.w(TAG, "hook $capability unavailable: exact descriptor missing")
                null
            }
            is MethodResolution.Ambiguous -> {
                XLog.w(TAG, "hook $capability unavailable: ${resolution.candidates.size} exact descriptors matched")
                null
            }
        }
    }

    private fun logRateLimited(key: String, message: String) {
        val now = System.currentTimeMillis()
        val previous = lastLogAt.put(key, now)
        if (previous == null || now - previous >= LOG_INTERVAL_MS) {
            XLog.i(TAG, message)
        }
    }
}
