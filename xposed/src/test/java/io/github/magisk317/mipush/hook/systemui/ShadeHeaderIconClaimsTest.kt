package io.github.magisk317.mipush.hook.systemui

import android.content.res.ColorStateList
import android.graphics.drawable.Icon
import android.widget.ImageView
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShadeHeaderIconClaimsTest {

    private val view = mockk<ImageView>(relaxed = true)
    private val otherView = mockk<ImageView>(relaxed = true)
    private val icon = mockk<Icon>(relaxed = true)
    private val tint = mockk<ColorStateList>(relaxed = true)

    private fun claim(view: ImageView = this.view, icon: Icon = this.icon, tint: ColorStateList = this.tint) {
        ShadeHeaderIconClaims.claim(view, icon, tint)
    }

    @Test
    fun `claimed view answers with the glyph and tint it was given`() {
        claim()

        val stored = ShadeHeaderIconClaims.claimFor(view)

        assertSame(icon, stored?.icon)
        assertSame(tint, stored?.tint)
    }

    @Test
    fun `unclaimed view has no claim`() {
        assertNull(ShadeHeaderIconClaims.claimFor(view))
    }

    @Test
    fun `a new bind invalidates the previous claim`() {
        claim()

        ShadeHeaderIconClaims.invalidate(view)

        assertNull(ShadeHeaderIconClaims.claimFor(view))
    }

    @Test
    fun `invalidating one view leaves another view's claim alone`() {
        claim(view = view)
        claim(view = otherView)

        ShadeHeaderIconClaims.invalidate(view)

        assertNull(ShadeHeaderIconClaims.claimFor(view))
        assertSame(icon, ShadeHeaderIconClaims.claimFor(otherView)?.icon)
    }

    @Test
    fun `a later claim replaces the earlier one for the same view`() {
        claim()
        val secondIcon = mockk<Icon>(relaxed = true)

        claim(icon = secondIcon)

        assertSame(secondIcon, ShadeHeaderIconClaims.claimFor(view)?.icon)
    }

    @Test
    fun `restore guard is down outside and up inside the block`() {
        assertFalse(ShadeHeaderIconClaims.isRestoring)

        var observed = false
        ShadeHeaderIconClaims.restoring { observed = ShadeHeaderIconClaims.isRestoring }

        assertTrue(observed)
        assertFalse(ShadeHeaderIconClaims.isRestoring)
    }

    @Test
    fun `nested restore blocks keep the guard raised and restore it afterwards`() {
        var innerObserved = false
        ShadeHeaderIconClaims.restoring {
            ShadeHeaderIconClaims.restoring { innerObserved = ShadeHeaderIconClaims.isRestoring }
        }

        assertTrue(innerObserved)
        assertFalse(ShadeHeaderIconClaims.isRestoring)
    }
}
