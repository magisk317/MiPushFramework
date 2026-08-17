package io.github.magisk317.mipush.feature.navigation

/** The five manager-owned top-level pages. */
enum class TopLevelPage(
    val index: Int,
    val route: String,
) {
    OVERVIEW(0, AppDestinations.Overview.ROUTE),
    APPLICATIONS(1, AppDestinations.AppsList.ROUTE),
    EVENTS(2, AppDestinations.EventsList.ROUTE),
    CONFIGURATIONS(3, AppDestinations.Configs.ROUTE),
    SETTINGS(4, AppDestinations.Settings.ROUTE),
    ;

    companion object {
        fun fromIndex(index: Int): TopLevelPage? = entries.firstOrNull { it.index == index }

        fun fromRoute(route: String): TopLevelPage? = when {
            route == AppDestinations.Overview.ROUTE -> OVERVIEW
            route == AppDestinations.AppsList.ROUTE || route.startsWith("${AppDestinations.AppDetails.ROUTE}/") -> APPLICATIONS
            route == AppDestinations.EventsList.ROUTE || route.startsWith("${AppDestinations.EventDetails.ROUTE}/") -> EVENTS
            route == AppDestinations.Configs.ROUTE || route.startsWith("${AppDestinations.ConfigsSearch.ROUTE}/") ||
                route.startsWith("${AppDestinations.ConfigEditor.ROUTE}/") -> CONFIGURATIONS
            route == AppDestinations.Settings.ROUTE || route.startsWith("${AppDestinations.SettingsSection.ROUTE}/") ||
                route == AppDestinations.StatusBarIconSettings.ROUTE -> SETTINGS
            else -> null
        }
    }
}

enum class NavigationInputKind {
    CLICK,
    SWIPE,
    RESTORE,
    DEEP_ROUTE,
}

enum class PageComposePolicy {
    CURRENT_ONLY,
    ADJACENT_PRECOMPOSE,
    KEEP_ALIVE,
}

enum class PageDataPolicy {
    NO_READS,
    CACHE_ONLY,
    CACHE_THEN_REFRESH,
    LOAD_ON_ACTIVE,
}

enum class RemotePriority {
    TRANSITION_CRITICAL,
    VISIBLE_PAGE,
    USER_ACTION,
    BACKGROUND_REFRESH,
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

    companion object {
        fun defaults(): Map<TopLevelPage, PageActivationPolicy> =
            TopLevelPage.entries.associateWith { PageActivationPolicy(it) }
    }
}

data class TransitionToken(
    val id: String,
    val sequence: Long,
    val sourcePage: Int,
    val targetPage: Int,
    val inputKind: NavigationInputKind,
    val startedAtNanos: Long,
)

data class NavigationState(
    val selectedPage: Int,
    val currentPage: Int,
    val settledPage: Int,
    val route: String,
)

data class PageActivation(
    val page: TopLevelPage,
    val isActive: Boolean,
    val dataPolicy: PageDataPolicy,
    val allowsBusinessRead: Boolean,
    val allowsRemoteCall: Boolean,
    val remotePriority: RemotePriority,
    /** True when this page is only composed as a lightweight adjacent shell. */
    val isShellOnly: Boolean = false,
)

enum class PageWorkKind {
    FIRST_BUSINESS_READ,
    REFRESH,
}

data class PageRequestScope(
    val token: TransitionToken,
    val generation: Long,
    val query: String = "",
    val filter: String = "",
    val userId: Int? = null,
    val route: String = token.targetPage.toString(),
)

/**
 * A manager-owned page operation. The callback must cancel only this operation; it must not
 * cancel work started by a newer scope.
 */
class PageWorkHandle internal constructor(
    val scope: PageRequestScope,
    val kind: PageWorkKind,
    val cancellable: Boolean,
    private val cancelCallback: () -> Unit,
) {
    private var cancelled = false

    val isCancelled: Boolean
        get() = cancelled

    internal fun cancelIfAllowed() {
        if (!cancellable || cancelled) return
        cancelled = true
        cancelCallback()
    }
}

sealed interface NavigationRequestResult {
    data class Accepted(
        val token: TransitionToken,
        val state: NavigationState,
    ) : NavigationRequestResult

    data class Rejected(val state: NavigationState) : NavigationRequestResult
}

/**
 * Manager-owned navigation state machine. It is deliberately independent of Compose and
 * NavController so click, gesture, restore and deep-route entry use the same token path.
 */
class PageActivationCoordinator(
    policies: Map<TopLevelPage, PageActivationPolicy> = PageActivationPolicy.defaults(),
    initialPage: Int = TopLevelPage.OVERVIEW.index,
    private val clockNanos: () -> Long = System::nanoTime,
    private val adjacentPrecompositionEnabled: Boolean = false,
) {
    private val policies = TopLevelPage.entries.associateWith { page -> policies[page] ?: PageActivationPolicy(page) }
    private var sequence = 0L
    private var generation = 0L
    private var currentToken: TransitionToken? = null
    private var navigationState: NavigationState = initialState(initialPage)
    private var lastSyncedRoute: String = navigationState.route
    private val pageWork = LinkedHashSet<PageWorkHandle>()

    val state: NavigationState
        get() = navigationState

    val activePage: TopLevelPage?
        get() = TopLevelPage.fromIndex(navigationState.settledPage)

    val token: TransitionToken?
        get() = currentToken

    /** Pages that may be composed as shell-only neighbors; disabled by default. */
    val adjacentShellPages: Set<TopLevelPage>
        get() {
            if (!adjacentPrecompositionEnabled) return emptySet()
            val current = navigationState.currentPage
            return setOf(current - 1, current + 1)
                .mapNotNull(TopLevelPage::fromIndex)
                .toSet()
        }

    fun shouldComposeShell(page: Int): Boolean =
        TopLevelPage.fromIndex(page)?.let { it in adjacentShellPages && navigationState.settledPage != it.index } == true

    /** Creates a generation-scoped request for active-page loading or refresh. */
    fun beginPageRequest(
        query: String = "",
        filter: String = "",
        userId: Int? = null,
        route: String = navigationState.route,
        token: TransitionToken? = currentToken,
    ): PageRequestScope? {
        val activeToken = currentToken ?: return null
        if (token?.id != activeToken.id || !activationFor(activeToken.targetPage).isActive) return null
        val scope = PageRequestScope(
            token = activeToken,
            generation = ++generation,
            query = query,
            filter = filter,
            userId = userId,
            route = route,
        )
        cancelSupersededWork(scope)
        return scope
    }

    /** Registers only active-page work; shells and inactive pages cannot register remote work. */
    fun registerPageWork(
        scope: PageRequestScope,
        kind: PageWorkKind,
        cancellable: Boolean = true,
        onCancel: () -> Unit,
    ): PageWorkHandle? {
        if (!isCurrent(scope) || !activationFor(scope.token.targetPage).isActive) return null
        val handle = PageWorkHandle(scope, kind, cancellable, onCancel)
        pageWork += handle
        return handle
    }

    fun isCurrent(scope: PageRequestScope): Boolean =
        currentToken?.id == scope.token.id && generation == scope.generation

    fun forgetPageWork(handle: PageWorkHandle) {
        pageWork.remove(handle)
    }

    /** Cancels cancellable work from an older token or superseded generation. */
    private fun cancelSupersededWork(newScope: PageRequestScope) {
        pageWork.toList().forEach { work ->
            if (work.scope.token.id != newScope.token.id || work.scope.generation < newScope.generation) {
                work.cancelIfAllowed()
                if (work.isCancelled || !work.cancellable) pageWork.remove(work)
            }
        }
    }

    fun policyFor(page: Int): PageActivationPolicy? = TopLevelPage.fromIndex(page)?.let(policies::get)

    /** Creates one token for each accepted request, regardless of the request's entry point. */
    fun requestNavigation(targetPage: Int, inputKind: NavigationInputKind): NavigationRequestResult {
        val target = TopLevelPage.fromIndex(targetPage) ?: return NavigationRequestResult.Rejected(navigationState)
        val sourcePage = navigationState.currentPage
        val token = TransitionToken(
            id = "navigation-${++sequence}",
            sequence = sequence,
            sourcePage = sourcePage,
            targetPage = target.index,
            inputKind = inputKind,
            startedAtNanos = clockNanos(),
        )
        currentToken = token
        generation++
        pageWork.toList().forEach { work ->
            if (work.scope.token.id != token.id) {
                work.cancelIfAllowed()
                if (work.isCancelled || !work.cancellable) pageWork.remove(work)
            }
        }
        navigationState = navigationState.copy(
            selectedPage = target.index,
            currentPage = target.index,
        )
        return NavigationRequestResult.Accepted(token, navigationState)
    }

    fun requestFromRoute(route: String, inputKind: NavigationInputKind = NavigationInputKind.DEEP_ROUTE): NavigationRequestResult {
        val page = TopLevelPage.fromRoute(route) ?: return NavigationRequestResult.Rejected(navigationState)
        return requestNavigation(page.index, inputKind)
    }

    /**
     * Accepts only a valid settled page belonging to the current request. Invalid or stale
     * settle notifications cannot overwrite the last valid navigation state.
     */
    fun onPagerSettled(page: Int, token: TransitionToken? = currentToken): Boolean {
        val settled = TopLevelPage.fromIndex(page) ?: return false
        val activeToken = currentToken
        if (activeToken != null && token?.id != activeToken.id) return false
        if (activeToken != null && settled.index != activeToken.targetPage) return false
        navigationState = navigationState.copy(
            selectedPage = settled.index,
            currentPage = settled.index,
            settledPage = settled.index,
            route = settled.route,
        )
        lastSyncedRoute = settled.route
        return true
    }

    /** Applies a route-controller change once; repeated effective route changes are ignored. */
    fun onRouteChanged(route: String): NavigationRequestResult? {
        val page = TopLevelPage.fromRoute(route) ?: return null
        if (page.index == navigationState.selectedPage) return null
        val result = requestNavigation(page.index, NavigationInputKind.DEEP_ROUTE)
        if (result is NavigationRequestResult.Accepted) {
            navigationState = navigationState.copy(route = route)
            lastSyncedRoute = route
        }
        return result
    }

    fun activationFor(page: Int): PageActivation {
        val topLevelPage = TopLevelPage.fromIndex(page)
            ?: return PageActivation(TopLevelPage.OVERVIEW, false, PageDataPolicy.NO_READS, false, false, RemotePriority.VISIBLE_PAGE)
        val policy = policies.getValue(topLevelPage)
        val isActive = navigationState.settledPage == topLevelPage.index
        val isShellOnly = shouldComposeShell(topLevelPage.index)
        return PageActivation(
            page = topLevelPage,
            isActive = isActive,
            dataPolicy = policy.dataPolicy,
            allowsBusinessRead = isActive && policy.allowsBusinessRead,
            allowsRemoteCall = isActive && policy.allowsRemoteCall,
            remotePriority = policy.remotePriority,
            isShellOnly = isShellOnly,
        )
    }

    private fun initialState(page: Int): NavigationState {
        val validPage = TopLevelPage.fromIndex(page) ?: TopLevelPage.OVERVIEW
        return NavigationState(validPage.index, validPage.index, validPage.index, validPage.route)
    }
}
