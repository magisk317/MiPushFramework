package com.xiaomi.push.service

import org.junit.Assert.assertEquals
import org.junit.Test

class PushServiceIntentRuntimeTest {

    @Test
    fun `close plan targets all package channels when channel id missing`() {
        val plan = PushServiceIntentRuntime.resolveCloseChannelPlan(
            request = PushServiceCloseRequest(
                packageName = "com.example.app",
                channelId = null,
                userId = null
            ),
            packageChannelIds = listOf("5", "9")
        )

        assertEquals(PushServiceCloseAction.ClosePackageChannels, plan.action)
        assertEquals(listOf("5", "9"), plan.channelIds)
    }

    @Test
    fun `close plan targets whole channel when user id missing`() {
        val plan = PushServiceIntentRuntime.resolveCloseChannelPlan(
            request = PushServiceCloseRequest(
                packageName = "com.example.app",
                channelId = "5",
                userId = null
            ),
            packageChannelIds = emptyList()
        )

        assertEquals(PushServiceCloseAction.CloseChannel, plan.action)
        assertEquals(listOf("5"), plan.channelIds)
    }

    @Test
    fun `close plan targets single user channel when chid and user id present`() {
        val plan = PushServiceIntentRuntime.resolveCloseChannelPlan(
            request = PushServiceCloseRequest(
                packageName = "com.example.app",
                channelId = "5",
                userId = "user@xiaomi.com/res"
            ),
            packageChannelIds = emptyList()
        )

        assertEquals(PushServiceCloseAction.CloseSingleUserChannel, plan.action)
        assertEquals("user@xiaomi.com/res", plan.userId)
    }

    @Test
    fun `register app plan clears account cache only for env change outside xmsf package`() {
        val plan = PushServiceIntentRuntime.resolveRegisterAppPlan(
            packageName = "com.example.app",
            payload = byteArrayOf(1, 2, 3),
            envChanged = true,
            envType = 2,
            servicePackageName = "com.example.framework"
        )

        assertEquals(true, plan.shouldClearAccountCache)
        assertEquals(2, plan.envType)
    }

    @Test
    fun `mipush payload queues while waiting for bind`() {
        val plan = PushServiceIntentRuntime.decideMiPushPayloadDispatch(
            hasActiveChannel = true,
            clientStatus = PushClientsManager.ClientStatus.unbind,
            cacheIfUnavailable = true
        )

        assertEquals(PushServiceMiPushPayloadDispatchAction.QueueOnly, plan.action)
        assertEquals("mipush_payload_queue_wait_bind", plan.eventAction)
    }

    @Test
    fun `mipush payload sends immediately on bound channel`() {
        val plan = PushServiceIntentRuntime.decideMiPushPayloadDispatch(
            hasActiveChannel = true,
            clientStatus = PushClientsManager.ClientStatus.binded,
            cacheIfUnavailable = true
        )

        assertEquals(PushServiceMiPushPayloadDispatchAction.SendNow, plan.action)
    }

    @Test
    fun `reset connection requires stale bound matching client`() {
        val client = PushClientsManager.ClientLoginInfo().apply {
            security = "sec"
            status = PushClientsManager.ClientStatus.binded
        }

        val plan = PushServiceIntentRuntime.decideResetConnection(
            channelId = "5",
            requestedSecurity = "sec",
            client = client,
            connectionReadable = false
        )

        assertEquals(PushServiceResetConnectionAction.Reset, plan.action)
    }

    @Test
    fun `reset connection ignores mismatched security`() {
        val client = PushClientsManager.ClientLoginInfo().apply {
            security = "sec"
            status = PushClientsManager.ClientStatus.binded
        }

        val plan = PushServiceIntentRuntime.decideResetConnection(
            channelId = "5",
            requestedSecurity = "other",
            client = client,
            connectionReadable = false
        )

        assertEquals(PushServiceResetConnectionAction.Ignore, plan.action)
        assertEquals("security_mismatch", plan.reason)
    }
}
