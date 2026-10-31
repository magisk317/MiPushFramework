package io.github.magisk317.mipush.hook.systemui

import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiuiHeaderAppIconPolicyTest {
    @Test
    fun `replaces only xspace fallback identities with a resolved icon`() {
        assertTrue(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 999,
                targetPackage = "com.example.app",
                postingPackage = "com.xiaomi.xmsf",
                hasReplacementIcon = true,
            )
        )

        assertFalse(
            MiuiHeaderAppIconPolicy.shouldReplace(0, "com.example.app", "com.xiaomi.xmsf", true)
        )
        assertFalse(MiuiHeaderAppIconPolicy.shouldReplace(999, "", "com.xiaomi.xmsf", true))
        assertFalse(MiuiHeaderAppIconPolicy.shouldReplace(999, "com.xiaomi.xmsf", "com.xiaomi.xmsf", true))
        assertFalse(MiuiHeaderAppIconPolicy.shouldReplace(999, "com.example.app", "com.xiaomi.xmsf", false))
        assertFalse(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 999,
                targetPackage = "com.example.app",
                postingPackage = "com.example.app",
                hasReplacementIcon = true,
            )
        )
    }

    @Test
    fun `replaces mock replay receipt header icon outside xspace`() {
        assertTrue(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 0,
                targetPackage = "com.example.app",
                postingPackage = "com.xiaomi.xmsf",
                hasReplacementIcon = true,
                isMockReplayReceipt = true,
            )
        )

        assertFalse(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 0,
                targetPackage = "com.xiaomi.xmsf",
                postingPackage = "com.xiaomi.xmsf",
                hasReplacementIcon = true,
                isMockReplayReceipt = true,
            )
        )
        assertFalse(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 0,
                targetPackage = "com.example.app",
                postingPackage = "com.xiaomi.xmsf",
                hasReplacementIcon = false,
                isMockReplayReceipt = true,
            )
        )
    }

    @Test
    fun `header selector is app when the target icon resolves`() {
        assertEquals(
            MiuiHeaderAppIconSource.APP,
            MiuiHeaderAppIconPolicy.selectReplacementSource(hasTargetAppIcon = true),
        )
        assertEquals(
            MiuiHeaderAppIconSource.UNAVAILABLE,
            MiuiHeaderAppIconPolicy.selectReplacementSource(hasTargetAppIcon = false),
        )
    }

    @Test
    fun `monochrome shade source mirrors the status bar icon chain`() {
        assertEquals(
            MiuiHeaderShadeSource.ICON_PACK,
            MiuiHeaderAppIconPolicy.selectMonochromeShadeSource(
                isMiPushManaged = true,
                attemptsIconPack = true,
                iconPackHit = true,
                isSyntheticAutogroupSummary = false,
                postedGlyphIsMonochrome = false,
                postedGlyphIsUnusable = false,
            ),
        )
        assertEquals(
            MiuiHeaderShadeSource.SILHOUETTE,
            MiuiHeaderAppIconPolicy.selectMonochromeShadeSource(
                isMiPushManaged = true,
                attemptsIconPack = true,
                iconPackHit = false,
                isSyntheticAutogroupSummary = false,
                postedGlyphIsMonochrome = false,
                postedGlyphIsUnusable = false,
            ),
        )
        // An unmanaged app keeps its own grayscale glyph instead of a synthesized silhouette.
        assertEquals(
            MiuiHeaderShadeSource.POSTED_GLYPH,
            MiuiHeaderAppIconPolicy.selectMonochromeShadeSource(
                isMiPushManaged = false,
                attemptsIconPack = true,
                iconPackHit = false,
                isSyntheticAutogroupSummary = false,
                postedGlyphIsMonochrome = true,
                postedGlyphIsUnusable = false,
            ),
        )
        assertEquals(
            MiuiHeaderShadeSource.ICON_PACK,
            MiuiHeaderAppIconPolicy.selectMonochromeShadeSource(
                isMiPushManaged = false,
                attemptsIconPack = true,
                iconPackHit = true,
                isSyntheticAutogroupSummary = false,
                postedGlyphIsMonochrome = false,
                postedGlyphIsUnusable = false,
            ),
        )
        // A pack miss with a colorful posted glyph stays native: synthesizing a silhouette for an
        // app the module does not own the glyph for is what produced blank and blob headers.
        assertEquals(
            MiuiHeaderShadeSource.NONE,
            MiuiHeaderAppIconPolicy.selectMonochromeShadeSource(
                isMiPushManaged = false,
                attemptsIconPack = true,
                iconPackHit = false,
                isSyntheticAutogroupSummary = false,
                postedGlyphIsMonochrome = false,
                postedGlyphIsUnusable = false,
            ),
        )
        assertEquals(
            MiuiHeaderShadeSource.NONE,
            MiuiHeaderAppIconPolicy.selectMonochromeShadeSource(
                isMiPushManaged = false,
                attemptsIconPack = false,
                iconPackHit = false,
                isSyntheticAutogroupSummary = false,
                postedGlyphIsMonochrome = false,
                postedGlyphIsUnusable = false,
            ),
        )
    }

    @Test
    fun `monochrome shade leaves native rendering only outside the switch scope`() {
        runBlocking {
            checkAll(
                40,
                Arb.boolean(),
                Arb.boolean(),
                Arb.boolean(),
                Arb.boolean(),
                Arb.boolean(),
                Arb.boolean(),
            ) {
                    isMiPushManaged,
                    attemptsIconPack,
                    iconPackHit,
                    isSyntheticAutogroupSummary,
                    postedGlyphIsMonochrome,
                    postedGlyphIsUnusable,
                ->
                val source = MiuiHeaderAppIconPolicy.selectMonochromeShadeSource(
                    isMiPushManaged = isMiPushManaged,
                    attemptsIconPack = attemptsIconPack,
                    iconPackHit = iconPackHit,
                    isSyntheticAutogroupSummary = isSyntheticAutogroupSummary,
                    postedGlyphIsMonochrome = postedGlyphIsMonochrome,
                    postedGlyphIsUnusable = postedGlyphIsUnusable,
                )
                val expected = when {
                    attemptsIconPack && iconPackHit -> MiuiHeaderShadeSource.ICON_PACK
                    isMiPushManaged -> MiuiHeaderShadeSource.SILHOUETTE
                    attemptsIconPack && isSyntheticAutogroupSummary -> MiuiHeaderShadeSource.SILHOUETTE
                    attemptsIconPack && postedGlyphIsUnusable -> MiuiHeaderShadeSource.SILHOUETTE
                    attemptsIconPack && postedGlyphIsMonochrome -> MiuiHeaderShadeSource.POSTED_GLYPH
                    else -> MiuiHeaderShadeSource.NONE
                }
                assertEquals(expected, source)
                // An unmanaged app only ever gets a synthesized silhouette when its own posted
                // glyph is unusable (resId=0, or the framework generic auto-group mark) or when the
                // record is an auto-group summary -- never because nobody configured it.
                if (!isMiPushManaged && !iconPackHit && source == MiuiHeaderShadeSource.SILHOUETTE) {
                    assertTrue(attemptsIconPack && (postedGlyphIsUnusable || isSyntheticAutogroupSummary))
                }
            }
        }
    }

    @Test
    fun `monochrome shade resolves auto-group summaries like the status bar`() {
        // The summary copies the child's own small icon, so without this branch the header keeps
        // that glyph while the status bar above it shows the package silhouette.
        assertEquals(
            MiuiHeaderShadeSource.SILHOUETTE,
            MiuiHeaderAppIconPolicy.selectMonochromeShadeSource(
                isMiPushManaged = false,
                attemptsIconPack = true,
                iconPackHit = false,
                isSyntheticAutogroupSummary = true,
                postedGlyphIsMonochrome = true,
                postedGlyphIsUnusable = false,
            ),
        )
        // The status bar forces a silhouette for summaries even outside the icon-pack switches;
        // the header does not, so a package the user excluded keeps its native header.
        assertEquals(
            MiuiHeaderShadeSource.NONE,
            MiuiHeaderAppIconPolicy.selectMonochromeShadeSource(
                isMiPushManaged = false,
                attemptsIconPack = false,
                iconPackHit = false,
                isSyntheticAutogroupSummary = true,
                postedGlyphIsMonochrome = true,
                postedGlyphIsUnusable = false,
            ),
        )
        // An icon-pack hit still wins over the summary branch.
        assertEquals(
            MiuiHeaderShadeSource.ICON_PACK,
            MiuiHeaderAppIconPolicy.selectMonochromeShadeSource(
                isMiPushManaged = false,
                attemptsIconPack = true,
                iconPackHit = true,
                isSyntheticAutogroupSummary = true,
                postedGlyphIsMonochrome = true,
                postedGlyphIsUnusable = false,
            ),
        )
    }
}
