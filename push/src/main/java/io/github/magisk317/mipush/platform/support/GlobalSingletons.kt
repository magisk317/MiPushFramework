package io.github.magisk317.mipush.platform.support

import io.github.magisk317.mipush.framework.hook.ModernHookHandler
import io.github.magisk317.mipush.framework.sdk.MiPushEventListener
import io.github.magisk317.mipush.service.RegistrationRecorder
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.common.cache.ApplicationNameCache
import io.github.magisk317.mipush.common.cache.IconCache
import io.github.magisk317.mipush.common.utils.Singleton

/**
 * 全局单例访问器
 *
 * 注意：此类是为了在迁移期间保持向后兼容而保留的。
 * 最终目标是通过依赖注入替代这些全局单例。
 */
object GlobalSingletons {
    @JvmStatic
    fun hookHandler(): ModernHookHandler = Singleton.instance()

    @JvmStatic
    fun setHookHandler(hookHandler: ModernHookHandler) {
        Singleton.reset(hookHandler)
    }

    @JvmStatic
    fun miPushEventListener(): MiPushEventListener = Singleton.instance()

    @JvmStatic
    fun setMiPushEventListener(listener: MiPushEventListener) {
        Singleton.reset(listener)
    }

    @JvmStatic
    fun registrationRecorder(): RegistrationRecorder = Singleton.instance()

    @JvmStatic
    fun setRegistrationRecorder(instance: RegistrationRecorder) {
        Singleton.reset(instance)
    }

    @JvmStatic
    fun configValueConverter(): com.xiaomi.xmsf.push.utils.ConfigValueConverter =
        Singleton.instance()

    @JvmStatic
    fun iconConfigurations(): io.github.magisk317.mipush.utils.IconConfigurations = Singleton.instance()

    @JvmStatic
    fun configCenter(): ConfigCenter = Singleton.instance()

    @JvmStatic
    fun setConfigCenter(configCenter: ConfigCenter) {
        Singleton.reset(configCenter)
    }

    @JvmStatic
    fun applicationNameCache(): ApplicationNameCache = ApplicationNameCache

    @JvmStatic
    fun iconCache(): IconCache = IconCache
}
