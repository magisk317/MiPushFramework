package io.github.magisk317.mipush.manager.client

internal enum class ManagerRuntimeReconnectAction {
    ScheduleReconnect,
    RecoverRuntime,
    FailExhausted,
}

/** Pure decisions used by the client after its lock has established the current session state. */
internal object ManagerRuntimeRecoveryPolicy {
    private const val PER_USER_RANGE = 100_000

    fun reconnectAction(
        reconnectAttempt: Int,
        maxReconnectAttempts: Int,
        recoveryIssued: Boolean,
        recoveryAvailable: Boolean,
    ): ManagerRuntimeReconnectAction = when {
        reconnectAttempt < maxReconnectAttempts -> ManagerRuntimeReconnectAction.ScheduleReconnect
        !recoveryIssued && recoveryAvailable -> ManagerRuntimeReconnectAction.RecoverRuntime
        else -> ManagerRuntimeReconnectAction.FailExhausted
    }

    fun androidUserId(uid: Int): Int = uid.takeIf { it >= 0 }?.div(PER_USER_RANGE) ?: -1
}
