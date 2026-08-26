package io.github.magisk317.mipush.runtime.core

enum class PushSlimCommand {
    Ping,
    Close,
    Connection,
    SecureMessage,
    Other,
}

/** Platform-neutral SLIM stream decisions. Blob command strings are mapped by platform adapters. */
object PushSlimStreamPlanFactory {
    private const val MAX_BLOB_SIZE = 32_768
    private const val MIN_BUFFER_SIZE = 2_048
    private const val HEADER_SIZE = 8
    private const val CRC_SIZE = 4

    fun planHandshake(
        hasChallenge: Boolean,
        hasConfigMessage: Boolean,
    ): PushSlimHandshakePlan = PushSlimHandshakePlan(
        valid = hasChallenge,
        eventAction = if (hasChallenge) "slim_handshake_ready" else "slim_handshake_invalid",
        shouldEmitConfigBlob = hasConfigMessage,
        failureReason = if (hasChallenge) null else "Invalid Connection",
    )

    fun planPayloadDispatch(
        payloadType: Int,
        command: PushSlimCommand,
        channelId: Int,
        hasSubcommand: Boolean,
    ): PushSlimPayloadPlan = when (payloadType) {
        1 -> PushSlimPayloadPlan(PushSlimPayloadAction.DeliverBlob)
        2 -> {
            val parseSecurePacket = command == PushSlimCommand.SecureMessage &&
                (channelId == 2 || channelId == 3) &&
                !hasSubcommand
            PushSlimPayloadPlan(
                if (parseSecurePacket) {
                    PushSlimPayloadAction.ParseSecurePacket
                } else {
                    PushSlimPayloadAction.DeliverBlob
                },
            )
        }
        3 -> PushSlimPayloadPlan(PushSlimPayloadAction.ParsePacket)
        else -> PushSlimPayloadPlan(
            action = PushSlimPayloadAction.IgnoreUnknown,
            eventAction = "slim_unknown_payload_type",
            shouldLogUnknownType = true,
        )
    }

    fun planWrite(
        serializedSize: Int,
        command: PushSlimCommand,
        currentCapacity: Int,
    ): PushSlimWritePlan {
        if (serializedSize > MAX_BLOB_SIZE) {
            return PushSlimWritePlan(
                eventAction = "slim_write_drop",
                shouldDrop = true,
                requiredCapacity = currentCapacity,
                shouldEncrypt = false,
            )
        }
        val requiredCapacity = serializedSize + HEADER_SIZE + CRC_SIZE
        return PushSlimWritePlan(
            eventAction = if (command == PushSlimCommand.Ping) "slim_ping_sent" else "slim_write",
            shouldDrop = false,
            requiredCapacity = if (requiredCapacity > currentCapacity || currentCapacity > 4096) {
                requiredCapacity
            } else {
                currentCapacity.coerceAtLeast(MIN_BUFFER_SIZE)
            },
            shouldEncrypt = command != PushSlimCommand.Connection,
        )
    }
}

/** Platform-neutral handling of decoded inbound SLIM control blobs. */
object PushSlimConnectionPlanFactory {
    fun planInboundBlob(
        channelId: Int,
        command: PushSlimCommand,
    ): PushSlimInboundPlan {
        if (channelId != 0) {
            return PushSlimInboundPlan(action = PushSlimInboundAction.DeliverBlob)
        }
        return when (command) {
            PushSlimCommand.Ping -> PushSlimInboundPlan(
                action = PushSlimInboundAction.PingReceived,
                eventAction = "slim_ping_received",
                shouldUpdateLastReceived = true,
            )
            PushSlimCommand.Close -> PushSlimInboundPlan(
                action = PushSlimInboundAction.CloseReceived,
                eventAction = "slim_close_received",
                connectionState = PushConnectionState.Disconnected,
                connectionReason = "server_close_blob",
                disconnectReasonCode = 13,
            )
            PushSlimCommand.Connection -> PushSlimInboundPlan(
                action = PushSlimInboundAction.ChallengeReceived,
                eventAction = "slim_challenge_received",
            )
            PushSlimCommand.SecureMessage,
            PushSlimCommand.Other -> PushSlimInboundPlan(action = PushSlimInboundAction.None)
        }
    }

    fun planSendPing(): PushSlimPingPlan = PushSlimPingPlan(eventAction = "slim_ping_sent")
}
