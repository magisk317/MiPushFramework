package io.github.magisk317.mipush.common.utils.rom.miui

import io.github.magisk317.xposed.logging.XLog
internal object MiuiSdkManagerHelper {
    @JvmStatic
    @Throws(ClassNotFoundException::class)
    fun getSdkManagerClass(): Class<*> {
        return try {
            Class.forName("miui.core.SdkManager")
        } catch (e: ClassNotFoundException) {
            try {
                Class.forName("com.miui.internal.core.SdkManager").also {
                    XLog.tagged("miuisdk").w { "using legacy sdk" }
                }
            } catch (e2: ClassNotFoundException) {
                XLog.tagged("miuisdk").e { "no sdk found" }
                throw e2
            }
        }
    }
}
