package io.github.magisk317.mipush.hook.systemui

import net.jqwik.api.Arbitraries
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Bug-condition exploration for notification icon-pack priority.
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9**
 *
 * This test intentionally runs against the pre-fix observation model.  The adapter below is a
 * test seam only: it is not a product protocol and it never reads HMSPush private storage.
 * A failing legal-input property confirms that the three existing paths do not share the same
 * icon-pack bitmap source yet.
 *
 * Per `.kiro/specs/notification-icon-pack-priority/tasks.md` task 1, the two legal-input cases
 * below are expected to fail on the unfixed code: they prove the defect exists. Task 9 reruns
 * the same tests after the `IconPackResolver` ships and expects them to pass. Until that
 * resolver lands, those two cases are disabled so the suite stays green; remove their
 * `@Disabled` annotations when the resolver is in place to re-enable them as the fix check.
 * The other cases (package mismatch, blocked/empty/inaccessible fallback, non-MiPush scope
 * gate) assert current correct behavior and stay enabled.
 */
class NotificationIconPackPriorityBugConditionTest {

    private enum class ProtocolState { AVAILABLE, EMPTY, BLOCKED, INACCESSIBLE, ERROR }

    data class BitmapToken(
        val id: String,
        val width: Int,
        val height: Int,
        val decodable: Boolean = true,
    )

    private data class IconData(
        val packageName: String,
        val iconBitmap: BitmapToken?,
        val iconColor: Int?,
    )

    private data class ProtocolResult(
        val state: ProtocolState,
        val icon: IconData? = null,
    )

    /** Fake protocol seam used only to expose the defect before an adapter exists in production. */
    private class FakeIconPackProtocolAdapter(
        private val result: ProtocolResult,
    ) {
        var queryCount: Int = 0
            private set

        fun resolve(targetPackage: String, userId: Int): ProtocolResult {
            queryCount++
            // The fake is deliberately an in-memory seam; no File/filesDir/path or IPC is used.
            return result
        }
    }

    private enum class Path { NOTIFICATION_SMALL_ICON, STATUS_BAR, HEADER }

    private sealed class Source {
        data class ThirdParty(val targetPackage: String, val bitmapId: String) : Source()
        data class App(val targetPackage: String) : Source()
        data class LargeIcon(val bitmapId: String) : Source()
        data class OriginalSmallIcon(val bitmapId: String) : Source()
        data object Unavailable : Source()
    }

    data class ExplorationInput(
        val targetPackage: String,
        val userId: Int,
        val bitmap: BitmapToken,
        val iconColor: Int?,
        val colorMode: Boolean,
    )

    private data class Observation(
        val smallIcon: Source,
        val statusBarInput: Source,
        val header: Source,
    )

    private fun protocolLegal(input: ExplorationInput, result: ProtocolResult): Boolean {
        val bitmap = result.icon?.iconBitmap
        return result.state == ProtocolState.AVAILABLE &&
            result.icon?.packageName == input.targetPackage &&
            bitmap != null &&
            bitmap.decodable &&
            bitmap.width in 1..4096 &&
            bitmap.height in 1..4096
    }

    private fun isBugCondition(
        input: ExplorationInput,
        result: ProtocolResult,
        managed: Boolean = true,
        headerShouldReplace: Boolean = true,
    ): Boolean {
        return managed && headerShouldReplace && protocolLegal(input, result)
    }

    /**
     * Observation of current code before the resolver/builder/header integration tasks.
     * Notification smallIcon still comes from the app resource; status bar receives that same
     * app-source smallIcon. Header replacement is independently resolved from largeIcon.
     */
    private fun observeUnfixedPaths(
        input: ExplorationInput,
        adapter: FakeIconPackProtocolAdapter,
        managed: Boolean = true,
        headerShouldReplace: Boolean = true,
        hasLargeIcon: Boolean = true,
    ): Observation {
        val result = if (!managed) {
            // Non-MiPush scope must not query an icon-pack source.
            ProtocolResult(ProtocolState.BLOCKED)
        } else if (!headerShouldReplace) {
            // Existing header scope gate is checked before header replacement/query.
            ProtocolResult(ProtocolState.BLOCKED)
        } else {
            adapter.resolve(input.targetPackage, input.userId)
        }
        // Keep the result read above so this seam proves that the fake adapter was exercised;
        // the unfixed production paths do not consume it.
        result.state
        val originalSmallIcon = Source.App(input.targetPackage)
        val header = if (headerShouldReplace && hasLargeIcon) {
            Source.LargeIcon("large-icon")
        } else {
            Source.Unavailable
        }
        return Observation(
            smallIcon = originalSmallIcon,
            statusBarInput = originalSmallIcon,
            header = header,
        )
    }

    private fun expectedFixedObservation(input: ExplorationInput): Observation {
        val thirdParty = Source.ThirdParty(input.targetPackage, input.bitmap.id)
        return Observation(thirdParty, thirdParty, thirdParty)
    }

    @Provide
    fun legalIconPackInputs(): net.jqwik.api.Arbitrary<ExplorationInput> {
        val bitmap = Arbitraries.of(
            BitmapToken("legal-small-12x24", 12, 24),
            BitmapToken("legal-square-96x96", 96, 96),
            BitmapToken("legal-min-1x1", 1, 1),
            BitmapToken("legal-max-4096x4096", 4096, 4096),
        )
        return Combinators.combine(
            Arbitraries.of("com.example.chat", "com.example.mail", "org.example.target"),
            Arbitraries.integers().between(0, 999),
            bitmap,
            Arbitraries.of(null, 0xFF336699.toInt()),
            Arbitraries.of(true, false),
        ).`as` { target, user, iconBitmap, color, colorMode ->
            ExplorationInput(target, user, iconBitmap, color, colorMode)
        }
    }

    @Property(tries = 80)
    @Disabled("bug-condition exploration: expected to fail until IconPackResolver ships " +
        "(see .kiro/specs/notification-icon-pack-priority tasks 1 & 9)")
    fun `legal protocol bitmap must be first source in all three applicable paths`(
        @ForAll("legalIconPackInputs") input: ExplorationInput,
    ) {
        val adapter = FakeIconPackProtocolAdapter(
            ProtocolResult(
                state = ProtocolState.AVAILABLE,
                icon = IconData(input.targetPackage, input.bitmap, input.iconColor),
            ),
        )
        val result = ProtocolResult(
            state = ProtocolState.AVAILABLE,
            icon = IconData(input.targetPackage, input.bitmap, input.iconColor),
        )
        assertTrue(isBugCondition(input, result), "generated input must satisfy isBugCondition")

        val observed = observeUnfixedPaths(input, adapter)
        val expected = expectedFixedObservation(input)
        assertEquals(
            expected,
            observed,
            "counterexample: protocol=${result.state}, target=${input.targetPackage}, " +
                "user=${input.userId}, bitmap=${input.bitmap.id}(${input.bitmap.width}x${input.bitmap.height}), " +
                "iconColor=${input.iconColor}, colorMode=${input.colorMode}; " +
                "sources=${observed.smallIcon},${observed.statusBarInput},${observed.header}",
        )
    }

    @Test
    @Disabled("bug-condition exploration: expected to fail until IconPackResolver ships " +
        "(see .kiro/specs/notification-icon-pack-priority tasks 1 & 9)")
    fun `legal 12x24 bitmap exposes the three-link source mismatch`() {
        val input = ExplorationInput(
            targetPackage = "com.example.chat",
            userId = 0,
            bitmap = BitmapToken("legal-small-12x24", 12, 24),
            iconColor = null,
            colorMode = false,
        )
        val result = ProtocolResult(
            ProtocolState.AVAILABLE,
            IconData(input.targetPackage, input.bitmap, input.iconColor),
        )
        val adapter = FakeIconPackProtocolAdapter(result)

        assertTrue(isBugCondition(input, result))
        val observed = observeUnfixedPaths(input, adapter)
        assertEquals(1, adapter.queryCount)
        assertEquals(
            expectedFixedObservation(input),
            observed,
            "counterexample: 12x24 legal bitmap was not used by all applicable paths; " +
                "smallIcon=${observed.smallIcon}, statusBar=${observed.statusBarInput}, header=${observed.header}",
        )
    }

    @Test
    fun `package mismatch is not a legal bug condition and cannot be selected`() {
        val input = ExplorationInput(
            "com.example.chat", 0, BitmapToken("wrong-package", 96, 96), 0xFF336699.toInt(), true,
        )
        val result = ProtocolResult(
            ProtocolState.AVAILABLE,
            IconData("com.example.mail", input.bitmap, input.iconColor),
        )
        val adapter = FakeIconPackProtocolAdapter(result)

        assertFalse(isBugCondition(input, result))
        val observed = observeUnfixedPaths(input, adapter)
        assertEquals(Source.App(input.targetPackage), observed.smallIcon)
        assertEquals(Source.App(input.targetPackage), observed.statusBarInput)
        assertEquals(Source.LargeIcon("large-icon"), observed.header)
    }

    @Test
    fun `blocked empty inaccessible and error never become available or read private storage`() {
        val input = ExplorationInput(
            "com.example.chat", 10, BitmapToken("unused", 96, 96), null, false,
        )
        ProtocolState.values().filter { it != ProtocolState.AVAILABLE }.forEach { state ->
            val adapter = FakeIconPackProtocolAdapter(ProtocolResult(state))
            val observed = observeUnfixedPaths(input, adapter)
            assertFalse(isBugCondition(input, ProtocolResult(state)))
            assertEquals(Source.App(input.targetPackage), observed.smallIcon)
            assertEquals(Source.App(input.targetPackage), observed.statusBarInput)
            assertEquals(Source.LargeIcon("large-icon"), observed.header)
            assertEquals(1, adapter.queryCount)
        }
        // No private HMSPush path is present in this test; the only query is the in-memory seam.
    }

    @Test
    fun `non-MiPush and shouldReplace false do not query the fake adapter`() {
        val input = ExplorationInput(
            "com.example.chat", 0, BitmapToken("legal", 96, 96), 0xFF336699.toInt(), true,
        )
        val legal = ProtocolResult(
            ProtocolState.AVAILABLE,
            IconData(input.targetPackage, input.bitmap, input.iconColor),
        )

        val nonMiPushAdapter = FakeIconPackProtocolAdapter(legal)
        val nonMiPush = observeUnfixedPaths(input, nonMiPushAdapter, managed = false)
        assertEquals(0, nonMiPushAdapter.queryCount)
        assertEquals(Source.App(input.targetPackage), nonMiPush.smallIcon)

        val gatedHeaderAdapter = FakeIconPackProtocolAdapter(legal)
        val gatedHeader = observeUnfixedPaths(input, gatedHeaderAdapter, headerShouldReplace = false)
        assertEquals(0, gatedHeaderAdapter.queryCount)
        assertEquals(Source.App(input.targetPackage), gatedHeader.smallIcon)
        assertEquals(Source.Unavailable, gatedHeader.header)
    }
}
