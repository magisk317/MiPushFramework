package io.github.magisk317.mipush.feature.main

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
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
import io.github.magisk317.mipush.feature.navigation.NavigationInputKind
import io.github.magisk317.mipush.feature.navigation.PageActivationCoordinator
import io.github.magisk317.mipush.feature.navigation.TopLevelRoutePagerSynchronizer
import io.github.magisk317.mipush.feature.navigation.navigateTopLevel
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.manager.telemetry.PagePerformanceHandle
import io.github.magisk317.mipush.manager.telemetry.TransitionInputKind
import io.github.magisk317.mipush.manager.telemetry.TransitionPerformanceRecorder
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import io.github.magisk317.uikit.pager.MainPagerState
import io.github.magisk317.uikit.pager.rememberMainPagerState
import io.github.magisk317.uikit.surface.MainTabScaffold
import io.github.magisk317.uikit.surface.MainTabSpec
import io.github.magisk317.uikit.surface.PagerTabScaffold
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

internal fun resolveTabIndex(destination: NavDestination?): Int {
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
            route.startsWith(AppDestinations.SettingsSection.ROUTE) ||
            route.startsWith(AppDestinations.ConnectionStatus.ROUTE) ||
            route.startsWith(AppDestinations.StatusBarIconSettings.ROUTE) -> 3
        else -> 0
    }
}

internal fun shouldShowCompactBottomBar(destination: NavDestination?): Boolean {
    val route = destination?.route ?: return true
    return route.startsWith(AppDestinations.Overview.ROUTE) ||
        route.startsWith(AppDestinations.AppsList.ROUTE) ||
        route.startsWith(AppDestinations.EventsList.ROUTE) ||
        route.startsWith(AppDestinations.Configs.ROUTE) ||
        route.startsWith(AppDestinations.ConfigsSearch.ROUTE) ||
        route.startsWith(AppDestinations.ConfigEditor.ROUTE) ||
        route.startsWith(AppDestinations.Settings.ROUTE)
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
    val settingsViewModel: io.github.magisk317.mipush.main.viewmodel.SettingsViewModel =
        org.koin.androidx.compose.koinViewModel()
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
            label = stringResource(R.string.main_settings),
            icon = Icons.Default.Settings,
        ),
    )
    val tabRoutes = listOf(
        AppDestinations.Overview.ROUTE,
        AppDestinations.AppsList.ROUTE,
        AppDestinations.EventsList.ROUTE,
        AppDestinations.Settings.ROUTE,
    )

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
    // Use currentRoute when available so AnimatedContent pager recreation starts on the right page.
    val initialPagerPage = tabRoutes.indexOf(
        currentRoute?.takeIf { it in tabRoutes } ?: startDestination,
    ).takeIf { it >= 0 } ?: 0
    val pagerState = rememberMainPagerState(
        pageCount = { tabRoutes.size },
        initialPage = initialPagerPage,
    )
    val pageActivationCoordinator = remember {
        PageActivationCoordinator(initialPage = initialPagerPage)
    }
    val routePagerSynchronizer = remember { TopLevelRoutePagerSynchronizer() }
    var routePagerReconciled by remember { mutableStateOf(false) }
    val performanceRecorder = remember { TransitionPerformanceRecorder() }
    val performanceHandle = remember { arrayOfNulls<PagePerformanceHandle>(1) }
    val isTopLevelRoute = currentRoute in tabRoutes
    LaunchedEffect(pagerState.selectedPage, isTopLevelRoute) {
        if (!isTopLevelRoute) {
            performanceHandle[0]?.cancel()
            performanceHandle[0] = null
            return@LaunchedEffect
        }
        val targetPage = pagerState.selectedPage
        val sourcePage = pagerState.currentPage
        if (pageActivationCoordinator.state.selectedPage != targetPage) {
            pageActivationCoordinator.requestNavigation(targetPage, NavigationInputKind.CLICK)
        }
        performanceHandle[0]?.cancel()
        val token = performanceRecorder.begin(
            sourcePage = sourcePage,
            targetPage = targetPage,
            inputKind = TransitionInputKind.CLICK,
            isCold = false,
        )
        performanceHandle[0] = PagePerformanceHandle(token, performanceRecorder).also { handle ->
            handle.firstComposition()
            withFrameNanos(handle::firstMeaningfulFrame)
        }
    }
    LaunchedEffect(pagerState.pagerState.currentPage) {
        pagerState.syncPage()
    }
    // Route restoration may lead the pager briefly (for example Settings route while the pager
    // is still settled on Overview). A route change must not restart this reverse bridge with the
    // old settled page, otherwise route -> pager and pager -> route continuously pull each other
    // between the two pages. Publish only when the pager itself settles or leaves top-level mode.
    LaunchedEffect(pagerState.pagerState.settledPage, isTopLevelRoute, routePagerReconciled) {
        if (!isTopLevelRoute || !routePagerReconciled) return@LaunchedEffect
        val route = tabRoutes.getOrNull(pagerState.pagerState.settledPage) ?: return@LaunchedEffect
        if (currentRoute == route) return@LaunchedEffect
        navController.navigateTopLevel(route)
    }
    LaunchedEffect(currentRoute, pagerState.isNavigating) {
        currentRoute?.let(pageActivationCoordinator::onRouteChanged)
        val targetPage = routePagerSynchronizer.targetPageFor(
            route = currentRoute,
            currentPage = pagerState.pagerState.currentPage,
            isNavigating = pagerState.isNavigating,
        )
        if (targetPage != null) {
            routePagerReconciled = false
            pagerState.animateToPage(targetPage)
            return@LaunchedEffect
        }
        routePagerReconciled = routePagerSynchronizer.isReconciled(
            route = currentRoute,
            currentPage = pagerState.pagerState.currentPage,
            isNavigating = pagerState.isNavigating,
        )
    }
    // When returning from a detail page to the pager, snap to the correct page immediately
    // instead of animating — otherwise the AnimatedContent transition briefly shows the wrong page.
    // Only snap on the transition from detail → pager (wasDetail=true → isTopLevelRoute=true).
    // popBackStack() briefly exposes the start destination before settling on the real target,
    // so snapping on every isTopLevelRoute=true would scroll to the wrong page.
    var wasDetailRoute by remember { mutableStateOf(false) }
    LaunchedEffect(isTopLevelRoute) {
        if (!isTopLevelRoute) {
            wasDetailRoute = true
            return@LaunchedEffect
        }
        if (!wasDetailRoute) return@LaunchedEffect
        wasDetailRoute = false
        val targetPage = currentRoute?.let { route -> tabRoutes.indexOf(route).takeIf { it >= 0 } }
            ?: return@LaunchedEffect
        if (pagerState.pagerState.currentPage != targetPage) {
            pagerState.pagerState.scrollToPage(targetPage)
        }
    }
    val allowScrollChrome = currentRoute?.let { route ->
        route.startsWith(AppDestinations.AppsList.ROUTE) ||
            route.startsWith(AppDestinations.EventsList.ROUTE)
    } == true
    // A top-level tab route is only the navigation bridge for deep routes. Do not reset the
    // scroll chrome for every Pager settle: doing so restarts the header animation and forces a
    // content inset/layout pass during the same frame as the page transition.
    val chromeResetKey = if (isTopLevelRoute) "main-tabs" else currentRoute
    val chromeController = rememberMainChromeController(
        isCompact = isCompact,
        compactChromeRouteAvailable = shouldShowCompactBottomBar(currentDestination),
        keepVisible = shouldKeepMainChromeVisible(currentRoute),
        allowScrollHide = allowScrollChrome,
        resetKey = chromeResetKey,
        animationMillis = MAIN_CHROME_ANIMATION_MILLIS,
    )
    val pageScrollChromeState = chromeController.pageScrollChromeState

    Box(modifier = Modifier.fillMaxSize()) {
        PagerTabScaffold(
            tabs = tabs,
            pagerState = pagerState,
            isCompact = isCompact,
            chromeController = chromeController,
            onTabReselected = { index ->
                tabRoutes.getOrNull(index)?.let(::triggerRefreshForRoute)
            },
            pagerVisible = isTopLevelRoute,
            onChromeTransition = { transition ->
                pageActivationCoordinator.onPagerSettled(
                    transition.settledPage,
                    pageActivationCoordinator.token,
                )
                performanceHandle[0]?.let { handle ->
                    if (transition.settledPage == handle.token.targetPage) {
                        handle.pagerSettled(transition.settledPage)
                    }
                }
            },
            // Four top-level pages now match the fully retained pager model: configurations are
            // a secondary Settings route, so they no longer participate in every pager frame.
            beyondViewportPageCount = 3,
            retainPageContentAfterFirstFrame = true,
            reserveCompactBottomBarSpace = true,
            animationMillis = MAIN_CHROME_ANIMATION_MILLIS,
            railHeader = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
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
        ) { page, contentPadding ->
            val pageIsSettled = pageActivationCoordinator.activationFor(page).isActive
            // Only the visible pager page may drive the shared chrome. Adjacent pages are
            // precomposed for fast navigation, but their list observers must not overwrite the
            // active page's bottom-bar/header state.
            val activePageScrollChromeState = if (page == pagerState.pagerState.currentPage) {
                pageScrollChromeState
            } else {
                null
            }
            when (page) {
                0 -> Overview(
                    contentPadding = contentPadding,
                    isActive = pageIsSettled,
                    onShowAboutDialog = { content -> aboutDialogContent = content },
                    onNavigateToConnectionStatus = {
                        context.startActivity(Intent(context, io.github.magisk317.mipush.feature.main.subpage.ConnectionStatusPage::class.java))
                    },
                )
                1 -> ApplicationList(
                    query = "",
                    contentPadding = contentPadding,
                    refreshSignal = appRefreshTrigger,
                    filterMode = 0,
                    isActive = pageIsSettled,
                    onAppClick = { pkg ->
                        context.startActivity(
                            Intent(context, ApplicationInfoPage::class.java)
                                .putExtra(ApplicationInfoPage.EXTRA_PACKAGE_NAME, pkg)
                                .putExtra(ApplicationInfoPage.EXTRA_IGNORE_NOT_REGISTERED, true),
                        )
                    },
                    scrollChromeState = activePageScrollChromeState,
                )
                2 -> EventList(
                    query = "",
                    contentPadding = contentPadding,
                    refreshSignal = eventRefreshTrigger,
                    groupByApp = false,
                    isActive = pageIsSettled,
                    scrollChromeState = activePageScrollChromeState,
                )
                3 -> Settings(
                    contentPadding = contentPadding,
                    viewModel = settingsViewModel,
                    onShowAboutDialog = { content -> aboutDialogContent = content },
                    onSectionChanged = {},
                    onNavigateToConnectionStatus = {
                        context.startActivity(Intent(context, io.github.magisk317.mipush.feature.main.subpage.ConnectionStatusPage::class.java))
                    },
                    onNavigateToStatusBarIconSettings = { navController.navigate(AppDestinations.StatusBarIconSettings.ROUTE) },
                    onNavigateToConfigurations = { navController.navigate(AppDestinations.Configs.ROUTE) },
                    sectionBackSignal = settingsBackSignal,
                    isActive = pageIsSettled,
                    scrollChromeState = activePageScrollChromeState,
                )
            }
        }

        // Detail overlay — rendered directly on top of the pager when a non-top-level
        // route is active.  No AnimatedVisibility to avoid spawning a second ViewRootImpl.
        if (!isTopLevelRoute) {
            MainTabScaffold(
                tabs = tabs,
                selectedIndex = resolveTabIndex(currentDestination),
                isCompact = isCompact,
                chromeController = chromeController,
                onTabSelected = { index ->
                    tabRoutes.getOrNull(index)?.let(navController::navigateTopLevel)
                },
                onTabReselected = { index -> tabRoutes.getOrNull(index)?.let(::triggerRefreshForRoute) },
                animationMillis = MAIN_CHROME_ANIMATION_MILLIS,
                railHeader = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
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
                        isActive = currentRoute?.startsWith(AppDestinations.Overview.ROUTE) == true,
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
                        isActive = currentRoute?.startsWith(AppDestinations.EventsList.ROUTE) == true,
                        scrollChromeState = pageScrollChromeState,
                    )
                },
                appsPage = { q, padding, _, filterMode ->
                    ApplicationList(
                        q,
                        contentPadding = padding,
                        refreshSignal = appRefreshTrigger,
                        filterMode = filterMode,
                        isActive = currentRoute?.startsWith(AppDestinations.AppsList.ROUTE) == true,
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
                configsPage = { initialQuery, padding, refreshSignal, onOpenEditor, onBack ->
                    Configurations(
                        initialQuery = initialQuery,
                        contentPadding = padding,
                        refreshSignal = configRefreshTrigger + refreshSignal,
                        isActive = currentRoute?.startsWith(AppDestinations.Configs.ROUTE) == true ||
                            currentRoute?.startsWith(AppDestinations.ConfigsSearch.ROUTE) == true,
                        onOpenEditor = onOpenEditor,
                        onBack = onBack,
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
                        viewModel = settingsViewModel,
                        isActive = currentRoute?.startsWith(AppDestinations.Settings.ROUTE) == true ||
                            currentRoute?.startsWith(AppDestinations.SettingsSection.ROUTE) == true,
                        onShowAboutDialog = onAbout,
                        onSectionChanged = {},
                        onNavigateToConnectionStatus = {
                            context.startActivity(Intent(context, io.github.magisk317.mipush.feature.main.subpage.ConnectionStatusPage::class.java))
                        },
                        onNavigateToStatusBarIconSettings = {
                            navController.navigate(AppDestinations.StatusBarIconSettings.ROUTE)
                        },
                        onNavigateToConfigurations = {
                            navController.navigate(AppDestinations.Configs.ROUTE)
                        },
                        sectionBackSignal = settingsBackSignal,
                        scrollChromeState = pageScrollChromeState,
                    )
                },
                onAbout = { content -> aboutDialogContent = content },
                onSectionChanged = {},
                )
            }
        } // detail overlay

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
