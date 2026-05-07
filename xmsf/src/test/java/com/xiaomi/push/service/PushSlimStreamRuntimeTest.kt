package com.xiaomi.push.service

import com.xiaomi.slim.Blob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushSlimStreamRuntimeTest {

    @Test
    fun `handshake requires challenge and emits config blob when present`() {
        val plan = PushSlimStreamRuntime.planHandshake(
            hasChallenge = true,
            hasConfigMessage = true
        )

        assertTrue(plan.valid)
        assertTrue(plan.shouldEmitConfigBlob)
    }

    @Test
    fun `secure secmsg payload is parsed as packet`() {
        val plan = PushSlimStreamRuntime.planPayloadDispatch(
            payloadType = 2,
            cmd = Blob.CMD_SECMSG,
            channelId = 2,
            subcmd = null
        )

        assertEquals(PushSlimPayloadAction.ParseSecurePacket, plan.action)
    }

    @Test
    fun `unknown payload type is ignored`() {
        val plan = PushSlimStreamRuntime.planPayloadDispatch(
            payloadType = 9,
            cmd = "MSG",
            channelId = 1,
            subcmd = null
        )

        assertEquals(PushSlimPayloadAction.IgnoreUnknown, plan.action)
        assertTrue(plan.shouldLogUnknownType)
    }

    @Test
    fun `conn blob write stays unencrypted`() {
        val plan = PushSlimStreamRuntime.planWrite(
            serializedSize = 100,
            cmd = Blob.CMD_CONN,
            currentCapacity = 2048
        )

        assertFalse(plan.shouldDrop)
        assertFalse(plan.shouldEncrypt)
        assertEquals("slim_write", plan.eventAction)
    }

    @Test
    fun `non conn blob write is encrypted`() {
        val plan = PushSlimStreamRuntime.planWrite(
            serializedSize = 100,
            cmd = Blob.CMD_BIND,
            currentCapacity = 2048
        )

        assertFalse(plan.shouldDrop)
        assertTrue(plan.shouldEncrypt)
    }

    @Test
    fun `oversized blob is dropped`() {
        val plan = PushSlimStreamRuntime.planWrite(
            serializedSize = 40000,
            cmd = "MSG",
            currentCapacity = 2048
        )

        assertTrue(plan.shouldDrop)
        assertFalse(plan.shouldEncrypt)
        assertEquals("slim_write_drop", plan.eventAction)
    }
}
