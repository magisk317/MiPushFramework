package io.github.magisk317.mipush.common.island

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class DynamicIslandColorResolverTest {
    @Test
    fun `dominant saturated pixels resolve to an opaque accent`() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(28, 126, 232))

        val color = DynamicIslandColorResolver.resolveBitmap(bitmap)

        assertNotNull(color)
        assertTrue(color!!.startsWith("#FF"))
        bitmap.recycle()
    }

    @Test
    fun `transparent or neutral pixels do not invent an accent`() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)

        assertTrue(DynamicIslandColorResolver.resolveBitmap(bitmap).isNullOrBlank())
        bitmap.recycle()
    }
}
