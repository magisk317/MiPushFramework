package io.github.magisk317.mipush.common.utils.rom.miui

import android.util.Log

internal object MiuiSdkManagerHelper {
    @JvmStatic
    @Throws(ClassNotFoundException::class)
    fun getSdkManagerClass(): Class<*> {
        return try {
            Class.forName("miui.core.SdkManager")
        } catch (e: ClassNotFoundException) {
            try {
                Class.forName("com.miui.internal.core.SdkManager").also {
                    Log.w("miuisdk", "using legacy sdk")
                }
            } catch (e2: ClassNotFoundException) {
                Log.e("miuisdk", "no sdk found")
                throw e2
            }
        }
    }
}
