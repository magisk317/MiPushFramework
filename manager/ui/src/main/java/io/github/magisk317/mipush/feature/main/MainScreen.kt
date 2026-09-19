package io.github.magisk317.mipush.feature.main

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.magisk317.mipush.feature.main.subpage.ApplicationList
import io.github.magisk317.mipush.feature.main.subpage.ConfigurationsPage
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
import io.github.magisk317.uikit.pager.rememberMainPagerState
import io.github.magisk317.uikit.surface.MainTabScaffold
import io.github.magisk317.uikit.surface.MainTabSpec
import io.github.magisk317.uikit.surface.PagerTabScaffold
import io.github.magisk317.uikit.surface.rememberIsCompactWidth
import io.github.magisk317.uikit.surface.rememberMainChromeController
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import co.touchlab.kermit.Logger

private val navLog = Logger.withTag("NavDiag")

private const val MAIN_CHROME_ANIMATION_MILLIS = 160

internal fun shouldKeepMainChromeVisible(route: String?): Boolean {
    return route?.startsWith(AppDestinations.Overview.ROUTE) == true ||
        route?.startsWith(AppDestinations.Settings.ROUTE) == true
}

internal fun resolveTabIndex(destination: NavDestination?): Int {
    val route = destination?.route ?: return 0
    return when {
        route.startsWith(AppDestinations.Overview.ROUTE) -> 0
        route.startsWith(AppDestinations.AppsList.ROUTE) ||
            route.startsWith(AppDestinations.AppDetails.ROUTE) -> 1
        route.startsWith(AppDestinations.EventsList.ROUTE) ||
            route.startsWith(AppDestinations.EventDetails.ROUTE) -> 2
        route.startsWith(AppDestinations.Settings.ROUTE) ||
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

    val settingsViewModel: io.github.magisk317.mipush.main.viewmodel.SettingsViewModel =
        org.koin.androidx.compose.koinViewModel()
    val floatingBottomBar by settingsViewModel.navigationFloatingBottomBar.collectAsStateWithLifecycle()
    val bottomBarBlur by settingsViewModel.navigationBottomBarBlur.collectAsStateWithLifecycle()
    val bottomBarBackdrop by settingsViewModel.navigationBottomBarBackdrop.collectAsStateWithLifecycle()
    var eventRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }
    var appRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }
    var aboutDialogContent by remember { mutableStateOf<String?>(null) }

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
    // Starts unreconciled on purpose. The route visible during the first composition can still
    // be the graph start destination rather than the restored route, and deriving `true` from
    // that guess let EffectD declare convergence before the real route arrived, which then kept
    // EffectE from ever publishing it. EffectD converges the flag on the first real route.
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
        navLog.d { "EffectA: selectedPage=$targetPage sourcePage=$sourcePage coordSP=${pageActivationCoordinator.state.selectedPage} isTop=$isTopLevelRoute" }
        if (pageActivationCoordinator.state.selectedPage != targetPage) {
            navLog.d { "EffectA: requestNavigation($targetPage, CLICK)" }
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
        navLog.d { "EffectE: settledPage=${pagerState.pagerState.settledPage} isTop=$isTopLevelRoute reconciled=$routePagerReconciled route=$currentRoute" }
        // Only a pager that is still moving may hold the reverse bridge back. Treating a stale
        // `false` as a hard block let one missed reconciliation strand the route on the previous
        // tab forever while the pager sat on the tab the user had tapped.
        if (!isTopLevelRoute) return@LaunchedEffect
        if (!routePagerReconciled && pagerState.pagerState.isScrollInProgress) return@LaunchedEffect
        val route = tabRoutes.getOrNull(pagerState.pagerState.settledPage) ?: return@LaunchedEffect
        if (currentRoute == route) return@LaunchedEffect
        navLog.d { "EffectE: navigateTopLevel($route)" }
        navController.navigateTopLevel(route)
    }
    // Track the route already dispatched to the activation coordinator. This effect is keyed on
    // isNavigating as well so a route change that lands mid-animation is retried once the pager
    // stops moving, but the dispatch itself must run only on an actual route change: re-running
    // it on an isNavigating flip would re-apply the (still stale) current route over a fresh
    // CLICK selection. The resulting DEEP_ROUTE token then disagrees with the click target, so
    // onPagerSettled's token check rejects the settle, settledPage sticks on the old page, and
    // the route<->pager bridges ping-pong the last two pages (each toggle re-firing the pages'
    // isActive load effects).
    var lastDispatchedRoute by remember { mutableStateOf(currentRoute) }
    LaunchedEffect(currentRoute, pagerState.isNavigating) {
        navLog.d {
            "EffectD: route=$currentRoute lastDispatched=$lastDispatchedRoute " +
                "isNav=${pagerState.isNavigating} " +
                "currentPage=${pagerState.pagerState.currentPage} " +
                "settledPage=${pagerState.pagerState.settledPage}"
        }
        if (currentRoute != null && currentRoute != lastDispatchedRoute) {
            lastDispatchedRoute = currentRoute
            navLog.d { "EffectD: onRouteChanged($currentRoute)" }
            pageActivationCoordinator.onRouteChanged(currentRoute)
        }
        val targetPage = routePagerSynchronizer.targetPageFor(
            route = currentRoute,
            currentPage = pagerState.pagerState.currentPage,
            isNavigating = pagerState.isNavigating,
        )
        navLog.d { "EffectD: targetPageFor=$targetPage reconciled=$routePagerReconciled" }
        if (targetPage != null) {
            routePagerReconciled = false
            navLog.d { "EffectD: animateToPage($targetPage)" }
            routePagerSynchronizer.notifyTargetPage(targetPage)
            pagerState.animateToPage(targetPage)
            return@LaunchedEffect
        }
        if (!routePagerReconciled) {
            routePagerReconciled = routePagerSynchronizer.isReconciled(
                route = currentRoute,
                currentPage = pagerState.pagerState.currentPage,
                isNavigating = pagerState.isNavigating,
            )
            navLog.d { "EffectD: isReconciled=$routePagerReconciled" }
        }
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
        compactChromeRouteAvailable = shouldShowCompactBottomBar(currentDestination) && isTopLevelRoute,
        keepVisible = shouldKeepMainChromeVisible(currentRoute),
        allowScrollHide = allowScrollChrome,
        resetKey = chromeResetKey,
        animationMillis = MAIN_CHROME_ANIMATION_MILLIS,
    )
    val pageScrollChromeState = chromeController.pageScrollChromeState
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PagerTabScaffold(
            tabs = tabs,
            pagerState = pagerState,
            isCompact = isCompact,
            showSystemBarsScrim = false,
            chromeController = chromeController,
            onTabSelected = { index ->
                navLog.d { "onTabSelected: index=$index isTop=$isTopLevelRoute selectedPage=${pagerState.selectedPage} currentPage=${pagerState.currentPage}" }
                // Register the tap before anything moves. Until the route controller catches up,
                // a route -> pager lookup still reads the route the user is leaving, and without
                // this registration that stale lookup bounces the pager straight back to it.
                routePagerSynchronizer.notifyUserIntent(index)
                // Always navigate. On a top-level route launchSingleTop makes this a no-op for the
                // tab already showing, so route and pager advance within the same frame instead of
                // waiting for EffectE to publish the tap after the animation settles. On a deep
                // route (settings section, theme page, ...) the pager layer is hidden, so the same
                // canonical navigateTopLevel policy pops that branch back onto the tapped tab.
                tabRoutes.getOrNull(index)?.let(navController::navigateTopLevel)
                if (isTopLevelRoute) {
                    pagerState.animateToPage(index)
                }
            },
            onTabReselected = { index ->
                if (isTopLevelRoute) {
                    tabRoutes.getOrNull(index)?.let(::triggerRefreshForRoute)
                } else {
                    tabRoutes.getOrNull(index)?.let(navController::navigateTopLevel)
                }
            },
            pagerVisible = isTopLevelRoute,
            onChromeTransition = { transition ->
                routePagerSynchronizer.notifyTargetPage(transition.settledPage)
                val settled = pageActivationCoordinator.onPagerSettled(
                    transition.settledPage,
                    pageActivationCoordinator.token,
                )
                navLog.d {
                    "onChromeTransition: settledPage=${transition.settledPage} " +
                        "currentPage=${transition.currentPage} " +
                        "token=${pageActivationCoordinator.token?.id} " +
                        "accepted=$settled " +
                        "coordSP=${pageActivationCoordinator.state.selectedPage} " +
                        "coordSettled=${pageActivationCoordinator.state.settledPage}"
                }
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
            onDiagnostic = { message -> navLog.d { message } },
            reserveCompactBottomBarSpace = true,
            floatingBottomBar = floatingBottomBar,
            bottomBarBlur = bottomBarBlur,
            bottomBarBackdrop = bottomBarBackdrop,
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
            if (page == 3) navLog.d { "PageContent: page=$page isActive=$pageIsSettled coordSettled=${pageActivationCoordinator.state.settledPage}" }
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
                    onSectionChanged = {},
                    onNavigateToConnectionStatus = {
                        context.startActivity(Intent(context, io.github.magisk317.mipush.feature.main.subpage.ConnectionStatusPage::class.java))
                    },
                    onNavigateToStatusBarIconSettings = { navController.navigate(AppDestinations.StatusBarIconSettings.ROUTE) },
                    onNavigateToThemeSettings = {
                        navController.navigate(AppDestinations.ThemeSettings.ROUTE)
                    },
                    onNavigateToConfigurations = {
                        context.startActivity(Intent(context, ConfigurationsPage::class.java))
                    },
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
                // Miuix pages own their full-bleed top surface (TopAppBar + page background);
                // the default scrim would paint a mismatched chrome band over the status-bar
                // strip on detail routes (theme page etc.).
                showSystemBarsScrim = false,
                floatingBottomBar = floatingBottomBar,
                bottomBarBlur = bottomBarBlur,
                bottomBarBackdrop = bottomBarBackdrop,
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
                settingsPage = { padding, _ ->
                    Settings(
                        contentPadding = padding,
                        viewModel = settingsViewModel,
                        isActive = currentRoute?.startsWith(AppDestinations.Settings.ROUTE) == true,
                        onSectionChanged = {},
                        onNavigateToConnectionStatus = {
                            context.startActivity(Intent(context, io.github.magisk317.mipush.feature.main.subpage.ConnectionStatusPage::class.java))
                        },
                        onNavigateToStatusBarIconSettings = {
                            navController.navigate(AppDestinations.StatusBarIconSettings.ROUTE)
                        },
                        onNavigateToThemeSettings = {
                            navController.navigate(AppDestinations.ThemeSettings.ROUTE)
                        },
                        onNavigateToConfigurations = {
                            context.startActivity(Intent(context, ConfigurationsPage::class.java))
                        },
                        scrollChromeState = pageScrollChromeState,
                    )
                },
                )
            }
        } // detail overlay

        if (aboutDialogContent != null) {
            io.github.magisk317.uikit.surface.AppAlertDialog(
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
