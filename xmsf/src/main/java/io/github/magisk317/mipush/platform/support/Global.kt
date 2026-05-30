package io.github.magisk317.mipush.platform.support

import io.github.magisk317.mipush.utils.ConfigValueConverter
import io.github.magisk317.mipush.utils.IconConfigurations
import io.github.magisk317.mipush.common.cache.ApplicationNameCache
import io.github.magisk317.mipush.common.cache.IconCache
import io.github.magisk317.mipush.push.hook.ModernHookHandler
import io.github.magisk317.mipush.MiPushEventListener
import io.github.magisk317.mipush.service.RegistrationRecorder
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.app.di.AppDependencies

/**
 * 全局组件访问中心
 *
 * 整合了所有的单例访问接口，确保项目状态管理的唯一性和闭环性。
 * 实例统一由 Koin 解析（参见 [AppDependencies] / xmsfCoreKoinModule）。
 */
object Global {
    @JvmStatic
    fun hookHandler(): ModernHookHandler = AppDependencies.get(ModernHookHandler::class)

    @JvmStatic
    fun miPushEventListener(): MiPushEventListener = AppDependencies.get(MiPushEventListener::class)

    @JvmStatic
    fun registrationRecorder(): RegistrationRecorder = AppDependencies.get(RegistrationRecorder::class)

    @JvmStatic
    fun configValueConverter(): ConfigValueConverter = AppDependencies.get(ConfigValueConverter::class)

    @JvmStatic
    fun iconConfigurations(): IconConfigurations = AppDependencies.get(IconConfigurations::class)

    @JvmStatic
    fun configCenter(): ConfigCenter = AppDependencies.get(ConfigCenter::class)

    @JvmStatic
    fun applicationNameCache(): ApplicationNameCache = ApplicationNameCache

    @JvmStatic
    fun iconCache(): IconCache = IconCache
}
