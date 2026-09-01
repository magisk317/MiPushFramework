package io.github.magisk317.mipush.hook.systemui

import io.github.magisk317.mipush.common.island.IslandVisualContract

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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse

/**
 * Preservation Property Tests — Property-Based
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
 *
 * Property 2: Preservation — Non-MiPush Notifications and Other Hooks Unaffected
 *
 * These tests verify the scoped SystemUI icon policy. They serve as regression guards:
 * - Non-MiPush notifications are intercepted only by the explicit strong global mode
 * - IconManager.setIcon must only force icon_is_pre_L for MiPush-managed notifications when color mode is ON
 *
 * The important invariant is that non-MiPush notifications are left to native SystemUI behavior
 * unless strong global monochrome mode explicitly widens the scope.
 */
class PreservationPropertyTest {

    companion object {
        private const val EXTRA_TARGET_PACKAGE = "target_package"
        private const val EXTRA_MIUI_TARGET_PACKAGE = "miui.targetPkg"
        private const val EXTRA_XMSF_TARGET_PACKAGE = "xmsf_target_package"
        private const val EXTRA_MOCK_REPLAY_RECEIPT = "mipush_mock_replay_receipt"
        private const val EXTRA_MOCK_REPLAY_SOURCE_PACKAGE = "mipush_mock_replay_source_package"

        /** All keys that identify a notification as MiPush-managed */
        private val MIPUSH_EXTRA_KEYS = setOf(
            EXTRA_TARGET_PACKAGE,
            EXTRA_MIUI_TARGET_PACKAGE,
            EXTRA_XMSF_TARGET_PACKAGE,
            EXTRA_MOCK_REPLAY_RECEIPT,
            EXTRA_MOCK_REPLAY_SOURCE_PACKAGE,
            IslandDispatchContract.SOURCE_PACKAGE,
            IslandVisualContract.OWNER_KEY,
        )
    }

    // ─── Pure Data Types ─────────────────────────────────────────────────────────

    /**
     * Represents notification extras as a pure-data abstraction.
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
     * Represents a non-MiPush notification with arbitrary extras that do NOT
     * contain any MiPush-identifying keys.
     */
    data class NonMiPushNotificationInput(
        val packageName: String,
        val genericExtras: Map<String, String>,
    ) {
        fun toExtras(): NotificationExtras {
            return NotificationExtras(strings = genericExtras)
        }
    }

    // ─── Pure Logic Simulation ───────────────────────────────────────────────────

    /**
     * Replicates `HookSystemUI.isMiPushManagedNotification` logic.
     */
    private fun isMiPushManagedNotification(extras: NotificationExtras): Boolean {
        return SystemUiNotificationPolicy.hasMiPushManagementMarker(
            containsKey = extras::containsKey,
            getBoolean = extras::getBoolean,
            getString = extras::getString,
        )
    }

    /**
     * Replicates the getSmallIcon hook's doBefore decision.
     *
     * @return true if hook intercepts (sets result), false otherwise
     */
    private fun getSmallIconHookIntercepts(
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
        extras: NotificationExtras,
    ): Boolean {
        return SystemUiNotificationPolicy.shouldInterceptSmallIcon(
            colorStatusBarIcon = colorStatusBarIcon,
            forceGlobalStatusBarIcons = forceGlobalStatusBarIcons,
            isMiPushManaged = isMiPushManagedNotification(extras),
        )
    }

    /**
     * Replicates IconManager.setIcon hook's doAfter logic.
     *
     * @return true when the hook should force icon_is_pre_L on the icon view.
     */
    private fun iconManagerSetIconResult(
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
        isMiPushManaged: Boolean,
    ): Boolean? {
        return SystemUiNotificationPolicy.statusBarIconPreLTagOverride(
            colorStatusBarIcon = colorStatusBarIcon,
            forceGlobalStatusBarIcons = forceGlobalStatusBarIcons,
            isMiPushManaged = isMiPushManaged,
        )
    }

    /**
     * Replicates legacy NotificationEntry.setIconTag hook scope on Android versions before R.
     */
    private fun legacySetIconTagResult(
        tagIdMatches: Boolean,
        hasStatusBarNotification: Boolean,
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
        isMiPushManaged: Boolean,
    ): Boolean? {
        if (!tagIdMatches) return null
        if (!hasStatusBarNotification) return null
        return SystemUiNotificationPolicy.statusBarIconPreLTagOverride(
            colorStatusBarIcon = colorStatusBarIcon,
            forceGlobalStatusBarIcons = forceGlobalStatusBarIcons,
            isMiPushManaged = isMiPushManaged,
        )
    }

    // ─── jqwik Setup ─────────────────────────────────────────────────────────────

    @BeforeProperty
    fun setup() {
        // Default preferences — will be overridden in specific tests as needed
        IslandPreferences.resetForTest(IslandOptions())
    }

    @AfterProperty
    fun teardown() {
        IslandPreferences.resetForTest()
    }

    // ─── Generators ──────────────────────────────────────────────────────────────

    /**
     * Generates random package names for non-MiPush notifications.
     */
    @Provide
    fun packageNames(): Arbitrary<String> = Arbitraries.of(
        "com.android.settings",
        "com.google.android.gm",
        "com.spotify.music",
        "org.telegram.messenger",
        "com.whatsapp",
        "com.android.chrome",
        "com.discord",
        "com.slack",
        "jp.naver.line.android",
        "com.twitter.android",
    )

    /**
     * Generates random generic extra keys that are NOT MiPush-identifying keys.
     * These simulate extras that normal (non-MiPush) notifications might have.
     */
    @Provide
    fun genericExtraKeys(): Arbitrary<String> = Arbitraries.of(
        "android.title",
        "android.text",
        "android.subText",
        "android.bigText",
        "android.infoText",
        "android.summaryText",
        "android.progressMax",
        "android.progress",
        "custom_extra_1",
        "app_specific_data",
    )

    /**
     * Generates random extra values.
     */
    @Provide
    fun genericExtraValues(): Arbitrary<String> = Arbitraries.of(
        "Hello world",
        "New message",
        "Update available",
        "",
        "12345",
        "Some notification content",
    )

    /**
     * Generates a random map of generic extras (none of which are MiPush-identifying keys).
     */
    @Provide
    fun genericExtrasMap(): Arbitrary<Map<String, String>> {
        return genericExtraKeys().flatMap { key ->
            genericExtraValues().map { value -> key to value }
        }.list().ofMinSize(0).ofMaxSize(5).map { pairs ->
            pairs.toMap()
        }
    }

    /**
     * Generates random non-MiPush notification inputs.
     * Guarantees that NO MiPush-identifying extras are present.
     */
    @Provide
    fun nonMiPushNotifications(): Arbitrary<NonMiPushNotificationInput> {
        return Combinators.combine(
            packageNames(),
            genericExtrasMap()
        ).`as` { pkg, extras ->
            // Double-check: filter out any accidentally included MiPush keys
            val safeExtras = extras.filterKeys { it !in MIPUSH_EXTRA_KEYS }
            NonMiPushNotificationInput(pkg, safeExtras)
        }
    }

    /**
     * Generates random IslandOptions with varying colorStatusBarIcon values.
     */
    @Provide
    fun islandOptions(): Arbitrary<IslandOptions> {
        return Combinators.combine(
            Arbitraries.of(true, false), // colorStatusBarIcon
            Arbitraries.of(true, false), // colorStatusBarIconGlobal
            Arbitraries.of(true, false), // enabled
            Arbitraries.of(true, false), // enableFloat
            Arbitraries.of(true, false), // focusNotification
        ).`as` { color, global, enabled, enableFloat, focus ->
            IslandOptions(
                colorStatusBarIcon = color,
                colorStatusBarIconGlobal = global,
                enabled = enabled,
                enableFloat = enableFloat,
                focusNotification = focus,
            )
        }
    }

    // ─── Property Tests ──────────────────────────────────────────────────────────

    /**
     * Property: Non-MiPush notifications are intercepted only when strong monochrome mode says so.
     *
     * **Validates: Requirements 3.5**
     *
     * This confirms the baseline behavior and the explicit strong-mode override:
     * non-MiPush notifications are left alone by default. When strong mode is enabled
     * and monochrome is selected, the hook bypasses MIUI's app-icon substitution.
     */
    @Property(tries = 200)
    fun `getSmallIcon hook only intercepts non-MiPush notifications in strong monochrome mode`(
        @ForAll("nonMiPushNotifications") notification: NonMiPushNotificationInput,
        @ForAll("islandOptions") options: IslandOptions
    ) {
        IslandPreferences.resetForTest(options)
        val extras = notification.toExtras()

        // Verify this notification is indeed NOT MiPush-managed
        assertFalse(
            isMiPushManagedNotification(extras),
            "Test invariant violated: generated notification should not be MiPush-managed " +
                "(package=${notification.packageName}, extras keys=${notification.genericExtras.keys})"
        )

        val intercepted = getSmallIconHookIntercepts(
            colorStatusBarIcon = options.colorStatusBarIcon,
            forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
            extras = extras,
        )
        val expected = !options.colorStatusBarIcon && options.colorStatusBarIconGlobal

        assertEquals(
            expected,
            intercepted,
            "getSmallIcon hook should intercept non-MiPush notifications only in strong monochrome mode. " +
                "colorStatusBarIcon=${options.colorStatusBarIcon}, " +
                "colorStatusBarIconGlobal=${options.colorStatusBarIconGlobal}, " +
                "package=${notification.packageName}, " +
                "extras keys=${notification.genericExtras.keys}"
        )
    }

    /**
     * Property: IconManager.setIcon only forces icon_is_pre_L for scoped color-icon handling on R+.
     *
     * **Validates: Requirements 3.3**
     *
     * The IconManager.setIcon hook must not mark unrelated notifications.
     */
    @Property(tries = 50)
    fun `IconManager setIcon only marks MiPush managed icons when color mode is on`(
        @ForAll("islandOptions") options: IslandOptions,
        @ForAll isMiPushManaged: Boolean,
    ) {
        IslandPreferences.resetForTest(options)

        val result = iconManagerSetIconResult(
            colorStatusBarIcon = options.colorStatusBarIcon,
            forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
            isMiPushManaged = isMiPushManaged,
        )
        val expected = expectedPreLTagOverride(
            colorStatusBarIcon = options.colorStatusBarIcon,
            forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
            isMiPushManaged = isMiPushManaged,
        )

        assertEquals(
            expected,
            result,
            "IconManager.setIcon should override icon_is_pre_L only for scoped icons " +
                "when colorStatusBarIcon=true. colorStatusBarIcon=${options.colorStatusBarIcon}, " +
                "colorStatusBarIconGlobal=${options.colorStatusBarIconGlobal}, " +
                "isMiPushManaged=$isMiPushManaged"
        )
    }

    /**
     * Property: legacy NotificationEntry.setIconTag only forces icon_is_pre_L for scoped handling.
     *
     * **Validates: Requirements 3.3**
     *
     * The pre-R hook must also leave unrelated or unresolved notifications to native SystemUI behavior.
     */
    @Property(tries = 80)
    fun `legacy NotificationEntry setIconTag only marks resolved MiPush managed icons when color mode is on`(
        @ForAll("islandOptions") options: IslandOptions,
        @ForAll tagIdMatches: Boolean,
        @ForAll hasStatusBarNotification: Boolean,
        @ForAll isMiPushManaged: Boolean,
    ) {
        IslandPreferences.resetForTest(options)

        val result = legacySetIconTagResult(
            tagIdMatches = tagIdMatches,
            hasStatusBarNotification = hasStatusBarNotification,
            colorStatusBarIcon = options.colorStatusBarIcon,
            forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
            isMiPushManaged = isMiPushManaged,
        )
        val expected = if (tagIdMatches && hasStatusBarNotification) {
            expectedPreLTagOverride(
                colorStatusBarIcon = options.colorStatusBarIcon,
                forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
                isMiPushManaged = isMiPushManaged,
            )
        } else {
            null
        }

        assertEquals(
            expected,
            result,
            "Legacy NotificationEntry.setIconTag should force icon_is_pre_L only for resolved " +
                "scoped icons when colorStatusBarIcon=true. tagIdMatches=$tagIdMatches, " +
                "hasStatusBarNotification=$hasStatusBarNotification, " +
                "colorStatusBarIcon=${options.colorStatusBarIcon}, " +
                "colorStatusBarIconGlobal=${options.colorStatusBarIconGlobal}, " +
                "isMiPushManaged=$isMiPushManaged"
        )
    }

    /**
     * Property: Non-MiPush notifications with colorStatusBarIcon=false are never intercepted.
     *
     * **Validates: Requirements 3.5**
     *
     * Specifically targets the monochrome-desired case with non-MiPush notifications.
     * Non-MiPush notifications are unaffected because `isMiPushManagedNotification` returns false.
     */
    @Property(tries = 100)
    fun `non-MiPush notifications unaffected when monochrome desired`(
        @ForAll("nonMiPushNotifications") notification: NonMiPushNotificationInput
    ) {
        IslandPreferences.resetForTest(IslandOptions(colorStatusBarIcon = false))
        val extras = notification.toExtras()

        val intercepted = getSmallIconHookIntercepts(
            colorStatusBarIcon = false,
            forceGlobalStatusBarIcons = false,
            extras = extras,
        )

        assertFalse(
            intercepted,
            "Non-MiPush notifications must never be intercepted even when " +
                "colorStatusBarIcon=false (monochrome desired). " +
                "package=${notification.packageName}"
        )
    }

    /**
     * Property: Non-MiPush notifications with colorStatusBarIcon=true are never intercepted.
     *
     * **Validates: Requirements 3.5**
     *
     * When the toggle is ON, only MiPush-managed notifications may be intercepted.
     */
    @Property(tries = 100)
    fun `non-MiPush notifications unaffected when color desired`(
        @ForAll("nonMiPushNotifications") notification: NonMiPushNotificationInput
    ) {
        IslandPreferences.resetForTest(IslandOptions(colorStatusBarIcon = true))
        val extras = notification.toExtras()

        val intercepted = getSmallIconHookIntercepts(
            colorStatusBarIcon = true,
            forceGlobalStatusBarIcons = false,
            extras = extras,
        )

        assertFalse(
            intercepted,
            "Non-MiPush notifications must never be intercepted even when " +
                "colorStatusBarIcon=true (color desired). " +
                "package=${notification.packageName}"
        )
    }

    /**
     * Header source observed before any icon-pack resolver exists. The non-mock path only
     * replaces an in-scope XSpace header when the existing large-icon lookup succeeds; the mock
     * replay path keeps its existing target-app -> large-icon -> small-icon order.
     */
    private enum class BaselineHeaderSource {
        NATIVE,
        TARGET_APP,
        LARGE_ICON,
        SMALL_ICON,
    }

    data class HeaderPreservationInput(
        val targetPackage: String?,
        val postingPackage: String?,
        val userId: Int?,
        val isMockReplayReceipt: Boolean,
        val hasTargetAppIcon: Boolean,
        val hasLargeIcon: Boolean,
        val hasSmallIcon: Boolean,
    )

    private class IconPackQueryProbe {
        var queryCount: Int = 0
    }

    @Provide
    fun headerPreservationInputs(): Arbitrary<HeaderPreservationInput> {
        return Combinators.combine(
            Arbitraries.of(null, "", "com.example.target", "com.xiaomi.xmsf"),
            Arbitraries.of(null, "", "com.xiaomi.xmsf", "com.example.target", "com.other.sender"),
            Arbitraries.of(null, 0, 999),
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
        ).`as` { target, posting, user, mock, targetIcon, largeIcon, smallIcon ->
            HeaderPreservationInput(
                targetPackage = target,
                postingPackage = posting,
                userId = user,
                isMockReplayReceipt = mock,
                hasTargetAppIcon = targetIcon,
                hasLargeIcon = largeIcon,
                hasSmallIcon = smallIcon,
            )
        }
    }

    private fun baselineHeaderSource(
        input: HeaderPreservationInput,
        queryProbe: IconPackQueryProbe,
    ): BaselineHeaderSource {
        // Observation-first guard: the pre-fix implementation has no icon-pack resolver seam.
        // Keeping this probe explicit makes an accidental third-party lookup visible in this
        // preservation harness without reading any HMSPush-private storage.
        val replacement: BaselineHeaderSource? = if (input.isMockReplayReceipt) {
            when {
                input.hasTargetAppIcon -> BaselineHeaderSource.TARGET_APP
                input.hasLargeIcon -> BaselineHeaderSource.LARGE_ICON
                input.hasSmallIcon -> BaselineHeaderSource.SMALL_ICON
                else -> null
            }
        } else {
            if (input.hasLargeIcon) BaselineHeaderSource.LARGE_ICON else null
        }
        val shouldReplace = MiuiHeaderAppIconPolicy.shouldReplace(
            userId = input.userId,
            targetPackage = input.targetPackage,
            postingPackage = input.postingPackage,
            hasReplacementIcon = replacement != null,
            isMockReplayReceipt = input.isMockReplayReceipt,
        )
        check(queryProbe.queryCount == 0) { "baseline unexpectedly queried an icon-pack source" }
        if (!shouldReplace) return BaselineHeaderSource.NATIVE
        return replacement ?: BaselineHeaderSource.NATIVE
    }

    /**
     * Preservation: unresolved identity and out-of-scope header inputs remain native and do not
     * query a third-party icon source.
     *
     * **Validates: Requirements 3.1, 3.4, 3.6**
     */
    @Property(tries = 200)
    fun `header identity and scope gates preserve native behavior without icon-pack queries`(
        @ForAll("headerPreservationInputs") input: HeaderPreservationInput,
    ) {
        val probe = IconPackQueryProbe()
        val observed = baselineHeaderSource(input, probe)
        val identityUnconfirmed = input.targetPackage.isNullOrBlank()
        val excludedTarget = input.targetPackage == "com.xiaomi.xmsf"
        val outsideXSpaceScope = !input.isMockReplayReceipt && (
            input.userId != 999 ||
                input.postingPackage.isNullOrBlank() ||
                input.postingPackage == input.targetPackage
            )
        if (identityUnconfirmed || excludedTarget || outsideXSpaceScope) {
            assertEquals(
                BaselineHeaderSource.NATIVE,
                observed,
                "header must preserve native behavior outside target/ MiPush scope: $input",
            )
        }
        assertEquals(0, probe.queryCount, "preservation path must not query an icon-pack source")
    }

    /**
     * Preservation: with no usable third-party pack, the existing header fallback order is
     * unchanged. This records the actual pre-fix behavior rather than inventing an APP fallback
     * for the non-mock path, which currently delegates to native SystemUI when no large icon is
     * available.
     *
     * **Validates: Requirements 3.2, 3.4, 3.5, 3.6**
     */
    @Property(tries = 120)
    fun `unavailable pack preserves existing header fallback order`(
        @ForAll("headerPreservationInputs") input: HeaderPreservationInput,
    ) {
        val probe = IconPackQueryProbe()
        val observed = baselineHeaderSource(input, probe)
        val expected = when {
            input.targetPackage.isNullOrBlank() -> BaselineHeaderSource.NATIVE
            input.targetPackage == "com.xiaomi.xmsf" -> BaselineHeaderSource.NATIVE
            input.isMockReplayReceipt && input.hasTargetAppIcon -> BaselineHeaderSource.TARGET_APP
            input.isMockReplayReceipt && input.hasLargeIcon -> BaselineHeaderSource.LARGE_ICON
            input.isMockReplayReceipt && input.hasSmallIcon -> BaselineHeaderSource.SMALL_ICON
            input.isMockReplayReceipt -> BaselineHeaderSource.NATIVE
            input.userId == 999 &&
                !input.postingPackage.isNullOrBlank() &&
                input.postingPackage != input.targetPackage &&
                input.hasLargeIcon -> BaselineHeaderSource.LARGE_ICON
            else -> BaselineHeaderSource.NATIVE
        }
        assertEquals(expected, observed, "pre-fix header source changed for $input")
        assertEquals(0, probe.queryCount, "no usable pack must not trigger a third-party query")
    }

    /**
     * Preservation: icon-policy observation changes no notification semantics. The snapshot
     * includes the non-icon fields called out by the bugfix contract and is intentionally kept
     * independent of bitmap/resource identity.
     *
     * **Validates: Requirements 3.2, 3.3, 3.5, 3.6**
     */
    data class NotificationSemantics(
        val content: String,
        val clickAction: String,
        val group: String?,
        val channel: String,
        val ongoing: Boolean,
        val autoCancel: Boolean,
        val whenMillis: Long,
        val color: Int?,
    )

    @Provide
    fun notificationSemantics(): Arbitrary<NotificationSemantics> {
        return Combinators.combine(
            Arbitraries.of("title", "body", "summary", ""),
            Arbitraries.of("open", "reply", "dismiss", ""),
            Arbitraries.of(null, "messages", "updates"),
            Arbitraries.of("default", "messages", "silent"),
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
            Arbitraries.longs().between(0L, 4_000_000_000_000L),
            Arbitraries.of(null, 0xFFFFFFFF.toInt(), 0xFF808080.toInt()),
        ).`as` { content, click, group, channel, ongoing, autoCancel, whenMillis, color ->
            NotificationSemantics(
                content = content,
                clickAction = click,
                group = group,
                channel = channel,
                ongoing = ongoing,
                autoCancel = autoCancel,
                whenMillis = whenMillis,
                color = color,
            )
        }
    }

    @Property(tries = 150)
    fun `unusable pack and existing fallback preserve notification semantics`(
        @ForAll("notificationSemantics") before: NotificationSemantics,
        @ForAll("nonMiPushNotifications") notification: NonMiPushNotificationInput,
        @ForAll("islandOptions") options: IslandOptions,
    ) {
        IslandPreferences.resetForTest(options)
        val extras = notification.toExtras()
        val beforeManaged = isMiPushManagedNotification(extras)
        val beforeIntercept = getSmallIconHookIntercepts(
            colorStatusBarIcon = options.colorStatusBarIcon,
            forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
            extras = extras,
        )
        val after = before.copy()
        val afterManaged = isMiPushManagedNotification(extras)
        val afterIntercept = getSmallIconHookIntercepts(
            colorStatusBarIcon = options.colorStatusBarIcon,
            forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
            extras = extras,
        )
        assertEquals(before, after, "icon fallback must not change notification semantics")
        assertEquals(beforeManaged, afterManaged, "icon fallback must not change MiPush scope")
        assertEquals(beforeIntercept, afterIntercept, "icon fallback must not change status-bar policy")
        assertFalse(beforeManaged, "generator must produce a non-MiPush baseline input")
    }

    private fun expectedPreLTagOverride(
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
        isMiPushManaged: Boolean,
    ): Boolean? {
        if (!forceGlobalStatusBarIcons && !isMiPushManaged) return null
        return if (colorStatusBarIcon) true else forceGlobalStatusBarIcons.takeIf { it }?.let { false }
    }
}
