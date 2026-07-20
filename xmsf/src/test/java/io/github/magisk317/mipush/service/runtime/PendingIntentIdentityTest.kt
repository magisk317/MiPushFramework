package io.github.magisk317.mipush.service.runtime

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import com.xiaomi.mipush.sdk.PushMessageHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class PendingIntentIdentityTest {
    @Test
    fun `same notification action has stable request code`() {
        val first = MyMIPushNotificationIntentSupport.pendingIntentRequestCode("app.one", 42, 1)
        val second = MyMIPushNotificationIntentSupport.pendingIntentRequestCode("app.one", 42, 1)

        assertEquals(first, second)
    }

    @Test
    fun `package notification and button contribute to request code`() {
        val baseline = MyMIPushNotificationIntentSupport.pendingIntentRequestCode("app.one", 42, 1)

        assertNotEquals(baseline, MyMIPushNotificationIntentSupport.pendingIntentRequestCode("app.two", 42, 1))
        assertNotEquals(baseline, MyMIPushNotificationIntentSupport.pendingIntentRequestCode("app.one", 43, 1))
        assertNotEquals(baseline, MyMIPushNotificationIntentSupport.pendingIntentRequestCode("app.one", 42, 2))
    }

    @Test
    fun `intent identity prevents reuse even when request codes collide`() {
        val context = RuntimeEnvironment.getApplication()
        val requestCode = 7
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        fun pendingIntent(packageName: String): PendingIntent = PendingIntent.getService(
            context,
            requestCode,
            Intent("test.action").apply {
                setClassName(context, "example.TargetService")
                data = MyMIPushNotificationIntentSupport.pendingIntentIdentity(packageName, 42, 0)
            },
            flags,
        )

        assertNotEquals(pendingIntent("app.one"), pendingIntent("app.two"))
    }

    @Test
    fun `self push handler remains available behind receive permission`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, PushMessageHandler::class.java),
            PackageManager.GET_META_DATA,
        )
        val pendingIntent = PendingIntent.getService(
            context,
            9,
            Intent(context, PushMessageHandler::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        assertEquals("com.xiaomi.xmsf.permission.MIPUSH_RECEIVE", serviceInfo.permission)
        assertNotNull(pendingIntent)
    }
}
