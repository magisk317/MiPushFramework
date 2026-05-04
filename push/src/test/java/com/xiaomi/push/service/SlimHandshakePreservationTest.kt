package com.xiaomi.push.service

import com.xiaomi.slim.Blob
import io.github.magisk317.mipush.runtime.core.PushChannelState
import io.github.magisk317.mipush.runtime.core.PushConnectionState
import io.github.magisk317.mipush.service.runtime.PushChannelOpenRuntime
import io.github.magisk317.mipush.service.runtime.PushSlimConnectionRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Preservation Property Test
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**
 *
 * Property 2: Preservation - Non-handshake Slim operations and channel open decisions
 * must remain unchanged after the bugfix.
 *
 * These tests capture the baseline behavior of pure functions that should NOT be affected
 * by the planSlimHandshake() fix. All tests MUST PASS on the current unfixed code.
 */
class SlimHandshakePreservationTest {

    // =========================================================================
    // 1. PushChannelOpenRuntime.decideOpenPlan() — all input combinations
    // =========================================================================

    companion object {
        /**
         * Generates all combinations of (hasNetwork, isConnected, clientStatus, shouldRebind).
         * ClientStatus values: unbind, binding, binded, and null.
         */
        @JvmStatic
        fun decideOpenPlanCombinations(): Stream<Arguments> {
            val booleans = listOf(true, false)
            val statuses: List<PushClientsManager.ClientStatus?> = listOf(
                PushClientsManager.ClientStatus.unbind,
                PushClientsManager.ClientStatus.binding,
                PushClientsManager.ClientStatus.binded,
                null
            )
            return booleans.flatMap { hasNetwork ->
                booleans.flatMap { isConnected ->
                    statuses.flatMap { clientStatus ->
                        booleans.map { shouldRebind ->
                            Arguments.of(hasNetwork, isConnected, clientStatus, shouldRebind)
                        }
                    }
                }
            }.stream()
        }

        /**
         * Generates various (channelId, cmd) combinations for planInboundBlob testing.
         */
        @JvmStatic
        fun inboundBlobCombinations(): Stream<Arguments> {
            val channelIds = listOf(0, 1, 5, -1)
            val cmds: List<String?> = listOf(
                Blob.CMD_PING,
                Blob.CMD_CLOSE,
                Blob.CMD_CONN,
                "MSG",
                "BIND",
                null
            )
            return channelIds.flatMap { channelId ->
                cmds.map { cmd ->
                    Arguments.of(channelId, cmd)
                }
            }.stream()
        }
    }

    /**
     * Preservation: decideOpenPlan() behavior for all input combinations.
     *
     * Observed baseline on unfixed code:
     * - !hasNetwork → OpenFailedNoNetwork, OpenFailed state, reasonCode=2
     * - hasNetwork && !isConnected → ScheduleConnect, Binding state
     * - hasNetwork && isConnected && effectiveStatus==unbind → Bind, Binding state
     * - hasNetwork && isConnected && shouldRebind (non-unbind) → Rebind, Binding state
     * - hasNetwork && isConnected && binding && !shouldRebind → AlreadyBinding, Binding state
     * - hasNetwork && isConnected && binded && !shouldRebind → AlreadyBound, Bound state
     *
     * **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
     */
    @ParameterizedTest(name = "decideOpenPlan(hasNetwork={0}, isConnected={1}, clientStatus={2}, shouldRebind={3})")
    @MethodSource("decideOpenPlanCombinations")
    fun `decideOpenPlan preserves baseline behavior for all combinations`(
        hasNetwork: Boolean,
        isConnected: Boolean,
        clientStatus: PushClientsManager.ClientStatus?,
        shouldRebind: Boolean
    ) {
        val plan = PushChannelOpenRuntime.decideOpenPlan(
            hasNetwork = hasNetwork,
            isConnected = isConnected,
            clientStatus = clientStatus,
            shouldRebind = shouldRebind
        )

        val effectiveStatus = clientStatus ?: PushClientsManager.ClientStatus.unbind

        when {
            !hasNetwork -> {
                assertEquals(PushChannelOpenAction.OpenFailedNoNetwork, plan.action)
                assertEquals(PushChannelState.OpenFailed, plan.state)
                assertEquals(2, plan.reasonCode)
                assertEquals("network_unavailable", plan.reasonMessage)
            }
            !isConnected -> {
                assertEquals(PushChannelOpenAction.ScheduleConnect, plan.action)
                assertEquals(PushChannelState.Binding, plan.state)
            }
            effectiveStatus == PushClientsManager.ClientStatus.unbind -> {
                assertEquals(PushChannelOpenAction.Bind, plan.action)
                assertEquals(PushChannelState.Binding, plan.state)
            }
            shouldRebind -> {
                assertEquals(PushChannelOpenAction.Rebind, plan.action)
                assertEquals(PushChannelState.Binding, plan.state)
            }
            effectiveStatus == PushClientsManager.ClientStatus.binding -> {
                assertEquals(PushChannelOpenAction.AlreadyBinding, plan.action)
                assertEquals(PushChannelState.Binding, plan.state)
            }
            effectiveStatus == PushClientsManager.ClientStatus.binded -> {
                assertEquals(PushChannelOpenAction.AlreadyBound, plan.action)
                assertEquals(PushChannelState.Bound, plan.state)
            }
            else -> {
                assertEquals(PushChannelOpenAction.NoAction, plan.action)
                assertEquals(PushChannelState.Unbound, plan.state)
            }
        }
    }

    // =========================================================================
    // 2. PushSlimConnectionRuntime.planInboundBlob() — various combinations
    // =========================================================================

    /**
     * Preservation: planInboundBlob() behavior for various (channelId, cmd) combinations.
     *
     * Observed baseline on unfixed code:
     * - channelId != 0 → action=None (regardless of cmd)
     * - channelId == 0 && cmd==PING → PingReceived, shouldUpdateLastReceived=true
     * - channelId == 0 && cmd==CLOSE → CloseReceived, Disconnected state, disconnectReasonCode=13
     * - channelId == 0 && cmd==CONN → ChallengeReceived
     * - channelId == 0 && other cmd → action=None
     *
     * **Validates: Requirements 3.5**
     */
    @ParameterizedTest(name = "planInboundBlob(channelId={0}, cmd={1})")
    @MethodSource("inboundBlobCombinations")
    fun `planInboundBlob preserves baseline behavior for various combinations`(
        channelId: Int,
        cmd: String?
    ) {
        val plan = PushSlimConnectionRuntime.planInboundBlob(
            channelId = channelId,
            cmd = cmd
        )

        if (channelId != 0) {
            assertEquals(PushSlimInboundAction.None, plan.action)
            assertNull(plan.eventAction)
            assertFalse(plan.shouldUpdateLastReceived)
            assertNull(plan.connectionState)
            assertNull(plan.disconnectReasonCode)
        } else {
            when (cmd) {
                Blob.CMD_PING -> {
                    assertEquals(PushSlimInboundAction.PingReceived, plan.action)
                    assertEquals("slim_ping_received", plan.eventAction)
                    assertTrue(plan.shouldUpdateLastReceived)
                    assertNull(plan.connectionState)
                }
                Blob.CMD_CLOSE -> {
                    assertEquals(PushSlimInboundAction.CloseReceived, plan.action)
                    assertEquals("slim_close_received", plan.eventAction)
                    assertEquals(PushConnectionState.Disconnected, plan.connectionState)
                    assertEquals("server_close_blob", plan.connectionReason)
                    assertEquals(13, plan.disconnectReasonCode)
                }
                Blob.CMD_CONN -> {
                    assertEquals(PushSlimInboundAction.ChallengeReceived, plan.action)
                    assertEquals("slim_challenge_received", plan.eventAction)
                }
                else -> {
                    assertEquals(PushSlimInboundAction.None, plan.action)
                    assertNull(plan.eventAction)
                    assertFalse(plan.shouldUpdateLastReceived)
                }
            }
        }
    }

    // =========================================================================
    // 3. PushSlimConnectionRuntime.planSendPing() — return value
    // =========================================================================

    /**
     * Preservation: planSendPing() returns a consistent event action.
     *
     * Observed baseline: eventAction = "slim_ping_sent"
     *
     * **Validates: Requirements 3.5**
     */
    @Test
    fun `planSendPing preserves baseline behavior`() {
        val plan = PushSlimConnectionRuntime.planSendPing()

        assertEquals("slim_ping_sent", plan.eventAction)
    }
}
