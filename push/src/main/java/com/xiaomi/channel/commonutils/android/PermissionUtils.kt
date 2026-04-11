package com.xiaomi.channel.commonutils.android

import android.content.Context

object PermissionUtils {
    const val readPhoneState = "android.permission.READ_PHONE_STATE"
    const val writeExternalStorage = "android.permission.WRITE_EXTERNAL_STORAGE"

    @JvmStatic
    fun checkSelfPermission(context: Context, permission: String): Boolean {
        return context.packageManager.checkPermission(permission, context.packageName) == 0
    }
}
