package io.github.magisk317.mipush.runtime.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushSlimPlanFactoryTest {
    @Test
    fun `handshake preserves challenge and config semantics`() {
        val ready = PushSlimStreamPlanFactory.planHandshake(
            hasChallenge = true,
            hasConfigMessage = true,
        )
        val invalid = PushSlimStreamPlanFactory.planHandshake(
            hasChallenge = false,
            hasConfigMessage = true,
        )

        assertTrue(ready.valid)
        assertTrue(ready.shouldEmitConfigBlob)
        assertNull(ready.failureReason)
        assertFalse(invalid.valid)
        assertTrue(invalid.shouldEmitConfigBlob)
        assertEquals("Invalid Connection", invalid.failureReason)
        assertEquals("slim_handshake_invalid", invalid.eventAction)
    }

    @Test
    fun `secure payload requires secmsg channel and no subcommand`() {
        val secure = PushSlimStreamPlanFactory.planPayloadDispatch(
            payloadType = 2,
            command = PushSlimCommand.SecureMessage,
            channelId = 2,
            hasSubcommand = false,
        )
        val withSubcommand = PushSlimStreamPlanFactory.planPayloadDispatch(
            payloadType = 2,
            command = PushSlimCommand.SecureMessage,
            channelId = 2,
            hasSubcommand = true,
        )
        val wrongChannel = PushSlimStreamPlanFactory.planPayloadDispatch(
            payloadType = 2,
            command = PushSlimCommand.SecureMessage,
            channelId = 1,
            hasSubcommand = false,
        )

        assertEquals(PushSlimPayloadAction.ParseSecurePacket, secure.action)
        assertEquals(PushSlimPayloadAction.DeliverBlob, withSubcommand.action)
        assertEquals(PushSlimPayloadAction.DeliverBlob, wrongChannel.action)
    }

    @Test
    fun `payload types retain blob packet and unknown actions`() {
        assertEquals(
            PushSlimPayloadAction.DeliverBlob,
            PushSlimStreamPlanFactory.planPayloadDispatch(
                payloadType = 1,
                command = PushSlimCommand.Other,
                channelId = 0,
                hasSubcommand = false,
            ).action,
        )
        assertEquals(
            PushSlimPayloadAction.ParsePacket,
            PushSlimStreamPlanFactory.planPayloadDispatch(
                payloadType = 3,
                command = PushSlimCommand.Other,
                channelId = 0,
                hasSubcommand = false,
            ).action,
        )
        val unknown = PushSlimStreamPlanFactory.planPayloadDispatch(
            payloadType = 9,
            command = PushSlimCommand.Other,
            channelId = 0,
            hasSubcommand = false,
        )
        assertEquals(PushSlimPayloadAction.IgnoreUnknown, unknown.action)
        assertTrue(unknown.shouldLogUnknownType)
        assertEquals("slim_unknown_payload_type", unknown.eventAction)
    }

    @Test
    fun `write plan preserves size capacity event and encryption rules`() {
        val connection = PushSlimStreamPlanFactory.planWrite(
            serializedSize = 100,
            command = PushSlimCommand.Connection,
            currentCapacity = 1024,
        )
        val ping = PushSlimStreamPlanFactory.planWrite(
            serializedSize = 100,
            command = PushSlimCommand.Ping,
            currentCapacity = 5000,
        )
        val oversized = PushSlimStreamPlanFactory.planWrite(
            serializedSize = 32_769,
            command = PushSlimCommand.Other,
            currentCapacity = 2048,
        )

        assertEquals(2048, connection.requiredCapacity)
        assertFalse(connection.shouldEncrypt)
        assertEquals("slim_write", connection.eventAction)
        assertEquals(112, ping.requiredCapacity)
        assertTrue(ping.shouldEncrypt)
        assertEquals("slim_ping_sent", ping.eventAction)
        assertTrue(oversized.shouldDrop)
        assertFalse(oversized.shouldEncrypt)
        assertEquals(2048, oversized.requiredCapacity)
    }

    @Test
    fun `inbound control commands retain ping close and challenge actions`() {
        val ping = PushSlimConnectionPlanFactory.planInboundBlob(0, PushSlimCommand.Ping)
        val close = PushSlimConnectionPlanFactory.planInboundBlob(0, PushSlimCommand.Close)
        val challenge = PushSlimConnectionPlanFactory.planInboundBlob(0, PushSlimCommand.Connection)

        assertEquals(PushSlimInboundAction.PingReceived, ping.action)
        assertTrue(ping.shouldUpdateLastReceived)
        assertEquals(PushSlimInboundAction.CloseReceived, close.action)
        assertEquals(PushConnectionState.Disconnected, close.connectionState)
        assertEquals(13, close.disconnectReasonCode)
        assertEquals(PushSlimInboundAction.ChallengeReceived, challenge.action)
    }

    @Test
    fun `non-control channels bypass command handling and send ping is observable`() {
        val inbound = PushSlimConnectionPlanFactory.planInboundBlob(5, PushSlimCommand.Close)
        val sendPing = PushSlimConnectionPlanFactory.planSendPing()

        assertEquals(PushSlimInboundAction.DeliverBlob, inbound.action)
        assertNull(inbound.eventAction)
        assertEquals("slim_ping_sent", sendPing.eventAction)
    }
}
