package io.github.magisk317.mipush.manager.remote

import io.github.magisk317.mipush.manager.client.DefaultManagerRuntimeCallScheduler
import io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult
import io.github.magisk317.mipush.manager.client.ManagerRuntimeCallScheduler
import io.github.magisk317.mipush.manager.client.RemoteCallBudget
import io.github.magisk317.mipush.manager.client.RemotePriority

/** Stable manager-side budgets for page reads and user initiated operations. */
object PageRemoteCallPolicy {
    val firstScreen = RemoteCallBudget(RemotePriority.TRANSITION_CRITICAL, 8_000L, 250L)
    val visiblePage = RemoteCallBudget(RemotePriority.VISIBLE_PAGE, 8_000L, 750L)
    val userAction = RemoteCallBudget(RemotePriority.USER_ACTION, 12_000L, 1_500L)
    val backgroundRefresh = RemoteCallBudget(RemotePriority.BACKGROUND_REFRESH, 8_000L, 100L)

    fun budget(userInitiated: Boolean, backgroundRefresh: Boolean): RemoteCallBudget = when {
        userInitiated -> userAction
        backgroundRefresh -> this.backgroundRefresh
        else -> visiblePage
    }
}

/** One adapter used by page gateways; it rejects inactive work before it reaches Binder. */
class PageRemoteCallAdapter(
    private val scheduler: ManagerRuntimeCallScheduler = DefaultManagerRuntimeCallScheduler(),
    private val isActive: () -> Boolean = { true },
) {
    suspend fun <T> call(
        operation: String,
        budget: RemoteCallBudget,
        transitionToken: String? = null,
        isStale: () -> Boolean = { !isActive() },
        block: suspend () -> T,
    ): ManagerRuntimeCallResult<T> {
        if (!isActive()) return ManagerRuntimeCallResult.Stale
        return scheduler.call(
            operation = operation,
            budget = budget,
            transitionToken = transitionToken,
            isStale = { !isActive() || isStale() },
            block = block,
        )
    }

    fun cancelForTransition(token: String) = scheduler.cancelForTransition(token)
}
