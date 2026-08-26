package io.github.magisk317.mipush.service.runtime

import com.xiaomi.push.service.PushSlimHandshakePlan
import com.xiaomi.push.service.PushSlimPayloadPlan
import com.xiaomi.push.service.PushSlimWritePlan
import com.xiaomi.slim.Blob
import io.github.magisk317.mipush.runtime.core.PushSlimCommand
import io.github.magisk317.mipush.runtime.core.PushSlimStreamPlanFactory

object PushSlimStreamRuntime {
    @JvmStatic
    fun planHandshake(
        hasChallenge: Boolean,
        hasConfigMessage: Boolean,
    ): PushSlimHandshakePlan =
        PushSlimStreamPlanFactory.planHandshake(hasChallenge, hasConfigMessage)

    @JvmStatic
    fun planPayloadDispatch(
        payloadType: Int,
        cmd: String?,
        channelId: Int,
        subcmd: String?,
    ): PushSlimPayloadPlan = PushSlimStreamPlanFactory.planPayloadDispatch(
        payloadType = payloadType,
        command = cmd.toSlimCommand(),
        channelId = channelId,
        hasSubcommand = !subcmd.isNullOrEmpty(),
    )

    @JvmStatic
    fun planWrite(
        serializedSize: Int,
        cmd: String?,
        currentCapacity: Int,
    ): PushSlimWritePlan = PushSlimStreamPlanFactory.planWrite(
        serializedSize = serializedSize,
        command = cmd.toSlimCommand(),
        currentCapacity = currentCapacity,
    )
}

internal fun String?.toSlimCommand(): PushSlimCommand = when (this) {
    Blob.CMD_PING -> PushSlimCommand.Ping
    Blob.CMD_CLOSE -> PushSlimCommand.Close
    Blob.CMD_CONN -> PushSlimCommand.Connection
    Blob.CMD_SECMSG -> PushSlimCommand.SecureMessage
    else -> PushSlimCommand.Other
}
