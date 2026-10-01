package io.github.magisk317.mipush.notification

import io.github.magisk317.mipush.common.island.IslandOptions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the dynamic-island "show original notification" suppression fix:
 *  - A1: original is only suppressed when the island proxy truly took over.
 *  - A2: replay receipt is posted whenever the original might be invisible.
 */
class NotificationControllerIslandProxyTest {

    // Scenario 1: showOriginal=false + island proxy did NOT take over -> original must be posted.
    @Test
    fun `showOriginal false without island proxy takeover keeps original notification`() {
        assertFalse(
            NotificationController.shouldSuppressOriginalByIslandProxy(
                showOriginalNotification = false,
                islandProxyTookOver = false,
            ),
        )
    }

    // Scenario 2: showOriginal=false + island proxy took over -> original suppressed.
    @Test
    fun `showOriginal false with island proxy takeover suppresses original notification`() {
        assertTrue(
            NotificationController.shouldSuppressOriginalByIslandProxy(
                showOriginalNotification = false,
                islandProxyTookOver = true,
            ),
        )
    }

    // Scenario 3: showOriginal=true -> original always kept, regardless of proxy.
    @Test
    fun `showOriginal true never suppresses original notification`() {
        assertFalse(
            NotificationController.shouldSuppressOriginalByIslandProxy(
                showOriginalNotification = true,
                islandProxyTookOver = false,
            ),
        )
        assertFalse(
            NotificationController.shouldSuppressOriginalByIslandProxy(
                showOriginalNotification = true,
                islandProxyTookOver = true,
            ),
        )
    }

    // Scenario 4a: receipt posted when original suppressed (opt-out) and no proxy.
    @Test
    fun `visible receipt posted when original invisible due to opt out`() {
        assertTrue(
            NotificationMockReplaySupport.shouldPostVisibleReceipt(
                isMockReplay = true,
                options = IslandOptions(showOriginalNotification = false),
                islandProxyTookOver = false,
            ),
        )
    }

    // Scenario 4b: receipt posted when the island proxy replaced the original.
    @Test
    fun `visible receipt posted when island proxy replaced original`() {
        assertTrue(
            NotificationMockReplaySupport.shouldPostVisibleReceipt(
                isMockReplay = true,
                options = IslandOptions(showOriginalNotification = false),
                islandProxyTookOver = true,
            ),
        )
        assertTrue(
            NotificationMockReplaySupport.shouldPostVisibleReceipt(
                isMockReplay = true,
                options = IslandOptions(showOriginalNotification = true),
                islandProxyTookOver = true,
            ),
        )
    }

    // Scenario 4c: no receipt when the original is visible and no proxy took over.
    @Test
    fun `visible receipt suppressed only when original visible and no proxy`() {
        assertFalse(
            NotificationMockReplaySupport.shouldPostVisibleReceipt(
                isMockReplay = true,
                options = IslandOptions(showOriginalNotification = true),
                islandProxyTookOver = false,
            ),
        )
    }

    // Scenario 4d: non-replay never posts a receipt.
    @Test
    fun `non replay never posts visible receipt`() {
        assertFalse(
            NotificationMockReplaySupport.shouldPostVisibleReceipt(
                isMockReplay = false,
                options = IslandOptions(showOriginalNotification = false),
                islandProxyTookOver = true,
            ),
        )
    }

    // A2 wiring through the controller delegate.
    @Test
    fun `controller delegate mirrors wide receipt fallback`() {
        assertTrue(
            NotificationController.shouldPostMockReplayVisibleReceipt(
                isMockReplay = true,
                options = IslandOptions(showOriginalNotification = false),
                islandProxyTookOver = false,
            ),
        )
        assertFalse(
            NotificationController.shouldPostMockReplayVisibleReceipt(
                isMockReplay = true,
                options = IslandOptions(showOriginalNotification = true),
                islandProxyTookOver = false,
            ),
        )
    }
}
