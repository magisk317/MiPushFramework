package top.trumeet.common.plugin

import android.content.ComponentName

/**
 * 提供对于推送提供程序的检索功能
 */
object PluginManager {
    const val ARG_COMPONENT = "moe.yuuta.mipush.plugin.ARG_COMPONENT"

    // TODO

    /**
     * 检索该推送提供程序的推送管理服务是否可用且已启用。只有已启用的服务才能使用 API
     * @param name 对方服务 ComponentName
     * @return 是否启用
     */
    @JvmStatic
    fun isEnabled(name: ComponentName?): Boolean {
        // TODO
        return true
    }

    /**
     * 检查该组件是否可用（配置正确）
     */
    @JvmStatic
    fun verifyComponent(name: ComponentName): Boolean {
        // TODO
        return true
    }
}
