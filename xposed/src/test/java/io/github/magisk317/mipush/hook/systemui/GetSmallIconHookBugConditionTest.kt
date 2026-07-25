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
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Monochrome getSmallIcon intercept contract.
 *
 * When colorStatusBarIcon is false (monochrome) and the notification is MiPush-managed,
 * SystemUI must intercept getSmallIcon so MIUI cannot substitute the multi-color app logo
 * over the status-bar-only monochrome fallback / monochrome RESOURCE.
 */
class GetSmallIconHookBugConditionTest {

    companion object {
        private const val EXTRA_TARGET_PACKAGE = "target_package"
        private const val EXTRA_MIUI_TARGET_PACKAGE = "miui.targetPkg"
        private const val EXTRA_XMSF_TARGET_PACKAGE = "xmsf_target_package"
        private const val EXTRA_MOCK_REPLAY_RECEIPT = "mipush_mock_replay_receipt"
        private const val EXTRA_MOCK_REPLAY_SOURCE_PACKAGE = "mipush_mock_replay_source_package"
    }

    data class NotificationExtras(
        val strings: Map<String, String> = emptyMap(),
        val booleans: Map<String, Boolean> = emptyMap(),
    ) {
        fun containsKey(key: String): Boolean = strings.containsKey(key) || booleans.containsKey(key)
        fun getBoolean(key: String, default: Boolean): Boolean = booleans[key] ?: default
        fun getString(key: String): String? = strings[key]
    }

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

    private fun isMiPushManagedNotification(extras: NotificationExtras): Boolean {
        return SystemUiNotificationPolicy.hasMiPushManagementMarker(
            containsKey = extras::containsKey,
            getBoolean = extras::getBoolean,
            getString = extras::getString,
        )
    }

    private fun hookIntercepts(colorStatusBarIcon: Boolean, extras: NotificationExtras): Boolean {
        val isMiPushManaged = isMiPushManagedNotification(extras)
        // Mirror the live guard: monochrome + MiPush (or strong global) still intercepts BITMAP/RESOURCE.
        return SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
            colorStatusBarIcon = colorStatusBarIcon,
            forceGlobalStatusBarIcons = false,
            isMiPushManaged = isMiPushManaged,
            iconType = SystemUiNotificationPolicy.ICON_TYPE_BITMAP,
            resId = 0,
            resPackage = null,
            packageName = "com.example.app",
            uid = 10123,
            isSystemApp = false,
            canColorize = false,
        )
    }

    @BeforeProperty
    fun setup() {
        IslandPreferences.resetForTest(IslandOptions(colorStatusBarIcon = false))
    }

    @AfterProperty
    fun teardown() {
        IslandPreferences.resetForTest()
    }

    @Provide
    fun packageNames(): Arbitrary<String> = Arbitraries.of(
        "com.tencent.mm",
        "com.taobao.taobao",
        "com.eg.android.AlipayGphone",
        "com.jingdong.app.mall",
        "com.sina.weibo",
        "com.zhihu.android",
        "com.netease.cloudmusic",
        "com.ss.android.ugc.aweme",
        "tv.danmaku.bili",
        "com.example.randomapp",
    )

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
            Arbitraries.of(true, false),
        ).filter { _, tp, miui, xmsf, replay, replaySource, source, owner ->
            tp || miui || xmsf || replay || replaySource || source || owner
        }.`as` { pkg, tp, miui, xmsf, replay, replaySource, source, owner ->
            MiPushNotificationInput(pkg, tp, miui, xmsf, replay, replaySource, source, owner)
        }
    }

    @Property(tries = 100)
    fun `hook must intercept MiPush notifications when monochrome desired`(
        @ForAll("miPushNotificationInputs") input: MiPushNotificationInput,
    ) {
        val colorStatusBarIcon = IslandPreferences.current().colorStatusBarIcon
        val extras = input.toExtras()
        val intercepted = hookIntercepts(colorStatusBarIcon, extras)
        assertTrue(
            intercepted,
            "When colorStatusBarIcon=false (monochrome) and notification is MiPush-managed " +
                "(package=${input.packageName}, active extras: [${input.activeExtrasDescription()}]), " +
                "getSmallIcon must intercept so MIUI cannot replace the monochrome silhouette " +
                "with the multi-color app logo.",
        )
    }
}
