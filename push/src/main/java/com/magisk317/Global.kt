package com.magisk317

import com.magisk317.service.RegistrationRecorder
import com.magisk317.utils.Singleton
import com.xiaomi.xmsf.push.utils.ConfigValueConverter
import com.xiaomi.xmsf.push.utils.IconConfigurations
import com.xiaomi.xmsf.utils.ConfigCenter
import top.trumeet.common.cache.ApplicationNameCache
import top.trumeet.common.cache.IconCache

object Global {
    @JvmStatic
    fun HookHandler(): HookHandler = Singleton.instance()

    @JvmStatic
    fun setHookHandler(hookHandler: HookHandler) {
        Singleton.reset(hookHandler)
    }

    @JvmStatic
    fun MiPushEventListener(): MiPushEventListener = Singleton.instance()

    @JvmStatic
    fun setMiPushEventListener(listener: MiPushEventListener) {
        Singleton.reset(listener)
    }

    @JvmStatic
    fun RegistrationRecorder(): RegistrationRecorder = Singleton.instance()

    @JvmStatic
    fun setRegistrationRecorder(instance: RegistrationRecorder) {
        Singleton.reset(instance)
    }

    @JvmStatic
    fun ConfigValueConverter(): ConfigValueConverter = Singleton.instance()

    @JvmStatic
    fun IconConfigurations(): IconConfigurations = Singleton.instance()

    @JvmStatic
    fun ConfigCenter(): ConfigCenter = Singleton.instance()

    @JvmStatic
    fun setConfigCenter(configCenter: ConfigCenter) {
        Singleton.reset(configCenter)
    }

    @JvmStatic
    fun ApplicationNameCache(): ApplicationNameCache = Singleton.instance()

    @JvmStatic
    fun IconCache(): IconCache = Singleton.instance()
}
