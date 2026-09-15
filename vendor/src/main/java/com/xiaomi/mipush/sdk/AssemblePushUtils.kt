package com.xiaomi.mipush.sdk

import com.xiaomi.channel.commonutils.logger.MyLog

import android.content.Context
import com.xiaomi.channel.commonutils.android.SystemProperties
import com.xiaomi.channel.commonutils.reflect.JavaCalls

/*
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
object AssemblePushUtils {
    const val HMS_PUSH_CLASS_NAME = "com.huawei.hms.core.service.HMSCoreService"
    const val HMS_PUSH_PACKAGE_NAME = "com.huawei.hwid"
    private var isGoogleServiceSatisfied = -1

    private inline fun <T> callOptionalProbe(block: () -> T): T? {
        return try {
            block()
        } catch (_: NoSuchMethodException) {
            null
        } catch (_: ClassNotFoundException) {
            null
        }
    }

    private inline fun <T> callProbeOrFailSafe(name: String, block: () -> T): T? {
        return try {
            callOptionalProbe(block)
        } catch (e: Exception) {
            MyLog.e("$name failed", e)
            null
        }
    }

    fun getPhoneBrand(context: Context): PhoneBrand {
        return PhoneBrand.OTHER
    }

    private fun isAvailableEMUI(): Boolean {
        val str = SystemProperties.get("ro.build.hw_emui_api_level", "")
        val emuiLevel = str.toIntOrNull() ?: 0
        return emuiLevel >= 9
    }

    fun isColorOSPushSupport(context: Context): Boolean {
        val objCallStaticMethod = callProbeOrFailSafe("ColorOS push probe") {
            JavaCalls.callStaticMethodOrThrow("com.xiaomi.assemble.control.COSPushManager", "isSupportPush", context)
        }
        var zBooleanValue = false
        if (objCallStaticMethod is Boolean) {
            zBooleanValue = objCallStaticMethod
        }
        MyLog.v("color os push  is avaliable ? :$zBooleanValue")
        return zBooleanValue
    }

    fun isFunTouchOSPushSupport(context: Context): Boolean {
        val objCallStaticMethod = callProbeOrFailSafe("FuntouchOS push probe") {
            JavaCalls.callStaticMethodOrThrow("com.xiaomi.assemble.control.FTOSPushManager", "isSupportPush", context)
        }
        var zBooleanValue = false
        if (objCallStaticMethod is Boolean) {
            zBooleanValue = objCallStaticMethod
        }
        MyLog.v("fun touch os push  is avaliable ? :$zBooleanValue")
        return zBooleanValue
    }

    fun isGoogleServiceSatisfied(context: Context): Boolean {
        val objCallMethod = callProbeOrFailSafe("Google Play services probe") {
            val availability = JavaCalls.callStaticMethodOrThrow(
                "com.google.android.gms.common.GoogleApiAvailability",
                "getInstance",
            )
            JavaCalls.callMethodOrThrow(
                availability,
                "isGooglePlayServicesAvailable",
                context,
            )
        }
        val staticField = try {
            Class.forName("com.google.android.gms.common.ConnectionResult")
                .getField("SUCCESS")
                .getInt(null)
        } catch (_: ClassNotFoundException) {
            null
        } catch (_: NoSuchFieldException) {
            null
        } catch (e: Exception) {
            MyLog.e("Google Play services result lookup failed", e)
            null
        }
        if (staticField == null) {
            MyLog.v("google service is not avaliable")
            isGoogleServiceSatisfied = 0
            return false
        }
        val iIntValue = staticField
        if (objCallMethod is Int) {
            isGoogleServiceSatisfied = if (objCallMethod == iIntValue) 1 else 0
        } else {
            isGoogleServiceSatisfied = 0
            MyLog.v("google service is not avaliable")
        }
        val canUse = isGoogleServiceSatisfied > 0
        MyLog.v("is google service can be used$canUse")
        return canUse
    }
}
