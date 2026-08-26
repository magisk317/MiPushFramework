package io.github.magisk317.mipush.service.runtime

import com.xiaomi.push.service.PushSlimInboundPlan
import com.xiaomi.push.service.PushSlimPingPlan
import io.github.magisk317.mipush.runtime.core.PushSlimConnectionPlanFactory

object PushSlimConnectionRuntime {
    @JvmStatic
    fun planInboundBlob(
        channelId: Int,
        cmd: String?,
    ): PushSlimInboundPlan =
        PushSlimConnectionPlanFactory.planInboundBlob(channelId, cmd.toSlimCommand())

    @JvmStatic
    fun planSendPing(): PushSlimPingPlan =
        PushSlimConnectionPlanFactory.planSendPing()
}
