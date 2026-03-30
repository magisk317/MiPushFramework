package top.trumeet.mipushframework.navigation

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.foundation.layout.PaddingValues
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle

/**
 * 应用导航图定义
 *
 * 将所有路由和页面组合在一个地方，形成一个完整的导航图。
 * 使用 Navigation Compose 2.8+ 的特性，支持编译期类型检查和智能参数传递。
 *
 * ## 页面结构
 * - EventsList (底部 Tab 0)
 *   ├─ EventDetails (详情页)
 * - AppsList (底部 Tab 1)
 *   ├─ AppDetails (详情页)
 * - Settings (底部 Tab 2)
 *   ├─ SettingsSection (子分类)
 *
 * ## 导航动画
 * - 水平滑动 + 淡入淡出效果
 * - 动画时长: 300ms
 * - Easing: EaseInOut
 */
@Composable
fun AppNavHostContent(
    navController: NavHostController,
    startDestination: String = AppDestinations.Overview.ROUTE,
    contentPadding: PaddingValues,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null,
    overviewPage: @Composable (PaddingValues, HazeState?, HazeStyle?) -> Unit,
    eventsPage: @Composable (String, PaddingValues, Int, Boolean, HazeState?, HazeStyle?) -> Unit,
    appsPage: @Composable (String, PaddingValues, Int, Int, HazeState?, HazeStyle?) -> Unit,
    settingsPage: @Composable (PaddingValues, (String?) -> Unit, (String?) -> Unit, Int, HazeState?, HazeStyle?) -> Unit,
    helpPage: @Composable (PaddingValues, HazeState?, HazeStyle?) -> Unit,
    onAbout: (String?) -> Unit = {},
    onSectionChanged: (String?) -> Unit = {},
) {
    fun routeRank(route: String?): Int {
        if (route == null) return 0
        return when {
            route.startsWith(AppDestinations.Overview.ROUTE) -> 0
            route.startsWith(AppDestinations.AppsList.ROUTE) ||
                route.startsWith(AppDestinations.AppDetails.ROUTE) -> 1

            route.startsWith(AppDestinations.EventsList.ROUTE) ||
                route.startsWith(AppDestinations.EventDetails.ROUTE) -> 2

            route.startsWith(AppDestinations.Settings.ROUTE) ||
                route.startsWith(AppDestinations.SettingsSection.ROUTE) -> 3

            route.startsWith(AppDestinations.Help.ROUTE) -> 4
            else -> 0
        }
    }

    fun forwardDirection(initialRoute: String?, targetRoute: String?): Int {
        return if (routeRank(targetRoute) >= routeRank(initialRoute)) 1 else -1
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(
            route = AppDestinations.Overview.ROUTE,
            enterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
        ) {
            overviewPage(contentPadding, hazeState, hazeStyle)
        }

        // ==================== Events 分支 ====================
        composable(
            route = AppDestinations.EventsList.ROUTE,
            enterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
        ) {
            eventsPage("", contentPadding, 0, false, hazeState, hazeStyle)
        }

        composable(
            route = AppDestinations.EventDetails.ROUTE_PATTERN,
            arguments = listOf(NavigationArguments.eventIdArgument),
            enterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
        ) { /* detail route reserved */ }

        // ==================== Apps 分支 ====================
        composable(
            route = AppDestinations.AppsList.ROUTE,
            enterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
        ) {
            appsPage("", contentPadding, 0, 0, hazeState, hazeStyle)
        }

        composable(
            route = AppDestinations.AppDetails.ROUTE_PATTERN,
            arguments = listOf(NavigationArguments.packageNameArgument),
            enterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
        ) { /* detail route reserved */ }

        // ==================== Settings 分支 ====================
        composable(
            route = AppDestinations.Settings.ROUTE,
            enterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
        ) {
            settingsPage(contentPadding, onAbout, onSectionChanged, 0, hazeState, hazeStyle)
        }

        // ==================== Help 分支 ====================
        composable(
            route = AppDestinations.Help.ROUTE,
            enterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
        ) {
            helpPage(contentPadding, hazeState, hazeStyle)
        }

        composable(
            route = AppDestinations.SettingsSection.ROUTE_PATTERN,
            arguments = listOf(NavigationArguments.settingsSectionArgument),
            enterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { -direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                val direction = forwardDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { direction * it },
                    animationSpec = tween(300, easing = EaseInOut),
                ) + fadeOut(animationSpec = tween(300))
            },
        ) { /* detail route reserved */ }
    }
}
