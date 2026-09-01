package io.github.magisk317.mipush.manager

enum class ManagerNavigationInputKind {
    CLICK,
    SWIPE,
    RESTORE,
    DEEP_ROUTE,
}

enum class ManagerPageComposePolicy {
    CURRENT_ONLY,
    ADJACENT_PRECOMPOSE,
    KEEP_ALIVE,
}

enum class ManagerPageDataPolicy {
    NO_READS,
    CACHE_ONLY,
    CACHE_THEN_REFRESH,
    LOAD_ON_ACTIVE,
}

enum class ManagerRemotePriority {
    TRANSITION_CRITICAL,
    VISIBLE_PAGE,
    USER_ACTION,
    BACKGROUND_REFRESH,
}

data class ManagerPageActivationPolicy<P>(
    val page: P,
    val composePolicy: ManagerPageComposePolicy = ManagerPageComposePolicy.KEEP_ALIVE,
    val dataPolicy: ManagerPageDataPolicy = ManagerPageDataPolicy.LOAD_ON_ACTIVE,
    val remotePriority: ManagerRemotePriority = ManagerRemotePriority.VISIBLE_PAGE,
) {
    val allowsBusinessRead: Boolean
        get() = dataPolicy != ManagerPageDataPolicy.NO_READS

    val allowsRemoteCall: Boolean
        get() = dataPolicy == ManagerPageDataPolicy.CACHE_THEN_REFRESH ||
            dataPolicy == ManagerPageDataPolicy.LOAD_ON_ACTIVE
}

data class ManagerTransitionToken(
    val id: String,
    val sequence: Long,
    val sourcePage: Int,
    val targetPage: Int,
    val inputKind: ManagerNavigationInputKind,
    val startedAtNanos: Long,
)

data class ManagerNavigationState(
    val selectedPage: Int,
    val currentPage: Int,
    val settledPage: Int,
    val route: String,
)

data class ManagerPageActivation<P>(
    val page: P,
    val isActive: Boolean,
    val dataPolicy: ManagerPageDataPolicy,
    val allowsBusinessRead: Boolean,
    val allowsRemoteCall: Boolean,
    val remotePriority: ManagerRemotePriority,
    val isShellOnly: Boolean = false,
)

enum class ManagerPageWorkKind {
    FIRST_BUSINESS_READ,
    REFRESH,
}

data class ManagerPageRequestScope(
    val token: ManagerTransitionToken,
    val generation: Long,
    val query: String = "",
    val filter: String = "",
    val userId: Int? = null,
    val route: String,
)

class ManagerPageWorkHandle internal constructor(
    val scope: ManagerPageRequestScope,
    val kind: ManagerPageWorkKind,
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

sealed interface ManagerNavigationRequestResult {
    data class Accepted(
        val token: ManagerTransitionToken,
        val state: ManagerNavigationState,
    ) : ManagerNavigationRequestResult

    data class Rejected(val state: ManagerNavigationState) : ManagerNavigationRequestResult
}

/** Pure navigation, activation, and request-generation state machine used by manager adapters. */
class ManagerPageActivationCoordinator<P>(
    pages: List<P>,
    private val routeOf: (P) -> String,
    private val pageForRoute: (String) -> P?,
    policies: Map<P, ManagerPageActivationPolicy<P>>,
    initialPage: Int = 0,
    private val clockNanos: () -> Long = System::nanoTime,
    private val adjacentPrecompositionEnabled: Boolean = false,
) {
    private val pages = pages.toList().also {
        require(it.isNotEmpty()) { "pages must not be empty" }
        require(it.distinct().size == it.size) { "pages must be unique" }
    }
    private val policies = this.pages.associateWith { page ->
        policies[page] ?: ManagerPageActivationPolicy(page)
    }
    private var sequence = 0L
    private var generation = 0L
    private var currentToken: ManagerTransitionToken? = null
    private var navigationState: ManagerNavigationState = initialState(initialPage)
    private val pageWork = LinkedHashSet<ManagerPageWorkHandle>()

    val state: ManagerNavigationState
        get() = navigationState

    val activePage: P?
        get() = pageAt(navigationState.settledPage)

    val token: ManagerTransitionToken?
        get() = currentToken

    val adjacentShellPages: Set<P>
        get() {
            if (!adjacentPrecompositionEnabled) return emptySet()
            val current = navigationState.currentPage
            return setOf(current - 1, current + 1)
                .mapNotNull(::pageAt)
                .toSet()
        }

    fun shouldComposeShell(page: Int): Boolean = pageAt(page)?.let {
        it in adjacentShellPages && navigationState.settledPage != page
    } == true

    fun beginPageRequest(
        query: String = "",
        filter: String = "",
        userId: Int? = null,
        route: String = navigationState.route,
        token: ManagerTransitionToken? = currentToken,
    ): ManagerPageRequestScope? {
        val activeToken = currentToken ?: return null
        if (token?.id != activeToken.id || !activationFor(activeToken.targetPage).isActive) return null
        val scope = ManagerPageRequestScope(
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

    fun registerPageWork(
        scope: ManagerPageRequestScope,
        kind: ManagerPageWorkKind,
        cancellable: Boolean = true,
        onCancel: () -> Unit,
    ): ManagerPageWorkHandle? {
        if (!isCurrent(scope) || !activationFor(scope.token.targetPage).isActive) return null
        return ManagerPageWorkHandle(scope, kind, cancellable, onCancel).also(pageWork::add)
    }

    fun isCurrent(scope: ManagerPageRequestScope): Boolean =
        currentToken?.id == scope.token.id && generation == scope.generation

    fun forgetPageWork(handle: ManagerPageWorkHandle) {
        pageWork.remove(handle)
    }

    fun policyFor(page: Int): ManagerPageActivationPolicy<P>? = pageAt(page)?.let(policies::get)

    fun requestNavigation(
        targetPage: Int,
        inputKind: ManagerNavigationInputKind,
    ): ManagerNavigationRequestResult {
        val target = pageAt(targetPage) ?: return ManagerNavigationRequestResult.Rejected(navigationState)
        val token = ManagerTransitionToken(
            id = "navigation-${++sequence}",
            sequence = sequence,
            sourcePage = navigationState.currentPage,
            targetPage = targetPage,
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
            selectedPage = targetPage,
            currentPage = targetPage,
        )
        return ManagerNavigationRequestResult.Accepted(token, navigationState)
    }

    fun requestFromRoute(
        route: String,
        inputKind: ManagerNavigationInputKind = ManagerNavigationInputKind.DEEP_ROUTE,
    ): ManagerNavigationRequestResult {
        val page = pageForRoute(route) ?: return ManagerNavigationRequestResult.Rejected(navigationState)
        return requestNavigation(indexOf(page), inputKind)
    }

    fun onPagerSettled(page: Int, token: ManagerTransitionToken? = currentToken): Boolean {
        val settled = pageAt(page) ?: return false
        val activeToken = currentToken
        if (activeToken != null && token?.id != activeToken.id) return false
        if (activeToken != null && page != activeToken.targetPage) return false
        navigationState = navigationState.copy(
            selectedPage = page,
            currentPage = page,
            settledPage = page,
            route = routeOf(settled),
        )
        return true
    }

    fun onRouteChanged(route: String): ManagerNavigationRequestResult? {
        val page = pageForRoute(route) ?: return null
        val pageIndex = indexOf(page)
        if (pageIndex == navigationState.selectedPage) return null
        return requestNavigation(pageIndex, ManagerNavigationInputKind.DEEP_ROUTE).also { result ->
            if (result is ManagerNavigationRequestResult.Accepted) {
                navigationState = navigationState.copy(route = route)
            }
        }
    }

    fun activationFor(page: Int): ManagerPageActivation<P> {
        val pageValue = pageAt(page) ?: pages.first()
        val policy = policies.getValue(pageValue)
        val isValidPage = pageAt(page) != null
        val isActive = isValidPage && navigationState.settledPage == page
        return ManagerPageActivation(
            page = pageValue,
            isActive = isActive,
            dataPolicy = if (isValidPage) policy.dataPolicy else ManagerPageDataPolicy.NO_READS,
            allowsBusinessRead = isActive && policy.allowsBusinessRead,
            allowsRemoteCall = isActive && policy.allowsRemoteCall,
            remotePriority = if (isValidPage) policy.remotePriority else ManagerRemotePriority.VISIBLE_PAGE,
            isShellOnly = isValidPage && shouldComposeShell(page),
        )
    }

    private fun cancelSupersededWork(newScope: ManagerPageRequestScope) {
        pageWork.toList().forEach { work ->
            if (work.scope.token.id != newScope.token.id || work.scope.generation < newScope.generation) {
                work.cancelIfAllowed()
                if (work.isCancelled || !work.cancellable) pageWork.remove(work)
            }
        }
    }

    private fun pageAt(index: Int): P? = pages.getOrNull(index)

    private fun indexOf(page: P): Int = pages.indexOf(page).also {
        check(it >= 0) { "page is not part of the coordinator" }
    }

    private fun initialState(page: Int): ManagerNavigationState {
        val validPage = pageAt(page) ?: pages.first()
        val index = indexOf(validPage)
        return ManagerNavigationState(index, index, index, routeOf(validPage))
    }
}
