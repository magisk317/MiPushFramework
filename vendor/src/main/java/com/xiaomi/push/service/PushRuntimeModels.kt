package com.xiaomi.push.service

import com.xiaomi.push.service.PushClientsManager.ClientStatus

// --- Foundation Core States ---
// Canonical definitions are in runtime-core (io.github.magisk317.mipush.runtime.core).
// These typealiases allow legacy-runtime code to use the short names without import changes.

typealias PushChannelState = io.github.magisk317.mipush.runtime.core.PushChannelState
typealias PushConnectionState = io.github.magisk317.mipush.runtime.core.PushConnectionState
typealias PushRegistrationState = io.github.magisk317.mipush.runtime.core.PushRegistrationState
typealias ConnectionStatus = io.github.magisk317.mipush.runtime.core.ConnectionStatus
typealias PushSlimInboundAction = io.github.magisk317.mipush.runtime.core.PushSlimInboundAction
typealias PushSlimInboundPlan = io.github.magisk317.mipush.runtime.core.PushSlimInboundPlan
typealias PushSlimPingPlan = io.github.magisk317.mipush.runtime.core.PushSlimPingPlan


// --- Socket Connection Plans ---

data class PushSocketHostSelectionPlan(
    val candidateHosts: List<String>,
    val eventAction: String
)

data class PushSocketFailurePlan(
    val shouldContinue: Boolean,
    val eventAction: String
)

data class PushShortConnectionPlan(
    val nextShortConnCount: Int,
    val shouldSinkDown: Boolean,
    val eventAction: String
)

enum class PushConnectionAttemptAction {
    Connect,
    SkipConnecting,
    SkipConnected
}

data class PushConnectionAttemptPlan(
    val action: PushConnectionAttemptAction,
    val eventAction: String
)

// --- Connection Status & Lifecycle Plans ---

enum class PushConnectionListenerEvent {
    None,
    Connected,
    Disconnected,
    Connecting,
    ReconnectionSuccessful,
    ReconnectionFailed,
    ConnectionClosed,
    ConnectionStarted
}

data class PushConnectionStatusPlan(
    val eventAction: String,
    val connectionState: PushConnectionState? = null,
    val shouldRemoveConnectingTimeout: Boolean = false,
    val listenerEvent: PushConnectionListenerEvent = PushConnectionListenerEvent.None,
    val warningMessage: String? = null
)

typealias PushReconnectState = io.github.magisk317.mipush.runtime.store.kmp.PushReconnectState
typealias PushReconnectDelayPlan = io.github.magisk317.mipush.runtime.store.kmp.PushReconnectDelayPlan
typealias PushReconnectAction = io.github.magisk317.mipush.runtime.store.kmp.PushReconnectAction
typealias PushReconnectAttemptPlan = io.github.magisk317.mipush.runtime.store.kmp.PushReconnectAttemptPlan

enum class PushCheckAliveAction {
    Ignore,
    Ping,
    ScheduleConnect,
    DisconnectAndReconnect,
    ResetConnection
}

data class PushCheckAlivePlan(
    val action: PushCheckAliveAction,
    val eventAction: String
)

enum class PushKickAction {
    Close,
    Rebind
}

data class PushKickPlan(
    val action: PushKickAction,
    val shouldCloseChannel: Boolean,
    val shouldScheduleRebind: Boolean,
    val shouldDeactivateClient: Boolean,
    val eventAction: String,
    val runtimeState: PushChannelState,
    val statusReasonCode: Int,
    val statusReasonMessage: String?,
    val statusErrorType: String?
)

enum class PushBindAction {
    Bound,
    Rebind,
    Deactivate,
    Ignore
}

data class PushBindResultPlan(
    val action: PushBindAction,
    val shouldScheduleRebind: Boolean,
    val shouldDeactivateClient: Boolean,
    val shouldReportInvalidSig: Boolean,
    val eventAction: String,
    val runtimeState: PushChannelState?,
    val clientStatus: ClientStatus?,
    val notifyType: Int?,
    val statusReasonCode: Int,
    val statusReasonMessage: String?,
    val statusErrorType: String?
)

data class PushRedirectPlan(
    val preferredHosts: List<String>,
    val shouldReconnect: Boolean
)

// --- Slim Flow Plans ---
// PushSlimInboundAction, PushSlimInboundPlan, PushSlimPingPlan are defined in runtime-core
// (PushSlimConnectionTypes.kt) and inherited via api(project(":runtime-core")).

typealias PushSlimHandshakePlan = io.github.magisk317.mipush.runtime.core.PushSlimHandshakePlan
typealias PushSlimPayloadPlan = io.github.magisk317.mipush.runtime.core.PushSlimPayloadPlan
typealias PushSlimPayloadAction = io.github.magisk317.mipush.runtime.core.PushSlimPayloadAction
typealias PushSlimWritePlan = io.github.magisk317.mipush.runtime.core.PushSlimWritePlan

// --- Service Intent Plans ---

data class PushServiceCloseRequest(
    val packageName: String?,
    val channelId: String?,
    val userId: String?
)

enum class PushServiceCloseAction {
    Ignore,
    ClosePackageChannels,
    CloseChannel,
    CloseSingleUserChannel
}

data class PushServiceClosePlan(
    val action: PushServiceCloseAction,
    val channelIds: List<String> = emptyList(),
    val userId: String? = null
)

data class PushServiceRegisterAppPlan(
    val action: PushServiceRegisterAppAction = PushServiceRegisterAppAction.Ignore,
    val packageName: String? = null,
    val payload: ByteArray? = null,
    val shouldClearAccountCache: Boolean = false,
    val envType: Int = 0
)

data class PushRegistrationPayloadRepairResult(
    val packageName: String,
    val appId: String,
    val appToken: String,
    val payload: ByteArray,
)

enum class PushServiceRegisterAppAction {
    Ignore,
    RegisterNow,
    ScheduleRegister
}

enum class PushServiceMiPushAppAction {
    Unsupported,
    SendMessage,
    Unregister
}

data class PushServiceMiPushAppPlan(
    val action: PushServiceMiPushAppAction,
    val packageName: String?,
    val payload: ByteArray?,
    val cacheMessage: Boolean
)

enum class PushServiceMiPushPayloadDispatchAction {
    Drop,
    QueueOnly,
    SendNow
}

data class PushServiceMiPushPayloadDispatchPlan(
    val action: PushServiceMiPushPayloadDispatchAction,
    val eventAction: String
)

enum class PushServiceResetConnectionAction {
    Ignore,
    Reset
}

data class PushServiceResetConnectionPlan(
    val action: PushServiceResetConnectionAction,
    val reason: String
)

// --- Open Channel specific ---

enum class PushChannelOpenAction {
    NoAction,
    OpenFailedNoNetwork,
    ScheduleConnect,
    Bind,
    Rebind,
    AlreadyBinding,
    AlreadyBound
}

data class PushChannelOpenRequest(
    val channelId: String?,
    val userId: String?,
    val token: String?,
    val packageName: String?,
    val clientExtra: String?,
    val cloudExtra: String?,
    val kick: Boolean,
    val security: String?,
    val session: String?,
    val authMethod: String?,
    val messenger: android.os.Messenger?
)

data class PushChannelOpenPlan(
    val action: PushChannelOpenAction,
    val state: PushChannelState,
    val sourceSuffix: String,
    val reasonCode: Int? = null,
    val reasonMessage: String? = null
)

// --- Registration & Channel Info Models ---

data class PushChannelInfoUpdateTarget(
    val channelId: String?,
    val client: PushClientsManager.ClientLoginInfo?,
    val reason: String
)

data class PushChannelInfoUpdateResult(
    val target: PushChannelInfoUpdateTarget,
    val updatedClientExtra: Boolean,
    val updatedCloudExtra: Boolean
)

// --- GSLB & Bucket Flow Models ---

data class PushGslbRequest(
    val requestUrl: String,
    val statsHostPort: String
)

data class PushBucketFetchPlan(
    val shouldRefresh: Boolean,
    val eventAction: String
)

data class PushBucketReconnectPlan(
    val shouldReconnect: Boolean,
    val eventAction: String,
    val connectionStateReason: String? = null
)

// --- Host Management Models ---

data class HostRequestUrlsPlan(
    val urls: List<String>
)

data class HostRefreshTargetsPlan(
    val targetHosts: List<String>
)

data class HostRequestThrottlePlan(
    val shouldRequest: Boolean,
    val nextTimestampMs: Long
)

// --- Batch 2: Event-driven plans (vendor detects, xmsf decides) ---

data class PushNetworkChangedPlan(
    val shouldResetConnection: Boolean,
    val shouldConnect: Boolean,
    val shouldDisconnect: Boolean,
    val shouldCheckAlive: Boolean,
    val shouldUpdateAlarm: Boolean,
    val eventAction: String
)

data class PushScreenStatePlan(
    val shouldStopAlarm: Boolean,
    val shouldUpdateAlarm: Boolean,
    val shouldConnect: Boolean,
    val eventAction: String
)

data class PushTimerPlan(
    val shouldStopAlarm: Boolean,
    val shouldRegisterPing: Boolean,
    val shouldConnect: Boolean,
    val shouldCheckAlive: Boolean,
    val eventAction: String
)

data class PushClientChangePlan(
    val shouldUpdateAlarm: Boolean,
    val shouldDisconnect: Boolean,
    val eventAction: String
)

data class PushPowerModePlan(
    val shouldDisconnect: Boolean,
    val shouldConnect: Boolean,
    val shouldUpdateAlarm: Boolean,
    val disconnectReason: Int,
    val eventAction: String
)

data class PushShouldReconnectPlan(
    val shouldReconnect: Boolean,
    val eventAction: String,
)

data class PushReconnectionFailurePlan(
    val shouldBroadcastUnavailable: Boolean,
    val shouldScheduleReconnect: Boolean,
    val eventAction: String
)

data class PushReconnectionSuccessPlan(
    val shouldBroadcastAvailable: Boolean,
    val shouldResetReconnectState: Boolean,
    val shouldRegisterAlarm: Boolean,
    val shouldBindAllClients: Boolean,
    val eventAction: String
)

data class PushConnectionClosedPlan(
    val shouldScheduleReconnect: Boolean,
    val eventAction: String
)

// --- Batch 2: Stock plan factory methods (default behavior, xmsf can override) ---

object PushConnectionPlanFactory {
    @JvmStatic
    fun planShouldReconnect(
        hasNetwork: Boolean,
        activeClientCount: Int,
        pushDisabled: Boolean,
        pushEnabled: Boolean,
        superPowerMode: Boolean,
        extremePowerMode: Boolean,
    ): PushShouldReconnectPlan {
        val shouldReconnect = hasNetwork && activeClientCount > 0 &&
            !pushDisabled && pushEnabled && !superPowerMode && !extremePowerMode
        return PushShouldReconnectPlan(
            shouldReconnect = shouldReconnect,
            eventAction = if (shouldReconnect) "should_reconnect" else "should_not_reconnect",
        )
    }

    @JvmStatic
    fun planNetworkChanged(
        hasNetwork: Boolean,
        isNetworkDeferred: Boolean,
        isConnected: Boolean,
        isConnecting: Boolean,
        shouldResetOnWifi: Boolean,
        shouldCheckAlive: Boolean
    ): PushNetworkChangedPlan {
        if (isNetworkDeferred) {
            return PushNetworkChangedPlan(
                shouldResetConnection = false, shouldConnect = false, shouldDisconnect = false,
                shouldCheckAlive = false, shouldUpdateAlarm = false,
                eventAction = "network_changed_deferred"
            )
        }
        if (hasNetwork) {
            val doReset = shouldResetOnWifi
            val doCheckAlive = !doReset && isConnected && shouldCheckAlive
            val doConnect = !isConnected && !isConnecting
            return PushNetworkChangedPlan(
                shouldResetConnection = doReset, shouldConnect = doConnect, shouldDisconnect = false,
                shouldCheckAlive = doCheckAlive, shouldUpdateAlarm = true,
                eventAction = when {
                    doReset -> "network_changed_wifi_reset"
                    doCheckAlive -> "network_changed_check_alive"
                    doConnect -> "network_changed_connect"
                    else -> "network_changed_noop"
                }
            )
        }
        return PushNetworkChangedPlan(
            shouldResetConnection = false, shouldConnect = false, shouldDisconnect = true,
            shouldCheckAlive = false, shouldUpdateAlarm = true,
            eventAction = "network_changed_disconnect"
        )
    }

    @JvmStatic
    fun planScreenState(isScreenOn: Boolean, shouldFalldown: Boolean, alarmAlive: Boolean, isConnected: Boolean, isConnecting: Boolean): PushScreenStatePlan {
        if (!isScreenOn) {
            return PushScreenStatePlan(
                shouldStopAlarm = shouldFalldown && alarmAlive, shouldUpdateAlarm = false,
                shouldConnect = false,
                eventAction = if (shouldFalldown && alarmAlive) "screen_off_falldown_stop" else "screen_off_noop"
            )
        }
        if (shouldFalldown) {
            return PushScreenStatePlan(shouldStopAlarm = false, shouldUpdateAlarm = false, shouldConnect = false, eventAction = "screen_on_falldown_skip")
        }
        return PushScreenStatePlan(
            shouldStopAlarm = false, shouldUpdateAlarm = true,
            shouldConnect = !isConnected && !isConnecting,
            eventAction = if (!isConnected && !isConnecting) "screen_on_connect" else "screen_on_update_alarm"
        )
    }

    @JvmStatic
    fun planTimer(shouldFalldown: Boolean, alarmAlive: Boolean, isConnected: Boolean, isConnecting: Boolean, shouldCheckAlive: Boolean): PushTimerPlan {
        if (shouldFalldown) {
            return PushTimerPlan(
                shouldStopAlarm = alarmAlive, shouldRegisterPing = false,
                shouldConnect = false, shouldCheckAlive = false,
                eventAction = if (alarmAlive) "timer_falldown_stop" else "timer_falldown_noop"
            )
        }
        val doConnect = !isConnected && !isConnecting
        return PushTimerPlan(
            shouldStopAlarm = false, shouldRegisterPing = true,
            shouldConnect = doConnect, shouldCheckAlive = !doConnect && shouldCheckAlive,
            eventAction = when { doConnect -> "timer_connect"; shouldCheckAlive -> "timer_check_alive"; else -> "timer_ping" }
        )
    }

    @JvmStatic
    fun planClientChange(activeClientCount: Int, shouldUpdateAlarm: Boolean): PushClientChangePlan {
        return PushClientChangePlan(
            shouldUpdateAlarm = shouldUpdateAlarm,
            shouldDisconnect = activeClientCount <= 0,
            eventAction = if (activeClientCount <= 0) "client_change_disconnect" else "client_change_update_alarm"
        )
    }

    @JvmStatic
    fun planPowerModeChanged(isExtremePowerMode: Boolean, isSuperPowerMode: Boolean, isConnected: Boolean): PushPowerModePlan {
        return when {
            isExtremePowerMode -> PushPowerModePlan(true, false, false, 23, "power_mode_extreme_disconnect")
            isSuperPowerMode -> PushPowerModePlan(true, false, true, 24, "power_mode_super_disconnect")
            else -> PushPowerModePlan(false, true, false, 0, "power_mode_off_connect")
        }
    }

    @JvmStatic
    fun planReconnectionFailure(shouldFalldown: Boolean): PushReconnectionFailurePlan {
        return PushReconnectionFailurePlan(
            shouldBroadcastUnavailable = true,
            shouldScheduleReconnect = !shouldFalldown,
            eventAction = if (shouldFalldown) "reconnect_failed_falldown" else "reconnect_failed_schedule"
        )
    }

    @JvmStatic
    fun planReconnectionSuccess(alarmAlive: Boolean, shouldFalldown: Boolean): PushReconnectionSuccessPlan {
        val shouldRegisterAlarm = !alarmAlive && !shouldFalldown
        return PushReconnectionSuccessPlan(
            shouldBroadcastAvailable = true, shouldResetReconnectState = true,
            shouldRegisterAlarm = shouldRegisterAlarm, shouldBindAllClients = true,
            eventAction = if (shouldRegisterAlarm) "reconnect_success_alarm_reactivated" else "reconnect_success"
        )
    }

    @JvmStatic
    fun planConnectionClosed(shouldFalldown: Boolean, reason: Int = 0, error: Exception? = null): PushConnectionClosedPlan {
        val failedConnection = error != null || reason == PushConstants.ERROR_READ_ERROR || reason == PushConstants.ERROR_PING_TIMEOUT
        return PushConnectionClosedPlan(
            shouldScheduleReconnect = !shouldFalldown || failedConnection,
            eventAction = if (shouldFalldown && !failedConnection) "connection_closed_falldown" else "connection_closed_schedule_reconnect"
        )
    }
}
