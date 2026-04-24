package io.github.magisk317.mipush.service.runtime
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.push.service.*
import com.xiaomi.slim.*

object PushSlimStreamRuntime {
    private const val MAX_BLOB_SIZE = 32_768
    private const val MIN_BUFFER_SIZE = 2_048
    private const val HEADER_SIZE = 8
    private const val CRC_SIZE = 4

    @JvmStatic
    fun planHandshake(
        hasChallenge: Boolean,
        hasConfigMessage: Boolean
    ): PushSlimHandshakePlan {
        return PushSlimHandshakePlan(
            valid = hasChallenge,
            eventAction = if (hasChallenge) "slim_handshake_ready" else "slim_handshake_invalid",
            shouldEmitConfigBlob = hasConfigMessage,
            failureReason = if (hasChallenge) null else "Invalid Connection"
        )
    }

    @JvmStatic
    fun planPayloadDispatch(
        payloadType: Int,
        cmd: String?,
        channelId: Int,
        subcmd: String?
    ): PushSlimPayloadPlan {
        return when (payloadType) {
            1 -> PushSlimPayloadPlan(PushSlimPayloadAction.DeliverBlob)
            2 -> {
                val shouldParseSecurePacket =
                    Blob.CMD_SECMSG == cmd &&
                        (channelId == 2 || channelId == 3) &&
                        subcmd.isNullOrEmpty()
                if (shouldParseSecurePacket) {
                    PushSlimPayloadPlan(PushSlimPayloadAction.ParseSecurePacket)
                } else {
                    PushSlimPayloadPlan(PushSlimPayloadAction.DeliverBlob)
                }
            }
            3 -> PushSlimPayloadPlan(PushSlimPayloadAction.ParsePacket)
            else -> PushSlimPayloadPlan(
                action = PushSlimPayloadAction.IgnoreUnknown,
                eventAction = "slim_unknown_payload_type",
            )
        }
    }

    @JvmStatic
    fun planWrite(
        serializedSize: Int,
        cmd: String?,
        currentCapacity: Int
    ): PushSlimWritePlan {
        if (serializedSize > MAX_BLOB_SIZE) {
            return PushSlimWritePlan(
                shouldDrop = true,
                requiredCapacity = currentCapacity,
                shouldEncrypt = false
            )
        }
        val requiredCapacity = serializedSize + HEADER_SIZE + CRC_SIZE
        return PushSlimWritePlan(
            shouldDrop = false,
            requiredCapacity = if (requiredCapacity > currentCapacity || currentCapacity > 4096) {
                requiredCapacity
            } else {
                currentCapacity.coerceAtLeast(MIN_BUFFER_SIZE)
            },
            shouldEncrypt = Blob.CMD_CONN != cmd
        )
    }
}
