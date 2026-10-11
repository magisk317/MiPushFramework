package com.xiaomi.push.service.heartbeat

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.Settings
import android.telephony.SubscriptionManager
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ConfigKey
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Availability gate + session state machine for the modem TCP keep-alive, ported
 * from stock XMSF 7.5.79-C.
 *
 * Stock reference: com.xiaomi.xmsf 7.5.79-C (versionCode 70005079),
 * base.apk sha256 edaa580b41db1a2bc5b0cc914fb27d4dcb5a5d0603d56c352311853e6f1fdc2d,
 * JADX paths:
 * - com.xiaomi.xmsf/stock-7.5.79-C/sources/com/xiaomi/push/service/hardware/heartbeat/e.java (state holder / gate chain)
 * - .../c.java (lifecycle manager: delayed init, cloud listener, socket hooks)
 * - .../a.java (availability counters), .../d.java (stats; kept log-only here)
 * - .../za (state machine: idle → session requested → modem active; folded into
 *   this controller as an explicit session phase rather than replicating the
 *   obfuscated class-per-state hierarchy)
 *
 * Differences from stock, all deliberate:
 * - Stats upload (stock d.java OneTrack/category_hb_*) stays log-only, matching the
 *   product telemetry boundary used by the stable heartbeat port.
 * - The 22-minute boot delay init uses a handler delay instead of a wakeup alarm.
 * - The cloud switch (ConfigKey MdKaSwitch 146, stock default true) is read on every
 *   availability check instead of via a config-change listener.
 */
class MdKaController private constructor(
    private val appContext: Context,
    private val proxy: MdKaTelephonyProxy,
    private val clock: () -> Long,
) {
    private val handlerThread = HandlerThread("MdKaController")
    private val handler = Handler(handlerThread.apply { start() }.looper)

    // Socket address capture (stock e.java f16149d-f16152g).
    private val localAddress = AtomicReference<String>()
    private val localPort = AtomicInteger()
    private val remoteAddress = AtomicReference<String>()
    private val remotePort = AtomicInteger()

    // Gate inputs (stock e.java f16153h-f16157l).
    private val socketOpen = AtomicBoolean(false)
    private val fallDown = AtomicBoolean(false)
    private val superPowerMode = AtomicBoolean(readSuperPowerMode())
    private val mobileConnected = AtomicBoolean(false)

    // Circuit breaker: stock d.java fail-too-much + hwka_restriction_time.
    private val consecutiveFailures = AtomicInteger(0)
    private val failTooMuchUntil = AtomicLong(0)

    private val initialized = AtomicBoolean(false)
    private var sessionStarted = false

    fun initializeIfNeeded() {
        if (initialized.get()) return
        if (clock() < BOOT_DELAY_MS) {
            MyLog.w("[MdKa] Device elapsed time ${clock()} defer telephony probe")
            handler.postDelayed({ initializeIfNeeded() }, BOOT_DELAY_MS - clock())
            return
        }
        if (!initialized.compareAndSet(false, true)) return
        runCatching {
            proxy.initialize()
            if (proxy.isSupported()) {
                proxy.addIndicationListener { result -> onModemIndication(result) }
                onNetworkChanged(currentNetworkSnapshot())
            }
            MyLog.w("[MdKa] HwKa init success supported=${proxy.isSupported()}")
        }.onFailure {
            MyLog.e("[MdKa] exception occurred when HwKa init", it)
        }
    }

    /**
     * Stock e.h gate chain, verbatim order and reasons. Returns the reason string on
     * unavailable (test-friendly) — use [isAvailable] for the boolean.
     */
    fun availabilityReason(): String {
        return try {
            val state = MdKaGate.State(
                supported = proxy.isSupported(),
                socketOpen = socketOpen.get(),
                failTooMuch = failTooMuch(),
                fallDown = fallDown.get(),
                cloudEnabled = cloudEnabled(),
                mobileConnected = mobileConnected.get(),
                superPowerMode = superPowerMode.get(),
                dataCardPhoneId = defaultDataPhoneId(),
                featureVersionAtLeast1 = proxy.featureVersionAtLeast(1),
            )
            MdKaGate.evaluate(state).second
        } catch (error: Throwable) {
            MyLog.e("[Available] exception occurred when check keep alive availability", error)
            "exception"
        }
    }

    fun isAvailable(): Boolean = availabilityReason() == "available"

    /** Stock heartbeat interval when the modem session carries the keep-alive: db.i.e = 3_540_000ms. */
    fun modemPingIntervalMs(): Long = MODEM_PING_INTERVAL_MS

    // ---- socket lifecycle hooks (stock c.h / c.g / c.i / c.j / c.k / c.l) ----

    fun onSocketConnected(socket: Socket?) {
        if (socket == null) {
            MyLog.w("[MdKa] socket is null when socket connected")
            return
        }
        val local = runCatching { socket.localSocketAddress as? InetSocketAddress }.getOrNull()
        val remote = runCatching { socket.remoteSocketAddress as? InetSocketAddress }.getOrNull()
        val localIp = local?.address?.hostAddress
        val remoteIp = remote?.address?.hostAddress
        if (localIp.isNullOrEmpty() || remoteIp.isNullOrEmpty()) {
            MyLog.w("[MdKa] local address or inet address is null when socket connected")
            return
        }
        socketOpen.set(true)
        localAddress.set(localIp)
        localPort.set(local.port)
        remoteAddress.set(remoteIp)
        remotePort.set(remote.port)
        MyLog.w(
            "[MdKa] update address info, source=$localIp:${local.port}, target=$remoteIp:${remote.port}",
        )
        handler.post {
            runCatching {
                startSessionIfAvailable()
            }.onFailure { MyLog.e("[MdKa] exception when socket connected", it) }
        }
    }

    fun onSocketClosed(reason: Int, error: Exception?) {
        socketOpen.set(false)
        MyLog.w("[MdKa] socket is closed.reason:$reason exception:${error?.message}")
        handler.post {
            runCatching {
                stopSession("socket closed")
            }.onFailure { MyLog.e("[MdKa] exception when socket closed", it) }
        }
    }

    fun onSocketSend() {
        handler.post { runCatching { sessionActivity("send") } }
    }

    fun onSocketReceive() {
        handler.post { runCatching { sessionActivity("receive") } }
    }

    fun onPingStart() {
        handler.post { runCatching { sessionActivity("ping start") } }
    }

    fun onPingStop() {
        handler.post { runCatching { sessionActivity("ping stop") } }
    }

    // ---- environment hooks (stock c.e / c.m / c.f) ----

    fun onSuperPowerModeChanged(enabled: Boolean) {
        MyLog.w("[MdKa] super power mode is changed. enable:$enabled")
        superPowerMode.set(enabled)
    }

    fun setFallDown(enabled: Boolean) {
        MyLog.w("[MdKa] update fall down state: $enabled")
        fallDown.set(enabled)
    }

    fun onNetworkChanged(snapshot: HeartbeatNetworkSnapshot?) {
        try {
            val phoneId = defaultDataPhoneId()
            // Stock e.o: data card 0 is always eligible; data card 1 needs feature version >= 1.
            mobileConnected.set(
                snapshot != null && snapshot.type == HeartbeatNetworkSnapshot.TYPE_MOBILE,
            )
            MyLog.w("[MdKa] network is change. isMobileNetworkConnected:${mobileConnected.get()}")
            handler.post { runCatching { restartSessionIfAvailable() } }
        } catch (error: Throwable) {
            MyLog.e("[MdKa] exception occurred when network changed", error)
        }
    }

    // ---- modem indication (stock c$b callback) ----

    private fun onModemIndication(result: String) {
        MyLog.w("[MdKa] [Modem Callback] onTcpKaInd, result=$result")
        if (result == MdKaTelephonyProxy.RESULT_OK) {
            consecutiveFailures.set(0)
        } else {
            val failures = consecutiveFailures.incrementAndGet()
            MyLog.w("[MdKa] modem keep alive callback error, failures=$failures result=$result")
            if (failures >= FAIL_THRESHOLD) {
                failTooMuchUntil.set(clock() + FAILURE_RESTRICTION_MS)
                consecutiveFailures.set(0)
                handler.post { runCatching { stopSession("fail too much") } }
            }
        }
    }

    // ---- internals ----

    private fun cloudEnabled(): Boolean =
        OnlineConfig.getInstance(appContext).getBooleanValue(ConfigKey.MdKaSwitch.value, true)

    private fun failTooMuch(): Boolean = clock() < failTooMuchUntil.get()

    private fun startSessionIfAvailable() {
        if (!isAvailable()) {
            MyLog.w("[MdKa] session not started, unavailable: ${availabilityReason()}")
            return
        }
        val result = proxy.startTcpKeepAlive(
            localAddress.get() ?: return,
            localPort.get(),
            remoteAddress.get() ?: return,
            remotePort.get(),
            MODEM_KEEPALIVE_INTERVAL_SECONDS,
        )
        if (result < 0) {
            MyLog.w("[MdKa] start modem keep alive failed, code=$result")
            return
        }
        proxy.startTcpMonitorRegister(
            localAddress.get() ?: return,
            localPort.get(),
            remoteAddress.get() ?: return,
            remotePort.get(),
        )
        sessionStarted = true
        MyLog.w("[MdKa] modem keep alive session started, id=$result")
    }

    private fun restartSessionIfAvailable() {
        if (!sessionStarted) return
        // Stock re-issues the session on network/data-card changes; stopping first is the
        // stock za.c exit path, then the next socket/ping event re-requests it.
        proxy.stopTcpKeepAlive()
        proxy.stopTcpMonitorRegister()
        sessionStarted = false
        startSessionIfAvailable()
    }

    private fun stopSession(reason: String) {
        if (!sessionStarted) return
        proxy.stopTcpKeepAlive()
        proxy.stopTcpMonitorRegister()
        sessionStarted = false
        MyLog.w("[MdKa] modem keep alive session stopped, reason=$reason")
    }

    private fun sessionActivity(source: String) {
        // Stock za.c uses send/receive/ping activity to confirm the socket is live; the
        // modem session itself needs no per-activity work. Kept as a log checkpoint.
        if (sessionStarted) {
            MyLog.w("[MdKa] session activity: $source")
        }
    }

    private fun currentNetworkSnapshot(): HeartbeatNetworkSnapshot? {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return null
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null
        val type = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                HeartbeatNetworkSnapshot.TYPE_MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                HeartbeatNetworkSnapshot.TYPE_WIFI
            else -> return null
        }
        return HeartbeatNetworkSnapshot(type)
    }

    /** Stock w5.d.g: phoneId of the default data subscription (0 first SIM, 1 second, -1 none). */
    private fun defaultDataPhoneId(): Int = try {
        val subId = SubscriptionManager.getDefaultDataSubscriptionId()
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) -1
        // getPhoneId is hidden in the public SDK; stock reaches it through reflection too.
        else
            JavaCalls.callStaticMethod(
                "android.telephony.SubscriptionManager",
                "getPhoneId",
                subId,
            ) as? Int ?: -1
    } catch (error: Throwable) {
        MyLog.e("[MdKa] exception occurred when get phone card number", error)
        -1
    }

    private fun readSuperPowerMode(): Boolean {
        return try {
            Settings.System.getInt(appContext.contentResolver, "power_supersave_mode_open", 0) == 1
        } catch (error: Throwable) {
            MyLog.e("[MdKa] exception occurred when check super power mode switch", error)
            false
        }
    }

    companion object {
        /** Stock db.i.e: 59-minute modem keep-alive interval. */
        const val MODEM_PING_INTERVAL_MS = 3_540_000L

        /** Stock proxy call passes the interval in seconds to startTcpKeepAlive. */
        const val MODEM_KEEPALIVE_INTERVAL_SECONDS = 3_540

        /** Stock c: delay the telephony probe until 22 minutes after boot. */
        const val BOOT_DELAY_MS = 1_320_000L

        /** Stock d.java fail-too-much circuit breaker (restriction time kept 30 min). */
        const val FAIL_THRESHOLD = 3
        const val FAILURE_RESTRICTION_MS = 1_800_000L

        @Volatile
        private var instance: MdKaController? = null

        @JvmStatic
        fun get(appContext: Context): MdKaController {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: MdKaController(
                    appContext.applicationContext,
                    MdKaTelephonyProxy.get(appContext.applicationContext),
                    clock = { SystemClock.elapsedRealtime() },
                ).also { instance = it }
            }
        }

        /** Test-only reset. */
        @JvmStatic
        fun resetForTests() {
            synchronized(this) { instance = null }
        }
    }
}

/** Pure availability gate (stock 7.5.79-C e.h) — extracted for JVM tests. */
object MdKaGate {
    data class State(
        val supported: Boolean = true,
        val socketOpen: Boolean = true,
        val failTooMuch: Boolean = false,
        val fallDown: Boolean = false,
        val cloudEnabled: Boolean = true,
        val mobileConnected: Boolean = true,
        val superPowerMode: Boolean = false,
        val dataCardPhoneId: Int = 0,
        val featureVersionAtLeast1: Boolean = false,
    )

    /** Returns (available, reason); the reason mirrors the stock log wording. */
    fun evaluate(state: State): Pair<Boolean, String> {
        if (!state.supported) return false to "not supported"
        if (!state.socketOpen) return false to "socket is closed"
        if (state.failTooMuch) return false to "fail too much"
        if (state.fallDown) return false to "fall down"
        if (!state.cloudEnabled) return false to "cloud config is not enable"
        if (!state.mobileConnected) return false to "mobile network is disconnected"
        if (state.superPowerMode) return false to "super power mode enable"
        val dataCardOk = state.dataCardPhoneId == 0 ||
            (state.dataCardPhoneId == 1 && state.featureVersionAtLeast1)
        if (!dataCardOk) return false to "data card not supported"
        return true to "available"
    }
}
