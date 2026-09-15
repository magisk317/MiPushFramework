package com.xiaomi.channel.commonutils.msa

import android.content.Context
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.logger.MyLog

/*
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
