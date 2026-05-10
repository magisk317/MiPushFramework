package io.github.magisk317.mipush.service.runtime

import android.app.Notification
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmsf.R
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MyMIPushNotificationStyleSupportRobolectricTest {

    @Test
    fun `normal builder renders sweet tags for collapsed text and big text`() {
        val context = RuntimeEnvironment.getApplication()
        val description = """This is a long notification body with <ft color="#ff0000">rich text</ft> inside."""
        val metaInfo = PushMetaInfo().apply {
            title = "Title"
            this.description = description
        }

        val builder = MyMIPushNotificationStyleSupport.normalStyleNotificationBuilder(
            context,
            metaInfo,
            context.packageName
        )
        builder.setSmallIcon(R.drawable.ic_notifications_black_24dp)
        val notification = builder.build()

        assertFalse(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("<ft"))
        assertFalse(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("<ft"))
    }
}
