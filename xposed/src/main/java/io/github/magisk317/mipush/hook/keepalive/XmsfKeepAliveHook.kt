package io.github.magisk317.mipush.hook.keepalive

import android.annotation.SuppressLint
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
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.currentApplication
import io.github.magisk317.xposed.keepalive.KeepAliveConfig
import io.github.magisk317.xposed.keepalive.KeepAliveFlags
import io.github.magisk317.xposed.keepalive.KeepAliveFlagsProvider
import io.github.magisk317.xposed.keepalive.KeepAliveHook
import io.github.magisk317.xposed.logging.XLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the live keep-alive switch snapshot.
 *
 * The switches are authored in the app process and published through a content provider,
 * so system_server has to poll them and listen for the change broadcast. The snapshot is
 * all-or-nothing: a failed refresh clears it, because keeping a stale decision live would
 * leave anti-kill or resource bypass behaviour on after the user turned it off.
 */
internal object XmsfKeepAliveFlags : KeepAliveFlagsProvider {

    private const val TAG = "MiPushKeepAliveHook"
    private const val REFRESH_INTERVAL_MS = 60_000L
    private const val LOG_INTERVAL_MS = 60_000L

    private val PREF_URI =
        Uri.parse("content://$KEEPALIVE_PREF_AUTHORITY/$KEEPALIVE_PREF_PATH_FLAGS")
    private val PREF_KEYS = arrayOf(
        KEEPALIVE_PREF_OOM_ADJ,
        KEEPALIVE_PREF_ANTI_KILL,
        KEEPALIVE_PREF_STANDBY_BYPASS,
        KEEPALIVE_PREF_DOZE_BYPASS,
    )

    private val lock = Any()
    private val generation = AtomicLong()
    private val lastLogAt = ConcurrentHashMap<String, Long>()

    @Volatile
    private var started = false
    private var snapshot = KeepAliveFlags()
    private var executor: ExecutorService? = null
    private var receiverThread: Thread? = null
    private var pollThread: Thread? = null
    private var receiverContext: Context? = null
    private var receiver: BroadcastReceiver? = null

    override fun current(): KeepAliveFlags = snapshot

    fun start() {
        if (started) return
        synchronized(lock) {
            if (started) return
            executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "MiPushKeepAlivePrefs").apply { isDaemon = true }
            }
            started = true
            val currentGeneration = generation.incrementAndGet()
            refreshAsync()
            startReceiverThread(currentGeneration)
            startPollThread(currentGeneration)
        }
    }

    fun stop() {
        val receiverToUnregister: Context?
        val receiverToDrop: BroadcastReceiver?
        val executorToStop: ExecutorService?
        val receiverThreadToStop: Thread?
        val pollThreadToStop: Thread?
        synchronized(lock) {
            generation.incrementAndGet()
            started = false
            receiverToUnregister = receiverContext
            receiverToDrop = receiver
            executorToStop = executor
            receiverThreadToStop = receiverThread
            pollThreadToStop = pollThread
            receiverContext = null
            receiver = null
            executor = null
            receiverThread = null
            pollThread = null
            snapshot = KeepAliveFlags()
        }
        receiverThreadToStop?.interrupt()
        pollThreadToStop?.interrupt()
        executorToStop?.shutdownNow()
        if (receiverToUnregister != null && receiverToDrop != null) {
            runCatching { receiverToUnregister.unregisterReceiver(receiverToDrop) }
        }
    }

    private fun isActive(expected: Long): Boolean = started && generation.get() == expected

    private fun startReceiverThread(expected: Long) {
        receiverThread = Thread({
            registerReceiverWhenReady(expected)
        }, "MiPushKeepAlivePrefReceiver").apply {
            isDaemon = true
            start()
        }
    }

    private fun startPollThread(expected: Long) {
        pollThread = Thread({
            while (isActive(expected)) {
                try {
                    Thread.sleep(REFRESH_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                if (isActive(expected)) refreshAsync()
            }
        }, "MiPushKeepAlivePrefPoll").apply {
            isDaemon = true
            start()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerReceiverWhenReady(expected: Long) {
        while (isActive(expected)) {
            val app = currentApplication()
            if (app != null) {
                val registered = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        refreshAsync()
                    }
                }
                val registeredOk = runCatching {
                    val filter = IntentFilter(ACTION_PREF_CHANGED).apply {
                        addAction(Intent.ACTION_USER_UNLOCKED)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        app.registerReceiver(
                            registered,
                            filter,
                            KEEPALIVE_PREF_READ_PERMISSION,
                            null,
                            Context.RECEIVER_EXPORTED,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        app.registerReceiver(
                            registered,
                            filter,
                            KEEPALIVE_PREF_READ_PERMISSION,
                            null,
                        )
                    }
                }.isSuccess
                if (registeredOk) {
                    val keep = synchronized(lock) {
                        if (isActive(expected)) {
                            receiverContext = app
                            receiver = registered
                            true
                        } else {
                            false
                        }
                    }
                    if (keep) {
                        refreshAsync()
                        return
                    }
                    runCatching { app.unregisterReceiver(registered) }
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

    private fun refreshAsync() {
        val current = executor ?: return
        if (current.isShutdown) return
        runCatching { current.execute(::refresh) }
    }

    private fun refresh() {
        val app = currentApplication() ?: return
        runCatching {
            val values = app.contentResolver.query(PREF_URI, null, null, PREF_KEYS, null)
                ?.use { cursor ->
                    val keyIndex = cursor.getColumnIndex(KEEPALIVE_PREF_COLUMN_KEY)
                    val enabledIndex = cursor.getColumnIndex(KEEPALIVE_PREF_COLUMN_ENABLED)
                    check(keyIndex >= 0 && enabledIndex >= 0) {
                        "keepalive preference columns missing"
                    }
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
            val previous = snapshot
            if (previous != updated) {
                snapshot = updated
                XLog.i(
                    TAG,
                    "keepalive preferences ready=true oom=${updated.oomAdj} " +
                        "antiKill=${updated.antiKill} standby=${updated.standbyBypass} " +
                        "doze=${updated.dozeBypass}",
                )
            }
        }.onFailure {
            val previous = snapshot
            snapshot = KeepAliveFlags()
            if (previous.ready) {
                XLog.w(TAG, "keepalive preferences unavailable; disabled stale runtime flags")
            }
            logRateLimited("pref_refresh", "failed to refresh keepalive prefs: ${it.message}")
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

/**
 * MiPush keep-alive hook.
 *
 * Descriptors, policy and hook bodies all come from the shared kit module. MiPush keeps
 * its provider-backed configuration refresh loop here, because that transport is project
 * specific, and ships the package-kill guard the other consumers do not use.
 */
class XmsfKeepAliveHook : KeepAliveHook(
    KeepAliveConfig(
        targetPackage = XMSF_PACKAGE_NAME,
        flagsProvider = XmsfKeepAliveFlags,
        systemPackages = setOf(ANDROID_PACKAGE_NAME),
        systemProcesses = setOf(ANDROID_PACKAGE_NAME),
        logTag = "MiPushKeepAliveHook",
    ),
) {

    override fun onLoadPackage(param: LoadParam) {
        super.onLoadPackage(param)
        if (shouldInstall(param)) XmsfKeepAliveFlags.start()
    }

    override fun onHotReloading() {
        XmsfKeepAliveFlags.stop()
        super.onHotReloading()
    }
}
