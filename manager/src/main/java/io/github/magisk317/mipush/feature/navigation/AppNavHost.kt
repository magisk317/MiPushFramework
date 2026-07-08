package io.github.magisk317.mipush.feature.navigation

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
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
    overviewPage: @Composable (PaddingValues) -> Unit,
    eventsPage: @Composable (String, PaddingValues, Int, Boolean) -> Unit,
    appsPage: @Composable (String, PaddingValues, Int, Int) -> Unit,
    configsPage: @Composable (String, PaddingValues, Int, (String) -> Unit) -> Unit,
    configEditorPage: @Composable (String, PaddingValues, () -> Unit) -> Unit,
    settingsPage: @Composable (PaddingValues, (String?) -> Unit, (String?) -> Unit, Int) -> Unit,
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

            route.startsWith(AppDestinations.Configs.ROUTE) ||
                route.startsWith(AppDestinations.ConfigsSearch.ROUTE) ||
                route.startsWith(AppDestinations.ConfigEditor.ROUTE) -> 3

            route.startsWith(AppDestinations.Settings.ROUTE) ||
                route.startsWith(AppDestinations.SettingsSection.ROUTE) ||
                route.startsWith(AppDestinations.StatusBarIconSettings.ROUTE) -> 4

            else -> 0
        }
    }

    fun decodeRouteArg(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
    }

    fun forwardDirection(initialRoute: String?, targetRoute: String?): Int {
        return if (routeRank(targetRoute) >= routeRank(initialRoute)) 1 else -1
    }

    fun predictivePopDirection(initialRoute: String?, targetRoute: String?): Int {
        val initialRank = routeRank(initialRoute)
        val targetRank = routeRank(targetRoute)
        return when {
            targetRank > initialRank -> 1
            targetRank < initialRank -> -1
            else -> -1
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        predictivePopEnterTransition = { _ ->
            val direction = predictivePopDirection(initialState.destination.route, targetState.destination.route)
            slideInHorizontally(
                initialOffsetX = { direction * it },
                animationSpec = tween(300, easing = EaseInOut),
            ) + fadeIn(animationSpec = tween(300))
        },
        predictivePopExitTransition = { _ ->
            val direction = predictivePopDirection(initialState.destination.route, targetState.destination.route)
            slideOutHorizontally(
                targetOffsetX = { -direction * it },
                animationSpec = tween(300, easing = EaseInOut),
            ) + fadeOut(animationSpec = tween(300))
        },
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
            overviewPage(contentPadding)
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
            eventsPage("", contentPadding, 0, false)
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
            appsPage("", contentPadding, 0, 0)
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

        // ==================== Configs 分支 ====================
        composable(
            route = AppDestinations.Configs.ROUTE,
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
            configsPage(
                "",
                contentPadding,
                0,
                { path -> navController.navigate(AppDestinations.ConfigEditor.route(path)) },
            )
        }

        composable(
            route = AppDestinations.ConfigsSearch.ROUTE_PATTERN,
            arguments = listOf(NavigationArguments.configInitialQueryArgument),
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
        ) { backStackEntry ->
            configsPage(
                decodeRouteArg(
                    backStackEntry.arguments?.getString(AppDestinations.ConfigsSearch.ARGUMENT_INITIAL_QUERY),
                ),
                contentPadding,
                0,
                { path -> navController.navigate(AppDestinations.ConfigEditor.route(path)) },
            )
        }

        composable(
            route = AppDestinations.ConfigEditor.ROUTE_PATTERN,
            arguments = listOf(NavigationArguments.configPathArgument),
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
        ) { backStackEntry ->
            configEditorPage(
                decodeRouteArg(
                    backStackEntry.arguments?.getString(AppDestinations.ConfigEditor.ARGUMENT_PATH),
                ),
                contentPadding,
                { navController.popBackStack() },
            )
        }

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
            settingsPage(contentPadding, onAbout, onSectionChanged, 0)
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

        // ==================== Connection Status ====================
        composable(
            route = AppDestinations.ConnectionStatus.ROUTE,
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
            val viewModel: io.github.magisk317.mipush.main.viewmodel.ConnectionStatusViewModel =
                org.koin.androidx.compose.koinViewModel()
            io.github.magisk317.mipush.feature.main.subpage.ConnectionStatusPage(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestinations.StatusBarIconSettings.ROUTE,
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
            val viewModel: io.github.magisk317.mipush.main.viewmodel.SettingsViewModel =
                org.koin.androidx.compose.koinViewModel()
            io.github.magisk317.mipush.feature.main.subpage.StatusBarIconSettingsPage(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
