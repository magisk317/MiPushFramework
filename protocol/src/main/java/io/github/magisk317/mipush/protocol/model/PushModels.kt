package io.github.magisk317.mipush.protocol.model

// --- Request Types ---

data class PushServiceCloseRequest(
    val packageName: String?,
    val channelId: String?,
    val userId: String?
)

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
    val messenger: android.os.Messenger? = null
)

// --- Connection States ---

enum class PushRegistrationState {
    NotRegistered,
    Registering,
    Registered,
    Unregistering,
    Unregistered,
    Failed
}

enum class PushConnectionState {
    Idle,
    Connecting,
    Connected,
    Disconnecting,
    Disconnected
}

enum class ConnectionStatus {
    connecting,
    connected,
    disconnected;

    companion object {
        @JvmStatic
        fun of(i: Int): ConnectionStatus = entries.getOrElse(i) { disconnected }
    }
}

enum class PushChannelState {
    Unbound,
    Binding,
    Bound,
    Unbinding,
    Closed,
    OpenFailed,
    Kicked
}

// --- Slim Flow Plans ---

data class PushSlimInboundPlan(
    val action: PushSlimInboundAction,
    val eventAction: String? = null,
    val shouldUpdateLastReceived: Boolean = false,
    val connectionState: PushConnectionState? = null,
    val connectionReason: String? = null,
    val disconnectReasonCode: Int? = null,
    val shouldLogUnknownType: Boolean = false
)

data class PushSlimPingPlan(
    val eventAction: String,
)

enum class PushSlimInboundAction {
    None,
    PingReceived,
    CloseReceived,
    ChallengeReceived,
    DeliverBlob,
    ParseSecurePacket,
    ParsePacket,
    IgnoreUnknown
}

data class PushSlimHandshakePlan(
    val isChallenge: Boolean = false,
    val eventAction: String = "",
    val shouldDrop: Boolean = false,
    val valid: Boolean = false,
    val shouldEmitConfigBlob: Boolean = false,
    val failureReason: String? = null
)

data class PushSlimPayloadPlan(
    val action: PushSlimPayloadAction,
    val eventAction: String = ""
)

enum class PushSlimPayloadAction {
    None,
    ParseSecure,
    ParseNormal,
    IgnoreUnknown,
    DeliverBlob,
    ParseSecurePacket,
    ParsePacket
}

data class PushSlimWritePlan(
    val eventAction: String? = "",
    val shouldDrop: Boolean = false,
    val requiredCapacity: Int = 0,
    val shouldEncrypt: Boolean = false
)

data class PushReconnectDelayPlan(
    val delayMs: Long,
    val nextState: PushReconnectState
)

// --- Socket & Reconnect Plans ---

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

// --- Business Logic Plans ---

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
    val clientStatus: Any? = null,
    val notifyType: Int?,
    val statusReasonCode: Int,
    val statusReasonMessage: String?,
    val statusErrorType: String?
)

data class PushRedirectPlan(
    val preferredHosts: List<String>,
    val shouldReconnect: Boolean
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

enum class PushServiceRegisterAppAction {
    Ignore,
    RegisterNow,
    ScheduleRegister
}

data class PushServiceRegisterAppPlan(
    val action: PushServiceRegisterAppAction = PushServiceRegisterAppAction.Ignore,
    val packageName: String? = null,
    val payload: ByteArray? = null,
    val shouldClearAccountCache: Boolean = false,
    val envType: Int = 0
)

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

enum class PushChannelOpenAction {
    NoAction,
    OpenFailedNoNetwork,
    ScheduleConnect,
    Bind,
    Rebind,
    AlreadyBinding,
    AlreadyBound
}

data class PushChannelOpenPlan(
    val action: PushChannelOpenAction,
    val state: PushChannelState,
    val sourceSuffix: String,
    val reasonCode: Int? = null,
    val reasonMessage: String? = null
)

data class PushChannelInfoUpdateTarget(
    val channelId: String?,
    val client: Any? = null,
    val reason: String
)

data class PushChannelInfoUpdateResult(
    val target: PushChannelInfoUpdateTarget,
    val updatedClientExtra: Boolean,
    val updatedCloudExtra: Boolean
)

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
