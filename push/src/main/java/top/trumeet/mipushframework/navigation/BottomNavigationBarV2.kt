package top.trumeet.mipushframework.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import com.xiaomi.xmsf.R

/**
 * 底部导航栏组件（现代化版本）
 *
 * 替代传统的 ViewPager + Tab 方式，使用 Navigation 驱动的导航模式。
 * 自动追踪 NavController 的当前路由，无需手动同步。
 *
 * ## 特点
 * ✅ 自动路由追踪：currentBackStackEntryAsState 自动同步
 * ✅ 类型安全：使用 AppDestinations 而非硬编码字符串
 * ✅ 毛玻璃效果：集成 Haze 库实现现代化设计
 * ✅ 双击刷新：支持 tab 双击时刷新页面内容
 *
 * ## 使用示例
 * ```
 * BottomNavigationBarV2(
 *     navController = navController,
 *     hazeState = hazeState,
 *     hazeStyle = hazeStyle,
 *     onTabDoubleTap = { tabIndex -> /* refresh */ }
 * )
 * ```
 */
@Composable
fun BottomNavigationBarV2(
    navController: NavController,
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    onTabDoubleTap: (Int) -> Unit = {}
) {
    // 追踪当前路由
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 选中的标签页索引
    var selectedIndex by remember { mutableStateOf(0) }
    var lastTappedIndex by remember { mutableStateOf(-1) }
    var lastTappedAt by remember { mutableStateOf(0L) }

    // Navigation items 定义
    data class TabItem(
        val label: Int,  // String resource ID
        val icon: Int,   // Drawable resource ID
        val route: String,
        val index: Int
    )

    val tabs = listOf(
        TabItem(
            label = R.string.main_event,
            icon = R.drawable.ic_event_note_black_24dp,
            route = AppDestinations.EventsList.ROUTE,
            index = 0
        ),
        TabItem(
            label = R.string.main_apps,
            icon = R.drawable.ic_apps_black_24dp,
            route = AppDestinations.AppsList.ROUTE,
            index = 1
        ),
        TabItem(
            label = R.string.action_help,
            icon = R.drawable.ic_help_outline_24,
            route = AppDestinations.Help.ROUTE,
            index = 2
        ),
        TabItem(
            label = R.string.main_settings,
            icon = R.drawable.ic_settings_black_24dp,
            route = AppDestinations.Settings.ROUTE,
            index = 3
        )
    )

    // 根据当前路由更新 selectedIndex
    tabs.forEachIndexed { index, tab ->
        if (currentRoute?.startsWith(tab.route) == true) {
            selectedIndex = index
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .hazeEffect(hazeState, hazeStyle) {
                forceInvalidateOnPreDraw = true
            }
            .navigationBarsPadding()
    ) {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = selectedIndex == tab.index,
                    onClick = {
                        // 检测双击
                        val now = System.currentTimeMillis()
                        if (lastTappedIndex == tab.index && now - lastTappedAt < 300) {
                            // 双击
                            onTabDoubleTap(tab.index)
                        } else {
                            // 单击 - 导航到该页面
                            when (tab.route) {
                                AppDestinations.EventsList.ROUTE -> {
                                    navController.navigate(AppDestinations.EventsList.ROUTE) {
                                        popUpTo(AppDestinations.EventsList.ROUTE) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                                AppDestinations.AppsList.ROUTE -> {
                                    navController.navigate(AppDestinations.AppsList.ROUTE) {
                                        popUpTo(AppDestinations.AppsList.ROUTE) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                                AppDestinations.Help.ROUTE -> {
                                    navController.navigate(AppDestinations.Help.ROUTE) {
                                        popUpTo(AppDestinations.Help.ROUTE) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                                AppDestinations.Settings.ROUTE -> {
                                    navController.navigate(AppDestinations.Settings.ROUTE) {
                                        popUpTo(AppDestinations.Settings.ROUTE) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                        lastTappedIndex = tab.index
                        lastTappedAt = now
                    },
                    icon = {
                        Icon(
                            painter = painterResource(id = tab.icon),
                            contentDescription = stringResource(tab.label),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    label = {
                        Text(stringResource(tab.label))
                    }
                )
            }
        }
    }
}
