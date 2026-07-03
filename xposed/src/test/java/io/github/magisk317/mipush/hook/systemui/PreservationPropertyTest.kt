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
 * - Non-MiPush notifications must never be intercepted by the getSmallIcon hook
 * - processSmallIconColor must return early when toggle OFF or the notification is not MiPush-managed,
 *   and process MiPush-managed non-grayscale icons when toggle ON
 * - IconManager.setIcon must only force icon_is_pre_L for MiPush-managed notifications when color mode is ON
 *
 * The important invariant is that non-MiPush notifications are left to native SystemUI behavior.
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
            IslandDispatchContract.OWNER,
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

    /**
     * Represents the inputs for processSmallIconColor decision logic.
     */
    data class ProcessSmallIconColorInput(
        val colorStatusBarIcon: Boolean,
        val isGrayscaleIcon: Boolean,
        val isMiPushManaged: Boolean,
    )

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
    private fun getSmallIconHookIntercepts(colorStatusBarIcon: Boolean, extras: NotificationExtras): Boolean {
        return SystemUiNotificationPolicy.shouldInterceptSmallIcon(
            colorStatusBarIcon = colorStatusBarIcon,
            isMiPushManaged = isMiPushManagedNotification(extras),
        )
    }

    /**
     * Replicates the processSmallIconColor hook's doBefore decision logic.
     *
     * From HookSystemUI.kt:
     * ```
     * if (!IslandPreferences.current().colorStatusBarIcon) {
     *     return@doBefore  // Toggle OFF: let MIUI's native processSmallIconColor run
     * }
     * ...
     * if (!isGrayscaleIcon) {
     *     contentView.setInt(android.R.id.icon, "setOriginalIconColor", 1)
     *     result = true
     * }
     * ```
     *
     * Returns a sealed result representing the hook's action.
     */
    sealed class ProcessSmallIconColorResult {
        /** Hook returns early, letting MIUI handle it natively */
        object ReturnEarly : ProcessSmallIconColorResult()
        /** Hook sets setOriginalIconColor(1) and result=true for non-grayscale icons */
        object SetOriginalIconColor : ProcessSmallIconColorResult()
        /** Hook does nothing (grayscale icon with toggle ON — MIUI handles) */
        object NoAction : ProcessSmallIconColorResult()
    }

    private fun processSmallIconColorDecision(input: ProcessSmallIconColorInput): ProcessSmallIconColorResult {
        if (!input.colorStatusBarIcon || !input.isMiPushManaged) {
            return ProcessSmallIconColorResult.ReturnEarly
        }
        if (SystemUiNotificationPolicy.shouldApplySmallIconColor(
                colorStatusBarIcon = input.colorStatusBarIcon,
                isMiPushManaged = input.isMiPushManaged,
                isGrayscaleIcon = input.isGrayscaleIcon,
            )
        ) {
            return ProcessSmallIconColorResult.SetOriginalIconColor
        }
        return ProcessSmallIconColorResult.NoAction
    }

    /**
     * Replicates IconManager.setIcon hook's doAfter logic.
     *
     * @return true when the hook should force icon_is_pre_L on the icon view.
     */
    private fun iconManagerSetIconResult(colorStatusBarIcon: Boolean, isMiPushManaged: Boolean): Boolean {
        return SystemUiNotificationPolicy.shouldForcePreLIconTag(
            colorStatusBarIcon = colorStatusBarIcon,
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
        isMiPushManaged: Boolean,
    ): Boolean {
        if (!tagIdMatches) return false
        if (!hasStatusBarNotification) return false
        return SystemUiNotificationPolicy.shouldForcePreLIconTag(
            colorStatusBarIcon = colorStatusBarIcon,
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
            Arbitraries.of(true, false), // enabled
            Arbitraries.of(true, false), // enableFloat
            Arbitraries.of(true, false), // focusNotification
        ).`as` { color, enabled, enableFloat, focus ->
            IslandOptions(
                colorStatusBarIcon = color,
                enabled = enabled,
                enableFloat = enableFloat,
                focusNotification = focus,
            )
        }
    }

    /**
     * Generates random processSmallIconColor inputs.
     */
    @Provide
    fun processSmallIconColorInputs(): Arbitrary<ProcessSmallIconColorInput> {
        return Combinators.combine(
            Arbitraries.of(true, false), // colorStatusBarIcon
            Arbitraries.of(true, false), // isGrayscaleIcon
            Arbitraries.of(true, false), // isMiPushManaged
        ).`as` { color, grayscale, isMiPushManaged ->
            ProcessSmallIconColorInput(color, grayscale, isMiPushManaged)
        }
    }

    // ─── Property Tests ──────────────────────────────────────────────────────────

    /**
     * Property: Non-MiPush notifications are NEVER intercepted by the getSmallIcon hook,
     * regardless of the colorStatusBarIcon toggle state.
     *
     * **Validates: Requirements 3.5**
     *
     * This confirms the baseline behavior: non-MiPush notifications are left alone.
     * This must hold on both unfixed and fixed code.
     */
    @Property(tries = 200)
    fun `getSmallIcon hook never intercepts non-MiPush notifications regardless of toggle`(
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

        // Verify the hook does NOT intercept
        val intercepted = getSmallIconHookIntercepts(options.colorStatusBarIcon, extras)

        assertFalse(
            intercepted,
            "getSmallIcon hook must NEVER intercept non-MiPush notifications. " +
                "colorStatusBarIcon=${options.colorStatusBarIcon}, " +
                "package=${notification.packageName}, " +
                "extras keys=${notification.genericExtras.keys}"
        )
    }

    /**
     * Property: processSmallIconColor returns early (lets MIUI handle) when toggle is OFF.
     *
     * **Validates: Requirements 3.4**
     *
     * When colorStatusBarIcon is false, the hook's guard condition
     * `if (!colorStatusBarIcon) { return@doBefore }` causes an early return,
     * allowing MIUI's native processSmallIconColor to run.
     */
    @Property(tries = 100)
    fun `processSmallIconColor returns early when colorStatusBarIcon is false`(
        @ForAll("processSmallIconColorInputs") input: ProcessSmallIconColorInput
    ) {
        if (!input.colorStatusBarIcon) {
            val result = processSmallIconColorDecision(input)
            assertEquals(
                ProcessSmallIconColorResult.ReturnEarly,
                result,
                "When colorStatusBarIcon=false, processSmallIconColor must return early " +
                    "to let MIUI's native logic handle it. " +
                    "isGrayscaleIcon=${input.isGrayscaleIcon}, isMiPushManaged=${input.isMiPushManaged}"
            )
        }
    }

    /**
     * Property: processSmallIconColor sets setOriginalIconColor(1) for non-grayscale icons
     * when toggle is ON.
     *
     * **Validates: Requirements 3.1, 3.2**
     *
     * When colorStatusBarIcon is true and the icon is NOT grayscale, the hook
     * calls `setOriginalIconColor(1)` to preserve color.
     * When the icon IS grayscale, the hook does nothing (lets MIUI handle).
     */
    @Property(tries = 100)
    fun `processSmallIconColor handles icons correctly when colorStatusBarIcon is true`(
        @ForAll("processSmallIconColorInputs") input: ProcessSmallIconColorInput
    ) {
        if (input.colorStatusBarIcon) {
            val result = processSmallIconColorDecision(input)
            if (!input.isMiPushManaged) {
                assertEquals(
                    ProcessSmallIconColorResult.ReturnEarly,
                    result,
                    "When colorStatusBarIcon=true but notification is not MiPush-managed, " +
                        "processSmallIconColor must return early"
                )
            } else if (!input.isGrayscaleIcon) {
                assertEquals(
                    ProcessSmallIconColorResult.SetOriginalIconColor,
                    result,
                    "When colorStatusBarIcon=true and icon is NOT grayscale, " +
                        "processSmallIconColor must set setOriginalIconColor(1)"
                )
            } else {
                assertEquals(
                    ProcessSmallIconColorResult.NoAction,
                    result,
                    "When colorStatusBarIcon=true and icon IS grayscale, " +
                        "processSmallIconColor must do nothing (let MIUI handle)"
                )
            }
        }
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

        val result = iconManagerSetIconResult(options.colorStatusBarIcon, isMiPushManaged)

        assertEquals(
            options.colorStatusBarIcon && isMiPushManaged,
            result,
            "IconManager.setIcon should force icon_is_pre_L only for MiPush-managed icons " +
                "when colorStatusBarIcon=true. colorStatusBarIcon=${options.colorStatusBarIcon}, " +
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
            isMiPushManaged = isMiPushManaged,
        )

        assertEquals(
            tagIdMatches && hasStatusBarNotification && options.colorStatusBarIcon && isMiPushManaged,
            result,
            "Legacy NotificationEntry.setIconTag should force icon_is_pre_L only for resolved " +
                "MiPush-managed icons when colorStatusBarIcon=true. tagIdMatches=$tagIdMatches, " +
                "hasStatusBarNotification=$hasStatusBarNotification, " +
                "colorStatusBarIcon=${options.colorStatusBarIcon}, isMiPushManaged=$isMiPushManaged"
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

        val intercepted = getSmallIconHookIntercepts(false, extras)

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

        val intercepted = getSmallIconHookIntercepts(true, extras)

        assertFalse(
            intercepted,
            "Non-MiPush notifications must never be intercepted even when " +
                "colorStatusBarIcon=true (color desired). " +
                "package=${notification.packageName}"
        )
    }
}
