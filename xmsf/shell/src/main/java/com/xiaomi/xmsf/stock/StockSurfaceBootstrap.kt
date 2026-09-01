package com.xiaomi.xmsf.stock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmsf.push.service.notificationcollection.NotificationListener
import com.xiaomi.xmsf.services.ISubProcBridge
import com.xiaomi.xmsf.services.ServiceBoxService

object StockSurfaceBootstrap {
    private const val ONLINE_CONFIG_CALLBACK_ID = 105
    private const val BIND_TIMEOUT_MS = 15_000L
    private const val MAX_RETRY_DELAY_MS = 300_000L
    private const val FAST_RETRY_LIMIT = 8

    private val lock = Any()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var appContext: Context? = null
    private var serviceBoxBridge: ISubProcBridge? = null
    private var bindRegistered = false
    private var failureCount = 0
    private var onlineConfigCallbackInstalled = false

    private val retryRunnable: Runnable = Runnable { bindServiceBox() }
    private val bindTimeoutRunnable: Runnable = Runnable {
        val context = synchronized(lock) {
            if (serviceBoxBridge != null || !bindRegistered) return@Runnable
            bindRegistered = false
            appContext
        } ?: return@Runnable
        runCatching { context.unbindService(serviceConnection) }
        scheduleRetry()
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val bridge = ISubProcBridge.Stub.asInterface(service)
            if (bridge == null) {
                releaseDeadBindingAndRetry()
                return
            }
            synchronized(lock) {
                serviceBoxBridge = bridge
                failureCount = 0
            }
            handler.removeCallbacks(bindTimeoutRunnable)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // The binding remains registered; Android reconnects it when the subprocess returns.
            synchronized(lock) { serviceBoxBridge = null }
        }

        override fun onBindingDied(name: ComponentName?) {
            releaseDeadBindingAndRetry()
        }

        override fun onNullBinding(name: ComponentName?) {
            releaseDeadBindingAndRetry()
        }
    }

    @JvmStatic
    fun bootstrap(context: Context) {
        val applicationContext = context.applicationContext
        NotificationListener.ensureStarted(applicationContext)
        synchronized(lock) { appContext = applicationContext }
        installOnlineConfigCallback(applicationContext)
        bindServiceBox()
    }

    private fun installOnlineConfigCallback(context: Context) {
        val shouldInstall = synchronized(lock) {
            if (onlineConfigCallbackInstalled) false else {
                onlineConfigCallbackInstalled = true
                true
            }
        }
        if (!shouldInstall) return
        OnlineConfig.getInstance(context).addOCUpdateCallbacks(
            object : OnlineConfig.OCUpdateCallback(ONLINE_CONFIG_CALLBACK_ID, "service box") {
                override fun onCallback() {
                    val bridge = synchronized(lock) { serviceBoxBridge }
                    runCatching { bridge?.notifyOnlineConfigChanged() }
                }
            },
        )
    }

    private fun bindServiceBox() {
        val context = synchronized(lock) {
            if (bindRegistered) return
            bindRegistered = true
            appContext
        } ?: run {
            synchronized(lock) { bindRegistered = false }
            return
        }
        handler.removeCallbacks(retryRunnable)
        val accepted = runCatching {
            // Stock XMSF 7.5.29-C keeps the main-to-:services ISubProcBridge route but removes
            // MainProcBridgeService. ServiceBoxService reads OnlineConfig locally and refreshes
            // KeepAliveRuntimeAdapter when callback 105 arrives.
            context.bindService(
                Intent(context, ServiceBoxService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
        }.getOrDefault(false)
        if (!accepted) {
            synchronized(lock) { bindRegistered = false }
            scheduleRetry()
            return
        }
        handler.removeCallbacks(bindTimeoutRunnable)
        handler.postDelayed(bindTimeoutRunnable, BIND_TIMEOUT_MS)
    }

    private fun releaseDeadBindingAndRetry() {
        val context = synchronized(lock) {
            serviceBoxBridge = null
            if (!bindRegistered) return
            bindRegistered = false
            appContext
        }
        handler.removeCallbacks(bindTimeoutRunnable)
        if (context != null) runCatching { context.unbindService(serviceConnection) }
        scheduleRetry()
    }

    private fun scheduleRetry() {
        val delay = synchronized(lock) {
            failureCount += 1
            retryDelayMs(failureCount)
        }
        handler.removeCallbacks(retryRunnable)
        handler.postDelayed(retryRunnable, delay)
    }

    /** Stock dc.e uses two seconds per failure through attempt eight, then caps at five minutes. */
    internal fun retryDelayMs(failures: Int): Long = when {
        failures <= 0 -> 0L
        failures <= FAST_RETRY_LIMIT -> failures * 2_000L
        else -> MAX_RETRY_DELAY_MS
    }
}
