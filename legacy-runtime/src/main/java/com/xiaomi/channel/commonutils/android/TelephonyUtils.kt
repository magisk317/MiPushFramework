package com.xiaomi.channel.commonutils.android

import android.content.Context
import android.telephony.TelephonyManager

object TelephonyUtils {
    @JvmStatic
    fun isChinaMobile(context: Context): Boolean {
        val simOperator = (context.getSystemService("phone") as TelephonyManager).simOperator
        return "46000" == simOperator || "46002" == simOperator || "46007" == simOperator
    }

    @JvmStatic
    fun isChinaTelecom(context: Context): Boolean {
        return "46003" == (context.getSystemService("phone") as TelephonyManager).simOperator
    }

    @JvmStatic
    fun isChinaUnicom(context: Context): Boolean {
        return "46001" == (context.getSystemService("phone") as TelephonyManager).simOperator
    }
}
