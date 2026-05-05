package com.xiaomi.channel.commonutils.msa

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.math.abs

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/msa/MdidIdManager.java
 */
class MdidIdManager(context: Context) : IdManager, InvocationHandler {
    private val mContext: Context = context.applicationContext
    private var mClassMdid: Class<*>? = null
    private var mClassIIdentifierListener: Class<*>? = null
    private var mMethodInitSdk: Method? = null
    private var mMethodGetUDID: Method? = null
    private var mMethodGetOAID: Method? = null
    private var mMethodGetVAID: Method? = null
    private var mMethodGetAAID: Method? = null
    private var mMethodIsSupported: Method? = null
    private var mMethodShutDown: Method? = null
    private val mLockObj = Object()
    @Volatile
    private var mRetryCount = 0
    @Volatile
    private var mGettingOrGotTime = 0L
    @Volatile
    private var mIdData: IdData? = null

    private inner class IdData {
        var aaid: String? = null
        var isSupport: Boolean? = null
        var oaid: String? = null
        var udid: String? = null
        var vaid: String? = null

        fun reviseSelf(): Boolean {
            if (
                !TextUtils.isEmpty(udid) ||
                !TextUtils.isEmpty(oaid) ||
                !TextUtils.isEmpty(vaid) ||
                !TextUtils.isEmpty(aaid)
            ) {
                isSupport = true
            }
            return isSupport != null
        }
    }

    init {
        initClass(context)
        callInitSdk(context)
    }

    private fun callInitSdk(context: Context) {
        val elapsedRealtime = SystemClock.elapsedRealtime()
        var result = -elapsedRealtime
        val listenerClass = mClassIIdentifierListener
        if (listenerClass != null) {
            try {
                var classLoader = listenerClass.classLoader
                if (classLoader == null) {
                    classLoader = context.classLoader
                }
                invokeMethod(
                    mMethodInitSdk,
                    mClassMdid?.getDeclaredConstructor()?.newInstance(),
                    context,
                    Proxy.newProxyInstance(classLoader, arrayOf(listenerClass), this)
                )
                result = elapsedRealtime
            } catch (throwable: Throwable) {
                outLog("call init sdk error:$throwable")
            }
        }
        mGettingOrGotTime = result
    }

    private fun cancelWait() {
        synchronized(mLockObj) {
            try {
                mLockObj.notifyAll()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun initClass(context: Context) {
        val mdidClass = loadClass(context, CORE_CLASS_MDID)
        var listenerClass: Class<*>? = null
        var supplierClass: Class<*>? = null
        for (i in LISTENER_SUPPLIER_CLASS_ARRAY.indices) {
            val classes = LISTENER_SUPPLIER_CLASS_ARRAY[i]
            listenerClass = loadClass(context, classes[0])
            supplierClass = loadClass(context, classes[1])
            if (listenerClass != null && supplierClass != null) {
                outLog("found class in index $i")
                break
            }
        }
        mClassMdid = mdidClass
        mMethodInitSdk = getMethod(mdidClass, "InitSdk", Context::class.java, listenerClass)
        mClassIIdentifierListener = listenerClass
        mMethodGetUDID = getMethod(supplierClass, "getUDID")
        mMethodGetOAID = getMethod(supplierClass, "getOAID")
        mMethodGetVAID = getMethod(supplierClass, "getVAID")
        mMethodGetAAID = getMethod(supplierClass, "getAAID")
        mMethodIsSupported = getMethod(supplierClass, "isSupported")
        mMethodShutDown = getMethod(supplierClass, "shutDown")
    }

    private fun waitAndGettingIfNeed(str: String) {
        if (mIdData != null) {
            return
        }
        val start = mGettingOrGotTime
        var effectiveStart = start
        var elapsed = SystemClock.elapsedRealtime() - abs(start)
        val retryCount = mRetryCount
        if (elapsed > TIME_WAIT_LOCK && retryCount < MAX_RETRY_COUNT) {
            synchronized(mLockObj) {
                if (mGettingOrGotTime == start && mRetryCount == retryCount) {
                    outLog("retry, current count is $retryCount")
                    mRetryCount += 1
                    callInitSdk(mContext)
                    effectiveStart = mGettingOrGotTime
                    elapsed = SystemClock.elapsedRealtime() - abs(effectiveStart)
                }
            }
        }
        if (
            mIdData != null ||
            effectiveStart < 0 ||
            elapsed > TIME_WAIT_LOCK ||
            Looper.myLooper() == Looper.getMainLooper()
        ) {
            return
        }
        synchronized(mLockObj) {
            if (mIdData == null) {
                try {
                    outLog("$str wait...")
                    mLockObj.wait(TIME_WAIT_LOCK.toLong())
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    override fun getAAID(): String? {
        waitAndGettingIfNeed("getAAID")
        return mIdData?.aaid
    }

    override fun getOAID(): String? {
        waitAndGettingIfNeed("getOAID")
        return mIdData?.oaid
    }

    override fun getUDID(): String? {
        waitAndGettingIfNeed("getUDID")
        return mIdData?.udid
    }

    override fun getVAID(): String? {
        waitAndGettingIfNeed("getVAID")
        return mIdData?.vaid
    }

    override fun invoke(proxy: Any?, method: Method?, args: Array<Any?>?): Any? {
        mGettingOrGotTime = SystemClock.elapsedRealtime()
        if (args != null) {
            val idData = IdData()
            for (arg in args) {
                if (arg != null && !isPrimitive(arg)) {
                    idData.udid = invokeMethod(mMethodGetUDID, arg) as? String
                    idData.oaid = invokeMethod(mMethodGetOAID, arg) as? String
                    idData.vaid = invokeMethod(mMethodGetVAID, arg) as? String
                    idData.aaid = invokeMethod(mMethodGetAAID, arg) as? String
                    idData.isSupport = invokeMethod(mMethodIsSupported, arg) as? Boolean
                    invokeMethod(mMethodShutDown, arg)
                    if (idData.reviseSelf()) {
                        outLog("has get succ, check duplicate:${mIdData != null}")
                        synchronized(MdidIdManager::class.java) {
                            if (mIdData == null) {
                                mIdData = idData
                            }
                        }
                    }
                }
            }
        }
        cancelWait()
        return null
    }

    override fun isAllowOAID(): Boolean = true

    override fun isSupported(): Boolean {
        waitAndGettingIfNeed("isSupported")
        return mIdData != null && java.lang.Boolean.TRUE == mIdData?.isSupport
    }

    companion object {
        private const val CORE_CLASS_MDID = "com.bun.miitmdid.core.MdidSdk"
        private val LISTENER_SUPPLIER_CLASS_ARRAY = arrayOf(
            arrayOf("com.bun.supplier.IIdentifierListener", "com.bun.supplier.IdSupplier"),
            arrayOf("com.bun.miitmdid.core.IIdentifierListener", "com.bun.miitmdid.supplier.IdSupplier")
        )
        private const val LOG_PREFIX = "mdid:"
        private const val MAX_RETRY_COUNT = 3
        private const val TIME_WAIT_LOCK = 3000L

        private fun getMethod(cls: Class<*>?, name: String, vararg parameterTypes: Class<*>?): Method? {
            if (cls == null || parameterTypes.any { it == null }) {
                return null
            }
            return try {
                @Suppress("UNCHECKED_CAST")
                cls.getMethod(name, *(parameterTypes as Array<Class<*>>))
            } catch (throwable: Throwable) {
                null
            }
        }

        private fun invokeMethod(method: Method?, obj: Any?, vararg args: Any?): Any? {
            if (method == null) {
                return null
            }
            return try {
                method.invoke(obj, *args)
            } catch (throwable: Throwable) {
                null
            }
        }

        private fun isPrimitive(obj: Any): Boolean {
            return obj is Boolean ||
                obj is Char ||
                obj is Byte ||
                obj is Short ||
                obj is Int ||
                obj is Long ||
                obj is Float ||
                obj is Double
        }

        private fun loadClass(context: Context, str: String): Class<*>? {
            return try {
                SystemUtils.loadClass(context, str)
            } catch (throwable: Throwable) {
                null
            }
        }

        private fun outLog(str: String) {
            MyLog.w(LOG_PREFIX + str)
        }
    }
}
