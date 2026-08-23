package io.github.magisk317.mipush.common.utils.rom.miui

import co.touchlab.kermit.Logger
internal object MiuiSdkManagerHelper {
    @JvmStatic
    @Throws(ClassNotFoundException::class)
    fun getSdkManagerClass(): Class<*> {
        return try {
            Class.forName("miui.core.SdkManager")
        } catch (e: ClassNotFoundException) {
            try {
                Class.forName("com.miui.internal.core.SdkManager").also {
                    Logger.withTag("miuisdk").w { "using legacy sdk" }
                }
            } catch (e2: ClassNotFoundException) {
                Logger.withTag("miuisdk").e { "no sdk found" }
                throw e2
            }
        }
    }
}
