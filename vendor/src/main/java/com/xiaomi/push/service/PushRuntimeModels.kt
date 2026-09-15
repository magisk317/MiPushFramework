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

typealias PushSocketHostSelectionPlan = io.github.magisk317.mipush.runtime.core.PushSocketHostSelectionPlan
typealias PushSocketFailurePlan = io.github.magisk317.mipush.runtime.core.PushSocketFailurePlan
typealias PushShortConnectionPlan = io.github.magisk317.mipush.runtime.core.PushShortConnectionPlan

typealias PushConnectionAttemptAction = io.github.magisk317.mipush.runtime.core.PushConnectionAttemptAction
typealias PushConnectionAttemptPlan = io.github.magisk317.mipush.runtime.core.PushConnectionAttemptPlan

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

typealias PushReconnectState = io.github.magisk317.mipush.runtime.core.PushReconnectState
typealias PushReconnectDelayPlan = io.github.magisk317.mipush.runtime.core.PushReconnectDelayPlan
typealias PushReconnectAction = io.github.magisk317.mipush.runtime.core.PushReconnectAction
typealias PushReconnectAttemptPlan = io.github.magisk317.mipush.runtime.core.PushReconnectAttemptPlan

typealias PushCheckAliveAction = io.github.magisk317.mipush.runtime.core.PushCheckAliveAction
typealias PushCheckAlivePlan = io.github.magisk317.mipush.runtime.core.PushCheckAlivePlan

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

// --- Inbound control decision results ---

/**
 * Product decision for the stock 7.5.29 setting_app_notification_permission control
 * (com.xiaomi.push.service.c/d). [errorCode] mirrors the stock d.a/d.b wire codes:
 * 0 applied, 1 apply did not settle, 2 already matching, 3 already marked setted,
 * 4 target not installed, 5 apply failed, 6 invalid request. [reason] fills the
 * stock ack reason field only when the stock reason is non-empty.
 */
data class PushSettingAppNotificationPermissionResult(
    val errorCode: Long,
    val reason: String? = null,
)

// --- Event-driven connection plans ---
// Canonical definitions and decisions live in runtime-core commonMain.

typealias PushNetworkChangedPlan = io.github.magisk317.mipush.runtime.core.PushNetworkChangedPlan
typealias PushScreenStatePlan = io.github.magisk317.mipush.runtime.core.PushScreenStatePlan
typealias PushTimerPlan = io.github.magisk317.mipush.runtime.core.PushTimerPlan
typealias PushClientChangePlan = io.github.magisk317.mipush.runtime.core.PushClientChangePlan
typealias PushPowerModePlan = io.github.magisk317.mipush.runtime.core.PushPowerModePlan
typealias PushShouldReconnectPlan = io.github.magisk317.mipush.runtime.core.PushShouldReconnectPlan
typealias PushReconnectionFailurePlan = io.github.magisk317.mipush.runtime.core.PushReconnectionFailurePlan
typealias PushReconnectionSuccessPlan = io.github.magisk317.mipush.runtime.core.PushReconnectionSuccessPlan
typealias PushConnectionClosedPlan = io.github.magisk317.mipush.runtime.core.PushConnectionClosedPlan
typealias PushConnectionPlanFactory = io.github.magisk317.mipush.runtime.core.PushConnectionPlanFactory
