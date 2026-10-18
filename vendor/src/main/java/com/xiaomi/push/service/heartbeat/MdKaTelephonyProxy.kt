package com.xiaomi.push.service.heartbeat

import android.content.Context
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Reflection proxy over `miui.telephony.TelephonyManagerEx` for the modem TCP
 * keep-alive offload, ported from stock XMSF 7.5.79-C.
 *
 * Stock reference: com.xiaomi.xmsf 7.5.79-C (versionCode 70005079),
 * base.apk sha256 edaa580b41db1a2bc5b0cc914fb27d4dcb5a5d0603d56c352311853e6f1fdc2d,
 * JADX path: com.xiaomi.xmsf/stock-7.5.79-C/sources/com/xiaomi/push/service/hardware/heartbeat/TelephonyManagerExProxy.java
 *
 * Stock compiles `android.telephony.tcpka.IModemTcpKeepAliveIndCallback` against the
 * MIUI framework. The public SDK has no such class, so the callback is registered as a
 * hand-written [Binder] speaking the same descriptor and transaction layout
 * (transaction 1 = onTcpKaInd(String)); the framework side only ever treats it as a
 * remote callback binder, so a matching onTransact is sufficient.
 *
 * Error codes mirror stock: -1 not supported, -2 illegal address/port, -3 unexpected
 * return type, -4 reflection exception, -5 proxy not initialized.
 */
class MdKaTelephonyProxy private constructor(private val appContext: Context) {

    fun interface IndicationListener {
        fun onTcpKaInd(result: String)
    }

    private var telephonyManagerEx: Any? = null
    private var supported = false
    private val featureVersion = AtomicInteger(-1)
    private val listeners = CopyOnWriteArraySet<IndicationListener>()

    /** Guard mirrored from stock TelephonyManagerExProxy.d: only the XMSF package probes. */
    private val isXmsfPackage: Boolean = MIUIUtils.isXMSF(appContext)

    fun initialize() {
        try {
            if (!isXmsfPackage) {
                MyLog.w("[Proxy] NO XMSF NO Support")
                return
            }
            if (TextUtils.equals(Build.PRODUCT, "xuanyuan")) {
                return
            }
            val ex = JavaCalls.callStaticMethod(
                "miui.telephony.TelephonyManagerEx",
                "getDefault",
            )
            telephonyManagerEx = ex
            if (ex == null) {
                MyLog.w("[Proxy] TelephonyManagerEx does not exist")
                return
            }
            val supportedResult = JavaCalls.callMethod(ex, "isModemTcpKaSupported")
            if (supportedResult is Boolean) {
                supported = supportedResult
            }
            MyLog.w("[Proxy] hardware heartbeat supportability:$supported")
            if (supported) {
                JavaCalls.callMethod(
                    ex,
                    "registerModemTcpKaCallback",
                    IndicationCallbackBinder(),
                )
            }
            val version = JavaCalls.callMethod(ex, "getTcpKeepAliveFeatureSupportVersion")
            if (version is Int) {
                featureVersion.set(version)
            }
        } catch (error: Throwable) {
            MyLog.e("[Proxy] exception occurred when proxy TME init, exception: ", error)
        }
    }

    fun isSupported(): Boolean = supported

    fun featureVersionAtLeast(version: Int): Boolean = featureVersion.get() >= version

    fun addIndicationListener(listener: IndicationListener) {
        listeners.add(listener)
    }

    fun startTcpKeepAlive(
        localIp: String,
        localPort: Int,
        remoteIp: String,
        remotePort: Int,
        intervalSeconds: Int,
    ): Int {
        return try {
            if (!supported) {
                MyLog.w("[Proxy] not support modem keep alive when start tcp keep alive")
                return ERROR_NOT_SUPPORTED
            }
            if (TextUtils.isEmpty(localIp) || TextUtils.isEmpty(remoteIp) ||
                localPort < 0 || localPort > 65535 || remotePort < 0 || remotePort > 65535
            ) {
                MyLog.w("[Proxy] illegal ip address/port when start tcp keep alive")
                return ERROR_ILLEGAL_ADDRESS
            }
            val ex = telephonyManagerEx
            if (ex == null) {
                MyLog.w("[Proxy] TelephonyManagerEx does not exist when start tcp keep alive")
                return ERROR_NO_PROXY
            }
            val result = JavaCalls.callMethod(
                ex,
                "startTcpKeepAlive",
                localIp,
                localPort,
                remoteIp,
                remotePort,
                intervalSeconds,
                1,
            )
            if (result is Int) result else ERROR_UNEXPECTED_RETURN
        } catch (error: Throwable) {
            MyLog.e("[Proxy] exception occurred in starting tcp keep alive, exception: ", error)
            ERROR_REFLECTION
        }
    }

    fun startTcpMonitorRegister(
        localIp: String,
        localPort: Int,
        remoteIp: String,
        remotePort: Int,
    ): Int {
        return try {
            if (!supported) {
                MyLog.w("[Proxy] not support modem keep alive when start tcp monitor register")
                return ERROR_NOT_SUPPORTED
            }
            if (TextUtils.isEmpty(localIp) || TextUtils.isEmpty(remoteIp) ||
                localPort < 0 || localPort > 65535 || remotePort < 0 || remotePort > 65535
            ) {
                MyLog.w("[Proxy] illegal ip address/port when start tcp monitor register")
                return ERROR_ILLEGAL_ADDRESS
            }
            val ex = telephonyManagerEx
            if (ex == null) {
                MyLog.w("[Proxy] TelephonyManagerEx does not exist when start tcp monitor register")
                return ERROR_NO_PROXY
            }
            val result = JavaCalls.callMethod(
                ex,
                "startTcpMonitorRegister",
                localIp,
                localPort,
                remoteIp,
                remotePort,
                1,
            )
            if (result is Int) result else ERROR_UNEXPECTED_RETURN
        } catch (error: Throwable) {
            MyLog.e("[Proxy] exception occurred in starting tcp monitor register, exception: ", error)
            ERROR_REFLECTION
        }
    }

    fun stopTcpKeepAlive() {
        try {
            if (!supported) {
                MyLog.w("[Proxy] not support modem keep alive when stop tcp keep alive")
                return
            }
            val ex = telephonyManagerEx ?: run {
                MyLog.w("[Proxy] TelephonyManagerEx does not exist when stop tcp keep alive")
                return
            }
            JavaCalls.callMethod(ex, "stopTcpKeepAlive", 1)
        } catch (error: Throwable) {
            MyLog.e("[Proxy] exception occurred in stopping tcp keep alive, exception: ", error)
        }
    }

    fun stopTcpMonitorRegister() {
        try {
            if (!supported) {
                MyLog.w("[Proxy] not support modem keep alive when stop tcp monitor register")
                return
            }
            val ex = telephonyManagerEx ?: run {
                MyLog.w("[Proxy] TelephonyManagerEx does not exist when stop tcp monitor register")
                return
            }
            JavaCalls.callMethod(ex, "stopTcpMonitorRegister", 1)
        } catch (error: Throwable) {
            MyLog.e("[Proxy] exception occurred in stopping tcp monitor register, exception: ", error)
        }
    }

    private fun dispatchIndication(result: String) {
        listeners.forEach { listener ->
            runCatching { listener.onTcpKaInd(result) }
        }
    }

    /**
     * Hand-written stand-in for `android.telephony.tcpka.IModemTcpKeepAliveIndCallback.Stub`.
     * The AIDL interface carries a single `onTcpKaInd(String)` method, so transaction
     * [Binder.FIRST_CALL_TRANSACTION] decodes one String and replies with no exception.
     */
    private inner class IndicationCallbackBinder : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != Binder.FIRST_CALL_TRANSACTION) {
                return super.onTransact(code, data, reply, flags)
            }
            data.enforceInterface(CALLBACK_DESCRIPTOR)
            dispatchIndication(data.readString() ?: "")
            reply?.writeNoException()
            return true
        }
    }

    companion object {
        const val RESULT_OK = "KEEP_ALIVE_RESULT_OK"
        const val RESULT_FAIL = "KEEP_ALIVE_RESULT_FAIL"
        const val RESULT_ERROR = "KEEP_ALIVE_RESULT_ERROR"
        const val RESULT_NETWORK_DOWN_ERROR = "KEEP_ALIVE_NETWORK_DOWN_ERROR"

        const val ERROR_NOT_SUPPORTED = -1
        const val ERROR_ILLEGAL_ADDRESS = -2
        const val ERROR_UNEXPECTED_RETURN = -3
        const val ERROR_REFLECTION = -4
        const val ERROR_NO_PROXY = -5

        private const val CALLBACK_DESCRIPTOR = "android.telephony.tcpka.IModemTcpKeepAliveIndCallback"

        @Volatile
        private var instance: MdKaTelephonyProxy? = null

        @JvmStatic
        fun get(appContext: Context): MdKaTelephonyProxy {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: MdKaTelephonyProxy(appContext.applicationContext).also { instance = it }
            }
        }
    }
}
