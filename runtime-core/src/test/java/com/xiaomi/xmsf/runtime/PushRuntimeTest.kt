package com.xiaomi.xmsf.runtime

import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushRuntimeTest {
    @Test
    fun `network available dispatches register task and pending app replays`() {
        PushRuntime.clearStateForTests()
        val host = TestExecutionHost()
        PushRuntime.attachExecutionHost(host)
        try {
            PushRuntime.observeRegistrationRequest("com.example.pending", "test")

            val result = PushRuntime.handleNetworkAvailable("test")

            assertTrue(result.processRegisterTaskTriggered)
            assertTrue(result.frameworkRegistrationTriggered)
            assertTrue(result.connectionEnsureTriggered)
            assertEquals(1, result.pendingAppReplayCount)
            assertEquals(1, host.frameworkRegistrationReasons.size)
            assertEquals(1, host.processRegisterTaskReasons.size)
            assertEquals(1, host.connectionEnsureReasons.size)
            assertEquals(listOf("com.example.pending"), host.replayedPackages)
        } finally {
            PushRuntime.detachExecutionHost(host)
        }
    }

    @Test
    fun `account changed dispatches alias sync and pending app replays`() {
        PushRuntime.clearStateForTests()
        val host = TestExecutionHost()
        PushRuntime.attachExecutionHost(host)
        try {
            PushRuntime.observeRegistrationRequest("com.example.pending", "test")

            val result = PushRuntime.handleAccountChanged("test")

            assertTrue(result.accountSyncTriggered)
            assertEquals(1, result.pendingAppReplayCount)
            assertEquals(1, host.accountSyncReasons.size)
            assertEquals(listOf("com.example.pending"), host.replayedPackages)
        } finally {
            PushRuntime.detachExecutionHost(host)
        }
    }

    @Test
    fun `downstream dispatch and notification cancel update runtime counters`() {
        PushRuntime.clearStateForTests()
        val host = TestExecutionHost(
            downstreamDispatchResult = PushRuntimeApplicationDispatchResult(
                dispatched = true,
                deliveredToService = false,
                deliveredByBroadcastFallback = true
            ),
            cancelNotificationResult = true
        )
        PushRuntime.attachExecutionHost(host)
        try {
            val dispatch = PushRuntime.dispatchDownstreamPayload(
                packageName = "com.example.app",
                action = "SendMessage",
                messageId = "msg-1",
                payload = byteArrayOf(1, 2, 3),
                source = "test",
                launchApp = true
            )
            val cancelled = PushRuntime.cancelNotificationForPayload(
                packageName = "com.example.app",
                payload = byteArrayOf(1, 2, 3),
                notificationId = 7,
                notificationGroup = "group",
                source = "test"
            )

            val snapshot = PushRuntime.snapshot()
            assertTrue(dispatch.dispatched)
            assertTrue(cancelled)
            assertEquals(1, snapshot.deliveredToAppCount)
            assertEquals(1, snapshot.broadcastFallbackDeliveryCount)
            assertEquals(1, snapshot.notificationCancelCount)
        } finally {
            PushRuntime.detachExecutionHost(host)
        }
    }

    @Test
    fun `channel synchronization tracks bound channels and connection state`() {
        PushRuntime.clearStateForTests()

        PushRuntime.observeChannelState(
            packageName = "com.example.app",
            channelId = "5",
            userId = "u@example.com",
            session = "s1",
            state = PushChannelState.OpenFailed,
            source = "test"
        )
        PushRuntime.synchronizeChannels(
            connectionState = PushConnectionState.Connected,
            host = "resolver.msg.xiaomi.net",
            channels = listOf(
                PushChannelRecord(
                    packageName = "com.example.app",
                    channelId = "5",
                    userId = "u@example.com",
                    session = "s1",
                    state = PushChannelState.Bound,
                    updatedAtMs = 1L,
                    source = "seed"
                ),
                PushChannelRecord(
                    packageName = "com.example.other",
                    channelId = "9",
                    userId = "other@example.com",
                    session = "s2",
                    state = PushChannelState.Binding,
                    updatedAtMs = 1L,
                    source = "seed"
                )
            ),
            source = "test"
        )

        val snapshot = PushRuntime.snapshot()
        val records = PushRuntime.getChannelRecords()

        assertEquals(PushConnectionState.Connected, snapshot.connectionState)
        assertEquals(2, snapshot.trackedChannelCount)
        assertEquals(1, snapshot.boundChannelCount)
        assertTrue(records.any { it.packageName == "com.example.app" && it.state == PushChannelState.Bound })
        assertTrue(records.any { it.packageName == "com.example.other" && it.state == PushChannelState.Binding })
    }

    @Test
    fun `registration state transitions are tracked in snapshot`() {
        PushRuntime.clearStateForTests()

        PushRuntime.observeRegistrationRequest("com.example.app", "test")
        PushRuntime.observeRegistrationResult("com.example.app", success = true, source = "test")

        val snapshot = PushRuntime.snapshot()
        val record = PushRuntime.getRegistrationRecord("com.example.app")

        assertEquals(1, snapshot.trackedRegistrationCount)
        assertEquals(1, snapshot.registeredPackageCount)
        assertEquals("com.example.app", snapshot.lastRegistrationPackage)
        assertEquals(PushRegistrationState.Registered, snapshot.lastRegistrationState)
        assertEquals(PushRegistrationState.Registered, record?.state)
    }

    @Test
    fun `attachBridgeHost drains queued bridge intents`() {
        PushRuntime.clearStateForTests()
        val processedIntents = mutableListOf<Intent>()
        val host = testHost(processedIntents)

        PushRuntime.submitBridgeIntent(Intent("queued.first"))
        PushRuntime.submitBridgeIntent(Intent("queued.second"))

        val beforeAttach = PushRuntime.snapshot()
        assertFalse(beforeAttach.bridgeReady)
        assertFalse(beforeAttach.executionReady)
        assertEquals(PushConnectionState.Idle, beforeAttach.connectionState)
        assertEquals(2, beforeAttach.pendingBridgeIntentCount)

        PushRuntime.attachBridgeHost(host)
        try {
            val afterAttach = PushRuntime.snapshot()
            assertTrue(afterAttach.bridgeReady)
            assertEquals(0, afterAttach.pendingBridgeIntentCount)
            assertEquals(2, processedIntents.size)
        } finally {
            PushRuntime.detachBridgeHost(host)
        }
    }

    @Test
    fun `detachBridgeHost causes later bridge intents to be queued again`() {
        PushRuntime.clearStateForTests()
        val processedIntents = mutableListOf<Intent>()
        val host = testHost(processedIntents)

        PushRuntime.attachBridgeHost(host)
        PushRuntime.submitBridgeIntent(Intent("live.intent"))
        PushRuntime.detachBridgeHost(host)
        PushRuntime.submitBridgeIntent(Intent("queued.after.detach"))

        assertEquals(1, processedIntents.size)

        val detachedSnapshot = PushRuntime.snapshot()
        assertFalse(detachedSnapshot.bridgeReady)
        assertEquals(1, detachedSnapshot.pendingBridgeIntentCount)

        PushRuntime.attachBridgeHost(host)
        try {
            assertEquals(2, processedIntents.size)
        } finally {
            PushRuntime.detachBridgeHost(host)
        }
    }

    @Test
    fun `inbound dedupe increments duplicate and ack counters`() {
        PushRuntime.clearStateForTests()

        assertTrue(
            PushRuntime.observeInboundMessage(
                packageName = "com.example.app",
                action = "SendMessage",
                messageId = "id-1",
                source = "test"
            )
        )
        assertFalse(
            PushRuntime.observeInboundMessage(
                packageName = "com.example.app",
                action = "SendMessage",
                messageId = "id-1",
                source = "test"
            )
        )
        assertTrue(
            PushRuntime.observeInboundMessage(
                packageName = "com.example.app",
                action = "AckMessage",
                messageId = "ack-1",
                source = "test",
                isAck = true
            )
        )

        val snapshot = PushRuntime.snapshot()
        assertEquals(2, snapshot.downstreamMessageCount)
        assertEquals(1, snapshot.duplicateMessageCount)
        assertEquals(1, snapshot.ackMessageCount)
    }

    @Test
    fun `capabilities expose runtime spine contract`() {
        PushRuntime.clearStateForTests()
        val capabilities = PushRuntime.capabilities()

        assertEquals(3, capabilities.runtimeApiVersion)
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.BRIDGE_RUNTIME_SPINE))
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.LEGACY_MAIN_SERVICE_COMPONENT))
        assertFalse(capabilities.capabilities.contains("local_legacy_compat_sourceset"))
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.REGISTRATION_RUNTIME))
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.DOWNSTREAM_MESSAGE_PIPELINE))
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.NOTIFICATION_POLICY_RUNTIME))
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.CHANNEL_LIFECYCLE_TRACKING))
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.CONNECTION_SESSION_RUNTIME))
    }

    private fun testHost(processedIntents: MutableList<Intent>): PushRuntimeBridgeHost {
        return object : PushRuntimeBridgeHost {
            override val context: Context
                get() = throw UnsupportedOperationException("Context is not used in PushRuntime unit tests")

            override fun processBridgeIntent(intent: Intent) {
                processedIntents += intent
            }
        }
    }

    private class TestExecutionHost(
        private val downstreamDispatchResult: PushRuntimeApplicationDispatchResult = PushRuntimeApplicationDispatchResult(),
        private val cancelNotificationResult: Boolean = false
    ) : PushRuntimeExecutionHost {
        val frameworkRegistrationReasons = mutableListOf<String>()
        val replayedPackages = mutableListOf<String>()
        val processRegisterTaskReasons = mutableListOf<String>()
        val accountSyncReasons = mutableListOf<String>()
        val connectionEnsureReasons = mutableListOf<String>()
        val connectionResetReasons = mutableListOf<String>()
        val downstreamDispatches = mutableListOf<String>()
        val cancellationRequests = mutableListOf<String>()

        override fun requestFrameworkRegistration(reason: String): Boolean {
            frameworkRegistrationReasons += reason
            return true
        }

        override fun requestApplicationRegistration(packageName: String, reason: String): Boolean {
            replayedPackages += packageName
            return true
        }

        override fun processPendingRegisterTasks(reason: String): Boolean {
            processRegisterTaskReasons += reason
            return true
        }

        override fun syncAccountAlias(reason: String): Boolean {
            accountSyncReasons += reason
            return true
        }

        override fun dispatchDownstreamPayload(
            payload: ByteArray,
            source: String,
            launchApp: Boolean
        ): PushRuntimeApplicationDispatchResult {
            downstreamDispatches += "$source:$launchApp:${payload.size}"
            return downstreamDispatchResult
        }

        override fun cancelNotificationForPayload(
            payload: ByteArray,
            notificationId: Int,
            notificationGroup: String?,
            source: String
        ): Boolean {
            cancellationRequests += "$source:$notificationId:${notificationGroup ?: ""}:${payload.size}"
            return cancelNotificationResult
        }

        override fun ensureConnection(reason: String): Boolean {
            connectionEnsureReasons += reason
            return true
        }

        override fun resetConnection(reason: String): Boolean {
            connectionResetReasons += reason
            return true
        }
    }
}
