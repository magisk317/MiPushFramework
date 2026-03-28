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
    // 导航动画规范
    val enterAnimation = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(300, easing = EaseInOut)
    ) + fadeIn(animationSpec = tween(300))

    val exitAnimation = slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = tween(300, easing = EaseInOut)
    ) + fadeOut(animationSpec = tween(300))

    val popEnterAnimation = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(300, easing = EaseInOut)
    ) + fadeIn(animationSpec = tween(300))

    val popExitAnimation = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(300, easing = EaseInOut)
    ) + fadeOut(animationSpec = tween(300))

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(
            route = AppDestinations.Overview.ROUTE,
            enterTransition = { enterAnimation },
            exitTransition = { exitAnimation },
            popEnterTransition = { popEnterAnimation },
            popExitTransition = { popExitAnimation },
        ) {
            overviewPage(contentPadding, hazeState, hazeStyle)
        }

        // ==================== Events 分支 ====================
        composable(
            route = AppDestinations.EventsList.ROUTE,
            enterTransition = { enterAnimation },
            exitTransition = { exitAnimation },
            popEnterTransition = { popEnterAnimation },
            popExitTransition = { popExitAnimation },
        ) {
            eventsPage("", contentPadding, 0, false, hazeState, hazeStyle)
        }

        composable(
            route = AppDestinations.EventDetails.ROUTE_PATTERN,
            arguments = listOf(NavigationArguments.eventIdArgument),
            enterTransition = { enterAnimation },
            exitTransition = { exitAnimation },
            popEnterTransition = { popEnterAnimation },
            popExitTransition = { popExitAnimation },
        ) { /* detail route reserved */ }

        // ==================== Apps 分支 ====================
        composable(
            route = AppDestinations.AppsList.ROUTE,
            enterTransition = { enterAnimation },
            exitTransition = { exitAnimation },
            popEnterTransition = { popEnterAnimation },
            popExitTransition = { popExitAnimation },
        ) {
            appsPage("", contentPadding, 0, 0, hazeState, hazeStyle)
        }

        composable(
            route = AppDestinations.AppDetails.ROUTE_PATTERN,
            arguments = listOf(NavigationArguments.packageNameArgument),
            enterTransition = { enterAnimation },
            exitTransition = { exitAnimation },
            popEnterTransition = { popEnterAnimation },
            popExitTransition = { popExitAnimation },
        ) { /* detail route reserved */ }

        // ==================== Settings 分支 ====================
        composable(
            route = AppDestinations.Settings.ROUTE,
            enterTransition = { enterAnimation },
            exitTransition = { exitAnimation },
            popEnterTransition = { popEnterAnimation },
            popExitTransition = { popExitAnimation },
        ) {
            settingsPage(contentPadding, onAbout, onSectionChanged, 0, hazeState, hazeStyle)
        }

        // ==================== Help 分支 ====================
        composable(
            route = AppDestinations.Help.ROUTE,
            enterTransition = { enterAnimation },
            exitTransition = { exitAnimation },
            popEnterTransition = { popEnterAnimation },
            popExitTransition = { popExitAnimation },
        ) {
            helpPage(contentPadding, hazeState, hazeStyle)
        }

        composable(
            route = AppDestinations.SettingsSection.ROUTE_PATTERN,
            arguments = listOf(NavigationArguments.settingsSectionArgument),
            enterTransition = { enterAnimation },
            exitTransition = { exitAnimation },
            popEnterTransition = { popEnterAnimation },
            popExitTransition = { popExitAnimation },
        ) { /* detail route reserved */ }
    }
}
