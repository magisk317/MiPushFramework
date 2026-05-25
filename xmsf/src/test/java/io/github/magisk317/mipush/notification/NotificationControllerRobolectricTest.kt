package io.github.magisk317.mipush.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.core.app.NotificationCompat
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.magisk317.mipush.common.utils.CustomConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class NotificationControllerRobolectricTest {

    @AfterEach
    fun tearDown() {
        val notificationManager = RuntimeEnvironment.getApplication()
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.deleteNotificationChannel("target-default-implicit")
        notificationManager.deleteNotificationChannel("target-explicit")
    }

    @Test
    fun `channel selection uses managed channel when borrow channel is absent`() {
        val context = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val metaInfo = PushMetaInfo().apply {
            extra = mutableMapOf("channel_id" to "push")
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                "target-default-implicit",
                "Target Default",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        NotificationManagerEx.init(context)

        val channelId = NotificationController.getExistsChannelId(context, metaInfo, packageName)

        assertEquals(NotificationChannelManager.getChannelId(metaInfo, packageName), channelId)
    }

    @Test
    fun `channel selection borrows only the explicitly requested channel`() {
        val context = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val metaInfo = PushMetaInfo().apply {
            extra = mutableMapOf("__mi_push_borrow_channel_id" to "target-explicit")
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                "target-explicit",
                "Target Explicit",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        NotificationManagerEx.init(context)

        val channelId = NotificationController.getExistsChannelId(context, metaInfo, packageName)

        assertEquals("target-explicit", channelId)
    }

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

    @Test
    fun `default island payload uses hyperisland param v2 and icon bundle`() {
        val context = RuntimeEnvironment.getApplication()
        val metaInfo = PushMetaInfo().apply {
            title = "Island title"
            description = "Island body"
        }
        val icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        val focusBundle = MiPushIslandPayloadBuilder.build(
            context = context,
            metaInfo = metaInfo,
            packageName = context.packageName,
            largeIcon = icon
        )

        assertNotNull(focusBundle)
        val focusParam = focusBundle!!.getString("miui.focus.param")
        assertNotNull(focusParam)
        assertTrue(focusParam!!.contains(""""param_v2""""))
        assertTrue(focusParam.contains(""""mipush_framework_push""""))
        assertEquals(true to "close", NotificationSortFilter.parseFocusParamForTest(focusParam))
        assertEquals("miui.focus.pic_mipush_icon", focusBundle.getString("miui.focus.pic_mipush_icon"))
        val pics = focusBundle.getBundle("miui.focus.pics")
        assertNotNull(pics)
        assertNotNull(pics!!.parcelable<Icon>("miui.focus.pic_mipush_icon"))
    }

    @Test
    fun `live update notifications keep ongoing auto cancel semantics`() {
        val context = RuntimeEnvironment.getApplication()
        val builder = NotificationCompat.Builder(context, "progress")
            .setSmallIcon(android.R.drawable.stat_sys_download)
        val metaInfo = PushMetaInfo().apply {
            title = "外卖配送"
            description = "骑手已取餐，预计15分钟送达"
        }
        val result = LiveUpdateDetector.DetectionResult(
            isProgress = true,
            category = LiveUpdateDetector.ProgressCategory.DELIVERY,
            progressPercent = 65,
            progressText = metaInfo.description,
            startLabel = "商家",
            endLabel = "目的地",
            trackerLabel = "配送中"
        )

        ProgressStyleBuilder.applyProgressStyle(context, builder, metaInfo, result)
        val notification = builder.build()

        assertTrue(ProgressStyleBuilder.isLiveUpdate(builder))
        assertFalse(NotificationController.shouldAutoCancelNotification(metaInfo, builder))
        assertEquals(0, notification.flags and Notification.FLAG_AUTO_CANCEL)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `regular notifications remain auto cancellable`() {
        val context = RuntimeEnvironment.getApplication()
        val builder = NotificationCompat.Builder(context, "plain")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
        val metaInfo = PushMetaInfo().apply {
            title = "普通消息"
            description = "内容"
        }

        assertFalse(ProgressStyleBuilder.isLiveUpdate(builder))
        assertTrue(NotificationController.shouldAutoCancelNotification(metaInfo, builder))
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
