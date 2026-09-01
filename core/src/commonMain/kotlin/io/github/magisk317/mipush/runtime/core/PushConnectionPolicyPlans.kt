package io.github.magisk317.mipush.runtime.core

enum class PushConnectionListenerEvent {
    None,
    Connected,
    Disconnected,
    Connecting,
    ReconnectionSuccessful,
    ReconnectionFailed,
    ConnectionClosed,
    ConnectionStarted,
}

data class PushConnectionStatusPlan(
    val eventAction: String,
    val connectionState: PushConnectionState? = null,
    val shouldRemoveConnectingTimeout: Boolean = false,
    val listenerEvent: PushConnectionListenerEvent = PushConnectionListenerEvent.None,
    val warningMessage: String? = null,
)

/** Platform-neutral connection status transition decisions. */
object PushConnectionStatusPlanFactory {
    private const val STATUS_CONNECTING = 0
    private const val STATUS_CONNECTED = 1
    private const val STATUS_DISCONNECTED = 2

    fun planStatusChange(
        currentStatus: Int,
        newStatus: Int,
    ): PushConnectionStatusPlan = when (newStatus) {
        STATUS_CONNECTED -> PushConnectionStatusPlan(
            eventAction = "connection_status_connected",
            shouldRemoveConnectingTimeout = true,
            listenerEvent = PushConnectionListenerEvent.ReconnectionSuccessful,
            warningMessage = if (currentStatus != STATUS_CONNECTING) {
                "try set connected while not connecting."
            } else {
                null
            },
        )
        STATUS_CONNECTING -> PushConnectionStatusPlan(
            eventAction = "connection_status_connecting",
            listenerEvent = PushConnectionListenerEvent.ConnectionStarted,
            warningMessage = if (currentStatus != STATUS_DISCONNECTED) {
                "try set connecting while not disconnected."
            } else {
                null
            },
        )
        STATUS_DISCONNECTED -> PushConnectionStatusPlan(
            eventAction = "connection_status_disconnected",
            shouldRemoveConnectingTimeout = true,
            listenerEvent = when (currentStatus) {
                STATUS_CONNECTING -> PushConnectionListenerEvent.ReconnectionFailed
                STATUS_CONNECTED -> PushConnectionListenerEvent.ConnectionClosed
                else -> PushConnectionListenerEvent.None
            },
        )
        else -> PushConnectionStatusPlan(eventAction = "connection_status_unknown")
    }
}

enum class PushBindingState {
    Unbound,
    Binding,
    Bound,
    Other,
}

data class PushChannelRebindPlan(
    val shouldRebind: Boolean,
    val sessionChanged: Boolean,
    val securityChanged: Boolean,
)

enum class PushChannelOpenAction {
    NoAction,
    OpenFailedNoNetwork,
    ScheduleConnect,
    Bind,
    Rebind,
    AlreadyBinding,
    AlreadyBound,
}

data class PushChannelOpenPlan(
    val action: PushChannelOpenAction,
    val state: PushChannelState,
    val sourceSuffix: String,
    val reasonCode: Int? = null,
    val reasonMessage: String? = null,
)

/** Platform-neutral channel identity and open/rebind decisions. */
object PushChannelOpenPlanFactory {
    fun planRebind(
        channelId: String?,
        existingSession: String?,
        requestedSession: String?,
        existingSecurity: String?,
        requestedSecurity: String?,
    ): PushChannelRebindPlan {
        if (channelId.isNullOrBlank()) {
            return PushChannelRebindPlan(false, false, false)
        }
        val sessionChanged = !existingSession.isNullOrEmpty() && existingSession != requestedSession
        val securityChanged = requestedSecurity != existingSecurity
        return PushChannelRebindPlan(
            shouldRebind = sessionChanged || securityChanged,
            sessionChanged = sessionChanged,
            securityChanged = securityChanged,
        )
    }

    fun planOpen(
        hasNetwork: Boolean,
        isConnected: Boolean,
        bindingState: PushBindingState,
        shouldRebind: Boolean,
    ): PushChannelOpenPlan = when {
        !hasNetwork -> PushChannelOpenPlan(
            action = PushChannelOpenAction.OpenFailedNoNetwork,
            state = PushChannelState.OpenFailed,
            sourceSuffix = "no_network",
            reasonCode = 2,
            reasonMessage = "network_unavailable",
        )
        !isConnected -> PushChannelOpenPlan(
            action = PushChannelOpenAction.ScheduleConnect,
            state = PushChannelState.Binding,
            sourceSuffix = "schedule_connect",
        )
        bindingState == PushBindingState.Unbound -> PushChannelOpenPlan(
            action = PushChannelOpenAction.Bind,
            state = PushChannelState.Binding,
            sourceSuffix = "bind",
        )
        shouldRebind -> PushChannelOpenPlan(
            action = PushChannelOpenAction.Rebind,
            state = PushChannelState.Binding,
            sourceSuffix = "rebind",
        )
        bindingState == PushBindingState.Binding -> PushChannelOpenPlan(
            action = PushChannelOpenAction.AlreadyBinding,
            state = PushChannelState.Binding,
            sourceSuffix = "already_binding",
        )
        bindingState == PushBindingState.Bound -> PushChannelOpenPlan(
            action = PushChannelOpenAction.AlreadyBound,
            state = PushChannelState.Bound,
            sourceSuffix = "already_bound",
        )
        else -> PushChannelOpenPlan(
            action = PushChannelOpenAction.NoAction,
            state = PushChannelState.Unbound,
            sourceSuffix = "noop",
        )
    }
}

enum class PushServiceResetConnectionAction {
    Ignore,
    Reset,
}

data class PushServiceResetConnectionPlan(
    val action: PushServiceResetConnectionAction,
    val reason: String,
)

object PushServiceResetConnectionPlanFactory {
    fun planReset(
        hasChannelId: Boolean,
        hasClient: Boolean,
        securityMatches: Boolean,
        isClientBound: Boolean,
        connectionReadable: Boolean,
    ): PushServiceResetConnectionPlan = when {
        !hasChannelId -> PushServiceResetConnectionPlan(
            PushServiceResetConnectionAction.Ignore,
            "missing_channel",
        )
        !hasClient -> PushServiceResetConnectionPlan(
            PushServiceResetConnectionAction.Ignore,
            "missing_client",
        )
        !securityMatches -> PushServiceResetConnectionPlan(
            PushServiceResetConnectionAction.Ignore,
            "security_mismatch",
        )
        !isClientBound -> PushServiceResetConnectionPlan(
            PushServiceResetConnectionAction.Ignore,
            "client_not_bound",
        )
        connectionReadable -> PushServiceResetConnectionPlan(
            PushServiceResetConnectionAction.Ignore,
            "connection_alive",
        )
        else -> PushServiceResetConnectionPlan(
            PushServiceResetConnectionAction.Reset,
            "stale_connection",
        )
    }
}

data class PushRedirectPlan(
    val preferredHosts: List<String>,
    val shouldReconnect: Boolean,
)

object PushRedirectPlanFactory {
    fun resolve(
        hostsText: String?,
        itemSeparator: String,
    ): PushRedirectPlan {
        val preferredHosts = if (hostsText == null || itemSeparator.isEmpty()) {
            emptyList()
        } else {
            hostsText.split(itemSeparator).map(String::trim).filter(String::isNotEmpty)
        }
        return PushRedirectPlan(
            preferredHosts = preferredHosts,
            shouldReconnect = preferredHosts.isNotEmpty(),
        )
    }
}

object PushNetworkCheckPlanFactory {
    const val CONNECTIVITY_CHECK_INTERVAL_MS: Long = 1_800_000L

    fun shouldRunConnectivityTest(
        activeCount: Int,
        nowMs: Long,
        lastCheckTimeMs: Long,
        allowStats: Boolean,
        testHostsCount: Int,
        intervalMs: Long = CONNECTIVITY_CHECK_INTERVAL_MS,
    ): Boolean = (activeCount <= 0 || nowMs - lastCheckTimeMs >= intervalMs) &&
        allowStats && testHostsCount > 0

    fun extractGateway(routeLine: String?): String? {
        if (routeLine.isNullOrEmpty() || !routeLine.startsWith("default via")) {
            return null
        }
        return routeLine
            .split(' ')
            .firstOrNull { token -> IPV4_PATTERN.matches(token) }
    }

    private val IPV4_PATTERN = Regex("([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})")
}
