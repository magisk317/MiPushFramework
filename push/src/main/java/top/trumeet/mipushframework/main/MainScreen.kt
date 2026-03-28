package top.trumeet.mipushframework.main

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xiaomi.xmsf.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import top.trumeet.mipushframework.data.EventRepository
import top.trumeet.mipushframework.main.subpage.ApplicationList
import top.trumeet.mipushframework.main.subpage.EventList
import top.trumeet.mipushframework.main.subpage.Overview
import top.trumeet.mipushframework.main.subpage.Settings
import top.trumeet.mipushframework.navigation.AppDestinations
import top.trumeet.mipushframework.navigation.AppNavHostContent
import top.trumeet.ui.theme.SystemBarsScrim

private const val TAB_DOUBLE_TAP_REFRESH_WINDOW_MS = 350L

@Immutable
private data class MainTabItem(
    val labelRes: Int,
    val iconRes: Int,
    val route: String,
)

@Composable
fun MainScreen(
    startDestination: String,
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    eventRepository: EventRepository,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 600

    var aboutDialogContent by remember { mutableStateOf<String?>(null) }
    var settingsBackSignal by rememberSaveable { mutableIntStateOf(0) }
    var eventRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }
    var appRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }
    val tabLastTapAt = remember { mutableStateMapOf<String, Long>() }

    val tabs = listOf(
        MainTabItem(
            labelRes = R.string.main_overview,
            iconRes = R.drawable.ic_home_black_24dp,
            route = AppDestinations.Overview.ROUTE,
        ),
        MainTabItem(
            labelRes = R.string.main_apps,
            iconRes = R.drawable.ic_apps_black_24dp,
            route = AppDestinations.AppsList.ROUTE,
        ),
        MainTabItem(
            labelRes = R.string.main_event,
            iconRes = R.drawable.ic_event_note_black_24dp,
            route = AppDestinations.EventsList.ROUTE,
        ),
        MainTabItem(
            labelRes = R.string.main_settings,
            iconRes = R.drawable.ic_settings_black_24dp,
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

            route.startsWith(AppDestinations.Settings.ROUTE) ||
                route.startsWith(AppDestinations.SettingsSection.ROUTE) -> 3

            else -> 0
        }
    }

    fun shouldShowCompactBottomBar(destination: NavDestination?): Boolean {
        val route = destination?.route ?: return true
        return route.startsWith(AppDestinations.Overview.ROUTE) ||
            route.startsWith(AppDestinations.AppsList.ROUTE) ||
            route.startsWith(AppDestinations.EventsList.ROUTE) ||
            route.startsWith(AppDestinations.Settings.ROUTE)
    }

    fun triggerRefreshForRoute(route: String) {
        when (route) {
            AppDestinations.Overview.ROUTE -> Unit
            AppDestinations.EventsList.ROUTE -> eventRefreshTrigger++
            AppDestinations.AppsList.ROUTE -> appRefreshTrigger++
            AppDestinations.Settings.ROUTE -> settingsBackSignal++
        }
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
                )
            },
            appsPage = { q, padding, _, filterMode, hState, hStyle ->
                ApplicationList(
                    q,
                    contentPadding = padding,
                    refreshSignal = appRefreshTrigger,
                    filterMode = filterMode,
                    onAppClick = { pkg -> eventRepository.startManagePermissions(pkg, true) },
                    hazeState = hState,
                    hazeStyle = hStyle,
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
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        if (isCompact) {
            Scaffold(
                bottomBar = {
                    if (shouldShowCompactBottomBar(navBackStackEntry?.destination)) {
                        Box(
                            modifier = Modifier
                                .hazeEffect(hazeState, hazeStyle) {
                                    forceInvalidateOnPreDraw = true
                                }
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                                .navigationBarsPadding(),
                        ) {
                            NavigationBar(
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp,
                            ) {
                                tabs.forEachIndexed { index, tab ->
                                    val selected = resolveTabIndex(navBackStackEntry?.destination) == index
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { handleTabClick(tab, selected) },
                                        icon = {
                                            Icon(
                                                painter = painterResource(tab.iconRes),
                                                contentDescription = stringResource(tab.labelRes),
                                            )
                                        },
                                        label = { Text(stringResource(tab.labelRes)) },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                        ),
                                        alwaysShowLabel = false,
                                    )
                                }
                            }
                        }
                    }
                },
            ) { innerPadding ->
                MainContent(innerPadding)
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    header = {
                        Icon(
                            painter = painterResource(R.drawable.ic_notifications_black_24dp),
                            contentDescription = null,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    },
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val selected = resolveTabIndex(navBackStackEntry?.destination) == index
                        NavigationRailItem(
                            icon = {
                                Icon(
                                    painter = painterResource(tab.iconRes),
                                    contentDescription = stringResource(tab.labelRes),
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            selected = selected,
                            alwaysShowLabel = false,
                            onClick = { handleTabClick(tab, selected) },
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    MainContent(androidx.compose.foundation.layout.PaddingValues(0.dp))
                }
            }
        }

        SystemBarsScrim(hazeState = hazeState, hazeStyle = hazeStyle)

        if (aboutDialogContent != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { aboutDialogContent = null },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { aboutDialogContent = null }) {
                        Text(text = stringResource(android.R.string.ok))
                    }
                },
                text = { Text(aboutDialogContent!!) },
            )
        }
    }
}
