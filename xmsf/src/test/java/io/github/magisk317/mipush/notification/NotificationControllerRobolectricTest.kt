package io.github.magisk317.mipush.notification

import android.graphics.Bitmap
import android.graphics.drawable.Icon
import io.github.magisk317.mipush.common.utils.CustomConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotificationControllerRobolectricTest {

    @Test
    fun `focus bundle uses each focus pic uri and skips missing bitmaps`() {
        val loadedUris = mutableListOf<String>()
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val configuration = CustomConfiguration(
            linkedMapOf(
                "miui.focus.param" to """{"updatable":true,"reopen":"close"}""",
                "miui.focus.pic_profile" to "content://profile",
                "miui.focus.pic_aod" to "content://aod",
                "miui.focus.pic_empty" to ""
            )
        )

        val focusBundle = NotificationController.buildFocusBundle(configuration) { uri ->
            loadedUris += uri
            if (uri == "content://profile") bitmap else null
        }

        assertNotNull(focusBundle)
        assertEquals(setOf("content://profile", "content://aod"), loadedUris.toSet())
        assertEquals("content://profile", focusBundle!!.getString("miui.focus.pic_profile"))
        assertEquals("content://aod", focusBundle.getString("miui.focus.pic_aod"))

        val pics = focusBundle.getBundle("miui.focus.pics")
        assertNotNull(pics)
        assertNotNull(pics!!.getParcelable<Icon>("miui.focus.pic_profile"))
        assertNull(pics.getParcelable<Icon>("miui.focus.pic_aod"))
    }
}
