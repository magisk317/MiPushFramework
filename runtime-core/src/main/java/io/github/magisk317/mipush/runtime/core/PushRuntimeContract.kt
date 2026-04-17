package io.github.magisk317.mipush.runtime.core

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
        fun of(i: Int): ConnectionStatus = entries[i]
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

data class PushRegistrationRecord(
    val packageName: String,
    val state: PushRegistrationState,
    val updatedAtMs: Long,
    val source: String,
    val reason: String? = null
)

object PushRuntimeCapability {
    const val BRIDGE_RUNTIME_SPINE = "bridge_runtime_spine"
    const val LEGACY_MAIN_SERVICE_COMPONENT = "legacy_main_service_component"
    const val REGISTRATION_RUNTIME = "registration_runtime"
    const val DOWNSTREAM_MESSAGE_PIPELINE = "downstream_message_pipeline"
    const val NOTIFICATION_POLICY_RUNTIME = "notification_policy_runtime"
    const val CHANNEL_LIFECYCLE_TRACKING = "channel_lifecycle_tracking"
    const val CONNECTION_SESSION_RUNTIME = "connection_session_runtime"
    const val STOCK_SURFACE_COMPATIBILITY = "stock_surface_compatibility"
    const val ACCOUNT_CLOUD_BRIDGE = "account_cloud_bridge"
}

data class PushRuntimeRegistrationDispatchResult(
    val frameworkRegistrationTriggered: Boolean = false,
    val pendingAppReplayCount: Int = 0,
    val processRegisterTaskTriggered: Boolean = false,
    val accountSyncTriggered: Boolean = false,
    val connectionEnsureTriggered: Boolean = false
)

data class PushRuntimeApplicationDispatchResult(
    val dispatched: Boolean = false,
    val deliveredToService: Boolean = false,
    val deliveredByBroadcastFallback: Boolean = false
)

data class PushConnectionRecord(
    val state: PushConnectionState,
    val updatedAtMs: Long,
    val source: String,
    val host: String? = null,
    val reason: String? = null
)

data class PushChannelRecord(
    val packageName: String?,
    val channelId: String,
    val userId: String?,
    val session: String?,
    val state: PushChannelState,
    val updatedAtMs: Long,
    val source: String,
    val reasonCode: Int? = null,
    val reasonMessage: String? = null
)

interface PushRuntimeExecutionHost {
    fun requestFrameworkRegistration(reason: String): Boolean

    fun requestApplicationRegistration(packageName: String, reason: String): Boolean

    fun processPendingRegisterTasks(reason: String): Boolean

    fun syncAccountAlias(reason: String): Boolean

    fun dispatchDownstreamPayload(
        payload: ByteArray,
        source: String,
        launchApp: Boolean
    ): PushRuntimeApplicationDispatchResult

    fun cancelNotificationForPayload(
        payload: ByteArray,
        notificationId: Int,
        notificationGroup: String?,
        source: String
    ): Boolean

    fun ensureConnection(reason: String): Boolean

    fun resetConnection(reason: String): Boolean
}
