package io.github.magisk317.mipush.notification

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import com.xiaomi.xmpush.thrift.PushMetaInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class ProgressStyleBuilderAndroid16RobolectricTest {

    @Test
    fun `native live update preserves promoted ongoing request extra on Android 16`() {
        val context = RuntimeEnvironment.getApplication()
        val builder = NotificationCompat.Builder(context, "progress")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("下载更新")
            .setContentText("下载中 65%")
        val metaInfo = PushMetaInfo().apply {
            title = "下载更新"
            description = "下载中 65%"
        }
        val result = LiveUpdateDetector.DetectionResult(
            isProgress = true,
            category = LiveUpdateDetector.ProgressCategory.DOWNLOAD,
            progressPercent = 65,
            progressText = metaInfo.description,
            startLabel = "开始",
            endLabel = "完成",
            trackerLabel = "65%",
        )

        ProgressStyleBuilder.applyProgressStyle(context, builder, metaInfo, result)
        val notification = ProgressStyleBuilder.buildNotification(context, builder)

        assertTrue(notification.extras.getBoolean("xmsf.live_update", false))
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, false))
        assertEquals(65, notification.extras.getInt("xmsf.live_update.progress"))
        assertEquals(0, notification.flags and Notification.FLAG_AUTO_CANCEL)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }
}
