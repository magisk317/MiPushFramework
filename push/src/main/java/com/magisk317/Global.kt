package com.magisk317

import com.xiaomi.xmsf.push.utils.ConfigValueConverter
import com.xiaomi.xmsf.push.utils.IconConfigurations
import com.xiaomi.xmsf.utils.ConfigCenter
import top.trumeet.common.cache.ApplicationNameCache
import top.trumeet.common.cache.IconCache

/**
 * Transitional facade for future package migration.
 * Existing singleton wiring still lives in com.nihility.Global.
 */
object Global {
    @JvmStatic
    fun HookHandler(): com.nihility.HookHandler = com.nihility.Global.HookHandler()

    @JvmStatic
    fun setHookHandler(hookHandler: com.nihility.HookHandler) {
        com.nihility.Global.setHookHandler(hookHandler)
    }

    @JvmStatic
    fun MiPushEventListener(): com.nihility.MiPushEventListener = com.nihility.Global.MiPushEventListener()

    @JvmStatic
    fun setMiPushEventListener(listener: com.nihility.MiPushEventListener) {
        com.nihility.Global.setMiPushEventListener(listener)
    }

    @JvmStatic
    fun RegistrationRecorder(): com.nihility.service.RegistrationRecorder =
        com.nihility.Global.RegistrationRecorder()

    @JvmStatic
    fun setRegistrationRecorder(instance: com.nihility.service.RegistrationRecorder) {
        com.nihility.Global.setRegistrationRecorder(instance)
    }

    @JvmStatic
    fun ConfigValueConverter(): ConfigValueConverter = com.nihility.Global.ConfigValueConverter()

    @JvmStatic
    fun IconConfigurations(): IconConfigurations = com.nihility.Global.IconConfigurations()

    @JvmStatic
    fun ConfigCenter(): ConfigCenter = com.nihility.Global.ConfigCenter()

    @JvmStatic
    fun setConfigCenter(configCenter: ConfigCenter) {
        com.nihility.Global.setConfigCenter(configCenter)
    }

    @JvmStatic
    fun ApplicationNameCache(): ApplicationNameCache = com.nihility.Global.ApplicationNameCache()

    @JvmStatic
    fun IconCache(): IconCache = com.nihility.Global.IconCache()
}

