@file:Suppress("DEPRECATION")
package com.nihility
// Compatibility shim: legacy namespace forwarding to com.magisk317.*

import com.magisk317.service.RegistrationRecorder
import com.xiaomi.xmsf.push.utils.ConfigValueConverter
import com.xiaomi.xmsf.push.utils.IconConfigurations
import com.xiaomi.xmsf.utils.ConfigCenter
import top.trumeet.common.cache.ApplicationNameCache
import top.trumeet.common.cache.IconCache

@Deprecated(
    message = "Use com.magisk317.Global instead.",
    replaceWith = ReplaceWith("com.magisk317.Global")
)
object Global {
    @JvmStatic
    fun HookHandler(): HookHandler = com.magisk317.Global.HookHandler()

    @JvmStatic
    fun setHookHandler(hookHandler: HookHandler) {
        com.magisk317.Global.setHookHandler(hookHandler)
    }

    @JvmStatic
    fun MiPushEventListener(): MiPushEventListener = com.magisk317.Global.MiPushEventListener()

    @JvmStatic
    fun setMiPushEventListener(listener: MiPushEventListener) {
        com.magisk317.Global.setMiPushEventListener(listener)
    }

    @JvmStatic
    fun RegistrationRecorder(): RegistrationRecorder = com.magisk317.Global.RegistrationRecorder()

    @JvmStatic
    fun setRegistrationRecorder(instance: RegistrationRecorder) {
        com.magisk317.Global.setRegistrationRecorder(instance)
    }

    @JvmStatic
    fun ConfigValueConverter(): ConfigValueConverter = com.magisk317.Global.ConfigValueConverter()

    @JvmStatic
    fun IconConfigurations(): IconConfigurations = com.magisk317.Global.IconConfigurations()

    @JvmStatic
    fun ConfigCenter(): ConfigCenter = com.magisk317.Global.ConfigCenter()

    @JvmStatic
    fun setConfigCenter(configCenter: ConfigCenter) {
        com.magisk317.Global.setConfigCenter(configCenter)
    }

    @JvmStatic
    fun ApplicationNameCache(): ApplicationNameCache = com.magisk317.Global.ApplicationNameCache()

    @JvmStatic
    fun IconCache(): IconCache = com.magisk317.Global.IconCache()
}
