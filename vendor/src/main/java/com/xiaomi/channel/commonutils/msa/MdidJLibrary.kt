package com.xiaomi.channel.commonutils.msa

import android.content.Context
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.logger.MyLog

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/msa/MdidJLibrary.java
 */
object MdidJLibrary {
    private const val CORE_CLASS_JLIBRARY = "com.bun.miitmdid.core.JLibrary"
    @Volatile
    private var sJLibraryInited = false

    private fun callInitEntry(cls: Class<*>, context: Context) {
        if (sJLibraryInited) {
            return
        }
        try {
            sJLibraryInited = true
            cls.getDeclaredMethod("InitEntry", Context::class.java).invoke(cls, context)
        } catch (throwable: Throwable) {
            MyLog.w("mdid:load lib error $throwable")
        }
    }

    @JvmStatic
    fun checkAndLoadMdidSdk(context: Context): Boolean {
        return try {
            val cls = SystemUtils.loadClass(context, CORE_CLASS_JLIBRARY)
            callInitEntry(cls, context)
            true
        } catch (throwable: Throwable) {
            MyLog.w("mdid:check error $throwable")
            false
        }
    }
}
