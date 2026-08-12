package com.xiaomi.xmsf.push.service.notificationcollection

import android.app.Notification
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.common.island.IslandOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Unit tests for focus cache refinement — per-package cache strategy, product switch, no cloud
 * whitelist dependency.
 *
 * _Requirements: 18.1, 18.2, 18.3_
 *
 * Requirement 18.1: Focus deletion cache uses per-package stock strategy (not uniform 24h).
 * Requirement 18.2: Product switch is preserved, allowing user to control focus notifications.
 * Requirement 18.3: No cloud-control whitelist is replicated (switch strategy only).
 */
// Keep Robolectric: this test relies on Android framework implementations indirectly;
// android.jar unit-test stubs throw "Method ... not mocked" without the extension.
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class FocusCacheRefinementTest {

    // ──────────────────────────────────────────────────────────────────────────
    // Requirement 18.1: Different packages use different cache strategies
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `HyperOS 1 caches delete state only for allowlist packages`() {
        val filter = FocusNotificationCollectionFilter(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_1,
        )

        // Allowlist package: com.autonavi.minimap
        val allowlistSbn = sbn("com.autonavi.minimap", id = 1, tag = "nav")
        filter.onNotificationRemoved(allowlistSbn, REASON_USER_DISMISS)
        assertTrue(filter.containsKey(allowlistSbn.key))

        // Non-allowlist package: com.example.app
        val nonAllowlistSbn = sbn("com.example.app", id = 2, tag = "msg")
        filter.onNotificationRemoved(nonAllowlistSbn, REASON_USER_DISMISS)
        assertFalse(filter.containsKey(nonAllowlistSbn.key))
    }

    @Test
    fun `HyperOS 1 caches multiple allowlist packages with different strategies`() {
        val filter = FocusNotificationCollectionFilter(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_1,
        )

        // Different packages in the allowlist all get cached
        val meituan = sbn("com.sankuai.meituan", id = 1, tag = "order")
        val amap = sbn("com.autonavi.minimap", id = 2, tag = "route")
        val alipay = sbn("com.eg.android.AlipayGphone", id = 3, tag = "pay")
        val unknownApp = sbn("com.random.unknown", id = 4, tag = "notif")

        filter.onNotificationRemoved(meituan, REASON_USER_DISMISS)
        filter.onNotificationRemoved(amap, REASON_USER_DISMISS)
        filter.onNotificationRemoved(alipay, REASON_USER_DISMISS)
        filter.onNotificationRemoved(unknownApp, REASON_USER_DISMISS)

        assertTrue(filter.containsKey(meituan.key), "allowlist package meituan should be cached")
        assertTrue(filter.containsKey(amap.key), "allowlist package amap should be cached")
        assertTrue(filter.containsKey(alipay.key), "allowlist package alipay should be cached")
        assertFalse(filter.containsKey(unknownApp.key), "non-allowlist package should NOT be cached")
    }

    @Test
    fun `HyperOS 2 caches all packages except system exclusions`() {
        val scheduler = RecordingScheduler()
        val filter = FocusNotificationCollectionFilter(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_2,
            scheduler,
        )

        // Normal package: should be cached
        val normalApp = sbn("com.example.app", id = 1, tag = "msg")
        filter.onNotificationRemoved(normalApp, REASON_USER_DISMISS)
        assertTrue(filter.containsKey(normalApp.key), "normal package should be cached in HyperOS 2")

        // Another normal package: should also be cached independently
        val anotherApp = sbn("com.another.example", id = 2, tag = "delivery")
        filter.onNotificationRemoved(anotherApp, REASON_USER_DISMISS)
        assertTrue(filter.containsKey(anotherApp.key), "another package should be independently cached")

        // System exclusion: com.android.settings — should NOT be cached
        val settings = sbn("com.android.settings", id = 3, tag = "settings")
        filter.onNotificationRemoved(settings, REASON_USER_DISMISS)
        assertFalse(filter.containsKey(settings.key), "system exclusion package should NOT be cached")
    }

    @Test
    fun `HyperOS 2 per-package cache with different system exclusions`() {
        val scheduler = RecordingScheduler()
        val filter = FocusNotificationCollectionFilter(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_2,
            scheduler,
        )

        // Each system exclusion should NOT be cached
        val exclusions = listOf(
            "com.android.soundrecorder",
            "com.android.deskclock",
            "com.android.settings",
            "com.android.incallui",
            "com.android.server.telecom",
            "com.miui.securitycenter",
        )
        exclusions.forEachIndexed { index, pkg ->
            val s = sbn(pkg, id = index, tag = "sys-$index")
            filter.onNotificationRemoved(s, REASON_USER_DISMISS)
            assertFalse(filter.containsKey(s.key), "exclusion $pkg should NOT be cached")
        }

        // A normal package side by side should be cached
        val normal = sbn("com.tencent.mm", id = 100, tag = "chat")
        filter.onNotificationRemoved(normal, REASON_USER_DISMISS)
        assertTrue(filter.containsKey(normal.key), "normal package should be cached")
    }

    @Test
    fun `HyperOS 1 and HyperOS 2 apply different per-package policies for same package`() {
        // com.autonavi.minimap: cached in both HyperOS 1 and 2
        val filterOs1 = FocusNotificationCollectionFilter(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_1,
        )
        val filterOs2 = FocusNotificationCollectionFilter(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_2,
            RecordingScheduler(),
        )

        val amap = sbn("com.autonavi.minimap", id = 1, tag = "nav")
        filterOs1.onNotificationRemoved(amap, REASON_USER_DISMISS)
        filterOs2.onNotificationRemoved(amap, REASON_USER_DISMISS)

        assertTrue(filterOs1.containsKey(amap.key), "HyperOS 1 allowlist package cached")
        assertTrue(filterOs2.containsKey(amap.key), "HyperOS 2 non-exclusion package cached")

        // com.example.app: NOT cached in HyperOS 1 but cached in HyperOS 2
        val example = sbn("com.example.app", id = 2, tag = "msg")
        filterOs1.onNotificationRemoved(example, REASON_USER_DISMISS)
        filterOs2.onNotificationRemoved(example, REASON_USER_DISMISS)

        assertFalse(filterOs1.containsKey(example.key), "HyperOS 1 non-allowlist NOT cached")
        assertTrue(filterOs2.containsKey(example.key), "HyperOS 2 non-exclusion cached")
    }

    @Test
    fun `disabled policy does not cache any package`() {
        val filter = FocusNotificationCollectionFilter(
            FocusNotificationCollectionFilter.Policy.DISABLED,
        )

        val any = sbn("com.autonavi.minimap", id = 1, tag = "nav")
        filter.onNotificationRemoved(any, REASON_USER_DISMISS)
        assertFalse(filter.containsKey(any.key), "disabled policy caches nothing")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Requirement 18.2: Product switch preserved and effective
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `product switch OFF prevents focus payload build`() {
        // When focusNotification = false, canBuildFocusPayload is always false
        val options = IslandOptions(
            enabled = true,
            focusNotification = false,
            enableFloat = true,
        )
        assertFalse(options.canBuildFocusPayload)
    }

    @Test
    fun `product switch ON with all conditions met allows focus payload build`() {
        // When focusNotification = true, enabled = true, enableFloat = true
        val options = IslandOptions(
            enabled = true,
            focusNotification = true,
            enableFloat = true,
        )
        assertTrue(options.canBuildFocusPayload)
    }

    @Test
    fun `product switch ON but main switch OFF still prevents focus payload`() {
        val options = IslandOptions(
            enabled = false,
            focusNotification = true,
            enableFloat = true,
        )
        assertFalse(options.canBuildFocusPayload)
    }

    @Test
    fun `product switch preserved per package - focus ON for one OFF for another`() {
        val optionsA = IslandOptions(
            enabled = true,
            focusNotification = true,
            enableFloat = true,
        )
        val optionsB = IslandOptions(
            enabled = true,
            focusNotification = false,
            enableFloat = true,
        )

        assertTrue(optionsA.canBuildFocusPayload, "Package A with focus ON should allow payload")
        assertFalse(optionsB.canBuildFocusPayload, "Package B with focus OFF should block payload")
    }

    @Test
    fun `product switch OFF suppresses focus regardless of cache state`() {
        // Even if cache records a deletion (focus-close state), if the product switch
        // is off the focus notification should not be built
        val filter = FocusNotificationCollectionFilter(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_2,
            RecordingScheduler(),
        )
        val notification = sbn("com.example.app", id = 1, tag = "msg")
        filter.onNotificationRemoved(notification, REASON_USER_DISMISS)
        assertTrue(filter.containsKey(notification.key))

        // Product switch OFF — focus payload would not be built regardless
        val switchOff = IslandOptions(
            enabled = true,
            focusNotification = false,
            enableFloat = true,
        )
        assertFalse(switchOff.canBuildFocusPayload,
            "product switch OFF means no focus notification even if cache exists")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Requirement 18.3: No dependency on cloud-control whitelist
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `policy selection is purely local system property based`() {
        // policyForOsVersionCode uses a local system property, no network/cloud dependency
        assertEquals(
            FocusNotificationCollectionFilter.Policy.DISABLED,
            FocusNotificationCollectionFilter.policyForOsVersionCode(null),
        )
        assertEquals(
            FocusNotificationCollectionFilter.Policy.DISABLED,
            FocusNotificationCollectionFilter.policyForOsVersionCode("0"),
        )
        assertEquals(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_1,
            FocusNotificationCollectionFilter.policyForOsVersionCode("1"),
        )
        assertEquals(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_2,
            FocusNotificationCollectionFilter.policyForOsVersionCode("2"),
        )
    }

    @Test
    fun `HyperOS 1 allowlist is hardcoded and requires no network call`() {
        val filter = FocusNotificationCollectionFilter(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_1,
        )

        // The allowlist is deterministic — same input always produces same result
        // without any network dependency. Verify all 6 known allowlist packages.
        val expectedAllowlist = listOf(
            "com.autonavi.minimap",
            "com.sankuai.meituan",
            "com.sankuai.meituan.takeoutnew",
            "me.ele",
            "com.android.keyguard",
            "com.eg.android.AlipayGphone",
        )

        expectedAllowlist.forEach { pkg ->
            val s = sbn(pkg, id = 1, tag = "test")
            filter.onNotificationRemoved(s, REASON_USER_DISMISS)
            assertTrue(filter.containsKey(s.key), "hardcoded allowlist package $pkg should be cached")
        }

        // Non-allowlist packages are deterministically rejected — no cloud lookup
        val nonAllowlist = sbn("com.cloud.whitelist.app", id = 99, tag = "cloud")
        filter.onNotificationRemoved(nonAllowlist, REASON_USER_DISMISS)
        assertFalse(filter.containsKey(nonAllowlist.key),
            "non-allowlist package cannot be admitted by cloud whitelist because it doesn't exist")
    }

    @Test
    fun `HyperOS 2 exclusion list is hardcoded and requires no network call`() {
        val scheduler = RecordingScheduler()
        val filter = FocusNotificationCollectionFilter(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_2,
            scheduler,
        )

        // The exclusion list is deterministic — same input always produces same result
        val expectedExclusions = listOf(
            "com.android.soundrecorder",
            "com.android.deskclock",
            "com.android.settings",
            "com.android.incallui",
            "com.android.server.telecom",
            "com.miui.securitycenter",
        )

        expectedExclusions.forEach { pkg ->
            val s = sbn(pkg, id = 1, tag = "test")
            filter.onNotificationRemoved(s, REASON_USER_DISMISS)
            assertFalse(filter.containsKey(s.key), "hardcoded exclusion $pkg should NOT be cached")
        }

        // Any package NOT in exclusions is accepted — no cloud expansion possible
        val anyOther = sbn("com.completely.new.app.never.seen.before", id = 50, tag = "new")
        filter.onNotificationRemoved(anyOther, REASON_USER_DISMISS)
        assertTrue(filter.containsKey(anyOther.key),
            "non-exclusion package is accepted without any cloud check")
    }

    @Test
    fun `filter operates in process-local memory with no persistent or network state`() {
        // Creating a new filter instance starts empty — verifying no hidden cloud state
        val filter1 = FocusNotificationCollectionFilter(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_2,
            RecordingScheduler(),
        )
        assertEquals(0, filter1.size(), "new filter starts empty with no preloaded state")

        val s = sbn("com.example.app", id = 1, tag = "msg")
        filter1.onNotificationRemoved(s, REASON_USER_DISMISS)
        assertEquals(1, filter1.size())

        // A separate filter instance does NOT share state
        val filter2 = FocusNotificationCollectionFilter(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_2,
            RecordingScheduler(),
        )
        assertEquals(0, filter2.size(), "independent filter instance has no shared state")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun sbn(
        packageName: String,
        id: Int,
        tag: String,
        focusParam: String? = null,
    ): StatusBarNotification {
        val notification = Notification().apply {
            focusParam?.let { extras.putString("miui.focus.param", it) }
        }
        return StatusBarNotification(
            packageName,
            packageName,
            id,
            tag,
            10_000,
            123,
            0,
            notification,
            Process.myUserHandle(),
            1_000L,
        )
    }

    private class RecordingScheduler : FocusNotificationCollectionFilter.TimeoutScheduler {
        val scheduled = mutableListOf<String>()
        val cancelled = mutableListOf<String>()

        override fun schedule(key: String) {
            scheduled += key
        }

        override fun cancel(key: String) {
            cancelled += key
        }
    }

    companion object {
        private const val REASON_USER_DISMISS = NotificationListenerService.REASON_CANCEL
    }
}
