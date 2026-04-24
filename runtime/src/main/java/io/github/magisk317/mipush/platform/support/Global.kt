package io.github.magisk317.mipush.platform.support

import io.github.magisk317.mipush.common.configurations.ConfigValueConverter
import io.github.magisk317.mipush.common.configurations.IconConfigurations
import com.xiaomi.push.sdk.PushMessageProcessor
import io.github.magisk317.mipush.common.cache.ApplicationNameCache
import io.github.magisk317.mipush.common.cache.IconCache
import io.github.magisk317.mipush.push.hook.ModernHookHandler
import io.github.magisk317.mipush.MiPushEventListener
import io.github.magisk317.mipush.service.RegistrationRecorder
import io.github.magisk317.mipush.common.utils.Singleton
import io.github.magisk317.mipush.runtime.app.ConfigCenter

/**
 * 全局组件访问中心
 * 
 * 整合了所有的单例访问接口，确保项目状态管理的唯一性和闭环性。
 */
object Global {
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
    fun configValueConverter(): ConfigValueConverter = Singleton.instance()

    @JvmStatic
    fun iconConfigurations(): IconConfigurations = Singleton.instance()

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

    // --- Legacy PascalCase compatibility (Internal use only, marked for removal) ---
    @Deprecated("Use camelCase version", ReplaceWith("hookHandler()"))
    fun HookHandler() = hookHandler()

    @Deprecated("Use camelCase version", ReplaceWith("miPushEventListener()"))
    fun MiPushEventListener() = miPushEventListener()

    @Deprecated("Use camelCase version", ReplaceWith("registrationRecorder()"))
    fun RegistrationRecorder() = registrationRecorder()

    @Deprecated("Use camelCase version", ReplaceWith("configValueConverter()"))
    fun ConfigValueConverter() = configValueConverter()

    @Deprecated("Use camelCase version", ReplaceWith("iconConfigurations()"))
    fun IconConfigurations() = iconConfigurations()

    @Deprecated("Use camelCase version", ReplaceWith("configCenter()"))
    fun ConfigCenter() = configCenter()

    @Deprecated("Use camelCase version", ReplaceWith("applicationNameCache()"))
    fun ApplicationNameCache() = applicationNameCache()

    @Deprecated("Use camelCase version", ReplaceWith("iconCache()"))
    fun IconCache() = iconCache()
}
