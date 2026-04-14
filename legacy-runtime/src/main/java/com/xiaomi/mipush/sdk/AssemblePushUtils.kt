package com.xiaomi.mipush.sdk

import com.xiaomi.channel.commonutils.logger.MyLog

import android.content.ComponentName
import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.reflect.JavaCalls

object AssemblePushUtils {
    const val HMS_PUSH_CLASS_NAME = "com.huawei.hms.core.service.HMSCoreService"
    const val HMS_PUSH_PACKAGE_NAME = "com.huawei.hwid"
    private var isGoogleServiceSatisfied = -1

    fun getPhoneBrand(context: Context): PhoneBrand {
        return try {
            if (context.packageManager.getServiceInfo(ComponentName(HMS_PUSH_PACKAGE_NAME, HMS_PUSH_CLASS_NAME), 128) == null || !isAvailableEMUI()) {
                PhoneBrand.OTHER
            } else {
                PhoneBrand.HUAWEI
            }
        } catch (e: Exception) {
            PhoneBrand.OTHER
        }
    }

    private fun isAvailableEMUI(): Boolean {
        return try {
            val str = JavaCalls.callStaticMethod("android.os.SystemProperties", "get", "ro.build.hw_emui_api_level", "") as String?
            if (TextUtils.isEmpty(str)) false
            else str!!.toInt() >= 9
        } catch (e: Exception) {
            MyLog.e(e)
            false
        }
    }

    fun isColorOSPushSupport(context: Context): Boolean {
        val objCallStaticMethod = JavaCalls.callStaticMethod("com.xiaomi.assemble.control.COSPushManager", "isSupportPush", context)
        var zBooleanValue = false
        if (objCallStaticMethod != null && objCallStaticMethod is Boolean) {
            zBooleanValue = objCallStaticMethod
        }
        MyLog.v("color os push  is avaliable ? :$zBooleanValue")
        return zBooleanValue
    }

    fun isFunTouchOSPushSupport(context: Context): Boolean {
        val objCallStaticMethod = JavaCalls.callStaticMethod("com.xiaomi.assemble.control.FTOSPushManager", "isSupportPush", context)
        var zBooleanValue = false
        if (objCallStaticMethod != null && objCallStaticMethod is Boolean) {
            zBooleanValue = objCallStaticMethod
        }
        MyLog.v("fun touch os push  is avaliable ? :$zBooleanValue")
        return zBooleanValue
    }

    fun isGoogleServiceSatisfied(context: Context): Boolean {
        val objCallMethod = JavaCalls.callMethod(
            JavaCalls.callStaticMethod("com.google.android.gms.common.GoogleApiAvailability", "getInstance", arrayOf<Any>()),
            "isGooglePlayServicesAvailable",
            context
        )
        val staticField = JavaCalls.getStaticField("com.google.android.gms.common.ConnectionResult", "SUCCESS")
        if (staticField == null || staticField !is Int) {
            MyLog.v("google service is not avaliable")
            isGoogleServiceSatisfied = 0
            return false
        }
        val iIntValue = staticField
        if (objCallMethod != null) {
            if (objCallMethod is Int) {
                isGoogleServiceSatisfied = if (objCallMethod == iIntValue) 1 else 0
            } else {
                isGoogleServiceSatisfied = 0
                MyLog.v("google service is not avaliable")
            }
        }
        val canUse = isGoogleServiceSatisfied > 0
        MyLog.v("is google service can be used$canUse")
        return canUse
    }
}
