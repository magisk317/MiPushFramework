package io.github.magisk317.mipush.bridge

import com.xiaomi.push.service.PushChannelState
import com.xiaomi.push.service.PushClientsManager
import io.github.magisk317.mipush.runtime.PushRuntimeChannelTracker
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.core.PushRuntimeRegistrationChannelObservationSink
import io.github.magisk317.mipush.service.runtime.PushClientStatusSupport

internal class MiPushRuntimeChannelClientObservationAdapter(
    private val observationSink: PushRuntimeRegistrationChannelObservationSink,
) {
    fun onChannelEvent(packageName: String?, event: String, reason: String) {
        observationSink.observeChannelEvent(packageName, event, reason)
    }

    fun onChannelStateChanged(
        packageName: String?,
        chid: String,
        userId: String?,
        session: String?,
        state: PushChannelState,
        reason: String,
        reasonCode: Int?,
        reasonMsg: String?,
    ) {
        observationSink.observeChannelState(
            packageName = packageName,
            channelId = chid,
            userId = userId,
            session = session,
            state = state,
            source = reason,
            reasonCode = reasonCode,
            reasonMessage = reasonMsg,
            nowMs = System.currentTimeMillis(),
            androidUserId = Utils.requireValidUserId(Utils.myUserId()),
        )
    }

    fun syncChannelTracker(reason: String) {
        PushRuntimeChannelTracker.syncNow(reason)
    }

    fun shouldNotifyClient(
        client: PushClientsManager.ClientLoginInfo,
        type: Int,
        reasonCode: Int,
        reasonMessage: String?,
        errorType: String?,
    ): Boolean = PushClientStatusSupport.shouldNotifyClient(client, type, reasonCode, errorType)

    fun computeNotifyDelay(
        client: PushClientsManager.ClientLoginInfo,
        type: Int,
        reasonCode: Int,
        reasonMessage: String?,
        errorType: String?,
    ): Long = PushClientStatusSupport.computeNotifyDelay(client).toLong()

    fun onClientStatusChanged(
        client: Any,
        type: Int,
        reasonCode: Int,
        reasonMessage: String?,
        errorType: String?,
    ) {
        val info = client as? PushClientsManager.ClientLoginInfo ?: return
        PushClientStatusSupport.notifyClientStatus(
            info,
            type,
            reasonCode,
            reasonMessage,
            errorType,
        )
    }
}
