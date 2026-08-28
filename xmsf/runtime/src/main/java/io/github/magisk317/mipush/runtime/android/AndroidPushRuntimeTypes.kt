package io.github.magisk317.mipush.runtime.android

import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.runtime.core.PushChannelState
import io.github.magisk317.mipush.runtime.core.PushConnectionState
import io.github.magisk317.mipush.runtime.core.PushRegistrationState

data class PushRuntimeSnapshot(
    val bridgeReady: Boolean,
    val executionReady: Boolean,
    val pendingBridgeIntentCount: Int,
    val connectionState: PushConnectionState,
    val trackedChannelCount: Int,
    val boundChannelCount: Int,
    val trackedRegistrationCount: Int,
    val registeredPackageCount: Int,
    val downstreamMessageCount: Long,
    val deliveredToAppCount: Long,
    val duplicateMessageCount: Long,
    val ackMessageCount: Long,
    val broadcastFallbackDeliveryCount: Long,
    val notificationCancelCount: Long,
    val notificationEventCount: Long,
    val channelEventCount: Long,
    val accountEventCount: Long,
    val lastPackageName: String?,
    val lastAction: String?,
    val lastChannelPackage: String?,
    val lastChannelState: PushChannelState?,
    val lastRegistrationPackage: String?,
    val lastRegistrationState: PushRegistrationState?
)

data class PushRuntimeCapabilities(
    val runtimeApiVersion: Int,
    val capabilities: List<String>
)

interface PushRuntimeBridgeHost {
    val context: Context

    fun onRuntimeStarted() {}

    fun onRuntimeStopped() {}

    fun processBridgeIntent(intent: Intent)
}
