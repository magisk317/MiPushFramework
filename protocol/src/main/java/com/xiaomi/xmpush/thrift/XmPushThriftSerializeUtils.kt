package com.xiaomi.xmpush.thrift

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.text.TextUtils
import android.util.Log
import org.apache.thrift.TBase
import org.apache.thrift.TDeserializer
import org.apache.thrift.TException
import org.apache.thrift.TSerializer
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.protocol.XmPushTBinaryProtocol

object XmPushThriftSerializeUtils {
    const val MASK_CHARGING = 4
    const val MASK_GEO_PASS = 1
    const val MASK_GEO_RECEIVE = 4
    const val MASK_GEO_SHOW = 2
    const val MASK_SCREEN_LOCKED = 8
    const val MASK_TYPE_SHIELD = 16
    
    private const val NOTIFICATION_STATUS_UNKNOWN = 0
    private const val NOTIFICATION_STATUS_ALLOWED = 1
    private const val NOTIFICATION_STATUS_BLOCKED = 2
    private const val TAG = "XmPushThriftSerializeUtils"

    @JvmStatic
    @Throws(TException::class)
    fun convertByteArrayToThriftObject(t: TBase<*, *>, bArr: ByteArray?) {
        if (bArr == null) {
            throw TException("the message byte is empty.")
        }
        TDeserializer(XmPushTBinaryProtocol.Factory(true, true, bArr.size)).deserialize(t, bArr)
    }

    @JvmStatic
    fun convertThriftObjectToBytes(t: TBase<*, *>?): ByteArray? {
        if (t == null) return null
        return try {
            TSerializer(TBinaryProtocol.Factory()).serialize(t)
        } catch (e: TException) {
            Log.e(TAG, "convertThriftObjectToBytes catch TException.", e)
            null
        }
    }

    @JvmStatic
    fun getDeviceStatus(context: Context, xmPushActionContainer: XmPushActionContainer?): Short {
        var deviceStatus = getNotificationStatus(context, xmPushActionContainer?.packageName)
        if (isCharging(context)) {
            deviceStatus += MASK_CHARGING
        }
        if (isScreenLocked(context)) {
            deviceStatus += MASK_SCREEN_LOCKED
        }
        return deviceStatus.toShort()
    }

    @JvmStatic
    fun getGeoMsgStatus(z: Boolean, z2: Boolean, z3: Boolean): Short {
        var status = 0
        if (z) status += MASK_GEO_RECEIVE
        if (z2) status += MASK_GEO_SHOW
        if (z3) status += MASK_GEO_PASS
        return status.toShort()
    }

    private fun getApplicationInfo(context: Context?, packageName: String?): ApplicationInfo? {
        if (context == null || TextUtils.isEmpty(packageName)) return null
        return try {
            if (packageName == context.packageName) {
                context.applicationInfo
            } else {
                context.packageManager.getApplicationInfo(packageName!!, 0)
            }
        } catch (th: Throwable) {
            null
        }
    }

    private fun getNotificationStatus(context: Context?, packageName: String?): Int {
        if (context == null || TextUtils.isEmpty(packageName)) return NOTIFICATION_STATUS_UNKNOWN
        
        val applicationInfo = getApplicationInfo(context, packageName) ?: return NOTIFICATION_STATUS_UNKNOWN
        
        if (packageName == context.packageName && Build.VERSION.SDK_INT >= 24) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (notificationManager != null) {
                    return if (notificationManager.areNotificationsEnabled()) NOTIFICATION_STATUS_ALLOWED else NOTIFICATION_STATUS_BLOCKED
                }
            } catch (th: Throwable) {
                Log.w(TAG, "Failed to query notifications for current package", th)
            }
        }
        
        val mode = queryNotificationMode(context, applicationInfo.uid, packageName!!) ?: return NOTIFICATION_STATUS_UNKNOWN
        val allowedMode = readStaticInt(AppOpsManager::class.java, "MODE_ALLOWED", 0)
        return if (mode == allowedMode) NOTIFICATION_STATUS_ALLOWED else NOTIFICATION_STATUS_BLOCKED
    }

    private fun isCharging(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val batteryChanged = context.registerReceiver(null, IntentFilter("android.intent.action.BATTERY_CHANGED"))
            val status = batteryChanged?.getIntExtra("status", -1) ?: -1
            status == 2 || status == 5
        } catch (th: Throwable) {
            false
        }
    }

    private fun isScreenLocked(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager != null && keyguardManager.isKeyguardLocked
        } catch (th: Throwable) {
            Log.w(TAG, "Failed to query keyguard state", th)
            false
        }
    }

    private fun queryNotificationMode(context: Context, uid: Int, packageName: String): Int? {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) ?: return null
        val opPostNotification = readStaticInt(AppOpsManager::class.java, "OP_POST_NOTIFICATION", null) ?: return null
        return try {
            val method = appOps.javaClass.getMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
            method.isAccessible = true
            val result = method.invoke(appOps, opPostNotification, uid, packageName)
            (result as? Number)?.toInt()
        } catch (th: Throwable) {
            Log.w(TAG, "Failed to query app ops notification mode", th)
            null
        }
    }

    private fun readStaticInt(type: Class<*>, fieldName: String, fallback: Int?): Int? {
        return try {
            val value = type.getField(fieldName).get(null)
            (value as? Number)?.toInt() ?: fallback
        } catch (th: Throwable) {
            fallback
        }
    }
}
