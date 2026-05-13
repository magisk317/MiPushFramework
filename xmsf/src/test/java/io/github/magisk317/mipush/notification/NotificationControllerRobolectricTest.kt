package io.github.magisk317.mipush.notification

import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import io.github.magisk317.mipush.common.utils.CustomConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
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
        assertNotNull(pics!!.parcelable<Icon>("miui.focus.pic_profile"))
        assertNull(pics.parcelable<Icon>("miui.focus.pic_aod"))
    }

    private inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelable(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelable(key)
        }
    }
}
