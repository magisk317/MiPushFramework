package io.github.magisk317.mipush.feature.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 统一路由定义与构造器。
 *
 * 当前项目保留字符串路由方案，但所有动态路由应通过 companion route builder 构造，
 * 避免业务层散落字符串拼接。
 */

object AppDestinations {
    @Serializable
    data object Overview {
        const val ROUTE = "overview"
    }

    /**
     * 事件列表页面
     * 主要内容：Display list of push events with search and filtering
     */
    @Serializable
    data object EventsList {
        const val ROUTE = "events"
    }

    /**
     * 事件详情页面
     *
     * @param eventId 事件的唯一标识符
     *
     * 路由格式: "event_details/{eventId}"
     */
    @Serializable
    data class EventDetails(val eventId: Long) {
        companion object {
            const val ROUTE = "event_details"
            const val ARGUMENT_EVENT_ID = "eventId"
            const val ROUTE_PATTERN = "$ROUTE/{$ARGUMENT_EVENT_ID}"

            @JvmStatic
            fun route(eventId: Long): String = "$ROUTE/$eventId"
        }
    }

    /**
     * 应用列表页面
     * 主要内容：Display list of installed applications with filtering
     */
    @Serializable
    data object AppsList {
        const val ROUTE = "apps"
    }

    /**
     * 应用详情页面
     *
     * @param packageName 应用的包名
     *
     * 路由格式: "app_details/{packageName}"
     */
    @Serializable
    data class AppDetails(val packageName: String) {
        companion object {
            const val ROUTE = "app_details"
            const val ARGUMENT_PACKAGE_NAME = "packageName"
            const val ROUTE_PATTERN = "$ROUTE/{$ARGUMENT_PACKAGE_NAME}"

            @JvmStatic
            fun route(packageName: String): String {
                require(packageName.isNotBlank()) { "packageName must not be blank" }
                return "$ROUTE/${encodeRouteArg(packageName)}"
            }
        }
    }

    /**
     * 设置页面（顶级路由）
     * 主要内容：Application settings and configuration
     */
    @Serializable
    data object Settings {
        const val ROUTE = "settings"
    }

    /**
     * 配置列表页面（顶级路由）
     */
    @Serializable
    data object Configs {
        const val ROUTE = "configs"
    }

    /**
     * 配置列表页面（携带初始过滤词）
     */
    @Serializable
    data class ConfigsSearch(val initialQuery: String) {
        companion object {
            const val ROUTE = "configs_search"
            const val ARGUMENT_INITIAL_QUERY = "initialQuery"
            const val ROUTE_PATTERN = "$ROUTE/{$ARGUMENT_INITIAL_QUERY}"

            @JvmStatic
            fun route(initialQuery: String): String {
                require(initialQuery.isNotBlank()) { "initialQuery must not be blank" }
                return "$ROUTE/${encodeRouteArg(initialQuery)}"
            }
        }
    }

    /**
     * 配置编辑页面
     */
    @Serializable
    data class ConfigEditor(val path: String) {
        companion object {
            const val ROUTE = "config_editor"
            const val ARGUMENT_PATH = "path"
            const val ROUTE_PATTERN = "$ROUTE/{$ARGUMENT_PATH}"

            @JvmStatic
            fun route(path: String): String {
                require(path.isNotBlank()) { "path must not be blank" }
                return "$ROUTE/${encodeRouteArg(path)}"
            }
        }
    }

    /**
     * 帮助页面 (顶级路由)
     * 展示帮助与支持内容
     */
    @Serializable
    data object Help {
        const val ROUTE = "help"
    }

    /**
     * 设置子页面
     *
     * @param section 设置的分类 (e.g., "about", "advance", "ui")
     *
     * 路由格式: "settings/{section}"
     */
    @Serializable
    data class SettingsSection(val section: String) {
        companion object {
            const val ROUTE = "settings_section"
            const val ARGUMENT_SECTION = "section"
            const val ROUTE_PATTERN = "$ROUTE/{$ARGUMENT_SECTION}"

            @JvmStatic
            fun route(section: String): String {
                require(section.isNotBlank()) { "section must not be blank" }
                return "$ROUTE/${encodeRouteArg(section)}"
            }
        }
    }

    private fun encodeRouteArg(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    }
}

/**
 * 导航参数定义
 *
 * 用于 Navigation Compose 的参数定义，支持编译期类型检查。
 * 这些参数与 AppDestinations 中的路由一一对应。
 */
object NavigationArguments {
    val eventIdArgument = navArgument(AppDestinations.EventDetails.ARGUMENT_EVENT_ID) {
        type = NavType.LongType
    }

    val packageNameArgument = navArgument(AppDestinations.AppDetails.ARGUMENT_PACKAGE_NAME) {
        type = NavType.StringType
    }

    val settingsSectionArgument = navArgument(AppDestinations.SettingsSection.ARGUMENT_SECTION) {
        type = NavType.StringType
    }

    val configInitialQueryArgument = navArgument(AppDestinations.ConfigsSearch.ARGUMENT_INITIAL_QUERY) {
        type = NavType.StringType
    }

    val configPathArgument = navArgument(AppDestinations.ConfigEditor.ARGUMENT_PATH) {
        type = NavType.StringType
    }
}

/**
 * 导航操作接口
 *
 * 提供一致的导航操作 API，解耦具体的 NavController 实现。
 * UI 层使用此接口，而不是直接操作 NavController，便于测试和重构。
 */
interface NavigationCoordinator {
    fun navigateToOverview()

    /**
     * 导航至事件列表页面
     */
    fun navigateToEventsList()

    /**
     * 导航至事件详情页面
     *
     * @param eventId 事件 ID
     */
    fun navigateToEventDetails(eventId: Long)

    /**
     * 导航至应用列表页面
     */
    fun navigateToAppsList()

    /**
     * 导航至应用详情页面
     *
     * @param packageName 应用包名
     */
    fun navigateToAppDetails(packageName: String)

    /**
     * 导航至设置页面
     */
    fun navigateToSettings()

    /**
     * 导航至设置子页面
     *
     * @param section 设置分类
     */
    fun navigateToSettingsSection(section: String)

    /**
     * 返回上一页
     *
     * @return true if navigation was successful, false if already at root
     */
    fun navigateBack(): Boolean

    /**
     * 获取当前路由
     *
     * @return 当前路由的简称 (e.g., "events", "apps", "settings")
     */
    fun getCurrentRoute(): String?
}
