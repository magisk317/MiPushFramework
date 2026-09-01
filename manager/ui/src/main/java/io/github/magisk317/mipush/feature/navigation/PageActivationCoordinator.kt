package io.github.magisk317.mipush.feature.navigation

import io.github.magisk317.mipush.manager.ManagerNavigationInputKind
import io.github.magisk317.mipush.manager.ManagerNavigationRequestResult
import io.github.magisk317.mipush.manager.ManagerNavigationState
import io.github.magisk317.mipush.manager.ManagerPageActivation
import io.github.magisk317.mipush.manager.ManagerPageActivationCoordinator
import io.github.magisk317.mipush.manager.ManagerPageActivationPolicy
import io.github.magisk317.mipush.manager.ManagerPageComposePolicy
import io.github.magisk317.mipush.manager.ManagerPageDataPolicy
import io.github.magisk317.mipush.manager.ManagerPageRequestScope
import io.github.magisk317.mipush.manager.ManagerPageWorkHandle
import io.github.magisk317.mipush.manager.ManagerPageWorkKind
import io.github.magisk317.mipush.manager.ManagerRemotePriority
import io.github.magisk317.mipush.manager.ManagerTransitionToken

/** The four manager-owned top-level pages. Configuration routes belong to Settings. */
enum class TopLevelPage(
    val index: Int,
    val route: String,
) {
    OVERVIEW(0, AppDestinations.Overview.ROUTE),
    APPLICATIONS(1, AppDestinations.AppsList.ROUTE),
    EVENTS(2, AppDestinations.EventsList.ROUTE),
    SETTINGS(3, AppDestinations.Settings.ROUTE),
    ;

    companion object {
        fun fromIndex(index: Int): TopLevelPage? = entries.firstOrNull { it.index == index }

        fun fromRoute(route: String): TopLevelPage? = when {
            route == AppDestinations.Overview.ROUTE -> OVERVIEW
            route == AppDestinations.AppsList.ROUTE || route.startsWith("${AppDestinations.AppDetails.ROUTE}/") -> APPLICATIONS
            route == AppDestinations.EventsList.ROUTE || route.startsWith("${AppDestinations.EventDetails.ROUTE}/") -> EVENTS
            route == AppDestinations.Configs.ROUTE || route.startsWith("${AppDestinations.ConfigsSearch.ROUTE}/") ||
                route.startsWith("${AppDestinations.ConfigEditor.ROUTE}/") -> SETTINGS
            route == AppDestinations.Settings.ROUTE || route.startsWith("${AppDestinations.SettingsSection.ROUTE}/") ||
                route == AppDestinations.ConnectionStatus.ROUTE ||
                route == AppDestinations.StatusBarIconSettings.ROUTE -> SETTINGS
            else -> null
        }
    }
}

typealias NavigationInputKind = ManagerNavigationInputKind
typealias PageComposePolicy = ManagerPageComposePolicy
typealias PageDataPolicy = ManagerPageDataPolicy
typealias RemotePriority = ManagerRemotePriority
typealias TransitionToken = ManagerTransitionToken
typealias NavigationState = ManagerNavigationState
typealias PageActivation = ManagerPageActivation<TopLevelPage>
typealias PageWorkKind = ManagerPageWorkKind
typealias PageRequestScope = ManagerPageRequestScope
typealias PageWorkHandle = ManagerPageWorkHandle

sealed interface NavigationRequestResult {
    data class Accepted(
        val token: TransitionToken,
        val state: NavigationState,
    ) : NavigationRequestResult

    data class Rejected(val state: NavigationState) : NavigationRequestResult
}

data class PageActivationPolicy(
    val page: TopLevelPage,
    val composePolicy: PageComposePolicy = PageComposePolicy.KEEP_ALIVE,
    val dataPolicy: PageDataPolicy = PageDataPolicy.LOAD_ON_ACTIVE,
    val remotePriority: RemotePriority = RemotePriority.VISIBLE_PAGE,
) {
    val allowsBusinessRead: Boolean
        get() = dataPolicy != PageDataPolicy.NO_READS

    val allowsRemoteCall: Boolean
        get() = dataPolicy == PageDataPolicy.CACHE_THEN_REFRESH ||
            dataPolicy == PageDataPolicy.LOAD_ON_ACTIVE

    internal fun toCore() = ManagerPageActivationPolicy(
        page = page,
        composePolicy = composePolicy,
        dataPolicy = dataPolicy,
        remotePriority = remotePriority,
    )

    companion object {
        fun defaults(): Map<TopLevelPage, PageActivationPolicy> =
            TopLevelPage.entries.associateWith { PageActivationPolicy(it) }
    }
}

/** Manager UI compatibility facade; navigation and request invalidation live in core. */
class PageActivationCoordinator(
    policies: Map<TopLevelPage, PageActivationPolicy> = PageActivationPolicy.defaults(),
    initialPage: Int = TopLevelPage.OVERVIEW.index,
    clockNanos: () -> Long = System::nanoTime,
    adjacentPrecompositionEnabled: Boolean = false,
) {
    private val delegate = ManagerPageActivationCoordinator(
        pages = TopLevelPage.entries.toList(),
        routeOf = TopLevelPage::route,
        pageForRoute = TopLevelPage::fromRoute,
        policies = policies.mapValues { (_, policy) -> policy.toCore() },
        initialPage = initialPage,
        clockNanos = clockNanos,
        adjacentPrecompositionEnabled = adjacentPrecompositionEnabled,
    )

    val state: NavigationState
        get() = delegate.state

    val activePage: TopLevelPage?
        get() = delegate.activePage

    val token: TransitionToken?
        get() = delegate.token

    val adjacentShellPages: Set<TopLevelPage>
        get() = delegate.adjacentShellPages

    fun shouldComposeShell(page: Int): Boolean = delegate.shouldComposeShell(page)

    fun beginPageRequest(
        query: String = "",
        filter: String = "",
        userId: Int? = null,
        route: String = state.route,
        token: TransitionToken? = delegate.token,
    ): PageRequestScope? = delegate.beginPageRequest(query, filter, userId, route, token)

    fun registerPageWork(
        scope: PageRequestScope,
        kind: PageWorkKind,
        cancellable: Boolean = true,
        onCancel: () -> Unit,
    ): PageWorkHandle? = delegate.registerPageWork(scope, kind, cancellable, onCancel)

    fun isCurrent(scope: PageRequestScope): Boolean = delegate.isCurrent(scope)

    fun forgetPageWork(handle: PageWorkHandle) {
        delegate.forgetPageWork(handle)
    }

    fun policyFor(page: Int): PageActivationPolicy? = delegate.policyFor(page)?.let(::fromCore)

    fun requestNavigation(targetPage: Int, inputKind: NavigationInputKind): NavigationRequestResult =
        delegate.requestNavigation(targetPage, inputKind).toFacadeResult()

    fun requestFromRoute(
        route: String,
        inputKind: NavigationInputKind = NavigationInputKind.DEEP_ROUTE,
    ): NavigationRequestResult = delegate.requestFromRoute(route, inputKind).toFacadeResult()

    fun onPagerSettled(page: Int, token: TransitionToken? = delegate.token): Boolean =
        delegate.onPagerSettled(page, token)

    fun onRouteChanged(route: String): NavigationRequestResult? =
        delegate.onRouteChanged(route)?.toFacadeResult()

    fun activationFor(page: Int): PageActivation = delegate.activationFor(page)

    private fun fromCore(policy: ManagerPageActivationPolicy<TopLevelPage>) = PageActivationPolicy(
        page = policy.page,
        composePolicy = policy.composePolicy,
        dataPolicy = policy.dataPolicy,
        remotePriority = policy.remotePriority,
    )

    private fun ManagerNavigationRequestResult.toFacadeResult(): NavigationRequestResult = when (this) {
        is ManagerNavigationRequestResult.Accepted -> NavigationRequestResult.Accepted(token, state)
        is ManagerNavigationRequestResult.Rejected -> NavigationRequestResult.Rejected(state)
    }
}
