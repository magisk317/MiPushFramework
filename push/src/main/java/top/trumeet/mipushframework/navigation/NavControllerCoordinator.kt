package top.trumeet.mipushframework.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.navOptions

/**
 * NavController-based 导航协调器实现
 *
 * 提供编译期类型检查的导航操作，替代字符串 route 的方式。
 * 与 Navigation Compose 2.8+ 的类型安全导航特性集成。
 *
 * ## 特点
 * ✅ 类型安全：所有导航操作都经过编译检查
 * ✅ 自动参数管理：参数序列化/反序列化自动处理
 * ✅ 智能返回：支持返回目标和条件判断
 * ✅ 导航栈管理：支持 popUpTo、inclusive 等高级操作
 *
 * ## 使用示例
 * ```
 * val navigator = NavControllerNavigationCoordinator(navController)
 * navigator.navigateToEventDetails(eventId = 123)
 * navigator.navigateToAppDetails(packageName = "com.example")
 * ```
 */
class NavControllerNavigationCoordinator(
    private val navController: NavController
) : NavigationCoordinator {
    override fun navigateToOverview() {
        navController.navigate(AppDestinations.Overview.ROUTE) {
            popUpTo(AppDestinations.Overview.ROUTE) { inclusive = true }
        }
    }

    override fun navigateToEventsList() {
        navController.navigate(AppDestinations.EventsList.ROUTE) {
            popUpTo(AppDestinations.EventsList.ROUTE) { inclusive = true }
        }
    }

    override fun navigateToEventDetails(eventId: Long) {
        navController.navigate(AppDestinations.EventDetails.route(eventId))
    }

    override fun navigateToAppsList() {
        navController.navigate(AppDestinations.AppsList.ROUTE) {
            popUpTo(AppDestinations.AppsList.ROUTE) { inclusive = true }
        }
    }

    override fun navigateToAppDetails(packageName: String) {
        navController.navigate(AppDestinations.AppDetails.route(packageName))
    }

    override fun navigateToSettings() {
        navController.navigate(AppDestinations.Settings.ROUTE) {
            popUpTo(AppDestinations.Settings.ROUTE) { inclusive = true }
        }
    }

    override fun navigateToSettingsSection(section: String) {
        navController.navigate(AppDestinations.SettingsSection.route(section))
    }

    override fun navigateBack(): Boolean {
        return navController.previousBackStackEntry != null && navController.navigateUp()
    }

    override fun getCurrentRoute(): String? {
        return navController.currentDestination?.route
    }
}

/**
 * 导航操作辅助函数
 *
 * 便捷函数，用于常见的导航场景，减少代码重复。
 */

/**
 * 从当前路由导航，支持条件跳转和后退栈清除
 *
 * @param route 目标路由
 * @param navOptions 导航选项（popUpTo, inclusive 等）
 */
fun NavController.navigateSafely(
    route: String,
    navOptions: NavOptions? = null
) {
    runCatching {
        navigate(route, navOptions)
    }.onFailure { e ->
        android.util.Log.e("Navigation", "Failed to navigate to $route", e)
    }
}

/**
 * 返回到指定的路由（如果在导航栈中）
 *
 * @param route 目标路由
 * @param inclusive 是否包含目标路由本身
 * @return true if navigation was successful
 */
fun NavController.navigateUpTo(
    route: String,
    inclusive: Boolean = false
): Boolean {
    return runCatching {
        popBackStack(route, inclusive)
    }.getOrDefault(false)
}
