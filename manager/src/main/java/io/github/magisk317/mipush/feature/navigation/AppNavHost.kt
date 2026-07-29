package io.github.magisk317.mipush.feature.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.magisk317.uikit.surface.tabEnterTransition
import io.github.magisk317.uikit.surface.tabExitTransition
import io.github.magisk317.uikit.surface.tabForwardDirection
import io.github.magisk317.uikit.surface.tabPopEnterTransition
import io.github.magisk317.uikit.surface.tabPopExitTransition
import io.github.magisk317.uikit.surface.tabPredictivePopEnterTransition
import io.github.magisk317.uikit.surface.tabPredictivePopExitTransition
import io.github.magisk317.uikit.surface.tabTransitionDirection
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

    fun rankDirection(initialRoute: String?, targetRoute: String?, isPop: Boolean = false): Int {
        return if (isPop) {
            tabTransitionDirection(
                initialIndex = routeRank(initialRoute),
                targetIndex = routeRank(targetRoute),
                isPop = true,
            )
        } else {
            tabForwardDirection(
                initialIndex = routeRank(initialRoute),
                targetIndex = routeRank(targetRoute),
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            tabEnterTransition(
                rankDirection(initialState.destination.route, targetState.destination.route),
            )
        },
        exitTransition = {
            tabExitTransition(
                rankDirection(initialState.destination.route, targetState.destination.route),
            )
        },
        popEnterTransition = {
            tabPopEnterTransition(
                rankDirection(initialState.destination.route, targetState.destination.route),
            )
        },
        popExitTransition = {
            tabPopExitTransition(
                rankDirection(initialState.destination.route, targetState.destination.route),
            )
        },
        predictivePopEnterTransition = { _ ->
            tabPredictivePopEnterTransition()
        },
        predictivePopExitTransition = { swipeEdge ->
            tabPredictivePopExitTransition(swipeEdge)
        },
    ) {
        composable(
            route = AppDestinations.Overview.ROUTE,
        ) {
            overviewPage(contentPadding)
        }

        // ==================== Events 分支 ====================
        composable(
            route = AppDestinations.EventsList.ROUTE,
        ) {
            eventsPage("", contentPadding, 0, false)
        }

        composable(
            route = AppDestinations.EventDetails.ROUTE_PATTERN,
            arguments = listOf(NavigationArguments.eventIdArgument),
        ) { /* detail route reserved */ }

        // ==================== Apps 分支 ====================
        composable(
            route = AppDestinations.AppsList.ROUTE,
        ) {
            appsPage("", contentPadding, 0, 0)
        }

        composable(
            route = AppDestinations.AppDetails.ROUTE_PATTERN,
            arguments = listOf(NavigationArguments.packageNameArgument),
        ) { /* detail route reserved */ }

        // ==================== Configs 分支 ====================
        composable(
            route = AppDestinations.Configs.ROUTE,
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
        ) {
            settingsPage(contentPadding, onAbout, onSectionChanged, 0)
        }

        composable(
            route = AppDestinations.SettingsSection.ROUTE_PATTERN,
            arguments = listOf(NavigationArguments.settingsSectionArgument),
        ) { /* detail route reserved */ }

        // ==================== Connection Status ====================
        composable(
            route = AppDestinations.ConnectionStatus.ROUTE,
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
