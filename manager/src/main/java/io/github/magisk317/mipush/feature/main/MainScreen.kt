package io.github.magisk317.mipush.feature.main

import android.content.Intent
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.magisk317.mipush.manager.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import io.github.magisk317.uikit.surface.AppBottomNavigationBar
import io.github.magisk317.uikit.surface.AppNavigationItemSpec
import io.github.magisk317.uikit.surface.AppNavigationRail
import io.github.magisk317.mipush.feature.ui.component.DialogAction
import io.github.magisk317.mipush.feature.ui.component.DialogActionRow
import io.github.magisk317.mipush.feature.main.subpage.ApplicationList
import io.github.magisk317.mipush.feature.main.subpage.ConfigurationEditor
import io.github.magisk317.mipush.feature.main.subpage.Configurations
import io.github.magisk317.mipush.feature.main.subpage.EventList
import io.github.magisk317.mipush.feature.main.subpage.Overview
import io.github.magisk317.mipush.feature.main.subpage.Settings
import io.github.magisk317.mipush.feature.navigation.AppDestinations
import io.github.magisk317.mipush.feature.navigation.AppNavHostContent
import io.github.magisk317.mipush.feature.ui.theme.SystemBarsScrim

private const val TAB_DOUBLE_TAP_REFRESH_WINDOW_MS = 350L
private const val MAIN_CHROME_ANIMATION_MILLIS = 160
private val COMPACT_BOTTOM_BAR_CONTENT_PADDING = 80.dp

internal fun shouldKeepMainChromeVisible(route: String?, chromeVisible: Boolean): Boolean {
    return route?.startsWith(AppDestinations.Overview.ROUTE) == true || chromeVisible
}

internal fun shouldShowBottomGestureScrim(
    isCompact: Boolean,
    compactBottomBarAvailable: Boolean,
    compactBottomBarVisible: Boolean,
): Boolean {
    return isCompact && compactBottomBarAvailable && compactBottomBarVisible
}

@Immutable
private data class MainTabItem(
    val labelRes: Int,
    val icon: ImageVector,
    val route: String,
)

@Composable
fun MainScreen(
    startDestination: String,
    initialRouteOverride: String? = null,
    hazeState: HazeState,
    hazeStyle: HazeBlurStyle,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 600

    var aboutDialogContent by remember { mutableStateOf<String?>(null) }
    var settingsBackSignal by rememberSaveable { mutableIntStateOf(0) }
    var eventRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }
    var appRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }
    var configRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }
    val tabLastTapAt = remember { mutableStateMapOf<String, Long>() }
    val scrollChromeState = rememberMainScrollChromeState()

    val tabs = listOf(
        MainTabItem(
            labelRes = R.string.main_overview,
            icon = Icons.Default.Home,
            route = AppDestinations.Overview.ROUTE,
        ),
        MainTabItem(
            labelRes = R.string.main_apps,
            icon = Icons.Default.Apps,
            route = AppDestinations.AppsList.ROUTE,
        ),
        MainTabItem(
            labelRes = R.string.main_event,
            icon = Icons.AutoMirrored.Filled.List,
            route = AppDestinations.EventsList.ROUTE,
        ),
        MainTabItem(
            labelRes = R.string.main_configs,
            icon = Icons.Default.Tune,
            route = AppDestinations.Configs.ROUTE,
        ),
        MainTabItem(
            labelRes = R.string.main_settings,
            icon = Icons.Default.Settings,
            route = AppDestinations.Settings.ROUTE,
        ),
    )

    fun resolveTabIndex(destination: NavDestination?): Int {
        val route = destination?.route ?: return 0
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
                route.startsWith(AppDestinations.SettingsSection.ROUTE) -> 4

            else -> 0
        }
    }

    fun shouldShowCompactBottomBar(destination: NavDestination?): Boolean {
        val route = destination?.route ?: return true
        return route.startsWith(AppDestinations.Overview.ROUTE) ||
            route.startsWith(AppDestinations.AppsList.ROUTE) ||
            route.startsWith(AppDestinations.EventsList.ROUTE) ||
            route.startsWith(AppDestinations.Configs.ROUTE) ||
            route.startsWith(AppDestinations.ConfigsSearch.ROUTE) ||
            route.startsWith(AppDestinations.Settings.ROUTE)
    }

    fun triggerRefreshForRoute(route: String) {
        when (route) {
            AppDestinations.Overview.ROUTE -> Unit
            AppDestinations.EventsList.ROUTE -> eventRefreshTrigger++
            AppDestinations.AppsList.ROUTE -> appRefreshTrigger++
            AppDestinations.Configs.ROUTE -> configRefreshTrigger++
            AppDestinations.Settings.ROUTE -> settingsBackSignal++
        }
    }

    LaunchedEffect(initialRouteOverride) {
        val route = initialRouteOverride?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (route == startDestination) return@LaunchedEffect
        if (navController.currentDestination?.route == route) return@LaunchedEffect
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    LaunchedEffect(navBackStackEntry?.destination?.route) {
        scrollChromeState.show()
    }

    fun handleTabClick(tab: MainTabItem, selected: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val last = tabLastTapAt[tab.route] ?: 0L
        tabLastTapAt[tab.route] = now

        if (selected) {
            if (now - last <= TAB_DOUBLE_TAP_REFRESH_WINDOW_MS) {
                triggerRefreshForRoute(tab.route)
            }
            return
        }

        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val compactBottomBarAvailable = isCompact && shouldShowCompactBottomBar(currentDestination)
    val mainChromeVisible = shouldKeepMainChromeVisible(currentRoute, scrollChromeState.isChromeVisible)
    val compactBottomBarVisible = compactBottomBarAvailable && mainChromeVisible

    @Composable
    fun MainContent(contentPadding: androidx.compose.foundation.layout.PaddingValues) {
        AppNavHostContent(
            navController = navController,
            startDestination = startDestination,
            contentPadding = contentPadding,
            hazeState = hazeState,
            hazeStyle = hazeStyle,
            overviewPage = { padding, hState, hStyle ->
                Overview(
                    contentPadding = padding,
                    onShowAboutDialog = { content -> aboutDialogContent = content },
                    hazeState = hState,
                    hazeStyle = hStyle,
                )
            },
            eventsPage = { q, padding, _, groupByApp, hState, hStyle ->
                EventList(
                    query = q,
                    contentPadding = padding,
                    refreshSignal = eventRefreshTrigger,
                    groupByApp = groupByApp,
                    hazeState = hState,
                    hazeStyle = hStyle,
                    scrollChromeState = scrollChromeState,
                )
            },
            appsPage = { q, padding, _, filterMode, hState, hStyle ->
                ApplicationList(
                    q,
                    contentPadding = padding,
                    refreshSignal = appRefreshTrigger,
                    filterMode = filterMode,
                    onAppClick = { pkg ->
                        context.startActivity(
                            Intent(context, ApplicationInfoPage::class.java)
                                .putExtra(ApplicationInfoPage.EXTRA_PACKAGE_NAME, pkg)
                                .putExtra(ApplicationInfoPage.EXTRA_IGNORE_NOT_REGISTERED, true),
                        )
                    },
                    hazeState = hState,
                    hazeStyle = hStyle,
                    scrollChromeState = scrollChromeState,
                )
            },
            configsPage = { initialQuery, padding, refreshSignal, onOpenEditor, hState, hStyle ->
                Configurations(
                    initialQuery = initialQuery,
                    contentPadding = padding,
                    refreshSignal = configRefreshTrigger + refreshSignal,
                    onOpenEditor = onOpenEditor,
                    hazeState = hState,
                    hazeStyle = hStyle,
                    scrollChromeState = scrollChromeState,
                )
            },
            configEditorPage = { path, padding, onBack, _, _ ->
                ConfigurationEditor(
                    path = path,
                    onBack = onBack,
                    contentPadding = padding,
                )
            },
            settingsPage = { padding, onAbout, _, _, hState, hStyle ->
                Settings(
                    contentPadding = padding,
                    onShowAboutDialog = onAbout,
                    onSectionChanged = {},
                    sectionBackSignal = settingsBackSignal,
                    hazeState = hState,
                    hazeStyle = hStyle,
                    scrollChromeState = scrollChromeState,
                )
            },
            helpPage = { padding, hState, hStyle ->
                HelpScreen(
                    modifier = Modifier.padding(padding),
                    hazeState = hState,
                    hazeStyle = hStyle,
                )
            },
            onAbout = { content -> aboutDialogContent = content },
            onSectionChanged = {},
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (isCompact) {
            val compactBottomPadding by animateDpAsState(
                targetValue = if (compactBottomBarVisible) {
                    COMPACT_BOTTOM_BAR_CONTENT_PADDING
                } else {
                    0.dp
                },
                animationSpec = tween(MAIN_CHROME_ANIMATION_MILLIS),
                label = "compactBottomPadding",
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                MainContent(androidx.compose.foundation.layout.PaddingValues(bottom = compactBottomPadding))
            }

            AnimatedVisibility(
                visible = compactBottomBarVisible,
                enter = slideInVertically(
                    animationSpec = tween(MAIN_CHROME_ANIMATION_MILLIS),
                    initialOffsetY = { it },
                ) + fadeIn(animationSpec = tween(MAIN_CHROME_ANIMATION_MILLIS)),
                exit = slideOutVertically(
                    animationSpec = tween(MAIN_CHROME_ANIMATION_MILLIS),
                    targetOffsetY = { it },
                ) + fadeOut(animationSpec = tween(MAIN_CHROME_ANIMATION_MILLIS)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .hazeEffect(hazeState) {
                                blurEffect { style = hazeStyle }
                                forceInvalidateOnPreDraw = true
                            },
                    ) {
                        AppBottomNavigationBar(
                            items = tabs.mapIndexed { index, tab ->
                                val selected = resolveTabIndex(navBackStackEntry?.destination) == index
                                AppNavigationItemSpec(
                                    label = stringResource(tab.labelRes),
                                    icon = tab.icon,
                                    selected = selected,
                                    onClick = { handleTabClick(tab, selected) },
                                )
                            },
                            containerColor = Color.Transparent,
                            alwaysShowLabel = false,
                        )
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .hazeEffect(hazeState) {
                            blurEffect { style = hazeStyle }
                            forceInvalidateOnPreDraw = true
                        },
                ) {
                    AppNavigationRail(
                        header = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_notifications_black_24dp),
                                    contentDescription = null,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxHeight(),
                        items = tabs.mapIndexed { index, tab ->
                            val selected = resolveTabIndex(navBackStackEntry?.destination) == index
                            AppNavigationItemSpec(
                                label = stringResource(tab.labelRes),
                                icon = tab.icon,
                                selected = selected,
                                onClick = { handleTabClick(tab, selected) },
                            )
                        },
                        alwaysShowLabel = false,
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    ) {
                        MainContent(androidx.compose.foundation.layout.PaddingValues(0.dp))
                    }
                }
            }
        }

        SystemBarsScrim(
            hazeState = hazeState,
            hazeStyle = hazeStyle,
            showTop = false,
            showBottom = shouldShowBottomGestureScrim(
                isCompact = isCompact,
                compactBottomBarAvailable = compactBottomBarAvailable,
                compactBottomBarVisible = compactBottomBarVisible,
            ),
            bottomBackgroundAlpha = 0f,
        )

        if (aboutDialogContent != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { aboutDialogContent = null },
                confirmButton = {
                    DialogActionRow(
                        actions = listOf(
                            DialogAction(
                                label = stringResource(android.R.string.ok),
                                onClick = { aboutDialogContent = null },
                            ),
                        ),
                    )
                },
                text = { Text(aboutDialogContent!!) },
            )
        }
    }
}
