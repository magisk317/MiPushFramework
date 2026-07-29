package io.github.magisk317.mipush.feature.main

import io.github.magisk317.mipush.common.R as CommonR
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.magisk317.mipush.feature.main.subpage.ApplicationList
import io.github.magisk317.mipush.feature.main.subpage.ConfigurationEditor
import io.github.magisk317.mipush.feature.main.subpage.Configurations
import io.github.magisk317.mipush.feature.main.subpage.EventList
import io.github.magisk317.mipush.feature.main.subpage.Overview
import io.github.magisk317.mipush.feature.main.subpage.Settings
import io.github.magisk317.mipush.feature.navigation.AppDestinations
import io.github.magisk317.mipush.feature.navigation.AppNavHostContent
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import io.github.magisk317.uikit.surface.MainTabScaffold
import io.github.magisk317.uikit.surface.MainTabSpec
import io.github.magisk317.uikit.surface.rememberIsCompactWidth
import io.github.magisk317.uikit.surface.rememberMainChromeController

private const val MAIN_CHROME_ANIMATION_MILLIS = 160

internal fun shouldKeepMainChromeVisible(route: String?): Boolean {
    return route?.startsWith(AppDestinations.Overview.ROUTE) == true ||
        route?.startsWith(AppDestinations.Settings.ROUTE) == true ||
        route?.startsWith(AppDestinations.SettingsSection.ROUTE) == true ||
        route?.startsWith(AppDestinations.Configs.ROUTE) == true ||
        route?.startsWith(AppDestinations.ConfigsSearch.ROUTE) == true
}


@Composable
fun MainScreen(
    startDestination: String,
    initialRouteOverride: String? = null,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val isCompact = rememberIsCompactWidth()

    var aboutDialogContent by remember { mutableStateOf<String?>(null) }
    var settingsBackSignal by rememberSaveable { mutableIntStateOf(0) }
    var eventRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }
    var appRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }
    var configRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }

    val tabs = listOf(
        MainTabSpec(
            label = stringResource(R.string.main_overview),
            icon = Icons.Default.Home,
        ),
        MainTabSpec(
            label = stringResource(R.string.main_apps),
            icon = Icons.Default.Apps,
        ),
        MainTabSpec(
            label = stringResource(R.string.main_event),
            icon = Icons.AutoMirrored.Filled.List,
        ),
        MainTabSpec(
            label = stringResource(R.string.main_configs),
            icon = Icons.Default.Tune,
        ),
        MainTabSpec(
            label = stringResource(R.string.main_settings),
            icon = Icons.Default.Settings,
        ),
    )
    val tabRoutes = listOf(
        AppDestinations.Overview.ROUTE,
        AppDestinations.AppsList.ROUTE,
        AppDestinations.EventsList.ROUTE,
        AppDestinations.Configs.ROUTE,
        AppDestinations.Settings.ROUTE,
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

    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val selectedIndex = resolveTabIndex(currentDestination)
    val allowScrollChrome = currentRoute?.let { route ->
        route.startsWith(AppDestinations.AppsList.ROUTE) ||
            route.startsWith(AppDestinations.EventsList.ROUTE)
    } == true
    val chromeController = rememberMainChromeController(
        isCompact = isCompact,
        compactChromeRouteAvailable = shouldShowCompactBottomBar(currentDestination),
        keepVisible = shouldKeepMainChromeVisible(currentRoute),
        allowScrollHide = allowScrollChrome,
        resetKey = currentRoute,
        animationMillis = MAIN_CHROME_ANIMATION_MILLIS,
    )
    val pageScrollChromeState = chromeController.pageScrollChromeState

    fun navigateToTab(index: Int) {
        val route = tabRoutes.getOrNull(index) ?: return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MainTabScaffold(
            tabs = tabs,
            selectedIndex = selectedIndex,
            isCompact = isCompact,
            chromeController = chromeController,
            onTabSelected = { index -> navigateToTab(index) },
            onTabReselected = { index ->
                tabRoutes.getOrNull(index)?.let(::triggerRefreshForRoute)
            },
            animationMillis = MAIN_CHROME_ANIMATION_MILLIS,
            railHeader = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(
                        painter = painterResource(CommonR.drawable.ic_notifications_black_24dp),
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
        ) { contentPadding ->
            AppNavHostContent(
                navController = navController,
                startDestination = startDestination,
                contentPadding = contentPadding,
                overviewPage = { padding ->
                    Overview(
                        contentPadding = padding,
                        onShowAboutDialog = { content -> aboutDialogContent = content },
                        onNavigateToConnectionStatus = {
                            navController.navigate(AppDestinations.ConnectionStatus.ROUTE)
                        },
                    )
                },
                eventsPage = { q, padding, _, groupByApp ->
                    EventList(
                        query = q,
                        contentPadding = padding,
                        refreshSignal = eventRefreshTrigger,
                        groupByApp = groupByApp,
                        scrollChromeState = pageScrollChromeState,
                    )
                },
                appsPage = { q, padding, _, filterMode ->
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
                        scrollChromeState = pageScrollChromeState,
                    )
                },
                configsPage = { initialQuery, padding, refreshSignal, onOpenEditor ->
                    Configurations(
                        initialQuery = initialQuery,
                        contentPadding = padding,
                        refreshSignal = configRefreshTrigger + refreshSignal,
                        onOpenEditor = onOpenEditor,
                        scrollChromeState = pageScrollChromeState,
                    )
                },
                configEditorPage = { path, padding, onBack ->
                    ConfigurationEditor(
                        path = path,
                        onBack = onBack,
                        contentPadding = padding,
                    )
                },
                settingsPage = { padding, onAbout, _, _ ->
                    Settings(
                        contentPadding = padding,
                        onShowAboutDialog = onAbout,
                        onSectionChanged = {},
                        onNavigateToConnectionStatus = {
                            navController.navigate(AppDestinations.ConnectionStatus.ROUTE)
                        },
                        onNavigateToStatusBarIconSettings = {
                            navController.navigate(AppDestinations.StatusBarIconSettings.ROUTE)
                        },
                        sectionBackSignal = settingsBackSignal,
                        scrollChromeState = pageScrollChromeState,
                    )
                },
                onAbout = { content -> aboutDialogContent = content },
                onSectionChanged = {},
            )
        }

        if (aboutDialogContent != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { aboutDialogContent = null },
                confirmButton = {
                    DialogActionRow(
                        actions = listOf(
                            DialogAction(
                                label = stringResource(android.R.string.copy),
                                onClick = {
                                    val clipboard = context.getSystemService(
                                        android.content.Context.CLIPBOARD_SERVICE,
                                    ) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                            "mipush",
                                            aboutDialogContent,
                                        ),
                                    )
                                    android.widget.Toast.makeText(
                                        context,
                                        android.R.string.copy,
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                    aboutDialogContent = null
                                },
                            ),
                        ),
                    )
                },
                text = { Text(aboutDialogContent!!) },
            )
        }
    }
}
