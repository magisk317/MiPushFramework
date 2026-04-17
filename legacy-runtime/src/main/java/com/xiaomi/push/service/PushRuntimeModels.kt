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

data class PushReconnectState(
    val attempts: Int = 0,
    val shortLiveConnCount: Int = 0,
    val curDelay: Int = 500,
    val lastConnectTime: Long = 0L
)

data class PushReconnectDelayPlan(
    val delayMs: Long,
    val nextState: PushReconnectState,
    val eventAction: String = ""
)

enum class PushReconnectAction {
    SkipNoReconnect,
    SkipExistingJob,
    Immediate,
    Schedule,
    Skip,
    Delayed
}

data class PushReconnectAttemptPlan(
    val shouldAttempt: Boolean = true,
    val action: PushReconnectAction = PushReconnectAction.Skip,
    val nextState: PushReconnectState = PushReconnectState(),
    val eventAction: String,
    val delayMs: Long = 0,
    val shouldDumpNativeNetInfo: Boolean = false,
    val shouldRunConnectivityTest: Boolean = false
)

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
