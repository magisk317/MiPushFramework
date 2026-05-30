package io.github.magisk317.mipush.runtime.android

import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.runtime.core.PushChannelRecord
import io.github.magisk317.mipush.runtime.core.PushChannelState
import io.github.magisk317.mipush.runtime.core.PushConnectionState
import io.github.magisk317.mipush.runtime.core.PushRegistrationState
import io.github.magisk317.mipush.runtime.core.PushRuntimeApplicationDispatchResult
import io.github.magisk317.mipush.runtime.core.PushRuntimeCapability
import io.github.magisk317.mipush.runtime.core.PushRuntimeExecutionHost
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushRuntimeTest {
    @Test
    fun `network available dispatches register task and pending app replays`() {
        AndroidPushRuntime.clearStateForTests()
        val host = TestExecutionHost()
        AndroidPushRuntime.attachExecutionHost(host)
        try {
            AndroidPushRuntime.observeRegistrationRequest("com.example.pending", "test")

            val result = AndroidPushRuntime.handleNetworkAvailable("test")

            assertTrue(result.processRegisterTaskTriggered)
            assertTrue(result.frameworkRegistrationTriggered)
            assertTrue(result.connectionEnsureTriggered)
            assertEquals(1, result.pendingAppReplayCount)
            assertEquals(1, host.frameworkRegistrationReasons.size)
            assertEquals(1, host.processRegisterTaskReasons.size)
            assertEquals(1, host.connectionEnsureReasons.size)
            assertEquals(listOf("com.example.pending"), host.replayedPackages)
        } finally {
            AndroidPushRuntime.detachExecutionHost(host)
        }
    }

    @Test
    fun `account changed dispatches alias sync and pending app replays`() {
        AndroidPushRuntime.clearStateForTests()
        val host = TestExecutionHost()
        AndroidPushRuntime.attachExecutionHost(host)
        try {
            AndroidPushRuntime.observeRegistrationRequest("com.example.pending", "test")

            val result = AndroidPushRuntime.handleAccountChanged("test")

            assertTrue(result.accountSyncTriggered)
            assertEquals(1, result.pendingAppReplayCount)
            assertEquals(1, host.accountSyncReasons.size)
            assertEquals(listOf("com.example.pending"), host.replayedPackages)
        } finally {
            AndroidPushRuntime.detachExecutionHost(host)
        }
    }

    @Test
    fun `downstream dispatch and notification cancel update runtime counters`() {
        AndroidPushRuntime.clearStateForTests()
        val host = TestExecutionHost(
            downstreamDispatchResult = PushRuntimeApplicationDispatchResult(
                dispatched = true,
                deliveredToService = false,
                deliveredByBroadcastFallback = true
            ),
            cancelNotificationResult = true
        )
        AndroidPushRuntime.attachExecutionHost(host)
        try {
            val dispatch = AndroidPushRuntime.dispatchDownstreamPayload(
                packageName = "com.example.app",
                action = "SendMessage",
                messageId = "msg-1",
                payload = byteArrayOf(1, 2, 3),
                source = "test",
                launchApp = true
            )
            val cancelled = AndroidPushRuntime.cancelNotificationForPayload(
                packageName = "com.example.app",
                payload = byteArrayOf(1, 2, 3),
                notificationId = 7,
                notificationGroup = "group",
                source = "test"
            )

            val snapshot = AndroidPushRuntime.snapshot()
            assertTrue(dispatch.dispatched)
            assertTrue(cancelled)
            assertEquals(1, snapshot.deliveredToAppCount)
            assertEquals(1, snapshot.broadcastFallbackDeliveryCount)
            assertEquals(1, snapshot.notificationCancelCount)
        } finally {
            AndroidPushRuntime.detachExecutionHost(host)
        }
    }

    @Test
    fun `channel synchronization tracks bound channels and connection state`() {
        AndroidPushRuntime.clearStateForTests()

        AndroidPushRuntime.observeChannelState(
            packageName = "com.example.app",
            channelId = "5",
            userId = "u@example.com",
            session = "s1",
            state = PushChannelState.OpenFailed,
            source = "test"
        )
        AndroidPushRuntime.synchronizeChannels(
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

        val snapshot = AndroidPushRuntime.snapshot()
        val records = AndroidPushRuntime.getChannelRecords()

        assertEquals(PushConnectionState.Connected, snapshot.connectionState)
        assertEquals(2, snapshot.trackedChannelCount)
        assertEquals(1, snapshot.boundChannelCount)
        assertTrue(records.any { it.packageName == "com.example.app" && it.state == PushChannelState.Bound })
        assertTrue(records.any { it.packageName == "com.example.other" && it.state == PushChannelState.Binding })
    }

    @Test
    fun `registration state transitions are tracked in snapshot`() {
        AndroidPushRuntime.clearStateForTests()

        AndroidPushRuntime.observeRegistrationRequest("com.example.app", "test")
        AndroidPushRuntime.observeRegistrationResult("com.example.app", success = true, source = "test")

        val snapshot = AndroidPushRuntime.snapshot()
        val record = AndroidPushRuntime.getRegistrationRecord("com.example.app")

        assertEquals(1, snapshot.trackedRegistrationCount)
        assertEquals(1, snapshot.registeredPackageCount)
        assertEquals("com.example.app", snapshot.lastRegistrationPackage)
        assertEquals(PushRegistrationState.Registered, snapshot.lastRegistrationState)
        assertEquals(PushRegistrationState.Registered, record?.state)
    }

    @Test
    fun `attachBridgeHost drains queued bridge intents`() {
        AndroidPushRuntime.clearStateForTests()
        val processedIntents = mutableListOf<Intent>()
        val host = testHost(processedIntents)

        AndroidPushRuntime.submitBridgeIntent(Intent("queued.first"))
        AndroidPushRuntime.submitBridgeIntent(Intent("queued.second"))

        val beforeAttach = AndroidPushRuntime.snapshot()
        assertFalse(beforeAttach.bridgeReady)
        assertFalse(beforeAttach.executionReady)
        assertEquals(PushConnectionState.Idle, beforeAttach.connectionState)
        assertEquals(2, beforeAttach.pendingBridgeIntentCount)

        AndroidPushRuntime.attachBridgeHost(host)
        try {
            val afterAttach = AndroidPushRuntime.snapshot()
            assertTrue(afterAttach.bridgeReady)
            assertEquals(0, afterAttach.pendingBridgeIntentCount)
            assertEquals(2, processedIntents.size)
        } finally {
            AndroidPushRuntime.detachBridgeHost(host)
        }
    }

    @Test
    fun `detachBridgeHost causes later bridge intents to be queued again`() {
        AndroidPushRuntime.clearStateForTests()
        val processedIntents = mutableListOf<Intent>()
        val host = testHost(processedIntents)

        AndroidPushRuntime.attachBridgeHost(host)
        AndroidPushRuntime.submitBridgeIntent(Intent("live.intent"))
        AndroidPushRuntime.detachBridgeHost(host)
        AndroidPushRuntime.submitBridgeIntent(Intent("queued.after.detach"))

        assertEquals(1, processedIntents.size)

        val detachedSnapshot = AndroidPushRuntime.snapshot()
        assertFalse(detachedSnapshot.bridgeReady)
        assertEquals(1, detachedSnapshot.pendingBridgeIntentCount)

        AndroidPushRuntime.attachBridgeHost(host)
        try {
            assertEquals(2, processedIntents.size)
        } finally {
            AndroidPushRuntime.detachBridgeHost(host)
        }
    }

    @Test
    fun `inbound dedupe increments duplicate and ack counters`() {
        AndroidPushRuntime.clearStateForTests()

        assertTrue(
            AndroidPushRuntime.observeInboundMessage(
                packageName = "com.example.app",
                action = "SendMessage",
                messageId = "id-1",
                source = "test"
            )
        )
        assertFalse(
            AndroidPushRuntime.observeInboundMessage(
                packageName = "com.example.app",
                action = "SendMessage",
                messageId = "id-1",
                source = "test"
            )
        )
        assertTrue(
            AndroidPushRuntime.observeInboundMessage(
                packageName = "com.example.app",
                action = "AckMessage",
                messageId = "ack-1",
                source = "test",
                isAck = true
            )
        )

        val snapshot = AndroidPushRuntime.snapshot()
        assertEquals(2, snapshot.downstreamMessageCount)
        assertEquals(1, snapshot.duplicateMessageCount)
        assertEquals(1, snapshot.ackMessageCount)
    }

    @Test
    fun `message id dedupe window remains active for sixty seconds`() {
        AndroidPushRuntime.clearStateForTests()

        assertTrue(
            AndroidPushRuntime.observeInboundMessage(
                packageName = "com.example.app",
                action = "SendMessage",
                messageId = "id-window",
                source = "test",
                nowMs = 1_000L
            )
        )
        assertFalse(
            AndroidPushRuntime.observeInboundMessage(
                packageName = "com.example.app",
                action = "SendMessage",
                messageId = "id-window",
                source = "test",
                nowMs = 31_000L
            )
        )
        assertTrue(
            AndroidPushRuntime.observeInboundMessage(
                packageName = "com.example.app",
                action = "SendMessage",
                messageId = "id-window",
                source = "test",
                nowMs = 92_000L
            )
        )
    }

    @Test
    fun `force trigger ignores reentrant application registration dispatch`() {
        AndroidPushRuntime.clearStateForTests()
        var nestedDispatchResult = true
        val host = TestExecutionHost(
            onApplicationRegistration = { packageName, _ ->
                nestedDispatchResult = AndroidPushRuntime.forceTriggerRegistration(
                    packageName,
                    "test:nested",
                    "reentrant"
                )
                true
            }
        )
        AndroidPushRuntime.attachExecutionHost(host)
        try {
            val dispatched = AndroidPushRuntime.forceTriggerRegistration("com.example.app", "test", "manual")

            assertTrue(dispatched)
            assertFalse(nestedDispatchResult)
            assertEquals(listOf("com.example.app"), host.replayedPackages)
        } finally {
            AndroidPushRuntime.detachExecutionHost(host)
        }
    }

    @Test
    fun `capabilities expose runtime spine contract`() {
        AndroidPushRuntime.clearStateForTests()
        val capabilities = AndroidPushRuntime.capabilities()

        assertEquals(3, capabilities.runtimeApiVersion)
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.BRIDGE_RUNTIME_SPINE))
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.LEGACY_MAIN_SERVICE_COMPONENT))
        assertFalse(capabilities.capabilities.contains("local_legacy_compat_sourceset"))
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.REGISTRATION_RUNTIME))
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.DOWNSTREAM_MESSAGE_PIPELINE))
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.NOTIFICATION_POLICY_RUNTIME))
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.CHANNEL_LIFECYCLE_TRACKING))
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.CONNECTION_SESSION_RUNTIME))
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.STOCK_SURFACE_COMPATIBILITY))
        assertTrue(capabilities.capabilities.contains(PushRuntimeCapability.ACCOUNT_CLOUD_BRIDGE))
    }

    private fun testHost(processedIntents: MutableList<Intent>): PushRuntimeBridgeHost {
        return object : PushRuntimeBridgeHost {
            override val context: Context
                get() = throw UnsupportedOperationException("Context is not used in AndroidPushRuntime unit tests")

            override fun processBridgeIntent(intent: Intent) {
                processedIntents += intent
            }
        }
    }

    private class TestExecutionHost(
        private val downstreamDispatchResult: PushRuntimeApplicationDispatchResult = PushRuntimeApplicationDispatchResult(),
        private val cancelNotificationResult: Boolean = false,
        private val onApplicationRegistration: ((String, String) -> Boolean)? = null
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
            return onApplicationRegistration?.invoke(packageName, reason) ?: true
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
