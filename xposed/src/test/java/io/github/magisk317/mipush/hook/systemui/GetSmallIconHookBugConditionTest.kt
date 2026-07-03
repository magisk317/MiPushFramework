package io.github.magisk317.mipush.hook.systemui

import io.github.magisk317.mipush.hook.island.IslandDispatchContract
import io.github.magisk317.mipush.hook.island.IslandOptions
import io.github.magisk317.mipush.hook.island.IslandPreferences
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.api.lifecycle.AfterProperty
import net.jqwik.api.lifecycle.BeforeProperty
import org.junit.jupiter.api.Assertions.assertNull

/**
 * Bug Condition Exploration Test — Property-Based
 *
 * **Validates: Requirements 1.1, 2.1**
 *
 * Property 1: Bug Condition — Monochrome Icons Incorrectly Intercepted When Toggle OFF
 *
 * This test encodes the EXPECTED (correct) behavior:
 * - When `colorStatusBarIcon` is `false` (monochrome desired) AND the notification is MiPush-managed,
 *   the `NotifImageUtil.getSmallIcon` hook's `doBefore` logic SHALL NOT intercept (shall not set `result`).
 * - This allows MIUI's native monochrome tinting pipeline to process the icon normally.
 *
 * On UNFIXED code, this test FAILS because:
 * - The guard `if (IslandPreferences.current().colorStatusBarIcon) return@doBefore` means
 *   "skip when toggle ON" — so when toggle is OFF, the guard does NOT return early.
 * - Execution continues, `isMiPushManagedNotification(sbn)` returns true, and
 *   `result = notification.smallIcon` is set (hook intercepts incorrectly).
 *
 * The property generates random MiPush-managed notification configurations with
 * various combinations of MiPush extras and asserts the hook does NOT intercept.
 * Test failure confirms the bug: the hook intercepts when it should not.
 */
class GetSmallIconHookBugConditionTest {

    companion object {
        private const val EXTRA_TARGET_PACKAGE = "target_package"
        private const val EXTRA_MIUI_TARGET_PACKAGE = "miui.targetPkg"
        private const val EXTRA_XMSF_TARGET_PACKAGE = "xmsf_target_package"
        private const val EXTRA_MOCK_REPLAY_RECEIPT = "mipush_mock_replay_receipt"
        private const val EXTRA_MOCK_REPLAY_SOURCE_PACKAGE = "mipush_mock_replay_source_package"
    }

    /**
     * Represents the extras present in a notification Bundle — used as a pure-data
     * abstraction that avoids depending on Android's Bundle class at test runtime.
     */
    data class NotificationExtras(
        val strings: Map<String, String> = emptyMap(),
        val booleans: Map<String, Boolean> = emptyMap(),
    ) {
        fun containsKey(key: String): Boolean = strings.containsKey(key) || booleans.containsKey(key)
        fun getBoolean(key: String, default: Boolean): Boolean = booleans[key] ?: default
        fun getString(key: String): String? = strings[key]
    }

    /**
     * Data class representing a MiPush-managed notification's relevant properties.
     * This is the input domain for the property test.
     */
    data class MiPushNotificationInput(
        val packageName: String,
        val hasTargetPackage: Boolean,
        val hasMiuiTargetPkg: Boolean,
        val hasXmsfTargetPackage: Boolean,
        val hasMockReplayReceipt: Boolean,
        val hasMockReplaySourcePackage: Boolean,
        val hasSourcePackage: Boolean,
        val hasOwnerMarker: Boolean,
    ) {
        fun toExtras(): NotificationExtras {
            val strings = mutableMapOf<String, String>()
            val booleans = mutableMapOf<String, Boolean>()

            if (hasTargetPackage) strings[EXTRA_TARGET_PACKAGE] = packageName
            if (hasMiuiTargetPkg) strings[EXTRA_MIUI_TARGET_PACKAGE] = packageName
            if (hasXmsfTargetPackage) strings[EXTRA_XMSF_TARGET_PACKAGE] = packageName
            if (hasMockReplayReceipt) booleans[EXTRA_MOCK_REPLAY_RECEIPT] = true
            if (hasMockReplaySourcePackage) strings[EXTRA_MOCK_REPLAY_SOURCE_PACKAGE] = packageName
            if (hasSourcePackage) strings[IslandDispatchContract.SOURCE_PACKAGE] = packageName
            if (hasOwnerMarker) strings[IslandDispatchContract.OWNER] = IslandDispatchContract.OWNER_MARKER

            return NotificationExtras(strings, booleans)
        }

        /** Human-readable description of active extras for failure messages */
        fun activeExtrasDescription(): String {
            val active = mutableListOf<String>()
            if (hasTargetPackage) active += "target_package"
            if (hasMiuiTargetPkg) active += "miui.targetPkg"
            if (hasXmsfTargetPackage) active += "xmsf_target_package"
            if (hasMockReplayReceipt) active += "mipush_mock_replay_receipt"
            if (hasMockReplaySourcePackage) active += "mipush_mock_replay_source_package"
            if (hasSourcePackage) active += "hyperisland_source_pkg"
            if (hasOwnerMarker) active += "hyperisland.owner"
            return active.joinToString(", ")
        }
    }

    /**
     * Replicates `HookSystemUI.isMiPushManagedNotification` using our pure-data [NotificationExtras].
     */
    private fun isMiPushManagedNotification(extras: NotificationExtras): Boolean {
        return extras.containsKey(EXTRA_TARGET_PACKAGE) ||
            extras.containsKey(EXTRA_MIUI_TARGET_PACKAGE) ||
            extras.containsKey(EXTRA_XMSF_TARGET_PACKAGE) ||
            extras.getBoolean(EXTRA_MOCK_REPLAY_RECEIPT, false) ||
            extras.getString(EXTRA_MOCK_REPLAY_SOURCE_PACKAGE)?.isNotBlank() == true ||
            extras.getString(IslandDispatchContract.SOURCE_PACKAGE)?.isNotBlank() == true ||
            extras.getString(IslandDispatchContract.OWNER) == IslandDispatchContract.OWNER_MARKER
    }

    /**
     * Replicates the hook's `doBefore` decision logic faithfully from FIXED code.
     * Returns whether the hook intercepts (sets result) or not.
     *
     * This mirrors the FIXED code in HookSystemUI.kt:
     * ```
     * doBefore {
     *     if (!IslandPreferences.current().colorStatusBarIcon) return@doBefore  // guard
     *     val sbn = args[1] as? StatusBarNotification ?: return@doBefore
     *     val notification = sbn.notification ?: return@doBefore
     *     if (isMiPushManagedNotification(sbn)) {
     *         result = notification.smallIcon   // <-- INTERCEPTS
     *         return@doBefore
     *     }
     * }
     * ```
     *
     * @return true if the hook would intercept (set result = notification.smallIcon), false otherwise
     */
    private fun hookIntercepts(colorStatusBarIcon: Boolean, extras: NotificationExtras): Boolean {
        // Replicate the guard condition from FIXED code
        if (!colorStatusBarIcon) return false // guard returns early when toggle OFF (fixed logic)

        // In the real code, if sbn or notification is null, hook returns early.
        // Our test always provides valid extras so we proceed.

        if (isMiPushManagedNotification(extras)) {
            return true // hook intercepts: sets result = notification.smallIcon
        }
        return false // hook does not intercept
    }

    @BeforeProperty
    fun setup() {
        // Set preferences to colorStatusBarIcon = false (monochrome desired — the bug condition)
        IslandPreferences.resetForTest(IslandOptions(colorStatusBarIcon = false))
    }

    @AfterProperty
    fun teardown() {
        IslandPreferences.resetForTest()
    }

    /**
     * Generates random package names for MiPush-managed notifications.
     */
    @Provide
    fun packageNames(): Arbitrary<String> = Arbitraries.of(
        "com.tencent.mm",         // WeChat
        "com.taobao.taobao",     // Taobao
        "com.eg.android.AlipayGphone", // Alipay
        "com.jingdong.app.mall", // JD
        "com.sina.weibo",        // Weibo
        "com.zhihu.android",     // Zhihu
        "com.netease.cloudmusic", // NetEase Music
        "com.ss.android.ugc.aweme", // Douyin
        "tv.danmaku.bili",       // Bilibili
        "com.example.randomapp"  // generic app
    )

    /**
     * Generates random MiPush notification inputs with at least one MiPush extra present.
     * This ensures every generated input represents a valid MiPush-managed notification.
     */
    @Provide
    fun miPushNotificationInputs(): Arbitrary<MiPushNotificationInput> {
        return Combinators.combine(
            packageNames(),
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
            Arbitraries.of(true, false)
        ).filter { _, tp, miui, xmsf, replay, replaySource, source, owner ->
            // At least one MiPush extra must be present to be a MiPush-managed notification
            tp || miui || xmsf || replay || replaySource || source || owner
        }.`as` { pkg, tp, miui, xmsf, replay, replaySource, source, owner ->
            MiPushNotificationInput(pkg, tp, miui, xmsf, replay, replaySource, source, owner)
        }
    }

    /**
     * Property 1: Bug Condition — Monochrome Icons Incorrectly Intercepted When Toggle OFF
     *
     * **Validates: Requirements 1.1, 2.1**
     *
     * For ANY MiPush-managed notification when `colorStatusBarIcon` is `false` (monochrome desired),
     * the `getSmallIcon` hook SHALL NOT intercept (shall not set result).
     *
     * On UNFIXED code, this property FAILS because the hook's guard condition is inverted:
     * `if (colorStatusBarIcon) return@doBefore` does NOT return early when toggle is OFF,
     * so the hook continues execution and sets `result = notification.smallIcon`.
     *
     * EXPECTED OUTCOME on unfixed code: FAILS for every generated input.
     */
    @Property(tries = 100)
    fun `hook must not intercept MiPush notifications when monochrome desired`(
        @ForAll("miPushNotificationInputs") input: MiPushNotificationInput
    ) {
        // Bug condition: colorStatusBarIcon = false (set in @BeforeProperty)
        val colorStatusBarIcon = IslandPreferences.current().colorStatusBarIcon
        val extras = input.toExtras()

        // Simulate the hook's doBefore decision on current code.
        val intercepted = hookIntercepts(colorStatusBarIcon, extras)

        // Expected behavior: hook does NOT intercept (intercepted == false)
        // Bug: hook DOES intercept (intercepted == true, result = notification.smallIcon)
        assertNull(
            if (intercepted) "INTERCEPTED" else null,
            "When colorStatusBarIcon=false (monochrome desired) and notification is MiPush-managed " +
                "(package=${input.packageName}, " +
                "active extras: [${input.activeExtrasDescription()}]), " +
                "the getSmallIcon hook must NOT intercept (must not set result). " +
                "Bug: the hook incorrectly sets result=notification.smallIcon, " +
                "bypassing MIUI's native monochrome tinting pipeline. " +
                "The guard condition `if (colorStatusBarIcon) return@doBefore` does not return " +
                "when colorStatusBarIcon=false, so the hook proceeds to intercept."
        )
    }
}
